package de.augmentia.strandsagents.mcp.server;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ToolScanner {
    private static final Logger log = LoggerFactory.getLogger(ToolScanner.class);

    static void registerTools(ToolManager toolManager, Object... toolInstances) {
        for (var instance : toolInstances) {
            for (var method : instance.getClass().getMethods()) {
                var toolAnn = method.getAnnotation(Tool.class);
                if (toolAnn == null) continue;

                var name = method.getName();
                var desc = toolAnn.value().length > 0 ? toolAnn.value()[0] : "";
                var paramTypes = buildParamTypes(method);

                var def = toolManager.newTool(name).setDescription(desc);
                for (var entry : paramTypes.entrySet()) {
                    def.addArgument(entry.getKey(), entry.getValue().description(),
                        true, entry.getValue().type());
                }

                def.setHandler(args -> {
                    try {
                        var namedArgs = args.args();
                        var params = new Object[method.getParameterCount()];
                        for (int i = 0; i < method.getParameters().length; i++) {
                            var param = method.getParameters()[i];
                            params[i] = convertValue(namedArgs.get(param.getName()), param.getType());
                        }
                        var result = method.invoke(instance, params);
                        log.debug("Tool {} completed", name);
                        return ToolResponse.success(result != null ? String.valueOf(result) : "Success");
                    } catch (InvocationTargetException e) {
                        throw new ToolCallException(
                            e.getCause() != null ? e.getCause().getMessage() : "Tool execution failed");
                    } catch (Exception e) {
                        throw new ToolCallException(e.getMessage() != null ? e.getMessage() : "Tool execution failed");
                    }
                }, true).register();

                log.info("Registered MCP tool: {}", name);
            }
        }
    }

    private static Map<String, ParamInfo> buildParamTypes(Method method) {
        var params = new LinkedHashMap<String, ParamInfo>();
        for (var p : method.getParameters()) {
            var pAnn = p.getAnnotation(P.class);
            var desc = pAnn != null ? pAnn.value() : "";
            params.put(p.getName(), new ParamInfo(desc, mapType(p.getType())));
        }
        return params;
    }

    private static Class<?> mapType(Class<?> type) {
        if (type == int.class || type == Integer.class) return Integer.class;
        if (type == boolean.class || type == Boolean.class) return Boolean.class;
        if (type == long.class || type == Long.class) return Long.class;
        if (type == double.class || type == Double.class) return Double.class;
        return String.class;
    }

    private static Object convertValue(Object value, Class<?> target) {
        if (value == null) return null;
        if (target.isInstance(value)) return value;
        if (target == String.class) return String.valueOf(value);
        if (target == int.class || target == Integer.class)
            return value instanceof Number n ? n.intValue() : Integer.parseInt(value.toString());
        if (target == long.class || target == Long.class)
            return value instanceof Number n ? n.longValue() : Long.parseLong(value.toString());
        if (target == double.class || target == Double.class)
            return value instanceof Number n ? n.doubleValue() : Double.parseDouble(value.toString());
        if (target == boolean.class || target == Boolean.class)
            return value instanceof Boolean b ? b : Boolean.parseBoolean(value.toString());
        return value;
    }

    private record ParamInfo(String description, Class<?> type) {}
}
