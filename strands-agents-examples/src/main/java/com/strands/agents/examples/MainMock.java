package com.strands.agents.examples;

import com.strands.agents.core.StrandsAgent;
import com.strands.agents.core.model.agent.AgentResult;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;

public class MainMock {

    public static void main(String[] args) {
        var agent = new StrandsAgent(new SimpleMockModel());

        System.out.println("=== Strands Agent (Mock) ===");
        System.out.println("(Demo-Modus – keine echten LLM-Aufrufe)");
        System.out.println();

        interact(agent, "Hallo, wer bist du?");
        interact(agent, "Was ist die Hauptstadt von Frankreich?");
        interact(agent, "Erinnere dich: mein Name ist Torsten.");
        interact(agent, "Wie heißt ich?");
    }

    static void interact(StrandsAgent agent, String prompt) {
        System.out.println("Du:    " + prompt);
        AgentResult result = agent.execute(prompt);
        System.out.println("Agent: " + result.finalAnswer());
        System.out.println("       (Tokens: " + result.metrics().inputTokens()
            + " in / " + result.metrics().outputTokens() + " out, "
            + result.metrics().durationMs() + " ms)");
        System.out.println();
    }

    static class SimpleMockModel implements ChatModel {
        @Override
        public ChatResponse chat(List<ChatMessage> messages) {
            if (messages.isEmpty()) {
                return ChatResponse.builder()
                    .aiMessage(AiMessage.from(""))
                    .tokenUsage(new TokenUsage(0, 0))
                    .finishReason(FinishReason.STOP)
                    .build();
            }
            var last = messages.get(messages.size() - 1);
            var text = last instanceof dev.langchain4j.data.message.UserMessage um
                ? um.singleText() : last.toString();
            var response = "Mock-Antwort auf: \"" + text + "\"";
            return ChatResponse.builder()
                .aiMessage(AiMessage.from(response))
                .tokenUsage(new TokenUsage(10, response.length()))
                .finishReason(FinishReason.STOP)
                .build();
        }
    }
}
