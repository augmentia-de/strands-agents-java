package de.augmentia.strandsagents.features.planning;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlanTest {

    @Test
    void constructor_setsAllFields() {
        var steps = List.of(new Step("s1", "Do something", "calc", "{\"a\":1}"));
        var ctx = Map.<String, Object>of("key", "val");
        var plan = new Plan("my goal", steps, 0, ctx);
        assertThat(plan.goal()).isEqualTo("my goal");
        assertThat(plan.steps()).hasSize(1);
        assertThat(plan.currentStep()).isEqualTo(0);
        assertThat(plan.sharedContext()).containsEntry("key", "val");
    }

    @Test
    void isComplete_returnsFalseWhenStepsRemain() {
        var plan = new Plan("goal", List.of(new Step("s1", "step1", "none", "")), 0, Map.of());
        assertThat(plan.isComplete()).isFalse();
    }

    @Test
    void isComplete_returnsTrueWhenAllStepsDone() {
        var plan = new Plan("goal", List.of(new Step("s1", "step1", "none", "")), 1, Map.of());
        assertThat(plan.isComplete()).isTrue();
    }

    @Test
    void current_returnsStepAtIndex() {
        var step = new Step("s1", "Do it", "tool", "{}");
        var plan = new Plan("goal", List.of(step), 0, Map.of());
        assertThat(plan.current()).isEqualTo(step);
    }

    @Test
    void current_returnsNullWhenComplete() {
        var plan = new Plan("goal", List.of(new Step("s1", "step1", "none", "")), 1, Map.of());
        assertThat(plan.current()).isNull();
    }

    @Test
    void advanceStep_incrementsIndex() {
        var plan = new Plan("goal", List.of(
            new Step("s1", "step1", "none", ""),
            new Step("s2", "step2", "none", "")
        ), 0, Map.of());
        var advanced = plan.advanceStep();
        assertThat(advanced.currentStep()).isEqualTo(1);
        assertThat(plan.currentStep()).isEqualTo(0); // original unchanged
    }

    @Test
    void withStep_setsIndex() {
        var plan = new Plan("goal", List.of(new Step("s1", "step1", "none", "")), 0, Map.of());
        assertThat(plan.withStep(5).currentStep()).isEqualTo(5);
    }

    @Test
    void withSharedContext_replacesContext() {
        var plan = new Plan("goal", List.of(), 0, Map.of("k1", "v1"));
        var updated = plan.withSharedContext(Map.of("k2", "v2"));
        assertThat(updated.sharedContext()).containsEntry("k2", "v2");
        assertThat(updated.sharedContext()).doesNotContainKey("k1");
    }
}

class StepTest {

    @Test
    void fullConstructor() {
        var step = new Step("s1", "desc", "tool", "{}", List.of("s0"), true);
        assertThat(step.id()).isEqualTo("s1");
        assertThat(step.description()).isEqualTo("desc");
        assertThat(step.toolName()).isEqualTo("tool");
        assertThat(step.argumentsTemplate()).isEqualTo("{}");
        assertThat(step.dependsOn()).containsExactly("s0");
        assertThat(step.optional()).isTrue();
    }

    @Test
    void shortConstructor_defaultsDependsOnAndOptional() {
        var step = new Step("s1", "desc", "none", "arg");
        assertThat(step.dependsOn()).isEmpty();
        assertThat(step.optional()).isFalse();
    }

    @Test
    void optionalConstructor_defaultsDependsOn() {
        var step = new Step("s1", "desc", "tool", "{}", true);
        assertThat(step.dependsOn()).isEmpty();
        assertThat(step.optional()).isTrue();
    }
}

class StepResultTest {

    @Test
    void ok_staticFactory() {
        var r = StepResult.ok("output");
        assertThat(r.success()).isTrue();
        assertThat(r.output()).isEqualTo("output");
        assertThat(r.error()).isNull();
        assertThat(r.artifacts()).isEmpty();
    }

    @Test
    void ok_withArtifacts() {
        var r = StepResult.ok("done", Map.of("key", "val"));
        assertThat(r.success()).isTrue();
        assertThat(r.artifacts()).containsEntry("key", "val");
    }

    @Test
    void fail_staticFactory() {
        var r = StepResult.fail("something broke");
        assertThat(r.success()).isFalse();
        assertThat(r.output()).isNull();
        assertThat(r.error()).isEqualTo("something broke");
    }

    @Test
    void constructor_booleanOutput() {
        var r = new StepResult(true, "ok");
        assertThat(r.success()).isTrue();
        assertThat(r.output()).isEqualTo("ok");
    }

    @Test
    void constructor_booleanOutputError() {
        var r = new StepResult(false, null, "err");
        assertThat(r.success()).isFalse();
        assertThat(r.error()).isEqualTo("err");
    }
}
