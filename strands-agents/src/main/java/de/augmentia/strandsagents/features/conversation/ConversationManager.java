package de.augmentia.strandsagents.features.conversation;

import de.augmentia.strandsagents.model.message.Message;
import java.util.List;

public sealed interface ConversationManager
    permits SlidingWindowConversationManager, SummarizingConversationManager {

    List<Message> prune(List<Message> messages);
}
