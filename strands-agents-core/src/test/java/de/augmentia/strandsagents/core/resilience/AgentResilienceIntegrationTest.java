package de.augmentia.strandsagents.core.resilience;

import static org.assertj.core.api.Assertions.assertThat;


import de.augmentia.strandsagents.core.ToolExecutor;
import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.agent.MockChatModel;
import de.augmentia.strandsagents.core.agent.StrandsAgent;
import de.augmentia.strandsagents.core.model.agent.StopReason;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AgentResilienceIntegrationTest {

    @Test
    void shouldRecoverFromTokenLimitError() {
        var counter = new AtomicInteger();
        var failingModel = new MockChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                if (counter.incrementAndGet() <= 2) {
                    throw new RuntimeException("maximum context length exceeded");
                }
                return super.chat(request);
            }
        };

        var agent = new StrandsAgent(failingModel);
        var result = agent.execute("Hallo");

        assertThat(result.stopReason()).isEqualTo(StopReason.COMPLETED);
        assertThat(result.finalAnswer()).isNotEmpty();
    }

    @Test
    void shouldGiveUpAfterMaxTokenRecoveryAttempts() {
        var failingModel = new MockChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                throw new RuntimeException("maximum context length exceeded");
            }
        };

        var agent = new StrandsAgent(failingModel);
        var result = agent.execute("Hallo");

        assertThat(result.stopReason()).isEqualTo(StopReason.ERROR);
    }

    @Test
    void shouldRetryOnTransientErrors() {
        var counter = new AtomicInteger();
        var failingModel = new MockChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                if (counter.incrementAndGet() < 3) {
                    throw new RuntimeException("timeout");
                }
                return super.chat(request);
            }
        };

        var agent = new StrandsAgent(failingModel);
        var result = agent.execute("Hallo");

        assertThat(result.stopReason()).isEqualTo(StopReason.COMPLETED);
        assertThat(result.finalAnswer()).isNotEmpty();
    }

    @Test
    void shouldFailAfterMaxRetries() {
        var failingModel = new MockChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                throw new RuntimeException("timeout");
            }
        };

        var agent = new StrandsAgent(failingModel);
        var result = agent.execute("Hallo");

        assertThat(result.stopReason()).isEqualTo(StopReason.ERROR);
    }

    @Test
    void shouldUseCircuitBreakerFallback() {
        var failingModel = new MockChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                throw new RuntimeException("service unavailable");
            }
        };

        var cfg = new ResilienceConfig(
            new RetryConfig(1, 1, 1.0),
            new CircuitBreakerConfig(0.3f, 100, 99999)
        );
        var agent = new StrandsAgent(failingModel, new ToolRegistry(), new ToolExecutor(), null, null, cfg);

        // First calls open the circuit breaker
        for (int i = 0; i < 5; i++) {
            var result = agent.execute("Hallo");
            assertThat(result.stopReason()).isEqualTo(StopReason.ERROR);
        }
    }

    @Test
    void shouldNotRetryAuthenticationErrors() {
        var counter = new AtomicInteger();
        var failingModel = new MockChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                counter.incrementAndGet();
                throw new RuntimeException("authentication failed - 401");
            }
        };

        var agent = new StrandsAgent(failingModel);
        agent.execute("Hallo");

        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    void shouldWorkWithoutResilienceConfig() {
        var agent = new StrandsAgent(new MockChatModel(),
            new ToolRegistry(), new ToolExecutor(), null, null, ResilienceConfig.NONE);
        var result = agent.execute("Hallo");
        assertThat(result.stopReason()).isEqualTo(StopReason.COMPLETED);
    }
}
