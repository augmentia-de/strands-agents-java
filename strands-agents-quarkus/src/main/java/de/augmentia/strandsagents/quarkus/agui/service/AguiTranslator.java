package de.augmentia.strandsagents.quarkus.agui.service;

import de.augmentia.strandsagents.core.AgentEventListener;
import de.augmentia.strandsagents.model.event.*;
import de.augmentia.strandsagents.quarkus.agui.dto.AguiEvent;
import de.augmentia.strandsagents.quarkus.agui.dto.AguiMessage;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class AguiTranslator implements AgentEventListener, Consumer<String>, AutoCloseable {

    private final String threadId;
    private final String runId;
    private final Queue<AguiEvent> eventQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger messageCounter = new AtomicInteger(0);
    private final AtomicInteger toolCallCounter = new AtomicInteger(0);
    private String currentMessageId;
    private boolean closed;

    public AguiTranslator(String threadId, String runId) {
        this.threadId = threadId;
        this.runId = runId;
    }

    public Queue<AguiEvent> eventQueue() {
        return eventQueue;
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void onEvent(AgentEvent event) {
        if (closed) return;
        switch (event) {
            case AgentStartedEvent e -> enqueue(AguiEvent.runStarted(threadId, runId));
            case ToolExecutionStartedEvent e -> onToolExecutionStarted(e);
            case ToolExecutionFinishedEvent e -> onToolExecutionFinished(e);
            case AgentFinishedEvent e -> onAgentFinished(e);
            default -> {}
        }
    }

    @Override
    public void accept(String token) {
        if (closed || token == null || token.isBlank()) return;
        if (currentMessageId == null) {
            currentMessageId = "msg_" + messageCounter.incrementAndGet();
            enqueue(AguiEvent.textMessageStart(currentMessageId, "assistant"));
        }
        enqueue(AguiEvent.textMessageContent(currentMessageId, token));
    }

    private void onToolExecutionStarted(ToolExecutionStartedEvent e) {
        closeCurrentMessage();
        var toolCall = e.toolCall();
        var tcId = "tc_" + toolCallCounter.incrementAndGet();
        enqueue(AguiEvent.toolCallStart(tcId, toolCall.toolName(), currentMessageId));
        enqueue(AguiEvent.toolCallArgs(tcId, toolCall.arguments()));
        enqueue(AguiEvent.toolCallEnd(tcId));
    }

    private void onToolExecutionFinished(ToolExecutionFinishedEvent e) {
        var tcId = "tc_" + toolCallCounter.get();
        var result = e.result();
        enqueue(AguiEvent.toolCallResult(tcId, currentMessageId,
            result.isError() ? "Error: " + result.result() : result.result()));
    }

    private void onAgentFinished(AgentFinishedEvent e) {
        closeCurrentMessage();
        var messages = buildMessagesSnapshot();
        enqueue(AguiEvent.messagesSnapshot(messages));
        enqueue(AguiEvent.runFinished(threadId, runId));
        closed = true;
    }

    public void onError(Throwable error) {
        closeCurrentMessage();
        enqueue(AguiEvent.runError(error.getMessage(), "STRANDS_ERROR"));
        closed = true;
    }

    private void closeCurrentMessage() {
        if (currentMessageId != null) {
            enqueue(AguiEvent.textMessageEnd(currentMessageId));
            currentMessageId = null;
        }
    }

    private List<AguiMessage> buildMessagesSnapshot() {
        var msg = new AguiMessage();
        msg.id = currentMessageId != null ? currentMessageId : "msg_" + messageCounter.get();
        msg.role = "assistant";
        return List.of(msg);
    }

    private void enqueue(AguiEvent event) {
        eventQueue.add(event);
    }

    @Override
    public void close() {
        closed = true;
        eventQueue.clear();
    }
}
