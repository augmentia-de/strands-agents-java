package de.augmentia.strandsagents.examples.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.Agent;
import de.augmentia.strandsagents.features.swarm.SwarmOrchestrator;
import de.augmentia.strandsagents.config.AgentConfig;
import de.augmentia.strandsagents.config.ModelFactory;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Scanner;

/**
 * Finance Assistant Swarm Demo (Java).
 * 
 * This sample demonstrates a collaborative swarm of specialized agents for comprehensive stock analysis.
 * It mimics the logic found in the Python 'finance-assistant-swarm-agent' industry use case.
 * 
 * Architecture:
 * FinanceAssistant (Orchestrator)
 *   ├── Company Strategist (Agent): Analyzes business model & sector.
 *   ├── Financial Analyst (Agent): Analyzes key metrics & financials.
 *   └── Market Analyst (Agent): Analyzes news & sentiment.
 */
public class InvestmentAnalysisDemo {
    private static final Logger log = LoggerFactory.getLogger(InvestmentAnalysisDemo.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) {
        if (System.getenv("OPENAI_API_KEY") == null || System.getenv("OPENAI_API_KEY").isBlank()) {
            System.err.println("Error: OPENAI_API_KEY environment variable is not set.");
            System.exit(1);
        }

        System.out.println("\n🤖 Hybrid Multi-Agent Stock Analysis (Java 21) 📊");
        System.out.println("Features: Real-time data + Specialized collaborative agents + Swarm synthesis\n");

        new InvestmentAnalysisDemo().run();
    }

    public void run() {
        ChatModel model = ModelFactory.createOpenAiFromEnv();

        // 1. Create specialized agents
        Agent companyStrategist = AgentConfig.builder()
            .systemPrompt("""
                You are a Business Strategist. Your task is to analyze the company's business model, 
                sector position, and competitive advantages. 
                Identify the core value proposition and market strategy.""")
            .build()
            .createAgent(model);

        Agent financialAnalyst = AgentConfig.builder()
            .toolRegistry(ToolRegistry.builder().with(new FinancialTools()).build())
            .systemPrompt("""
                You are a Senior Financial Analyst. Your task is to assess the company's financial health.
                Use the 'get_financial_metrics' tool to gather data. 
                Analyze profitability, debt levels, and valuation (P/E, PEG).""")
            .build()
            .createAgent(model);

        Agent marketAnalyst = AgentConfig.builder()
            .toolRegistry(ToolRegistry.builder().standard().include("web_search").build())
            .systemPrompt("""
                You are a Market Sentiment Analyst. Your task is to research recent news and market trends.
                Use 'web_search' to find current events and public sentiment.
                Synthesize findings into a market outlook.""")
            .build()
            .createAgent(model);

        // 2. Create the Swarm Orchestrator
        // The orchestrator coordinates handoffs between these specialized roles.
        SwarmOrchestrator orchestrator = new SwarmOrchestrator(
            Map.of(
                "strategist", companyStrategist,
                "finance", financialAnalyst,
                "market", marketAnalyst
            ),
            companyStrategist // Start with strategist
        );

        // 3. Main Loop
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("\nWhat company would you like to analyze? (or 'exit' to quit) > ");
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("exit")) {
                System.out.println("Goodbye! 👋");
                break;
            }

            if (input.isEmpty()) continue;

            System.out.println("\nInitiating collaborative swarm analysis for: " + input + "...\n");

            try {
                // We prompt the orchestrator to engage the swarm
                String prompt = String.format(
                    "Perform a comprehensive analysis of %s. " +
                    "Start with the strategist for business model, then consult finance for health metrics, " +
                    "and finally market for current sentiment. Provide a unified investment thesis.", 
                    input);

                var result = orchestrator.execute(prompt);

                System.out.println("\n==========================================");
                System.out.println("📊 COMPREHENSIVE STOCK ANALYSIS REPORT");
                System.out.println("==========================================");
                System.out.println(result.finalAnswer());
                System.out.println("==========================================");
                System.out.printf("Tokens: %d in / %d out | Duration: %d ms\n", 
                    result.metrics().inputTokens(), 
                    result.metrics().outputTokens(), 
                    result.metrics().durationMs());

            } catch (Exception e) {
                System.err.println("Error during analysis: " + e.getMessage());
            }
        }
    }

    /**
     * Specialized financial tools (simulated for the demo).
     */
    public static class FinancialTools {
        
        @Tool("Fetches key financial metrics for a given stock ticker.")
        public String get_financial_metrics(@P("The stock ticker symbol (e.g. AAPL, MSFT)") String ticker) {
            log.debug("Tool: get_financial_metrics for {}", ticker);
            ObjectNode node = mapper.createObjectNode();
            ObjectNode data = node.putObject("data");
            
            data.put("symbol", ticker.toUpperCase());
            
            // Simulated data matching typical financial reports
            if (ticker.equalsIgnoreCase("AAPL")) {
                data.put("market_cap", "3.5T");
                data.put("pe_ratio", 32.5);
                data.put("revenue_growth", "5.2%");
                data.put("profit_margins", "26.3%");
                data.put("debt_to_equity", 1.45);
            } else if (ticker.equalsIgnoreCase("TSLA")) {
                data.put("market_cap", "800B");
                data.put("pe_ratio", 65.2);
                data.put("revenue_growth", "15.8%");
                data.put("profit_margins", "12.1%");
                data.put("debt_to_equity", 0.08);
            } else {
                data.put("market_cap", "N/A");
                data.put("pe_ratio", 20.0);
                data.put("revenue_growth", "8.0%");
                data.put("profit_margins", "15.0%");
                data.put("status", "Using generic industry averages for " + ticker);
            }
            
            return node.toString();
        }
    }
}
