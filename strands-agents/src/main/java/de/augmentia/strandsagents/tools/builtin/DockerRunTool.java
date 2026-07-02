package de.augmentia.strandsagents.tools.builtin;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import de.augmentia.strandsagents.tools.AgentTool;
import de.augmentia.strandsagents.tools.TextContent;
import de.augmentia.strandsagents.tools.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DockerRunTool implements AgentTool<DockerRunTool.Params> {
    private static final Logger log = LoggerFactory.getLogger(DockerRunTool.class);
    private static final int MAX_LINES = 300;
    private static final int MAX_BYTES = 30_720;
    private static final String DEFAULT_IMAGE = "strands-runner:latest";
    private static final ObjectMapper SCHEMA_MAPPER = new ObjectMapper();

    private final Path workspace;
    private final String memory;
    private final int defaultTimeoutMs;
    private final String image;

    public DockerRunTool(Path workspace) {
        this(workspace, "512m", 120_000, DEFAULT_IMAGE);
    }

    public DockerRunTool(Path workspace, String memory, int defaultTimeoutMs, String image) {
        this.workspace = workspace;
        this.memory = memory;
        this.defaultTimeoutMs = defaultTimeoutMs;
        this.image = image;
    }

    @Override
    public String name() {
        return "run";
    }

    @Override
    public String description() {
        return "Run a command inside a sandboxed Docker container. "
                + "The workspace directory is mounted at /workspace. "
                + "Has no network access and limited memory.";
    }

    @Override
    public Class<Params> parameterType() {
        return Params.class;
    }

    @Override
    public ObjectNode parameterSchema() {
        var schema = SCHEMA_MAPPER.createObjectNode();
        schema.put("type", "object");
        var props = schema.putObject("properties");
        var cmdProp = props.putObject("command");
        cmdProp.put("type", "string");
        cmdProp.put("description", "Command to execute inside the container");
        var tProp = props.putObject("timeout");
        tProp.put("type", "integer");
        tProp.put("description", "Timeout in seconds (optional)");
        schema.putArray("required").add("command");
        return schema;
    }

    @Override
    public ToolResult execute(String toolCallId, Params params, AtomicBoolean abortFlag, Consumer<ToolResult> onUpdate) {
        log.debug("Tool: run START command={}", trunc(params.command(), 500));

        // Generate a unique container name to allow safe external termination
        String containerName = "strands-agent-" + UUID.randomUUID();
        Process process = null;

        try {
            var timeoutMs = params.timeout() != null && params.timeout() > 0
                    ? params.timeout() * 1000L : defaultTimeoutMs;

            var cmd = new ArrayList<String>();
            cmd.add("docker");
            cmd.add("run");
            cmd.add("--rm");
            cmd.add("--name");
            cmd.add(containerName);
            cmd.add("-v");
            cmd.add(workspace.toAbsolutePath().toString() + ":/workspace");
            cmd.add("-w");
            cmd.add("/workspace");
            cmd.add("--network");
            cmd.add("none");
            cmd.add("--memory");
            cmd.add(memory);
            cmd.add(image);
            cmd.add("sh");
            cmd.add("-c");
            cmd.add(params.command());

            var pb = new ProcessBuilder(cmd);
            process = pb.start();

            // Thread-safe list and byte tracker for multi-threaded stream consumption
            List<String> lines = new CopyOnWriteArrayList<>();
            AtomicLong totalBytes = new AtomicLong(0);
            CountDownLatch streamsDone = new CountDownLatch(2);

            // 1. Consume STDOUT Concurrently
            Process finalProcess = process;
            Thread stdoutThread = new Thread(() -> {
                try (var reader = new BufferedReader(new InputStreamReader(finalProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.add(line);
                        long currentBytes = totalBytes.addAndGet(line.length() + 1);

                        // Manage ring buffer limits safely
                        while (lines.size() > MAX_LINES || currentBytes > MAX_BYTES * 2) {
                            if (!lines.isEmpty()) {
                                String removed = lines.remove(0);
                                currentBytes = totalBytes.addAndGet(-(removed.length() + 1));
                            } else {
                                break;
                            }
                        }
                        if (onUpdate != null) {
                            onUpdate.accept(new ToolResult(List.of(new TextContent(String.join("\n", lines))), null));
                        }
                    }
                } catch (IOException ignored) {
                } finally {
                    streamsDone.countDown();
                }
            }, "docker-stdout-" + toolCallId);

            // 2. Consume STDERR Concurrently
            Thread stderrThread = new Thread(() -> {
                try (var reader = new BufferedReader(new InputStreamReader(finalProcess.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.add("[stderr] " + line);
                    }
                } catch (IOException ignored) {
                } finally {
                    streamsDone.countDown();
                }
            }, "docker-stderr-" + toolCallId);

            stdoutThread.setDaemon(true);
            stderrThread.setDaemon(true);
            stdoutThread.start();
            stderrThread.start();

            // 3. Monitor execution loop (Checks for aborts and timeouts)
            long startTime = System.currentTimeMillis();
            boolean finished = false;

            while (!finished) {
                if (abortFlag.get()) {
                    log.debug("Tool: run abort flag detected during execution.");
                    cleanupContainer(process, containerName);
                    throw new InterruptedException("Command aborted by agent");
                }

                if (System.currentTimeMillis() - startTime > timeoutMs) {
                    cleanupContainer(process, containerName);
                    throw new RuntimeException("Command timed out after " + (timeoutMs / 1000) + " seconds");
                }

                // Check if process has finished naturally
                finished = streamsDone.await(50, TimeUnit.MILLISECONDS);
            }

            var exitCode = process.waitFor();
            var result = String.join("\n", lines);

            if (exitCode != 0 && !abortFlag.get()) {
                result += "\n\nCommand exited with code " + exitCode;
                log.debug("Tool: run ERROR exitCode={}", exitCode);
                return ToolResult.error(result);
            }

            log.debug("Tool: run DONE exitCode={} lines={}", exitCode, lines.size());
            return ToolResult.success(trunc(result, 500));

        } catch (IOException | InterruptedException e) {
            if (abortFlag.get()) {
                log.debug("Tool: run ABORTED");
                return ToolResult.error("Command aborted: " + params.command());
            }
            log.debug("Tool: run ERROR: {}", e.getMessage());
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void cleanupContainer(Process process, String containerName) {
        if (process != null) {
            process.destroyForcibly();
        }
        try {
            // Correctly stops the container via its assigned explicit name
            new ProcessBuilder("docker", "stop", "-t", "2", containerName).start();
        } catch (IOException e) {
            log.warn("Failed to stop docker container explicitly: {}", containerName, e);
        }
    }

    private static String trunc(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "..." : s;
    }

    public record Params(String command, Integer timeout) {}
}