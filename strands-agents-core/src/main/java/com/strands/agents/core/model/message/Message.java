package com.strands.agents.core.model.message;

import java.time.Instant;
import java.util.Map;

public sealed interface Message
    permits UserMessage, AssistantMessage, SystemMessage, ToolMessage {

    String id();

    Instant timestamp();

    String content();

    Map<String, Object> metadata();
}
