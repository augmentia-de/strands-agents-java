package de.augmentia.strandsagents.model.message;

import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.Instant;
import java.util.Map;

@JsonTypeInfo(use = JsonTypeInfo.Id.CUSTOM, property = "_type")
@JsonTypeIdResolver(MessageTypeIdResolver.class)
public sealed interface Message
    permits UserMessage, AssistantMessage, SystemMessage, ToolMessage {

    String id();

    Instant timestamp();

    String content();

    Map<String, Object> metadata();
}
