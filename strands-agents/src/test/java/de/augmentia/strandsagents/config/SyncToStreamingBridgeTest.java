package de.augmentia.strandsagents.config;

import static org.assertj.core.api.Assertions.assertThat;

import de.augmentia.strandsagents.core.MockChatModel;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class SyncToStreamingBridgeTest {

    @Test
    void streamsSyncResponseAsTokens() throws Exception {
        var sync = new MockChatModel("hello world");
        var bridge = new SyncToStreamingBridge(sync);
        var request = ChatRequest.builder()
            .messages(UserMessage.from("test"))
            .build();
        var collected = new StringBuilder();
        var future = new CompletableFuture<ChatResponse>();
        bridge.chat(request, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String token) { collected.append(token); }
            @Override
            public void onCompleteResponse(ChatResponse response) { future.complete(response); }
            @Override
            public void onError(Throwable error) { future.completeExceptionally(error); }
        });
        var response = future.get(5, TimeUnit.SECONDS);
        assertThat(collected.toString()).isEqualTo("hello world");
        assertThat(response.aiMessage().text()).contains("hello world");
    }

    @Test
    void handlesEmptyResponse() throws Exception {
        var sync = new MockChatModel("");
        var bridge = new SyncToStreamingBridge(sync);
        var future = new CompletableFuture<ChatResponse>();
        bridge.chat(ChatRequest.builder().messages(UserMessage.from("hi")).build(),
            new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String token) {}
                @Override
                public void onCompleteResponse(ChatResponse r) { future.complete(r); }
                @Override
                public void onError(Throwable e) { future.completeExceptionally(e); }
            });
        var response = future.get(5, TimeUnit.SECONDS);
        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
    }
}
