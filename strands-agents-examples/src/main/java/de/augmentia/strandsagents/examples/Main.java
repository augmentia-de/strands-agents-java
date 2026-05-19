package de.augmentia.strandsagents.examples;

import de.augmentia.strandsagents.core.ToolExecutor;
import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.agent.StrandsAgent;
import de.augmentia.strandsagents.core.config.ModelFactory;
import de.augmentia.strandsagents.core.model.agent.AgentResult;
import de.augmentia.strandsagents.core.model.event.AgentStartedEvent;
import de.augmentia.strandsagents.core.model.event.ToolExecutionFinishedEvent;
import de.augmentia.strandsagents.core.model.event.ToolExecutionStartedEvent;
import de.augmentia.strandsagents.skills.*;
import dev.langchain4j.model.chat.ChatModel;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        var apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("Fehler: OPENAI_API_KEY ist nicht gesetzt.");
            System.out.println("  Nutze ./dev.sh run-mock für einen Demo-Durchlauf ohne API-Key.");
            System.out.println("  Oder setze die Umgebungsvariable: export OPENAI_API_KEY=sk-...");
            System.exit(1);
        }

        System.out.println("=== Strands Agent (OpenAI-kompatibel) ===");
        System.out.println();

        demoMode1();
        demoMode2();
        demoMode3();
    }

    static void demoMode1() {
        System.out.println("--- Mode 1: Predefined (initialSkills) ---");

        var builder = ToolRegistry.builder().standard();
        var registry = builder.build();
        var skills = loadDemoSkills();
        var skillsPlugin = new AgentSkillsPlugin(skills, List.of("example-skills"));

        var agent = new StrandsAgent(createModel(), registry, new ToolExecutor(),
            null, null, null, List.of(skillsPlugin));
        agent.setSystemPrompt("You are a helpful assistant. Skills have been pre-loaded.");
        setupEvents(agent);

        var result = agent.execute("What skills are available to you?");
        System.out.println("Agent: " + result.finalAnswer());
        System.out.println();
    }

    static void demoMode2() {
        System.out.println("--- Mode 2: Dynamic (skill_search + mcp_ingest) ---");

        var registry = ToolRegistry.builder().standard().build();
        var skills = loadDemoSkills();
        var skillsPlugin = new AgentSkillsPlugin(skills);
        skillsPlugin.setSkillSearchEnabled(true);

        var model = createModel();
        registry.register(ToolRegistry.createMethod(new McpIngestTool(registry)));

        var agent = new StrandsAgent(model, registry, new ToolExecutor(),
            null, null, null, List.of(skillsPlugin));
        agent.setSystemPrompt("You have skill_search to discover and activate skills, "
            + "and mcp_ingest to connect external MCP servers.");
        setupEvents(agent);

        var result = agent.execute("Search for available skills and activate 'example-skills'.");
        System.out.println("Agent: " + result.finalAnswer());
        System.out.println();
    }

    static void demoMode3() {
        System.out.println("--- Mode 3: Capability Search Sub-Agent ---");

        var registry = ToolRegistry.builder().standard().build();
        var skills = loadDemoSkills();
        var skillsPlugin = new AgentSkillsPlugin(skills);
        skillsPlugin.setSkillSearchEnabled(true);

        var capRegistry = CapabilityRegistry.builder()
            .skillDir(java.nio.file.Path.of("skills"))
            .build();

        var model = createModel();
        var capTool = new CapabilitySearchTool(capRegistry, model);
        registry.register(ToolRegistry.createMethod(capTool));

        var agent = new StrandsAgent(model, registry, new ToolExecutor(),
            null, null, null, List.of(skillsPlugin));
        agent.setSystemPrompt("Use capability_search to discover skills and tools relevant to your task.");
        setupEvents(agent);

        var result = agent.execute("What capabilities are available for my task?");
        System.out.println("Agent: " + result.finalAnswer());
        System.out.println();
    }

    static void interact(StrandsAgent agent, String prompt) {
        System.out.println("---");
        System.out.println("Du:    " + prompt);
        AgentResult result = agent.execute(prompt);
        System.out.println("Agent: " + result.finalAnswer());
        System.out.println("       StopReason: " + result.stopReason());
        System.out.println("       Tokens: " + result.metrics().inputTokens()
            + " in / " + result.metrics().outputTokens() + " out, "
            + result.metrics().durationMs() + " ms"
            + ", Tool-Calls: " + result.metrics().toolCallsCount());
        System.out.println();
    }

    private static ChatModel createModel() {
        return ModelFactory.createOpenAiFromEnv();
    }

    private static List<Skill> loadDemoSkills() {
        try {
            var dir = java.nio.file.Path.of("skills");
            if (java.nio.file.Files.isDirectory(dir)) {
                return SkillParser.fromDirectory(dir);
            }
        } catch (Exception ignored) {}
        return List.of(new Skill("example-skills",
            "An example skill for demonstration purposes",
            "You are skilled in general problem solving and can help with any task.",
            null, List.of(), java.util.Map.of(), null, null));
    }

    private static void setupEvents(StrandsAgent agent) {
        agent.setEventListener(event -> {
            switch (event) {
                case AgentStartedEvent e ->
                    System.out.println("  [EVENT] Gestartet");
                case ToolExecutionStartedEvent e ->
                    System.out.println("  [EVENT] Tool: " + e.toolCall().toolName());
                case ToolExecutionFinishedEvent e ->
                    System.out.println("  [EVENT] Result: " + e.result().toolName()
                        + " \u2192 " + e.result().result());
                default -> {}
            }
        });
    }
}
