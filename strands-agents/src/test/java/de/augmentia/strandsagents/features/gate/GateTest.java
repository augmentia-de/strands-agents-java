package de.augmentia.strandsagents.features.gate;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class GateTypeTest {

    @Test
    void enumHasExpectedValues() {
        assertThat(GateType.values()).containsExactly(
            GateType.COOLDOWN, GateType.CRON, GateType.CONDITION,
            GateType.EVENT, GateType.MANUAL);
    }

    @Test
    void valueOf_returnsCorrect() {
        assertThat(GateType.valueOf("COOLDOWN")).isEqualTo(GateType.COOLDOWN);
        assertThat(GateType.valueOf("MANUAL")).isEqualTo(GateType.MANUAL);
    }
}

class GateAnnotationTest {

    @Test
    void annotationCanBeAppliedToMethod() throws Exception {
        class GatedPlugin {
            @Gate(type = GateType.COOLDOWN, duration = "30s")
            public void doSomething() {}
        }
        Method method = GatedPlugin.class.getMethod("doSomething");
        var gate = method.getAnnotation(Gate.class);
        assertThat(gate).isNotNull();
        assertThat(gate.type()).isEqualTo(GateType.COOLDOWN);
        assertThat(gate.duration()).isEqualTo("30s");
        assertThat(gate.schedule()).isEmpty();
        assertThat(gate.condition()).isEmpty();
        assertThat(gate.on()).isEmpty();
    }

    @Test
    void annotationWithAllAttributes() throws Exception {
        class FullGatePlugin {
            @Gate(type = GateType.CRON, duration = "10m", schedule = "*/5 * * * *",
                  condition = "isOpen", on = "method1")
            public void run() {}
        }
        Method method = FullGatePlugin.class.getMethod("run");
        var gate = method.getAnnotation(Gate.class);
        assertThat(gate.type()).isEqualTo(GateType.CRON);
        assertThat(gate.duration()).isEqualTo("10m");
        assertThat(gate.schedule()).isEqualTo("*/5 * * * *");
        assertThat(gate.condition()).isEqualTo("isOpen");
        assertThat(gate.on()).isEqualTo("method1");
    }

    @Test
    void annotationNotPresentOnPlainMethod() throws Exception {
        class PlainPlugin {
            public void doNothing() {}
        }
        Method method = PlainPlugin.class.getMethod("doNothing");
        assertThat(method.getAnnotation(Gate.class)).isNull();
    }
}

class GateEvaluatorTest {

    @Test
    void defaultMethods_canBeImplemented() {
        var evaluator = new GateEvaluator() {
            @Override
            public boolean isOpen(Method pluginMethod, Gate gate) { return true; }

            @Override
            public void recordExecution(Method pluginMethod, Gate gate, boolean success) {}
        };
        assertThat(evaluator).isNotNull();
    }
}
