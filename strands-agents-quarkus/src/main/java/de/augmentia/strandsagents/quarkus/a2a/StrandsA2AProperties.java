package de.augmentia.strandsagents.quarkus.a2a;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Demo-level A2A configuration via application.properties.
 *
 * <p><b>Demo notice:</b> This is a simplified demo integration of the A2A protocol.
 * It only supports a single fixed, property-configured agent without per-session isolation,
 * dynamic tool selection, or streaming. For production A2A scenarios the
 * {@code StrandsAgentExecutor} should work session-based and mirror the full flexibility
 * of the REST API (initAgent, tool filtering, MCP, skills).</p>
 */
@ApplicationScoped
public class StrandsA2AProperties {

    @ConfigProperty(name = "a2a.enabled", defaultValue = "false")
    boolean enabled;

    @ConfigProperty(name = "a2a.agent.name", defaultValue = "Strands A2A Agent")
    String agentName;

    @ConfigProperty(name = "a2a.agent.description", defaultValue = "Demo agent for A2A protocol testing")
    String agentDescription;

    @ConfigProperty(name = "a2a.system-prompt", defaultValue = "You are a helpful demo agent. Answer the user's questions using your available tools.")
    String systemPrompt;

    @ConfigProperty(name = "a2a.tools", defaultValue = "")
    String tools;

    @ConfigProperty(name = "a2a.session-timeout-seconds", defaultValue = "300")
    int sessionTimeoutSeconds;

    public boolean enabled() { return enabled; }
    public String agentName() { return agentName; }
    public String agentDescription() { return agentDescription; }
    public String systemPrompt() { return systemPrompt; }
    public String tools() { return tools; }
    public int sessionTimeoutSeconds() { return sessionTimeoutSeconds; }
}
