package de.augmentia.strandsagents.skills;

import java.time.Instant;
import java.util.List;

public record SkillResolvedEvent(
    String skillName,
    List<String> declaredTools,
    List<String> resolvedTools,
    String resolveSource,
    Instant timestamp
) {
    public SkillResolvedEvent(String skillName, List<String> declaredTools, List<String> resolvedTools, String resolveSource) {
        this(skillName, declaredTools, resolvedTools, resolveSource, Instant.now());
    }
}
