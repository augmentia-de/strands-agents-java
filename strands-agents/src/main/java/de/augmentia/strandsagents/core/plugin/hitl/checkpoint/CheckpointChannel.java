package de.augmentia.strandsagents.core.plugin.hitl.checkpoint;

@FunctionalInterface
public interface CheckpointChannel {
    void notify(Checkpoint checkpoint);
}
