package de.augmentia.strandsagents.core.agent.routing;

import de.augmentia.strandsagents.core.prompt.PromptRegistry;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;

public class LlmRouter {

    private final ChatModel model;
    private final double confidenceThreshold;

    public LlmRouter(ChatModel model) {
        this(model, 0.6);
    }

    public LlmRouter(ChatModel model, double confidenceThreshold) {
        this.model = model;
        this.confidenceThreshold = confidenceThreshold;
    }

    public RoutingResult classify(String prompt, List<String> topics) {
        var topicsJoined = String.join(", ", topics);
        var systemPrompt = PromptRegistry.get("llm_router.system", topicsJoined).strip();

        var request = ChatRequest.builder()
            .messages(List.of(
                new SystemMessage(systemPrompt),
                UserMessage.from(prompt)
            ))
            .build();

        ChatResponse response;
        try {
            response = model.chat(request);
        } catch (Exception e) {
            return new RoutingResult("DEFAULT", 0.0, prompt);
        }

        var topic = response.aiMessage().text().strip();
        var isKnown = topics.stream().anyMatch(t -> t.equalsIgnoreCase(topic));

        if (!isKnown) {
            return new RoutingResult("DEFAULT", 0.0, prompt);
        }

        var normalizedTopic = topics.stream()
            .filter(t -> t.equalsIgnoreCase(topic))
            .findFirst()
            .orElse("DEFAULT");

        return new RoutingResult(normalizedTopic, 0.9, prompt);
    }

    public double getConfidenceThreshold() {
        return confidenceThreshold;
    }
}
