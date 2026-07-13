package de.augmentia.strandsagents.core.internal;

import static org.assertj.core.api.Assertions.assertThat;

import de.augmentia.strandsagents.model.message.Message;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChatMessageConverterTest {

    @Test
    void fromUserMessage() {
        var lc4j = new dev.langchain4j.data.message.UserMessage("Hallo Welt");
        var msg = Message.from(lc4j);
        assertThat(msg.id()).isNotEmpty();
        assertThat(msg.timestamp()).isNotNull();
        assertThat(msg.isUser()).isTrue();
        assertThat(msg.content()).isEqualTo("Hallo Welt");
    }

    @Test
    void fromSystemMessage() {
        var lc4j = new dev.langchain4j.data.message.SystemMessage("Du bist ein Assistent");
        var msg = Message.from(lc4j);
        assertThat(msg.isSystem()).isTrue();
        assertThat(msg.content()).isEqualTo("Du bist ein Assistent");
    }

    @Test
    void fromAssistantMessageWithoutToolCalls() {
        var lc4j = new AiMessage("Antwort");
        var msg = Message.from(lc4j);
        assertThat(msg.isAssistant()).isTrue();
        assertThat(msg.content()).isEqualTo("Antwort");
        assertThat(msg.hasToolExecutionRequests()).isFalse();
    }

    @Test
    void fromAssistantMessageWithToolCalls() {
        var request = ToolExecutionRequest.builder()
            .id("tc-1").name("bash").arguments("{\"cmd\": \"ls\"}").build();
        var lc4j = AiMessage.from(null, List.of(request));
        var msg = Message.from(lc4j);
        assertThat(msg.isAssistant()).isTrue();
        assertThat(msg.hasToolExecutionRequests()).isTrue();
        assertThat(msg.toolExecutionRequests()).hasSize(1);
        assertThat(msg.toolExecutionRequests().get(0).name()).isEqualTo("bash");
    }

    @Test
    void fromToolExecutionResultMessage() {
        var request = ToolExecutionRequest.builder()
            .id("tc-1").name("bash").build();
        var lc4j = dev.langchain4j.data.message.ToolExecutionResultMessage.from(request, "result");
        var msg = Message.from(lc4j);
        assertThat(msg.isToolResult()).isTrue();
        assertThat(msg.content()).isEqualTo("result");
        assertThat(msg.toolName()).isEqualTo("bash");
        assertThat(msg.toolCallId()).isEqualTo("tc-1");
    }

    @Test
    void roundTripPreservesContent() {
        var original = Message.user("Hallo Welt");
        var lc4j = original.toChatMessage();
        var restored = Message.from(lc4j);
        assertThat(restored.content()).isEqualTo(original.content());
        assertThat(restored.isUser()).isEqualTo(original.isUser());
    }

    @Test
    void roundTripPreservesToolCallIds() {
        var request = ToolExecutionRequest.builder()
            .id("call-xyz").name("read").arguments("{\"path\": \"/tmp\"}").build();
        var original = Message.assistant("a1", Instant.now(), null, Map.of(), List.of(request));
        var lc4j = original.toChatMessage();
        var restored = Message.from(lc4j);
        assertThat(restored.toolExecutionRequests().get(0).id()).isEqualTo("call-xyz");
        assertThat(restored.toolExecutionRequests().get(0).name()).isEqualTo("read");
        assertThat(restored.toolExecutionRequests().get(0).arguments()).isEqualTo("{\"path\": \"/tmp\"}");
    }

    @Test
    void roundTripListOfMessages() {
        var domainMessages = List.<Message>of(
            Message.system("s1", Instant.now(), "Sys", Map.of()),
            Message.user("u1", Instant.now(), "User", Map.of()),
            Message.assistant("a1", Instant.now(), "Assi", Map.of(), List.of())
        );
        var lc4jMessages = domainMessages.stream().map(Message::toChatMessage).toList();
        assertThat(lc4jMessages).hasSize(3);

        var back = lc4jMessages.stream().map(Message::from).toList();
        assertThat(back).hasSize(3);
        assertThat(back.get(0).isSystem()).isTrue();
        assertThat(back.get(1).isUser()).isTrue();
        assertThat(back.get(2).isAssistant()).isTrue();
    }

    @Test
    void fromGeneratesIdAndTimestamp() {
        var lc4j = new dev.langchain4j.data.message.UserMessage("test");
        var msg = Message.from(lc4j);
        assertThat(msg.id()).isNotEmpty();
        assertThat(msg.timestamp()).isNotNull();
    }
}
