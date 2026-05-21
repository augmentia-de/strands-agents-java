package de.augmentia.strandsagents.testagent.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.augmentia.strandsagents.core.model.agent.AgentResult;
import de.augmentia.strandsagents.testagent.config.TestConfig;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ResultRecorder {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    private static final Path RESULTS_FILE = Path.of("test-results.yaml");

    private ResultRecorder() {}

    public static synchronized void record(
            TestConfig config, AgentResult result,
            boolean passed, long durationMs, Throwable error) {
        var entry = new TestResult(
            config.run().variant(),
            config.run().label(),
            passed,
            durationMs,
            result != null ? result.stopReason().name() : "EXCEPTION",
            result != null && result.metrics() != null
                ? result.metrics().toolCallsCount() : -1,
            result != null && result.metrics() != null
                ? result.metrics().inputTokens() : -1,
            result != null && result.metrics() != null
                ? result.metrics().outputTokens() : -1,
            error != null ? error.getClass().getSimpleName()
                + ": " + error.getMessage() : null
        );

        var results = loadResults();
        results.add(entry);
        saveResults(results);
    }

    public static List<TestResult> loadResults() {
        if (!RESULTS_FILE.toFile().exists()) return new ArrayList<>();
        try {
            var node = MAPPER.readTree(RESULTS_FILE.toFile());
            var arr = node.get("results");
            if (arr == null || !arr.isArray()) return new ArrayList<>();
            var list = new ArrayList<TestResult>();
            for (var el : arr) {
                list.add(MAPPER.treeToValue(el, TestResult.class));
            }
            return list;
        } catch (IOException e) {
            System.err.println("Failed to load results: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public static void saveResults(List<TestResult> results) {
        try {
            var dir = RESULTS_FILE.getParent();
            if (dir != null) java.nio.file.Files.createDirectories(dir);
            MAPPER.writerWithDefaultPrettyPrinter()
                .writeValue(RESULTS_FILE.toFile(), Map.of("results", results));
        } catch (IOException e) {
            System.err.println("Failed to save results: " + e.getMessage());
        }
    }
}
