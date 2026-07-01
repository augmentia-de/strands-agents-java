package de.augmentia.strandsagents.examples.feature;

import de.augmentia.strandsagents.features.gate.Gate;
import de.augmentia.strandsagents.features.gate.GateEvaluator;
import de.augmentia.strandsagents.features.gate.GateType;
import de.augmentia.strandsagents.features.workflow.StepStatus;
import de.augmentia.strandsagents.features.workflow.WorkCoordinator;
import de.augmentia.strandsagents.features.workflow.WorkflowDefinition;
import de.augmentia.strandsagents.features.workflow.WorkflowStep;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class GatedWorkflowDemo {

    public static void main(String[] args) throws Exception {
        run();
        System.out.println("=== GatedWorkflowDemo PASSED ===");
    }

    public static void run() throws Exception {
        // ── 1. @Gate annotation + GateEvaluator ──
        var evaluator = new CooldownGateEvaluator();
        Method gatedMethod = GatedService.class.getMethod("expensiveOperation", String.class);
        Gate gate = gatedMethod.getAnnotation(Gate.class);

        assert gate != null : "@Gate annotation present";
        assert gate.type() == GateType.COOLDOWN : "cooldown type";
        assert gate.duration().equals("30s") : "duration 30s";

        // First call: should be open
        boolean first = evaluator.isOpen(gatedMethod, gate);
        System.out.println("  [Gate] first call isOpen=" + first);
        assert first : "first call should be open";
        evaluator.recordExecution(gatedMethod, gate, true);

        // Second call: should be blocked by cooldown
        boolean second = evaluator.isOpen(gatedMethod, gate);
        System.out.println("  [Gate] second call isOpen=" + second);
        assert !second : "second call should be blocked by cooldown";

        // ── 2. WorkflowDefinition + WorkflowStep ──
        var step1 = new WorkflowStep(
            "greet", "assistant", "llm", "Greet the user",
            List.of("respond"), Map.of("input", "userName"), Map.of("result", "greeting")
        );
        var step2 = new WorkflowStep(
            "respond", "assistant", "llm", "Generate response",
            List.of(), Map.of("greeting", "result"), Map.of("output", "final")
        );
        var workflow = new WorkflowDefinition(
            "wf-demo", "Demo Workflow", "A simple two-step workflow",
            "greet", List.of(step1, step2), Map.of("env", "test")
        );

        System.out.println("  [Workflow] id=" + workflow.id());
        System.out.println("  [Workflow] name=" + workflow.name());
        System.out.println("  [Workflow] startStep=" + workflow.startStep());
        System.out.println("  [Workflow] steps=" + workflow.steps().size());
        assert workflow.id().equals("wf-demo") : "workflow id";
        assert workflow.steps().size() == 2 : "two steps";
        assert workflow.startStep().equals("greet") : "start step is greet";
        assert step1.next().contains("respond") : "step1 leads to step2";
        assert step2.next().isEmpty() : "step2 is terminal";

        // ── 3. WorkCoordinator: async dispatch + collect ──
        var coordinator = new DemoWorkCoordinator();
        var future1 = coordinator.dispatch(step1, Map.of("userName", "Alice"));
        var result1 = future1.get();
        System.out.println("  [Coordinator] step1 workId=" + result1.workId()
            + " status=" + result1.status()
            + " output=" + result1.outputs());
        assert result1.status() == StepStatus.COMPLETED : "step1 completed";
        assert result1.outputs().containsKey("greeting") : "step1 produced greeting";

        var future2 = coordinator.dispatch(step2, Map.of("greeting", result1.outputs().get("greeting")));
        var result2 = future2.get();
        System.out.println("  [Coordinator] step2 workId=" + result2.workId()
            + " status=" + result2.status()
            + " output=" + result2.outputs());
        assert result2.status() == StepStatus.COMPLETED : "step2 completed";

        // ── 4. Heartbeat listener ──
        var hbListener = coordinator.heartbeatListener();
        if (hbListener != null) {
            hbListener.onHeartbeat("work-1", System.currentTimeMillis());
            System.out.println("  [Coordinator] heartbeat sent");
        } else {
            System.out.println("  [Coordinator] no heartbeat listener (expected for demo)");
        }
    }

    // -- Gated plugin method --
    static class GatedService {
        @Gate(type = GateType.COOLDOWN, duration = "30s")
        public String expensiveOperation(String input) {
            return "Processed: " + input;
        }
    }

    // -- Simple cooldown evaluator --
    static class CooldownGateEvaluator implements GateEvaluator {
        private final Map<Method, Long> lastExecution = new ConcurrentHashMap<>();

        @Override
        public boolean isOpen(Method pluginMethod, Gate gate) {
            if (gate.type() != GateType.COOLDOWN) return true;
            long last = lastExecution.getOrDefault(pluginMethod, 0L);
            long elapsed = System.currentTimeMillis() - last;
            long cooldownMs = parseDuration(gate.duration());
            return elapsed >= cooldownMs;
        }

        @Override
        public void recordExecution(Method pluginMethod, Gate gate, boolean success) {
            lastExecution.put(pluginMethod, System.currentTimeMillis());
        }

        private long parseDuration(String duration) {
            if (duration.endsWith("s")) return Long.parseLong(duration.replace("s", "")) * 1000L;
            if (duration.endsWith("ms")) return Long.parseLong(duration.replace("ms", ""));
            if (duration.endsWith("m")) return Long.parseLong(duration.replace("m", "")) * 60_000L;
            return 30_000L;
        }
    }

    // -- Demo WorkCoordinator: sync dispatch wrapped in CompletableFuture --
    static class DemoWorkCoordinator implements WorkCoordinator {
        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public CompletableFuture<StepResult> dispatch(WorkflowStep step, Map<String, Object> context) {
            var workId = "work-" + counter.incrementAndGet();
            var outputs = new java.util.LinkedHashMap<String, Object>();
            outputs.put(step.id() + ".output", "Executed: " + step.description() + " with " + context);
            if (step.id().equals("greet")) {
                outputs.put("greeting", "Hello, " + context.getOrDefault("userName", "world") + "!");
            }
            var result = new StepResult(workId, step.id(), StepStatus.COMPLETED, outputs, null);
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public CompletableFuture<StepResult> collect(String workId) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("collect not used"));
        }
    }
}
