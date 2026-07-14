package de.augmentia.strandsagents.tools.builtin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import de.augmentia.strandsagents.tools.AgentTool;
import de.augmentia.strandsagents.tools.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes a bash command with output truncation and configurable timeout.
 */
public class BashTool implements AgentTool<BashTool.Params> {
    private static final Logger log = LoggerFactory.getLogger(BashTool.class);
    private static final int MAX_LINES = 300;
    private static final int MAX_BYTES = 30_720;
    private static final ObjectMapper SCHEMA_MAPPER = new ObjectMapper();
    private final Path cwd;

    public BashTool(Path cwd) {
        this.cwd = cwd;
    }

    @Override
    public String name() {
        return BaseToolNames.BASH;
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
        var schema = SCHEMA_MAPPER.createObjectNode();
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

    /**
     * Spawns a shell process, captures output with ring-buffer truncation, and supports abort/timeout.
     *
     * @param toolCallId unique identifier for this tool invocation
     * @param params     the bash parameters (command, timeout)
     * @param abortFlag  flag to signal premature cancellation
     * @param onUpdate   callback for streaming intermediate results
     * @return the tool result containing command output or an error
     */
    @Override
    public ToolResult execute(String toolCallId, Params params, AtomicBoolean abortFlag, Consumer<ToolResult> onUpdate) {
        log.debug("Tool: bash START command={}", trunc(params.command(), 500));

        Process process = null;
        try {
            var shell = System.getenv().getOrDefault("SHELL", "/bin/bash");
            var pb = new ProcessBuilder(shell, "-c", params.command());
            pb.directory(cwd.toFile());

            pb.redirectErrorStream(true);

            process = pb.start();

            final Process finalProcess = process;
            final Consumer<ToolResult> finalOnUpdate = onUpdate;
            var lines = new ArrayList<String>();
            var totalBytesHolder = new long[]{0};
            
            long startTime = System.currentTimeMillis();
            long timeoutMillis = (params.timeout() != null && params.timeout() > 0) ? params.timeout() * 1000L : Long.MAX_VALUE;

            var readDone = new CountDownLatch(1);
            Thread readThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try (var reader = new BufferedReader(new InputStreamReader(finalProcess.getInputStream()))) {
                        var line = reader.readLine();
                        while (line != null) {
                            synchronized (lines) {
                                lines.add(line);
                                totalBytesHolder[0] += line.length() + 1;
                                while (lines.size() > MAX_LINES || totalBytesHolder[0] > MAX_BYTES * 2) {
                                    totalBytesHolder[0] -= lines.removeFirst().length() + 1;
                                }
                            }
                            if (finalOnUpdate != null) {
                                finalOnUpdate.accept(ToolResult.success(String.join("\n", lines)));
                            }
                            if (Thread.currentThread().isInterrupted()) {
                                finalProcess.destroyForcibly();
                                break;
                            }
                            line = reader.readLine();
                        }
                    } catch (IOException ignored) {
                    } finally {
                        readDone.countDown();
                    }
                }
            }, "bash-read-" + toolCallId);
            readThread.setDaemon(true);
            readThread.start();

            while (!readDone.await(10, TimeUnit.MILLISECONDS)) {
                if (abortFlag.get()) {
                    log.debug("Tool: bash abort flag detected during execution.");
                    process.destroyForcibly();
                    readThread.interrupt();
                    throw new InterruptedException("Command aborted by agent");
                }
                if (System.currentTimeMillis() - startTime > timeoutMillis) {
                    process.destroyForcibly();
                    readThread.interrupt();
                    throw new RuntimeException("Command timed out after " + params.timeout() + " seconds");
                }
            }

            int exitCode = process.waitFor();
            var result = String.join("\n", lines);

            if (exitCode != 0 && !abortFlag.get()) {
                result += "\n\nCommand exited with code " + exitCode;
                log.debug("Tool: bash ERROR exitCode={}", exitCode);
                throw new RuntimeException(result);
            }

            log.debug("Tool: bash DONE exitCode={} lines={}", exitCode, lines.size());
            return ToolResult.success(trunc(result, 500));
        } catch (IOException | InterruptedException e) {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (abortFlag.get()) {
                log.debug("Tool: bash ABORTED");
                throw new RuntimeException("Command aborted", e);
            }
            log.debug("Tool: bash ERROR: {}", e.getMessage());
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * Parameters for executing a bash command: the command string and optional timeout in seconds.
     */
    public record Params(String command, Integer timeout) {}
}