package de.augmentia.strandsagents.core;

import static org.assertj.core.api.Assertions.assertThat;

import de.augmentia.strandsagents.config.ModelTier;
import de.augmentia.strandsagents.core.routing.LlmRouter;
import de.augmentia.strandsagents.interceptor.resilience.ResilienceConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoutingAgentTest {

    @Test
    void resolveRoutingTier_withRouter_returnsAdvanced() {
        var simple = new MockChatModel("response");
        var advanced = new MockChatModel("expert response");
        var router = new LlmRouter(new MockChatModel("ADVANCED"));
        var agent = new RoutingAgent(simple, advanced, router,
            new ToolRegistry(), new DefaultToolExecutor(), null, null, null,
            ResilienceConfig.DEFAULT, List.of());
        var tier = agent.resolveRoutingTier("complex problem");
        assertThat(tier).isEqualTo(ModelTier.ADVANCED);
        assertThat(agent.getResolvedTier()).isEqualTo(ModelTier.ADVANCED);
    }

    @Test
    void resolveRoutingTier_withRouter_returnsSimple() {
        var simple = new MockChatModel("response");
        var advanced = new MockChatModel("expert response");
        var router = new LlmRouter(new MockChatModel("SIMPLE"));
        var agent = new RoutingAgent(simple, advanced, router,
            new ToolRegistry(), new DefaultToolExecutor(), null, null, null,
            ResilienceConfig.DEFAULT, List.of());
        var tier = agent.resolveRoutingTier("simple query");
        assertThat(tier).isEqualTo(ModelTier.SIMPLE);
    }

    @Test
    void resolveRoutingTrie_withoutRouter_fallsBackToModelCall() {
        var simple = new MockChatModel("The topic is SIMPLE");
        var advanced = new MockChatModel("expert");
        var agent = new RoutingAgent(simple, advanced, null,
            new ToolRegistry(), new DefaultToolExecutor(), null, null, null,
            ResilienceConfig.DEFAULT, List.of());
        var tier = agent.resolveRoutingTier("help me");
        assertThat(tier).isIn(ModelTier.SIMPLE, ModelTier.ADVANCED);
        assertThat(agent.getResolvedTier()).isIn(ModelTier.SIMPLE, ModelTier.ADVANCED);
    }

    @Test
    void applyRouting_switchesToAdvanced() {
        var simple = new MockChatModel("simple");
        var advanced = new MockChatModel("advanced");
        var router = new LlmRouter(new MockChatModel("ADVANCED"));
        var agent = new RoutingAgent(simple, advanced, router,
            new ToolRegistry(), new DefaultToolExecutor(), null, null, null,
            ResilienceConfig.DEFAULT, List.of());
        agent.resolveRoutingTier("hard task");
        agent.applyRouting();
        assertThat(agent.getModelTier()).isEqualTo(ModelTier.ADVANCED);
    }

    @Test
    void applyRouting_switchesToSimple() {
        var simple = new MockChatModel("simple");
        var advanced = new MockChatModel("advanced");
        var router = new LlmRouter(new MockChatModel("SIMPLE"));
        var agent = new RoutingAgent(simple, advanced, router,
            new ToolRegistry(), new DefaultToolExecutor(), null, null, null,
            ResilienceConfig.DEFAULT, List.of());
        agent.resolveRoutingTier("easy task");
        agent.applyRouting();
        assertThat(agent.getModelTier()).isEqualTo(ModelTier.SIMPLE);
    }

    @Test
    void initialTierIsROUTING() {
        var simple = new MockChatModel("simple");
        var advanced = new MockChatModel("advanced");
        var agent = new RoutingAgent(simple, advanced, null,
            new ToolRegistry(), new DefaultToolExecutor(), null, null, null,
            ResilienceConfig.DEFAULT, List.of());
        assertThat(agent.getModelTier()).isEqualTo(ModelTier.ROUTING);
    }

    @Test
    void getCurrentModel_returnsSimpleOrAdvancedBasedOnTier() {
        var simple = new MockChatModel("simple");
        var advanced = new MockChatModel("advanced");
        var agent = new RoutingAgent(simple, advanced, null,
            new ToolRegistry(), new DefaultToolExecutor(), null, null, null,
            ResilienceConfig.DEFAULT, List.of());
        agent.switchTier(ModelTier.ADVANCED);
        assertThat(agent.getCurrentModel()).isSameAs(advanced);
        agent.switchTier(ModelTier.SIMPLE);
        assertThat(agent.getCurrentModel()).isSameAs(simple);
    }
}
