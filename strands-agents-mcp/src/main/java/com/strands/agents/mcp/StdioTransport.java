package com.strands.agents.mcp;

import java.io.*;
import java.util.concurrent.*;
import org.slf4j.*;

public class StdioTransport implements McpTransport {

    private static final Logger log = LoggerFactory.getLogger(StdioTransport.class);

    private final ProcessBuilder processBuilder;
    private Process process;
    private BufferedReader reader;
    private BufferedWriter writer;
    private boolean connected;

    public StdioTransport(String... command) {
        this.processBuilder = new ProcessBuilder(command);
        processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
    }

    @Override
    public void connect() throws Exception {
        this.process = processBuilder.start();
        this.reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
        this.connected = true;
    }

    @Override
    public String sendAndReceive(String message) throws Exception {
        if (!connected) throw new IllegalStateException("Transport not connected");

        writer.write(message);
        writer.newLine();
        writer.flush();

        var future = CompletableFuture.supplyAsync(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    var trimmed = line.strip();
                    if (trimmed.startsWith("{")) {
                        return trimmed;
                    }
                }
                return null;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        try {
            var result = future.get(30, TimeUnit.SECONDS);
            if (result == null) {
                throw new RuntimeException("MCP server closed connection");
            }
            return result;
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new RuntimeException("MCP transport timeout", e);
        }
    }

    @Override
    public boolean isConnected() {
        return connected && process != null && process.isAlive();
    }

    @Override
    public void close() {
        this.connected = false;
        try { if (writer != null) writer.close(); } catch (Exception ignored) {}
        try { if (reader != null) reader.close(); } catch (Exception ignored) {}
        if (process != null) process.destroyForcibly();
    }
}
