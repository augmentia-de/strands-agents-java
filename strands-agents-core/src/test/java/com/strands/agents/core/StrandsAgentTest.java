package com.strands.agents.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.strands.agents.core.model.agent.AgentResult;
import com.strands.agents.core.model.agent.StopReason;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

class StrandsAgentTest {

    @Test
    void shouldReturnValidAgentResult() {
        var agent = new StrandsAgent(new MockChatModel());
        var result = agent.execute("Hallo Welt");

        assertThat(result).isNotNull();
        assertThat(result.finalAnswer()).isNotEmpty();
        assertThat(result.stopReason()).isEqualTo(StopReason.COMPLETED);
        assertThat(result.sessionId()).isNotEmpty();
    }

    @Test
    void shouldMaintainConversationHistory() {
        var agent = new StrandsAgent(new MockChatModel("Antwort: %s"), 10);

        agent.execute("Erste Frage");
        agent.execute("Zweite Frage");

        var memory = agent.getChatMemory();
        assertThat(memory.messages()).hasSize(4);
        assertThat(((UserMessage) memory.messages().get(0)).singleText()).isEqualTo("Erste Frage");
    }

    @Test
    void shouldTruncateHistoryWhenExceedingMaxMessages() {
        var agent = new StrandsAgent(new MockChatModel(), 2);

        agent.execute("Frage 1");
        agent.execute("Frage 2");
        agent.execute("Frage 3");

        var memory = agent.getChatMemory();
        assertThat(memory.messages().size()).isLessThanOrEqualTo(4);
    }
}
