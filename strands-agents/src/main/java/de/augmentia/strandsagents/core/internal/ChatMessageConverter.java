package de.augmentia.strandsagents.core.internal;

import de.augmentia.strandsagents.core.model.message.AssistantMessage;
import de.augmentia.strandsagents.core.model.message.Message;
import de.augmentia.strandsagents.core.model.message.SystemMessage;
import de.augmentia.strandsagents.core.model.message.ToolMessage;
import de.augmentia.strandsagents.core.model.message.UserMessage;
import de.augmentia.strandsagents.core.model.tool.ToolCall;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class ChatMessageConverter {

    public static List<Message> toDomainMessages(List<ChatMessage> source) {
        return source.stream()
            .map(ChatMessageConverter::toDomainMessage)
            .toList();
    }

    public static Message toDomainMessage(ChatMessage source) {
        var id = UUID.randomUUID().toString();
        var now = Instant.now();
        Map<String, Object> metadata = Map.of();

        return switch (source) {
            case dev.langchain4j.data.message.UserMessage m ->
                new UserMessage(id, now, m.singleText(), metadata);
            case AiMessage m -> new AssistantMessage(
                id, now, m.text(), metadata,
                m.hasToolExecutionRequests()
                    ? m.toolExecutionRequests().stream()
                        .map(t -> new ToolCall(t.id(), t.name(), t.arguments()))
                        .toList()
                    : List.of()
            );
            case dev.langchain4j.data.message.SystemMessage m ->
                new SystemMessage(id, now, m.text(), metadata);
            case ToolExecutionResultMessage m ->
                new ToolMessage(id, now, m.text(), metadata, m.id(), m.toolName());
            default -> throw new IllegalArgumentException(
                "Unknown message type: " + source.getClass()
            );
        };
    }

    public static List<ChatMessage> toLangChain4jMessages(List<Message> domainMessages) {
        return domainMessages.stream()
            .map(ChatMessageConverter::toLangChain4j)
            .collect(Collectors.toList());
    }

    public static ChatMessage toLangChain4j(Message domainMessage) {
        return switch (domainMessage) {
            case UserMessage m ->
                new dev.langchain4j.data.message.UserMessage(m.content());
            case AssistantMessage m -> {
                if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
                    var toolRequests = m.toolCalls().stream()
                        .map(tc -> ToolExecutionRequest.builder()
                            .id(tc.id())
                            .name(tc.toolName())
                            .arguments(tc.arguments())
                            .build())
                        .toList();
                    yield AiMessage.from(m.content(), toolRequests);
                }
                yield new dev.langchain4j.data.message.AiMessage(m.content());
            }
            case SystemMessage m ->
                new dev.langchain4j.data.message.SystemMessage(m.content());
            case ToolMessage m -> ToolExecutionResultMessage.from(
                ToolExecutionRequest.builder()
                    .id(m.toolCallId())
                    .name(m.toolName())
                    .build(),
                m.content()
            );
        };
    }
}
