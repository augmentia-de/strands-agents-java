package de.augmentia.strandsagents.core.internal;

import static org.assertj.core.api.Assertions.assertThat;

import de.augmentia.strandsagents.model.message.*;
import de.augmentia.strandsagents.model.tool.ToolCall;
import de.augmentia.strandsagents.tools.builtin.BaseToolNames;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ChatMessageConverterTest {

    @Test
    void convertsUserMessageBidirectionally() {
        var domain = new UserMessage("id-1", Instant.now(), "Hallo Welt", Map.of("key", "val"));
        var lc4j = ChatMessageConverter.toLangChain4j(domain);
        assertThat(lc4j).isInstanceOf(dev.langchain4j.data.message.UserMessage.class);
        assertThat(((dev.langchain4j.data.message.UserMessage) lc4j).singleText()).isEqualTo("Hallo Welt");

        var back = ChatMessageConverter.toDomainMessage(lc4j);
        assertThat(back).isInstanceOf(UserMessage.class);
        assertThat(back.content()).isEqualTo("Hallo Welt");
    }

    @Test
    void convertsSystemMessageBidirectionally() {
        var domain = new SystemMessage("id-1", Instant.now(), "Du bist ein Assistent", Map.of());
        var lc4j = ChatMessageConverter.toLangChain4j(domain);
        assertThat(lc4j).isInstanceOf(dev.langchain4j.data.message.SystemMessage.class);

        var back = ChatMessageConverter.toDomainMessage(lc4j);
        assertThat(back).isInstanceOf(SystemMessage.class);
        assertThat(back.content()).isEqualTo("Du bist ein Assistent");
    }

    @Test
    void convertsAssistantMessageWithoutToolCalls() {
        var domain = new AssistantMessage("id-1", Instant.now(), "Antwort", Map.of(), List.of());
        var lc4j = ChatMessageConverter.toLangChain4j(domain);
        assertThat(lc4j).isInstanceOf(dev.langchain4j.data.message.AiMessage.class);
        assertThat(((dev.langchain4j.data.message.AiMessage) lc4j).text()).isEqualTo("Antwort");
        assertThat(((dev.langchain4j.data.message.AiMessage) lc4j).hasToolExecutionRequests()).isFalse();

        var back = ChatMessageConverter.toDomainMessage(lc4j);
        assertThat(back).isInstanceOf(AssistantMessage.class);
        assertThat(back.content()).isEqualTo("Antwort");
    }

    @Test
    void convertsAssistantMessageWithToolCalls() {
        var toolCall = new ToolCall("tc-1", BaseToolNames.BASH, "{\"cmd\": \"ls\"}");
        var domain = new AssistantMessage("id-1", Instant.now(), null, Map.of(), List.of(toolCall));
        var lc4j = ChatMessageConverter.toLangChain4j(domain);
        assertThat(lc4j).isInstanceOf(dev.langchain4j.data.message.AiMessage.class);
        var aiMsg = (dev.langchain4j.data.message.AiMessage) lc4j;
        assertThat(aiMsg.hasToolExecutionRequests()).isTrue();
        assertThat(aiMsg.toolExecutionRequests()).hasSize(1);
        assertThat(aiMsg.toolExecutionRequests().get(0).name()).isEqualTo(BaseToolNames.BASH);
        assertThat(aiMsg.toolExecutionRequests().get(0).arguments()).isEqualTo("{\"cmd\": \"ls\"}");

        var back = ChatMessageConverter.toDomainMessage(lc4j);
        assertThat(back).isInstanceOf(AssistantMessage.class);
        var am = (AssistantMessage) back;
        assertThat(am.toolCalls()).hasSize(1);
        assertThat(am.toolCalls().get(0).toolName()).isEqualTo(BaseToolNames.BASH);
    }

    @Test
    void convertsToolMessageBidirectionally() {
        var domain = new ToolMessage("id-1", Instant.now(), "result", Map.of(), "tc-1", BaseToolNames.BASH);
        var lc4j = ChatMessageConverter.toLangChain4j(domain);
        assertThat(lc4j).isInstanceOf(dev.langchain4j.data.message.ToolExecutionResultMessage.class);

        var back = ChatMessageConverter.toDomainMessage(lc4j);
        assertThat(back).isInstanceOf(ToolMessage.class);
        var tm = (ToolMessage) back;
        assertThat(tm.content()).isEqualTo("result");
        assertThat(tm.toolName()).isEqualTo(BaseToolNames.BASH);
        assertThat(tm.toolCallId()).isEqualTo("tc-1");
    }

    @Test
    void convertsListOfMessages() {
        var domainMessages = List.<Message>of(
            new SystemMessage("s1", Instant.now(), "Sys", Map.of()),
            new UserMessage("u1", Instant.now(), "User", Map.of()),
            new AssistantMessage("a1", Instant.now(), "Assi", Map.of(), List.of())
        );
        var lc4j = ChatMessageConverter.toLangChain4jMessages(domainMessages);
        assertThat(lc4j).hasSize(3);

        var back = ChatMessageConverter.toDomainMessages(lc4j);
        assertThat(back).hasSize(3);
        assertThat(back.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(back.get(1)).isInstanceOf(UserMessage.class);
        assertThat(back.get(2)).isInstanceOf(AssistantMessage.class);
    }

    @Test
    void roundTripPreservesToolCallIds() {
        var tc = new ToolCall("call-xyz", "read", "{\"path\": \"/tmp\"}");
        var domain = new AssistantMessage("a1", Instant.now(), null, Map.of(), List.of(tc));
        var lc4j = ChatMessageConverter.toLangChain4j(domain);
        var back = (AssistantMessage) ChatMessageConverter.toDomainMessage(lc4j);
        assertThat(back.toolCalls().get(0).id()).isEqualTo("call-xyz");
        assertThat(back.toolCalls().get(0).toolName()).isEqualTo("read");
        assertThat(back.toolCalls().get(0).arguments()).isEqualTo("{\"path\": \"/tmp\"}");
    }

    @Test
    void lc4jToDomainGeneratesIdAndTimestamp() {
        var lc4j = new dev.langchain4j.data.message.UserMessage("test");
        var domain = ChatMessageConverter.toDomainMessage(lc4j);
        assertThat(domain.id()).isNotEmpty();
        assertThat(domain.timestamp()).isNotNull();
    }
}
