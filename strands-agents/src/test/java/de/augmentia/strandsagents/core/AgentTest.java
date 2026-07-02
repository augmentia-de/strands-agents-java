package de.augmentia.strandsagents.core;

import static org.assertj.core.api.Assertions.assertThat;

import de.augmentia.strandsagents.core.conversation.SlidingWindowConversationManager;
import de.augmentia.strandsagents.model.agent.StopReason;

import de.augmentia.strandsagents.model.structured.StructuredOutputConfig;
import de.augmentia.strandsagents.core.sessions.FileSessionManager;
import de.augmentia.strandsagents.tools.AgentTool;
import de.augmentia.strandsagents.tools.ToolResult;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.output.FinishReason;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import de.augmentia.strandsagents.interceptor.pipeline.AgentHook;
import de.augmentia.strandsagents.interceptor.pipeline.HookContexts;
import de.augmentia.strandsagents.interceptor.pipeline.HookResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentTest {

    @Test
    void shouldReturnValidAgentResult() {
        var agent = new Agent(new MockChatModel());
        var result = agent.execute("Hallo Welt");

        assertThat(result).isNotNull();
        assertThat(result.finalAnswer()).isNotEmpty();
        assertThat(result.stopReason()).isEqualTo(StopReason.COMPLETED);
        assertThat(result.sessionId()).isNotEmpty();
    }

    @Test
    void shouldMaintainConversationHistory() {
        var agent = new Agent(new MockChatModel("Antwort: %s"));

        agent.execute("Erste Frage");
        agent.execute("Zweite Frage");

        var memory = agent.getChatMemory();
        assertThat(memory.messages()).hasSize(4);
        assertThat(((dev.langchain4j.data.message.UserMessage) memory.messages().get(0)).singleText()).isEqualTo("Erste Frage");
    }

    // ── P1: System Prompt Modification via Hooks ─────────────────────

    @Test
    void pluginHooksCanModifySystemPrompt() {
        var model = new RecordingChatModel();
        var agent = new Agent(model);
        agent.setSystemPrompt("Base prompt");
        agent.addHook(new AgentHook() {
            @Override public String name() { return "test-hook"; }
            @Override public HookResult beforeModelCall(HookContexts.BeforeModelCallContext ctx) {
                ctx.systemPrompt().append("\n<!-- extra -->");
                return new HookResult.Continue();
            }
        });

        agent.execute("test");

        assertThat(agent.getSystemPrompt()).isEqualTo("Base prompt");
        var request = model.lastRequest();
        assertThat(request).isNotNull();
        var firstMsg = request.messages().get(0);
        assertThat(((dev.langchain4j.data.message.SystemMessage) firstMsg).text()).contains("<!-- extra -->");
    }

    @Test
    void multiplePluginHooksAreAppliedInOrder() {
        var model = new RecordingChatModel();
        var agent = new Agent(model);
        agent.setSystemPrompt("Start");
        agent.addHook(new AgentHook() {
            @Override public String name() { return "hook-a"; }
            @Override public HookResult beforeModelCall(HookContexts.BeforeModelCallContext ctx) {
                ctx.systemPrompt().append(" A");
                return new HookResult.Continue();
            }
        });
        agent.addHook(new AgentHook() {
            @Override public String name() { return "hook-b"; }
            @Override public HookResult beforeModelCall(HookContexts.BeforeModelCallContext ctx) {
                ctx.systemPrompt().append(" B");
                return new HookResult.Continue();
            }
        });

        agent.execute("test");

        assertThat(agent.getSystemPrompt()).isEqualTo("Start");
        var request = model.lastRequest();
        assertThat(request).isNotNull();
        var firstMsg = request.messages().get(0);
        assertThat(((dev.langchain4j.data.message.SystemMessage) firstMsg).text()).isEqualTo("Start A B");
    }

    @Test
    void systemPromptModificationViaBeforeInvocationEvent() {
        var captured = new StringBuilder[]{new StringBuilder()};
        var model = new RecordingChatModel();
        var agent = new Agent(model);
        agent.setSystemPrompt("Original");
        agent.setEventListener(event -> {
            if (event instanceof de.augmentia.strandsagents.model.event.BeforeInvocationEvent bie) {
                captured[0] = bie.systemPrompt();
                bie.systemPrompt().append(" + event-added");
            }
        });

        agent.execute("test");

        assertThat(captured[0].toString()).contains("Original");
        assertThat(captured[0].toString()).contains("event-added");
        assertThat(agent.getSystemPrompt()).isEqualTo("Original");
        var request = model.lastRequest();
        assertThat(request).isNotNull();
        var firstMsg = request.messages().get(0);
        assertThat(((dev.langchain4j.data.message.SystemMessage) firstMsg).text()).contains("event-added");
    }

    // ── P1: Conversation Manager Pruning Integration ────────────────

    @Test
    void slidingWindowPrunesOldMessages() {
        var manager = new SlidingWindowConversationManager(2);
        var agent = new Agent(new MockChatModel("R: %s"),
            new ToolRegistry(), new DefaultToolExecutor(), manager);

        agent.execute("Frage 1");
        agent.execute("Frage 2");
        agent.execute("Frage 3");

        var memory = agent.getChatMemory();
        assertThat(memory.messages()).hasSize(2);
        assertThat(memory.messages().get(0)).isInstanceOf(dev.langchain4j.data.message.UserMessage.class);
        assertThat(((dev.langchain4j.data.message.UserMessage) memory.messages().get(0)).singleText()).isEqualTo("Frage 3");
    }

    @Test
    void slidingWindowWithSessionPreservesRecentOnly() {
        var manager = new SlidingWindowConversationManager(1);
        var agent = new Agent(new MockChatModel("R: %s"),
            new ToolRegistry(), new DefaultToolExecutor(), manager);

        agent.execute("first");

        agent.execute("second");

        var memory = agent.getChatMemory();
        assertThat(memory.messages()).hasSize(1);
        assertThat(memory.messages().get(0)).isInstanceOf(dev.langchain4j.data.message.AiMessage.class);
    }

    // ── P1: Session Persistence Roundtrip ────────────────────────────

    @Test
    void sessionPersistenceRoundTrip(@TempDir Path tempDir) {
        var sessionManager = new FileSessionManager(tempDir);
        var session = sessionManager.createSession("agent", Map.of());
        var agent = new Agent(new MockChatModel("R: %s"),
            new ToolRegistry(), new DefaultToolExecutor(), null, sessionManager);

        var result = agent.execute(session.sessionId(), "Hallo");

        assertThat(result.stopReason()).isEqualTo(StopReason.COMPLETED);

        var loaded = sessionManager.loadSession(session.sessionId());
        assertThat(loaded).isPresent();
        assertThat(loaded.get().messages()).isNotEmpty();
        assertThat(loaded.get().messages().get(0).content()).isEqualTo("Hallo");
    }

    @Test
    void sessionPersistenceAccumulatesMessages(@TempDir Path tempDir) {
        var sessionManager = new FileSessionManager(tempDir);
        var session = sessionManager.createSession("agent", Map.of());
        var agent = new Agent(new MockChatModel("R: %s"),
            new ToolRegistry(), new DefaultToolExecutor(), null, sessionManager);

        agent.execute(session.sessionId(), "Erste");
        agent.execute(session.sessionId(), "Zweite");

        var loaded = sessionManager.loadSession(session.sessionId());
        assertThat(loaded).isPresent();
        var msgs = loaded.get().messages();
        assertThat(msgs).hasSize(4);
    }

    @Test
    void sessionWithoutManagerUsesDefaultSession() {
        var agent = new Agent(new MockChatModel());
        var result = agent.execute("hello");
        assertThat(result.sessionId()).isNotNull();
        assertThat(result.stopReason()).isEqualTo(StopReason.COMPLETED);
    }

    // ── P1: Structured Output Force Retry ────────────────────────────

    @Test
    void structuredOutputParseFailureTriggersForceRetry() {
        var model = new StagedChatModel()
            .thenReturn("not valid json")
            .thenReturn("{\"key\": \"value\"}");
        var agent = new Agent(model);
        agent.setStructuredOutputConfig(StructuredOutputConfig.dynamicSchema(
            "{\"type\": \"object\", \"properties\": {\"key\": {\"type\": \"string\"}}}"));

        var result = agent.execute("return json");

        assertThat(result.stopReason()).isEqualTo(StopReason.COMPLETED);
        assertThat(result.structuredOutput()).isEqualTo("{\"key\": \"value\"}");
        assertThat(model.callCount()).isEqualTo(2);
    }

    @Test
    void structuredOutputWithoutErrorsReturnsDirectly() {
        var model = new StagedChatModel()
            .thenReturn("{\"result\": \"ok\"}");
        var agent = new Agent(model);
        agent.setStructuredOutputConfig(StructuredOutputConfig.dynamicSchema(
            "{\"type\": \"object\", \"properties\": {\"result\": {\"type\": \"string\"}}}"));

        var result = agent.execute("valid json");

        assertThat(result.stopReason()).isEqualTo(StopReason.COMPLETED);
        assertThat(result.structuredOutput()).isEqualTo("{\"result\": \"ok\"}");
        assertThat(model.callCount()).isEqualTo(1);
    }

    @Test
    void structuredOutputForceRetryAlsoFailsGracefully() {
        var model = new StagedChatModel()
            .thenReturn("not json")
            .thenReturn("still not json");
        var agent = new Agent(model);
        agent.setStructuredOutputConfig(StructuredOutputConfig.dynamicSchema(
            "{\"type\": \"object\", \"properties\": {}}"));

        var result = agent.execute("bad data");

        assertThat(result.stopReason()).isEqualTo(StopReason.COMPLETED);
        assertThat(model.callCount()).isEqualTo(2);
    }

    // ── P1: callWithResilience + TokenRecovery ───────────────────────

    @Test
    void tokenLimitErrorTriggersRecovery() {
        var model = new StagedChatModel()
            .thenThrow(new RuntimeException("maximum context length exceeded"))
            .thenReturn("recovered response");
        var agent = new Agent(model);

        var result = agent.execute("long message");

        assertThat(result.finalAnswer()).contains("recovered");
        assertThat(result.stopReason()).isEqualTo(StopReason.COMPLETED);
        assertThat(model.callCount()).isEqualTo(2);
    }

    @Test
    void repeatedTokenErrorsExhaustRecovery() {
        var model = new StagedChatModel()
            .thenThrow(new RuntimeException("context_length_exceeded"))
            .thenThrow(new RuntimeException("context_length_exceeded"))
            .thenThrow(new RuntimeException("context_length_exceeded"))
            .thenThrow(new RuntimeException("context_length_exceeded"));
        var agent = new Agent(model);

        var result = agent.execute("too long");

        assertThat(result.stopReason()).isEqualTo(StopReason.ERROR);
    }

    // ── P1: buildRequest via RecordingChatModel ──────────────────────

    @Test
    void buildRequestIncludesSystemPrompt() {
        var model = new RecordingChatModel();
        var agent = new Agent(model);
        agent.setSystemPrompt("Test System Prompt");

        agent.execute("hello");

        var request = model.lastRequest();
        assertThat(request).isNotNull();
        var msgs = request.messages();
        assertThat(msgs.get(0)).isInstanceOf(dev.langchain4j.data.message.SystemMessage.class);
        assertThat(((dev.langchain4j.data.message.SystemMessage) msgs.get(0)).text()).isEqualTo("Test System Prompt");
    }

    @Test
    void buildRequestIncludesTools() {
        var model = new RecordingChatModel();
        var registry = new ToolRegistry();
        registry.register(new AgentTool<Object>() {
            @Override public String name() { return "my-tool"; }
            @Override public String description() { return "A tool"; }
            @Override public Class<Object> parameterType() { return Object.class; }
            @Override public com.fasterxml.jackson.databind.node.ObjectNode parameterSchema() {
                return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            }
            @Override
            public ToolResult execute(
                    String id, Object p, java.util.concurrent.atomic.AtomicBoolean a,
                    java.util.function.Consumer<ToolResult> u) {
                return ToolResult.success("ok");
            }
        });
        var agent = new Agent(model, registry, new DefaultToolExecutor());

        agent.execute("use tool");

        var request = model.lastRequest();
        assertThat(request.toolSpecifications()).isNotEmpty();
        assertThat(request.toolSpecifications().get(0).name()).isEqualTo("my-tool");
    }

    // ── P1: Dynamic Tool Change Notifications ────────────────────────

    @Test
    void toolChangeDetectedWhenToolAddedBetweenTurns() {
        var model = new RecordingChatModel();
        var registry = new ToolRegistry();
        var tool = new AgentTool<Object>() {
            @Override public String name() { return "tool-a"; }
            @Override public String description() { return "Tool A"; }
            @Override public Class<Object> parameterType() { return Object.class; }
            @Override public com.fasterxml.jackson.databind.node.ObjectNode parameterSchema() {
                return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            }
            @Override
            public ToolResult execute(
                    String id, Object p, java.util.concurrent.atomic.AtomicBoolean a,
                    java.util.function.Consumer<ToolResult> u) {
                return ToolResult.success("ok");
            }
        };
        registry.register(tool);
        var agent = new Agent(model, registry, new DefaultToolExecutor());

        // First turn with tool-a only
        agent.execute("first turn");
        var messagesAfterFirst = agent.getChatMemory().messages();
        var noticesAfterFirst = messagesAfterFirst.stream()
            .filter(m -> m instanceof dev.langchain4j.data.message.SystemMessage)
            .filter(m -> ((dev.langchain4j.data.message.SystemMessage) m).text().contains("tools have been updated"))
            .count();
        assertThat(noticesAfterFirst).as("no tool change notice on first turn").isZero();

        // Add a second tool mid-session
        var toolB = new AgentTool<Object>() {
            @Override public String name() { return "tool-b"; }
            @Override public String description() { return "Tool B"; }
            @Override public Class<Object> parameterType() { return Object.class; }
            @Override public com.fasterxml.jackson.databind.node.ObjectNode parameterSchema() {
                return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            }
            @Override
            public ToolResult execute(
                    String id, Object p, java.util.concurrent.atomic.AtomicBoolean a,
                    java.util.function.Consumer<ToolResult> u) {
                return ToolResult.success("ok");
            }
        };
        agent.addTool(toolB);

        // Second turn — tool change should be detected
        agent.execute("second turn");
        var messagesAfterSecond = agent.getChatMemory().messages();
        var noticesAfterSecond = messagesAfterSecond.stream()
            .filter(m -> m instanceof dev.langchain4j.data.message.SystemMessage)
            .filter(m -> ((dev.langchain4j.data.message.SystemMessage) m).text().contains("tools have been updated"))
            .toList();
        assertThat(noticesAfterSecond).hasSize(1);
        assertThat(((dev.langchain4j.data.message.SystemMessage) noticesAfterSecond.get(0)).text())
            .contains("tool-b");
    }

    @Test
    void toolChangeDetectedWhenToolRemovedBetweenTurns() {
        var model = new RecordingChatModel();
        var registry = new ToolRegistry();
        var tool = new AgentTool<Object>() {
            @Override public String name() { return "tool-a"; }
            @Override public String description() { return "Tool A"; }
            @Override public Class<Object> parameterType() { return Object.class; }
            @Override public com.fasterxml.jackson.databind.node.ObjectNode parameterSchema() {
                return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            }
            @Override
            public ToolResult execute(
                    String id, Object p, java.util.concurrent.atomic.AtomicBoolean a,
                    java.util.function.Consumer<ToolResult> u) {
                return ToolResult.success("ok");
            }
        };
        registry.register(tool);
        var agent = new Agent(model, registry, new DefaultToolExecutor());

        agent.execute("first turn");

        agent.removeTool("tool-a");

        agent.execute("second turn");
        var messages = agent.getChatMemory().messages();
        var notices = messages.stream()
            .filter(m -> m instanceof dev.langchain4j.data.message.SystemMessage)
            .filter(m -> ((dev.langchain4j.data.message.SystemMessage) m).text().contains("tools have been updated"))
            .toList();
        assertThat(notices).hasSize(1);
        assertThat(((dev.langchain4j.data.message.SystemMessage) notices.get(0)).text())
            .contains("Removed: tool-a");
    }

    // ── Helper: RecordingChatModel ───────────────────────────────────

    static class RecordingChatModel implements ChatModel {
        final List<ChatRequest> requests = new CopyOnWriteArrayList<>();

        @Override
        public ChatResponse chat(ChatRequest request) {
            requests.add(request);
            var msgs = request.messages();
            String text;
            if (msgs.isEmpty()) {
                text = "";
            } else {
                var last = msgs.get(msgs.size() - 1);
                text = last instanceof UserMessage um ? um.singleText() : last.toString();
            }
            return ChatResponse.builder()
                .aiMessage(AiMessage.from("Echo: " + text))
                .tokenUsage(new TokenUsage(10, text.length()))
                .finishReason(FinishReason.STOP)
                .build();
        }

        ChatRequest lastRequest() {
            return requests.isEmpty() ? null : requests.get(requests.size() - 1);
        }

        int callCount() { return requests.size(); }
    }

    // ── Helper: StagedChatModel for multi-call scenarios ────────────

    static class StagedChatModel implements ChatModel {
        private final List<Object> stages = new ArrayList<>();
        private int calls = 0;

        StagedChatModel thenReturn(String text) {
            stages.add(text);
            return this;
        }

        StagedChatModel thenThrow(RuntimeException e) {
            stages.add(e);
            return this;
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            int idx = calls++;
            if (idx >= stages.size()) {
                return defaultResponse(request, "default");
            }
            var stage = stages.get(idx);
            if (stage instanceof RuntimeException re) throw re;
            return defaultResponse(request, (String) stage);
        }

        private ChatResponse defaultResponse(ChatRequest request, String text) {
            return ChatResponse.builder()
                .aiMessage(AiMessage.from(text))
                .tokenUsage(new TokenUsage(10, text.length()))
                .finishReason(FinishReason.STOP)
                .build();
        }

        int callCount() { return calls; }
    }
}
