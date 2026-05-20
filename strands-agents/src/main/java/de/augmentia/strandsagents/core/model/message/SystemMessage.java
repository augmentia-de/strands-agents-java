package de.augmentia.strandsagents.core.model.message;

import java.time.Instant;
import java.util.Map;

public record SystemMessage(
    String id,
    Instant timestamp,
    String content,
    Map<String, Object> metadata
) implements Message {}
