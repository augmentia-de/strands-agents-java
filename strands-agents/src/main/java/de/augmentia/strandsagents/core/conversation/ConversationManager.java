package de.augmentia.strandsagents.core.conversation;

import de.augmentia.strandsagents.model.message.Message;
import java.util.List;

/**
 * Strategy for pruning conversation history to manage context window limits.
 */
public sealed interface ConversationManager
    permits SlidingWindowConversationManager, SummarizingSlidingWindowConversationManager {

    /**
     * Prunes the given message list to fit within the conversation window constraints.
     */
    List<Message> prune(List<Message> messages);
}
