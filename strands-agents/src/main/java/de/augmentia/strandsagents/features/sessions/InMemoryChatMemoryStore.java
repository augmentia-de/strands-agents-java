package de.augmentia.strandsagents.features.sessions;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryChatMemoryStore implements ChatMemoryStore {

    private final Map<Object, List<ChatMessage>> store = new ConcurrentHashMap<>();

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        return store.getOrDefault(memoryId, List.of());
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        store.put(memoryId, List.copyOf(messages));
    }

    @Override
    public void deleteMessages(Object memoryId) {
        store.remove(memoryId);
    }
}
