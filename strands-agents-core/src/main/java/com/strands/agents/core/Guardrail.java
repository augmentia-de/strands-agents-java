package com.strands.agents.core;

import com.strands.agents.core.model.message.Message;
import java.util.List;

public interface Guardrail {
    GuardrailResult validate(List<Message> messages, String context);
}
