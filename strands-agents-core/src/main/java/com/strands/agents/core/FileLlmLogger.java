package com.strands.agents.core;

import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class FileLlmLogger implements AutoCloseable {

    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    private final FileChannel channel;
    private final Path path;
    private long callIndex;

    public FileLlmLogger(Path path) {
        this.path = path;
        try {
            var dir = path.getParent();
            if (dir != null && !java.nio.file.Files.exists(dir))
                java.nio.file.Files.createDirectories(dir);
            this.channel = FileChannel.open(path,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND);
            write("=== LLM-Call-Log gestartet: " + now() + " ===\n");
        } catch (IOException e) {
            throw new RuntimeException("Failed to open LLM log: " + path, e);
        }
    }

    public synchronized void log(ChatRequest request, ChatResponse response, long durationMs) {
        callIndex++;
        var sb = new StringBuilder();
        sb.append("--- Call #").append(callIndex)
            .append(" (").append(now()).append(", ").append(durationMs).append("ms) ---\n");
        sb.append("REQUEST:\n");
        for (var msg : request.messages()) {
            var role = msg.type() != null ? msg.type().name() : "UNKNOWN";
            var text = switch (msg) {
                case dev.langchain4j.data.message.UserMessage um -> um.singleText();
                case dev.langchain4j.data.message.AiMessage am ->
                    am.text() != null ? am.text() : "(tool-request: " + am.toolExecutionRequests() + ")";
                case dev.langchain4j.data.message.SystemMessage sm -> sm.text();
                case dev.langchain4j.data.message.ToolExecutionResultMessage tr ->
                    "[tool-result] " + tr.toolName() + ": " + tr.text();
                default -> msg.toString();
            };
            sb.append("  [").append(role).append("] ").append(text).append("\n");
        }
        var ai = response.aiMessage();
        sb.append("RESPONSE:\n");
        sb.append("  finishReason=").append(response.finishReason())
            .append(", tokens=");
        if (response.tokenUsage() != null)
            sb.append(response.tokenUsage().inputTokenCount()).append(" in / ")
                .append(response.tokenUsage().outputTokenCount()).append(" out");
        else
            sb.append("?");
        sb.append("\n");
        if (ai.text() != null)
            sb.append("  text: ").append(ai.text()).append("\n");
        if (ai.toolExecutionRequests() != null && !ai.toolExecutionRequests().isEmpty()) {
            sb.append("  tools:\n");
            for (var req : ai.toolExecutionRequests()) {
                sb.append("    - ").append(req.name())
                    .append("(").append(req.arguments()).append(")\n");
            }
        }
        sb.append("\n");
        write(sb.toString());
    }

    private void write(String text) {
        try {
            var bytes = text.getBytes(StandardCharsets.UTF_8);
            var buf = ByteBuffer.wrap(bytes);
            while (buf.hasRemaining())
                channel.write(buf);
            channel.force(false);
        } catch (IOException e) {
            System.err.println("LLM-Log write error: " + e.getMessage());
        }
    }

    private static String now() {
        return FMT.format(Instant.now());
    }

    @Override
    public synchronized void close() {
        try {
            write("=== LLM-Call-Log beendet: " + now() + " ===\n");
            channel.close();
        } catch (IOException e) {
            System.err.println("LLM-Log close error: " + e.getMessage());
        }
    }

    public Path path() {
        return path;
    }
}
