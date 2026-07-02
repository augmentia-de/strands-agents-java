package de.augmentia.strandsagents.core;

import dev.langchain4j.agent.tool.ToolSpecification;
import java.util.List;
import java.util.Optional;

@FunctionalInterface
public interface ToolDescriptionValidator {

    Optional<String> validate(ToolSpecification spec);

    static List<ToolDescriptionValidator> defaults() {
        return List.of(
            spec -> spec.description() == null || spec.description().isBlank()
                ? Optional.of("Tool '%s' has no description".formatted(spec.name()))
                : Optional.empty(),
            spec -> spec.description() != null && spec.description().length() < 10
                ? Optional.of("Tool '%s' description too short (%d chars)".formatted(spec.name(), spec.description().length()))
                : Optional.empty(),
            spec -> spec.description() != null && spec.description().length() > 2000
                ? Optional.of("Tool '%s' description too long (%d chars)".formatted(spec.name(), spec.description().length()))
                : Optional.empty()
        );
    }
}
