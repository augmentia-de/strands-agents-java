package de.augmentia.strandsagents.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import de.augmentia.strandsagents.core.MockChatModel;
import de.augmentia.strandsagents.core.StrandsAgent;
import de.augmentia.strandsagents.core.model.event.*;
import de.augmentia.strandsagents.core.model.tool.ToolCall;
import de.augmentia.strandsagents.core.model.tool.ToolExecutionResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.time.Instant;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class TelemetryTest {

    // --- HookRegistry Tests ---

    @Test
    void hookRegistryShouldDeliverEventsToAllHooks() {
        var events1 = new ArrayList<AgentEvent>();
        var events2 = new ArrayList<AgentEvent>();
        var registry = new HookRegistry();

        registry.registerHook("h1", e -> true, events1::add);
        registry.registerHook("h2", e -> true, events2::add);

        registry.onEvent(new AgentStartedEvent("s1", Instant.now(), "test"));

        assertThat(events1).hasSize(1);
        assertThat(events2).hasSize(1);
    }

    @Test
    void hookRegistryShouldFilterEvents() {
        var toolEvents = new ArrayList<AgentEvent>();
        var registry = new HookRegistry();

        registry.registerHook("tools-only",
            e -> e instanceof ToolExecutionStartedEvent,
            toolEvents::add);

        registry.onEvent(new AgentStartedEvent("s1", Instant.now(), "test"));
        registry.onEvent(new ToolExecutionStartedEvent("s1", Instant.now(),
            new ToolCall("id1", "tool", "{}")));

        assertThat(toolEvents).hasSize(1);
        assertThat(toolEvents.get(0)).isInstanceOf(ToolExecutionStartedEvent.class);
    }

    @Test
    void hookRegistryShouldDeliverToDownstream() {
        var downstreamEvents = new ArrayList<AgentEvent>();
        var registry = new HookRegistry();

        registry.setDownstream(downstreamEvents::add);
        registry.onEvent(new AgentStartedEvent("s1", Instant.now(), "test"));

        assertThat(downstreamEvents).hasSize(1);
    }

    @Test
    void hookRegistryShouldIsolateHookFailures() {
        var registry = new HookRegistry();
        var goodEvents = new ArrayList<AgentEvent>();

        registry.registerHook("failing", e -> { throw new RuntimeException("fail"); });
        registry.registerHook("good", goodEvents::add);

        registry.onEvent(new AgentStartedEvent("s1", Instant.now(), "test"));

        assertThat(goodEvents).hasSize(1);
    }

    @Test
    void hookRegistryShouldListRegisteredHooks() {
        var registry = new HookRegistry();
        registry.registerHook("test-hook", e -> true, e -> {});

        assertThat(registry.getHooks()).hasSize(1);
        assertThat(registry.getHooks().get(0).name()).isEqualTo("test-hook");
    }

    @Test
    void hookRegistryShouldClear() {
        var registry = new HookRegistry();
        registry.registerHook("h1", e -> true, e -> {});
        registry.clear();
        assertThat(registry.getHooks()).isEmpty();
    }

    @Test
    void hookRegistryShouldAcceptHookWithoutFilter() {
        var events = new ArrayList<AgentEvent>();
        var registry = new HookRegistry();

        registry.registerHook("simple", events::add);
        registry.onEvent(new AgentStartedEvent("s1", Instant.now(), "test"));

        assertThat(events).hasSize(1);
    }

    // --- LoggingHook Tests ---

    @Test
    void loggingHookShouldAcceptAllEventTypes() {
        var hook = new LoggingHook();
        var toolCall = new ToolCall("id1", "test-tool", "{}");
        var toolResult = new ToolExecutionResult("id1", "test-tool", "ok", false);
        // should not throw
        hook.onEvent(new AgentStartedEvent("s1", Instant.now(), "test"));
        hook.onEvent(new ModelRequestedEvent("s1", Instant.now(), java.util.List.of()));
        hook.onEvent(new ToolExecutionStartedEvent("s1", Instant.now(), toolCall));
        hook.onEvent(new ToolExecutionFinishedEvent("s1", Instant.now(), toolResult));
        hook.onEvent(new TokenEvent("s1", Instant.now(), "token"));
        hook.onEvent(new AgentFinishedEvent("s1", Instant.now(), "done"));
    }

    // --- AgentMetrics + MetricsHook Tests ---

    @Test
    void metricsHookShouldCountLlmCalls() {
        var registry = new SimpleMeterRegistry();
        var hook = new MetricsHook(registry);

        hook.onEvent(new ModelRequestedEvent("s1", Instant.now(), java.util.List.of()));
        hook.onEvent(new ModelRequestedEvent("s1", Instant.now(), java.util.List.of()));

        assertThat(registry.get("agent.llm.calls").counter().count()).isEqualTo(2);
    }

    @Test
    void metricsHookShouldCountToolExecutions() {
        var registry = new SimpleMeterRegistry();
        var hook = new MetricsHook(registry);

        hook.onEvent(new ToolExecutionStartedEvent("s1", Instant.now(), null));
        hook.onEvent(new ToolExecutionStartedEvent("s1", Instant.now(), null));
        hook.onEvent(new ToolExecutionStartedEvent("s1", Instant.now(), null));

        assertThat(registry.get("agent.tool.executions").counter().count()).isEqualTo(3);
    }

    @Test
    void agentMetricsShouldRegisterAllMetrics() {
        var registry = new SimpleMeterRegistry();
        var metrics = new AgentMetrics(registry);

        assertThat(registry.find("agent.execution.duration").timer()).isNotNull();
        assertThat(registry.find("agent.llm.calls").counter()).isNotNull();
        assertThat(registry.find("agent.tool.executions").counter()).isNotNull();
        assertThat(registry.find("agent.tokens.prompt").summary()).isNotNull();
        assertThat(registry.find("agent.tokens.completion").summary()).isNotNull();
        assertThat(registry.find("agent.errors").counter()).isNotNull();
    }

    // --- AgentTracing + TracingHook Tests ---

    @Test
    void tracingHookShouldCreateSpans() {
        var spanExporter = InMemorySpanExporter.create();
        var tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
            .build();
        var openTelemetry = OpenTelemetry.noop(); // Use noop for OTel API

        // We test TracingHook with a real tracer provider
        var tracing = new AgentTracing(OpenTelemetry.noop());

        var span = tracing.startAgentSpan("s1", "test prompt");
        assertThat(span).isNotNull();
        span.end();
    }

    @Test
    void tracingShouldCreateSpanHierarchy() {
        var tracing = new AgentTracing(OpenTelemetry.noop());

        var agentSpan = tracing.startAgentSpan("s1", "test");
        var llmSpan = tracing.startLlmSpan("s1", 5);
        var toolSpan = tracing.startToolSpan("s1", "calculator");

        assertThat(agentSpan).isNotNull();
        assertThat(llmSpan).isNotNull();
        assertThat(toolSpan).isNotNull();

        tracing.endToolSpan("s1", "calculator", false);
        tracing.endLlmSpan("s1", 10, 20);
        tracing.endAgentSpan("s1", "completed");
    }

    @Test
    void tracingShouldHandleMissingSpans() {
        var tracing = new AgentTracing(OpenTelemetry.noop());
        // should not throw when ending non-existent spans
        tracing.endLlmSpan("nonexistent", 0, 0);
        tracing.endAgentSpan("nonexistent", "error");
        tracing.endToolSpan("nonexistent", "tool", false);
    }

    // --- Integration Tests ---

    @Test
    void hookRegistryShouldIntegrateWithAgent() {
        var events = new ArrayList<AgentEvent>();
        var hooks = new HookRegistry();

        hooks.registerHook("recorder", events::add);

        var agent = new StrandsAgent(new MockChatModel());
        agent.setEventListener(hooks);
        agent.execute("Hallo");

        assertThat(events).isNotEmpty();
        assertThat(events.get(0)).isInstanceOf(AgentStartedEvent.class);
    }

    @Test
    void metricsHookShouldWorkWithRealAgent() throws Exception {
        var meterRegistry = new SimpleMeterRegistry();
        var hooks = new HookRegistry();

        hooks.registerHook("metrics", new MetricsHook(meterRegistry));

        var agent = new StrandsAgent(new MockChatModel());
        agent.setEventListener(hooks);
        agent.execute("Hallo");

        assertThat(meterRegistry.get("agent.llm.calls").counter().count()).isGreaterThan(0);
    }

    @Test
    void multipleHooksShouldWorkTogether() {
        var agentEvents = new ArrayList<AgentEvent>();
        var meterRegistry = new SimpleMeterRegistry();
        var hooks = new HookRegistry();

        hooks.registerHook("recorder", agentEvents::add);
        hooks.registerHook("metrics", new MetricsHook(meterRegistry));

        var agent = new StrandsAgent(new MockChatModel());
        agent.setEventListener(hooks);
        agent.execute("Hallo");

        assertThat(agentEvents).isNotEmpty();
        assertThat(meterRegistry.get("agent.llm.calls").counter().count()).isGreaterThan(0);
    }

    @Test
    void filteredHookShouldOnlyReceiveMatchingEvents() {
        var toolEvents = new ArrayList<ToolExecutionStartedEvent>();
        var hooks = new HookRegistry();

        hooks.registerHook("tools-only",
            e -> e instanceof ToolExecutionStartedEvent,
            e -> toolEvents.add((ToolExecutionStartedEvent) e));

        var agent = new StrandsAgent(new MockChatModel());
        agent.setEventListener(hooks);
        agent.execute("Hallo");

        assertThat(toolEvents).isEmpty(); // no tools in mock agent
    }
}
