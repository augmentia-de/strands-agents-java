package de.augmentia.strandsagents.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.augmentia.strandsagents.features.tools.AgentTool;
import de.augmentia.strandsagents.features.tools.ToolResult;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class ToolExecutorRandomFailureTest {

    private static final ToolRegistry REGISTRY = new ToolRegistry();
    static {
        REGISTRY.register(new AgentTool<Object>() {
            @Override public String name() { return "test-tool"; }
            @Override public String description() { return "A test tool"; }
            @Override public Class<Object> parameterType() { return Object.class; }
            @Override public ObjectNode parameterSchema() {
                return JsonNodeFactory.instance.objectNode();
            }
            @Override
            public ToolResult execute(String id, Object p, AtomicBoolean a, Consumer<ToolResult> u) {
                return ToolResult.success("ok");
            }
        });
    }

    private static ToolExecutionRequest request(String name) {
        return ToolExecutionRequest.builder()
            .id("req-1")
            .name(name)
            .arguments("{}")
            .build();
    }

    @Test
    void disabled_shouldExecuteNormally() throws Exception {
        var executor = new ToolExecutor(30, false, 1.0, 0.0, 0.0);
        var result = executor.executeSingle(request("test-tool"), REGISTRY);
        assertThat(result.result()).isEqualTo("ok");
        assertThat(result.isError()).isFalse();
    }

    @Test
    void exceptionMode_shouldThrowRuntimeException() {
        var executor = new ToolExecutor(30, true, 0.0, 1.0, 0.0);
        assertThatThrownBy(() -> executor.executeSingle(request("test-tool"), REGISTRY))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Simulated random error");
    }

    @Test
    void invalidJsonMode_shouldReturnMalformedResult() throws Exception {
        var executor = new ToolExecutor(30, true, 0.0, 0.0, 1.0);
        var result = executor.executeSingle(request("test-tool"), REGISTRY);
        assertThat(result.result()).isEqualTo("{invalid json");
        assertThat(result.isError()).isFalse();
    }

    @Test
    void timeoutMode_shouldTriggerTimeoutException() {
        var executor = new ToolExecutor(1, true, 1.0, 0.0, 0.0);
        assertThatThrownBy(() -> executor.executeSingle(request("test-tool"), REGISTRY))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Simulated timeout");
    }

    @Test
    void allProbabilitiesZero_shouldNotFail() throws Exception {
        var executor = new ToolExecutor(30, true, 0.0, 0.0, 0.0);
        var result = executor.executeSingle(request("test-tool"), REGISTRY);
        assertThat(result.result()).isEqualTo("ok");
    }

    @Test
    void executeSingle_withDisabledFailure_shouldWork() throws Exception {
        var executor = new ToolExecutor(30, false, 0.0, 0.0, 0.0);
        var result = executor.executeSingle(request("test-tool"), REGISTRY);
        assertThat(result.result()).isEqualTo("ok");
    }
}
