package de.augmentia.strandsagents.testagent.engine;

import de.augmentia.strandsagents.core.model.agent.AgentResult;
import de.augmentia.strandsagents.testagent.config.TestConfig;

public class ResultValidator {

    private final TestConfig.AssertConfig asserts;

    public ResultValidator(TestConfig.AssertConfig asserts) {
        this.asserts = asserts;
    }

    public boolean validate(AgentResult result) {
        if (asserts == null) return true;

        if (asserts.finalAnswerNotNull()) {
            if (result.finalAnswer() == null || result.finalAnswer().isBlank()) {
                return false;
            }
        }

        if (asserts.stopReason() != null && !asserts.stopReason().isEmpty()) {
            if (!asserts.stopReason().contains(result.stopReason().name())) {
                return false;
            }
        }

        var metrics = result.metrics();
        if (metrics != null) {
            if (metrics.durationMs() < asserts.metricsDurationMsMin()) {
                return false;
            }
            if (metrics.toolCallsCount() < asserts.metricsToolCallsMin()) {
                return false;
            }
        }

        if (asserts.expectedOutputContains() != null
                && !result.finalAnswer()
                    .contains(asserts.expectedOutputContains())) {
            return false;
        }

        if (asserts.expectedOutputNotContains() != null
                && result.finalAnswer()
                    .contains(asserts.expectedOutputNotContains())) {
            return false;
        }

        return true;
    }
}
