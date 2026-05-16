package com.strands.agents.core.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.strands.agents.core.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ToolRegistryTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    @Test
    void shouldRegisterToolFromAnnotatedClass() {
        registry.register(new CalculatorTool());
        assertThat(registry.getToolNames()).contains("add", "multiply", "stringLength");
        assertThat(registry.size()).isEqualTo(3);
    }

    @Test
    void shouldProvideToolSpecifications() {
        registry.register(new CalculatorTool());
        var specs = registry.getSpecifications();
        assertThat(specs).hasSize(3);
        assertThat(specs).extracting("name").contains("add", "multiply");
    }

    @Test
    void shouldLookUpToolMethod() {
        registry.register(new CalculatorTool());
        var toolMethod = registry.get("add");
        assertThat(toolMethod).isNotNull();
        assertThat(toolMethod.spec().name()).isEqualTo("add");
        assertThat(toolMethod.spec().description()).contains("Addiert");
    }

    @Test
    void shouldThrowOnUnknownTool() {
        registry.register(new CalculatorTool());
        assertThatThrownBy(() -> registry.get("unknown"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown tool");
    }

    @Test
    void shouldBeEmptyWithNoTools() {
        assertThat(registry.size()).isZero();
        assertThat(registry.getSpecifications()).isEmpty();
    }
}
