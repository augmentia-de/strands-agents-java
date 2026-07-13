package de.augmentia.strandsagents.tools.builtin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import de.augmentia.strandsagents.tools.AgentTool;
import de.augmentia.strandsagents.tools.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommandTool implements AgentTool<CommandTool.Params> {
    private static final Logger log = LoggerFactory.getLogger(CommandTool.class);
    private static final int MAX_LINES = 300;
    private static final int MAX_BYTES = 30_720;
    private static final ObjectMapper SCHEMA_MAPPER = new ObjectMapper();
    private final Path cwd;

    public CommandTool(Path cwd) {
        this.cwd = cwd;
    }

    @Override
    public String name() {
        return BaseToolNames.EXECUTE_COMMAND;
    }

    @Override
    public String description() {
        return "Execute a local system command (e.g., 'mvn clean', 'git status'). Output truncated to last " + MAX_LINES + " lines. "
                + "The command runs in the workspace directory but can access any path the process has permissions for.";
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
        addStr(props, "command", "The full command line to execute (e.g., 'mvn clean install')");
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
        log.debug("Tool: command START command={}", trunc(params.command(), 500));

        Process process = null;
        try {
            // ÄNDERUNG: Wir splitten den String in ein Array auf.
            // Aus "mvn clean install" wird ["mvn", "clean", "install"]
            String[] commandTokens = params.command().trim().split("\\s+");

            // Wenn der String leer war, werfen wir direkt einen Fehler
            if (commandTokens.length == 0 || commandTokens[0].isEmpty()) {
                throw new IllegalArgumentException("Command cannot be empty");
            }

            // Der ProcessBuilder erhält das Array direkt. Er sucht nun im System-PATH nach dem Programm (z.B. mvn)
            var pb = new ProcessBuilder(commandTokens);
            pb.directory(cwd.toFile());

            // Streams zusammenführen gegen Deadlocks
            pb.redirectErrorStream(true);

            process = pb.start();

            var lines = new ArrayList<String>();
            long totalBytes = 0;

            long startTime = System.currentTimeMillis();
            long timeoutMillis = (params.timeout() != null && params.timeout() > 0) ? params.timeout() * 1000L : Long.MAX_VALUE;

            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                while (process.isAlive() || reader.ready()) {
                    if (abortFlag.get()) {
                        log.debug("Tool: command abort flag detected.");
                        process.destroyForcibly();
                        throw new InterruptedException("Command aborted by agent");
                    }

                    if (System.currentTimeMillis() - startTime > timeoutMillis) {
                        process.destroyForcibly();
                        throw new RuntimeException("Command timed out after " + params.timeout() + " seconds");
                    }

                    if (reader.ready()) {
                        String line = reader.readLine();
                        if (line != null) {
                            lines.add(line);
                            totalBytes += line.length() + 1;

                            while (lines.size() > MAX_LINES || totalBytes > MAX_BYTES * 2) {
                                if (!lines.isEmpty()) {
                                    totalBytes -= lines.removeFirst().length() + 1;
                                } else {
                                    break;
                                }
                            }

                            if (onUpdate != null) {
                                onUpdate.accept(ToolResult.success(String.join("\n", lines)));
                            }
                        }
                    } else {
                        Thread.sleep(10);
                    }
                }
            }

            int exitCode = process.waitFor();
            var result = String.join("\n", lines);

            if (exitCode != 0) {
                result += "\n\nCommand exited with code " + exitCode;
                log.debug("Tool: command ERROR exitCode={}", exitCode);
                throw new RuntimeException(result);
            }

            log.debug("Tool: command DONE exitCode={} lines={}", exitCode, lines.size());
            return ToolResult.success(trunc(result, 500));

        } catch (IOException | InterruptedException e) {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (abortFlag.get()) {
                log.debug("Tool: command ABORTED");
                throw new RuntimeException("Command aborted", e);
            }
            log.debug("Tool: command ERROR: {}", e.getMessage());
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public record Params(String command, Integer timeout) {}
}