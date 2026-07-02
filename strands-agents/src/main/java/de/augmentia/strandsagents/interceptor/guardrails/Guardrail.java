package de.augmentia.strandsagents.interceptor.guardrails;

import de.augmentia.strandsagents.model.message.Message;
import java.util.List;

public interface Guardrail {
    GuardrailResult validate(List<Message> messages, String context);
}
