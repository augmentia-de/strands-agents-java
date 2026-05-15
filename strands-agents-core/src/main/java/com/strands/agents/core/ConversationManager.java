package com.strands.agents.core;

import com.strands.agents.core.model.message.Message;
import java.util.List;

public sealed interface ConversationManager
    permits SlidingWindowConversationManager, SummarizingConversationManager {

    List<Message> prune(List<Message> messages);
}
