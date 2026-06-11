package de.augmentia.strandsagents.features.conversation;

import de.augmentia.strandsagents.model.message.Message;
import java.util.List;

public record SlidingWindowConversationManager(int windowSize) implements ConversationManager {

    public SlidingWindowConversationManager {
        if (windowSize < 1) {
            throw new IllegalArgumentException("windowSize must be >= 1, got: " + windowSize);
        }
    }

    @Override
    public List<Message> prune(List<Message> messages) {
        if (messages.size() <= windowSize) {
            return messages;
        }
        return messages.subList(messages.size() - windowSize, messages.size());
    }
}
