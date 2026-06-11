package de.augmentia.strandsagents.features.hitl.checkpoint;

@FunctionalInterface
public interface CheckpointChannel {
    void notify(Checkpoint checkpoint);
}
