package de.augmentia.strandsagents.core.tools;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DockerRunTool implements AgentTool<DockerRunTool.Params> {
    private static final Logger log = LoggerFactory.getLogger(DockerRunTool.class);
    private static final int MAX_LINES = 300;
    private static final int MAX_BYTES = 30_720;
    private static final String DEFAULT_IMAGE = "strands-runner:latest";

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
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var schema = mapper.createObjectNode();
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
        try {
            var timeoutMs = params.timeout() != null && params.timeout() > 0
                ? params.timeout() * 1000L : defaultTimeoutMs;

            var cmd = new ArrayList<String>();
            cmd.add("docker");
            cmd.add("run");
            cmd.add("--rm");
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
            var process = pb.start();

            var timer = new java.util.Timer();
            timer.schedule(new java.util.TimerTask() {
                @Override
                public void run() {
                    process.destroyForcibly();
                    try {
                        Runtime.getRuntime().exec(new String[]{
                            "docker", "stop", "-t", "2",
                            Long.toString(process.pid())
                        });
                    } catch (IOException ignored) {}
                }
            }, timeoutMs);

            var lines = new ArrayList<String>();
            var totalBytes = new long[]{0};
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                var line = reader.readLine();
                while (line != null && !abortFlag.get()) {
                    lines.add(line);
                    totalBytes[0] += line.length() + 1;
                    while (lines.size() > MAX_LINES || totalBytes[0] > MAX_BYTES * 2) {
                        totalBytes[0] -= lines.removeFirst().length() + 1;
                    }
                    if (onUpdate != null) {
                        onUpdate.accept(new ToolResult(List.of(new TextContent(String.join("\n", lines))), null));
                    }
                    line = reader.readLine();
                }
            }
            try (var reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                var line = reader.readLine();
                while (line != null) {
                    lines.add(line);
                    line = reader.readLine();
                }
            }

            timer.cancel();
            var exitCode = process.waitFor();
            var result = String.join("\n", lines);
            if (exitCode != 0) {
                result += "\n\nCommand exited with code " + exitCode;
                log.debug("Tool: run ERROR exitCode={}", exitCode);
                return ToolResult.error(result);
            }
            log.debug("Tool: run DONE exitCode={} lines={}", exitCode, lines.size());
            return ToolResult.success(trunc(result, 500));
        } catch (IOException | InterruptedException e) {
            if (abortFlag.get()) {
                log.debug("Tool: run ABORTED");
                throw new RuntimeException("Command aborted", e);
            }
            log.debug("Tool: run ERROR: {}", e.getMessage());
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private static String trunc(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "..." : s;
    }

    public record Params(String command, Integer timeout) {}
}
