package de.augmentia.strandsagents.features.context;

import java.util.Map;

public class AgentContext {

    public static final ThreadLocal<Map<String, Object>> SESSION = new ThreadLocal<>();
    public static final ThreadLocal<String> SESSION_ID = new ThreadLocal<>();

    private AgentContext() {}
}
