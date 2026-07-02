package de.augmentia.strandsagents.interceptor.hitl.checkpoint;

@FunctionalInterface
public interface CheckpointChannel {
    void notify(Checkpoint checkpoint);
}
