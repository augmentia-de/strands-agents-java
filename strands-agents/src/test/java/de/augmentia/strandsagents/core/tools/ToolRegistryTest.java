package de.augmentia.strandsagents.core.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import de.augmentia.strandsagents.core.ToolRegistry;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ToolRegistryTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    static AgentTool<?> simpleTool(String name, String desc) {
        return new AgentTool<Object>() {
            @Override public String name() { return name; }
            @Override public String description() { return desc; }
            @Override public Class<Object> parameterType() { return Object.class; }
            @Override public com.fasterxml.jackson.databind.node.ObjectNode parameterSchema() {
                return JsonNodeFactory.instance.objectNode();
            }
            @Override
            public ToolResult execute(String toolCallId, Object params, AtomicBoolean abortFlag, java.util.function.Consumer<ToolResult> onUpdate) {
                return ToolResult.success("ok");
            }
        };
    }

    @Test
    void shouldRegisterToolFromAnnotatedClass() {
        registry.register(simpleTool("add", "Adds two numbers"));
        registry.register(simpleTool("multiply", "Multiplies two numbers"));
        registry.register(simpleTool("stringLength", "Returns the length of a string"));
        assertThat(registry.getToolNames()).contains("add", "multiply", "stringLength");
        assertThat(registry.size()).isEqualTo(3);
    }

    @Test
    void shouldProvideToolSpecifications() {
        registry.register(simpleTool("add", "Adds two numbers"));
        registry.register(simpleTool("multiply", "Multiplies two numbers"));
        registry.register(simpleTool("stringLength", "Returns the length of a string"));
        var specs = registry.getSpecifications();
        assertThat(specs).hasSize(3);
        assertThat(specs).extracting("name").contains("add", "multiply");
    }

    @Test
    void shouldLookUpToolMethod() {
        registry.register(simpleTool("add", "Adds two numbers"));
        var toolMethod = registry.get("add");
        assertThat(toolMethod).isNotNull();
        assertThat(toolMethod.spec().name()).isEqualTo("add");
        assertThat(toolMethod.spec().description()).contains("Adds two numbers");
    }

    @Test
    void shouldThrowOnUnknownTool() {
        registry.register(simpleTool("add", "Addiert"));
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
