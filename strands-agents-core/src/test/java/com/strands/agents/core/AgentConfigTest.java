package com.strands.agents.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentConfigTest {

    @Test
    void shouldBuildWithDefaults() {
        var config = AgentConfig.builder().build();

        assertThat(config.name()).isEqualTo("unnamed");
        assertThat(config.maxIterations()).isEqualTo(10);
        assertThat(config.toolClassNames()).isEmpty();
        assertThat(config.routes()).isEmpty();
    }

    @Test
    void shouldBuildWithCustomValues() {
        var config = AgentConfig.builder()
            .name("recherche-agent")
            .modelName("openai/gpt-4o")
            .toolClassNames(List.of("com.strands.agents.core.tools.CalculatorTool"))
            .maxIterations(15)
            .routes(Map.of("wetter", "weather-agent"))
            .build();

        assertThat(config.name()).isEqualTo("recherche-agent");
        assertThat(config.modelName()).isEqualTo("openai/gpt-4o");
        assertThat(config.toolClassNames()).contains("com.strands.agents.core.tools.CalculatorTool");
        assertThat(config.maxIterations()).isEqualTo(15);
        assertThat(config.routes()).containsEntry("wetter", "weather-agent");
    }
}
