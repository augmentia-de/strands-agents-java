package de.augmentia.strandsagents.model.message;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A chat message with an id, timestamp, delegate ChatMessage, and metadata.
 */
@JsonSerialize(using = MessageSerializer.class)
@JsonDeserialize(using = MessageDeserializer.class)
public record Message(
    String id,
    Instant timestamp,
    ChatMessage delegate,
    Map<String, Object> metadata
) {
    /** Returns the text content of the delegate message. */
    public String content() {
        return switch (delegate) {
            case dev.langchain4j.data.message.UserMessage m -> m.singleText();
            case AiMessage m -> m.text();
            case dev.langchain4j.data.message.SystemMessage m -> m.text();
            case ToolExecutionResultMessage m -> m.text();
            default -> null;
        };
    }

    /** Checks if the delegate is a system message. */
    public boolean isSystem() {
        return delegate instanceof dev.langchain4j.data.message.SystemMessage;
    }

    /** Checks if the delegate is a user message. */
    public boolean isUser() {
        return delegate instanceof dev.langchain4j.data.message.UserMessage;
    }

    /** Checks if the delegate is an AI/assistant message. */
    public boolean isAssistant() {
        return delegate instanceof AiMessage;
    }

    /** Checks if the delegate is a tool execution result message. */
    public boolean isToolResult() {
        return delegate instanceof ToolExecutionResultMessage;
    }

    /** Returns tool execution requests from the delegate AI message. */
    public List<ToolExecutionRequest> toolExecutionRequests() {
        if (delegate instanceof AiMessage ai && ai.hasToolExecutionRequests()) {
            return ai.toolExecutionRequests();
        }
        return List.of();
    }

    /** Checks if the delegate AI message has tool execution requests. */
    public boolean hasToolExecutionRequests() {
        return delegate instanceof AiMessage ai && ai.hasToolExecutionRequests();
    }

    /** Returns the tool name from a tool execution result message. */
    public String toolName() {
        if (delegate instanceof ToolExecutionResultMessage t) return t.toolName();
        return null;
    }

    /** Returns the tool call ID from a tool execution result message. */
    public String toolCallId() {
        if (delegate instanceof ToolExecutionResultMessage t) return t.id();
        return null;
    }

    /** Returns the underlying ChatMessage delegate. */
    public ChatMessage toChatMessage() {
        return delegate;
    }

    /** Creates a user message with the given id, timestamp, text, and metadata. */
    public static Message user(String id, Instant timestamp, String text, Map<String, Object> metadata) {
        var userMsg = text != null
            ? dev.langchain4j.data.message.UserMessage.from(text)
            : new dev.langchain4j.data.message.UserMessage("");
        return new Message(id, timestamp, userMsg, metadata);
    }

    /** Creates a user message with a generated id and current timestamp. */
    public static Message user(String text) {
        return user(UUID.randomUUID().toString(), Instant.now(), text, Map.of());
    }

    /** Creates a system message with the given id, timestamp, text, and metadata. */
    public static Message system(String id, Instant timestamp, String text, Map<String, Object> metadata) {
        return new Message(id, timestamp, new dev.langchain4j.data.message.SystemMessage(text), metadata);
    }

    /** Creates a system message with a generated id and current timestamp. */
    public static Message system(String text) {
        return system(UUID.randomUUID().toString(), Instant.now(), text, Map.of());
    }

    /** Creates an assistant message with the given id, timestamp, text, metadata, and tool requests. */
    public static Message assistant(String id, Instant timestamp, String text,
            Map<String, Object> metadata, List<ToolExecutionRequest> tools) {
        var ai = tools != null && !tools.isEmpty()
            ? AiMessage.from(text, tools)
            : new AiMessage(text);
        return new Message(id, timestamp, ai, metadata);
    }

    /** Creates an assistant message with a generated id and current timestamp. */
    public static Message assistant(String text) {
        return assistant(UUID.randomUUID().toString(), Instant.now(), text, Map.of(), List.of());
    }

    /** Creates an assistant message with tool requests, a generated id, and current timestamp. */
    public static Message assistant(String text, List<ToolExecutionRequest> tools) {
        return assistant(UUID.randomUUID().toString(), Instant.now(), text, Map.of(), tools);
    }

    /** Creates a tool result message with the given id, timestamp, text, metadata, tool call ID, and tool name. */
    public static Message toolResult(String id, Instant timestamp, String text,
            Map<String, Object> metadata, String toolCallId, String toolName) {
        var request = ToolExecutionRequest.builder()
            .id(toolCallId)
            .name(toolName)
            .build();
        return new Message(id, timestamp, ToolExecutionResultMessage.from(request, text), metadata);
    }

    /** Creates a tool result message with a generated id and current timestamp. */
    public static Message toolResult(String text, String toolCallId, String toolName) {
        return toolResult(UUID.randomUUID().toString(), Instant.now(), text,
            Map.of(), toolCallId, toolName);
    }

    /** Wraps a ChatMessage in a Message with a generated id and current timestamp. */
    public static Message from(ChatMessage cm) {
        return new Message(UUID.randomUUID().toString(), Instant.now(), cm, Map.of());
    }
}
