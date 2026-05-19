package de.augmentia.strandsagents.core;

import static org.assertj.core.api.Assertions.assertThat;

import de.augmentia.strandsagents.core.agent.MockChatModel;
import de.augmentia.strandsagents.core.agent.planning.*;
import de.augmentia.strandsagents.core.model.agent.AgentPhase;
import de.augmentia.strandsagents.core.model.agent.StopReason;
import de.augmentia.strandsagents.core.model.event.AgentStateChangedEvent;
import de.augmentia.strandsagents.core.tools.CalculatorTool;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class PlannerTest {

    @Test
    void planCanAdvanceAndComplete() {
        var steps = List.of(
            new Step("step-1", "Erster Schritt", "none", "Hallo"),
            new Step("step-2", "Zweiter Schritt", "none", "Welt")
        );
        var plan = new Plan("Test Goal", steps, 0, Map.of());

        assertThat(plan.isComplete()).isFalse();
        assertThat(plan.current()).isEqualTo(steps.get(0));

        plan = plan.advanceStep();
        assertThat(plan.currentStep()).isEqualTo(1);
        assertThat(plan.current()).isEqualTo(steps.get(1));

        plan = plan.advanceStep();
        assertThat(plan.isComplete()).isTrue();
        assertThat(plan.current()).isNull();
    }

    @Test
    void planStoresSharedContext() {
        var plan = new Plan("Test", List.of(), 0, Map.of("key", "value"));
        assertThat(plan.sharedContext()).containsEntry("key", "value");

        plan = plan.withSharedContext(Map.of("key", "new-value", "key2", "value2"));
        assertThat(plan.sharedContext()).hasSize(2);
    }

    @Test
    void stepResultFactoryMethods() {
        var ok = StepResult.ok("output");
        assertThat(ok.success()).isTrue();
        assertThat(ok.output()).isEqualTo("output");

        var okWithArtifacts = StepResult.ok("output", Map.of("key", "val"));
        assertThat(okWithArtifacts.artifacts()).containsEntry("key", "val");

        var fail = StepResult.fail("error message");
        assertThat(fail.success()).isFalse();
        assertThat(fail.error()).isEqualTo("error message");
    }

    @Test
    void stepConstructorVariants() {
        var step1 = new Step("s1", "desc", "tool", "args");
        assertThat(step1.dependsOn()).isEmpty();
        assertThat(step1.optional()).isFalse();

        var step2 = new Step("s2", "desc", "tool", "args", true);
        assertThat(step2.optional()).isTrue();

        var step3 = new Step("s3", "desc", "tool", "args", List.of("s1"), false);
        assertThat(step3.dependsOn()).containsExactly("s1");
    }

    @Test
    void planningAgentExecutesStepsViaMock() {
        var registry = new ToolRegistry();
        registry.register(new CalculatorTool());

        var model = new MockChatModel();
        var planner = new CoTPlanner(model);
        var agent = new PlanningAgent(model, registry, new ToolExecutor(), planner);

        var result = agent.executePlanned("Test: Berechne 2+2");

        assertThat(result).isNotNull();
        assertThat(result.finalAnswer()).isNotBlank();
    }

    @Test
    void planningAgentFiresStateChangeEvents() {
        var registry = new ToolRegistry();
        registry.register(new CalculatorTool());

        var model = new MockChatModel();
        var planner = new CoTPlanner(model);
        var agent = new PlanningAgent(model, registry, new ToolExecutor(), planner);

        var events = new CopyOnWriteArrayList<AgentStateChangedEvent>();
        agent.setEventListener(event -> {
            if (event instanceof AgentStateChangedEvent sce) {
                events.add(sce);
            }
        });

        var result = agent.executePlanned("Test-Goal");

        assertThat(events).isNotEmpty();
        assertThat(events.get(0).previousPhase()).isEqualTo(AgentPhase.IDLE);
        assertThat(events.get(0).currentPhase()).isEqualTo(AgentPhase.PLANNING);

        var lastEvent = events.get(events.size() - 1);
        assertThat(lastEvent.currentPhase()).isIn(AgentPhase.COMPLETED, AgentPhase.FAILED);
    }

    @Test
    void planningAgentTerminalStates() {
        var registry = new ToolRegistry();
        registry.register(new CalculatorTool());

        var model = new MockChatModel();
        var planner = new CoTPlanner(model, 1);
        var agent = new PlanningAgent(model, registry, new ToolExecutor(), planner);

        var result = agent.executePlanned("Ein einfaches Ziel");

        assertThat(result.stopReason()).isIn(StopReason.COMPLETED, StopReason.ERROR);
        assertThat(agent.getIterationCount()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void planWithToolExecution() {
        var registry = new ToolRegistry();
        registry.register(new CalculatorTool());

        var model = new MockChatModel();
        var planner = new CoTPlanner(model);
        var agent = new PlanningAgent(model, registry, new ToolExecutor(), planner);

        var steps = List.of(
            new Step("calc-1", "Berechne", "add", "{\"a\": 2, \"b\": 3}")
        );
        var plan = new Plan("Rechne", steps, 0, Map.of());

        var stepResult = planner.executeStep(plan, 0, agent.getToolExecutor(), registry);

        assertThat(stepResult.success()).isTrue();
        assertThat(stepResult.output()).isEqualTo("5");
    }

    @Test
    void coTPlannerCreatesPlan() {
        var model = new MockChatModel();
        var planner = new CoTPlanner(model);

        var plan = planner.createPlan("Ein Testziel", List.of("calculator"));

        assertThat(plan).isNotNull();
        assertThat(plan.goal()).isEqualTo("Ein Testziel");
        assertThat(plan.steps()).isNotEmpty();
    }

    @Test
    void coTPlannerRevisesPlan() {
        var model = new MockChatModel();
        var planner = new CoTPlanner(model);

        var plan = planner.createPlan("Test", List.of("calculator"));
        var failure = StepResult.fail("Simulierter Fehler");
        var revised = planner.revise(plan, failure, "Bitte korrigieren");

        assertThat(revised).isNotNull();
        assertThat(revised.goal()).isEqualTo("Test");
        assertThat(revised.steps()).isNotEmpty();
    }

    @Test
    void coTPlannerChecksCompletion() {
        var model = new MockChatModel();
        var planner = new CoTPlanner(model);

        var plan = new Plan("Test", List.of(), 0, Map.of());
        var complete = planner.isComplete(plan, "Ergebnis erreicht");

        assertThat(complete).isTrue();
    }

    @Test
    void planningAgentGetPhase() {
        var model = new MockChatModel();
        var planner = new CoTPlanner(model);
        var agent = new PlanningAgent(model, new ToolRegistry(), new ToolExecutor(), planner);

        assertThat(agent.getPhase()).isEqualTo(AgentPhase.IDLE);
        assertThat(agent.getPlanner()).isSameAs(planner);
        assertThat(agent.getErrorLog()).isEmpty();
    }
}
