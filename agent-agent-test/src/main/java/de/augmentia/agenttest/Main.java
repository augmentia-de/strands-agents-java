package de.augmentia.agenttest;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.augmentia.strandsagents.core.ToolExecutor;
import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.agent.Agent;
import de.augmentia.strandsagents.core.agent.MockChatModel;
import de.augmentia.strandsagents.core.config.ModelFactory;
import de.augmentia.strandsagents.core.logging.FileLlmLogger;
import de.augmentia.strandsagents.core.logging.LoggingChatModel;
import de.augmentia.strandsagents.core.model.event.*;
import de.augmentia.strandsagents.core.tools.ListToolsTool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.model.chat.ChatModel;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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

Available MCP tools:
{available_tools}

Design a scenario that uses ONLY tools from this list.
Each workflow step must use a tool that actually exists.
DO NOT invent tools that are not in the list.
Design 1-3 steps depending on the task complexity.

IMPORTANT: Set expectedOutputContains to null unless you are 100% sure what the agent will output.
You can call list_tools to see tool descriptions.

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

    private static final int MAX_RETRIES = 3;

    private static final String RECOVERY_PROMPT = """
You are a test scenario optimizer for Strands AI Agents.
The generated test failed during validation. Optimize the scenario so it passes.

Available MCP tools:
{available_tools}

=== Previous Config ===
{config}

=== Previous Schemas ===
{schemas}

=== Error Type ===
{errorType}

=== Error Message ===
{errorMsg}

=== Build Output ===
{log}

Check the error type from the validation above and respond accordingly:
- **assert error**: Fix systemPrompt/testPrompt to steer the agent toward the expected output
- **runtime error**: Fix tool selection or step structure — tools must exist
- **compile error**: Fix config structure, step counts, or tool names

You can call list_tools to explore available tool descriptions.

Output a SINGLE valid JSON object (no markdown, no backticks) with this structure:
{
  "config": { ... optimized WorkflowConfig ... },
  "schemas": { "stepSchemas": { ... optimized StepSchemas ... } }
}
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

First, find the GenTest JSON output in the log. It starts with {"step1" or {"step" and ends with }.
Extract it and compare against the config's asserts.

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

Output VALID JSON only (no markdown, no backticks):
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

        var toolList = String.join(", ", mcpRegistry.getToolNames());
        if (toolList.isEmpty()) {
            toolList = "(no MCP tools available – run start.sh with MCP_SERVER_URL)";
        }

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
        var mapper = new ObjectMapper();
        while (true) {
            iteration++;
            log("─".repeat(60));
            log("ITERATION " + iteration);
            log("─".repeat(60));

            // ─── Phase 1a: Define Workflow ──────────────────────────────
            log("[Phase 1a/5] Workflow definieren");
            orchestrator.getChatMemory().clear();
            orchestrator.setSystemPrompt(DEFINE_PROMPT_1A.replace("{available_tools}", toolList));
            var t1a = System.nanoTime();
            var configJson = orchestrator.execute(
                "Create a unique Strands agent test scenario. Iteration " + iteration + ".").finalAnswer();
            log("[Phase 1a/5] Fertig (" + (System.nanoTime() - t1a) / 1_000_000 + "ms)");
            log("  Config: " + configJson);

            // ─── Phase 1b: Generate Schemas ──────────────────────────────
            log("[Phase 1b/5] Step-Schemas generieren");
            orchestrator.getChatMemory().clear();
            orchestrator.setSystemPrompt(DEFINE_PROMPT_1B);
            var t1b = System.nanoTime();
            var schemasJson = orchestrator.execute(
                "Generate JSON Schemas for this config:\n" + configJson).finalAnswer();
            log("[Phase 1b/5] Fertig (" + (System.nanoTime() - t1b) / 1_000_000 + "ms)");
            log("  Schemas: " + schemasJson);

            String validationJson = "";
            String buildOut = "";
            boolean passed = false;

            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                if (attempt > 1) {
                    // ─── Phase R: Recovery Agent (frische Instanz) ───────
                    log("[Phase R/5] Recovery-Versuch " + attempt + "/" + MAX_RETRIES);
                    var tRec = System.nanoTime();
                    var errorType = extractErrorType(validationJson);
                    var errorMsg = extractErrorMessage(validationJson);
                    var recoveryPrompt = RECOVERY_PROMPT
                        .replace("{available_tools}", toolList)
                        .replace("{config}", configJson)
                        .replace("{schemas}", schemasJson)
                        .replace("{errorType}", errorType)
                        .replace("{errorMsg}", errorMsg)
                        .replace("{log}", buildOut.length() > 4000
                            ? buildOut.substring(0, 4000) + "\n... (truncated)"
                            : buildOut);
                    var recoveryAgent = new Agent(rawModel, execRegistry, new ToolExecutor());
                    recoveryAgent.setSystemPrompt(recoveryPrompt);
                    var recoveryResult = recoveryAgent.execute(
                        "Fix the test scenario. Previous validation: " + validationJson).finalAnswer();
                    log("[Phase R/5] Fertig (" + (System.nanoTime() - tRec) / 1_000_000 + "ms)");
                    var parsed = parseRecoveryResult(recoveryResult);
                    configJson = parsed.config();
                    schemasJson = parsed.schemas();
                    log("  Config: " + configJson);
                    log("  Schemas: " + schemasJson);
                }

                // ─── Phase 2: Code generieren (Template-basiert) ──────
                log("[Phase 2/5] Code generieren (Template, Versuch " + attempt + ")");
                var t2 = System.nanoTime();
                String cleanCode;
                try {
                    var workConfig = mapper.readValue(configJson, WorkflowConfig.class);
                    var stepSchemas = mapper.readValue(schemasJson, StepSchemas.class);
                    cleanCode = CodeAssembler.assemble(workConfig, stepSchemas);
                } catch (Exception e) {
                    log("  Phase 2 fehlgeschlagen: " + e.getMessage());
                    log("[Retry] Versuch " + attempt + " fehlgeschlagen, n\u00e4chster Versuch...");
                    continue;
                }
                log("[Phase 2/5] Fertig (" + (System.nanoTime() - t2) / 1_000_000 + "ms)");
                Files.writeString(GEN_SOURCE, cleanCode);
                log("  Generated: " + cleanCode.length() + " Bytes \u2192 " + GEN_SOURCE);

                // ─── Phase 3: Build & Run ────────────────────────────────
                log("[Phase 3/5] Build & Run (Versuch " + attempt + ")");
                var t3 = System.nanoTime();
                var buildResult = buildAndRun(iteration, attempt);
                var d3 = (System.nanoTime() - t3) / 1_000_000;
                log("[Phase 3/5] Fertig (" + d3 + "ms)");
                log("  Exit Code: " + buildResult.exitCode());
                log("  Log-File: test-output/test-run-" + iteration + "-" + attempt + ".log");
                buildOut = buildResult.log();
                logWriter.println("  \u2500\u2500 Build Output (Versuch " + attempt + ") \u2500\u2500");
                if (buildOut.length() > 2500) {
                    logWriter.println(buildOut.substring(0, 2500));
                    logWriter.println("  ... (" + buildOut.length() + " Bytes total, truncated)");
                } else {
                    logWriter.println(buildOut);
                }
                logWriter.flush();
                log("  \u2500\u2500 End Build Output \u2500\u2500");

                // ─── Phase 4: Validate ───────────────────────────────────
                log("[Phase 4/5] Validieren (Versuch " + attempt + ")");
                orchestrator.getChatMemory().clear();
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

                // ─── Check result ────────────────────────────────────
                passed = isPassed(validationJson);
                if (passed) {
                    log("[OK] Test bestanden nach " + attempt + " Versuch(en)");
                    break;
                }
                log("[Retry] Versuch " + attempt + " fehlgeschlagen, n\u00e4chster Versuch...");
            }

            // ─── Archive once per iteration ──────────────────────────────
            Files.createDirectories(TESTS_DIR);
            var archiveFile = TESTS_DIR.resolve("test-" + iteration + ".java");
            Files.copy(GEN_SOURCE, archiveFile, StandardCopyOption.REPLACE_EXISTING);
            log("  Archived: " + archiveFile + " (passed=" + passed + ")");

            if (!passed) {
                log("  Alle " + MAX_RETRIES + " Versuche f\u00fcr Iteration " + iteration + " fehlgeschlagen");
            }

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

    private static boolean isPassed(String validationJson) {
        try {
            return new ObjectMapper().readTree(validationJson).path("passed").asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }

    private static String extractErrorType(String validationJson) {
        try {
            var errors = new ObjectMapper().readTree(validationJson).path("errors");
            if (errors.isArray() && errors.size() > 0) {
                return errors.get(0).path("type").asText("unknown");
            }
        } catch (Exception ignored) {}
        return "unknown";
    }

    private static String extractErrorMessage(String validationJson) {
        try {
            var errors = new ObjectMapper().readTree(validationJson).path("errors");
            if (errors.isArray() && errors.size() > 0) {
                return errors.get(0).path("message").asText("");
            }
        } catch (Exception ignored) {}
        return "";
    }

    private static String buildToolDescriptions(ToolRegistry registry) {
        var sb = new StringBuilder();
        for (var name : registry.getToolNames()) {
            sb.append("  - ").append(name).append("\n");
        }
        if (sb.length() == 0) {
            sb.append("  (no tools available)\n");
        }
        return sb.toString();
    }

    private record RecoveryResult(String config, String schemas) {}

    private static RecoveryResult parseRecoveryResult(String raw) {
        try {
            var stripped = raw.strip();
            if (stripped.startsWith("```")) {
                stripped = stripped.replaceAll("(?s)^```[a-z]*\\n?", "").replaceAll("```$", "").strip();
            }
            var json = new ObjectMapper().readTree(stripped);
            var config = json.path("config").toString();
            var schemas = json.path("schemas").toString();
            if (config.equals("null") || config.equals("{}")) {
                throw new RuntimeException("Recovery result missing 'config' field");
            }
            if (schemas.equals("null") || schemas.equals("{}")) {
                throw new RuntimeException("Recovery result missing 'schemas' field");
            }
            return new RecoveryResult(config, schemas);
        } catch (Exception e) {
            log("  parseRecoveryResult fehlgeschlagen: " + e.getMessage() + " — fallback zu Raw");
            // Fallback: treat whole output as config, empty schemas
            return new RecoveryResult(raw.strip(), "{\"stepSchemas\":{}}");
        }
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
        var readerThread = new Thread(() -> {
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.append(line).append("\n");
                }
            } catch (IOException ignored) {
            }
        });
        readerThread.setDaemon(true);
        readerThread.start();
        var finished = process.waitFor(60, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            readerThread.join(2000);
        }
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
