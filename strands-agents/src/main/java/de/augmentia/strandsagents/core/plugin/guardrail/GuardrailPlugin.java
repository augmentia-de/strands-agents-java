package de.augmentia.strandsagents.core.plugin.guardrail;

import de.augmentia.strandsagents.core.agent.Agent;
import de.augmentia.strandsagents.core.plugin.Plugin;

import java.util.List;

public class GuardrailPlugin implements Plugin {

    private final List<Guardrail> inputGuardrails;
    private final List<Guardrail> outputGuardrails;
    private final BlockAction blockAction;
    private final String fallbackMessage;
    private Agent agent;

    public GuardrailPlugin(List<Guardrail> inputGuardrails, List<Guardrail> outputGuardrails) {
        this(inputGuardrails, outputGuardrails, BlockAction.FALLBACK, "Diese Anfrage kann ich nicht bearbeiten.");
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

    public List<Guardrail> inputGuardrails() {
        return inputGuardrails;
    }

    public List<Guardrail> outputGuardrails() {
        return outputGuardrails;
    }

    public BlockAction blockAction() {
        return blockAction;
    }

    public String fallbackMessage() {
        return fallbackMessage;
    }

    public Agent agent() {
        return agent;
    }
}
