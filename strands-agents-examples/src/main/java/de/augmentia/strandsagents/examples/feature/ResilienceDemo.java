package de.augmentia.strandsagents.examples.feature;

import de.augmentia.strandsagents.core.DefaultToolExecutor;
import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.Agent;
import de.augmentia.strandsagents.config.ModelFactory;
import de.augmentia.strandsagents.interceptor.resilience.ResilienceConfig;
import de.augmentia.strandsagents.interceptor.resilience.RetryConfig;
import de.augmentia.strandsagents.examples.tools.ChaosMonkeyHook;
import de.augmentia.strandsagents.examples.tools.ToolCallingMockModel;
import de.augmentia.strandsagents.examples.tools.TraceRecorder;
import de.augmentia.strandsagents.examples.tools.UnreliableCalculatorTool;
import de.augmentia.strandsagents.examples.tools.UnreliableWeatherTool;
import dev.langchain4j.model.chat.ChatModel;

public class ResilienceDemo {

    public static void main(String[] args) {
        boolean useMock = false;
        for (var a : args) {
            if ("--mock".equals(a)) useMock = true;
        }

        ChatModel model = useMock
            ? ToolCallingMockModel.createDefault()
            : ModelFactory.createOpenAiFromEnv();

        var registry = ToolRegistry.builder()
            .with(new UnreliableCalculatorTool())
            .with(new UnreliableWeatherTool())
            .build();

        var executor = new DefaultToolExecutor(8);
        var resilience = new ResilienceConfig(
            new RetryConfig(3, 500, 2.0),
            null);

        if (useMock) {
            System.out.println("=== Unreliable Tools Demo (Mock-Modus) ===");
        } else {
            System.out.println("=== Unreliable Tools Demo (OpenAI) ===");
        }

        var agent = new Agent(model, registry, executor, null, null, resilience);

        var trace = new TraceRecorder();
        agent.setEventListener(trace);
        agent.addHook(trace);
        agent.addHook(ChaosMonkeyHook.moderate());

        var result = agent.execute(
            "Calculate 1234 + 5678 and get the weather for Berlin."
        );

        trace.printSummary();

        System.out.println("\n=== Result ===");
        System.out.println("  StopReason: " + result.stopReason());
        System.out.println("  Answer: " + result.finalAnswer());
        System.out.println("  Tokens: " + result.metrics().inputTokens() + " / "
            + result.metrics().outputTokens());
        System.out.println("  Tool Calls: " + result.metrics().toolCallsCount());
        System.out.println("  Duration: " + result.metrics().durationMs() + "ms");
    }
}
