package de.augmentia.strandsagents.examples.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.augmentia.strandsagents.core.AgentFactory;
import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.Agent;
import de.augmentia.strandsagents.core.subagent.SubAgentTool;
import de.augmentia.strandsagents.config.AgentConfig;
import de.augmentia.strandsagents.config.AgentSettings;
import de.augmentia.strandsagents.config.ModelFactory;
import de.augmentia.strandsagents.model.agent.AgentResult;
import de.augmentia.strandsagents.model.event.ToolExecutionFinishedEvent;
import de.augmentia.strandsagents.tools.AsyncAgentTool;
import de.augmentia.strandsagents.tools.ToolResult;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Multi-Agent Evaluation Demo (Java).
 * 
 * This sample demonstrates how to evaluate a multi-agent system by analyzing individual 
 * agent performance, collective system outcomes, and coordination quality.
 * 
 * Architecture (Agent-as-a-Tool Pattern):
 * Orchestrator (Router)
 *   ├── Technical Support (Sub-Agent Tool)
 *   ├── Billing Support (Sub-Agent Tool)
 *   └── Returns & Exchanges (Sub-Agent Tool)
 */
public class MultiAgentEvaluationDemo {
    private static final Logger log = LoggerFactory.getLogger(MultiAgentEvaluationDemo.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        if (System.getenv("OPENAI_API_KEY") == null || System.getenv("OPENAI_API_KEY").isBlank()) {
            System.err.println("Error: OPENAI_API_KEY environment variable is not set.");
            System.exit(1);
        }

        System.out.println("\n🔍 Multi-Agent System Evaluation (Java 21) 🧪");
        System.out.println("Dimensions: Individual Performance + Collective System + Coordination Quality\n");

        new MultiAgentEvaluationDemo().run();
    }

    public void run() throws Exception {
        ChatModel model = ModelFactory.createOpenAiFromEnv();
        Method executeMethod = SubAgentTool.class.getMethod("execute", String.class, Object.class, AtomicBoolean.class, java.util.function.Consumer.class);
        Method executeAsyncMethod = SubAgentTool.class.getMethod("executeAsync", String.class, Object.class, AtomicBoolean.class, java.util.function.Consumer.class);

        // 1. Setup Specialists
        Agent techAgent = AgentFactory.buildAgent(AgentSettings.builder()
            .systemPrompt("You are a Technical Support Specialist. Troubleshoot the user's issue. You MUST use 'lookup_customer' to verify the user and 'create_ticket' if the issue is a bug or crash. Provide a detailed resolution.")
            .build(),
            AgentConfig.builder()
                .toolRegistry(ToolRegistry.builder().with(new DatabaseTools()).build())
                .build(),
            model);

        Agent billingAgent = AgentFactory.buildAgent(AgentSettings.builder()
            .systemPrompt("You are a Billing Specialist. Verify the customer using 'lookup_customer' and address their payment or subscription concern.")
            .build(),
            AgentConfig.builder()
                .toolRegistry(ToolRegistry.builder().with(new DatabaseTools()).build())
                .build(),
            model);

        Agent returnsAgent = AgentFactory.buildAgent(AgentSettings.builder()
            .systemPrompt("You are a Returns Specialist. Use tools to verify the order and customer. Provide clear instructions for returns or exchanges.")
            .build(),
            AgentConfig.builder()
                .toolRegistry(ToolRegistry.builder().with(new DatabaseTools()).build())
                .build(),
            model);

        // 2. Wrap Specialists as Tools (ASYNC for long-running operations)
        SubAgentTool techTool = new SubAgentTool(techAgent, "technical_support", "Handle technical issues, bugs, and crashes.");
        SubAgentTool billingTool = new SubAgentTool(billingAgent, "billing_support", "Handle payments, invoices, and subscriptions.");
        SubAgentTool returnsTool = new SubAgentTool(returnsAgent, "returns_exchanges", "Handle returns, exchanges, and shipping status.");

        // 3. Setup Orchestrator
        Agent orchestrator = AgentFactory.buildAgent(AgentSettings.builder()
            .systemPrompt("""
                You are a customer support router. Your ONLY task is to delegate queries to the appropriate specialist.
                
                CRITICAL RULES:
                - You MUST use one of the available tools (technical_support, billing_support, returns_exchanges) for any domain-specific query.
                - DO NOT answer domain-specific questions yourself. 
                - DO NOT just say you will connect the user; actually CALL the tool.
                - If the user provides a Customer ID or Order ID, pass it to the specialist.
                
                Routing Map:
                - Technical issues, bugs, errors -> technical_support
                - Billing, payments, subscriptions -> billing_support
                - Returns, exchanges, orders -> returns_exchanges
                - Simple greetings -> answer directly""")
            .build(),
            AgentConfig.builder().build(), model);

        // Register tools - SubAgentTool supports both sync and async via the same interface
        orchestrator.getToolRegistry().register(techTool);
        orchestrator.getToolRegistry().register(billingTool);
        orchestrator.getToolRegistry().register(returnsTool);

        // 4. Trace Interactions (Coordination Data)
        List<Interaction> interactions = new ArrayList<>();
        orchestrator.setEventListener(event -> {
            if (event instanceof ToolExecutionFinishedEvent e) {
                interactions.add(new Interaction(e.result().toolName(), "User Query", e.result().result()));
            }
        });

        // 5. Test Case: Technical Issue (uses async SubAgentTool)
        String testInput = "My app keeps crashing when I try to login. Customer ID: user123";
        System.out.println("[Test Input]: " + testInput);
        System.out.println("[Async Tool] SubAgentTool uses executeAsync() internally for non-blocking execution\n");
        
        AgentResult systemResult = orchestrator.execute(testInput);
        
        System.out.println("\n[System Response]: " + systemResult.finalAnswer());

        // 6. Perform Coordination Evaluation
        System.out.println("\n--- Coordination Quality Evaluation ---");
        evaluateCoordination(model, testInput, systemResult.finalAnswer(), interactions);
    }

    /**
     * Uses an LLM to evaluate the coordination quality of the multi-agent system.
     */
    private void evaluateCoordination(ChatModel model, String originalInput, String finalAnswer, List<Interaction> interactions) {
        StringBuilder report = new StringBuilder();
        report.append("Original Query: ").append(originalInput).append("\n");
        report.append("Orchestrator Final Answer: ").append(finalAnswer).append("\n");
        report.append("Interactions captured (Tool Calls):\n");
        if (interactions.isEmpty()) {
            report.append("- NO TOOLS WERE CALLED.\n");
        } else {
            for (var i : interactions) {
                report.append("- Agent Called: ").append(i.nodeName()).append("\n");
                report.append("  Response: ").append(i.response()).append("\n");
            }
        }

        Agent evaluator = AgentFactory.buildAgent(AgentSettings.builder()
            .systemPrompt("""
                You are a Multi-Agent Quality Evaluator. 
                Your task is to score the coordination between an orchestrator and specialist agents.
                
                Rubric:
                1.0: Correct agent selected via tool call, and specialist provided a helpful response.
                0.5: Correct agent selected, but the response was incomplete or the orchestrator was too chatty.
                0.0: COORDINATION FAILURE. This includes:
                     - Wrong agent selected.
                     - NO tool was called for a domain-specific query (Orchestrator answered directly).
                     - Orchestrator just promised to connect but did not trigger the tool call.
                
                Provide a score (0.0 to 1.0) and a brief justification.""")
            .build(),
            AgentConfig.builder().build(), model);

        var evalResult = evaluator.execute("Evaluate this interaction report:\n" + report.toString());
        
        System.out.println(evalResult.finalAnswer());
    }

    /**
     * Interaction record for evaluation.
     */
    public record Interaction(String nodeName, String input, String response) {}

    /**
     * Simulated Database Tools (SYNC - for quick operations).
     */
    public static class DatabaseTools {
        @Tool("Creates a support ticket for a customer")
        public String create_ticket(@P("Customer ID") String customerId, @P("Description of the issue") String description) {
            return "Ticket created: ID-99822 for customer " + customerId;
        }

        @Tool("Looks up customer subscription info")
        public String lookup_customer(@P("Customer ID") String customerId) {
            return "Customer " + customerId + ": John Doe, Pro Subscription, Active.";
        }
    }

    /**
     * Async Database Tools (ASYNC - for simulated long-running operations).
     * Demonstrates how to implement AsyncAgentTool for operations that benefit from true async execution.
     */
    public static class AsyncDatabaseTools implements AsyncAgentTool<AsyncDatabaseTools.Params> {
        @Override
        public String name() { return "async_database"; }
        
        @Override
        public String description() { return "Async database operations for complex queries"; }
        
        @Override
        public Class<Params> parameterType() { return Params.class; }
        
        @Override
        public com.fasterxml.jackson.databind.node.ObjectNode parameterSchema() {
            var factory = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance;
            var schema = factory.objectNode();
            schema.put("type", "object");
            var props = factory.objectNode();
            var queryProp = factory.objectNode();
            queryProp.put("type", "string");
            queryProp.put("description", "The query to execute");
            props.set("query", queryProp);
            schema.set("properties", props);
            var required = factory.arrayNode();
            required.add("query");
            schema.set("required", required);
            return schema;
        }

        @Override
        public ToolResult execute(String toolCallId, Params params, AtomicBoolean abortFlag, java.util.function.Consumer<ToolResult> onUpdate) {
            try {
                Thread.sleep(100);
                return ToolResult.success("Quick sync result for: " + params.query());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ToolResult.error("Interrupted");
            }
        }

        @Override
        public CompletableFuture<ToolResult> executeAsync(String toolCallId, Params params, AtomicBoolean abortFlag, java.util.function.Consumer<ToolResult> onUpdate) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(2000);
                    return ToolResult.success("Async result after 2s for: " + params.query());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return ToolResult.error("Interrupted");
                }
            });
        }

        public record Params(String query) {}
    }
}
