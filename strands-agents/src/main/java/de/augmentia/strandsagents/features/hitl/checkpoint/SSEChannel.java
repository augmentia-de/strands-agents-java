package de.augmentia.strandsagents.features.hitl.checkpoint;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class SSEChannel implements CheckpointChannel {

    private final ConcurrentHashMap<String, Consumer<String>> sessionEmitters = new ConcurrentHashMap<>();

    public void register(String sessionId, Consumer<String> emitter) {
        sessionEmitters.put(sessionId, emitter);
    }

    public void unregister(String sessionId) {
        sessionEmitters.remove(sessionId);
    }

    @Override
    public void notify(Checkpoint checkpoint) {
        var emitter = sessionEmitters.get(checkpoint.sessionId());
        if (emitter != null) {
            var json = String.format(
                "{\"type\":\"checkpoint\",\"checkpointId\":\"%s\",\"toolName\":\"%s\",\"sessionId\":\"%s\",\"arguments\":%s}",
                escape(checkpoint.id()),
                escape(checkpoint.toolName()),
                escape(checkpoint.sessionId()),
                checkpoint.arguments() != null ? checkpoint.arguments() : "{}"
            );
            emitter.accept(json);
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public Map<String, Consumer<String>> getEmitters() {
        return sessionEmitters;
    }
}
