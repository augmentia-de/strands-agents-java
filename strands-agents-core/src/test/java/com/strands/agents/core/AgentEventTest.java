package com.strands.agents.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.strands.agents.core.model.event.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentEventTest {

    @Test
    void shouldFireLifecycleEventsInOrder() {
        var events = new ArrayList<AgentEvent>();
        var agent = new StrandsAgent(new MockChatModel());
        agent.setEventListener(events::add);

        agent.execute("Hallo");

        assertThat(events).isNotEmpty();
        assertThat(events.get(0)).isInstanceOf(AgentStartedEvent.class);
        assertThat(((AgentStartedEvent) events.get(0)).initialPrompt()).isEqualTo("Hallo");
        assertThat(events.get(events.size() - 1)).isInstanceOf(AgentFinishedEvent.class);
    }

    @Test
    void shouldFireModelRequestedEvent() {
        var events = new ArrayList<AgentEvent>();
        var agent = new StrandsAgent(new MockChatModel());
        agent.setEventListener(events::add);

        agent.execute("Test");

        var modelRequested = events.stream()
            .filter(e -> e instanceof ModelRequestedEvent)
            .map(e -> (ModelRequestedEvent) e)
            .toList();
        assertThat(modelRequested).hasSize(1);
        assertThat(modelRequested.get(0).promptHistory()).isNotEmpty();
    }

    @Test
    void shouldIncludeSessionIdInEvents() {
        var events = new ArrayList<AgentEvent>();
        var agent = new StrandsAgent(new MockChatModel());
        agent.setEventListener(events::add);

        agent.execute("Test");

        var sessionId = agent.getSessionId();
        assertThat(events).allMatch(e -> e.sessionId().equals(sessionId));
    }

    @Test
    void shouldFireAgentFinishedWithCorrectStopReason() {
        var events = new ArrayList<AgentEvent>();
        var agent = new StrandsAgent(new MockChatModel());
        agent.setEventListener(events::add);

        agent.execute("Hallo");

        var finished = (AgentFinishedEvent) events.get(events.size() - 1);
        assertThat(finished.finalAnswer()).contains("Mock antwortet");
    }

    @Test
    void shouldWorkWithoutEventListener() {
        var agent = new StrandsAgent(new MockChatModel());
        var result = agent.execute("Hallo");
        assertThat(result.finalAnswer()).isNotEmpty();
    }
}
