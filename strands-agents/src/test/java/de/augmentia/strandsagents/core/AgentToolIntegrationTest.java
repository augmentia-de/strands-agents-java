package de.augmentia.strandsagents.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import de.augmentia.strandsagents.tools.AgentTool;
import de.augmentia.strandsagents.tools.ToolResult;
import de.augmentia.strandsagents.model.agent.StopReason;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class AgentToolIntegrationTest {

    private static AgentTool<?> simpleTool(String name) {
        return new AgentTool<Object>() {
            @Override public String name() { return name; }
            @Override public String description() { return name; }
            @Override public Class<Object> parameterType() { return Object.class; }
            @Override public com.fasterxml.jackson.databind.node.ObjectNode parameterSchema() {
                return JsonNodeFactory.instance.objectNode();
            }
            @Override public ToolResult execute(String id, Object p, AtomicBoolean a, java.util.function.Consumer<ToolResult> u) {
                return ToolResult.success("ok");
            }
        };
    }

    @Test
    void agentShouldUseMockTool() {
        var registry = new ToolRegistry();
        registry.register(simpleTool("add"));
        var executor = new DefaultToolExecutor();
        var model = new MockChatModel();
        var agent = new Agent(model, registry, executor);

        var result = agent.execute("Berechne 3 + 4");

        assertThat(result.finalAnswer()).isNotEmpty();
        assertThat(result.stopReason()).isEqualTo(StopReason.COMPLETED);
        assertThat(result.metrics().toolCallsCount()).isZero();
    }

    @Test
    void agentShouldHaveToolSpecifications() {
        var registry = new ToolRegistry();
        registry.register(simpleTool("add"));
        registry.register(simpleTool("multiply"));
        registry.register(simpleTool("stringLength"));
        var specs = registry.getSpecifications();

        assertThat(specs).extracting("name")
            .contains("add", "multiply", "stringLength");
    }
}
