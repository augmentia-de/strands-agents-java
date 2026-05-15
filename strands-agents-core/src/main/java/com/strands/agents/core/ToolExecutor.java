package com.strands.agents.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.strands.agents.core.model.tool.ToolExecutionResult;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class ToolExecutor {

    private static final ExecutorService VIRTUAL_EXECUTOR =
        Executors.newVirtualThreadPerTaskExecutor();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final long timeoutSeconds;

    public ToolExecutor() {
        this(30);
    }

    public ToolExecutor(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public List<ToolExecutionResult> executeAll(
            List<ToolExecutionRequest> requests,
            ToolRegistry registry) throws Exception {

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            List<StructuredTaskScope.Subtask<ToolExecutionResult>> subtasks = requests.stream()
                .map(req -> scope.fork(() -> executeSingle(req, registry)))
                .toList();

            scope.join();
            scope.throwIfFailed();

            return subtasks.stream()
                .map(StructuredTaskScope.Subtask::get)
                .toList();
        }
    }

    ToolExecutionResult executeSingle(ToolExecutionRequest request, ToolRegistry registry)
            throws Exception {

        var toolMethod = registry.get(request.name());
        var method = toolMethod.method();
        var instance = toolMethod.instance();
        var args = parseArguments(request.arguments(), method);

        var future = VIRTUAL_EXECUTOR.submit(() -> method.invoke(instance, args));
        Object result;
        try {
            result = future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return new ToolExecutionResult(
                request.id(), request.name(), "Timeout nach " + timeoutSeconds + "s", true);
        }

        return new ToolExecutionResult(
            request.id(), request.name(), String.valueOf(result), false);
    }

    private Object[] parseArguments(String jsonArguments, Method method) {
        var params = method.getParameters();
        if (params.length == 0) return new Object[0];

        try {
            Map<String, Object> argsMap = MAPPER.readValue(
                jsonArguments, new TypeReference<Map<String, Object>>() {});
            var result = new Object[params.length];
            for (int i = 0; i < params.length; i++) {
                var paramName = params[i].getName();
                var value = argsMap.get(paramName);
                if (value == null) {
                    throw new IllegalArgumentException("Fehlendes Argument: " + paramName);
                }
                result[i] = convertValue(value, params[i].getType());
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Fehler beim Parsen der Tool-Argumente: " + jsonArguments, e);
        }
    }

    private Object convertValue(Object value, Class<?> targetType) {
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

    public void shutdown() {
        VIRTUAL_EXECUTOR.shutdown();
    }
}
