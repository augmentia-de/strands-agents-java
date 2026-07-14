package de.augmentia.strandsagents.model.structured;

import de.augmentia.strandsagents.prompt.PromptRegistry;
import java.lang.reflect.ParameterizedType;
import java.util.List;

/** Configuration for structured JSON output from model responses. */
public record StructuredOutputConfig(
    boolean dynamicSchema,
    Class<?> outputClass,
    String jsonSchema,
    String forcePrompt
) {
    private static String defaultForcePrompt() {
        return PromptRegistry.getOrDefault("structured_output.force_prompt",
            "You must format the previous response as structured output.");
    }

    /** Creates a config for a statically-typed output class. */
    public static StructuredOutputConfig staticModel(Class<?> outputClass) {
        return new StructuredOutputConfig(false, outputClass, null, defaultForcePrompt());
    }

    /** Creates a config for a statically-typed output class with a custom force prompt. */
    public static StructuredOutputConfig staticModel(Class<?> outputClass, String forcePrompt) {
        return new StructuredOutputConfig(false, outputClass, null, forcePrompt);
    }

    /** Creates a config with a dynamic JSON schema. */
    public static StructuredOutputConfig dynamicSchema(String jsonSchema) {
        return new StructuredOutputConfig(true, null, jsonSchema, defaultForcePrompt());
    }

    /** Creates a config with a dynamic JSON schema and a custom force prompt. */
    public static StructuredOutputConfig dynamicSchema(String jsonSchema, String forcePrompt) {
        return new StructuredOutputConfig(true, null, jsonSchema, forcePrompt);
    }

    /** Returns true if this configuration has a valid output class or non-blank JSON schema. */
    public boolean isEnabled() {
        return (!dynamicSchema && outputClass != null)
            || (dynamicSchema && jsonSchema != null && !jsonSchema.isBlank());
    }

    /** Returns the JSON schema, generated from the output class or from the raw schema. */
    public String effectiveSchema() {
        if (!dynamicSchema) {
            if (outputClass == null) return null;
            return generateSchemaFromClass(outputClass);
        }
        return jsonSchema;
    }

    private static String generateSchemaFromClass(Class<?> cls) {
        var sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"$schema\": \"https://json-schema.org/draft/2020-12/schema\",\n");
        sb.append("  \"title\": \"").append(cls.getSimpleName()).append("\",\n");
        sb.append("  \"type\": \"object\",\n");
        sb.append("  \"properties\": {");

        var fields = cls.getRecordComponents();
        if (fields != null) {
            for (int i = 0; i < fields.length; i++) {
                if (i > 0) sb.append(",");
                sb.append("\n    \"").append(fields[i].getName()).append("\": ");
                appendFieldSchema(sb, fields[i], "    ");
            }
            sb.append("\n");
        }

        sb.append("  },\n");
        sb.append("  \"required\": [");
        if (fields != null) {
            for (int i = 0; i < fields.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append("\"").append(fields[i].getName()).append("\"");
            }
        }
        sb.append("]\n");
        sb.append("}");
        return sb.toString();
    }

    private static void appendFieldSchema(StringBuilder sb, java.lang.reflect.RecordComponent field, String indent) {
        var type = field.getType();
        if (type.isRecord()) {
            sb.append("{\n");
            sb.append(indent).append("  \"type\": \"object\",\n");
            sb.append(indent).append("  \"properties\": {");
            var components = type.getRecordComponents();
            if (components != null) {
                for (int i = 0; i < components.length; i++) {
                    if (i > 0) sb.append(",");
                    sb.append("\n").append(indent).append("    \"")
                        .append(components[i].getName()).append("\": ");
                    appendFieldSchema(sb, components[i], indent + "    ");
                }
                sb.append("\n").append(indent);
            }
            sb.append("  },\n");
            sb.append(indent).append("  \"required\": [");
            if (components != null) {
                for (int i = 0; i < components.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append("\"").append(components[i].getName()).append("\"");
                }
            }
            sb.append("]\n");
            sb.append(indent).append("}");
        } else if (List.class.isAssignableFrom(type)) {
            sb.append("{\"type\": \"array\"");
            var genericType = field.getAccessor().getGenericReturnType();
            if (genericType instanceof ParameterizedType pt) {
                var args = pt.getActualTypeArguments();
                if (args.length > 0 && args[0] instanceof Class<?> elementClass) {
                    sb.append(", \"items\": ")
                        .append(simpleTypeSchema(elementClass));
                }
            }
            sb.append("}");
        } else if (type.isArray()) {
            sb.append("{\"type\": \"array\", \"items\": ")
                .append(simpleTypeSchema(type.getComponentType()))
                .append("}");
        } else {
            sb.append(simpleTypeSchema(type));
        }
    }

    private static String simpleTypeSchema(Class<?> type) {
        var format = dateTimeFormat(type);
        var base = "{\"type\": \"" + mapJavaTypeToJsonType(type) + "\"";
        if (format != null) {
            return base + ", \"format\": \"" + format + "\"}";
        }
        return base + "}";
    }

    private static String dateTimeFormat(Class<?> type) {
        if (type == java.time.LocalDate.class) return "date";
        if (type == java.time.LocalDateTime.class) return "date-time";
        if (type == java.time.OffsetDateTime.class) return "date-time";
        if (type == java.time.Instant.class) return "date-time";
        return null;
    }

    private static String mapJavaTypeToJsonType(Class<?> type) {
        if (type == String.class) return "string";
        if (type == Integer.class || type == int.class) return "integer";
        if (type == Long.class || type == long.class) return "integer";
        if (type == Double.class || type == double.class || type == Float.class || type == float.class) return "number";
        if (type == Boolean.class || type == boolean.class) return "boolean";
        if (type == java.time.LocalDate.class) return "string";
        if (type == java.time.LocalDateTime.class) return "string";
        if (type == java.time.OffsetDateTime.class) return "string";
        if (type == java.time.Instant.class) return "string";
        return "string";
    }
}
