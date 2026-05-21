package de.augmentia.strandsagents.core.plugin.guardrail;

import de.augmentia.strandsagents.core.hook.AgentHook;
import de.augmentia.strandsagents.core.hook.HookContexts;
import de.augmentia.strandsagents.core.hook.HookResult;
import de.augmentia.strandsagents.core.internal.ChatMessageConverter;
import de.augmentia.strandsagents.core.model.message.Message;
import java.util.List;

public class GuardrailHook implements AgentHook {

    private final List<Guardrail> inputGuardrails;
    private final List<Guardrail> outputGuardrails;
    private final BlockAction blockAction;
    private final String fallbackMessage;

    public GuardrailHook(List<Guardrail> inputGuardrails, List<Guardrail> outputGuardrails) {
        this(inputGuardrails, outputGuardrails, BlockAction.FALLBACK, "This request cannot be processed.");
    }

    public GuardrailHook(List<Guardrail> inputGuardrails, List<Guardrail> outputGuardrails,
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
    public HookResult beforeModelCall(HookContexts.BeforeModelCallContext ctx) {
        for (var g : inputGuardrails) {
            var result = g.validate(ctx.messages(), "input");
            if (!result.pass()) {
                return switch (blockAction) {
                    case THROW -> new HookResult.Cancel(result.reason());
                    case FALLBACK -> {
                        ctx.systemPrompt().setLength(0);
                        ctx.systemPrompt().append(fallbackMessage);
                        yield new HookResult.Modify<>("");
                    }
                    case ESCALATE -> new HookResult.Cancel("Escalation not implemented via hook");
                };
            }
        }
        return new HookResult.Continue();
    }

    @Override
    public HookResult afterModelCall(HookContexts.AfterModelCallContext ctx, String response) {
        var domainMessages = ChatMessageConverter.toDomainMessages(List.of());
        for (var g : outputGuardrails) {
            var result = g.validate(domainMessages, "output:" + response);
            if (!result.pass()) {
                return switch (blockAction) {
                    case THROW -> new HookResult.Cancel(result.reason());
                    case FALLBACK -> new HookResult.Modify<>(fallbackMessage);
                    case ESCALATE -> new HookResult.Cancel("Escalation not implemented via hook");
                };
            }
        }
        return new HookResult.Continue();
    }
}
