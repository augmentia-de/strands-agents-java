package de.augmentia.strandsagents.core;

import static org.assertj.core.api.Assertions.assertThat;

import de.augmentia.strandsagents.core.agent.MockChatModel;
import de.augmentia.strandsagents.core.agent.MockStreamingChatModel;
import de.augmentia.strandsagents.core.agent.StrandsAgent;
import de.augmentia.strandsagents.core.agent.StreamingAgent;
import de.augmentia.strandsagents.core.model.agent.StopReason;
import de.augmentia.strandsagents.core.model.event.*;
import de.augmentia.strandsagents.core.model.event.*;
import de.augmentia.strandsagents.core.tools.CalculatorTool;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;

class StreamingTest {

    @Test
    void executeAsyncShouldCompleteWithResult() {
        var agent = new StrandsAgent(new MockChatModel());
        var future = agent.executeAsync("Hallo");

        var result = future.join();
        assertThat(result.finalAnswer()).isNotEmpty();
        assertThat(result.stopReason()).isEqualTo(StopReason.COMPLETED);
    }

    @Test
    void executeAsyncShouldWorkWithContextVariables() {
        var agent = new StrandsAgent(new MockChatModel());
        var future = agent.executeAsync("Hallo", java.util.Map.of("key", "value"));

        var result = future.join();
        assertThat(result.finalAnswer()).isNotEmpty();
    }

    @Test
    void eventStreamShouldReceiveLifecycleEvents() {
        var agent = new StrandsAgent(new MockChatModel());
        var events = new CopyOnWriteArrayList<AgentEvent>();
        var done = new java.util.concurrent.CompletableFuture<Void>();

        agent.eventStream().subscribe(new Flow.Subscriber<>() {
            private Flow.Subscription subscription;
            @Override
            public void onSubscribe(Flow.Subscription s) { subscription = s; s.request(Long.MAX_VALUE); }
            @Override
            public void onNext(AgentEvent item) {
                events.add(item);
                if (item instanceof AgentFinishedEvent) {
                    done.complete(null);
                }
            }
            @Override
            public void onError(Throwable throwable) { done.completeExceptionally(throwable); }
            @Override
            public void onComplete() { done.complete(null); }
        });

        agent.execute("Hallo");
        done.orTimeout(5, java.util.concurrent.TimeUnit.SECONDS).join();

        assertThat(events).isNotEmpty();
        assertThat(events.get(0)).isInstanceOf(AgentStartedEvent.class);
        assertThat(events.get(events.size() - 1)).isInstanceOf(AgentFinishedEvent.class);
    }

    @Test
    void eventStreamShouldContainAllEventTypes() {
        var agent = new StrandsAgent(new MockChatModel());
        var events = new CopyOnWriteArrayList<AgentEvent>();

        agent.eventStream().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription s) { s.request(Long.MAX_VALUE); }
            @Override
            public void onNext(AgentEvent item) { events.add(item); }
            @Override
            public void onError(Throwable throwable) {}
            @Override
            public void onComplete() {}
        });

        agent.execute("Test");

        assertThat(events).extracting("class")
            .contains(
                AgentStartedEvent.class,
                ModelRequestedEvent.class,
                AgentFinishedEvent.class
            );
    }

    @Test
    void executeEventsShouldDeliverEvents() {
        var agent = new StrandsAgent(new MockChatModel());
        var events = new CopyOnWriteArrayList<AgentEvent>();

        var future = agent.executeEvents("Hallo", new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription s) { s.request(Long.MAX_VALUE); }
            @Override
            public void onNext(AgentEvent item) { events.add(item); }
            @Override
            public void onError(Throwable throwable) {}
            @Override
            public void onComplete() {}
        });

        future.join();
        assertThat(events).isNotEmpty();
        assertThat(events.get(0)).isInstanceOf(AgentStartedEvent.class);
    }

    @Test
    void eventListenerShouldStillWorkWithEventStream() {
        var agent = new StrandsAgent(new MockChatModel());
        var listenerEvents = new ArrayList<AgentEvent>();

        agent.setEventListener(listenerEvents::add);
        agent.execute("Hallo");

        assertThat(listenerEvents).isNotEmpty();
    }

    @Test
    void streamingAgentShouldExecuteNormally() {
        var agent = new StreamingAgent(new MockStreamingChatModel());
        var result = agent.execute("Hallo");

        assertThat(result.finalAnswer()).isNotEmpty();
        assertThat(result.stopReason()).isEqualTo(StopReason.COMPLETED);
    }

    @Test
    void streamingAgentShouldEmitTokensViaConsumer() {
        var agent = new StreamingAgent(new MockStreamingChatModel("Antwort: %s"));
        var receivedTokens = new ArrayList<String>();

        var result = agent.executeStreaming("Test", receivedTokens::add);

        assertThat(result.finalAnswer()).isNotEmpty();
        assertThat(receivedTokens).isNotEmpty();
        var joined = String.join("", receivedTokens);
        assertThat(joined).isEqualTo(result.finalAnswer());
    }

    @Test
    void streamingAgentShouldFireTokenEvents() {
        var agent = new StreamingAgent(new MockStreamingChatModel());
        var events = new CopyOnWriteArrayList<AgentEvent>();

        agent.eventStream().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription s) { s.request(Long.MAX_VALUE); }
            @Override
            public void onNext(AgentEvent item) { events.add(item); }
            @Override
            public void onError(Throwable throwable) {}
            @Override
            public void onComplete() {}
        });

        agent.execute("Hallo");

        var tokenEvents = events.stream()
            .filter(e -> e instanceof TokenEvent)
            .toList();
        assertThat(tokenEvents).isNotEmpty();
    }

    @Test
    void streamingAgentExecuteStreamingShouldFireTokenEvents() {
        var agent = new StreamingAgent(new MockStreamingChatModel());
        var events = new CopyOnWriteArrayList<AgentEvent>();

        agent.eventStream().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription s) { s.request(Long.MAX_VALUE); }
            @Override
            public void onNext(AgentEvent item) { events.add(item); }
            @Override
            public void onError(Throwable throwable) {}
            @Override
            public void onComplete() {}
        });

        agent.executeStreaming("Hallo", token -> {});

        var tokenEvents = events.stream()
            .filter(e -> e instanceof TokenEvent)
            .toList();
        assertThat(tokenEvents).isNotEmpty();
    }

    @Test
    void streamingAgentExecuteAsyncShouldWork() {
        var agent = new StreamingAgent(new MockStreamingChatModel());
        var future = agent.executeAsync("Hallo");

        var result = future.join();
        assertThat(result.finalAnswer()).isNotEmpty();
    }

    @Test
    void streamingAgentExecuteStreamingAsyncShouldWork() {
        var agent = new StreamingAgent(new MockStreamingChatModel());
        var tokens = new ArrayList<String>();

        var future = agent.executeStreamingAsync("Hallo", tokens::add);
        var result = future.join();

        assertThat(result.finalAnswer()).isNotEmpty();
        assertThat(tokens).isNotEmpty();
    }

    @Test
    void tokenEventShouldContainSessionIdAndToken() {
        var agent = new StreamingAgent(new MockStreamingChatModel("XYZ-%s"));
        var tokenEvents = new ArrayList<TokenEvent>();

        agent.eventStream().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription s) { s.request(Long.MAX_VALUE); }
            @Override
            public void onNext(AgentEvent item) { if (item instanceof TokenEvent te) tokenEvents.add(te); }
            @Override
            public void onError(Throwable throwable) {}
            @Override
            public void onComplete() {}
        });

        agent.execute("Hallo");

        assertThat(tokenEvents).isNotEmpty();
        assertThat(tokenEvents.get(0).sessionId()).isNotBlank();
        assertThat(tokenEvents.get(0).token()).isNotBlank();
    }

    @Test
    void streamingAgentShouldSupportTools() {
        var registry = new ToolRegistry();
        registry.register(new CalculatorTool());
        var agent = new StreamingAgent(new MockStreamingChatModel(), registry, new ToolExecutor());
        var result = agent.execute("Was ist 2+3?");
        assertThat(result.finalAnswer()).isNotEmpty();
    }

    @Test
    void executeAsyncShouldHandleError() {
        var failingModel = new MockChatModel() {
            @Override
            public dev.langchain4j.model.chat.response.ChatResponse chat(
                    dev.langchain4j.model.chat.request.ChatRequest request) {
                throw new RuntimeException("API-Fehler");
            }
        };
        var agent = new StrandsAgent(failingModel);
        var future = agent.executeAsync("Hallo");
        var result = future.join();
        assertThat(result.stopReason()).isEqualTo(StopReason.ERROR);
    }
}
