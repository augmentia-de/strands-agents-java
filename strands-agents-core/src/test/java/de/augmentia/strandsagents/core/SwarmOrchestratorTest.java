package de.augmentia.strandsagents.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SwarmOrchestratorTest {

    @Test
    void shouldRouteToMatchingAgentByKeyword() {
        var weatherAgent = new StrandsAgent(new MockChatModel("Wetter-Antwort: %s"));
        var mathAgent = new StrandsAgent(new MockChatModel("Mathe-Antwort: %s"));
        var defaultAgent = new StrandsAgent(new MockChatModel("Default: %s"));

        var orchestrator = new SwarmOrchestrator(Map.of(
            "wetter", weatherAgent,
            "mathe", mathAgent
        ), defaultAgent);

        var result = orchestrator.execute("Wie wird das Wetter morgen?");

        assertThat(result.finalAnswer()).contains("[Orchestrator → wetter]");
        assertThat(result.finalAnswer()).contains("Wetter-Antwort");
    }

    @Test
    void shouldFallbackToDefaultAgent() {
        var mathAgent = new StrandsAgent(new MockChatModel("Mathe: %s"));
        var defaultAgent = new StrandsAgent(new MockChatModel("Default: %s"));

        var orchestrator = new SwarmOrchestrator(
            List.of(new SwarmOrchestrator.Route("mathe", mathAgent)),
            defaultAgent);

        var result = orchestrator.execute("Hallo, wie geht es dir?");

        assertThat(result.finalAnswer()).contains("[Orchestrator → Default]");
        assertThat(result.finalAnswer()).contains("Default");
    }

    @Test
    void shouldRouteBasedOnListOfRoutes() {
        var weatherAgent = new StrandsAgent(new MockChatModel("W: %s"));
        var mathAgent = new StrandsAgent(new MockChatModel("M: %s"));
        var defaultAgent = new StrandsAgent(new MockChatModel("D: %s"));

        var orchestrator = new SwarmOrchestrator(List.of(
            new SwarmOrchestrator.Route("wetter", weatherAgent),
            new SwarmOrchestrator.Route("rechnen", mathAgent)
        ), defaultAgent);

        assertThat(orchestrator.execute("5 + 3 rechnen").finalAnswer()).contains("[Orchestrator → rechnen]");
        assertThat(orchestrator.execute("Sonnenschein wetter").finalAnswer()).contains("[Orchestrator → wetter]");
    }

    @Test
    void shouldProvideRoutes() {
        var agent = new StrandsAgent(new MockChatModel());
        var orchestrator = new SwarmOrchestrator(
            List.of(new SwarmOrchestrator.Route("test", agent)),
            agent);

        assertThat(orchestrator.getRoutes()).hasSize(1);
        assertThat(orchestrator.getRoutes().get(0).topic()).isEqualTo("test");
    }
}
