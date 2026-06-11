package de.augmentia.strandsagents.model.session;

import static org.assertj.core.api.Assertions.assertThat;

import de.augmentia.strandsagents.model.agent.AgentState;
import de.augmentia.strandsagents.model.agent.AgentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SessionTest {

    @Test
    void constructor_setsAllFields() {
        var now = Instant.now();
        var s = new Session("sid1", "agent1", List.of(),
            new AgentState("sid1", List.of(), Map.of(), AgentStatus.IDLE),
            Map.of("env", "test"), now, now);
        assertThat(s.sessionId()).isEqualTo("sid1");
        assertThat(s.agentName()).isEqualTo("agent1");
        assertThat(s.messages()).isEmpty();
        assertThat(s.state().status()).isEqualTo(AgentStatus.IDLE);
        assertThat(s.metadata()).containsEntry("env", "test");
        assertThat(s.createdAt()).isEqualTo(now);
        assertThat(s.updatedAt()).isEqualTo(now);
    }
}
