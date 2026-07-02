package de.augmentia.strandsagents.core.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import de.augmentia.strandsagents.core.workflow.StepStatus;
import de.augmentia.strandsagents.core.workflow.WorkflowDefinition;
import de.augmentia.strandsagents.core.workflow.WorkflowStep;
import org.junit.jupiter.api.Test;

class WorkflowDefinitionTest {

    @Test
    void constructor_setsAllFields() {
        var step = new WorkflowStep("s1", "assistant", "llm", "Do stuff",
            List.of("s2"), Map.of(), Map.of("result", "output"));
        var def = new WorkflowDefinition("wf1", "My Workflow", "A test workflow",
            "s1", List.of(step), Map.of("env", "prod"));
        assertThat(def.id()).isEqualTo("wf1");
        assertThat(def.name()).isEqualTo("My Workflow");
        assertThat(def.description()).isEqualTo("A test workflow");
        assertThat(def.startStep()).isEqualTo("s1");
        assertThat(def.steps()).containsExactly(step);
        assertThat(def.globalContext()).containsEntry("env", "prod");
    }

    @Test
    void toString_containsId() {
        var def = new WorkflowDefinition("wf1", "name", "desc", "s1", List.of(), Map.of());
        assertThat(def.toString()).contains("wf1");
    }
}

class WorkflowStepTest {

    @Test
    void constructor_setsAllFields() {
        var step = new WorkflowStep("s1", "assistant", "llm", "Description",
            List.of("s2", "s3"), Map.of("input", "value"), Map.of("result", "output"));
        assertThat(step.id()).isEqualTo("s1");
        assertThat(step.role()).isEqualTo("assistant");
        assertThat(step.type()).isEqualTo("llm");
        assertThat(step.description()).isEqualTo("Description");
        assertThat(step.next()).containsExactly("s2", "s3");
        assertThat(step.inputMapping()).containsEntry("input", "value");
        assertThat(step.outputMapping()).containsEntry("result", "output");
    }

    @Test
    void constructor_handlesEmptyLists() {
        var step = new WorkflowStep("s1", "tool", "function", "desc", List.of(), Map.of(), Map.of());
        assertThat(step.next()).isEmpty();
    }
}

class StepStatusTest {

    @Test
    void values_areAllPresent() {
        assertThat(StepStatus.values())
            .containsExactly(StepStatus.PENDING, StepStatus.IN_PROGRESS, StepStatus.COMPLETED,
                StepStatus.FAILED, StepStatus.SKIPPED, StepStatus.WAITING_FOR_HUMAN);
    }

    @Test
    void valueOf_roundTrip() {
        for (var s : StepStatus.values()) {
            assertThat(StepStatus.valueOf(s.name())).isEqualTo(s);
        }
    }

    @Test
    void ordinal_order() {
        assertThat(StepStatus.PENDING.ordinal()).isLessThan(StepStatus.IN_PROGRESS.ordinal());
        assertThat(StepStatus.IN_PROGRESS.ordinal()).isLessThan(StepStatus.COMPLETED.ordinal());
        assertThat(StepStatus.COMPLETED.ordinal()).isLessThan(StepStatus.FAILED.ordinal());
        assertThat(StepStatus.FAILED.ordinal()).isLessThan(StepStatus.SKIPPED.ordinal());
        assertThat(StepStatus.SKIPPED.ordinal()).isLessThan(StepStatus.WAITING_FOR_HUMAN.ordinal());
    }
}
