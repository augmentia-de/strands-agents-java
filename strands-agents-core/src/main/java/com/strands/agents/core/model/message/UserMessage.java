package com.strands.agents.core.model.message;

import java.time.Instant;
import java.util.Map;

public record UserMessage(
    String id,
    Instant timestamp,
    String content,
    Map<String, Object> metadata
) implements Message {}
