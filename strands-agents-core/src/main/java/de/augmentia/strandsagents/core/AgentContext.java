package de.augmentia.strandsagents.core;

import java.util.Map;

public class AgentContext {

    public static final ScopedValue<Map<String, Object>> SESSION = ScopedValue.newInstance();

    private AgentContext() {}
}
