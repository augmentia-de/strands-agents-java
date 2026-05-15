package com.strands.agents.sessions;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class FileChatMemoryStore implements ChatMemoryStore {

    private final Path baseDir;

    public FileChatMemoryStore(Path baseDir) {
        this.baseDir = baseDir;
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new RuntimeException("Speicher-Verzeichnis kann nicht erstellt werden: " + baseDir, e);
        }
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        var file = resolve(memoryId);
        if (!Files.exists(file)) return List.of();
        try {
            var json = Files.readString(file);
            return ChatMessageDeserializer.messagesFromJson(json);
        } catch (IOException e) {
            throw new RuntimeException("Fehler beim Lesen von " + file, e);
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        var file = resolve(memoryId);
        try {
            var json = ChatMessageSerializer.messagesToJson(messages);
            Files.writeString(file, json);
        } catch (IOException e) {
            throw new RuntimeException("Fehler beim Schreiben von " + file, e);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        var file = resolve(memoryId);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new RuntimeException("Fehler beim Löschen von " + file, e);
        }
    }

    private Path resolve(Object memoryId) {
        return baseDir.resolve(memoryId.toString().replaceAll("[^a-zA-Z0-9._-]", "_") + ".json");
    }
}
