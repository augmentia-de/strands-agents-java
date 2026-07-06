package de.augmentia.strandsagents.examples.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

    public class ToolCallingMockModel implements ChatModel {

    // Per-agent maxToolIterations, default 12 in AgentSettings.
    static int MAX_AGENT_LOOP = 12;
    private static final ObjectMapper JSON = new ObjectMapper();
    private boolean structuredMode;
    private static final Pattern EDIT_KEYWORDS =
        Pattern.compile("(?i)\\b(edit|replace|change|modify|update|sed)\\b");
    private static final Pattern GREP_KEYWORDS =
        Pattern.compile("(?i)\\b(grep|search|find|pattern|contains?|match)\\b");
    private static final Pattern ITERATIONS_PATTERN =
        Pattern.compile("run (\\d+) iterations", Pattern.CASE_INSENSITIVE);

    private final double wrongToolProb;
    private final double skipToolProb;
    private final double badArgsProb;
    private final double unknownToolProb;

    private ToolCallingMockModel(Builder b) {
        this.wrongToolProb = b.wrongToolProb;
        this.skipToolProb = b.skipToolProb;
        this.badArgsProb = b.badArgsProb;
        this.unknownToolProb = b.unknownToolProb;
        sanityCheck();
    }

    private void sanityCheck() {
        var sum = wrongToolProb + skipToolProb + badArgsProb + unknownToolProb;
        if (sum > 0.5) {
            throw new IllegalArgumentException("Total chaos probability too high: " + sum
                + " (max 0.5).");
        }
    }

    public static ToolCallingMockModel createDefault() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private double wrongToolProb = 0.05;
        private double skipToolProb = 0.03;
        private double badArgsProb = 0.02;
        private double unknownToolProb = 0.01;
        public Builder wrongToolProb(double p) { this.wrongToolProb = p; return this; }
        public Builder skipToolProb(double p) { this.skipToolProb = p; return this; }
        public Builder badArgsProb(double p) { this.badArgsProb = p; return this; }
        public Builder unknownToolProb(double p) { this.unknownToolProb = p; return this; }
        public ToolCallingMockModel build() { return new ToolCallingMockModel(this); }
    }

    private boolean roll(double prob) {
        return prob > 0 && ThreadLocalRandom.current().nextDouble() < prob;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        var messages = request.messages();
        var tools = request.toolSpecifications();

        if (messages.isEmpty()) return textResponse("");

        // Structured output mode: return mock JSON if a response format is set
        var responseFormat = request.responseFormat();
        this.structuredMode = responseFormat != null
            && responseFormat.jsonSchema() != null;

        var userText = extractUserText(messages);
        boolean hasToolResult = messages.stream().anyMatch(m -> m instanceof ToolExecutionResultMessage);

        // Phase 2: tool result in history → decide what to do next
        if (hasToolResult) {
            return handlePostToolCall(messages, userText, tools);
        }

        // Phase 1: no tool results yet → decide whether to make a tool call
        if (tools == null || tools.isEmpty()) {
            if (this.structuredMode) {
                return structuredResponse(userText);
            }
            return textResponse("Mock answer: " + userText);
        }

        var toolNames = tools.stream().map(ToolSpecification::name).toList();

        // Orchestrator mode: detect by tool names
        if (isOrchestratorMode(toolNames)) {
            return startOrchestratorCycle(userText, tools);
        }

        // Normal unreliable-tools mode: keyword-based detection
        if (this.structuredMode) {
            return structuredToolResponse(userText, tools);
        }
        return handleUnknownToolsCall(userText, tools);
    }

    // --- Phase 2: tool result already in conversation ---

    private ChatResponse handlePostToolCall(List<ChatMessage> messages, String userText,
                                             List<ToolSpecification> tools) {
        var toolNames = tools != null
            ? tools.stream().map(ToolSpecification::name).toList()
            : List.<String>of();

        if (isOrchestratorMode(toolNames)) {
            return continueOrchestratorCycle(messages, userText, tools);
        }

        // Sub-agent mode: combine all tool results
        if (this.structuredMode) {
            return structuredPostToolCall(messages);
        }
        var text = messages.stream()
            .filter(m -> m instanceof ToolExecutionResultMessage)
            .map(m -> ((ToolExecutionResultMessage) m).text())
            .collect(Collectors.joining(" | "));
        if (text.isEmpty()) text = "done";
        if (text.length() > 200) text = text.substring(0, 200) + "...";
        return textResponse("Result: " + text);
    }

    // --- Orchestrator Mode ---

    private boolean isOrchestratorMode(List<String> toolNames) {
        return toolNames.contains("runTask");
    }

    private ChatResponse startOrchestratorCycle(String userText, List<ToolSpecification> tools) {
        if (countToolCallsInHistory(List.of()) + 2 >= MAX_AGENT_LOOP) {
            return textResponse("Orchestrator completed. Summary: ran tasks. Check logs.");
        }
        return callRunTask(generateTask(), tools);
    }

    private ChatResponse continueOrchestratorCycle(List<ChatMessage> messages, String userText,
                                                    List<ToolSpecification> tools) {
        var lastCallName = findLastToolCallName(messages);
        var verifyCount = countToolResults(messages, "verifyAndLog");
        var iterations = extractIterations(messages);
        var lastRunTaskResult = findLastToolResultByTool(messages, "runTask");

        var toolCallsSoFar = countToolCallsInHistory(messages);

        // Check if there's room for another full cycle (runTask + verifyAndLog)
        if (toolCallsSoFar + 2 >= MAX_AGENT_LOOP || verifyCount >= iterations) {
            var done = Math.min(verifyCount, iterations);
            return textResponse("All " + done + " iterations completed. Summary: ran "
                + done + " tasks with varying results. Check log files for details.");
        }

        // After runTask → call verifyAndLog
        if ("runTask".equals(lastCallName)) {
            var taskPrompt = extractTaskPromptFromHistory(messages);
            var resultSummary = lastRunTaskResult != null ? lastRunTaskResult.text() : "no result";
            return callVerifyAndLog(verifyCount + 1, taskPrompt, resultSummary, tools);
        }

        // After verifyAndLog → start next iteration
        return callRunTask(generateTask(), tools);
    }

    private String extractTaskPromptFromHistory(List<ChatMessage> messages) {
        // Find the last AiMessage that called runTask and extract the taskPrompt from its arguments
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof AiMessage ai) {
                var reqs = ai.toolExecutionRequests();
                if (reqs != null) {
                    for (var req : reqs) {
                        if ("runTask".equals(req.name())) {
                            try {
                                var tree = JSON.readTree(req.arguments());
                                var task = tree.get("taskPrompt");
                                if (task != null) return task.asText();
                            } catch (Exception ignored) {}
                            return "task from history";
                        }
                    }
                }
            }
        }
        return "Read workspace/data.txt and grep for 'TODO'";
    }

    private String generateTask() {
        var r = ThreadLocalRandom.current();
        var files = List.of("workspace/data.txt", "workspace/out.txt", "workspace/result.txt",
            "workspace/test.txt", "workspace/config.txt", "workspace/notes.txt");
        var file1 = files.get(r.nextInt(files.size()));
        var file2 = files.get(r.nextInt(files.size()));
        var patterns = List.of("TODO", "ERROR", "FIXME", "username", "password", "api_key", "DEBUG");
        var pattern = patterns.get(r.nextInt(patterns.size()));
        var oldText = List.of("foo", "old_value", "placeholder", "admin", "localhost");
        var newText = List.of("bar", "new_value", "actual_value", "guest", "0.0.0.0");

        return switch (r.nextInt(6)) {
            case 0 -> "Read " + file1;
            case 1 -> "Grep for '" + pattern + "' in " + file1;
            case 2 -> "Edit " + file1 + ": replace '" + oldText.get(r.nextInt(oldText.size()))
                + "' with '" + newText.get(r.nextInt(newText.size())) + "'";
            case 3 -> "Read " + file1 + " and grep for '" + pattern + "'";
            case 4 -> "Write 'hello world' to " + file1 + " and then read it back";
            case 5 -> "Grep for '" + pattern + "' in " + file1 + ", then edit "
                + file2 + " to replace 'old' with 'new'";
            default -> "Read " + file1;
        };
    }

    // --- Unreliable tools mode ---

    private ChatResponse handleUnknownToolsCall(String userText, List<ToolSpecification> tools) {
        if (roll(skipToolProb)) {
            return textResponse("Mock answer (skipped tools): " + userText);
        }

        var neededTools = detectAllKnownTools(userText, tools);

        if (neededTools.isEmpty()) {
            return textResponse("Mock answer: " + userText);
        }

        if (roll(unknownToolProb)) {
            return toolCallResponse("unknown_tool_" + ThreadLocalRandom.current().nextInt(999), "{}");
        }

        var requests = new ArrayList<ToolExecutionRequest>();
        for (var toolName : neededTools) {
            var args = buildKnownArgs(toolName, userText);
            if (roll(badArgsProb)) {
                args.put("bad_param_" + ThreadLocalRandom.current().nextInt(99), "garbage");
            }
            requests.add(ToolExecutionRequest.builder()
                .name(toolName).arguments(toJson(args)).build());
        }

        // wrongToolProb: swap one random tool to a different available tool
        if (roll(wrongToolProb) && tools != null) {
            var others = tools.stream()
                .filter(t -> !neededTools.contains(t.name()))
                .map(ToolSpecification::name)
                .toList();
            if (!others.isEmpty()) {
                var idx = ThreadLocalRandom.current().nextInt(requests.size());
                requests.set(idx, ToolExecutionRequest.builder()
                    .name(others.get(ThreadLocalRandom.current().nextInt(others.size())))
                    .arguments(requests.get(idx).arguments())
                    .build());
            }
        }

        return ChatResponse.builder()
            .aiMessage(AiMessage.from(requests))
            .tokenUsage(new TokenUsage(10, 5))
            .finishReason(FinishReason.STOP)
            .build();
    }

    // --- Orchestrator Tool Calls ---

    private ChatResponse callRunTask(String taskPrompt, List<ToolSpecification> tools) {
        var args = new LinkedHashMap<String, Object>();
        args.put("taskPrompt", taskPrompt);
        return toolCallResponse("runTask", toJson(args));
    }

    private ChatResponse callVerifyAndLog(int iteration, String taskPrompt, String resultSummary,
                                           List<ToolSpecification> tools) {
        var args = new LinkedHashMap<String, Object>();
        args.put("iteration", iteration);
        args.put("taskPrompt", truncate(taskPrompt, 100));
        args.put("resultSummary", truncate(resultSummary, 200));
        return toolCallResponse("verifyAndLog", toJson(args));
    }

    // --- Structured output mode (mock TaskResult JSON) ---

    private ChatResponse structuredResponse(String userText) {
        var result = buildMockTaskResult(userText);
        return textResponse(toJson(result));
    }

    private ChatResponse structuredToolResponse(String userText, List<ToolSpecification> tools) {
        // When structured output is active but tools are also available:
        // First, call needed tools, then in handlePostToolCall return structured JSON
        var neededTools = detectAllKnownTools(userText, tools);
        if (!neededTools.isEmpty()) {
            var requests = new ArrayList<ToolExecutionRequest>();
            for (var toolName : neededTools) {
                var args = buildKnownArgs(toolName, userText);
                requests.add(ToolExecutionRequest.builder()
                    .name(toolName).arguments(toJson(args)).build());
            }
            return ChatResponse.builder()
                .aiMessage(AiMessage.from(requests))
                .tokenUsage(new TokenUsage(10, 5))
                .finishReason(FinishReason.STOP)
                .build();
        }
        return structuredResponse(userText);
    }

    private ChatResponse structuredPostToolCall(List<ChatMessage> messages) {
        var resultObj = new LinkedHashMap<String, Object>();
        var results = messages.stream()
            .filter(m -> m instanceof ToolExecutionResultMessage)
            .toList();
        var toolResultText = results.stream()
            .map(m -> ((ToolExecutionResultMessage) m).text())
            .collect(Collectors.joining(" | "));
        if (toolResultText.length() > 100) toolResultText = toolResultText.substring(0, 100) + "...";

        resultObj.put("taskName", "mock-task");
        resultObj.put("result", toolResultText.isEmpty() ? "done" : toolResultText);
        resultObj.put("toolCalls", results.size());
        resultObj.put("errors", 0);
        resultObj.put("timeouts", 0);
        resultObj.put("stopReason", "COMPLETED");
        resultObj.put("durationMs", 1_000L);
        return textResponse(toJson(resultObj));
    }

    private LinkedHashMap<String, Object> buildMockTaskResult(String userText) {
        var r = ThreadLocalRandom.current();
        var resultObj = new LinkedHashMap<String, Object>();
        resultObj.put("taskName", truncate(userText, 60));
        resultObj.put("result", "completed — see log file for details");
        resultObj.put("toolCalls", r.nextInt(3) + 1);
        resultObj.put("errors", r.nextDouble() < 0.2 ? 1 : 0);
        resultObj.put("timeouts", r.nextDouble() < 0.1 ? 1 : 0);
        resultObj.put("stopReason", "COMPLETED");
        resultObj.put("durationMs", r.nextLong(5000) + 500);
        return resultObj;
    }

    // --- Known tool detection (readFile, writeFile, editFile, grepFile) ---

    private List<String> detectAllKnownTools(String userText, List<ToolSpecification> tools) {
        boolean needsRead = userText.toLowerCase().contains("read");
        boolean needsWrite = userText.toLowerCase().contains("write");
        boolean needsEdit = EDIT_KEYWORDS.matcher(userText).find();
        boolean needsGrep = GREP_KEYWORDS.matcher(userText).find();
        boolean needsFile = needsRead || needsWrite || needsEdit || needsGrep
            || userText.toLowerCase().contains("/tmp");

        var available = tools.stream().map(ToolSpecification::name).toList();
        var result = new ArrayList<String>();

        if (needsRead && available.contains("readFile")) result.add("readFile");
        if (needsWrite && available.contains("writeFile")) result.add("writeFile");
        if (needsEdit && available.contains("editFile")) result.add("editFile");
        if (needsGrep && available.contains("grepFile")) result.add("grepFile");

        // Fallback: /tmp with no explicit keyword → add readFile
        if (result.isEmpty() && !needsRead && !needsWrite && !needsEdit && !needsGrep
            && userText.toLowerCase().contains("/tmp")
            && available.contains("readFile")) {
            result.add("readFile");
        }

        if (result.isEmpty() && !available.isEmpty()) {
            result.add(available.get(0));
        }

        return result;
    }

    private Map<String, Object> buildKnownArgs(String toolName, String userText) {
        var args = new LinkedHashMap<String, Object>();

        switch (toolName) {
            case "readFile" -> {
                var path = extractPath(userText, "workspace/data.txt");
            }
            case "writeFile" -> {
                var path = extractPath(userText, "workspace/out.txt");
                args.put("path", path);
                args.put("content", "mock content for " + path);
            }
            case "editFile" -> {
                var path = extractPath(userText, "workspace/data.txt");
                args.put("path", path);
                args.put("oldText", "old");
                args.put("newText", "new");
            }
            case "grepFile" -> {
                var path = extractPath(userText, "workspace/data.txt");
                args.put("path", path);
                args.put("pattern", extractPattern(userText, "TODO"));
            }
            case "stringLength" -> {
                args.put("text", userText.length() > 50 ? userText.substring(0, 50) : userText);
            }
        }
        return args;
    }

    private static String extractPath(String userText, String fallback) {
        var m = Pattern.compile("workspace/[\\w./-]+").matcher(userText);
        return m.find() ? m.group() : fallback;
    }

    private static String extractPattern(String userText, String fallback) {
        var m = Pattern.compile("['\"]([^'\"]+)['\"]").matcher(userText);
        return m.find() ? m.group(1) : fallback;
    }

    // --- Utilities ---

    private String extractUserText(List<ChatMessage> messages) {
        for (var m : messages) {
            if (m instanceof UserMessage um) return um.singleText();
        }
        return "";
    }

    private String findLastToolCallName(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof AiMessage ai) {
                var reqs = ai.toolExecutionRequests();
                if (reqs != null && !reqs.isEmpty()) {
                    return reqs.get(reqs.size() - 1).name();
                }
            }
        }
        return null;
    }

    private ToolExecutionResultMessage findLastToolResult(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof ToolExecutionResultMessage tr) {
                return tr;
            }
        }
        return null;
    }

    private ToolExecutionResultMessage findLastToolResultByTool(List<ChatMessage> messages, String toolName) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof ToolExecutionResultMessage tr
                && toolName.equals(tr.toolName())) {
                return tr;
            }
        }
        return null;
    }

    private int countToolResults(List<ChatMessage> messages, String toolName) {
        return (int) messages.stream()
            .filter(m -> m instanceof ToolExecutionResultMessage tr
                && toolName.equals(tr.toolName()))
            .count();
    }

    private int countToolCallsInHistory(List<ChatMessage> messages) {
        int count = 0;
        for (var m : messages) {
            if (m instanceof AiMessage ai && ai.hasToolExecutionRequests()) {
                count += ai.toolExecutionRequests().size();
            }
        }
        return count;
    }

    private int extractIterations(List<ChatMessage> messages) {
        for (var m : messages) {
            String text = null;
            if (m instanceof dev.langchain4j.data.message.SystemMessage sm) {
                text = sm.text();
            } else if (m instanceof UserMessage um) {
                text = um.singleText();
            }
            if (text != null) {
                var matcher = ITERATIONS_PATTERN.matcher(text);
                if (matcher.find()) {
                    return Integer.parseInt(matcher.group(1));
                }
            }
        }
        return 5;
    }

    private String toJson(Map<String, Object> args) {
        try { return JSON.writeValueAsString(args); }
        catch (JsonProcessingException e) { return "{}"; }
    }

    private ChatResponse toolCallResponse(String toolName, String argsJson) {
        return ChatResponse.builder()
            .aiMessage(AiMessage.from(ToolExecutionRequest.builder()
                .name(toolName).arguments(argsJson).build()))
            .tokenUsage(new TokenUsage(10, 5))
            .finishReason(FinishReason.STOP)
            .build();
    }

    private ChatResponse textResponse(String text) {
        return ChatResponse.builder()
            .aiMessage(AiMessage.from(text))
            .tokenUsage(new TokenUsage(10, text.length()))
            .finishReason(FinishReason.STOP)
            .build();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
