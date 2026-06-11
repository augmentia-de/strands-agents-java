package de.augmentia.strandsagents.features.tools;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import de.augmentia.strandsagents.features.tools.AgentTool;
import de.augmentia.strandsagents.features.tools.TextContent;
import de.augmentia.strandsagents.features.tools.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BashTool implements AgentTool<BashTool.Params> {
    private static final Logger log = LoggerFactory.getLogger(BashTool.class);
    private static final int MAX_LINES = 300;
    private static final int MAX_BYTES = 30_720;
    private final Path cwd;

    public BashTool(Path cwd) {
        this.cwd = cwd;
    }

    @Override
    public String name() {
        return "bash";
    }

    @Override
    public String description() {
        return "Execute a bash command. Output truncated to last " + MAX_LINES + " lines. "
                + "Note: this tool is NOT restricted to the workspace directory. "
                + "The command runs in the workspace directory but can access any path the process has permissions for.";
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
        addStr(props, "command", "Bash command to execute");
        addInt(props, "timeout", "Timeout in seconds (optional)");
        schema.putArray("required").add("command");
        return schema;
    }

    private void addStr(ObjectNode p, String n, String d) {
        var node = p.putObject(n);
        node.put("type", "string");
        node.put("description", d);
    }

    private void addInt(ObjectNode p, String n, String d) {
        var node = p.putObject(n);
        node.put("type", "integer");
        node.put("description", d);
    }

    private static String trunc(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "..." : s;
    }

    @Override
    public ToolResult execute(String toolCallId, Params params, AtomicBoolean abortFlag, Consumer<ToolResult> onUpdate) {
        log.debug("Tool: bash START command={}", trunc(params.command(), 500));

        Process process = null;
        try {
            var shell = System.getenv().getOrDefault("SHELL", "/bin/bash");
            var pb = new ProcessBuilder(shell, "-c", params.command());
            pb.directory(cwd.toFile());

            // Redirect error stream into standard output to prevent OS pipe buffer deadlocks
            pb.redirectErrorStream(true);

            process = pb.start();

            var lines = new ArrayList<String>();
            long totalBytes = 0;

            long startTime = System.currentTimeMillis();
            long timeoutMillis = (params.timeout() != null && params.timeout() > 0) ? params.timeout() * 1000L : Long.MAX_VALUE;

            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                while (process.isAlive() || reader.ready()) {
                    // Check abort flag from agent orchestration layer
                    if (abortFlag.get()) {
                        log.debug("Tool: bash abort flag detected during execution.");
                        process.destroyForcibly();
                        throw new InterruptedException("Command aborted by agent");
                    }

                    // Check timeout manually while parsing long loops or slow streams
                    if (System.currentTimeMillis() - startTime > timeoutMillis) {
                        process.destroyForcibly();
                        throw new RuntimeException("Command timed out after " + params.timeout() + " seconds");
                    }

                    // Non-blocking loop read assistance via line checking when ready
                    if (reader.ready()) {
                        String line = reader.readLine();
                        if (line != null) {
                            lines.add(line);
                            totalBytes += line.length() + 1;

                            // Trim output windows to match max metrics gracefully
                            while (lines.size() > MAX_LINES || totalBytes > MAX_BYTES * 2) {
                                if (!lines.isEmpty()) {
                                    totalBytes -= lines.removeFirst().length() + 1;
                                } else {
                                    break;
                                }
                            }

                            if (onUpdate != null) {
                                onUpdate.accept(new ToolResult(List.of(new TextContent(String.join("\n", lines))), null));
                            }
                        }
                    } else {
                        // Small cooling sleep to keep CPU cycles relaxed during quiet streams
                        Thread.sleep(10);
                    }
                }
            }

            // Await final exit status clearance
            int exitCode = process.waitFor();
            var result = String.join("\n", lines);

            if (exitCode != 0) {
                result += "\n\nCommand exited with code " + exitCode;
                log.debug("Tool: bash ERROR exitCode={}", exitCode);
                throw new RuntimeException(result);
            }

            log.debug("Tool: bash DONE exitCode={} lines={}", exitCode, lines.size());
            return new ToolResult(List.of(new TextContent(trunc(result, 500))), null);

        } catch (IOException | InterruptedException e) {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt(); // Restore standard interrupt flag state
            }
            if (abortFlag.get()) {
                log.debug("Tool: bash ABORTED");
                throw new RuntimeException("Command aborted", e);
            }
            log.debug("Tool: bash ERROR: {}", e.getMessage());
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public record Params(String command, Integer timeout) {}
}