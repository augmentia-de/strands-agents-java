package de.augmentia.strandsagents.examples.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.augmentia.strandsagents.core.ToolExecutor;
import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.agent.Agent;
import de.augmentia.strandsagents.core.resilience.ResilienceConfig;
import de.augmentia.strandsagents.core.resilience.RetryConfig;
import de.augmentia.strandsagents.core.structured.StructuredOutputConfig;
import de.augmentia.strandsagents.examples.tools.ChaosMonkeyHook;
import de.augmentia.strandsagents.examples.tools.TraceRecorder;
import de.augmentia.strandsagents.examples.tools.UnreliableCalculatorTool;
import de.augmentia.strandsagents.examples.tools.UnreliableReadFileTool;
import de.augmentia.strandsagents.examples.tools.UnreliableWeatherTool;
import de.augmentia.strandsagents.examples.tools.UnreliableWriteFileTool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.chat.ChatModel;

public class RunTaskTool {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ChatModel model;
    private final UnreliableCalculatorTool calculator = new UnreliableCalculatorTool();
    private final UnreliableWeatherTool weather = new UnreliableWeatherTool();
    private final UnreliableReadFileTool readFile = new UnreliableReadFileTool();
    private final UnreliableWriteFileTool writeFile = new UnreliableWriteFileTool();

    public RunTaskTool(ChatModel model) {
        this.model = model;
    }

    @Tool("Runs a task against the unreliable tools and returns a summary of the result.")
    public String runTask(@P("taskPrompt") String taskPrompt) {
        var registry = ToolRegistry.builder()
            .with(calculator)
            .with(weather)
            .with(readFile)
            .with(writeFile)
            .build();

        var executor = new ToolExecutor(8);
        var resilience = new ResilienceConfig(new RetryConfig(3, 500, 2.0), null);

        var saved = setMaxToolIterations(4);
        var agent = new Agent(model, registry, executor, null, null, resilience);
        setMaxToolIterations(saved);

        // Enable structured output: sub-agent returns TaskResult JSON
        agent.setStructuredOutputConfig(
            StructuredOutputConfig.staticModel(TaskResult.class,
                "Return the result as a TaskResult JSON object with fields: taskName, result, toolCalls, errors, timeouts, stopReason, durationMs"));

        var trace = new TraceRecorder();
        agent.setEventListener(trace);
        agent.addHook(trace);
        agent.addHook(ChaosMonkeyHook.moderate());

        try {
            var result = agent.execute(taskPrompt);

            var structured = result.structuredOutput();
            var tr = parseTaskResult(structured);

            var answer = tr != null
                ? "{\"taskName\":\"" + esc(tr.taskName()) + "\",\"result\":\"" + esc(tr.result()) + "\"}"
                : result.finalAnswer();
            if (answer.length() > 200) answer = answer.substring(0, 200) + "...";

            var names = String.join(", ", registry.getToolNames());
            return formatResult(taskPrompt, answer, result, trace, names, tr);
        } catch (Exception e) {
            var names = String.join(", ", registry.getToolNames());
            return "Task: " + taskPrompt + "\n"
                + "Answer: ERROR — " + e.getMessage() + "\n"
                + "StopReason: ERROR\n"
                + "ToolCalls: " + trace.getToolCallCount() + "\n"
                + "Errors: " + trace.getToolErrorCount() + "\n"
                + "Timeouts: " + trace.getToolTimeoutCount() + "\n"
                + "Duration: -\n"
                + "Tools: " + names;
        }
    }

    private static TaskResult parseTaskResult(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return JSON.readValue(json, TaskResult.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static int setMaxToolIterations(int n) {
        try {
            var f = Agent.class.getDeclaredField("MAX_TOOL_ITERATIONS");
            f.setAccessible(true);
            var old = f.getInt(null);
            f.setInt(null, n);
            return old;
        } catch (Exception ignored) {
            return -1;
        }
    }

    private String formatResult(String task, String answer,
                                 de.augmentia.strandsagents.core.model.agent.AgentResult result,
                                 TraceRecorder trace, String toolNames, TaskResult tr) {
        var seoScore = tr != null && tr.result() != null && tr.result().contains("seo_score")
            ? extractField(tr.result(), "seo_score") : null;
        var structuredInfo = tr != null
            ? "\nStructured: OK"
            + (seoScore != null ? " | seo_score=" + seoScore : "")
            + "\nStructErrors: " + trace.getStructuredErrorCount()
            + "\nStructOk: " + trace.getStructuredOkCount()
            : "\nStructured: (none)";
        return "Task: " + task + "\n"
            + "Answer: " + answer + "\n"
            + "StopReason: " + result.stopReason() + "\n"
            + "ToolCalls: " + result.metrics().toolCallsCount() + "\n"
            + "Errors: " + trace.getToolErrorCount() + "\n"
            + "Timeouts: " + trace.getToolTimeoutCount() + "\n"
            + "Duration: " + result.metrics().durationMs() + "ms\n"
            + "Tools: " + toolNames
            + structuredInfo;
    }

    private static String extractField(String text, String field) {
        var prefix = field + ":";
        var idx = text.indexOf(prefix);
        if (idx < 0) return null;
        var start = idx + prefix.length();
        var end = text.indexOf(',', start);
        if (end < 0) end = text.indexOf('}', start);
        return end > start ? text.substring(start, end).trim() : text.substring(start).trim();
    }
}
