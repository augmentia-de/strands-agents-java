package com.strands.agents.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import java.lang.reflect.Method;
import java.util.*;

public class ToolRegistry {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, ToolMethod> tools = new LinkedHashMap<>();

    public void register(Object toolInstance) {
        for (Method method : toolInstance.getClass().getMethods()) {
            if (method.isAnnotationPresent(Tool.class)) {
                var spec = ToolSpecifications.toolSpecificationFrom(method);
                tools.put(spec.name(), new JavaToolMethod(toolInstance, method, spec));
            }
        }
    }

    public void register(String name, Object toolInstance, Method method) {
        var spec = ToolSpecifications.toolSpecificationFrom(method);
        tools.put(name, new JavaToolMethod(toolInstance, method, spec));
    }

    public void register(String name, ToolSpecification spec, ToolMethod toolMethod) {
        tools.put(name, toolMethod);
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

    public interface ToolMethod {

        ToolSpecification spec();

        String execute(String jsonArguments) throws Exception;
    }

    record JavaToolMethod(Object instance, Method method, ToolSpecification spec)
            implements ToolMethod {

        @Override
        public String execute(String jsonArguments) throws Exception {
            var params = method.getParameters();
            if (params.length == 0) {
                return String.valueOf(method.invoke(instance));
            }
            Map<String, Object> argsMap = MAPPER.readValue(
                jsonArguments, new TypeReference<Map<String, Object>>() {});
            var args = new Object[params.length];
            for (int i = 0; i < params.length; i++) {
                var paramName = params[i].getName();
                var value = argsMap.get(paramName);
                if (value == null) {
                    throw new IllegalArgumentException("Fehlendes Argument: " + paramName);
                }
                args[i] = convertValue(value, params[i].getType());
            }
            return String.valueOf(method.invoke(instance, args));
        }

        private static Object convertValue(Object value, Class<?> targetType) {
            if (targetType == String.class) return String.valueOf(value);
            if (targetType == int.class || targetType == Integer.class)
                return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(value.toString());
            if (targetType == long.class || targetType == Long.class)
                return value instanceof Number ? ((Number) value).longValue() : Long.parseLong(value.toString());
            if (targetType == double.class || targetType == Double.class)
                return value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(value.toString());
            if (targetType == boolean.class || targetType == Boolean.class)
                return value instanceof Boolean ? value : Boolean.parseBoolean(value.toString());
            return value;
        }
    }
}
