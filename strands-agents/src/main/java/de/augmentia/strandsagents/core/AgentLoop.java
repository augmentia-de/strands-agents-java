package de.augmentia.strandsagents.core;

import de.augmentia.strandsagents.core.context.AgentContext;
import de.augmentia.strandsagents.core.internal.ChatMessageConverter;
import de.augmentia.strandsagents.interceptor.pipeline.HookContexts;
import de.augmentia.strandsagents.interceptor.pipeline.HookResult;
import de.augmentia.strandsagents.interceptor.plugin.Plugin;
import de.augmentia.strandsagents.interceptor.guardrails.GuardrailException;
import de.augmentia.strandsagents.interceptor.guardrails.GuardrailResult;
import de.augmentia.strandsagents.interceptor.hitl.checkpoint.Checkpoint;
import de.augmentia.strandsagents.interceptor.resilience.Retry;
import de.augmentia.strandsagents.interceptor.resilience.RetryConfig;
import de.augmentia.strandsagents.interceptor.resilience.TokenRecovery;
import de.augmentia.strandsagents.model.agent.AgentPhase;
import de.augmentia.strandsagents.model.agent.AgentResult;
import de.augmentia.strandsagents.model.agent.ExecutionMetrics;
import de.augmentia.strandsagents.model.agent.StopReason;
import de.augmentia.strandsagents.model.event.*;
import de.augmentia.strandsagents.model.message.Message;
import de.augmentia.strandsagents.model.tool.ToolCall;
import de.augmentia.strandsagents.model.tool.ToolExecutionResult;
import de.augmentia.strandsagents.prompt.PromptRegistry;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;

import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

final class AgentLoop {

    private static final Logger log = LoggerFactory.getLogger(AgentLoop.class);
    private static final int LOG_MAX = 2000;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true)
        .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final Agent agent;
    private final RunSnapshot run;
    private final String sid;
    private String prompt;
    private final Map<String, Object> contextVariables;

    private long startNanos;
    private int totalInputTokens;
    private int totalOutputTokens;
    private int toolCallCount;
    private boolean structuredForceAttempted;
    private boolean structuredForceActive;
    private String structuredOutputResult;
    private final StuckDetector stuckDetector = new StuckDetector();

    AgentLoop(Agent agent, String sid, String prompt, Map<String, Object> contextVariables) {
        this.agent = agent;
        this.run = agent.runConfig.snapshot();
        this.sid = sid;
        this.prompt = prompt;
        this.contextVariables = contextVariables;
        agent.abortFlag.set(false);
    }

    AgentResult execute() {
        agent.cancelled = false;
        agent.executionThread = Thread.currentThread();
        try {
            startNanos = System.nanoTime();
            totalInputTokens = 0;
            totalOutputTokens = 0;
            toolCallCount = 0;
            structuredForceAttempted = false;
            structuredForceActive = false;
            structuredOutputResult = null;

            agent.phase = AgentPhase.EXECUTING;
            agent.fire(new AgentStartedEvent(sid, Instant.now(), prompt));

            if (handleBeforeAgent()) return lastResult;

            var sysPrompt = run.systemPrompt();
            if (sysPrompt != null && !sysPrompt.isBlank()) {
                var hasSystemMessage = agent.chatMemory.messages().stream()
                    .anyMatch(m -> m instanceof SystemMessage);
                if (!hasSystemMessage) {
                    agent.chatMemory.add(SystemMessage.from(sysPrompt));
                }
            }

            prompt = prompt.trim();
            agent.chatMemory.add(UserMessage.from(prompt));


            for (int iteration = 0; iteration < Agent.MAX_TOOL_ITERATIONS; iteration++) {
                checkPaused();
                if (agent.cancelled || agent.abortFlag.get()) {
                    agent.cancelled = true;
                    throw new RuntimeException("Agent execution cancelled by user");
                }

                log.debug("Iteration {}/{} — chatMemory messages={}",
                    iteration + 1, Agent.MAX_TOOL_ITERATIONS, agent.chatMemory.messages().size());

                var domainMessages = pruneConversation();

                if (runInputGuardrails(domainMessages)) return lastResult;

                var effectivePrompt = buildSystemPrompt(domainMessages);
                var toolSpecs = buildToolSpecs();

                String promptStr = handleBeforeModelCall(effectivePrompt, domainMessages, toolSpecs);
                if (promptStr == null) return lastResult;

                if (!promptStr.equals(run.systemPrompt()) && agent.chatMemory instanceof MultiSystemMessageChatMemory msm) {
                    msm.replaceFirstSystemMessage(SystemMessage.from(promptStr));
                }

                var aiMessage = invokeModel(agent.chatMemory.messages(), toolSpecs);
                if (aiMessage == null) return lastResult;

                if (runOutputGuardrails(domainMessages, aiMessage)) return lastResult;

                agent.chatMemory.add(aiMessage);

                if (handleStructuredOutput(aiMessage)) continue;

                if (!aiMessage.hasToolExecutionRequests()) {
                    return handleCompletion(aiMessage);
                }

                toolCallCount += aiMessage.toolExecutionRequests().size();
                executeTools(aiMessage);

                if (stuckDetector.isStuck(aiMessage.toolExecutionRequests())) {
                    log.warn("Stuck-state detected — terminating after {} iterations", iteration + 1);
                    return handleStuck();
                }
            }

            return handleMaxIterations();
        } finally {
            agent.executionThread = null;
        }
    }

    // ── Hooks ────────────────────────────────────────────────────────

    private boolean handleBeforeAgent() {
        var beforeAgentResult = run.hookRegistry().triggerBeforeAgent(
            new HookContexts.BeforeAgentContext(sid, prompt, contextVariables));
        if (beforeAgentResult instanceof HookResult.Modify<?> m) {
            log.debug("beforeAgent hook modified prompt — was '{}', now '{}'",
                truncate(prompt), truncate((String) m.value()));
            prompt = (String) m.value();
        } else if (beforeAgentResult instanceof HookResult.Cancel c) {
            log.debug("beforeAgent hook cancelled — reason={}", c.reason());
            lastResult = result(c.reason(), StopReason.INTERRUPTED);
            agent.fire(new AgentFinishedEvent(sid, Instant.now(), lastResult.finalAnswer()));
            return true;
        }
        return false;
    }

    private String handleBeforeModelCall(String effectivePrompt, List<Message> domainMessages,
                                          List<ToolSpecification> toolSpecs) {
        var promptBuilder = new StringBuilder(effectivePrompt);
        var beforeMcCtx = new HookContexts.BeforeModelCallContext(
            sid, promptBuilder, domainMessages, toolSpecs, new ArrayList<>());
        var beforeMcResult = run.hookRegistry().triggerBeforeModelCall(beforeMcCtx);
        var modifiedPrompt = beforeMcCtx.systemPrompt().toString();
        if (beforeMcResult instanceof HookResult.Modify<?> m
                && m.value() instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            var modifiedTools = (List<ToolSpecification>) list;
            if (modifiedTools != toolSpecs) {
                log.debug("beforeModelCall hook modified tools — {} → {} tools",
                    toolSpecs.size(), modifiedTools.size());
                toolSpecs.clear();
                toolSpecs.addAll(modifiedTools);
            }
        }
        if (beforeMcResult instanceof HookResult.Cancel c) {
            log.debug("beforeModelCall hook cancelled — reason={}", c.reason());
            agent.phase = AgentPhase.FAILED;
            lastResult = result(PromptRegistry.get("agent.hook_cancelled", c.reason()), StopReason.INTERRUPTED);
            agent.fire(new AgentFinishedEvent(sid, Instant.now(), lastResult.finalAnswer()));
            return null;
        }

        var currentToolNames = toolSpecs.stream()
            .map(ToolSpecification::name)
            .sorted()
            .toList();
        if (!currentToolNames.equals(agent.lastToolNames)) {
            if (!agent.lastToolNames.isEmpty()) {
                var added = new ArrayList<>(currentToolNames);
                added.removeAll(agent.lastToolNames);
                var removed = new ArrayList<>(agent.lastToolNames);
                removed.removeAll(currentToolNames);
                var notice = new StringBuilder("SYSTEM NOTE: Your available tools have been updated.");
                if (!added.isEmpty()) {
                    notice.append(" Added: ").append(String.join(", ", added));
                }
                if (!removed.isEmpty()) {
                    notice.append(" Removed: ").append(String.join(", ", removed));
                }
                log.debug("Tool change detected — added={}, removed={}", added, removed);
                beforeMcCtx.additionalMessages().add(
                    new de.augmentia.strandsagents.model.message.SystemMessage(
                        UUID.randomUUID().toString(), Instant.now(), notice.toString(), Map.of()));
            }
            agent.lastToolNames = currentToolNames;
        }

        if (!beforeMcCtx.additionalMessages().isEmpty()) {
            for (var msg : beforeMcCtx.additionalMessages()) {
                agent.chatMemory.add(ChatMessageConverter.toLangChain4j(msg));
            }
        }

        return modifiedPrompt;
    }

    // ── Conversation ─────────────────────────────────────────────────

    private List<Message> pruneConversation() {
        var currentMessages = agent.chatMemory.messages();
        var domainMessages = ChatMessageConverter.toDomainMessages(currentMessages);

        if (agent.conversationManager != null) {
            domainMessages = agent.conversationManager.prune(domainMessages);
            var prunedLangChain = ChatMessageConverter.toLangChain4jMessages(domainMessages);
            agent.chatMemory.clear();
            for (var msg : prunedLangChain) {
                agent.chatMemory.add(msg);
            }
            domainMessages = ChatMessageConverter.toDomainMessages(prunedLangChain);
        }

        return domainMessages;
    }

    // ── Guardrails ───────────────────────────────────────────────────

    private boolean runInputGuardrails(List<Message> domainMessages) {
        for (var plugin : agent.getOrderedPlugins()) {
            for (var g : plugin.getInputGuardrails()) {
                var result = g.validate(domainMessages, plugin.name());
                if (!result.pass()) {
                    var guardResult = handlePluginGuardrail(plugin, result);
                    if (guardResult != null) {
                        lastResult = guardResult;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean runOutputGuardrails(List<Message> domainMessages, AiMessage aiMessage) {
        var responseText = aiMessage.text() != null ? aiMessage.text() : "";
        agent.fire(new AfterInvocationEvent(sid, Instant.now(), responseText, domainMessages));
        for (var plugin : agent.getOrderedPlugins()) {
            for (var g : plugin.getOutputGuardrails()) {
                var result = g.validate(domainMessages, "output:" + responseText);
                if (!result.pass()) {
                    var guardResult = handlePluginGuardrail(plugin, result);
                    if (guardResult != null) {
                        lastResult = guardResult;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private AgentResult handlePluginGuardrail(Plugin plugin, GuardrailResult guardResult) {
        log.debug("Guardrail blocked by '{}' — action={}, reason='{}'",
            plugin.name(), plugin.getBlockAction(), guardResult.reason());
        var sanitized = guardResult.sanitized();
        return switch (plugin.getBlockAction()) {
            case THROW -> {
                var msg = sanitized != null
                    ? guardResult.reason() + " (sanitized: " + sanitized + ")"
                    : guardResult.reason();
                throw new GuardrailException(msg);
            }
            case FALLBACK -> {
                agent.phase = AgentPhase.FAILED;
                var answer = sanitized != null ? sanitized : plugin.getFallbackMessage();
                agent.fire(new AgentFinishedEvent(sid, Instant.now(), answer));
                yield result(answer, StopReason.ERROR);
            }
            case ESCALATE -> {
                if (agent.checkpointService != null) {
                    var cp = agent.checkpointService.createCheckpoint(sid, "guardrail:" + plugin.name(), guardResult.reason());
                    agent.phase = AgentPhase.WAITING_FOR_HUMAN;
                    try {
                        var resolved = agent.checkpointService.await(cp);
                        if (resolved.status() == Checkpoint.Status.APPROVED) {
                            agent.phase = AgentPhase.EXECUTING;
                            yield null;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                agent.phase = AgentPhase.FAILED;
                var answer = sanitized != null ? sanitized : plugin.getFallbackMessage();
                agent.fire(new AgentFinishedEvent(sid, Instant.now(), answer));
                yield result(answer, StopReason.ERROR);
            }
        };
    }

    // ── System Prompt ────────────────────────────────────────────────

    private String buildSystemPrompt(List<Message> domainMessages) {
        var sb = new StringBuilder(run.systemPrompt() != null ? run.systemPrompt() : "");
        var bie = new BeforeInvocationEvent(sid, Instant.now(), sb, domainMessages);
        agent.fire(bie);
        return sb.toString().trim();
    }

    private List<ToolSpecification> buildToolSpecs() {
        var specs = structuredForceActive
            ? java.util.Collections.<ToolSpecification>emptyList()
            : run.toolRegistry() != null ? run.toolRegistry().getSpecifications()
              : java.util.Collections.<ToolSpecification>emptyList();
        return new ArrayList<>(specs);
    }

    // ── Structured Output ────────────────────────────────────────────

    private boolean handleStructuredOutput(AiMessage aiMessage) {
        var soConfig = run.structuredOutputConfig();
        if (soConfig != null && soConfig.isEnabled() && !aiMessage.hasToolExecutionRequests()) {
            var responseText = aiMessage.text() != null ? aiMessage.text() : "";
            try {
                OBJECT_MAPPER.readTree(responseText);
                structuredOutputResult = responseText;
                log.debug("Structured output parsed successfully — {} chars", responseText.length());
            } catch (JsonProcessingException e) {
                if (!structuredForceAttempted) {
                    log.debug("Structured output parse failed, forcing with prompt");
                    structuredForceAttempted = true;
                    structuredForceActive = true;
                    agent.chatMemory.add(UserMessage.from(soConfig.forcePrompt()));
                    return true;
                }
                log.debug("Structured output force attempt also failed");
            }
        }
        return false;
    }

    // ── LLM Call ─────────────────────────────────────────────────────

    private AiMessage invokeModel(List<ChatMessage> currentMessages, List<ToolSpecification> toolSpecs) {
        var responseText = "";
        AiMessage aiMessage = null;

        for (int hookRetry = 0; hookRetry < Agent.MAX_HOOK_RETRIES; hookRetry++) {
            var domainMessages = ChatMessageConverter.toDomainMessages(currentMessages);
            agent.fire(new ModelRequestedEvent(sid, Instant.now(), domainMessages));

            ChatResponse response;
            try {
                response = callWithResilience(currentMessages, toolSpecs);
            } catch (Exception e) {
                agent.phase = AgentPhase.FAILED;
                lastResult = result(PromptRegistry.get("agent.llm_error", e.getMessage()), StopReason.ERROR);
                agent.fire(new AgentFinishedEvent(sid, Instant.now(), lastResult.finalAnswer()));
                return null;
            }

            aiMessage = response.aiMessage();
            responseText = aiMessage.text() != null ? aiMessage.text() : "";
            agent.lastThinking = aiMessage.thinking();

            var inputTokens = response.tokenUsage() != null ? response.tokenUsage().inputTokenCount() : 0;
            var outputTokens = response.tokenUsage() != null ? response.tokenUsage().outputTokenCount() : 0;

            totalInputTokens += inputTokens;
            totalOutputTokens += outputTokens;

            log.debug("LLM call — hookRetry={}, inputTokens={}, outputTokens={}, responseLen={}",
                hookRetry, inputTokens, outputTokens, responseText.length());

            var afterMc = run.hookRegistry().triggerAfterModelCall(
                new HookContexts.AfterModelCallContext(sid, responseText, inputTokens, outputTokens, response), responseText);
            if (afterMc instanceof HookResult.Cancel c) {
                log.debug("afterModelCall hook cancelled — reason={}", c.reason());
                agent.phase = AgentPhase.FAILED;
                lastResult = result(PromptRegistry.get("agent.hook_cancelled", c.reason()), StopReason.INTERRUPTED);
                agent.fire(new AgentFinishedEvent(sid, Instant.now(), lastResult.finalAnswer()));
                return null;
            }
            if (afterMc instanceof HookResult.Modify<?> m) {
                log.debug("afterModelCall hook modified response — was '{}', now '{}'",
                    truncate(responseText), truncate((String) m.value()));
                responseText = (String) m.value();
            }
            if (afterMc instanceof HookResult.Retry) {
                log.debug("afterModelCall hook requested retry — reason={}", ((HookResult.Retry) afterMc).reason());
                continue;
            }
            break;
        }

        return aiMessage != null
            ? AiMessage.builder()
                .text(responseText)
                .toolExecutionRequests(aiMessage.toolExecutionRequests())
                .build()
            : null;
    }

    private ChatResponse callWithResilience(List<ChatMessage> currentMessages, List<ToolSpecification> toolSpecs) {
        var recovery = new TokenRecovery();
        var msgs = currentMessages;

        while (true) {
            try {
                var request = buildRequest(msgs, toolSpecs);

                Callable<ChatResponse> chatCall = () -> agent.doChat(request);

                if (agent.modelTimeout != null || agent.circuitBreaker != null) {
                    Callable<ChatResponse> wrapped = () -> {
                        var future = CompletableFuture.supplyAsync(() -> agent.doChat(request), Agent.VIRTUAL_EXECUTOR);
                        if (agent.modelTimeout != null) {
                            return future.get(agent.modelTimeout.toMillis(), TimeUnit.MILLISECONDS);
                        }
                        return future.join();
                    };
                    if (agent.circuitBreaker != null) {
                        RetryConfig cfg = effectiveRetryConfig();
                        return agent.circuitBreaker.call(
                            () -> Retry.run(wrapped, cfg),
                            () -> { throw new RuntimeException("CircuitBreaker: Service temporarily unavailable"); });
                    }
                    return Retry.run(wrapped, effectiveRetryConfig());
                }

                return Retry.run(chatCall, effectiveRetryConfig());
            } catch (Exception e) {
                if (TokenRecovery.isTokenLimitError(e) && recovery.recover(agent.chatMemory)) {
                    msgs = agent.chatMemory.messages();
                    continue;
                }
                if (e instanceof RuntimeException re) {
                    throw re;
                }
                throw new RuntimeException("LLM call failed", e);
            }
        }
    }

    private ChatRequest buildRequest(List<ChatMessage> messages, List<ToolSpecification> toolSpecs) {
        var builder = ChatRequest.builder();
        builder.messages(messages);
        if (toolSpecs != null && !toolSpecs.isEmpty()) {
            builder.toolSpecifications(toolSpecs);
        }
        var soConfig = run.structuredOutputConfig();
        if (soConfig != null && soConfig.isEnabled()) {
            var schemaStr = soConfig.effectiveSchema();
            if (schemaStr != null) {
                var rawSchema = JsonRawSchema.from(schemaStr);
                var jsonSchema = JsonSchema.builder()
                    .name(soConfig.mode().name())
                    .rootElement(rawSchema)
                    .build();
                builder.responseFormat(ResponseFormat.builder()
                    .type(ResponseFormatType.JSON)
                    .jsonSchema(jsonSchema)
                    .build());
            }
        }
        return builder.build();
    }

    private RetryConfig effectiveRetryConfig() {
        return agent.retryConfig != null ? agent.retryConfig : RetryConfig.DEFAULT;
    }

    // ── Tool Execution ───────────────────────────────────────────────

    private void executeTools(AiMessage aiMessage) {
        for (ToolExecutionRequest req : aiMessage.toolExecutionRequests()) {
            if (agent.cancelled || agent.abortFlag.get()) {
                agent.cancelled = true;
                throw new RuntimeException("Agent execution cancelled by user");
            }

            var args = parseArgs(req.arguments());

            var beforeTc = run.hookRegistry().triggerBeforeToolCall(
                new HookContexts.BeforeToolCallContext(sid, req.name(), args));
            if (beforeTc instanceof HookResult.Cancel c) {
                log.debug("beforeToolCall hook cancelled tool '{}' — reason={}", req.name(), c.reason());
                agent.fire(new ToolExecutionStartedEvent(sid, Instant.now(),
                    new ToolCall(req.id(), req.name(), req.arguments())));
                agent.fire(new ToolExecutionFinishedEvent(sid, Instant.now(),
                    new ToolExecutionResult(req.id(), req.name(), "Skipped: " + c.reason(), false)));
                continue;
            }

            agent.fire(new ToolExecutionStartedEvent(sid, Instant.now(),
                new ToolCall(req.id(), req.name(), req.arguments())));

            // Link agent's abort flag to toolRegistry for tool execution
            run.toolRegistry().setAbortFlag(agent.abortFlag);

            String prevSessionId = AgentContext.SESSION_ID.get();
            AgentContext.SESSION_ID.set(sid);
            try {
                ToolExecutionResult toolResult;
                if (contextVariables.isEmpty()) {
                    toolResult = wrapWithRetry(() ->
                        agent.toolExecutor.execute(req, run.toolRegistry()));
                } else {
                    var prevSession = AgentContext.SESSION.get();
                    AgentContext.SESSION.set(contextVariables);
                    try {
                        toolResult = wrapWithRetry(() ->
                            agent.toolExecutor.execute(req, run.toolRegistry()));
                    } finally {
                        if (prevSession != null) {
                            AgentContext.SESSION.set(prevSession);
                        } else {
                            AgentContext.SESSION.remove();
                        }
                    }
                }

                var afterTcMessages = new ArrayList<de.augmentia.strandsagents.model.message.Message>();
                var afterTcCtx = new HookContexts.AfterToolCallContext(
                    sid, req.name(), toolResult.result(), toolResult.isError(), afterTcMessages);
                var afterTcResult = run.hookRegistry().triggerAfterToolCall(afterTcCtx, toolResult.result());
                var modifiedResult = afterTcResult instanceof HookResult.Modify<?>(Object value)
                        ? (String) value : toolResult.result();
                if (afterTcResult instanceof HookResult.Modify<?>) {
                    log.debug("afterToolCall '{}' — isError={}, modified={}",
                            req.name(), toolResult.isError(), afterTcResult instanceof HookResult.Modify);
                }
                var finalToolResult = new ToolExecutionResult(req.id(), req.name(), modifiedResult, toolResult.isError());

                var request = findRequest(aiMessage.toolExecutionRequests(), req.name());
                if (request != null) {
                    agent.chatMemory.add(ToolExecutionResultMessage.from(request, modifiedResult));
                }
                for (var msg : afterTcMessages) {
                    agent.chatMemory.add(ChatMessageConverter.toLangChain4j(msg));
                }
                agent.fire(new ToolExecutionFinishedEvent(sid, Instant.now(), finalToolResult));
            } catch (Exception e) {
                if (agent.abortFlag.get()) {
                    log.debug("Tool execution aborted by user");
                    throw new RuntimeException("Agent execution cancelled by user");
                }
                Throwable cause = e;
                while ((cause instanceof java.util.concurrent.ExecutionException ||
                        cause instanceof java.lang.reflect.InvocationTargetException) &&
                       cause.getCause() != null) {
                    cause = cause.getCause();
                }
                log.error("Tool execution error in '{}': {}", req.name(), cause.getMessage());
                var errorMessage = agent.retryConfig != null
                    ? "Tool '" + req.name() + "' failed after "
                        + agent.retryConfig.maxAttempts() + " attempts: " + cause.getMessage()
                    : "Tool error: " + cause.getMessage();
                var toolResult = new ToolExecutionResult(req.id(), req.name(), errorMessage, true);

                var request = findRequest(aiMessage.toolExecutionRequests(), req.name());
                if (request != null) {
                    log.debug("Tool execution error — request={}", truncate(String.valueOf(request)));
                    agent.chatMemory.add(ToolExecutionResultMessage.from(request, errorMessage));
                }
                agent.fire(new ToolExecutionFinishedEvent(sid, Instant.now(), toolResult));
            } finally {
                if (prevSessionId != null) {
                    AgentContext.SESSION_ID.set(prevSessionId);
                } else {
                    AgentContext.SESSION_ID.remove();
                }
            }
        }
    }

    private <T> T wrapWithRetry(Callable<T> callable) throws Exception {
        if (agent.retryConfig != null) {
            return Retry.run(callable, agent.retryConfig);
        }
        return callable.call();
    }

    private ToolExecutionRequest findRequest(List<ToolExecutionRequest> requests, String toolName) {
        return requests.stream()
            .filter(r -> r.name().equals(toolName))
            .findFirst()
            .orElse(null);
    }

    private Map<String, Object> parseArgs(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(arguments,
                new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    // ── Completion ───────────────────────────────────────────────────

    private AgentResult handleCompletion(AiMessage aiMessage) {
        var responseText = aiMessage.text() != null ? aiMessage.text() : "";
        var doneResult = new AgentResult(
            sid,
            responseText,
            ChatMessageConverter.toDomainMessages(agent.chatMemory.messages()),
            new ExecutionMetrics(durationMs(), totalInputTokens, totalOutputTokens, toolCallCount),
            StopReason.COMPLETED,
            structuredOutputResult
        );
        var afterAgent = run.hookRegistry().triggerAfterAgent(
            new HookContexts.AfterAgentContext(sid, doneResult), doneResult.finalAnswer());
        var finalAnswer = afterAgent instanceof HookResult.Modify<?> m
            ? (String) m.value() : doneResult.finalAnswer();
        if (afterAgent instanceof HookResult.Modify<?> m) {
            log.debug("afterAgent hook — answer was modified (len {}→{})",
                    doneResult.finalAnswer().length(), finalAnswer.length());
        }

        agent.phase = AgentPhase.COMPLETED;
        var result = new AgentResult(sid, finalAnswer, doneResult.generatedMessages(),
            doneResult.metrics(), doneResult.stopReason(), doneResult.structuredOutput());
        agent.fire(new AgentFinishedEvent(sid, Instant.now(), result.finalAnswer()));
        return result;
    }

    private AgentResult handleMaxIterations() {
        log.debug("Max iterations ({}) reached — returning result", Agent.MAX_TOOL_ITERATIONS);
        agent.phase = AgentPhase.FAILED;
        var result = new AgentResult(
            sid,
            PromptRegistry.getOrDefault("agent.max_iterations", "Maximum iterations reached"),
            ChatMessageConverter.toDomainMessages(agent.chatMemory.messages()),
            new ExecutionMetrics(durationMs(), totalInputTokens, totalOutputTokens, toolCallCount),
            StopReason.MAX_ITERATIONS
        );
        var afterAgent = run.hookRegistry().triggerAfterAgent(
            new HookContexts.AfterAgentContext(sid, result), result.finalAnswer());
        var finalAnswer = afterAgent instanceof HookResult.Modify<?>(Object value)
            ? (String) value : result.finalAnswer();
        var modifiedResult = new AgentResult(sid, finalAnswer, result.generatedMessages(),
            result.metrics(), result.stopReason(), result.structuredOutput());
        agent.fire(new AgentFinishedEvent(sid, Instant.now(), modifiedResult.finalAnswer()));
        return modifiedResult;
    }

    private AgentResult handleStuck() {
        log.debug("Stuck-state detected — returning result");
        agent.phase = AgentPhase.FAILED;
        var result = new AgentResult(
            sid,
            PromptRegistry.getOrDefault("agent.stuck", "Stuck-state detected — repeated identical tool calls"),
            ChatMessageConverter.toDomainMessages(agent.chatMemory.messages()),
            new ExecutionMetrics(durationMs(), totalInputTokens, totalOutputTokens, toolCallCount),
            StopReason.STUCK
        );
        var afterAgent = run.hookRegistry().triggerAfterAgent(
            new HookContexts.AfterAgentContext(sid, result), result.finalAnswer());
        var finalAnswer = afterAgent instanceof HookResult.Modify<?>(Object value)
            ? (String) value : result.finalAnswer();
        var modifiedResult = new AgentResult(sid, finalAnswer, result.generatedMessages(),
            result.metrics(), result.stopReason(), result.structuredOutput());
        agent.fire(new AgentFinishedEvent(sid, Instant.now(), modifiedResult.finalAnswer()));
        return modifiedResult;
    }

    // ── HITL / Pause ─────────────────────────────────────────────────

    private void checkPaused() {
        if (agent.phase == AgentPhase.WAITING_FOR_HUMAN) {
            agent.pauseLock.lock();
            try {
                while (agent.phase == AgentPhase.WAITING_FOR_HUMAN) {
                    agent.pauseCondition.await();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("HITL interrupted", e);
            } finally {
                agent.pauseLock.unlock();
            }
            if (agent.phase == AgentPhase.FAILED) {
                throw new RuntimeException("HITL rejected");
            }
        }
    }

    // ── Result Factory ───────────────────────────────────────────────

    private AgentResult lastResult;

    private AgentResult result(String answer, StopReason reason) {
        return new AgentResult(
            sid,
            answer,
            ChatMessageConverter.toDomainMessages(agent.chatMemory.messages()),
            new ExecutionMetrics(durationMs(), totalInputTokens, totalOutputTokens, toolCallCount),
            reason
        );
    }

    private long durationMs() {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    // ── Utility ──────────────────────────────────────────────────────

    private static String truncate(String s) {
        if (s == null) return "null";
        return s.length() <= LOG_MAX ? s : s.substring(0, LOG_MAX) + "...";
    }
}
