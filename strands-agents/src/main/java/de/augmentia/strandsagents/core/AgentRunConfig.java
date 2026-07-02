package de.augmentia.strandsagents.core;


import de.augmentia.strandsagents.interceptor.pipeline.HookRegistry;
import de.augmentia.strandsagents.skills.Skill;
import de.augmentia.strandsagents.model.structured.StructuredOutputConfig;
import java.util.ArrayList;
import java.util.List;

/**
 * Mutable holder for an Agent's runtime-configurable settings.
 * <p>
 * {@code AgentRunConfig} lives alongside the Agent and stays writable so that
 * configuration can be changed between executions (e.g. swapping tools or
 * the system prompt). At the start of each {@code execute()} call the Agent
 * calls {@link #snapshot()} to freeze an immutable {@link RunSnapshot} that
 * is then used for the entire agent loop. This guarantees that a single run
 * sees a consistent view even if a concurrent thread mutates the config.
 * <p>
 * Thread safety: individual fields use {@code volatile} for visibility;
 * {@link #dynamicSkills} is synchronised on {@code this}.
 */
public class AgentRunConfig {

    private volatile String systemPrompt = "";
    private volatile ToolRegistry toolRegistry;
    private volatile HookRegistry hookRegistry;
    private volatile AgentEventListener eventListener;
    private volatile StructuredOutputConfig structuredOutputConfig;
    private final List<Skill> dynamicSkills = new ArrayList<>();

    public AgentRunConfig() {
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public ToolRegistry getToolRegistry() {
        return toolRegistry;
    }

    public void setToolRegistry(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public HookRegistry getHookRegistry() {
        return hookRegistry;
    }

    public void setHookRegistry(HookRegistry hookRegistry) {
        this.hookRegistry = hookRegistry;
    }

    public AgentEventListener getEventListener() {
        return eventListener;
    }

    public void setEventListener(AgentEventListener eventListener) {
        this.eventListener = eventListener;
    }

    public StructuredOutputConfig getStructuredOutputConfig() {
        return structuredOutputConfig;
    }

    public void setStructuredOutputConfig(StructuredOutputConfig structuredOutputConfig) {
        this.structuredOutputConfig = structuredOutputConfig;
    }

    public synchronized List<Skill> getDynamicSkills() {
        return List.copyOf(dynamicSkills);
    }

    public synchronized void setDynamicSkills(List<Skill> skills) {
        this.dynamicSkills.clear();
        if (skills != null) {
            this.dynamicSkills.addAll(skills);
        }
    }

    public synchronized void addSkill(Skill skill) {
        if (skill != null) {
            this.dynamicSkills.add(skill);
        }
    }

    /**
     * Captures all current config values into an immutable {@link RunSnapshot}.
     * Called once at the top of the agent execution loop so the entire run
     * uses a consistent configuration view, unaffected by concurrent mutations.
     *
     * @return a frozen snapshot of the current configuration
     */
    public RunSnapshot snapshot() {
        return new RunSnapshot(
            systemPrompt,
            toolRegistry,
            hookRegistry,
            eventListener,
            structuredOutputConfig,
            getDynamicSkills()
        );
    }
}
