package de.augmentia.strandsagents.interceptor.guardrails;

import de.augmentia.strandsagents.core.Agent;
import de.augmentia.strandsagents.interceptor.plugin.Plugin;
import de.augmentia.strandsagents.prompt.PromptRegistry;
import java.util.List;

public class GuardrailPlugin implements Plugin {

    private final List<Guardrail> inputGuardrails;
    private final List<Guardrail> outputGuardrails;
    private final BlockAction blockAction;
    private final String fallbackMessage;
    private Agent agent;

    public GuardrailPlugin(List<Guardrail> inputGuardrails, List<Guardrail> outputGuardrails) {
        this(inputGuardrails, outputGuardrails, BlockAction.FALLBACK,
            PromptRegistry.getOrDefault("guardrail_plugin.fallback", "I cannot process this request."));
    }

    public GuardrailPlugin(List<Guardrail> inputGuardrails, List<Guardrail> outputGuardrails,
                           BlockAction blockAction, String fallbackMessage) {
        this.inputGuardrails = inputGuardrails;
        this.outputGuardrails = outputGuardrails;
        this.blockAction = blockAction;
        this.fallbackMessage = fallbackMessage;
    }

    @Override
    public String name() {
        return "guardrails";
    }

    @Override
    public void initAgent(Agent agent) {
        this.agent = agent;
    }

    @Override
    public List<Guardrail> getInputGuardrails() {
        return inputGuardrails;
    }

    @Override
    public List<Guardrail> getOutputGuardrails() {
        return outputGuardrails;
    }

    @Override
    public BlockAction getBlockAction() {
        return blockAction;
    }

    @Override
    public String getFallbackMessage() {
        return fallbackMessage;
    }

    public Agent agent() {
        return agent;
    }
}
