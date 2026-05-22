package de.augmentia.strandsagents.examples.orchestrator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class LogManager {

    private static final Path RUN_LOG = Path.of("unreliable_run.log");
    private static final Path COMPLETE_LOG = Path.of("unreliable_complete.log");
    private static final Path PROBLEMS_LOG = Path.of("unreliable_problems.log");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public void clear() {
        for (var p : List.of(RUN_LOG, COMPLETE_LOG, PROBLEMS_LOG)) {
            try { Files.writeString(p, "", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING); }
            catch (IOException ignored) {}
        }
    }

    public void logRun(int iteration, String task, String resultSummary, boolean isMock) {
        var ts = LocalDateTime.now().format(FMT);
        var taskStr = task != null && !task.isBlank() ? task : "(not provided)";
        var resultStr = resultSummary != null && !resultSummary.isBlank() ? resultSummary : "(not provided)";
        var entry = new StringBuilder()
            .append("=== Iteration ").append(iteration).append(" ===\n")
            .append("Time: ").append(ts).append("\n")
            .append("Model: ").append(isMock ? "mock" : "openai").append("\n")
            .append("Task: ").append(taskStr).append("\n")
            .append("Answer: ").append(resultStr).append("\n")
            .append("---\n\n")
            .toString();

        write(RUN_LOG, entry, false);
        write(COMPLETE_LOG, entry, true);
    }

    public void logProblem(int iteration, String task, String issues) {
        var ts = LocalDateTime.now().format(FMT);
        var entry = new StringBuilder()
            .append("=== Problem Iteration ").append(iteration).append(" ===\n")
            .append("Time: ").append(ts).append("\n")
            .append("Task: ").append(task).append("\n")
            .append("Issues:\n").append(issues).append("\n")
            .append("---\n\n")
            .toString();

        write(PROBLEMS_LOG, entry, true);
    }

    public void logSummary(String summary) {
        var ts = LocalDateTime.now().format(FMT);
        var entry = new StringBuilder()
            .append("=== Summary (").append(ts).append(") ===\n")
            .append(summary).append("\n")
            .append("---\n\n")
            .toString();

        write(COMPLETE_LOG, entry, true);
        write(RUN_LOG, entry, false);
    }

    private void write(Path path, String content, boolean append) {
        try {
            var opts = append
                ? new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND}
                : new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING};
            Files.writeString(path, content, opts);
        } catch (IOException e) {
            System.err.println("Log write error: " + e.getMessage());
        }
    }

    public Path runLog() { return RUN_LOG; }
    public Path completeLog() { return COMPLETE_LOG; }
    public Path problemsLog() { return PROBLEMS_LOG; }
}
