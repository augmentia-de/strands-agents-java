package de.augmentia.strandsagents.model.tool;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ToolModelTest {

    @Test
    void toolExecutionResult_constructor() {
        var r = new ToolExecutionResult("call1", "calculator", "42", false);
        assertThat(r.toolCallId()).isEqualTo("call1");
        assertThat(r.toolName()).isEqualTo("calculator");
        assertThat(r.result()).isEqualTo("42");
        assertThat(r.isError()).isFalse();
    }

    @Test
    void toolExecutionResult_errorCase() {
        var r = new ToolExecutionResult("call2", "search", "timeout", true);
        assertThat(r.isError()).isTrue();
    }

    @Test
    void toolCall_constructor() {
        var tc = new ToolCall("id1", "calculator", "{\"a\":1,\"b\":2}");
        assertThat(tc.id()).isEqualTo("id1");
        assertThat(tc.toolName()).isEqualTo("calculator");
        assertThat(tc.arguments()).contains("a");
    }
}
