package de.augmentia.strandsagents.model.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.augmentia.strandsagents.model.tool.ToolCall;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UserMessageTest {

    @Test
    void constructor_setsAllFields() {
        var now = Instant.now();
        var msg = new UserMessage("id1", now, "hello", Map.of("k", "v"));
        assertThat(msg.id()).isEqualTo("id1");
        assertThat(msg.timestamp()).isEqualTo(now);
        assertThat(msg.content()).isEqualTo("hello");
        assertThat(msg.metadata()).containsEntry("k", "v");
    }
}

class AssistantMessageTest {

    @Test
    void constructor_setsAllFields() {
        var now = Instant.now();
        var tc = new ToolCall("tcid", "calc", "{\"a\":1}");
        var msg = new AssistantMessage("id2", now, "response", Map.of(), List.of(tc));
        assertThat(msg.id()).isEqualTo("id2");
        assertThat(msg.content()).isEqualTo("response");
        assertThat(msg.toolCalls()).hasSize(1);
        assertThat(msg.toolCalls().get(0).toolName()).isEqualTo("calc");
    }

    @Test
    void noToolCalls() {
        var msg = new AssistantMessage("id3", Instant.now(), "ok", Map.of(), List.of());
        assertThat(msg.toolCalls()).isEmpty();
    }
}

class SystemMessageTest {

    @Test
    void constructor_setsAllFields() {
        var now = Instant.now();
        var msg = new SystemMessage("id4", now, "system prompt", Map.of());
        assertThat(msg.id()).isEqualTo("id4");
        assertThat(msg.content()).isEqualTo("system prompt");
    }
}

class ToolMessageTest {

    @Test
    void constructor_setsAllFields() {
        var now = Instant.now();
        var msg = new ToolMessage("id5", now, "42", Map.of(), "call1", "calculator");
        assertThat(msg.id()).isEqualTo("id5");
        assertThat(msg.content()).isEqualTo("42");
        assertThat(msg.toolCallId()).isEqualTo("call1");
        assertThat(msg.toolName()).isEqualTo("calculator");
    }
}

class MessageTypeIdResolverTest {

    private final MessageTypeIdResolver resolver = new MessageTypeIdResolver();

    @Test
    void idFromValue_userMessage() {
        var msg = new UserMessage("id", Instant.now(), "hello", Map.of());
        assertThat(resolver.idFromValue(msg)).isEqualTo("user");
    }

    @Test
    void idFromValue_assistantMessage() {
        var msg = new AssistantMessage("id", Instant.now(), "hi", Map.of(), List.of());
        assertThat(resolver.idFromValue(msg)).isEqualTo("assistant");
    }

    @Test
    void idFromValue_systemMessage() {
        var msg = new SystemMessage("id", Instant.now(), "sys", Map.of());
        assertThat(resolver.idFromValue(msg)).isEqualTo("system");
    }

    @Test
    void idFromValue_toolMessage() {
        var msg = new ToolMessage("id", Instant.now(), "res", Map.of(), "c1", "tool");
        assertThat(resolver.idFromValue(msg)).isEqualTo("tool");
    }

    @Test
    void idFromValue_unknownType_throws() {
        assertThatThrownBy(() -> resolver.idFromValue("not a message"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
