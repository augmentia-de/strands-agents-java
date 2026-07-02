package de.augmentia.strandsagents.examples.domain;

import de.augmentia.strandsagents.core.DefaultToolExecutor;
import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.StreamingAgent;
import de.augmentia.strandsagents.config.ModelFactory;
import de.augmentia.strandsagents.core.conversation.SlidingWindowConversationManager;
import de.augmentia.strandsagents.model.agent.AgentResult;
import de.augmentia.strandsagents.model.agent.AgentState;
import de.augmentia.strandsagents.model.agent.AgentStatus;
import de.augmentia.strandsagents.model.agent.StopReason;
import de.augmentia.strandsagents.model.session.Session;
import de.augmentia.strandsagents.core.sessions.FileSessionManager;
import de.augmentia.strandsagents.core.sessions.SessionManager;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;

public class SDLCWorkflowDemo {

    private static final Logger logger = LoggerFactory.getLogger(SDLCWorkflowDemo.class);

    private final StreamingAgent agent;
    private final String sessionId;
    private final Scanner scanner;
    private final SessionManager sessionManager;
    private final Path workDir; // Keep workDir to pass to SDLCPrompts

    public enum ReppitPhase {
        RESEARCH, PROPOSAL, PLAN, IMPLEMENT, TEST, COMPLETED
    }

    public SDLCWorkflowDemo(StreamingChatModel streamingModel, ChatModel syncFallback, String sessionId) {
        this(streamingModel, syncFallback, sessionId, Path.of("."));
    }

    public SDLCWorkflowDemo(StreamingChatModel streamingModel, ChatModel syncFallback, String sessionId, Path workDir) {
        this.sessionId = sessionId;
        this.scanner = new Scanner(System.in);
        this.workDir = workDir; // Store workDir

        this.sessionManager = new FileSessionManager(Path.of(".sessions"));
        logger.info("Initialized SessionManager with path: {}", Path.of(".sessions").toAbsolutePath());

        // Initialize agent with an empty registry; tools will be set dynamically per phase
        ToolRegistry registry = new ToolRegistry();
        this.agent = new StreamingAgent(streamingModel, registry, new DefaultToolExecutor(), new SlidingWindowConversationManager(8192), sessionManager);
        logger.info("StreamingAgent initialized for session: {}", sessionId);

        this.agent.addEventListener(event -> {
            if (event instanceof de.augmentia.strandsagents.model.event.ToolExecutionStartedEvent startEvent) {
                var tc = startEvent.toolCall();
                System.out.printf("\n🛠️  [Tool-Aufruf] Tool '%s' wird gestartet mit Parametern: %s\n", tc.toolName(), tc.arguments());
            } else if (event instanceof de.augmentia.strandsagents.model.event.ToolExecutionFinishedEvent finishEvent) {
                var res = finishEvent.result();
                if (res.isError()) {
                    System.err.printf("⚠️  [Tool-Ergebnis] Tool '%s' fehlgeschlagen. Fehler: %s\n", res.toolName(), res.result());
                } else {
                    String out = res.result();
                    if (out != null && out.length() > 250) {
                        out = out.substring(0, 250) + "... (gekürzt)";
                    }
                    System.out.printf("✅ [Tool-Ergebnis] Tool '%s' erfolgreich beendet. Ergebnis: %s\n", res.toolName(), out);
                }
            }
        });
    }

    public void runWorkflow(String initialUserGoal) {
        ReppitPhase currentPhase = ReppitPhase.RESEARCH;
        String currentInput = "Das Ziel des Projekts ist: " + initialUserGoal;
        logger.info("Starting workflow with initial goal: {}", initialUserGoal);

        Optional<Session> existingSession = sessionManager.loadSession(sessionId);
        if (existingSession.isPresent()) {
            Session session = existingSession.get();
            var metadata = session.metadata();
            if (metadata != null && metadata.containsKey("currentPhase")) {
                String persistedPhaseStr = (String) metadata.get("currentPhase");
                try {
                    ReppitPhase persistedPhase = ReppitPhase.valueOf(persistedPhaseStr);
                    if (persistedPhase != ReppitPhase.COMPLETED) {
                        System.out.printf("\n🔄 Eine bestehende Session für ID '%s' wurde gefunden (Letzte aktive Phase: %s).\n", sessionId, persistedPhase);
                        System.out.print("Möchten Sie diese Session fortsetzen? (j/n) [Standard: j]: ");
                        String response = scanner.nextLine().trim().toLowerCase();
                        if (response.isEmpty() || "j".equals(response) || "ja".equals(response) || "y".equals(response) || "yes".equals(response)) {
                            currentPhase = persistedPhase;
                            currentInput = "Fortsetzung des Workflows. Bitte fahre mit Phase " + currentPhase + " fort.";
                            logger.info("Resuming workflow from persisted phase: {}", currentPhase);
                        } else {
                            resetSessionData(currentPhase);
                        }
                    } else {
                        System.out.printf("\nDie Session für ID '%s' ist bereits abgeschlossen. Starte einen neuen Workflow.\n", sessionId);
                        resetSessionData(currentPhase);
                    }
                } catch (IllegalArgumentException e) {
                    logger.warn("Fehler beim Parsen der gespeicherten Phase '{}', beginne von vorne", persistedPhaseStr, e);
                }
            }
        } else {
            resetSessionData(currentPhase);
        }

        while (currentPhase != ReppitPhase.COMPLETED) {
            logger.info("=== [AKTUELLE PHASE: {}] ===", currentPhase);

            updateSessionPhase(currentPhase);

            boolean success = executePhaseWithStreaming(currentPhase, currentInput);

            if (!success && currentPhase == ReppitPhase.IMPLEMENT) {
                int implementRetries = 0;
                while (!success && implementRetries < 3) {
                    implementRetries++;
                    System.out.printf("\n⚙️ [Auto-Repair] Build fehlgeschlagen. Starte automatischen Behebungsversuch %d von 3...\n", implementRetries);

                    // Wir füttern den Agenten mit der Aufforderung, den Fehler im nächsten Durchlauf direkt zu fixen
                    currentInput = "Der letzte Build/Test-Aufruf schlug fehl. Analysiere den Fehlercode/Log aus dem vorherigen Tool-Output, behebe die Ursache in den betroffenen Dateien und führe den Build erneut aus.";
                    success = executePhaseWithStreaming(currentPhase, currentInput);
                }

                // Wenn es nach 3 automatischen Durchläufen immer noch hakt, geht es in das normale User-Feedback
                if (!success) {
                    System.out.println("\n⚠️ [Auto-Repair] 3 Versuche erschöpft. Der Code konnte nicht automatisch repariert werden.");
                }
            }


            if (!success) {
                System.out.println("\nWas möchten Sie tun?");
                System.out.println("  [1] Die aktuelle Phase erneut ausführen (Retry)");
                System.out.println("  [2] Feedback eingeben und erneut ausführen");
                System.out.println("  [3] Workflow abbrechen");
                System.out.print("Auswahl: ");
                String errorChoice = scanner.nextLine().trim();
                if ("1".equals(errorChoice)) {
                    currentInput = "Bitte versuche es erneut.";
                    continue;
                } else if ("2".equals(errorChoice)) {
                    System.out.print("Bitte geben Sie Ihr Feedback / Ihre Anweisungen ein: ");
                    String feedback = scanner.nextLine().trim();
                    currentInput = "Bitte führe die Phase erneut aus mit folgendem Feedback: " + feedback;
                    continue;
                } else {
                    System.out.println("Workflow abgebrochen.");
                    break;
                }
            }

            InteractionResult action = promptUserForFeedback(currentPhase);
            logger.debug("User interaction result: Type={}, TargetPhase={}, Feedback={}", action.type, action.targetPhase, action.feedback);

            switch (action.type) {
                case APPROVE:
                    if (currentPhase == ReppitPhase.PROPOSAL) {
                        String selectedApproach = promptUserForApproachSelection();
                        currentInput = "Ausgezeichnet. Fahre mit der nächsten Phase fort und berücksichtige dabei den ausgewählten Ansatz: " + selectedApproach;
                        logger.info("Proposal approved. User selected approach: {}", selectedApproach);
                    } else {
                        currentInput = "Ausgezeichnet. Fahre mit der nächsten Phase fort.";
                    }
                    currentPhase = getNextPhase(currentPhase);
                    logger.info("Phase approved. Moving to next phase: {}", currentPhase);
                    break;

                case REWORK_CURRENT:
                    logger.info("-> Generating rework for phase {}. Feedback: {}", currentPhase, action.feedback);
                    currentInput = "Bitte überarbeite die Ergebnisse dieser Phase basierend auf folgendem Feedback: " + action.feedback;
                    break;

                case JUMP_TO_PHASE:
                    currentPhase = action.targetPhase;
                    logger.info("<- Jumping back to phase: {}. Feedback: {}", currentPhase, action.feedback);
                    currentInput = "Wir springen zurück zu Phase " + currentPhase + ". Basierend auf folgendem Feedback müssen wir die Phase neu bewerten: " + action.feedback;
                    break;

                case COMMAND_CLEAR:
                    System.out.println("🧹 Lösche Historie dieser Session und starte Phase " + currentPhase + " neu...");
                    resetSessionData(currentPhase);
                    currentInput = "Die Historie wurde zurückgesetzt. Bitte starte die Analyse für Phase " + currentPhase + " komplett neu basierend auf der initialen Aufgabe: " + initialUserGoal;
                    break;
            }
        }

        updateSessionPhase(ReppitPhase.COMPLETED);
        logger.info("=== WORKFLOW ERFOLGREICH BEENDET ===");
    }

    private void resetSessionData(ReppitPhase targetPhase) {
        sessionManager.deleteSession(sessionId);
        var now = Instant.now();
        var state = new AgentState(sessionId, List.of(), Map.of(), AgentStatus.IDLE);
        var newSession = new Session(sessionId, "SDLCWorkflowDemo", List.of(), state, Map.of("currentPhase", targetPhase.name()), now, now);
        sessionManager.saveSession(newSession);
        logger.info("Session history cleared/reset for ID: {} and set to phase {}", sessionId, targetPhase);
    }

    private void updateSessionPhase(ReppitPhase phase) {
        sessionManager.loadSession(sessionId).ifPresent(session -> {
            Map<String, Object> newMetadata = new HashMap<>(session.metadata());
            newMetadata.put("currentPhase", phase.name());
            Session updated = new Session(
                    session.sessionId(),
                    session.agentName(),
                    session.messages(),
                    session.state(),
                    newMetadata,
                    session.createdAt(),
                    Instant.now()
            );
            sessionManager.saveSession(updated);
        });
    }

    private boolean executePhaseWithStreaming(ReppitPhase phase, String input) {
        logger.debug("Executing phase {} with input: {}", phase, input);

        // Dynamische Zuweisung der Werkzeug-Registrierung für absolute Rollenklarheit des Agenten
        agent.setToolRegistry(SDLCPrompts.createPhaseToolRegistry(phase, workDir));

        // System Prompt für diesen spezifischen Durchlauf injizieren
        String systemPrompt = SDLCPrompts.getSystemPrompt(phase);
        logger.debug("System prompt for phase {}: {}", phase, systemPrompt);
        agent.setSystemPrompt(systemPrompt);

        System.out.print("Agent denkt und schreibt: ");
        try {
            AgentResult result = agent.executeStreaming(sessionId, input, token -> {
                System.out.print(token);
                System.out.flush();
            });
            System.out.println();

            if (result != null && result.stopReason() == StopReason.ERROR) {
                System.err.println("\n❌ [Fehler] Ein Fehler ist bei der Agenten-Ausführung aufgetreten:");
                System.err.println(result.finalAnswer());
                return false;
            }
            return true;
        } catch (Exception e) {
            System.err.println("\n❌ [Fehler] Ein unerwarteter Ausnahmefehler ist aufgetreten: " + e.getMessage());
            logger.error("Agent execution failed in phase {}", phase, e);
            return false;
        }
    }

    private ReppitPhase getNextPhase(ReppitPhase current) {
        ReppitPhase next = switch (current) {
            case RESEARCH -> ReppitPhase.PROPOSAL;
            case PROPOSAL -> ReppitPhase.PLAN;
            case PLAN -> ReppitPhase.IMPLEMENT;
            case IMPLEMENT -> ReppitPhase.TEST;
            case TEST -> ReppitPhase.COMPLETED;
            default -> ReppitPhase.COMPLETED;
        };
        logger.debug("Getting next phase: Current={} -> Next={}", current, next);
        return next;
    }

    private ReppitPhase getPreviousPhase(ReppitPhase current) {
        ReppitPhase previous = switch (current) {
            case PROPOSAL -> ReppitPhase.RESEARCH;
            case PLAN -> ReppitPhase.PROPOSAL;
            case IMPLEMENT -> ReppitPhase.PLAN;
            case TEST -> ReppitPhase.IMPLEMENT;
            default -> current;
        };
        logger.debug("Getting previous phase: Current={} -> Previous={}", current, previous);
        return previous;
    }

    private InteractionResult promptUserForFeedback(ReppitPhase phase) {
        while (true) {
            System.out.println("\n-------------------------------------------------------");
            System.out.println("Was möchten Sie tun? (Eingabe von '/clear' setzt den Verlauf zurück)");
            System.out.println("  [1] Freigeben (Approve) und mit nächster Phase fortfahren");
            System.out.println("  [2] Nachbessern (Rework) in der aktuellen Phase");

            // Berechne alle Phasen, zu denen man zurückspringen kann
            List<ReppitPhase> dynamicPreviousPhases = new ArrayList<>();
            for (ReppitPhase p : ReppitPhase.values()) {
                if (p.ordinal() < phase.ordinal()) {
                    dynamicPreviousPhases.add(p);
                }
            }

            int menuIndex = 3;
            if (!dynamicPreviousPhases.isEmpty()) {
                System.out.println("  --- Rücksprung-Optionen ---");
                for (ReppitPhase prev : dynamicPreviousPhases) {
                    System.out.printf("  [%d] Zurückspringen zu Phase: %s\n", menuIndex, prev.name());
                    menuIndex++;
                }
            }

            System.out.print("Auswahl / Befehl: ");
            String choice = scanner.nextLine().trim();
            logger.debug("User choice for feedback: {}", choice);

            // 1. Slash-Commands prüfen
            if (choice.equalsIgnoreCase("/clear")) {
                return new InteractionResult(InteractionType.COMMAND_CLEAR, null, null);
            }

            // 2. Menü-Zahlen prüfen
            if ("1".equals(choice)) {
                return new InteractionResult(InteractionType.APPROVE, null, null);
            } else if ("2".equals(choice)) {
                System.out.print("Bitte geben Sie Ihr Feedback / Ihre Änderungswünsche ein: ");
                String feedback = scanner.nextLine().trim();
                return new InteractionResult(InteractionType.REWORK_CURRENT, null, feedback);
            }

            try {
                int numericChoice = Integer.parseInt(choice);
                if (numericChoice >= 3 && numericChoice < menuIndex) {
                    // Mappe den Index zurück auf das Element aus dynamicPreviousPhases
                    ReppitPhase target = dynamicPreviousPhases.get(numericChoice - 3);
                    System.out.printf("Bitte geben Sie Ihr Feedback für den Rückschritt zu %s ein: ", target.name());
                    String feedback = scanner.nextLine().trim();
                    return new InteractionResult(InteractionType.JUMP_TO_PHASE, target, feedback);
                }
            } catch (NumberFormatException e) {
                // Keine Zahl eingegeben und kein bekannter Command
            }

            System.out.println("Ungültige Auswahl oder unbekannter Befehl. Bitte wählen Sie eine Option oder nutzen Sie /clear.");
        }
    }

    private String promptUserForApproachSelection() {
        while (true) {
            System.out.println("\n-------------------------------------------------------");
            System.out.println("Bitte wählen Sie einen der vorgeschlagenen Ansätze für die PLAN-Phase:");
            System.out.println("  [A] Ansatz A – Leichtgewichtig / Lokale Umsetzung");
            System.out.println("  [B] Ansatz B – (Beispiel für weiteren Ansatz)");
            System.out.println("  [C] Ansatz C – (Beispiel für weiteren Ansatz)");
            System.out.print("Auswahl (A, B oder C): ");
            String choice = scanner.nextLine().trim().toUpperCase();
            if ("A".equals(choice) || "B".equals(choice) || "C".equals(choice)) {
                logger.info("User selected approach: {}", choice);
                return "Ansatz " + choice;
            } else {
                System.out.println("Ungültige Auswahl. Bitte wählen Sie A, B oder C.");
            }
        }
    }

    enum InteractionType { APPROVE, REWORK_CURRENT, JUMP_TO_PHASE, COMMAND_CLEAR }
    record InteractionResult(InteractionType type, ReppitPhase targetPhase, String feedback) {}

    public static void main(String[] args) {
        logger.info("Starting RePPIT-Streaming-Workflow Test environment...");

        StreamingChatModel streamingModel = ModelFactory.createOpenAiStreamingFromEnv(null);
        logger.debug("StreamingChatModel created.");

        String testSessionId = "session-reppit-test-001";
        logger.info("Using session ID: {}", testSessionId);

        Path workDir = Path.of("/home/torsten/dev/my-projects/strands-agents-java-1/strands-cli");
        if (args.length > 0) {
            workDir = Path.of(args[0]).toAbsolutePath();
            logger.info("Using custom working directory: {}", workDir);
        } else {
            logger.info("Using default working directory: {}", workDir.toAbsolutePath());
        }

        SDLCWorkflowDemo workflow = new SDLCWorkflowDemo(
                streamingModel,
                null,
                testSessionId,
                workDir
        );

        Scanner inputScanner = new Scanner(System.in);
        System.out.println("\n========================================================");
        System.out.println("  RePPIT SDLC Workflow");
        System.out.println("========================================================");
        System.out.print("Bitte beschreiben Sie die Aufgabe / das Ziel des Projekts:\n> ");
        String startAnforderung = inputScanner.nextLine().trim();
        if (startAnforderung.isEmpty()) {
            startAnforderung = "Implementiere ein neues 'status'-Kommando für die Strands CLI, das den aktuellen Status der Agenten-Verbindung anzeigt.";
            logger.info("Kein Input angegeben – verwende Standard-Aufgabe: {}", startAnforderung);
        }
        workflow.runWorkflow(startAnforderung);
        logger.info("Workflow run completed.");
    }
}