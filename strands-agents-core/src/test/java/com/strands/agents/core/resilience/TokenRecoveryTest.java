package com.strands.agents.core.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.junit.jupiter.api.Test;

class TokenRecoveryTest {

    @Test
    void shouldDetectTokenLimitErrors() {
        assertThat(TokenRecovery.isTokenLimitError(
            new RuntimeException("maximum context length exceeded"))).isTrue();
        assertThat(TokenRecovery.isTokenLimitError(
            new RuntimeException("context_length_exceeded"))).isTrue();
        assertThat(TokenRecovery.isTokenLimitError(
            new RuntimeException("max_tokens exceeded"))).isTrue();
        assertThat(TokenRecovery.isTokenLimitError(
            new RuntimeException("token limit reached"))).isTrue();
        assertThat(TokenRecovery.isTokenLimitError(
            new RuntimeException("too many tokens"))).isTrue();
        assertThat(TokenRecovery.isTokenLimitError(
            new RuntimeException("request too large"))).isTrue();
    }

    @Test
    void shouldNotDetectNonTokenErrors() {
        assertThat(TokenRecovery.isTokenLimitError(
            new RuntimeException("timeout"))).isFalse();
        assertThat(TokenRecovery.isTokenLimitError(
            new IllegalArgumentException("bad request"))).isFalse();
        assertThat(TokenRecovery.isTokenLimitError(null)).isFalse();
    }

    @Test
    void shouldRemoveHalfOfOldestMessages() {
        var memory = MessageWindowChatMemory.builder().maxMessages(100).build();
        for (int i = 1; i <= 10; i++) {
            memory.add(UserMessage.from("msg-" + i));
        }
        assertThat(memory.messages()).hasSize(10);

        var recovery = new TokenRecovery();
        var result = recovery.recover(memory);

        assertThat(result).isTrue();
        assertThat(memory.messages()).hasSize(5);
        assertThat(((UserMessage) memory.messages().get(0)).singleText()).isEqualTo("msg-6");
    }

    @Test
    void shouldKeepAtLeastTwoMessages() {
        var memory = MessageWindowChatMemory.builder().maxMessages(100).build();
        memory.add(UserMessage.from("msg-1"));
        memory.add(UserMessage.from("msg-2"));
        memory.add(UserMessage.from("msg-3"));

        var recovery = new TokenRecovery();
        recovery.recover(memory);

        assertThat(memory.messages()).hasSize(2);
    }

    @Test
    void shouldLimitRecoveryAttempts() {
        var memory = MessageWindowChatMemory.builder().maxMessages(100).build();
        for (int i = 1; i <= 10; i++) {
            memory.add(UserMessage.from("msg-" + i));
        }

        var recovery = new TokenRecovery();
        assertThat(recovery.recover(memory)).isTrue();
        assertThat(recovery.recover(memory)).isTrue();
        assertThat(recovery.recover(memory)).isTrue();
        assertThat(recovery.recover(memory)).isFalse();
        assertThat(recovery.attempts()).isEqualTo(3);
    }

    @Test
    void shouldHandleEmptyMemory() {
        var memory = MessageWindowChatMemory.builder().maxMessages(100).build();
        var recovery = new TokenRecovery();
        assertThat(recovery.recover(memory)).isFalse();
    }

    @Test
    void shouldResetAttempts() {
        var memory = MessageWindowChatMemory.builder().maxMessages(100).build();
        for (int i = 1; i <= 10; i++) {
            memory.add(UserMessage.from("msg-" + i));
        }

        var recovery = new TokenRecovery();
        recovery.recover(memory);
        recovery.recover(memory);
        recovery.recover(memory);
        assertThat(recovery.recover(memory)).isFalse();

        recovery.reset();
        assertThat(recovery.attempts()).isZero();
    }
}
