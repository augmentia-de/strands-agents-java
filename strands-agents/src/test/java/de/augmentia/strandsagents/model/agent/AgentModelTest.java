package de.augmentia.strandsagents.model.agent;

import static org.assertj.core.api.Assertions.assertThat;

import de.augmentia.strandsagents.model.message.UserMessage;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentResultTest {

    @Test
    void constructor_setsAllFields() {
        var metrics = new ExecutionMetrics(100, 10, 20, 3);
        var result = new AgentResult("sid1", "answer", List.of(), metrics, StopReason.COMPLETED);
        assertThat(result.sessionId()).isEqualTo("sid1");
        assertThat(result.finalAnswer()).isEqualTo("answer");
        assertThat(result.generatedMessages()).isEmpty();
        assertThat(result.metrics()).isEqualTo(metrics);
        assertThat(result.stopReason()).isEqualTo(StopReason.COMPLETED);
        assertThat(result.structuredOutput()).isNull();
    }

    @Test
    void fullConstructor_includesStructuredOutput() {
        var result = new AgentResult("sid1", "json", List.of(),
            new ExecutionMetrics(50, 5, 10, 1), StopReason.COMPLETED, "{\"key\":\"val\"}");
        assertThat(result.structuredOutput()).isEqualTo("{\"key\":\"val\"}");
    }
}

class AgentStateTest {

    @Test
    void constructor_setsAllFields() {
        var state = new AgentState("sid1", List.of(), Map.of("key", "val"), AgentStatus.IDLE);
        assertThat(state.sessionId()).isEqualTo("sid1");
        assertThat(state.history()).isEmpty();
        assertThat(state.contextVariables()).containsEntry("key", "val");
        assertThat(state.status()).isEqualTo(AgentStatus.IDLE);
    }

    @Test
    void historyAndContextAreDefensive() {
        var msgs = List.<de.augmentia.strandsagents.model.message.Message>of(new UserMessage("id1", Instant.now(), "hello", Map.of()));
        var ctx = Map.<String, Object>of("k", "v");
        var state = new AgentState("sid1", msgs, ctx, AgentStatus.RUNNING);
        assertThat(state.history()).hasSize(1);
    }
}

class AgentStatusTest {

    @Test
    void enumHasExpectedValues() {
        assertThat(AgentStatus.values()).containsExactly(
            AgentStatus.IDLE, AgentStatus.RUNNING, AgentStatus.AWAITING_TOOL_EXECUTION,
            AgentStatus.COMPLETED, AgentStatus.FAILED, AgentStatus.INTERRUPTED);
    }
}

class AgentPhaseTest {

    @Test
    void enumHasExpectedValues() {
        assertThat(AgentPhase.values()).containsExactly(
            AgentPhase.IDLE, AgentPhase.PLANNING, AgentPhase.EXECUTING,
            AgentPhase.REVIEWING, AgentPhase.REVISING, AgentPhase.COMPLETED,
            AgentPhase.FAILED, AgentPhase.WAITING_FOR_HUMAN);
    }
}

class StopReasonTest {

    @Test
    void enumHasExpectedValues() {
        assertThat(StopReason.values()).containsExactly(
            StopReason.MAX_ITERATIONS, StopReason.COMPLETED,
            StopReason.INTERRUPTED, StopReason.ERROR);
    }
}

class ExecutionMetricsTest {

    @Test
    void constructor_setsAllFields() {
        var m = new ExecutionMetrics(100L, 10, 20, 3);
        assertThat(m.durationMs()).isEqualTo(100L);
        assertThat(m.inputTokens()).isEqualTo(10);
        assertThat(m.outputTokens()).isEqualTo(20);
        assertThat(m.toolCallsCount()).isEqualTo(3);
    }
}
