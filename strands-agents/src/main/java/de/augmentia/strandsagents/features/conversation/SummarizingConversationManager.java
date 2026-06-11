package de.augmentia.strandsagents.features.conversation;

import de.augmentia.strandsagents.model.message.Message;
import de.augmentia.strandsagents.model.message.SystemMessage;
import de.augmentia.strandsagents.model.message.UserMessage;
import de.augmentia.strandsagents.model.message.AssistantMessage;
import de.augmentia.strandsagents.model.message.ToolMessage;
import de.augmentia.strandsagents.prompt.PromptRegistry;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SummarizingConversationManager(ChatModel summarizer, int maxTokens) implements ConversationManager {

    private static final int TOKEN_ESTIMATE_DIVISOR = 4;

    public SummarizingConversationManager {
        if (maxTokens < 1) {
            throw new IllegalArgumentException("maxTokens must be >= 1, got: " + maxTokens);
        }
    }

    @Override
    public List<Message> prune(List<Message> messages) {
        int totalTokens = estimateTokens(messages);
        if (totalTokens <= maxTokens) {
            return messages;
        }

        if (messages.size() < 2) {
            return messages;
        }

        int splitIndex = Math.max(messages.size() / 2, 1);

        List<Message> toSummarize = messages.subList(0, splitIndex);
        List<Message> toKeep = messages.subList(splitIndex, messages.size());

        String summary = generateSummary(toSummarize);
        Message summaryMessage = new SystemMessage(
            UUID.randomUUID().toString(),
            Instant.now(),
            PromptRegistry.get("summarizing.prefix") + summary,
            Map.of()
        );

        List<Message> result = new ArrayList<>();
        result.add(summaryMessage);
        result.addAll(toKeep);
        return result;
    }

    private int estimateTokens(List<Message> messages) {
        return messages.stream()
            .mapToInt(m -> {
                String c = m.content();
                return (c != null ? c.length() : 0) / TOKEN_ESTIMATE_DIVISOR + 4;
            })
            .sum();
    }

    String generateSummary(List<Message> messages) {
        var sb = new StringBuilder();
        sb.append(PromptRegistry.get("summarizing.instruction")).append("\n\n");
        for (var msg : messages) {
            String role = switch (msg) {
                case UserMessage u -> "User";
                case AssistantMessage a ->
                    a.toolCalls() != null && !a.toolCalls().isEmpty() ? "Assistant (Tool-Call)" : "Assistant";
                case SystemMessage s -> "System";
                case ToolMessage t -> "Tool (" + t.toolName() + ")";
            };
            sb.append(role).append(": ").append(msg.content() != null ? msg.content() : "").append("\n");
        }

        var request = ChatRequest.builder()
            .messages(dev.langchain4j.data.message.UserMessage.from(sb.toString()))
            .build();

        ChatResponse response = summarizer.chat(request);
        return response.aiMessage().text();
    }
}
