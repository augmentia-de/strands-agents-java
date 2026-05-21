package de.augmentia.strandsagents.core;

import de.augmentia.strandsagents.core.model.tool.ToolExecutionResult;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import java.util.List;
import java.util.concurrent.*;

public class ToolExecutor {

    private static final ExecutorService VIRTUAL_EXECUTOR =
        Executors.newVirtualThreadPerTaskExecutor();

    private final long timeoutSeconds;

    public ToolExecutor() {
        this(30);
    }

    public ToolExecutor(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public List<ToolExecutionResult> executeAll(
            List<ToolExecutionRequest> requests,
            ToolRegistry registry) throws Exception {

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            List<StructuredTaskScope.Subtask<ToolExecutionResult>> subtasks = requests.stream()
                .map(req -> scope.fork(() -> executeSingle(req, registry)))
                .toList();

            scope.join();
            scope.throwIfFailed();

            return subtasks.stream()
                .map(StructuredTaskScope.Subtask::get)
                .toList();
        }
    }

    public ToolExecutionResult execute(ToolExecutionRequest request, ToolRegistry registry)
            throws Exception {
        return executeSingle(request, registry);
    }

    ToolExecutionResult executeSingle(ToolExecutionRequest request, ToolRegistry registry)
            throws Exception {

        var toolMethod = registry.get(request.name());

        var future = VIRTUAL_EXECUTOR.submit(() -> toolMethod.execute(request.arguments()));
        String result;
        try {
            result = future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return new ToolExecutionResult(
                request.id(), request.name(), "Timeout nach " + timeoutSeconds + "s", true);
        }

        return new ToolExecutionResult(
            request.id(), request.name(), result, false);
    }

    public void shutdown() {
        VIRTUAL_EXECUTOR.shutdown();
    }
}
