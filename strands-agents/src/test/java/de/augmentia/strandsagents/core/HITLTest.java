package de.augmentia.strandsagents.core;

import static org.assertj.core.api.Assertions.assertThat;

import de.augmentia.strandsagents.core.MockChatModel;
import de.augmentia.strandsagents.core.Agent;
import de.augmentia.strandsagents.model.agent.AgentPhase;
import java.util.List;

import de.augmentia.strandsagents.features.guardrails.ApprovalResult;
import de.augmentia.strandsagents.features.hitl.HITLAuthority;
import de.augmentia.strandsagents.features.hitl.HITLPlugin;
import de.augmentia.strandsagents.features.hitl.HITLProvider;
import org.junit.jupiter.api.Test;

class HITLTest {

    @Test
    void approvalResultFactoryMethods() {
        var approved = ApprovalResult.approved("test-action");
        assertThat(approved.approved()).isTrue();
        assertThat(approved.action()).isEqualTo("test-action");

        var denied = ApprovalResult.denied("test-action", "not allowed");
        assertThat(denied.approved()).isFalse();
        assertThat(denied.feedback()).isEqualTo("not allowed");
    }

    @Test
    void hitlPluginStoresConfig() {
        var provider = (HITLProvider) (action, context) -> ApprovalResult.approved(action);
        var plugin = new HITLPlugin(provider, HITLAuthority.CONFIRM,
            List.of("payment", "db-write"));

        assertThat(plugin.name()).isEqualTo("hitl");
        assertThat(plugin.authority()).isEqualTo(HITLAuthority.CONFIRM);
        assertThat(plugin.reviewActions()).contains("payment", "db-write");
    }

    @Test
    void hitlPluginDefaultReviewActions() {
        var provider = (HITLProvider) (action, context) -> ApprovalResult.approved(action);
        var plugin = new HITLPlugin(provider, HITLAuthority.AUTO);

        assertThat(plugin.reviewActions()).isEmpty();
    }

    @Test
    void hitlAuthorityValues() {
        assertThat(HITLAuthority.values()).containsExactly(
            HITLAuthority.AUTO, HITLAuthority.CONFIRM,
            HITLAuthority.REVIEW, HITLAuthority.DENY);
    }

    @Test
    void strandAgentPhaseLifecycle() {
        var model = new MockChatModel();
        var agent = new Agent(model);

        assertThat(agent.getPhase()).isEqualTo(AgentPhase.IDLE);

        agent.pauseExecution();
        assertThat(agent.getPhase()).isEqualTo(AgentPhase.WAITING_FOR_HUMAN);

        agent.approve();
        assertThat(agent.getPhase()).isEqualTo(AgentPhase.EXECUTING);

        agent.pauseExecution();
        agent.reject("cancelled");
        assertThat(agent.getPhase()).isEqualTo(AgentPhase.FAILED);
    }

    @Test
    void strandAgentResumeExecution() {
        var model = new MockChatModel();
        var agent = new Agent(model);

        agent.pauseExecution();
        assertThat(agent.getPhase()).isEqualTo(AgentPhase.WAITING_FOR_HUMAN);

        agent.resumeExecution();
        assertThat(agent.getPhase()).isEqualTo(AgentPhase.EXECUTING);
    }
}
