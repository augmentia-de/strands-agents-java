package com.strands.agents.core;

import com.strands.agents.core.model.agent.AgentPhase;
import static com.strands.agents.core.model.agent.AgentPhase.WAITING_FOR_HUMAN;
import com.strands.agents.core.model.event.AgentStateChangedEvent;
import com.strands.agents.core.model.event.BeforeInvocationEvent;
import com.strands.agents.core.model.event.ToolExecutionStartedEvent;
import java.time.Instant;
import java.util.List;

public class HITLPlugin implements Plugin {

    private final HITLProvider provider;
    private final HITLAuthority authority;
    private final List<String> reviewActions;
    private StrandsAgent agent;

    public HITLPlugin(HITLProvider provider, HITLAuthority authority) {
        this(provider, authority, List.of());
    }

    public HITLPlugin(HITLProvider provider, HITLAuthority authority, List<String> reviewActions) {
        this.provider = provider;
        this.authority = authority;
        this.reviewActions = reviewActions;
    }

    @Override
    public String name() {
        return "hitl";
    }

    @Override
    public void initAgent(StrandsAgent strandsAgent) {
        this.agent = strandsAgent;
    }

    public HITLProvider provider() {
        return provider;
    }

    public HITLAuthority authority() {
        return authority;
    }

    public List<String> reviewActions() {
        return reviewActions;
    }

    public StrandsAgent agent() {
        return agent;
    }
}
