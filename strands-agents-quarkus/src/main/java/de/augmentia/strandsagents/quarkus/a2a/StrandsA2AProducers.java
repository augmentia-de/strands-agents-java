package de.augmentia.strandsagents.quarkus.a2a;

import de.augmentia.strandsagents.quarkus.service.AgentService;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.spec.*;
import org.a2aproject.sdk.server.PublicAgentCard;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;

/**
 * CDI Producers for A2A server components in a Quarkus environment.
 * This class provides the {@link AgentCard} and {@link AgentExecutor} required by the
 * A2A Java SDK to expose the Strands Agent as an A2A service.
 *
 * <p><b>Demo notice:</b> This is a demo integration of the A2A protocol.
 * The {@link AgentExecutor} works with a single fixed agent configured via {@link StrandsA2AProperties}.
 * The {@link AgentCard} lists the available tools as skills.
 * Disabled by default ({@code a2a.enabled=false}).
 * For production scenarios see the REST API (/api/agent/init, /api/chat).</p>
 */
@ApplicationScoped
public class StrandsA2AProducers {

    @Inject
    AgentService agentService;

    @Inject
    StrandsA2AProperties a2aProps;

    @ConfigProperty(name = "agent.url", defaultValue = "http://localhost:8080")
    String agentUrl;

    @Produces
    @PublicAgentCard
    public AgentCard agentCard() {
        return AgentCard.builder()
                .name(a2aProps.agentName())
                .description(a2aProps.agentDescription())
                .provider(new AgentProvider("Augmentia", agentUrl))
                .version("1.0.0")
                .url(agentUrl)
                .capabilities(AgentCapabilities.builder()
                        .streaming(false)
                        .build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(buildSkills())
                .supportedInterfaces(List.of(
                        new AgentInterface("JSONRPC", agentUrl)))
                .build();
    }

    @Produces
    public AgentExecutor agentExecutor() {
        return new StrandsAgentExecutor(agentService, a2aProps);
    }

    private List<AgentSkill> buildSkills() {
        var tools = agentService.listTools();
        var toolFilter = a2aProps.tools().isBlank() ? null
                : java.util.Set.of(a2aProps.tools().split(","));
        return tools.stream()
                .filter(t -> toolFilter == null || toolFilter.contains(t.name))
                .map(t -> AgentSkill.builder()
                        .id(t.name)
                        .name(t.name)
                        .description(t.description != null ? t.description : "")
                        .build())
                .toList();
    }
}
