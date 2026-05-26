package de.augmentia.agenttest;

import de.augmentia.strandsagents.core.*;
import de.augmentia.strandsagents.core.agent.*;
import de.augmentia.strandsagents.core.config.ModelFactory;
import de.augmentia.strandsagents.core.tools.ListToolsTool;
import de.augmentia.strandsagents.core.logging.FileLlmLogger;
import de.augmentia.strandsagents.core.logging.LoggingChatModel;
import de.augmentia.strandsagents.core.model.event.*;
import dev.langchain4j.model.chat.ChatModel;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class Main {

    private static final Path OUTPUT_DIR = Path.of("test-output");
    private static final Path RUN_LOG = OUTPUT_DIR.resolve("run.log");
    private static final Path EVENTS_LOG = OUTPUT_DIR.resolve("orchestrator-events.log");
    private static final Path LLM_LOG = OUTPUT_DIR.resolve("orchestrator-llm.log");
    private static final Path GEN_MODULE = Path.of("generated-test");
    private static final Path GEN_SOURCE = GEN_MODULE.resolve(
        "src/main/java/de/augmentia/generated/GenTest.java");
    private static final Path TESTS_DIR = Path.of("tests");

    private static PrintWriter logWriter;
    private static PrintWriter eventWriter;
    private static final DateTimeFormatter DTF =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    private static final String DEFINE_PROMPT_1A = """
You are a creative test scenario designer for Strands AI Agents.
Generate a unique test scenario. Each iteration must test different agent capabilities.

IMPORTANT: First call `list_tools` to discover what tools are available.
Then design a scenario that uses ONLY tools from that list.
Each workflow step must use a tool that actually exists.
DO NOT use tools that are not in the available list.
Always design workflows with 2-3 steps.

Output VALID JSON only (no markdown, no explanation) with this schema:
{
  "name": "short scenario name",
  "description": "what the agent should do",
  "systemPrompt": "system prompt for the agent under test, mentioning the specific tools to use",
  "testPrompt": "the user message to execute",
  "workflow": [
    {"step": 1, "action": "describe the step", "tool": "tool_name"},
    {"step": 2, "action": "describe the step", "tool": "tool_name"},
    {"step": 3, "action": "describe the step", "tool": "tool_name"}
  ],
  "tools": {
    "include": ["tool1", "tool2"],
    "exclude": []
  },
  "asserts": {
    "finalAnswerNotNull": true,
    "expectedOutputContains": "... or null"
  }
}

Be creative with the scenario description, systemPrompt, and testPrompt.
Model comes from environment variable OPENAI_API_KEY — no model config needed.
The workflow must be fully executable with ONLY the listed include tools.
Use includes to select only the tools needed for the workflow steps.
""";

    private static final String DEFINE_PROMPT_1B = """
You are a JSON Schema generator for Strands AI Agent test scenarios.
Given a workflow config with steps, generate a valid JSON Schema for each step's output.

Each schema defines what structured data the agent should produce after executing that step.
The schema must be a valid JSON Schema (draft-2020-12) object describing the expected output fields.
Make the schema meaningful — use field names and types that match the step's action and tool.

Output VALID JSON only (no markdown, no explanation) with this schema:
{
  "stepSchemas": {
    "1": {"type": "object", "properties": {"result": {"type": "string"}}, "required": ["result"]},
    "2": {"type": "object", "properties": {"url": {"type": "string"}, "content": {"type": "string"}}, "required": ["url", "content"]}
  }
}
""";

    private static final String GENERATE_PROMPT = """
You are a Java code generator for Strands Agents.
Generate a complete class `GenTest` in package `de.augmentia.generated`.

The class must have a main method that:
1. Creates a ChatModel via ModelFactory.createOpenAiFromEnv()
2. Builds a ToolRegistry from `.standard()` filtered by `tools.include`/`tools.exclude` from config
3. Creates an Agent(model, registry, new ToolExecutor())
4. For each workflow step: sets StructuredOutputConfig via dynamicSchema() with that step's schema from StepSchemas
5. Sets systemPrompt on the agent per workflow step
6. Calls agent.execute() once per workflow step, passing previous results into the next call
7. Collects all step results and prints final JSON {"step1":"...","step2":"...","stopReason":"...","toolCalls":N}

The config has field "workflow" (array of steps) and StepSchemas has "stepSchemas" (object keyed by step number).
Match the number of steps in the workflow — generate exactly that many execute() calls.

OUTPUT ONLY THE JAVA CODE. No markdown, no backticks, no explanation.
Use these imports:
  import de.augmentia.strandsagents.core.*;
  import de.augmentia.strandsagents.core.agent.*;
  import de.augmentia.strandsagents.core.config.*;
  import de.augmentia.strandsagents.core.structured.StructuredOutputConfig;
  import dev.langchain4j.model.chat.ChatModel;
  import java.util.LinkedHashMap;
  import com.fasterxml.jackson.databind.ObjectMapper;

Template for ToolRegistry:
  // if include present: ToolRegistry.builder().standard().include("tool1","tool2").build()
  // default: ToolRegistry.builder().standard().build()

Template (adapt number of steps to match the workflow):
  ChatModel model = ModelFactory.createOpenAiFromEnv();
  var registry = ToolRegistry.builder().standard().build();
  var agent = new Agent(model, registry, new ToolExecutor());

  // Step 1 — use step's schema from StepSchemas
  agent.setStructuredOutputConfig(StructuredOutputConfig.dynamicSchema("{\\"type\\":\\"object\\",\\"properties\\":{\\"result\\":{\\"type\\":\\"string\\"}},\\"required\\":[\\"result\\"]}"));
  agent.setSystemPrompt("Step 1 system prompt");
  var step1 = agent.execute("Step 1 user message");

  // Step 2 — use step's schema from StepSchemas
  agent.setStructuredOutputConfig(StructuredOutputConfig.dynamicSchema("{\\"type\\":\\"object\\",\\"properties\\":{\\"url\\":{\\"type\\":\\"string\\"}},\\"required\\":[\\"url\\"]}"));
  agent.setSystemPrompt("Step 2 system prompt");
  var step2 = agent.execute("Step 2 user message based on: " + step1.finalAnswer());

  // ... repeat for each workflow step with its schema ...

  // Collect results — one put() per step
  var out = new LinkedHashMap<String, Object>();
  out.put("step1", step1.finalAnswer());
  out.put("step2", step2.finalAnswer());
  out.put("stopReason", step2.stopReason().name());
  out.put("toolCalls", (step1.metrics() != null ? step1.metrics().toolCallsCount() : 0)
      + (step2.metrics() != null ? step2.metrics().toolCallsCount() : 0));
  new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(System.out, out);
""";

    private static final String VALIDATE_PROMPT = """
You are a test result validator.
Analyze the test execution below and produce a structured validation report.

=== Config ===
{config}

=== Exit Code ===
{exitCode}

=== Execution Log ===
{log}

Check:
1. Does the log contain a valid JSON result from the generated test?
2. Does the result match the asserts in the config?
3. If compilation error: identify what went wrong in the generated code
4. If runtime error: analyze the failure and suggest a fix

Output VALID JSON only:
{
  "passed": true/false,
  "stopReason": "COMPLETED or ERROR or COMPILE_ERROR",
  "toolCalls": N,
  "errors": [
    {"type": "compile|runtime|assert", "message": "...", "suggestion": "..."}
  ],
  "summary": "Kurze Zusammenfassung auf Deutsch"
}
""";

    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUTPUT_DIR);
        logWriter = new PrintWriter(new FileWriter(RUN_LOG.toFile(), true), true);
        eventWriter = new PrintWriter(new FileWriter(EVENTS_LOG.toFile(), true), true);
        Runtime.getRuntime().addShutdownHook(new Thread(Main::closeLogs));

        log("=== Strands Agent Test Generator gestartet ===");

        var apiKey = System.getenv("OPENAI_API_KEY");
        ChatModel rawModel;
        if (apiKey != null && !apiKey.isBlank()) {
            rawModel = ModelFactory.createOpenAiFromEnv();
            log("Modus: OpenAI (Modell=" + System.getenv().getOrDefault("OPENAI_MODEL", "default") + ")");
        } else {
            rawModel = new MockChatModel();
            log("Modus: Mock (OPENAI_API_KEY nicht gesetzt)");
        }

        var llmLogger = new FileLlmLogger(LLM_LOG);
        var model = new LoggingChatModel(rawModel, llmLogger);
        log("LLM-Log: " + LLM_LOG.toAbsolutePath());

        var registry = ToolRegistry.builder().standard().build();
        registry.register(new ListToolsTool(registry));
        var orchestrator = new Agent(model, registry, new ToolExecutor());
        log("Tools: " + registry.getToolNames());
        orchestrator.setEventListener(event -> {
            switch (event) {
                case ModelRequestedEvent e ->
                    event("LLM-REQUEST", e.promptHistory().size() + " Nachrichten");
                case ToolExecutionStartedEvent e ->
                    event("TOOL-CALL", e.toolCall().toolName() + "(" + truncate(e.toolCall().arguments(), 120) + ")");
                case ToolExecutionFinishedEvent e ->
                    event("TOOL-RESULT", e.result().toolName() + " → " + truncate(e.result().result(), 200));
                case AgentStartedEvent e ->
                    event("AGENT-START", "");
                case AgentFinishedEvent e ->
                    event("AGENT-ENDE", "finalAnswer=" + truncate(e.finalAnswer(), 80));
                case AgentStateChangedEvent e ->
                    event("PHASE", e.previousPhase() + " → " + e.currentPhase());
                default -> {}
            }
        });

        log("Events: " + EVENTS_LOG.toAbsolutePath());
        log("");

        var iteration = 0;
        while (true) {
            iteration++;
            log("─".repeat(60));
            log("ITERATION " + iteration);
            log("─".repeat(60));

            // ─── Phase 1a: Define Workflow ──────────────────────────────
            log("[Phase 1a/5] Workflow definieren");
            orchestrator.setSystemPrompt(DEFINE_PROMPT_1A);
            var t1a = System.nanoTime();
            var configJson = orchestrator.execute(
                "Create a unique Strands agent test scenario. Iteration " + iteration + ".").finalAnswer();
            log("[Phase 1a/5] Fertig (" + (System.nanoTime() - t1a) / 1_000_000 + "ms)");
            log("  Config: " + configJson);

            // ─── Phase 1b: Generate Schemas ──────────────────────────────
            log("[Phase 1b/5] Step-Schemas generieren");
            orchestrator.setSystemPrompt(DEFINE_PROMPT_1B);
            var t1b = System.nanoTime();
            var schemasJson = orchestrator.execute(
                "Generate JSON Schemas for this config:\n" + configJson).finalAnswer();
            log("[Phase 1b/5] Fertig (" + (System.nanoTime() - t1b) / 1_000_000 + "ms)");
            log("  Schemas: " + schemasJson);

            // ─── Phase 2: Generate Code ──────────────────────────────────
            log("[Phase 2/5] Java-Code generieren");
            orchestrator.setSystemPrompt(GENERATE_PROMPT);
            var t2 = System.nanoTime();
            var generatedCode = orchestrator.execute(
                "Generate GenTest.java for this config:\n" + configJson + "\n\nStepSchemas:\n" + schemasJson).finalAnswer();
            log("[Phase 2/5] Fertig (" + (System.nanoTime() - t2) / 1_000_000 + "ms)");
            var cleanCode = extractCode(generatedCode);
            Files.writeString(GEN_SOURCE, cleanCode);
            log("  Generated: " + cleanCode.length() + " Bytes → " + GEN_SOURCE);

            // ─── Phase 3: Build & Run ────────────────────────────────────
            log("[Phase 3/5] Build & Run");
            var t3 = System.nanoTime();
            var buildResult = buildAndRun(iteration);
            var d3 = (System.nanoTime() - t3) / 1_000_000;
            log("[Phase 3/5] Fertig (" + d3 + "ms)");
            log("  Exit Code: " + buildResult.exitCode());
            log("  Log-File: test-output/test-run-" + iteration + ".log");
            log("  ── Build Output ──");
            var buildOut = buildResult.log();
            if (buildOut.length() > 2500) {
                logWriter.println(buildOut.substring(0, 2500));
                logWriter.println("  ... (" + buildOut.length() + " Bytes total, truncated)");
            } else {
                logWriter.println(buildOut);
            }
            logWriter.flush();
            log("  ── End Build Output ──");

            // ─── Phase 4: Validate ───────────────────────────────────────
            log("[Phase 4/5] Validieren");
            var validatePrompt = VALIDATE_PROMPT
                .replace("{config}", configJson)
                .replace("{exitCode}", String.valueOf(buildResult.exitCode()))
                .replace("{log}", buildOut.length() > 3500
                    ? buildOut.substring(0, 3500) + "\n... (truncated)"
                    : buildOut);
            orchestrator.setSystemPrompt(validatePrompt);
            var t4 = System.nanoTime();
            var validationJson = orchestrator.execute(
                "Validate the test execution result.").finalAnswer();
            log("[Phase 4/5] Fertig (" + (System.nanoTime() - t4) / 1_000_000 + "ms)");
            var reportFile = OUTPUT_DIR.resolve("validation-" + iteration + ".json");
            Files.writeString(reportFile, validationJson);
            log("  Validation: " + validationJson);

            // ─── Archivierung ──────────────────────────────────────────
            Files.createDirectories(TESTS_DIR);
            var targetName = "test-" + iteration + ".java";
            var targetFile = TESTS_DIR.resolve(targetName);
            Files.copy(GEN_SOURCE, targetFile, StandardCopyOption.REPLACE_EXISTING);
            log("  Archived: " + targetFile);

            log("");
        }
    }

    private static void log(String msg) {
        var line = DTF.format(Instant.now()) + " " + msg;
        System.out.println(line);
        logWriter.println(line);
    }

    private static void event(String type, String details) {
        var line = DTF.format(Instant.now()) + " [" + type + "] " + details;
        eventWriter.println(line);
    }

    private static void closeLogs() {
        logWriter.close();
        eventWriter.close();
    }

    private static String extractCode(String raw) {
        var lines = raw.split("\n", -1);
        var code = new StringBuilder();
        boolean inCode = false;
        for (var line : lines) {
            if (line.trim().startsWith("```")) {
                inCode = !inCode;
                continue;
            }
            if (inCode) {
                code.append(line).append("\n");
            }
        }
        if (code.length() > 0) return code.toString().strip();
        return raw.strip();
    }

    private static BuildResult buildAndRun(int iteration) throws Exception {
        var pb = new ProcessBuilder(
            "mvn", "-f", "pom.xml",
            "-Dmaven.test.skip=true",
            "compile", "exec:exec"
        );
        pb.directory(GEN_MODULE.toFile());
        pb.redirectErrorStream(true);
        var process = pb.start();
        var log = new StringBuilder();
        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.append(line).append("\n");
            }
        }
        var finished = process.waitFor(120, TimeUnit.SECONDS);
        var exitCode = finished ? process.exitValue() : -1;
        var logFile = OUTPUT_DIR.resolve("test-run-" + iteration + ".log");
        Files.writeString(logFile, log.toString());
        return new BuildResult(exitCode, log.toString());
    }

    private static String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "…" : s;
    }

    private record BuildResult(int exitCode, String log) {}
}
