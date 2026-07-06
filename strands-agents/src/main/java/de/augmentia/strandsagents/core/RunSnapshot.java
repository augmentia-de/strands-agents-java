package de.augmentia.strandsagents.core;


import de.augmentia.strandsagents.interceptor.pipeline.HookRegistry;
import de.augmentia.strandsagents.skills.Skill;
import de.augmentia.strandsagents.model.structured.StructuredOutputConfig;
import java.util.List;

/**
 * Immutable snapshot of an Agent's runtime configuration at a point in time.
 * <p>
 * Created by {@link AgentRunConfig#snapshot()} at the top of every
 * {@code Agent.execute()} call. The agent loop reads exclusively from this
 * snapshot so that concurrent mutations to {@link AgentRunConfig} (e.g.
 * swapping tools or the system prompt via another thread) do not affect a
 * run that is already in progress.
 */
public record RunSnapshot(
    String systemPrompt,
    ToolRegistry toolRegistry,
    HookRegistry hookRegistry,
    AgentEventListener eventListener,
    StructuredOutputConfig structuredOutputConfig,
    int maxToolIterations,
    List<Skill> dynamicSkills
) {
}
