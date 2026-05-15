package com.strands.agents.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.strands.agents.core.tools.CalculatorTool;
import com.strands.agents.core.ToolRegistry;
import org.junit.jupiter.api.Test;

class AgentToolIntegrationTest {

    @Test
    void agentShouldUseMockTool() {
        var registry = new ToolRegistry();
        registry.register(new CalculatorTool());
        var executor = new ToolExecutor();
        var model = new MockChatModel();
        var agent = new StrandsAgent(model, registry, executor);

        var result = agent.execute("Berechne 3 + 4");

        assertThat(result.finalAnswer()).isNotEmpty();
        assertThat(result.stopReason()).isEqualTo(com.strands.agents.core.model.agent.StopReason.COMPLETED);
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
