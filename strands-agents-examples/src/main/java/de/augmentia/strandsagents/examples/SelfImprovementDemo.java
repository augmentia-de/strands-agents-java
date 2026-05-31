package de.augmentia.strandsagents.examples;

import de.augmentia.strandsagents.core.ToolExecutor;
import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.agent.Agent;
import de.augmentia.strandsagents.core.config.ModelFactory;
import de.augmentia.strandsagents.core.tools.CalculatorTool;
import de.augmentia.strandsagents.core.tools.local.TimeTool;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

/**
 * Demonstrates modifying an agent between two calls to simulate self-improvement.
 * Uses the new addTool / removeTool / setToolRegistry / setSystemPrompt methods.
 */
public class SelfImprovementDemo {

    public static void main(String[] args) {
        System.out.println("=== Self-Improvement Demo ===");
        System.out.println("The agent is modified between two calls:\n");

        var model = ModelFactory.createOpenAiFromEnv();

        System.out.println("=== Self-Improvement Demo ===\n");

        // Phase 1 — calculator only
        System.out.println("─── Phase 1: calculator tool, math prompt ───");
        var reg = new ToolRegistry();
        reg.register(new CalculatorTool());
        var agent = new Agent(model, reg, new ToolExecutor());
        agent.setSystemPrompt("You are a math assistant. You must use math tools!");

        var r1 = agent.execute("What is 123 * 456? Also, what time is it?");
        System.out.println("  Agent:  " + r1.finalAnswer());
        System.out.println("  Tools:  " + r1.metrics().toolCallsCount() + " call(s)\n");

        // Phase 2 — modify in-place: new prompt, swap tool set
        System.out.println("─── Phase 2: time tool, new prompt (same agent) ───");
        agent.setSystemPrompt("You are a time keeper. Provide current date/time only.");
        agent.removeTool("add");
        agent.removeTool("multiply");
        agent.removeTool("stringLength");
        agent.addTool(new TimeTool());

        var r2 = agent.execute("Calculate 123 * 456? Also, what time is it?");
        System.out.println("  Agent:  " + r2.finalAnswer());
        System.out.println("  Tools:  " + r2.metrics().toolCallsCount() + " call(s)\n");

        // Phase 3 — replace the entire registry at once
        System.out.println("─── Phase 3: calculator again (setToolRegistry) ───");
        var freshReg = new ToolRegistry();
        freshReg.register(new CalculatorTool());
        agent.setToolRegistry(freshReg);
        agent.setSystemPrompt("You are a math assistant again.");

        var r3 = agent.execute("What is 123 * 456? Also, what time is it?");
        System.out.println("  Agent:  " + r3.finalAnswer());
        System.out.println("  Tools:  " + r3.metrics().toolCallsCount() + " call(s)\n");

        System.out.println("─── Summary ───");
        System.out.println("  Phase 1 (calc tool, math prompt):  " + r1.finalAnswer());
        System.out.println("  Phase 2 (time tool, time prompt):  " + r2.finalAnswer());
        System.out.println("  Phase 3 (calc tool, math prompt):  " + r3.finalAnswer());
        System.out.println();
        System.out.println("Methods used:");
        System.out.println("  - setSystemPrompt()       — change prompt in-place");
        System.out.println("  - addTool(obj)            — register an annotated tool");
        System.out.println("  - removeTool(name)        — unregister a tool");
        System.out.println("  - setToolRegistry(reg)    — swap entire tool set");
    }

    static class DemoMockModel implements ChatModel {
        @Override
        public ChatResponse chat(ChatRequest request) {
            var messages = request.messages();
            if (messages.isEmpty()) {
                return ChatResponse.builder()
                    .aiMessage(AiMessage.from(""))
                    .tokenUsage(new TokenUsage(0, 0))
                    .finishReason(FinishReason.STOP)
                    .build();
            }
            var last = messages.get(messages.size() - 1);
            var text = last instanceof UserMessage um
                ? um.singleText() : last.toString();

            var resp = switch (text) {
                case String s when s.contains("time") ->
                    "I can provide the current date and time using my tools.";
                default ->
                    "I processed your request: " + text;
            };

            return ChatResponse.builder()
                .aiMessage(AiMessage.from(resp))
                .tokenUsage(new TokenUsage(10, resp.length()))
                .finishReason(FinishReason.STOP)
                .build();
        }
    }
}
