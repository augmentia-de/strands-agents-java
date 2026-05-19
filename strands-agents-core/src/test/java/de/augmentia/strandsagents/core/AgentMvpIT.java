package de.augmentia.strandsagents.core;

import static org.assertj.core.api.Assertions.assertThat;

import de.augmentia.strandsagents.core.model.agent.AgentResult;
import de.augmentia.strandsagents.core.model.agent.StopReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class AgentMvpIT {

    @Test
    void shouldAnswerSimpleQuestion() {
        var model = ModelFactory.createOpenAiFromEnv();
        var agent = new StrandsAgent(model);
        AgentResult result = agent.execute("What is the capital of France?");

        assertThat(result.finalAnswer()).containsIgnoringCase("Paris");
        assertThat(result.stopReason()).isEqualTo(StopReason.COMPLETED);
        assertThat(result.metrics().inputTokens()).isPositive();
        assertThat(result.metrics().outputTokens()).isPositive();
    }

    @Test
    void shouldRememberConversationContext() {
        var model = ModelFactory.createOpenAiFromEnv();
        var agent = new StrandsAgent(model);

        agent.execute("My name is Torsten.");
        AgentResult result = agent.execute("What is my name?");

        assertThat(result.finalAnswer()).containsIgnoringCase("Torsten");
    }
}
