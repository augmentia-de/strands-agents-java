package de.augmentia.strandsagents.examples.feature;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Demonstrates PII tokenization and detokenization without LLM call.
 * <p>
 * The demo shows: input text -> PII tokenization (pre-LLM) ->
 * simulated LLM response -> detokenization (post-LLM).
 * <p>
 * No API key required -- pure Java logic.
 */
public class PiiTokenizationDemo {

    public static void main(String[] args) {
        System.out.println("=" .repeat(72));
        System.out.println("  PII Tokenization & Detokenization (Enterprise Feature)");
        System.out.println("=" .repeat(72));
        System.out.println();

        var hook = new PiiHook();

        // Example texts with different PII types
        var inputs = List.of(
            "My email is max.mustermann@firma.de and phone +49 171 1234567.",
            "IBAN: DE12 5001 0517 0648 4898 90, Credit Card: 4532 1234 5678 9012",
            "Hello, I live in 80331 Munich and my name is Anna Schmidt."
        );

        for (int i = 0; i < inputs.size(); i++) {
            System.out.println("-".repeat(50));
            System.out.printf("  Text %d (before tokenization):%n", i + 1);
            System.out.println("    " + inputs.get(i));

            var tokenized = hook.tokenize("session-" + i, inputs.get(i));
            System.out.println("  After tokenization:");
            System.out.println("    " + tokenized);

            // Simulated LLM output (may contain tokens)
            var llmOutput = "The data has been processed: " + tokenized
                + " Please confirm the details.";
            var restored = hook.detokenize("session-" + i, llmOutput);
            System.out.println("  After detokenization (LLM output -> original PII):");
            System.out.println("    " + restored);
            System.out.println("  Detokenization " + (restored.contains(llmOutput) ? "FAILED" : "SUCCESSFUL"));
        }

        System.out.println();
        System.out.println("= " .repeat(72));
        System.out.println("  Total tokenized: " + hook.counter.get() + " tokens");
        System.out.println("  Session isolation: " + (hook.sessionTokenMaps.size() + " active sessions"));
        System.out.println("= " .repeat(72));
    }

    // PII hook: identical to the logic in EnterpriseGuardDemo.PiiTokenizingHook
    static class PiiHook {
        private final Map<String, Map<String, String>> sessionTokenMaps = new ConcurrentHashMap<>();
        private final AtomicInteger counter = new AtomicInteger(0);
        private final List<Pattern> patterns = new ArrayList<>();
        private final List<String> typeNames = new ArrayList<>();

        PiiHook() {
            addRule("[\\w.+-]+@[\\w-]+\\.[\\w.]+", "EMAIL");
            addRule("(\\+49|0)[1-5]\\d{1,2}[\\s/-]?\\d{3,}", "TEL");
            addRule("DE\\d{2}(?:\\s*\\d{4}){5}\\s*\\d{2}", "IBAN");
            addRule("\\b(?:\\d{4}[\\s-]?){4}\\b", "CC");
            addRule("\\b\\d{5}\\b(?!(\\s*[-–]\\s*\\d{5}))", "PLZ");
        }

        private void addRule(String regex, String type) {
            patterns.add(Pattern.compile(regex));
            typeNames.add(type);
        }

        String tokenize(String sessionId, String text) {
            if (text == null) return null;
            var tokenMap = sessionTokenMaps.computeIfAbsent(sessionId,
                k -> new ConcurrentHashMap<>());
            var result = text;
            for (int i = 0; i < patterns.size(); i++) {
                result = replaceAndTokenize(result, patterns.get(i),
                    typeNames.get(i), tokenMap);
            }
            return result;
        }

        String detokenize(String sessionId, String text) {
            if (text == null) return null;
            var tokenMap = sessionTokenMaps.get(sessionId);
            if (tokenMap == null || tokenMap.isEmpty()) return text;
            var result = text;
            var sorted = tokenMap.entrySet().stream()
                .sorted(Map.Entry.<String, String>comparingByKey().reversed())
                .toList();
            for (var entry : sorted) {
                result = result.replace(entry.getKey(), entry.getValue());
            }
            sessionTokenMaps.remove(sessionId);
            return result;
        }

        private String replaceAndTokenize(String text, Pattern pattern,
                                           String type, Map<String, String> tokenMap) {
            var matcher = pattern.matcher(text);
            var sb = new StringBuffer();
            while (matcher.find()) {
                var match = matcher.group();
                var token = "[" + type + "_" + counter.incrementAndGet() + "]";
                tokenMap.put(token, match);
                matcher.appendReplacement(sb, token);
            }
            matcher.appendTail(sb);
            return sb.toString();
        }
    }
}
