package de.augmentia.strandsagents.core.sessions;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.augmentia.strandsagents.model.agent.AgentState;
import de.augmentia.strandsagents.model.agent.AgentStatus;
import de.augmentia.strandsagents.model.message.Message;
import de.augmentia.strandsagents.model.session.Session;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public class JdbcSessionManager implements SessionManager {

    private final DataSource dataSource;
    private final ObjectMapper mapper;

    public JdbcSessionManager(DataSource dataSource) {
        this.dataSource = dataSource;
        this.mapper = createMapper();
        initSchema();
    }

    private void initSchema() {
        var sql = """
            CREATE TABLE IF NOT EXISTS sessions (
                id VARCHAR(64) PRIMARY KEY,
                agent_name VARCHAR(256) NOT NULL,
                messages_json CLOB,
                state_json CLOB,
                metadata_json CLOB,
                created_at TIMESTAMP NOT NULL,
                updated_at TIMESTAMP NOT NULL
            )
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Kann Sessions-Tabelle nicht erstellen", e);
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
        var sql = "SELECT * FROM sessions WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, sessionId);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Fehler beim Laden von Session " + sessionId, e);
        }
        return Optional.empty();
    }

    @Override
    public void saveSession(Session session) {
        var sql = """
            MERGE INTO sessions (id, agent_name, messages_json, state_json, metadata_json, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, session.sessionId());
            stmt.setString(2, session.agentName());
            try {
                stmt.setString(3, mapper.writerFor(new TypeReference<List<Message>>() {}).writeValueAsString(session.messages()));
                stmt.setString(4, mapper.writeValueAsString(session.state()));
                stmt.setString(5, mapper.writeValueAsString(session.metadata()));
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new RuntimeException("JSON-Serialisierung fehlgeschlagen", e);
            }
            stmt.setTimestamp(6, Timestamp.from(session.createdAt()));
            stmt.setTimestamp(7, Timestamp.from(Instant.now()));
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Fehler beim Speichern von Session " + session.sessionId(), e);
        }
    }

    @Override
    public void deleteSession(String sessionId) {
        var sql = "DELETE FROM sessions WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, sessionId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting session " + sessionId, e);
        }
    }

    @Override
    public List<Session> listSessions(String agentName) {
        var sql = "SELECT * FROM sessions WHERE agent_name = ? ORDER BY updated_at DESC";
        var sessions = new ArrayList<Session>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, agentName);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    sessions.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listing sessions for " + agentName, e);
        }
        return sessions;
    }

    @Override
    public List<Session> searchByMetadata(String key, String value) {
        var sql = "SELECT * FROM sessions WHERE metadata_json LIKE ?";
        var pattern = "%\"" + key + "\":\"" + value + "\"%";
        var sessions = new ArrayList<Session>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, pattern);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    sessions.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Fehler bei Metadaten-Suche", e);
        }
        return sessions;
    }

    private Session mapRow(ResultSet rs) throws SQLException {
        var sessionId = rs.getString("id");
        var agentName = rs.getString("agent_name");
        var messages = fromJson(rs.getString("messages_json"),
            new TypeReference<List<Message>>() {});
        var state = fromJson(rs.getString("state_json"), AgentState.class);
        var metadata = fromJson(rs.getString("metadata_json"),
            new TypeReference<Map<String, Object>>() {});
        var createdAt = rs.getTimestamp("created_at").toInstant();
        var updatedAt = rs.getTimestamp("updated_at").toInstant();
        return new Session(sessionId, agentName, messages, state, metadata, createdAt, updatedAt);
    }

    private <T> T fromJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            try {
                return type.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                return null;
            }
        }
        try {
            return mapper.readValue(json, type);
        } catch (Exception e) {
            throw new RuntimeException("JSON-Deserialisierung fehlgeschlagen", e);
        }
    }

    private <T> T fromJson(String json, TypeReference<T> typeRef) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(json, typeRef);
        } catch (Exception e) {
            throw new RuntimeException("JSON-Deserialisierung fehlgeschlagen", e);
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
