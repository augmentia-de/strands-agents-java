package de.augmentia.strandsagents.model.message;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MessageTest {

    @Test
    void userFactoryFull() {
        var now = Instant.now();
        var msg = Message.user("id1", now, "hello", Map.of("k", "v"));
        assertThat(msg.id()).isEqualTo("id1");
        assertThat(msg.timestamp()).isEqualTo(now);
        assertThat(msg.content()).isEqualTo("hello");
        assertThat(msg.metadata()).containsEntry("k", "v");
        assertThat(msg.isUser()).isTrue();
    }

    @Test
    void userFactorySimple() {
        var msg = Message.user("hello");
        assertThat(msg.id()).isNotEmpty();
        assertThat(msg.content()).isEqualTo("hello");
        assertThat(msg.isUser()).isTrue();
    }

    @Test
    void assistantFactoryFullWithTools() {
        var now = Instant.now();
        var tc = ToolExecutionRequest.builder()
            .id("tcid").name("calc").arguments("{\"a\":1}").build();
        var msg = Message.assistant("id2", now, "response", Map.of(), List.of(tc));
        assertThat(msg.id()).isEqualTo("id2");
        assertThat(msg.content()).isEqualTo("response");
        assertThat(msg.toolExecutionRequests()).hasSize(1);
        assertThat(msg.toolExecutionRequests().get(0).name()).isEqualTo("calc");
        assertThat(msg.isAssistant()).isTrue();
    }

    @Test
    void assistantFactoryNoTools() {
        var msg = Message.assistant("id3", Instant.now(), "ok", Map.of(), List.of());
        assertThat(msg.toolExecutionRequests()).isEmpty();
        assertThat(msg.isAssistant()).isTrue();
    }

    @Test
    void assistantFactorySimple() {
        var msg = Message.assistant("ok");
        assertThat(msg.content()).isEqualTo("ok");
        assertThat(msg.isAssistant()).isTrue();
    }

    @Test
    void assistantFactoryWithToolsList() {
        var tc = ToolExecutionRequest.builder()
            .id("tcid").name("calc").arguments("{}").build();
        var msg = Message.assistant("response", List.of(tc));
        assertThat(msg.toolExecutionRequests()).hasSize(1);
        assertThat(msg.isAssistant()).isTrue();
    }

    @Test
    void systemFactoryFull() {
        var now = Instant.now();
        var msg = Message.system("id4", now, "system prompt", Map.of());
        assertThat(msg.id()).isEqualTo("id4");
        assertThat(msg.content()).isEqualTo("system prompt");
        assertThat(msg.isSystem()).isTrue();
    }

    @Test
    void systemFactorySimple() {
        var msg = Message.system("system prompt");
        assertThat(msg.content()).isEqualTo("system prompt");
        assertThat(msg.isSystem()).isTrue();
    }

    @Test
    void toolResultFactoryFull() {
        var now = Instant.now();
        var msg = Message.toolResult("id5", now, "42", Map.of(), "call1", "calculator");
        assertThat(msg.id()).isEqualTo("id5");
        assertThat(msg.content()).isEqualTo("42");
        assertThat(msg.toolCallId()).isEqualTo("call1");
        assertThat(msg.toolName()).isEqualTo("calculator");
        assertThat(msg.isToolResult()).isTrue();
    }

    @Test
    void toolResultFactorySimple() {
        var msg = Message.toolResult("42", "call1", "calculator");
        assertThat(msg.content()).isEqualTo("42");
        assertThat(msg.toolCallId()).isEqualTo("call1");
        assertThat(msg.toolName()).isEqualTo("calculator");
        assertThat(msg.isToolResult()).isTrue();
    }

    @Test
    void toChatMessageRoundtrip() {
        var original = Message.user("id1", Instant.now(), "hello", Map.of());
        var cm = original.toChatMessage();
        var restored = Message.from(cm);
        assertThat(restored.content()).isEqualTo("hello");
        assertThat(restored.isUser()).isTrue();
    }

    @Test
    void fromChatMessageUser() {
        var cm = new dev.langchain4j.data.message.UserMessage("test");
        var msg = Message.from(cm);
        assertThat(msg.id()).isNotEmpty();
        assertThat(msg.timestamp()).isNotNull();
        assertThat(msg.isUser()).isTrue();
    }

    @Test
    void fromChatMessageSystem() {
        var cm = new dev.langchain4j.data.message.SystemMessage("system");
        var msg = Message.from(cm);
        assertThat(msg.isSystem()).isTrue();
        assertThat(msg.content()).isEqualTo("system");
    }

    @Test
    void fromChatMessageAssistant() {
        var cm = new dev.langchain4j.data.message.AiMessage("assistant");
        var msg = Message.from(cm);
        assertThat(msg.isAssistant()).isTrue();
        assertThat(msg.content()).isEqualTo("assistant");
    }

    @Test
    void fromChatMessageToolResult() {
        var request = ToolExecutionRequest.builder()
            .id("c1").name("tool").build();
        var cm = dev.langchain4j.data.message.ToolExecutionResultMessage.from(request, "result");
        var msg = Message.from(cm);
        assertThat(msg.isToolResult()).isTrue();
        assertThat(msg.content()).isEqualTo("result");
    }

    @Test
    void hasToolExecutionRequestsFalseForUser() {
        var msg = Message.user("hello");
        assertThat(msg.hasToolExecutionRequests()).isFalse();
    }

    @Test
    void hasToolExecutionRequestsTrueForAssistantWithTools() {
        var tc = ToolExecutionRequest.builder()
            .id("tcid").name("calc").arguments("{}").build();
        var msg = Message.assistant("id", Instant.now(), "resp", Map.of(), List.of(tc));
        assertThat(msg.hasToolExecutionRequests()).isTrue();
    }

    @Test
    void toolNameAndCallIdNullForNonTool() {
        var msg = Message.user("hello");
        assertThat(msg.toolName()).isNull();
        assertThat(msg.toolCallId()).isNull();
    }

    @Test
    void metadataDefaultsToEmptyMap() {
        var msg = Message.user("hello");
        assertThat(msg.metadata()).isEmpty();
    }
}
