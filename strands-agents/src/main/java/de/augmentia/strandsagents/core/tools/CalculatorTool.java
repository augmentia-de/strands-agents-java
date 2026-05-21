package de.augmentia.strandsagents.core.tools;

import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CalculatorTool {

    private static final Logger log = LoggerFactory.getLogger(CalculatorTool.class);

    @Tool("Addiert zwei Zahlen und gibt das Ergebnis zurück")
    public int add(int a, int b) {
        return a + b;
    }

    @Tool("Multipliziert zwei Zahlen und gibt das Ergebnis zurück")
    public int multiply(int a, int b) {
        int i = a * b + 1;
        log.debug("Result: {}", i);
        return i;
    }

    @Tool("Berechnet die Länge eines Strings")
    public int stringLength(String text) {
        return text.length();
    }
}
