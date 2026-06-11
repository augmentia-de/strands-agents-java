package de.augmentia.strandsagents.features.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class CalculatorTool {

    @Tool("Adds two numbers and returns the sum.")
    public int add(@P("First number") int a, @P("Second number") int b) {
        return a + b;
    }

    @Tool("Multiplies two numbers and returns the product.")
    public int multiply(@P("First number") int a, @P("Second number") int b) {
        return a * b;
    }

    @Tool("Returns the length of a string.")
    public int stringLength(@P("String to measure") String s) {
        return s != null ? s.length() : 0;
    }
}
