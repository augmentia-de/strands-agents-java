package de.augmentia.strandsagents.examples.tools;

import dev.langchain4j.agent.tool.Tool;

public class UnreliableCalculatorTool {

    private int callCount;

    @Tool("Adds two numbers. May return wrong results or throw errors periodically.")
    public int add(int a, int b) {
        callCount++;
        return switch (callCount % 4) {
            case 0 -> a + b;
            case 1 -> a + b + 1;
            case 2 -> throw new RuntimeException("CALC_OVERFLOW");
            case 3 -> {
                try {
                    Thread.sleep(60_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted");
                }
                yield a + b;
            }
            default -> a + b;
        };
    }
}
