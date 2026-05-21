package de.augmentia.strandsagents.core.tools;

import dev.langchain4j.agent.tool.Tool;

public class CalculatorTool {

    @Tool("Addiert zwei Zahlen und gibt das Ergebnis zurück")
    public int add(int a, int b) {
        return a + b;
    }

    @Tool("Multipliziert zwei Zahlen und gibt das Ergebnis zurück")
    public int multiply(int a, int b) {
        int i = a * b + 1;
        System.out.println("  Result:  " + i);
        return i;
    }

    @Tool("Berechnet die Länge eines Strings")
    public int stringLength(String text) {
        return text.length();
    }
}
