package de.augmentia.strandsagents.examples.domain;

import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.features.tools.*;
import de.augmentia.strandsagents.features.tools.WebFetchTool;
import de.augmentia.strandsagents.features.tools.WebSearchTool;

import java.nio.file.Path;

public class SDLCPrompts {

    private static final String BASE_PROMPT = """
            Du bist der RePPIT-Architekt. Du arbeitest strikt phasenbasiert.
            Weiche NIEMALS von den Vorgaben der aktuellen Phase ab.
            Versuche nicht, Aufgaben zukünftiger Phasen vorwegzunehmen.
            """;

    public static final String RESEARCH_PROMPT = BASE_PROMPT + """
            PHASE: RESEARCH.
            Deine Aufgabe ist es, den Ist-Zustand des Projekts zu analysieren und relevante Informationen zu sammeln.
            Nutze die Tools `grep`, `find`, `read`, `ls`, `webSearch` und `webFetch`.
            Dokumentiere den Ist-Zustand mit exakten Dateipfaden und externen Informationen.
            KEINE Codeänderungen, KEINE Verbesserungsvorschläge.
            """;

    public static final String PROPOSAL_PROMPT = BASE_PROMPT + """
            PHASE: PROPOSAL.
            Deine Aufgabe ist es, 2-3 konkrete Lösungsansätze basierend auf dem Research zu erstellen.
            Zeige Vor- und Nachteile der einzelnen Ansätze auf.
            Nutze `webSearch` für Best Practices und Vergleiche, um deine Vorschläge zu untermauern.
            """;

    public static final String PLAN_PROMPT = BASE_PROMPT + """
            PHASE: PLAN.
            Deine Aufgabe ist es, einen präzisen Markdown-Entwicklungsplan (Blueprint) für den vom Nutzer ausgewählten Ansatz zu erstellen.
            Definiere die exakten Schritte, die zur Umsetzung notwendig sind.
            Ändere noch keinen Code.
            """;

    public static final String IMPLEMENT_PROMPT = BASE_PROMPT + """
            PHASE: IMPLEMENT (Fokus-Umsetzung & Verifizierung)
            ==================================================
            ZIEL: Setze den genehmigten Plan exakt in Quellcode um und stelle sicher, dass das Projekt baut.
            
            DEINE REGELN & ABLAUF:
            1. Ändere NUR Code, der im PLAN freigegeben wurde. Refaktoriere keine Fremdkomponenten nebenbei.
            2. Halte dich strikt an vorgefundene Codestile (Naming Conventions, Error-Handling).
            3. Sobald du deine Codeänderungen vorgenommen hast, MUSST du zwingend das `execute_command`-Tool aufrufen, um einen Build durchzuführen (z.B. ein Kompilier- oder Testkommando).
            
            AUTOMATISCHE FEHLERBEHEBUNG (SELF-HEALING LOOP):
            - Wenn der Build fehlschlägt (Compilerfehler, Syntaxfehler, etc.), analysiere die Fehlermeldung genau.
            - Korrigiere den Fehler umgehend mit `edit` oder `write` und starte den Build erneut.
            - Du hast für jeden auftretenden Fehler bis zu 3 Korrekturversuche (Retries).
            - Tritt nach einer Korrektur ein *neuer*, anderer Fehler auf, hast du für diesen neuen Fehler erneut bis zu 3 Versuche.
            - Beende die Phase erst und melde Vollzug, wenn der Build erfolgreich durchläuft ODER wenn du die 3 Versuche für einen Fehler ohne Erfolg ausgeschöpft hast (dokumentiere in dem Fall die Blocker).
            """;

    public static final String TEST_PROMPT = BASE_PROMPT + """
            PHASE: TEST & ARCHITECTURE REVIEW.
            Deine Aufgabe ist es, die Implementierung zu verifizieren und zu testen.
            Führe zwingend `execute_command` aus, um Compiler/Linter/Tests laufen zu lassen.
            Verifiziere das Ergebnis der Implementierung.
            Nutze `webSearch` für Fehlercodes oder Testframework-Dokumentation, falls nötig.
            """;

    public static String getSystemPrompt(SDLCWorkflowDemo.ReppitPhase phase) {
        return switch (phase) {
            case RESEARCH -> RESEARCH_PROMPT;
            case PROPOSAL -> PROPOSAL_PROMPT;
            case PLAN -> PLAN_PROMPT;
            case IMPLEMENT -> IMPLEMENT_PROMPT;
            case TEST -> TEST_PROMPT;
            default -> BASE_PROMPT; // Should not happen for COMPLETED
        };
    }

    public static ToolRegistry createPhaseToolRegistry(SDLCWorkflowDemo.ReppitPhase phase, Path workDir) {
        ToolRegistry registry = ToolRegistry.builder().workspace(workDir.toAbsolutePath()).build();

        // Instantiate tools once per call to ensure correct workDir context if it changes
        BashTool bashTool = new BashTool(workDir); // Keep bashTool for other uses if needed, but prefer CommandTool for builds
        CommandTool commandTool = new CommandTool(workDir);
        ReadTool readTool = new ReadTool(workDir);
        WriteTool writeTool = new WriteTool(workDir);
        EditTool editTool = new EditTool(workDir);
        GrepTool grepTool = new GrepTool(workDir);
        FindTool findTool = new FindTool(workDir);
        LsTool lsTool = new LsTool(workDir);
        TimeTool timeTool = new TimeTool();
        WebSearchTool webSearchTool = new WebSearchTool();
        WebFetchTool webFetchTool = new WebFetchTool();

        switch (phase) {
            case RESEARCH -> {
                registry.register(grepTool);
                registry.register(findTool);
                registry.register(readTool);
                registry.register(lsTool);
                registry.register(webSearchTool);
                registry.register(webFetchTool);
                registry.register(timeTool); // TimeTool is generally useful
            }
            case PROPOSAL -> {
                registry.register(readTool);
                registry.register(webSearchTool);
                registry.register(timeTool);
            }
            case PLAN -> {
                // In der PLAN-Phase werden absichtlich keine Werkzeuge registriert,
                // da der Agent hier nur den Plan erstellen soll, nicht ausführen.
                registry.register(timeTool); // Still useful for general context
            }
            case IMPLEMENT -> {
                registry.register(writeTool);
                registry.register(editTool);
                registry.register(readTool);
                registry.register(commandTool); // Use CommandTool for builds/tests
                registry.register(lsTool); // Useful for verifying changes
                registry.register(timeTool);
            }
            case TEST -> {
                registry.register(commandTool); // Use CommandTool for builds/tests
                registry.register(readTool);
                registry.register(webSearchTool);
                registry.register(lsTool); // Useful for checking test reports/logs
                registry.register(timeTool);
            }
            case COMPLETED -> {
                // No tools needed for completed phase
            }
        }
        return registry;
    }
}
