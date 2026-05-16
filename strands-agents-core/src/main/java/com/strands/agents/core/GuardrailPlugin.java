package com.strands.agents.core;

import com.strands.agents.core.model.agent.StopReason;
import com.strands.agents.core.model.event.BeforeInvocationEvent;
import java.util.List;

public class GuardrailPlugin implements Plugin {

    private final List<Guardrail> inputGuardrails;
    private final List<Guardrail> outputGuardrails;
    private final BlockAction blockAction;
    private final String fallbackMessage;
    private StrandsAgent agent;

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
    public void initAgent(StrandsAgent strandsAgent) {
        this.agent = strandsAgent;
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

    public StrandsAgent agent() {
        return agent;
    }
}
