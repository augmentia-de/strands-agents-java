package de.augmentia.strandsagents.interceptor.telemetry;

import de.augmentia.strandsagents.core.AgentEventListener;
import de.augmentia.strandsagents.model.event.*;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TracingHook implements AgentEventListener {

    private final AgentTracing tracing;
    private final Map<String, Span> spans = new ConcurrentHashMap<>();

    public TracingHook(OpenTelemetry openTelemetry) {
        this.tracing = new AgentTracing(openTelemetry);
    }

    public TracingHook(AgentTracing tracing) {
        this.tracing = tracing;
    }

    @Override
    public void onEvent(AgentEvent event) {
        switch (event) {
            case AgentStartedEvent e -> {
                var span = tracing.startAgentSpan(e.sessionId(), e.initialPrompt());
                spans.put(e.sessionId(), span);
            }
            case ModelRequestedEvent e -> {
                var span = tracing.startLlmSpan(e.sessionId(), e.promptHistory().size());
                spans.put(e.sessionId() + ":llm", span);
            }
            case ToolExecutionStartedEvent e -> {
                var span = tracing.startToolSpan(e.sessionId(), e.toolCall().toolName());
                spans.put(e.sessionId() + ":tool:" + e.toolCall().toolName(), span);
            }
            case ToolExecutionFinishedEvent e -> {
                tracing.endToolSpan(e.sessionId(), e.result().toolName(), e.result().isError());
            }
            case BeforeInvocationEvent e -> {}
            case AfterInvocationEvent e -> {}
            case AgentStateChangedEvent e -> {}
            case TokenEvent e -> {}
            case AgentFinishedEvent e -> {
                tracing.endLlmSpan(e.sessionId(), 0, 0);
                tracing.endAgentSpan(e.sessionId(), e.finalAnswer());
            }
        }
    }

    public AgentTracing getTracing() {
        return tracing;
    }
}
