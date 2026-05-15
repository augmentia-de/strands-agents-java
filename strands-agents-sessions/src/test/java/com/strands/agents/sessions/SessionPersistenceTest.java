package com.strands.agents.sessions;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionPersistenceTest {

    @Test
    void inMemoryStoreShouldPersistAndRetrieveMessages() {
        var store = new InMemoryChatMemoryStore();
        var messages = List.of(UserMessage.from("Hallo"), AiMessage.from("Hi"));

        store.updateMessages("session-1", messages);
        var loaded = store.getMessages("session-1");

        assertThat(loaded).hasSize(2);
        assertThat(loaded.get(0).type().name()).isEqualTo("USER");
    }

    @Test
    void inMemoryStoreShouldReturnEmptyForUnknownSession() {
        var store = new InMemoryChatMemoryStore();
        assertThat(store.getMessages("unknown")).isEmpty();
    }

    @Test
    void inMemoryStoreShouldDeleteSession() {
        var store = new InMemoryChatMemoryStore();
        store.updateMessages("s1", List.of(UserMessage.from("test")));
        store.deleteMessages("s1");
        assertThat(store.getMessages("s1")).isEmpty();
    }

    @Test
    void fileStoreShouldPersistAndRetrieveMessages(@TempDir Path tempDir) {
        var store = new FileChatMemoryStore(tempDir);

        var messages = List.of(
            UserMessage.from("Wer bist du?"),
            AiMessage.from("Ich bin ein Agent.")
        );
        store.updateMessages("session-1", messages);

        var loaded = store.getMessages("session-1");
        assertThat(loaded).hasSize(2);
        assertThat(loaded.get(1).type().name()).isEqualTo("AI");
    }

    @Test
    void fileStoreShouldSurviveRestore(@TempDir Path tempDir) {
        var messages = List.<ChatMessage>of(UserMessage.from("Hallo"));

        new FileChatMemoryStore(tempDir).updateMessages("s1", messages);
        var loaded = new FileChatMemoryStore(tempDir).getMessages("s1");

        assertThat(loaded).hasSize(1);
    }

    @Test
    void fileStoreShouldDeleteSession(@TempDir Path tempDir) throws IOException {
        var store = new FileChatMemoryStore(tempDir);
        store.updateMessages("delete-me", List.<ChatMessage>of(UserMessage.from("test")));
        store.deleteMessages("delete-me");

        assertThat(Files.list(tempDir)).isEmpty();
    }

    @Test
    void fileStoreShouldHandleSpecialCharsInSessionId(@TempDir Path tempDir) {
        var store = new FileChatMemoryStore(tempDir);
        store.updateMessages("user@host!session", List.<ChatMessage>of(UserMessage.from("test")));
        assertThat(store.getMessages("user@host!session")).isNotEmpty();
    }
}
