package de.augmentia.strandsagents.quarkus.a2a;

import de.augmentia.strandsagents.core.agent.Agent;
import de.augmentia.strandsagents.core.agent.subagent.SubAgentExecutor;
import de.augmentia.strandsagents.quarkus.service.AgentService;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.server.PublicAgentCard;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Collections;
import java.util.List;

/**
 * CDI Producers for A2A server components in a Quarkus environment.
 * This class provides the {@link AgentCard} and {@link AgentExecutor} required by the
 * A2A Java SDK to expose the Strands Agent as an A2A service.
 */
@ApplicationScoped
public class StrandsA2AProducers {

    @Inject
    AgentService agentService;

    @ConfigProperty(name = "agent.url", defaultValue = "http://localhost:8080")
    String agentUrl;

    @Produces
    @PublicAgentCard
    public AgentCard agentCard() {
        return AgentCard.builder()
                .name("Strands Quarkus Agent")
                .description("Strands Agent exposed via A2A Protocol")
                .supportedInterfaces(Collections.singletonList(
                        new AgentInterface("JSONRPC", agentUrl)))
                .version("1.0.0")
                .capabilities(AgentCapabilities.builder()
                        .streaming(true)
                        .build())
                .defaultInputModes(Collections.singletonList("text"))
                .defaultOutputModes(Collections.singletonList("text"))
                .skills(Collections.singletonList(
                        AgentSkill.builder()
                                .id("strands_chat")
                                .name("Strands Chat")
                                .description("Chat with the Strands Agent")
                                .build()
                ))
                .build();
    }

    @Produces
    public SubAgentExecutor agentExecutor() {
        // We need a base agent instance from the service. 
        // For simplicity, we use one created with default tools.
        Agent agent = agentService.createDefaultAgent(); 
        return new SubAgentExecutor();
    }
}
