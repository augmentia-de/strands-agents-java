package de.augmentia.strandsagents.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.augmentia.strandsagents.tools.*;
import de.augmentia.strandsagents.tools.AgentTool;
import de.augmentia.strandsagents.tools.TextContent;
import de.augmentia.strandsagents.tools.ToolCapability;
import de.augmentia.strandsagents.interceptor.security.CapabilityToken;
import de.augmentia.strandsagents.tools.ToolResult;
import de.augmentia.strandsagents.tools.builtin.*;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final Map<String, ToolMethod> tools = new LinkedHashMap<>();
    private List<ToolDescriptionValidator> validators = ToolDescriptionValidator.defaults();
    private boolean strictValidation = false;

    public ToolRegistry() {}

    public void setValidators(List<ToolDescriptionValidator> validators) {
        this.validators = validators != null ? validators : List.of();
    }

    public void setStrictValidation(boolean strict) {
        this.strictValidation = strict;
    }

    private void validate(ToolSpecification spec) {
        if (validators == null) return;
        for (var v : validators) {
            v.validate(spec).ifPresent(warning -> {
                if (strictValidation) {
                    throw new IllegalArgumentException(warning);
                }
                log.warn(warning);
            });
        }
    }

    public void register(Object toolInstance) {
        for (Method method : toolInstance.getClass().getMethods()) {
            if (method.isAnnotationPresent(Tool.class)) {
                var spec = ToolSpecifications.toolSpecificationFrom(method);
                validate(spec);
                var cap = method.isAnnotationPresent(ToolCapability.class)
                    ? method.getAnnotation(ToolCapability.class).value()
                    : null;
                tools.put(spec.name(), new JavaToolMethod(toolInstance, method, spec, cap));
            }
        }
    }

    public void register(String name, Object toolInstance, Method method) {
        var spec = ToolSpecifications.toolSpecificationFrom(method);
        validate(spec);
        var cap = method.isAnnotationPresent(ToolCapability.class)
            ? method.getAnnotation(ToolCapability.class).value()
            : null;
        tools.put(name, new JavaToolMethod(toolInstance, method, spec, cap));
    }

    public void register(String name, ToolSpecification spec, ToolMethod toolMethod) {
        validate(spec);
        tools.put(name, toolMethod);
    }

    public void register(AgentTool<?> agentTool) {
        var spec = toToolSpecification(agentTool);
        validate(spec);
        tools.put(agentTool.name(), new AgentToolMethod(agentTool, spec));
    }

    private static ToolSpecification toToolSpecification(AgentTool<?> tool) {
        var builder = ToolSpecification.builder()
            .name(tool.name())
            .description(tool.description());

        var schemaNode = tool.parameterSchema();
        if (schemaNode instanceof com.fasterxml.jackson.databind.node.ObjectNode obj) {
            if (obj.has("properties")) {
                var jsonSchemaBuilder = JsonObjectSchema.builder();
                var props = obj.get("properties");
                props.fieldNames().forEachRemaining(name -> {
                    var prop = props.get(name);
                    var type = prop.has("type") ? prop.get("type").asText() : "string";
                    var desc = prop.has("description") ? prop.get("description").asText() : null;
                    addPropertyToBuilder(jsonSchemaBuilder, name, type, desc);
                });
                if (obj.has("required")) {
                    var required = new ArrayList<String>();
                    obj.get("required").forEach(n -> required.add(n.asText()));
                    jsonSchemaBuilder.required(required);
                }
                builder.parameters(jsonSchemaBuilder.build());
            }
        }

        return builder.build();
    }

private static void addPropertyToBuilder(JsonObjectSchema.Builder b, String name, String type, String desc) {
        switch (type) {
            case "int", "integer" -> {
                if (desc != null) b.addIntegerProperty(name, desc);
                else b.addIntegerProperty(name);
            }
            case "bool", "boolean" -> {
                if (desc != null) b.addBooleanProperty(name, desc);
                else b.addBooleanProperty(name);
            }
            case "number", "double", "float" -> {
                if (desc != null) b.addNumberProperty(name, desc);
                else b.addNumberProperty(name);
            }
            default -> {
                if (desc != null) b.addStringProperty(name, desc);
                else b.addStringProperty(name);
            }
        }
    }

    public Map<String, ToolMethod> getTools() {
        return Collections.unmodifiableMap(tools);
    }

    public ToolMethod get(String name) {
        var tm = tools.get(name);
        if (tm == null) {
            throw new IllegalArgumentException("Unknown tool: " + name);
        }
        return tm;
    }

    private volatile AtomicBoolean sharedAbortFlag = new AtomicBoolean(false);

    void setAbortFlag(AtomicBoolean flag) {
        this.sharedAbortFlag = flag;
    }

    public List<ToolSpecification> getSpecifications() {
        return tools.values().stream()
            .map(ToolMethod::spec)
            .toList();
    }

    public Set<String> getToolNames() {
        return tools.keySet();
    }

    public static ToolMethod createMethod(AgentTool<?> tool) {
        return new AgentToolMethod(tool, toToolSpecification(tool));
    }

    public ToolRegistry withOnly(Set<String> names) {
        var filtered = new ToolRegistry();
        for (var name : names) {
            var tm = tools.get(name);
            if (tm != null) {
                filtered.register(name, tm.spec(), tm);
            }
        }
        return filtered;
    }

    public void remove(String name) {
        tools.remove(name);
    }

    public void removeAll(java.util.Collection<String> names) {
        names.forEach(tools::remove);
    }

    public int size() {
        return tools.size();
    }

    public static Builder builder() {
        return new Builder();
    }

    public interface ToolMethod {

        ToolSpecification spec();

        String execute(String jsonArguments) throws Exception;

        default CapabilityToken requiredCapability() {
            return null;
        }
    }

    record JavaToolMethod(Object instance, Method method, ToolSpecification spec,
                          CapabilityToken requiredCapability)
            implements ToolMethod {

        JavaToolMethod(Object instance, Method method, ToolSpecification spec) {
            this(instance, method, spec, null);
        }

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
                    throw new IllegalArgumentException("Missing argument: " + paramName);
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

    static final class AgentToolMethod implements ToolMethod {
        private final AgentTool<?> agentTool;
        private final ToolSpecification spec;
        private final AtomicBoolean abortFlag;

        AgentToolMethod(AgentTool<?> agentTool, ToolSpecification spec) {
            this(agentTool, spec, null);
        }

        AgentToolMethod(AgentTool<?> agentTool, ToolSpecification spec, AtomicBoolean abortFlag) {
            this.agentTool = agentTool;
            this.spec = spec;
            this.abortFlag = abortFlag;
        }

        @Override
        public ToolSpecification spec() {
            return spec;
        }

        @Override
        @SuppressWarnings({"rawtypes", "unchecked"})
        public String execute(String jsonArguments) throws Exception {
            var paramType = agentTool.parameterType();
            Object params;
            try {
                if (paramType == Map.class) {
                    params = MAPPER.readValue(jsonArguments,
                        new TypeReference<Map<String, Object>>() {});
                } else {
                    params = MAPPER.readValue(jsonArguments, (Class) paramType);
                }
            } catch (Exception e) {
                throw new IllegalArgumentException(
                    "Failed to parse arguments for tool '" + agentTool.name() + "': " + e.getMessage(), e);
            }

            var flag = abortFlag != null ? abortFlag : new AtomicBoolean(false);
            ToolResult result;
            try {
                result = ((AgentTool) agentTool).execute(null, params, flag, null);
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            var sb = new StringBuilder();
            for (var block : result.content()) {
                if (block instanceof TextContent t) {
                    sb.append(t.text());
                } else {
                    sb.append(block.toString());
                }
            }
            if (abortFlag != null && abortFlag.get()) {
                throw new InterruptedException("Tool execution aborted");
            }
            return sb.toString();
        }
    }

    public static class Builder {
        private final List<AgentTool<?>> agentTools = new ArrayList<>();
        private final List<Object> annotatedTools = new ArrayList<>();
        private final List<String> classNames = new ArrayList<>();
        private Set<String> includes = null;
        private Set<String> excludes = null;
        private Path workspace = Path.of("").toAbsolutePath();

        public Builder with(AgentTool<?> tool) {
            agentTools.add(tool);
            return this;
        }

        public Builder with(Object annotatedTool) {
            annotatedTools.add(annotatedTool);
            return this;
        }

        public Builder with(String className) {
            classNames.add(className);
            return this;
        }

        public Builder standard() {
            return standard(false);
        }

        public Builder standard(boolean includeBash) {
            var wd = workspace;
            if (includeBash) {
                agentTools.add(new CommandTool(wd));
                agentTools.add(new BashTool(wd));
            }
            agentTools.add(new DockerRunTool(wd));
            agentTools.add(new ReadTool(wd));
            agentTools.add(new WriteTool(wd));
            agentTools.add(new EditTool(wd));
            agentTools.add(new FindTool(wd));
            agentTools.add(new GrepTool(wd));
            agentTools.add(new LsTool(wd));
            agentTools.add(new WebFetchTool());
            agentTools.add(new WebSearchTool());
            return this;
        }

        public Builder include(String... names) {
            if (this.includes == null) this.includes = new HashSet<>();
            this.includes.addAll(Arrays.asList(names));
            return this;
        }

        public Builder exclude(String... names) {
            if (this.excludes == null) this.excludes = new HashSet<>();
            this.excludes.addAll(Arrays.asList(names));
            return this;
        }

        public Builder workspace(Path workspace) {
            this.workspace = workspace;
            return this;
        }

        public Builder cwd(Path cwd) {
            return workspace(cwd);
        }

        public ToolRegistry build() {
            var registry = new ToolRegistry();

            for (var tool : agentTools) {
                var name = tool.name();
                if (isFiltered(name)) continue;
                registry.register(tool);
            }

            for (var instance : annotatedTools) {
                for (Method method : instance.getClass().getMethods()) {
                    if (method.isAnnotationPresent(Tool.class)) {
                        var spec = ToolSpecifications.toolSpecificationFrom(method);
                        if (isFiltered(spec.name())) continue;
                        registry.register(spec.name(), instance, method);
                    }
                }
            }

            for (var className : classNames) {
                try {
                    var clazz = Class.forName(className);
                    var instance = clazz.getDeclaredConstructor().newInstance();
                    for (Method method : instance.getClass().getMethods()) {
                        if (method.isAnnotationPresent(Tool.class)) {
                            var spec = ToolSpecifications.toolSpecificationFrom(method);
                            if (isFiltered(spec.name())) continue;
                            registry.register(spec.name(), instance, method);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Tool not loadable: {} - {}", className, e.getMessage());
                }
            }

            return registry;
        }

        private boolean isFiltered(String name) {
            if (excludes != null && excludes.contains(name)) return true;
            if (includes != null && !includes.contains(name)) return true;
            return false;
        }
    }
}
