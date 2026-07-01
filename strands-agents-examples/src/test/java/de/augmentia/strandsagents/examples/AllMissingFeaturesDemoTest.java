package de.augmentia.strandsagents.examples;

import de.augmentia.strandsagents.examples.feature.AgentServiceDemo;
import de.augmentia.strandsagents.examples.feature.GatedWorkflowDemo;
import de.augmentia.strandsagents.examples.feature.SecretVaultDemo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Runs all 4 missing-feature demos sequentially.
 * Each demo covers feature areas with no prior demo coverage:
 * - SmartPlannerDemo: context, planning, routing, secrets
 * - GatedWorkflowDemo: gate, workflow
 * - SecretVaultDemo: secrets (standalone + composite)
 * - AgentServiceDemo: service (umbrella API)
 */
class AllMissingFeaturesDemoTest {

    @Test
    void smartPlannerDemo() {
        assertThatNoException().isThrownBy(() -> SmartPlannerDemo.run());
    }

    @Test
    void gatedWorkflowDemo() {
        assertThatNoException().isThrownBy(() -> GatedWorkflowDemo.run());
    }

    @Test
    void secretVaultDemo() {
        assertThatNoException().isThrownBy(() -> SecretVaultDemo.run());
    }

    @Test
    void agentServiceDemo() {
        assertThatNoException().isThrownBy(() -> AgentServiceDemo.run());
    }
}
