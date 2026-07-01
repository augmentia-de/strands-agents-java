package de.augmentia.strandsagents.examples;

import de.augmentia.strandsagents.core.ToolExecutor;
import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.Agent;
import de.augmentia.strandsagents.config.ModelFactory;
import de.augmentia.strandsagents.features.pipeline.AgentHook;
import de.augmentia.strandsagents.features.pipeline.HookContexts;
import de.augmentia.strandsagents.features.pipeline.HookRegistry;
import de.augmentia.strandsagents.features.pipeline.HookResult;
import de.augmentia.strandsagents.model.agent.AgentResult;
import de.augmentia.strandsagents.features.tools.CalculatorTool;
import de.augmentia.strandsagents.features.tools.TimeTool;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import java.util.ArrayList;
import java.util.List;

/**
 * Demonstrates ALL hook capabilities:
 * - beforeAgent: modify prompt, add/remove tools in registry
 * - afterAgent: modify final answer
 * - beforeModelCall: add/remove tools for this call, modify system prompt in-place
 * - afterModelCall: modify response, request retry
 * - beforeToolCall: cancel specific tool calls
 * - afterToolCall: modify tool result
 *
 * Run with: MAVEN_OPTS="--enable-preview" mvn exec:java -pl strands-agents-examples
 *   -Dexec.mainClass="de.augmentia.strandsagents.examples.HookDemo"
 */
public class HookDemo {

    public static void main(String[] args) {

        // --- Setup tools ---
        var tools = ToolRegistry.builder()
            .with(new CalculatorTool())
            .with(new TimeTool())
            .build();

        // --- Create hook registry and register all demo hooks ---
        var hooks = new HookRegistry();
        hooks.register(new PromptModifierHook());
        hooks.register(new ToolGuardHook());
        hooks.register(new AuditHook());
        var addToolHook = new DynamicToolHook();
        hooks.register(addToolHook);

        // --- Build agent with ALL features: tools + session + resilience + hooks ---
        var agent = new Agent(
            //new MockChatModel("Mock: %s"),
                ModelFactory.createOpenAiFromEnv(),
            tools,
            new ToolExecutor(),
            null,   // no conversation manager
            null,   // no session manager
            null,   // no resilience
            null,   // no plugins
            hooks   // hook registry
        );
        agent.setSystemPrompt("You are a helpful assistant.");

        // --- Execute ---
        System.out.println("=== HookDemo ===\n");

        System.out.println("--- Run 1: Basic execution with CalculatorTool ---");
        printResult(agent.execute("What is 2+2?"));

        System.out.println("--- Run 2: TimeTool blocked by ToolGuardHook, beforeToolCall blocks CalculatorTool ---");
        printResult(agent.execute("What time is it?"));

        System.out.println("--- Run 3: Add dynamic tool, then use it ---");
        addToolHook.addDynamicTool(agent);
        printResult(agent.execute("Use the extraValue tool"));

        System.out.println("--- Run 4: Short answer triggers Retry in afterModelCall ---");
        printResult(agent.execute("OK"));

        System.out.println("--- Run 5: Remove dynamic tool ---");
        addToolHook.cleanup(agent);
        printResult(agent.execute("What is 3*4?"));

        System.out.println("\n=== HookDemo complete ===");

        System.out.println("\n=== HookDemo complete ===");
    }

    private static void printResult(AgentResult result) {
        System.out.println("  stopReason: " + result.stopReason());
        System.out.println("  answer: " + result.finalAnswer());
        var m = result.metrics();
        System.out.println("  metrics: " + m.durationMs() + "ms, "
            + m.toolCallsCount() + " tool calls, "
            + m.inputTokens() + " in / " + m.outputTokens() + " out");
        System.out.println();
    }

    // ---------------------------------------------------------------
    // Hook 1: PromptModifierHook — modifies prompt and system prompt
    // ---------------------------------------------------------------

    static class PromptModifierHook implements AgentHook {
        @Override
        public String name() { return "prompt-modifier"; }

        @Override
        public HookResult beforeAgent(HookContexts.BeforeAgentContext ctx) {
            System.out.println("  [hook:beforeAgent] original prompt: " + ctx.prompt());
            var modified = "[PREFIX] " + ctx.prompt() + " [SUFFIX]";
            System.out.println("  [hook:beforeAgent] modified prompt: " + modified);
            return new HookResult.Modify<>(modified);
        }

        @Override
        public HookResult beforeModelCall(HookContexts.BeforeModelCallContext ctx) {
            System.out.println("  [hook:beforeModelCall] appending to system prompt");
            ctx.systemPrompt().append("\n(Additional context injected by hook)");
            return new HookResult.Continue();
        }

        @Override
        public HookResult afterModelCall(HookContexts.AfterModelCallContext ctx, String response) {
            if (response.length() < 10) {
                System.out.println("  [hook:afterModelCall] response too short ("
                    + response.length() + " chars), requesting retry");
                return new HookResult.Retry("Please provide a more detailed answer.");
            }
            System.out.println("  [hook:afterModelCall] response OK (" + response.length() + " chars)");
            return new HookResult.Modify<>("[HOOK-TRANSFORMED] " + response);
        }

        @Override
        public HookResult afterAgent(HookContexts.AfterAgentContext ctx, String response) {
            System.out.println("  [hook:afterAgent] final answer: " + response);
            return new HookResult.Modify<>(response + "\n(Post-processed by hook)");
        }
    }

    // ---------------------------------------------------------------
    // Hook 2: ToolGuardHook — restricts tools per call
    // ---------------------------------------------------------------

    static class ToolGuardHook implements AgentHook {
        @Override
        public String name() { return "tool-guard"; }

        @Override
        public HookResult beforeModelCall(HookContexts.BeforeModelCallContext ctx) {
            var restricted = new ArrayList<ToolSpecification>();
            for (var spec : ctx.tools()) {
                if (spec.name().contains("Time")) {
                    System.out.println("  [hook:beforeModelCall] BLOCKING tool: " + spec.name());
                    continue;
                }
                restricted.add(spec);
            }
            if (restricted.size() != ctx.tools().size()) {
                System.out.println("  [hook:beforeModelCall] reduced tools from "
                    + ctx.tools().size() + " to " + restricted.size());
                return new HookResult.Modify<>(List.copyOf(restricted));
            }
            return new HookResult.Continue();
        }

        @Override
        public HookResult beforeToolCall(HookContexts.BeforeToolCallContext ctx) {
            if (ctx.toolName().contains("Calculator")) {
                System.out.println("  [hook:beforeToolCall] BLOCKING dangerous tool: "
                    + ctx.toolName());
                return new HookResult.Cancel(
                    "Tool '" + ctx.toolName() + "' is not allowed in this context");
            }
            System.out.println("  [hook:beforeToolCall] allowing: " + ctx.toolName());
            return new HookResult.Continue();
        }
    }

    // ---------------------------------------------------------------
    // Hook 3: AuditHook — logs and transforms tool results
    // ---------------------------------------------------------------

    static class AuditHook implements AgentHook {
        @Override
        public String name() { return "audit"; }

        @Override
        public HookResult afterModelCall(HookContexts.AfterModelCallContext ctx, String response) {
            System.out.println("  [hook:audit] LLM call: "
                + ctx.inputTokens() + " in, " + ctx.outputTokens() + " out");
            return new HookResult.Continue();
        }

        @Override
        public HookResult afterToolCall(HookContexts.AfterToolCallContext ctx, String result) {
            var status = ctx.isError() ? "ERROR" : "OK";
            System.out.println("  [hook:audit] tool " + ctx.toolName()
                + " → " + status + " (" + result.length() + " chars)");
            return new HookResult.Modify<>("[audited] " + result);
        }

        @Override
        public HookResult beforeAgent(HookContexts.BeforeAgentContext ctx) {
            System.out.println("  [hook:audit] session: " + ctx.sessionId());
            return new HookResult.Continue();
        }
    }

    // ---------------------------------------------------------------
    // Hook 4: DynamicToolHook — adds and removes tools at runtime
    // ---------------------------------------------------------------

    static class DynamicToolHook implements AgentHook {
        private boolean toolAdded = false;

        @Override
        public String name() { return "dynamic-tool"; }

        @Override
        public HookResult beforeAgent(HookContexts.BeforeAgentContext ctx) {
            // Can't access ToolRegistry from context yet — would need §15.2 change.
            // Instead, tools are managed externally via addTool/removeTool on the Agent.
            return new HookResult.Continue();
        }

        @Override
        public HookResult beforeModelCall(HookContexts.BeforeModelCallContext ctx) {
            if (hasTool(ctx, "ReadTool")) {
                System.out.println("  [hook:dynamic-tool] ReadTool already registered");
            }
            return new HookResult.Continue();
        }

        private boolean hasTool(HookContexts.BeforeModelCallContext ctx, String name) {
            return ctx.tools().stream().anyMatch(t -> t.name().equals(name));
        }

        void addDynamicTool(Agent agent) {
            if (!toolAdded) {
                agent.addTool(new ExtraTool());
                toolAdded = true;
                System.out.println("  [hook:dynamic-tool] added ExtraTool to agent");
            }
        }

        void cleanup(Agent agent) {
            if (toolAdded) {
                agent.removeTool("extra_tool");
                toolAdded = false;
                System.out.println("  [hook:dynamic-tool] removed ExtraTool from agent");
            }
        }
    }

    /** A tool added at runtime. */
    public static class ExtraTool {
        @Tool("Returns a fixed extra value")
        public String extraValue() {
            return "extra-tool-result-42";
        }
    }
}
