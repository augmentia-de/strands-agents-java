package de.augmentia.strandsagents.interceptor.hitl.checkpoint;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CheckpointService {

    private static final Logger log = LoggerFactory.getLogger(CheckpointService.class);

    private final CheckpointStore store;
    private final List<CheckpointChannel> channels = new CopyOnWriteArrayList<>();
    private final long timeoutMs;
    private final String hitlTools;

    public CheckpointService(CheckpointStore store, String hitlTools, long timeoutMs) {
        this.store = store != null ? store : new InMemoryCheckpointStore();
        this.hitlTools = hitlTools != null ? hitlTools : "";
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : 120_000;
    }

    public CheckpointService() {
        this(new InMemoryCheckpointStore(), System.getenv("STRANDS_AGENT_HITL_TOOLS"), 120_000);
    }

    public CheckpointService(String hitlTools, long timeoutMs) {
        this(new InMemoryCheckpointStore(), hitlTools, timeoutMs);
    }

    public boolean requiresApproval(String toolName) {
        if (hitlTools.isBlank()) return false;
        for (var t : hitlTools.split(",")) {
            if (t.trim().equalsIgnoreCase(toolName)) return true;
        }
        return false;
    }

    public Checkpoint createCheckpoint(String sessionId, String toolName, String arguments) {
        var cp = new Checkpoint(
            sessionId + ":" + toolName + ":" + System.nanoTime(),
            sessionId, toolName, arguments
        );
        store.save(cp);
        for (var ch : channels) {
            try {
                ch.notify(cp);
            } catch (Exception e) {
                log.warn("CheckpointChannel '{}' failed to notify checkpoint {}: {}",
                    ch.getClass().getSimpleName(), cp.id(), e.getMessage());
            }
        }
        return cp;
    }

    public boolean approve(String checkpointId, String feedback) {
        var cp = store.load(checkpointId).orElse(null);
        if (cp == null || cp.status() != Checkpoint.Status.PENDING) return false;
        cp.approve(feedback != null ? feedback : "");
        store.updateStatus(checkpointId, Checkpoint.Status.APPROVED, cp.feedback());
        return true;
    }

    public boolean reject(String checkpointId, String feedback) {
        var cp = store.load(checkpointId).orElse(null);
        if (cp == null || cp.status() != Checkpoint.Status.PENDING) return false;
        cp.reject(feedback != null ? feedback : "Rejected");
        store.updateStatus(checkpointId, Checkpoint.Status.REJECTED, cp.feedback());
        return true;
    }

    public Checkpoint getCheckpoint(String id) {
        return store.load(id).orElse(null);
    }

    public List<Checkpoint> getPendingCheckpoints(String sessionId) {
        return store.findPending(sessionId);
    }

    public void registerChannel(CheckpointChannel channel) {
        if (channel != null) channels.add(channel);
    }

    public void unregisterChannel(CheckpointChannel channel) {
        channels.remove(channel);
    }

    public long timeoutMs() {
        return timeoutMs;
    }

    public Checkpoint await(Checkpoint cp) throws InterruptedException {
        try {
            return cp.future().get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            cp.reject("Timeout after " + timeoutMs + "ms");
            return cp;
        } catch (java.util.concurrent.ExecutionException e) {
            cp.reject("Error: " + e.getCause().getMessage());
            return cp;
        }
    }
}
