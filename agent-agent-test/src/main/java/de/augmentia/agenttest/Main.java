package de.augmentia.agenttest;

import de.augmentia.strandsagents.core.*;
import de.augmentia.strandsagents.core.agent.*;
import de.augmentia.strandsagents.core.config.ModelFactory;
import de.augmentia.strandsagents.core.tools.ListToolsTool;
import de.augmentia.strandsagents.core.logging.FileLlmLogger;
import de.augmentia.strandsagents.core.logging.LoggingChatModel;
import de.augmentia.strandsagents.core.model.event.*;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.mcp.client.DefaultMcpClient;
  import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import java.io.*;
import java.net.URI;
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
    {"step": 1, "action": "describe the step", "tool": "mcp_localhost_8099_tool_name"},
    {"step": 2, "action": "describe the step", "tool": "mcp_localhost_8099_tool_name"},
    {"step": 3, "action": "describe the step", "tool": "mcp_localhost_8099_tool_name"}
  ],
  "tools": {
    "include": ["mcp_localhost_8099_tool1", "mcp_localhost_8099_tool2"],
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
2. Builds a ToolRegistry (connect to MCP server, NO .standard()) with tools filtered by `tools.include`/`tools.exclude` from config
3. Creates an Agent(model, registry, new ToolExecutor())
4. For each workflow step: sets StructuredOutputConfig via dynamicSchema() with that step's schema from StepSchemas
5. Sets systemPrompt on the agent per workflow step, set correct tools defined by `tools.include`/`tools.exclude` from config, starting with mcp_localhost_8099_tool_
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
  import de.augmentia.strandsagents.core.tools.McpToolMethod;
  import dev.langchain4j.agent.tool.ToolSpecification;
  import dev.langchain4j.model.chat.ChatModel;
  import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
  import java.util.LinkedHashMap;
  import java.util.Set;
  import java.nio.file.Path;
  import com.fasterxml.jackson.databind.ObjectMapper;

Template for ToolRegistry (connect to MCP server, NO .standard()):
  var mcpUrl = System.getenv("MCP_SERVER_URL");
  var transport = StreamableHttpMcpTransport.builder().url(mcpUrl).logRequests(false).logResponses(false).build();
  var mcpClient = DefaultMcpClient.builder().transport(transport).build();
    var prefix = "mcp_localhost_8099_";
    var registry = new ToolRegistry();
    var selectedTools = Set.of(
        "mcp_localhost_8099_write",
        "mcp_localhost_8099_ls",
        "mcp_localhost_8099_grep"
    );
    for (String tools: selectedTools) {
        for (ToolSpecification spec : mcpClient.listTools()) {
            if ((prefix+ spec.name()).equals(tools))  {
                registry.register(spec.name(), spec, new McpToolMethod(mcpClient, mcpUrl, spec.name(), spec));
            }
        }
    }

Template (adapt number of steps to match the workflow):
  ChatModel model = ModelFactory.createOpenAiFromEnv();
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

Rules for determining error type:
- COMPILE_ERROR only if the log contains "BUILD FAILURE" or compilation error messages like "cannot find symbol".
- If the log contains "BUILD SUCCESS", it is NOT a compile error — check for runtime issues instead.
- RUNTIME_ERROR if the log shows exceptions, AccessDeniedException, tool failures, or the agent produced empty/invalid results at runtime.
- ASSERT_ERROR if the test compiled and ran but the output does not match the asserts in the config.
- If exit code is 0 and the log ends with "BUILD SUCCESS" and the JSON output contains expected values, set passed=true.

Check:
1. First determine: did compilation succeed? (look for "BUILD SUCCESS" vs "BUILD FAILURE")
2. Does the log contain a valid JSON result (starting with {"step...) from the generated test?
3. Does the result match the asserts in the config?
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

    private static final String RECOVERY_PROMPT = """
You are a Java code fixer for Strands Agents.
The generated test failed. Fix the code below.

=== Previous Code ===
{code}

=== Validation Error ===
{error}

=== Build Output ===
{log}

Common fixes:
- Ensure imports are correct: de.augmentia.strandsagents.core.*, core.agent.*, core.config.*
- Use ToolRegistry.builder().standard().include(...).build() for the registry
- agent.execute() returns AgentResult (record) - use result.finalAnswer(), result.stopReason(), result.metrics().toolCallsCount()
- StructuredOutputConfig.dynamicSchema(...) for step schemas
- Keep the same package and class name (GenTest in de.augmentia.generated)

OUTPUT ONLY THE FIXED JAVA CODE. No markdown, no backticks, no explanation.
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

        var mcpRegistry = new ToolRegistry();

        var mcpUrl = System.getenv("MCP_SERVER_URL");
        if (mcpUrl != null && !mcpUrl.isBlank()) {
            try {
                var transport = StreamableHttpMcpTransport.builder()
                    .url(mcpUrl).logRequests(false).logResponses(false).build();
                var mcpClient = DefaultMcpClient.builder().transport(transport).build();
                var prefix = mcpPrefix(mcpUrl);
                try {
                    for (var spec : mcpClient.listTools()) {
                        var prefixedName = prefix + "_" + spec.name();
                        var prefixedSpec = ToolSpecification.builder()
                            .name(prefixedName)
                            .description(spec.description())
                            .parameters(spec.parameters())
                            .build();
                        mcpRegistry.register(prefixedName, prefixedSpec,
                            new ToolRegistry.ToolMethod() {
                                public ToolSpecification spec() { return prefixedSpec; }
                                public String execute(String json) { return "MCP-only"; }
                            });
                    }
                } finally {
                    mcpClient.close();
                }
            } catch (Exception e) {
                log("MCP-Fehler: " + e.getMessage());
            }
        }

        var execRegistry = ToolRegistry.builder().standard().cwd(Path.of("").toAbsolutePath()).build();
        execRegistry.register(new ListToolsTool(mcpRegistry));

        log("Tools: " + execRegistry.getToolNames());
        log("MCP Tools: " + mcpRegistry.getToolNames());


        var orchestrator = new Agent(model, execRegistry, new ToolExecutor());
        log("Tools: " + execRegistry.getToolNames());
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

            // ─── Retry-Schleife: Generate/Recovery + Build + Validate ──
            String cleanCode = null;
            int maxAttempts = 3;
            boolean passed = false;
            String validationJson = "";
            String buildOut = "";

            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                if (attempt == 1) {
                    // ─── Phase 2: Generate Code ──────────────────────────────
                    log("[Phase 2/5] Java-Code generieren");
                    orchestrator.setSystemPrompt(GENERATE_PROMPT);
                    var t2 = System.nanoTime();
                    var generatedCode = orchestrator.execute(
                        "Generate GenTest.java for this config:\n" + configJson + "\n\nStepSchemas:\n" + schemasJson).finalAnswer();
                    log("[Phase 2/5] Fertig (" + (System.nanoTime() - t2) / 1_000_000 + "ms)");
                    cleanCode = extractCode(generatedCode);
                } else {
                    // ─── Phase R: Recovery Agent (frische Instanz) ───────────
                    log("[Phase R/5] Recovery-Versuch " + attempt + "/" + maxAttempts);
                    var tRec = System.nanoTime();
                    var recoveryPrompt = RECOVERY_PROMPT
                        .replace("{code}", cleanCode != null ? cleanCode : "(no code)")
                        .replace("{error}", validationJson)
                        .replace("{log}", buildOut.length() > 4000
                            ? buildOut.substring(0, 4000) + "\n... (truncated)"
                            : buildOut);
                    var recoveryAgent = new Agent(
                        rawModel, execRegistry, new ToolExecutor());
                    recoveryAgent.setSystemPrompt(recoveryPrompt);
                    var fixedCode = recoveryAgent.execute(
                        "Fix the generated Java test code. Previous errors: " + validationJson).finalAnswer();
                    log("[Phase R/5] Fertig (" + (System.nanoTime() - tRec) / 1_000_000 + "ms)");
                    cleanCode = extractCode(fixedCode);
                }

                Files.writeString(GEN_SOURCE, cleanCode);
                log("  Generated: " + cleanCode.length() + " Bytes → " + GEN_SOURCE);

                // ─── Phase 3: Build & Run ────────────────────────────────────
                log("[Phase 3/5] Build & Run (Versuch " + attempt + ")");
                var t3 = System.nanoTime();
                var buildResult = buildAndRun(iteration, attempt);
                var d3 = (System.nanoTime() - t3) / 1_000_000;
                log("[Phase 3/5] Fertig (" + d3 + "ms)");
                log("  Exit Code: " + buildResult.exitCode());
                log("  Log-File: test-output/test-run-" + iteration + "-" + attempt + ".log");
                log("  ── Build Output ──");
                buildOut = buildResult.log();
                if (buildOut.length() > 2500) {
                    logWriter.println(buildOut.substring(0, 2500));
                    logWriter.println("  ... (" + buildOut.length() + " Bytes total, truncated)");
                } else {
                    logWriter.println(buildOut);
                }
                logWriter.flush();
                log("  ── End Build Output ──");

                // ─── Phase 4: Validate ───────────────────────────────────────
                log("[Phase 4/5] Validieren (Versuch " + attempt + ")");
                var validatePrompt = VALIDATE_PROMPT
                    .replace("{config}", configJson)
                    .replace("{exitCode}", String.valueOf(buildResult.exitCode()))
                    .replace("{log}", buildOut.length() > 3500
                        ? buildOut.substring(0, 3500) + "\n... (truncated)"
                        : buildOut);
                orchestrator.setSystemPrompt(validatePrompt);
                var t4 = System.nanoTime();
                validationJson = orchestrator.execute(
                    "Validate the test execution result.").finalAnswer();
                log("[Phase 4/5] Fertig (" + (System.nanoTime() - t4) / 1_000_000 + "ms)");
                var reportFile = OUTPUT_DIR.resolve("validation-" + iteration + "-" + attempt + ".json");
                Files.writeString(reportFile, validationJson);
                log("  Validation: " + validationJson);

                if (isPassed(validationJson)) {
                    passed = true;
                    log("[OK] Test bestanden nach " + attempt + " Versuch(en)");
                    break;
                }
                log("[Retry] Versuch " + attempt + " fehlgeschlagen, nächster Versuch...");
            }

            // ─── Archivierung ──────────────────────────────────────────
            Files.createDirectories(TESTS_DIR);
            var targetName = "test-" + iteration + ".java";
            var targetFile = TESTS_DIR.resolve(targetName);
            Files.copy(GEN_SOURCE, targetFile, StandardCopyOption.REPLACE_EXISTING);
            log("  Archived: " + targetFile + " (passed=" + passed + ")");

            log("");

            if (iteration > 3) break;
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

    private static boolean isPassed(String validationJson) {
        return validationJson.contains("\"passed\": true") || validationJson.contains("\"passed\":true");
    }

    private static BuildResult buildAndRun(int iteration, int attempt) throws Exception {
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
        var logFile = OUTPUT_DIR.resolve("test-run-" + iteration + "-" + attempt + ".log");
        Files.writeString(logFile, log.toString());
        return new BuildResult(exitCode, log.toString());
    }

    private static String mcpPrefix(String mcpUrl) {
        try {
            var uri = new URI(mcpUrl);
            var host = uri.getHost();
            var port = uri.getPort();
            return "mcp_" + (host != null ? host : "unknown") + (port > 0 ? "_" + port : "");
        } catch (Exception e) {
            return "mcp_" + Math.abs(mcpUrl.hashCode()) % 10000;
        }
    }

    private static String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "…" : s;
    }

    private record BuildResult(int exitCode, String log) {}
}
