package de.augmentia.strandsagents.model.message;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import java.util.Map;

public class MessageTypeIdResolver extends TypeIdResolverBase {

    private static final String PREFIX = "de.augmentia.strandsagents.model.message.";

    private static final Map<String, Class<? extends Message>> SHORT_NAMES = Map.of(
        "user", UserMessage.class,
        "assistant", AssistantMessage.class,
        "system", SystemMessage.class,
        "tool", ToolMessage.class
    );

    @Override
    public String idFromValue(Object value) {
        if (value instanceof UserMessage) return "user";
        if (value instanceof AssistantMessage) return "assistant";
        if (value instanceof SystemMessage) return "system";
        if (value instanceof ToolMessage) return "tool";
        throw new IllegalArgumentException("Unknown Message type: " + value.getClass());
    }

    @Override
    public String idFromValueAndType(Object value, Class<?> clazz) {
        return idFromValue(value);
    }

    @Override
    public JavaType typeFromId(DatabindContext context, String id) {
        var lookup = id;
        if (id.startsWith(PREFIX)) {
            lookup = switch (id.substring(PREFIX.length())) {
                case "UserMessage" -> "user";
                case "AssistantMessage" -> "assistant";
                case "SystemMessage" -> "system";
                case "ToolMessage" -> "tool";
                default -> id.substring(PREFIX.length());
            };
        }
        var clazz = SHORT_NAMES.get(lookup);
        if (clazz == null) {
            throw new IllegalArgumentException("Unknown Message type: " + id);
        }
        return context.getTypeFactory().constructType(clazz);
    }

    @Override
    public JsonTypeInfo.Id getMechanism() {
        return JsonTypeInfo.Id.CUSTOM;
    }
}
