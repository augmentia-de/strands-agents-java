package de.augmentia.strandsagents.quarkus.agui.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AguiEvent {

    public static final String RUN_STARTED = "RUN_STARTED";
    public static final String RUN_FINISHED = "RUN_FINISHED";
    public static final String RUN_ERROR = "RUN_ERROR";
    public static final String TEXT_MESSAGE_START = "TEXT_MESSAGE_START";
    public static final String TEXT_MESSAGE_CONTENT = "TEXT_MESSAGE_CONTENT";
    public static final String TEXT_MESSAGE_END = "TEXT_MESSAGE_END";
    public static final String TEXT_MESSAGE_CHUNK = "TEXT_MESSAGE_CHUNK";
    public static final String TOOL_CALL_START = "TOOL_CALL_START";
    public static final String TOOL_CALL_ARGS = "TOOL_CALL_ARGS";
    public static final String TOOL_CALL_END = "TOOL_CALL_END";
    public static final String TOOL_CALL_RESULT = "TOOL_CALL_RESULT";
    public static final String MESSAGES_SNAPSHOT = "MESSAGES_SNAPSHOT";

    public String type;
    public String threadId;
    public String runId;
    public String messageId;
    public String role;
    public String delta;
    public String toolCallId;
    public String toolCallName;
    public String parentMessageId;
    public String content;
    public String errorMessage;
    public String errorCode;
    public List<AguiMessage> messages;

    public static AguiEvent runStarted(String threadId, String runId) {
        var e = new AguiEvent();
        e.type = RUN_STARTED;
        e.threadId = threadId;
        e.runId = runId;
        return e;
    }

    public static AguiEvent runFinished(String threadId, String runId) {
        var e = new AguiEvent();
        e.type = RUN_FINISHED;
        e.threadId = threadId;
        e.runId = runId;
        return e;
    }

    public static AguiEvent runError(String message, String code) {
        var e = new AguiEvent();
        e.type = RUN_ERROR;
        e.errorMessage = message;
        e.errorCode = code;
        return e;
    }

    public static AguiEvent textMessageStart(String messageId, String role) {
        var e = new AguiEvent();
        e.type = TEXT_MESSAGE_START;
        e.messageId = messageId;
        e.role = role;
        return e;
    }

    public static AguiEvent textMessageContent(String messageId, String delta) {
        var e = new AguiEvent();
        e.type = TEXT_MESSAGE_CONTENT;
        e.messageId = messageId;
        e.delta = delta;
        return e;
    }

    public static AguiEvent textMessageEnd(String messageId) {
        var e = new AguiEvent();
        e.type = TEXT_MESSAGE_END;
        e.messageId = messageId;
        return e;
    }

    public static AguiEvent textMessageChunk(String messageId, String delta, String role) {
        var e = new AguiEvent();
        e.type = TEXT_MESSAGE_CHUNK;
        e.messageId = messageId;
        e.delta = delta;
        e.role = role;
        return e;
    }

    public static AguiEvent toolCallStart(String toolCallId, String toolCallName, String parentMessageId) {
        var e = new AguiEvent();
        e.type = TOOL_CALL_START;
        e.toolCallId = toolCallId;
        e.toolCallName = toolCallName;
        e.parentMessageId = parentMessageId;
        return e;
    }

    public static AguiEvent toolCallArgs(String toolCallId, String delta) {
        var e = new AguiEvent();
        e.type = TOOL_CALL_ARGS;
        e.toolCallId = toolCallId;
        e.delta = delta;
        return e;
    }

    public static AguiEvent toolCallEnd(String toolCallId) {
        var e = new AguiEvent();
        e.type = TOOL_CALL_END;
        e.toolCallId = toolCallId;
        return e;
    }

    public static AguiEvent toolCallResult(String toolCallId, String messageId, String content) {
        var e = new AguiEvent();
        e.type = TOOL_CALL_RESULT;
        e.toolCallId = toolCallId;
        e.messageId = messageId;
        e.content = content;
        return e;
    }

    public static AguiEvent messagesSnapshot(List<AguiMessage> messages) {
        var e = new AguiEvent();
        e.type = MESSAGES_SNAPSHOT;
        e.messages = messages;
        return e;
    }
}
