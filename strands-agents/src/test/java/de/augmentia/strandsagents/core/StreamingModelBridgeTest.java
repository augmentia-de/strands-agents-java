package de.augmentia.strandsagents.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.augmentia.strandsagents.core.StreamingModelBridge;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class StreamingModelBridgeTest {

    @Test
    void bridgesStreamingToSync() {
        var streaming = new MockStreamingChatModel("streamed response");
        var bridge = new StreamingModelBridge(streaming);
        var response = bridge.chat(ChatRequest.builder().messages(UserMessage.from("hello")).build());
        assertThat(response.aiMessage().text()).isEqualTo("streamed response");
    }

    @Test
    void capturesTokensViaConsumer() {
        var streaming = new MockStreamingChatModel("abc");
        var tokens = new StringBuilder();
        var bridge = new StreamingModelBridge(streaming, tokens::append);
        bridge.chat(ChatRequest.builder().messages(UserMessage.from("x")).build());
        assertThat(tokens.toString()).isEqualTo("abc");
    }

    @Test
    void rejectsNullStreamingModel() {
        assertThatThrownBy(() -> new StreamingModelBridge(null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
