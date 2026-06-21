package de.augmentia.strandsagents.features.conversation;

import de.augmentia.strandsagents.model.message.Message;
import java.util.List;

public sealed interface ConversationManager
    permits SlidingWindowConversationManager, SummarizingSlidingWindowConversationManager {

    List<Message> prune(List<Message> messages);
}
