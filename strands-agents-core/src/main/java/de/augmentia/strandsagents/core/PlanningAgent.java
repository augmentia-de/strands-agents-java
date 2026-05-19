package de.augmentia.strandsagents.core;

import de.augmentia.strandsagents.core.model.agent.*;
import de.augmentia.strandsagents.core.model.agent.AgentPhase;
import de.augmentia.strandsagents.core.model.agent.AgentResult;
import de.augmentia.strandsagents.core.model.agent.ExecutionMetrics;
import de.augmentia.strandsagents.core.model.agent.StopReason;
import de.augmentia.strandsagents.core.model.event.AgentStateChangedEvent;
import de.augmentia.strandsagents.core.resilience.ResilienceConfig;
import dev.langchain4j.model.chat.ChatModel;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class PlanningAgent extends StrandsAgent {

    private static final int MAX_EXECUTION_ITERATIONS = 50;

    private final Planner planner;
    private AgentPhase phase = AgentPhase.IDLE;
    private int iterationCount = 0;
    private int revisionCount = 0;
    private final List<String> errorLog = new ArrayList<>();

    public PlanningAgent(ChatModel model, Planner planner) {
        super(model);
        this.planner = planner;
    }

    public PlanningAgent(ChatModel model, ToolRegistry toolRegistry, ToolExecutor toolExecutor,
                         Planner planner) {
        super(model, toolRegistry, toolExecutor);
        this.planner = planner;
    }

    public PlanningAgent(ChatModel model, ToolRegistry toolRegistry, ToolExecutor toolExecutor,
                         ConversationManager conversationManager, Planner planner) {
        super(model, toolRegistry, toolExecutor, conversationManager);
        this.planner = planner;
    }

    public PlanningAgent(ChatModel model, ToolRegistry toolRegistry, ToolExecutor toolExecutor,
                         ConversationManager conversationManager, SessionManager sessionManager,
                         ResilienceConfig resilienceConfig, Planner planner) {
        super(model, toolRegistry, toolExecutor, conversationManager, sessionManager, resilienceConfig);
        this.planner = planner;
    }

    public PlanningAgent(ChatModel model, ToolRegistry toolRegistry, ToolExecutor toolExecutor,
                         ConversationManager conversationManager, SessionManager sessionManager,
                         ResilienceConfig resilienceConfig, List<Plugin> plugins, Planner planner) {
        super(model, toolRegistry, toolExecutor, conversationManager, sessionManager, resilienceConfig, plugins);
        this.planner = planner;
    }

    @Override
    public AgentResult execute(String prompt) {
        return executePlanned(prompt);
    }

    public AgentResult executePlanned(String goal) {
        var start = Instant.now();
        phase = AgentPhase.PLANNING;
        iterationCount = 0;
        revisionCount = 0;
        errorLog.clear();

        fireStateChange(AgentPhase.IDLE, AgentPhase.PLANNING, goal);

        var availableTools = getToolRegistry().getToolNames().stream().toList();
        var plan = planner.createPlan(goal, availableTools);

        phase = AgentPhase.EXECUTING;
        fireStateChange(AgentPhase.PLANNING, AgentPhase.EXECUTING, goal);

        for (int i = 0; i < MAX_EXECUTION_ITERATIONS; i++) {
            if (plan.isComplete()) {
                break;
            }

            var stepResult = planner.executeStep(plan, plan.currentStep(), getToolExecutor(), getToolRegistry());

            if (stepResult.success()) {
                var updatedContext = new java.util.HashMap<>(plan.sharedContext());
                if (stepResult.artifacts() != null) {
                    updatedContext.putAll(stepResult.artifacts());
                }
                var step = plan.current();
                if (step != null) {
                    updatedContext.put(step.id() + ".output", stepResult.output());
                }
                plan = plan.withSharedContext(updatedContext).advanceStep();
            } else {
                phase = AgentPhase.REVISING;
                revisionCount++;
                fireStateChange(AgentPhase.EXECUTING, AgentPhase.REVISING, goal);

                errorLog.add("Schritt %s: %s".formatted(
                    plan.current() != null ? plan.current().id() : "?", stepResult.error()));

                if (revisionCount > planner.maxRevisions()) {
                    phase = AgentPhase.FAILED;
                    fireStateChange(AgentPhase.REVISING, AgentPhase.FAILED, goal);
                    var elapsed = Duration.between(start, Instant.now());
                    return new AgentResult(
                        getSessionId(),
                        "Planung fehlgeschlagen nach " + revisionCount + " Revisionen: " + stepResult.error(),
                        List.of(),
                        new ExecutionMetrics(elapsed.toMillis(), 0, 0, 0),
                        StopReason.ERROR
                    );
                }

                plan = planner.revise(plan, stepResult, "Bitte korrigieren: " + stepResult.error());

                phase = AgentPhase.EXECUTING;
                fireStateChange(AgentPhase.REVISING, AgentPhase.EXECUTING, goal);
            }

            iterationCount++;
        }

        var finalOutput = buildFinalOutput(plan);

        phase = AgentPhase.REVIEWING;
        fireStateChange(AgentPhase.EXECUTING, AgentPhase.REVIEWING, goal);

        boolean complete = planner.isComplete(plan, finalOutput);

        if (complete) {
            phase = AgentPhase.COMPLETED;
            fireStateChange(AgentPhase.REVIEWING, AgentPhase.COMPLETED, goal);
        } else {
            phase = AgentPhase.FAILED;
            fireStateChange(AgentPhase.REVIEWING, AgentPhase.FAILED, goal);
        }

        var elapsed = Duration.between(start, Instant.now());
        return new AgentResult(
            getSessionId(),
            finalOutput,
            List.of(),
            new ExecutionMetrics(elapsed.toMillis(), 0, 0, iterationCount),
            complete ? StopReason.COMPLETED : StopReason.ERROR
        );
    }

    public AgentPhase getPhase() {
        return phase;
    }

    public int getIterationCount() {
        return iterationCount;
    }

    public int getRevisionCount() {
        return revisionCount;
    }

    public List<String> getErrorLog() {
        return List.copyOf(errorLog);
    }

    public Planner getPlanner() {
        return planner;
    }

    private void fireStateChange(AgentPhase previous, AgentPhase current, String goal) {
        fire(new AgentStateChangedEvent(
            getSessionId(),
            Instant.now(),
            previous,
            current,
            goal,
            iterationCount,
            revisionCount
        ));
    }

    private String buildFinalOutput(Plan plan) {
        var sb = new StringBuilder();
        sb.append("## Planergebnis: ").append(plan.goal()).append("\n\n");

        for (int i = 0; i < plan.steps().size(); i++) {
            var step = plan.steps().get(i);
            var output = plan.sharedContext().get(step.id() + ".output");
            sb.append("### ").append(step.id()).append(": ").append(step.description()).append("\n");
            if (output != null) {
                sb.append(output).append("\n");
            } else {
                sb.append("_(nicht ausgeführt)_\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

}
