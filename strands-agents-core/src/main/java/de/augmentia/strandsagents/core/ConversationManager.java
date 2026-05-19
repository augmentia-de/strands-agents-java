package de.augmentia.strandsagents.core;

import de.augmentia.strandsagents.core.model.message.Message;
import java.util.List;

public sealed interface ConversationManager
    permits SlidingWindowConversationManager, SummarizingConversationManager {

    List<Message> prune(List<Message> messages);
}
