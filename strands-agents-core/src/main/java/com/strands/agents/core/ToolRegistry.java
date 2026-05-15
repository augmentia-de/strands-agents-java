package com.strands.agents.core;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import java.lang.reflect.Method;
import java.util.*;

public class ToolRegistry {

    private final Map<String, ToolMethod> tools = new LinkedHashMap<>();

    public void register(Object toolInstance) {
        for (Method method : toolInstance.getClass().getMethods()) {
            if (method.isAnnotationPresent(Tool.class)) {
                var spec = ToolSpecifications.toolSpecificationFrom(method);
                tools.put(spec.name(), new ToolMethod(toolInstance, method, spec));
            }
        }
    }

    public void register(String name, Object toolInstance, Method method) {
        var spec = ToolSpecifications.toolSpecificationFrom(method);
        tools.put(name, new ToolMethod(toolInstance, method, spec));
    }

    public ToolMethod get(String name) {
        var tm = tools.get(name);
        if (tm == null) {
            throw new IllegalArgumentException("Unbekanntes Tool: " + name);
        }
        return tm;
    }

    public List<ToolSpecification> getSpecifications() {
        return tools.values().stream()
            .map(ToolMethod::spec)
            .toList();
    }

    public Set<String> getToolNames() {
        return tools.keySet();
    }

    public int size() {
        return tools.size();
    }

    public record ToolMethod(Object instance, Method method, ToolSpecification spec) {}
}
