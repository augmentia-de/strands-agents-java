package de.augmentia.strandsagents.facade;

import static org.assertj.core.api.Assertions.assertThat;

import de.augmentia.strandsagents.core.Agent;
import de.augmentia.strandsagents.core.MockChatModel;
import de.augmentia.strandsagents.core.MockStreamingChatModel;
import de.augmentia.strandsagents.core.StreamingAgent;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DefaultStrandsAgentTest {

    @Test
    void ask_shouldReturnFinalAnswer() {
        var agent = new Agent(new MockChatModel("Answer: %s"));
        var strands = new DefaultStrandsAgent(agent, "test");

        var result = strands.ask("hello");

        assertThat(result).isEqualTo("Answer: hello");
    }

    @Test
    void ask_withSessionId_shouldInvokeCorrectMethod() {
        var agent = new Agent(new MockChatModel("Echo: %s"));
        var strands = new DefaultStrandsAgent(agent, "test");

        var result = strands.ask("ping", "session-123");

        assertThat(result).isEqualTo("Echo: ping");
        assertThat(agent.getSessionId()).isEqualTo("session-123");
    }

    @Test
    void askStream_withSyncAgent_shouldDeliverFullResult() {
        var agent = new Agent(new MockChatModel("Sync: %s"));
        var strands = new DefaultStrandsAgent(agent, "test");

        var captured = new AtomicReference<String>();
        strands.askStream("hello", captured::set);

        assertThat(captured.get()).isEqualTo("Sync: hello");
    }

    @Test
    void askStream_withStreamingAgent_shouldDeliverStreamedResult() {
        var streamingModel = new MockStreamingChatModel("Streamed: %s");
        var streamingAgent = new StreamingAgent(streamingModel);
        var strands = new DefaultStrandsAgent(streamingAgent, "stream-test");

        var sb = new StringBuilder();
        strands.askStream("world", token -> sb.append(token));

        assertThat(sb.toString()).isEqualTo("Streamed: world");
    }

    @Test
    void askStream_withSessionAndStreamingAgent() {
        var streamingModel = new MockStreamingChatModel("Session: %s");
        var streamingAgent = new StreamingAgent(streamingModel);
        var strands = new DefaultStrandsAgent(streamingAgent, "stream-test");

        var sb = new StringBuilder();
        strands.askStream("data", "session-456", token -> sb.append(token));

        assertThat(sb.toString()).isEqualTo("Session: data");
    }

    @Test
    void getName_shouldReturnConfiguredName() {
        var agent = new Agent(new MockChatModel());
        var strands = new DefaultStrandsAgent(agent, "my-custom-agent");

        assertThat(strands.getName()).isEqualTo("my-custom-agent");
    }

    @Test
    void getDelegate_shouldReturnWrappedAgent() {
        var inner = new Agent(new MockChatModel());
        var strands = new DefaultStrandsAgent(inner, "test");

        assertThat(strands.getDelegate()).isSameAs(inner);
    }
}
