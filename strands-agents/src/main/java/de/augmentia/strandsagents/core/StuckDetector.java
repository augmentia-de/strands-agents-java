package de.augmentia.strandsagents.core;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class StuckDetector {

    private static final int DEFAULT_MAX_IDENTICAL_CALLS = 3;
    private static final int DEFAULT_MAX_OSCILLATION_CYCLES = 2;
    private static final int OSCILLATION_WINDOW = 4;

    private final int maxIdenticalCalls;
    private final int maxOscillationCycles;
    private final Map<String, Integer> toolCallFrequencies = new HashMap<>();
    private final Deque<String> signatureHistory = new ArrayDeque<>(OSCILLATION_WINDOW);
    private String lastSignature = "";
    private int cycleCount;

    StuckDetector() {
        this(DEFAULT_MAX_IDENTICAL_CALLS, DEFAULT_MAX_OSCILLATION_CYCLES);
    }

    StuckDetector(int maxIdenticalCalls, int maxOscillationCycles) {
        this.maxIdenticalCalls = maxIdenticalCalls;
        this.maxOscillationCycles = maxOscillationCycles;
    }

    boolean isStuck(List<ToolExecutionRequest> requests) {
        if (requests == null || requests.isEmpty()) return false;

        for (var req : requests) {
            var key = req.name() + "::" + (req.arguments() != null ? req.arguments() : "");
            int count = toolCallFrequencies.merge(key, 1, Integer::sum);
            if (count >= maxIdenticalCalls) return true;
        }

        var sig = requests.stream()
            .map(ToolExecutionRequest::name)
            .sorted()
            .collect(Collectors.joining(","));
        signatureHistory.addLast(sig);
        if (signatureHistory.size() >= OSCILLATION_WINDOW) {
            var first = signatureHistory.peekFirst();
            var last = signatureHistory.peekLast();
            if (first != null && first.equals(last)) {
                var distinct = signatureHistory.stream().distinct().count();
                if (distinct == 2) {
                    cycleCount++;
                    if (cycleCount >= maxOscillationCycles) return true;
                }
            }
        }

        if (!sig.equals(lastSignature)) {
            cycleCount = 0;
            lastSignature = sig;
        }

        return false;
    }

    void reset() {
        toolCallFrequencies.clear();
        signatureHistory.clear();
        lastSignature = "";
        cycleCount = 0;
    }
}
