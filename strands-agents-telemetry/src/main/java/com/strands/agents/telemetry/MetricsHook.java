package com.strands.agents.telemetry;

import com.strands.agents.core.AgentEventListener;
import com.strands.agents.core.model.event.*;
import io.micrometer.core.instrument.MeterRegistry;

public class MetricsHook implements AgentEventListener {

    private final AgentMetrics metrics;

    public MetricsHook(MeterRegistry registry) {
        this.metrics = new AgentMetrics(registry);
    }

    public MetricsHook(AgentMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public void onEvent(AgentEvent event) {
        switch (event) {
            case ModelRequestedEvent e -> {
                metrics.getLlmCalls().increment();
            }
            case ToolExecutionStartedEvent e -> {
                metrics.getToolExecutions().increment();
            }
            case TokenEvent e -> {}
            case AgentFinishedEvent e -> {
                // execution duration is measured externally
            }
            default -> {}
        }
    }

    public AgentMetrics getMetrics() {
        return metrics;
    }
}
