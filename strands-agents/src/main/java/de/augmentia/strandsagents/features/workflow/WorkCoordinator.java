package de.augmentia.strandsagents.features.workflow;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface WorkCoordinator {

    CompletableFuture<StepResult> dispatch(
        WorkflowStep step,
        Map<String, Object> context
    );

    CompletableFuture<StepResult> collect(String workId);

    default HeartbeatListener heartbeatListener() { return null; }

    interface HeartbeatListener {
        void onHeartbeat(String workId, long timestamp);
    }

    record StepResult(
        String workId,
        String stepId,
        StepStatus status,
        Map<String, Object> outputs,
        String error
    ) {}
}
