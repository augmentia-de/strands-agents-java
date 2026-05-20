package de.augmentia.strandsagents.core;

import static org.assertj.core.api.Assertions.assertThat;

import de.augmentia.strandsagents.core.agent.MockChatModel;
import de.augmentia.strandsagents.core.agent.Agent;
import de.augmentia.strandsagents.core.tools.CalculatorTool;
import de.augmentia.strandsagents.core.model.agent.StopReason;
import org.junit.jupiter.api.Test;

class AgentToolIntegrationTest {

    @Test
    void agentShouldUseMockTool() {
        var registry = new ToolRegistry();
        registry.register(new CalculatorTool());
        var executor = new ToolExecutor();
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
        registry.register(new CalculatorTool());
        var specs = registry.getSpecifications();

        assertThat(specs).extracting("name")
            .contains("add", "multiply", "stringLength");
    }
}
