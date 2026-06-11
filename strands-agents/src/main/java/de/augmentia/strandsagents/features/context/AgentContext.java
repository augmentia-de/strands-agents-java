package de.augmentia.strandsagents.features.context;

import java.util.Map;

public class AgentContext {

    public static final ThreadLocal<Map<String, Object>> SESSION = new ThreadLocal<>();

    private AgentContext() {}
}
