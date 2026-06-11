package de.augmentia.strandsagents.features.planning;

import de.augmentia.strandsagents.features.workflow.StepStatus;
import java.util.Optional;

public interface CheckpointStore {
    void saveStepStatus(String sessionId, String stepId, StepStatus status, String output);
    Optional<StepStatus> loadStepStatus(String sessionId, String stepId);
    void clearSession(String sessionId);
}
