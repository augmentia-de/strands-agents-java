package de.augmentia.strandsagents.interceptor.gate;

import java.lang.reflect.Method;

public interface GateEvaluator {
    boolean isOpen(Method pluginMethod, Gate gate);
    void recordExecution(Method pluginMethod, Gate gate, boolean success);
}
