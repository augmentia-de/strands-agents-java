package com.strands.agents.core.internal;

import com.strands.agents.core.model.message.AssistantMessage;
import com.strands.agents.core.model.message.Message;
import com.strands.agents.core.model.message.SystemMessage;
import com.strands.agents.core.model.message.ToolMessage;
import com.strands.agents.core.model.message.UserMessage;
import com.strands.agents.core.model.tool.ToolCall;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
                new ToolMessage(id, now, m.text(), metadata, m.id());
            default -> throw new IllegalArgumentException(
                "Unknown message type: " + source.getClass()
            );
        };
    }
}
