package de.augmentia.strandsagents.core.conversation;

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

public record SummarizingSlidingWindowConversationManager(
    ChatModel summarizer,
    int maxTokens,
    int keepLastUserMessages
) implements ConversationManager {

    private static final int TOKEN_ESTIMATE_DIVISOR = 4;
    private static final double SYSTEM_AGGREGATE_THRESHOLD = 0.30;
    private static final int SYSTEM_AGGREGATE_MIN = 2;

    public SummarizingSlidingWindowConversationManager {
        if (maxTokens < 1) {
            throw new IllegalArgumentException("maxTokens must be >= 1, got: " + maxTokens);
        }
        if (keepLastUserMessages < 1) {
            throw new IllegalArgumentException("keepLastUserMessages must be >= 1, got: " + keepLastUserMessages);
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

        List<Message> systemMsgs = new ArrayList<>();
        List<Message> nonSystemMsgs = new ArrayList<>();
        for (var msg : messages) {
            if (msg instanceof SystemMessage) {
                systemMsgs.add(msg);
            } else {
                nonSystemMsgs.add(msg);
            }
        }

        if (nonSystemMsgs.size() <= 10) {
            return messages;
        }

        List<Message> toKeep = selectLastUserMessagesBlock(nonSystemMsgs);
        if (toKeep == nonSystemMsgs) {
            return messages;
        }

        int splitIndex = nonSystemMsgs.size() - toKeep.size();
        List<Message> toSummarize = nonSystemMsgs.subList(0, splitIndex);

        if (toKeep.size() * 2 >= toSummarize.size()) {
            return messages;
        }

        if (estimateTokens(toSummarize) <= estimateTokens(toKeep) * 2) {
            return messages;
        }

        List<Message> result = new ArrayList<>();

        if (!systemMsgs.isEmpty()) {
            result.addAll(maybeAggregateSystem(systemMsgs, messages.size()));
        }

        String summary = generateSummary(toSummarize);
        result.add(new SystemMessage(
            UUID.randomUUID().toString(),
            Instant.now(),
            PromptRegistry.get("summarizing_sliding.prefix") + summary,
            Map.of()
        ));
        result.addAll(toKeep);

        return result;
    }

    private List<Message> selectLastUserMessagesBlock(List<Message> nonSystemMsgs) {
        int userCount = 0;
        int cutIndex = nonSystemMsgs.size();
        for (int i = nonSystemMsgs.size() - 1; i >= 0; i--) {
            if (nonSystemMsgs.get(i) instanceof UserMessage) {
                userCount++;
                if (userCount == keepLastUserMessages) {
                    cutIndex = i;
                    break;
                }
            }
        }
        if (cutIndex == 0) {
            return nonSystemMsgs;
        }
        if (userCount < keepLastUserMessages) {
            return nonSystemMsgs;
        }
        return nonSystemMsgs.subList(cutIndex, nonSystemMsgs.size());
    }

    private List<Message> maybeAggregateSystem(List<Message> systemMsgs, int totalSize) {
        if (systemMsgs.size() < SYSTEM_AGGREGATE_MIN) {
            return systemMsgs;
        }
        if ((double) systemMsgs.size() / totalSize < SYSTEM_AGGREGATE_THRESHOLD) {
            return systemMsgs;
        }

        var sb = new StringBuilder();
        sb.append(PromptRegistry.get("summarizing_sliding.aggregate_instruction")).append("\n\n");
        for (int i = 0; i < systemMsgs.size(); i++) {
            sb.append(i + 1).append(". ").append(systemMsgs.get(i).content()).append("\n");
        }

        var request = ChatRequest.builder()
            .messages(dev.langchain4j.data.message.UserMessage.from(sb.toString()))
            .build();
        ChatResponse response = summarizer.chat(request);
        String combined = response.aiMessage().text();

        return List.of(new SystemMessage(
            UUID.randomUUID().toString(),
            Instant.now(),
            combined,
            Map.of()
        ));
    }

    private String generateSummary(List<Message> messages) {
        var sb = new StringBuilder();
        sb.append(PromptRegistry.get("summarizing_sliding.instruction")).append("\n\n");
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

    private int estimateTokens(List<Message> messages) {
        return messages.stream()
            .mapToInt(m -> {
                String c = m.content();
                return (c != null ? c.length() : 0) / TOKEN_ESTIMATE_DIVISOR + 4;
            })
            .sum();
    }
}
