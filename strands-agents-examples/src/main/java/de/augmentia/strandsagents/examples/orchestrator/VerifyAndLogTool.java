package de.augmentia.strandsagents.examples.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.util.ArrayList;
import java.util.List;

public class VerifyAndLogTool {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final LogManager logManager;

    public VerifyAndLogTool(LogManager logManager) {
        this.logManager = logManager;
    }

    @Tool("Verifies a task result and logs it. Returns issues found or 'OK'.")
    public String verifyAndLog(
            @P("iteration") int iteration,
            @P("taskPrompt") String taskPrompt,
            @P("resultSummary") String resultSummary) {

        logManager.logRun(iteration, taskPrompt, resultSummary, false);

        var issues = verify(taskPrompt, resultSummary);

        if (!issues.isEmpty()) {
            var details = String.join("\n", issues);
            logManager.logProblem(iteration, taskPrompt, details);
            return String.format("Iteration %d: %d issue(s) — %s", iteration, issues.size(), issues.get(0));
        }

        return "Iteration " + iteration + ": OK — no issues found.";
    }

    private List<String> verify(String task, String result) {
        var issues = new ArrayList<String>();

        var stopReason = extractField(result, "StopReason");
        if (stopReason != null && !"COMPLETED".equals(stopReason)) {
            issues.add("StopReason=" + stopReason);
        }

        var errorsStr = extractField(result, "Errors");
        if (errorsStr != null) {
            try {
                var errors = Integer.parseInt(errorsStr.trim());
                if (errors > 0) issues.add(errors + " tool error(s)");
            } catch (NumberFormatException ignored) {}
        }

        var timeoutsStr = extractField(result, "Timeouts");
        if (timeoutsStr != null) {
            try {
                var timeouts = Integer.parseInt(timeoutsStr.trim());
                if (timeouts > 0) issues.add(timeouts + " timeout(s)");
            } catch (NumberFormatException ignored) {}
        }

        // Check structured output validity
        var structStr = extractField(result, "Structured");
        if ("ERROR".equalsIgnoreCase(structStr)) {
            issues.add("structured output failed");
        } else if ("OK".equalsIgnoreCase(structStr)) {
            // Structured output present — look for embedded JSON in Answer field
            var answer = extractField(result, "Answer");
            if (answer != null && looksLikeJson(answer)) {
                try {
                    JSON.readTree(answer);
                } catch (Exception e) {
                    issues.add("structure error: Answer JSON is invalid — " + e.getMessage());
                }
            }
        }

        // Check StructErrors count
        var structErrorsStr = extractField(result, "StructErrors");
        if (structErrorsStr != null) {
            try {
                var se = Integer.parseInt(structErrorsStr.trim());
                if (se > 0) issues.add(se + " structured error(s) detected by TraceRecorder");
            } catch (NumberFormatException ignored) {}
        }

        return issues;
    }

    private static boolean looksLikeJson(String s) {
        if (s == null || s.isBlank()) return false;
        var t = s.trim();
        return (t.startsWith("{") && t.endsWith("}"))
            || (t.startsWith("[") && t.endsWith("]"));
    }

    private String extractField(String text, String field) {
        var prefix = field + ": ";
        var idx = text.indexOf(prefix);
        if (idx < 0) return null;
        var start = idx + prefix.length();
        var end = text.indexOf('\n', start);
        return end > start ? text.substring(start, end).trim() : text.substring(start).trim();
    }
}
