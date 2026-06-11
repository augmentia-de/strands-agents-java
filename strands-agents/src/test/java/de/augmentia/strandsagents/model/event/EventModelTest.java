package de.augmentia.strandsagents.model.event;

import static org.assertj.core.api.Assertions.assertThat;

import de.augmentia.strandsagents.model.agent.AgentPhase;
import de.augmentia.strandsagents.model.message.UserMessage;
import de.augmentia.strandsagents.model.tool.ToolCall;
import de.augmentia.strandsagents.model.tool.ToolExecutionResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentStartedEventTest {

    @Test
    void constructor_setsAllFields() {
        var now = Instant.now();
        var e = new AgentStartedEvent("sid1", now, "hello");
        assertThat(e.sessionId()).isEqualTo("sid1");
        assertThat(e.timestamp()).isEqualTo(now);
        assertThat(e.initialPrompt()).isEqualTo("hello");
    }
}

class AgentFinishedEventTest {

    @Test
    void constructor_setsAllFields() {
        var now = Instant.now();
        var e = new AgentFinishedEvent("sid1", now, "answer");
        assertThat(e.sessionId()).isEqualTo("sid1");
        assertThat(e.finalAnswer()).isEqualTo("answer");
    }
}

class ToolExecutionStartedEventTest {

    @Test
    void constructor_setsAllFields() {
        var now = Instant.now();
        var tc = new ToolCall("c1", "calc", "{}");
        var e = new ToolExecutionStartedEvent("sid1", now, tc);
        assertThat(e.toolCall().toolName()).isEqualTo("calc");
    }
}

class ToolExecutionFinishedEventTest {

    @Test
    void constructor_setsAllFields() {
        var now = Instant.now();
        var r = new ToolExecutionResult("c1", "calc", "42", false);
        var e = new ToolExecutionFinishedEvent("sid1", now, r);
        assertThat(e.result().result()).isEqualTo("42");
    }
}

class BeforeInvocationEventTest {

    @Test
    void constructor_setsAllFields() {
        var now = Instant.now();
        var sb = new StringBuilder("prompt");
        var msgs = List.<de.augmentia.strandsagents.model.message.Message>of(new UserMessage("id1", now, "hi", Map.of()));
        var e = new BeforeInvocationEvent("sid1", now, sb, msgs);
        assertThat(e.systemPrompt().toString()).isEqualTo("prompt");
        assertThat(e.currentMessages()).hasSize(1);
    }
}

class AfterInvocationEventTest {

    @Test
    void constructor_setsAllFields() {
        var now = Instant.now();
        var e = new AfterInvocationEvent("sid1", now, "response", List.of());
        assertThat(e.response()).isEqualTo("response");
        assertThat(e.messages()).isEmpty();
    }
}

class ModelRequestedEventTest {

    @Test
    void constructor_setsAllFields() {
        var now = Instant.now();
        var e = new ModelRequestedEvent("sid1", now, List.of());
        assertThat(e.promptHistory()).isEmpty();
    }
}

class TokenEventTest {

    @Test
    void constructor_setsAllFields() {
        var now = Instant.now();
        var e = new TokenEvent("sid1", now, "Hello");
        assertThat(e.token()).isEqualTo("Hello");
    }
}

class AgentStateChangedEventTest {

    @Test
    void constructor_setsAllFields() {
        var now = Instant.now();
        var e = new AgentStateChangedEvent("sid1", now,
            AgentPhase.IDLE, AgentPhase.EXECUTING, "goal", 1, 0);
        assertThat(e.previousPhase()).isEqualTo(AgentPhase.IDLE);
        assertThat(e.currentPhase()).isEqualTo(AgentPhase.EXECUTING);
        assertThat(e.goal()).isEqualTo("goal");
        assertThat(e.iterationCount()).isEqualTo(1);
        assertThat(e.revisionCount()).isEqualTo(0);
    }
}
