package de.augmentia.strandsagents.core.plugin.hitl;

import de.augmentia.strandsagents.core.agent.Agent;
import de.augmentia.strandsagents.core.plugin.Plugin;

import java.util.List;

public class HITLPlugin implements Plugin {

    private final HITLProvider provider;
    private final HITLAuthority authority;
    private final List<String> reviewActions;
    private Agent agent;

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
    public void initAgent(Agent agent) {
        this.agent = agent;
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

    public Agent agent() {
        return agent;
    }
}
