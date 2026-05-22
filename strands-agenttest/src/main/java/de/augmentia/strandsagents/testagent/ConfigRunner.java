package de.augmentia.strandsagents.testagent;

import de.augmentia.strandsagents.core.model.agent.AgentResult;
import de.augmentia.strandsagents.testagent.config.AgentFactory;
import de.augmentia.strandsagents.testagent.config.ConfigParser;
import de.augmentia.strandsagents.testagent.config.TestConfig;
import de.augmentia.strandsagents.testagent.engine.ResultValidator;
import de.augmentia.strandsagents.testagent.engine.VariantEngine;
import de.augmentia.strandsagents.testagent.report.ResultRecorder;
import de.augmentia.strandsagents.testagent.report.ResultReporter;
import java.nio.file.Path;

public class ConfigRunner {

    public static void main(String[] args) {
        var configFile = args.length > 0
            ? Path.of(args[0])
            : Path.of("strands-test/test-config.yaml");

        System.out.println("=== Strands Agent Test ===");
        System.out.println("Config: " + configFile.toAbsolutePath());
        System.out.println();

        int total = 0;
        int passed = 0;

        // Save initial config to reset after all variants are done
        TestConfig initialConfig;
        try {
            initialConfig = ConfigParser.fromYaml(configFile);
        } catch (Exception e) {
            System.err.println("Fehler beim Lesen der Config: "
                + e.getMessage());
            return;
        }
        if (initialConfig == null) {
            System.out.println("Keine Config gefunden – Abbruch.");
            return;
        }

        while (true) {
            TestConfig config;
            try {
                config = ConfigParser.fromYaml(configFile);
            } catch (Exception e) {
                System.err.println("Fehler beim Lesen der Config: "
                    + e.getMessage());
                break;
            }

            if (config == null) {
                System.out.println("Keine Config gefunden – Abbruch.");
                break;
            }

            var variant = config.run().variant();
            var label = config.run().label();
            System.out.println("--- Variant " + variant
                + ": " + label + " ---");

            // Agent bauen
            var agent = AgentFactory.fromConfig(config);

            // Ausführen
            var start = System.nanoTime();
            AgentResult result = null;
            Throwable error = null;
            try {
                result = agent.execute(config.testPrompt());
            } catch (Exception e) {
                error = e;
            }
            var durationMs = (System.nanoTime() - start) / 1_000_000;

            // Validieren
            var ok = result != null
                && new ResultValidator(config.asserts()).validate(result);

            // Aufzeichnen
            ResultRecorder.record(config, result, ok, durationMs, error);

            total++;
            if (ok) passed++;
            System.out.println("  → " + (ok ? "PASS" : "FAIL")
                + " (" + durationMs + "ms)"
                + (error != null ? " – " + error.getMessage() : ""));
            System.out.println();

            // Nächste Variante
            var nextOpt = VariantEngine.next(config);
            if (nextOpt.isEmpty()) {
                System.out.println("Alle Varianten durchlaufen.");
                // Config für nächsten Durchlauf zurücksetzen
                try {
                    ConfigParser.toYaml(initialConfig, configFile);
                    System.out.println("Config reset to variant 0.");
                } catch (Exception e) {
                    System.err.println("Fehler beim Zurücksetzen der Config: "
                        + e.getMessage());
                }
                break;
            }

            // Config überschreiben
            try {
                ConfigParser.toYaml(nextOpt.get(), configFile);
            } catch (Exception e) {
                System.err.println("Fehler beim Schreiben der Config: "
                    + e.getMessage());
                break;
            }
        }

        // Report
        var results = ResultRecorder.loadResults();
        ResultReporter.generate(results);

        System.out.println("=== Zusammenfassung: "
            + passed + "/" + total + " bestanden ===");

        // Exit-Code für CI
        System.exit(total > 0 && passed == total ? 0 : 1);
    }
}
