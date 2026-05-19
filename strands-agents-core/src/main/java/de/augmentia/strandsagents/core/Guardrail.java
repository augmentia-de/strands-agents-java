package de.augmentia.strandsagents.core;

import de.augmentia.strandsagents.core.model.message.Message;
import java.util.List;

public interface Guardrail {
    GuardrailResult validate(List<Message> messages, String context);
}
