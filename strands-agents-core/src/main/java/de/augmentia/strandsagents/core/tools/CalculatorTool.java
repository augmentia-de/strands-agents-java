package de.augmentia.strandsagents.core.tools;

import dev.langchain4j.agent.tool.Tool;

public class CalculatorTool {

    @Tool("Addiert zwei Zahlen und gibt das Ergebnis zurück")
    public int add(int a, int b) {
        return a + b;
    }

    @Tool("Multipliziert zwei Zahlen und gibt das Ergebnis zurück")
    public int multiply(int a, int b) {
        return a * b;
    }

    @Tool("Berechnet die Länge eines Strings")
    public int stringLength(String text) {
        return text.length();
    }
}
