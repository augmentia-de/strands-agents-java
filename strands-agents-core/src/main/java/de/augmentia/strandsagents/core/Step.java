package de.augmentia.strandsagents.core;

import java.util.List;

public record Step(
    String id,
    String description,
    String toolName,
    String argumentsTemplate,
    List<String> dependsOn,
    boolean optional
) {

    public Step(String id, String description, String toolName, String argumentsTemplate) {
        this(id, description, toolName, argumentsTemplate, List.of(), false);
    }

    public Step(String id, String description, String toolName, String argumentsTemplate, boolean optional) {
        this(id, description, toolName, argumentsTemplate, List.of(), optional);
    }
}
