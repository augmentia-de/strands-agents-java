package com.strands.agents.core.resilience;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import java.util.List;

public class TokenRecovery {

    private static final int MAX_ATTEMPTS = 3;

    private int attempts;

    public TokenRecovery() {
        this.attempts = 0;
    }

    public static boolean isTokenLimitError(Exception e) {
        if (e == null) return false;
        var msg = e.getMessage();
        if (msg == null) return false;
        var lower = msg.toLowerCase();
        return lower.contains("maximum context length")
            || lower.contains("context_length_exceeded")
            || lower.contains("max_tokens")
            || lower.contains("token limit")
            || lower.contains("too many tokens")
            || lower.contains("request too large")
            || (lower.contains("400") && lower.contains("context"));
    }

    public boolean recover(ChatMemory chatMemory) {
        if (attempts >= MAX_ATTEMPTS) return false;
        attempts++;

        var messages = chatMemory.messages();
        if (messages.size() <= 1) return false;

        int keepCount = Math.max(2, messages.size() / 2);

        var kept = messages.subList(messages.size() - keepCount, messages.size());

        if (chatMemory instanceof MessageWindowChatMemory mwcm) {
            mwcm.clear();
            for (var msg : kept) {
                mwcm.add(msg);
            }
        }

        return true;
    }

    public int attempts() {
        return attempts;
    }

    public void reset() {
        attempts = 0;
    }
}
