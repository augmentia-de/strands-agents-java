package com.strands.agents.mcp;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class SseTransport implements McpTransport {

    private final URI serverUri;
    private HttpURLConnection connection;
    private BufferedWriter writer;
    private boolean connected;
    private String sessionId;

    public SseTransport(URI serverUri) {
        this.serverUri = serverUri;
    }

    @Override
    public void connect() throws Exception {
        connection = (HttpURLConnection) serverUri.toURL().openConnection();
        connection.setDoOutput(true);
        connection.setDoInput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "text/event-stream");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);
        connection.connect();
        writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream()));
        this.connected = true;
    }

    @Override
    public String sendAndReceive(String message) throws Exception {
        if (!connected) throw new IllegalStateException("Transport not connected");

        writer.write(message);
        writer.newLine();
        writer.flush();

        try (var reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            var future = CompletableFuture.supplyAsync(() -> {
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            return line.substring(6);
                        }
                    }
                    return null;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });

            try {
                return future.get(30, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new RuntimeException("SSE transport timeout", e);
            }
        }
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void close() {
        this.connected = false;
        try { if (writer != null) writer.close(); } catch (Exception ignored) {}
        if (connection != null) connection.disconnect();
    }
}
