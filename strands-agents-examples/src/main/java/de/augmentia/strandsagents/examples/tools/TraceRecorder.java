package de.augmentia.strandsagents.examples.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.augmentia.strandsagents.core.AgentEventListener;
import de.augmentia.strandsagents.features.pipeline.AgentHook;
import de.augmentia.strandsagents.features.pipeline.HookContexts;
import de.augmentia.strandsagents.features.pipeline.HookResult;
import de.augmentia.strandsagents.model.event.AfterInvocationEvent;
import de.augmentia.strandsagents.model.event.AgentEvent;
import de.augmentia.strandsagents.model.event.AgentFinishedEvent;
import de.augmentia.strandsagents.model.event.AgentStartedEvent;
import de.augmentia.strandsagents.model.event.AgentStateChangedEvent;
import de.augmentia.strandsagents.model.event.BeforeInvocationEvent;
import de.augmentia.strandsagents.model.event.ModelRequestedEvent;
import de.augmentia.strandsagents.model.event.TokenEvent;
import de.augmentia.strandsagents.model.event.ToolExecutionFinishedEvent;
import de.augmentia.strandsagents.model.event.ToolExecutionStartedEvent;
import java.util.ArrayList;
import java.util.List;

public class TraceRecorder implements AgentEventListener, AgentHook {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final List<String> trace = new ArrayList<>();
    private int toolCallCount;
    private int toolErrorCount;
    private int hookCancelCount;
    private int toolTimeoutCount;
    private int structuredOkCount;
    private int structuredErrorCount;

    @Override
    public String name() {
        return "trace-recorder";
    }

    public List<String> getTrace() {
        return List.copyOf(trace);
    }

    public int getToolCallCount() { return toolCallCount; }
    public int getToolErrorCount() { return toolErrorCount; }
    public int getHookCancelCount() { return hookCancelCount; }
    public int getToolTimeoutCount() { return toolTimeoutCount; }
    public int getStructuredOkCount() { return structuredOkCount; }
    public int getStructuredErrorCount() { return structuredErrorCount; }

    @Override
    public void onEvent(AgentEvent event) {
        switch (event) {
            case AgentStartedEvent e ->
                trace("[EVENT] Agent started — prompt: " + truncate(e.initialPrompt(), 80));
            case AgentFinishedEvent e ->
                trace("[EVENT] Agent finished — answer: " + truncate(e.finalAnswer(), 80));
            case BeforeInvocationEvent e ->
                trace("[EVENT] Before model invocation");
            case AfterInvocationEvent e ->
                trace("[EVENT] After model invocation — response: " + truncate(e.response(), 80));
            case ModelRequestedEvent ignored ->
                trace("[EVENT] Model requested");
            case ToolExecutionStartedEvent e ->
                trace("[EVENT] Tool started: " + e.toolCall().toolName());
            case ToolExecutionFinishedEvent e -> {
                toolCallCount++;
                var isTimeout = e.result().isError()
                    && (e.result().result().toLowerCase().contains("timeout")
                        || e.result().result().contains("Timeout"));
                if (isTimeout) {
                    toolTimeoutCount++;
                    trace("[EVENT] Tool TIMEOUT: " + e.result().toolName()
                        + " — " + truncate(e.result().result(), 80));
                } else if (e.result().isError()) {
                    toolErrorCount++;
                    trace("[EVENT] Tool ERROR: " + e.result().toolName()
                        + " — " + truncate(e.result().result(), 100));
                } else if (e.result().result().startsWith("Skipped: ")) {
                    trace("[EVENT] Tool SKIPPED: " + e.result().toolName()
                        + " — " + e.result().result());
                } else if (e.result().result().contains("timeout") || e.result().result().contains("timed out")) {
                    toolTimeoutCount++;
                    trace("[EVENT] Tool TIMEOUT: " + e.result().toolName());
                } else {
                    trace("[EVENT] Tool OK: " + e.result().toolName()
                        + " — " + truncate(e.result().result(), 80));
                }
            }
            case AgentStateChangedEvent ignored -> {}
            case TokenEvent ignored -> {}
        }
    }

    @Override
    public HookResult beforeToolCall(HookContexts.BeforeToolCallContext ctx) {
        trace("[HOOK] beforeToolCall: " + ctx.toolName()
            + " args=" + ctx.arguments());
        return new HookResult.Continue();
    }

    @Override
    public HookResult afterToolCall(HookContexts.AfterToolCallContext ctx, String result) {
        trace("[HOOK] afterToolCall: " + ctx.toolName()
            + " isError=" + ctx.isError()
            + " result=" + truncate(result, 80));
        return new HookResult.Continue();
    }

    @Override
    public HookResult beforeModelCall(HookContexts.BeforeModelCallContext ctx) {
        trace("[HOOK] beforeModelCall: " + ctx.messages().size() + " messages, "
            + ctx.tools().size() + " tools");
        return new HookResult.Continue();
    }

    @Override
    public HookResult afterModelCall(HookContexts.AfterModelCallContext ctx, String llmResponse) {
        trace("[HOOK] afterModelCall: " + ctx.inputTokens() + " in / "
            + ctx.outputTokens() + " out");

        // Check for structured output (JSON response)
        if (llmResponse != null && !llmResponse.isBlank()) {
            var trimmed = llmResponse.trim();
            if ((trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                try {
                    JSON.readTree(trimmed);
                    structuredOkCount++;
                    trace("[STRUCTURED] Valid JSON");
                } catch (Exception e) {
                    structuredErrorCount++;
                    trace("[STRUCTURED] Invalid JSON: " + e.getMessage());
                }
            }
        }

        return new HookResult.Continue();
    }

    @Override
    public HookResult beforeAgent(HookContexts.BeforeAgentContext ctx) {
        trace("[HOOK] beforeAgent");
        return new HookResult.Continue();
    }

    @Override
    public HookResult afterAgent(HookContexts.AfterAgentContext ctx, String response) {
        trace("[HOOK] afterAgent — finalAnswer: " + truncate(response, 80));
        return new HookResult.Continue();
    }

    public void printSummary() {
        System.out.println("\n=== Trace ===");
        for (var entry : trace) {
            System.out.println("  " + entry);
        }
        System.out.println("\n=== Summary ===");
        System.out.println("  Tool errors:     " + toolErrorCount);
        System.out.println("  Tool timeouts:   " + toolTimeoutCount);
        System.out.println("  Structured OK:   " + structuredOkCount);
        System.out.println("  Structured ERR:  " + structuredErrorCount);
        System.out.println("  Total trace entries: " + trace.size());
    }

    private void trace(String msg) {
        trace.add(msg);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "null";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
