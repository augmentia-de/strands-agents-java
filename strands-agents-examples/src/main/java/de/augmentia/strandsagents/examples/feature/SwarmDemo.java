package de.augmentia.strandsagents.examples.feature;

import java.lang.reflect.Method;
import java.util.Map;

import de.augmentia.strandsagents.core.*;
import de.augmentia.strandsagents.core.agent.subagent.SubAgentTool;
import de.augmentia.strandsagents.core.agent.swarm.SwarmOrchestrator;
import de.augmentia.strandsagents.core.config.AgentConfig;
import de.augmentia.strandsagents.core.config.ModelFactory;
import de.augmentia.strandsagents.core.model.event.ToolExecutionFinishedEvent;
import de.augmentia.strandsagents.core.model.event.ToolExecutionStartedEvent;

import dev.langchain4j.model.chat.ChatModel;
import de.augmentia.strandsagents.core.tools.CalculatorTool;

public class SwarmDemo {

    public static void main(String[] args) {
        if (System.getenv("OPENAI_API_KEY") == null || System.getenv("OPENAI_API_KEY").isBlank()) {
            System.out.println("OPENAI_API_KEY not set.");
            System.exit(1);
        }
        new SwarmDemo().run();
    }

    void run() {
        var model = ModelFactory.createOpenAiFromEnv();

        System.out.println("=== AUTONOMOUS SWARM DEMO ===\n");

        demoOrchestrator(model);
        System.out.println();
        demoAgentTool(model);
    }

    // ---------------------------------------------------------------
    // Pattern A: Orchestrator-based routing (SwarmOrchestrator)
    // ---------------------------------------------------------------
    void demoOrchestrator(ChatModel model) {
        System.out.println("── Pattern A: Orchestrator-based ────");

        var researchAgent = AgentConfig.builder()
            .toolRegistry(ToolRegistry.builder().standard().include("web_search", "web_fetch").build())
            .systemPrompt("""
                You are a market research analyst. Research the given topic thoroughly.
                Use web_search and web_fetch to gather current data.
                Always conclude with a clear summary of your findings.""")
            .build()
            .createAgent(model);

        var financeAgent = AgentConfig.builder()
            .toolRegistry(ToolRegistry.builder().with(new CalculatorTool()).build())
            .systemPrompt("""
                You are a senior financial analyst. Analyze data and produce
                financial projections, ROI estimates, and risk assessments.
                Use the calculator tool for precise computations.""")
            .build()
            .createAgent(model);

        var orchestrator = new SwarmOrchestrator(
            Map.of("research", researchAgent, "finance", financeAgent),
            researchAgent);

        System.out.println("  Routes: research, finance\n");

        var r1 = orchestrator.execute(
            "research the European EV market trends for 2024");
        System.out.println("  [research] " + truncate(r1.finalAnswer(), 200));
        System.out.println("  Tokens: " + r1.metrics().inputTokens()
            + " in / " + r1.metrics().outputTokens() + " out, "
            + r1.metrics().durationMs() + " ms\n");

        var r2 = orchestrator.execute(
            "finance: calculate the projected ROI for a new EV charging network");
        System.out.println("  [finance] " + truncate(r2.finalAnswer(), 200));
        System.out.println("  Tokens: " + r2.metrics().inputTokens()
            + " in / " + r2.metrics().outputTokens() + " out, "
            + r2.metrics().durationMs() + " ms");
    }

    // ---------------------------------------------------------------
    // Pattern B: Agent-as-a-Tool (autonomous handoff)
    // ---------------------------------------------------------------
    void demoAgentTool(ChatModel model) {
        System.out.println("── Pattern B: Agent-as-a-Tool ──────");
        System.out.println("  (LLM decides autonomously when to delegate)\n");

        Method executeMethod;
        try {
            executeMethod = SubAgentTool.class.getMethod("execute", String.class);
        } catch (NoSuchMethodException e) {
            System.out.println("  Error: AgentTool.execute(String) not found");
            return;
        }

        // Sub-agent: financial analyst with CalculatorTool
        var financeAgent = AgentConfig.builder()
            .toolRegistry(ToolRegistry.builder().with(new CalculatorTool()).build())
            .systemPrompt("""
                You are a Senior Financial Analyst.
                Your expertise covers quantitative modeling, ROI projections,
                and risk assessment. Use the calculator for precise numbers.""")
            .build()
            .createAgent(model);

        var financeTool = new SubAgentTool(financeAgent, "finance_expert",
            "Delegate complex financial calculations or ROI analysis.");

        // Parent agent: researcher with web tools + finance_expert as tool
        var researcher = AgentConfig.builder()
            .toolRegistry(ToolRegistry.builder()
                .standard().include("web_search", "web_fetch")
                .build())
            .systemPrompt("""
                You are an expert Market Researcher.
                Research topics thoroughly using web_search and web_fetch.
                If you encounter financial data, ROI calculations, or complex
                modeling, delegate to the 'finance_expert' tool.
                Always produce a final consolidated report.""")
            .build()
            .createAgent(model);

        researcher.getToolRegistry().register("finance_expert", financeTool, executeMethod);

        // Event listener for real-time handoff visibility
        researcher.setEventListener(event -> {
            switch (event) {
                case ToolExecutionStartedEvent e ->
                    System.out.println("  → Tool call: " + e.toolCall().toolName());
                case ToolExecutionFinishedEvent e ->
                    System.out.println("  ← Tool done: " + e.result().toolName()
                        + " (" + truncate(e.result().result(), 80) + ")");
                default -> {}
            }
        });

        var result = researcher.execute(
            "Analyze the European EV market for 2024. Research market trends, " +
            "then delegate the financial ROI analysis to the finance_expert " +
            "for a new charging station network.");

        System.out.println("\n  Final: " + truncate(result.finalAnswer(), 300));
        System.out.println("  Tokens: " + result.metrics().inputTokens()
            + " in / " + result.metrics().outputTokens() + " out, "
            + result.metrics().durationMs() + " ms");
        System.out.println("  Stop reason: " + result.stopReason());
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private String truncate(String s, int max) {
        if (s == null) return "null";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
