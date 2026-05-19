package de.augmentia.strandsagents.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.augmentia.strandsagents.core.tools.CalculatorTool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolExecutionTest {

    @Test
    void shouldExecuteSingleTool() throws Exception {
        var registry = new ToolRegistry();
        registry.register(new CalculatorTool());

        var executor = new ToolExecutor();
        var request = ToolExecutionRequest.builder()
            .id("1")
            .name("stringLength")
            .arguments("{\"text\": \"Hallo\"}")
            .build();

        var result = executor.executeSingle(request, registry);

        assertThat(result.toolName()).isEqualTo("stringLength");
        assertThat(result.result()).isEqualTo("5");
        assertThat(result.isError()).isFalse();
    }

    @Test
    void shouldExecuteMultipleToolsInParallel() throws Exception {
        var registry = new ToolRegistry();
        registry.register(new CalculatorTool());
        var executor = new ToolExecutor();

        var requests = List.of(
            ToolExecutionRequest.builder()
                .id("1").name("add")
                .arguments("{\"a\": \"3\", \"b\": \"4\"}")
                .build(),
            ToolExecutionRequest.builder()
                .id("2").name("multiply")
                .arguments("{\"a\": \"5\", \"b\": \"6\"}")
                .build()
        );

        var results = executor.executeAll(requests, registry);

        assertThat(results).hasSize(2);
        assertThat(results).extracting("toolName")
            .containsExactlyInAnyOrder("add", "multiply");
    }

    @Test
    void shouldHandleUnknownToolGracefully() {
        var registry = new ToolRegistry();
        registry.register(new CalculatorTool());
        var executor = new ToolExecutor();

        var request = ToolExecutionRequest.builder()
            .id("1").name("unknown_tool")
            .arguments("{}")
            .build();

        assertThatThrownBy(() -> executor.executeSingle(request, registry))
            .isInstanceOf(IllegalArgumentException.class);
    }

}
