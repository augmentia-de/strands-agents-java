package com.strands.agents.mcp;

import java.io.*;
import java.util.concurrent.*;

public class StdioTransport implements McpTransport {

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
                return reader.readLine();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        try {
            return future.get(30, TimeUnit.SECONDS);
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
