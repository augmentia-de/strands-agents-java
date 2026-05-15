package com.strands.agents.core.model.message;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.Instant;
import java.util.Map;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "_type")
public sealed interface Message
    permits UserMessage, AssistantMessage, SystemMessage, ToolMessage {

    String id();

    Instant timestamp();

    String content();

    Map<String, Object> metadata();
}
