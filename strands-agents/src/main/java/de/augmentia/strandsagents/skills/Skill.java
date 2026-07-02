package de.augmentia.strandsagents.skills;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Skill(
    String name,
    String description,
    String instructions,
    Path path,
    List<String> allowedTools,
    Map<String, Object> metadata,
    String license,
    String compatibility,
    List<String> declaredTools
) {
    public Skill {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Skill name must not be empty");
        if (description == null || description.isBlank())
            throw new IllegalArgumentException("Skill description must not be empty");
        if (declaredTools == null) declaredTools = List.of();
    }
}
