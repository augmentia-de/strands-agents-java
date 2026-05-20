package de.augmentia.strandsagents.core;

import static org.assertj.core.api.Assertions.assertThat;

import de.augmentia.strandsagents.core.agent.MockChatModel;
import de.augmentia.strandsagents.core.agent.Agent;
import de.augmentia.strandsagents.core.model.agent.StopReason;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

class AgentTest {

    @Test
    void shouldReturnValidAgentResult() {
        var agent = new Agent(new MockChatModel());
        var result = agent.execute("Hallo Welt");

        assertThat(result).isNotNull();
        assertThat(result.finalAnswer()).isNotEmpty();
        assertThat(result.stopReason()).isEqualTo(StopReason.COMPLETED);
        assertThat(result.sessionId()).isNotEmpty();
    }

    @Test
    void shouldMaintainConversationHistory() {
        var agent = new Agent(new MockChatModel("Antwort: %s"));

        agent.execute("Erste Frage");
        agent.execute("Zweite Frage");

        var memory = agent.getChatMemory();
        assertThat(memory.messages()).hasSize(4);
        assertThat(((UserMessage) memory.messages().get(0)).singleText()).isEqualTo("Erste Frage");
    }
}
