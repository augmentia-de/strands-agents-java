package de.augmentia.strandsagents.sessions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.augmentia.strandsagents.core.model.agent.AgentState;
import de.augmentia.strandsagents.core.model.agent.AgentStatus;
import de.augmentia.strandsagents.core.model.session.Session;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class FileSessionManager implements SessionManager {

    private final Path baseDir;
    private final ObjectMapper mapper;

    public FileSessionManager(Path baseDir) {
        this.baseDir = baseDir;
        this.mapper = createMapper();
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new RuntimeException("Session-Verzeichnis kann nicht erstellt werden: " + baseDir, e);
        }
    }

    @Override
    public Session createSession(String agentName, Map<String, Object> metadata) {
        var now = Instant.now();
        var sessionId = UUID.randomUUID().toString();
        var state = new AgentState(sessionId, List.of(), Map.of(), AgentStatus.IDLE);
        var session = new Session(sessionId, agentName, List.of(), state, metadata, now, now);
        saveSession(session);
        return session;
    }

    @Override
    public Optional<Session> loadSession(String sessionId) {
        var file = resolve(sessionId);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            var session = mapper.readValue(file.toFile(), Session.class);
            return Optional.of(session);
        } catch (IOException e) {
            throw new RuntimeException("Fehler beim Lesen von Session " + sessionId, e);
        }
    }

    @Override
    public void saveSession(Session session) {
        var file = resolve(session.sessionId());
        try {
            writeWithLock(file, session);
        } catch (IOException e) {
            throw new RuntimeException("Fehler beim Schreiben von Session " + session.sessionId(), e);
        }
    }

    @Override
    public void deleteSession(String sessionId) {
        var file = resolve(sessionId);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new RuntimeException("Error deleting session " + sessionId, e);
        }
    }

    @Override
    public List<Session> listSessions(String agentName) {
        List<Session> results = new ArrayList<>();
        try (var files = Files.list(baseDir)) {
            files.filter(f -> f.toString().endsWith(".json"))
                .forEach(f -> {
                    try {
                        var session = mapper.readValue(f.toFile(), Session.class);
                        if (session.agentName().equals(agentName)) {
                            results.add(session);
                        }
                    } catch (IOException ignored) {
                    }
                });
        } catch (IOException ignored) {
        }
        return results;
    }

    @Override
    public List<Session> searchByMetadata(String key, String value) {
        List<Session> results = new ArrayList<>();
        try (var files = Files.list(baseDir)) {
            files.filter(f -> f.toString().endsWith(".json"))
                .forEach(f -> {
                    try {
                        var session = mapper.readValue(f.toFile(), Session.class);
                        var metaValue = session.metadata().get(key);
                        if (metaValue != null && metaValue.toString().equals(value)) {
                            results.add(session);
                        }
                    } catch (IOException ignored) {
                    }
                });
        } catch (IOException ignored) {
        }
        return results;
    }

    private Path resolve(String sessionId) {
        return baseDir.resolve(sanitize(sessionId) + ".json");
    }

    private String sanitize(String input) {
        return input.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private void writeWithLock(Path file, Session session) throws IOException {
        var parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);
        try (var raf = new RandomAccessFile(file.toFile(), "rw");
             var channel = raf.getChannel();
             FileLock lock = channel.lock()) {
            var json = mapper.writeValueAsString(session);
            raf.setLength(0);
            raf.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            raf.setLength(raf.getFilePointer());
        }
    }

    private static ObjectMapper createMapper() {
        var mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        return mapper;
    }

}
