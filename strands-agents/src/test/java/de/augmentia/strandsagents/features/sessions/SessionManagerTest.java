package de.augmentia.strandsagents.features.sessions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


import de.augmentia.strandsagents.core.ToolExecutor;
import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.Agent;
import de.augmentia.strandsagents.core.MockChatModel;
import de.augmentia.strandsagents.model.agent.AgentState;
import de.augmentia.strandsagents.model.agent.AgentStatus;
import de.augmentia.strandsagents.model.agent.StopReason;
import de.augmentia.strandsagents.model.message.Message;
import de.augmentia.strandsagents.model.message.UserMessage;
import de.augmentia.strandsagents.model.session.Session;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionManagerTest {

    // ── FileSessionManager ────────────────────────────────────────────

    @Test
    void fileSessionManagerCreateAndLoad(@TempDir Path tempDir) {
        var manager = new FileSessionManager(tempDir);

        var session = manager.createSession("test-agent", Map.of("user", "alice"));

        assertThat(session.sessionId()).isNotEmpty();
        assertThat(session.agentName()).isEqualTo("test-agent");
        assertThat(session.messages()).isEmpty();
        assertThat(session.metadata()).containsEntry("user", "alice");

        var loaded = manager.loadSession(session.sessionId());
        assertThat(loaded).isPresent();
        assertThat(loaded.get().sessionId()).isEqualTo(session.sessionId());
    }

    @Test
    void fileSessionManagerSaveAndLoadMessages(@TempDir Path tempDir) {
        var manager = new FileSessionManager(tempDir);
        var session = manager.createSession("test-agent", Map.of());

        var messages = List.<Message>of(
            new UserMessage("id-1", Instant.now(), "Hallo", Map.of()),
            new UserMessage("id-2", Instant.now(), "Wie geht es?", Map.of())
        );
        var updated = new Session(
            session.sessionId(), session.agentName(), messages,
            new AgentState(session.sessionId(), messages, Map.of(), AgentStatus.COMPLETED),
            session.metadata(), session.createdAt(), Instant.now()
        );
        manager.saveSession(updated);

        var loaded = manager.loadSession(session.sessionId());
        assertThat(loaded).isPresent();
        assertThat(loaded.get().messages()).hasSize(2);
        assertThat(loaded.get().messages().get(0).content()).isEqualTo("Hallo");
    }

    @Test
    void fileSessionManagerDeleteSession(@TempDir Path tempDir) {
        var manager = new FileSessionManager(tempDir);
        var session = manager.createSession("delete-me", Map.of());

        manager.deleteSession(session.sessionId());

        var loaded = manager.loadSession(session.sessionId());
        assertThat(loaded).isEmpty();
    }

    @Test
    void fileSessionManagerListSessions(@TempDir Path tempDir) {
        var manager = new FileSessionManager(tempDir);
        manager.createSession("agent-a", Map.of());
        manager.createSession("agent-a", Map.of());
        manager.createSession("agent-b", Map.of());

        var sessionsA = manager.listSessions("agent-a");
        assertThat(sessionsA).hasSize(2);

        var sessionsB = manager.listSessions("agent-b");
        assertThat(sessionsB).hasSize(1);
    }

    @Test
    void fileSessionManagerSearchByMetadata(@TempDir Path tempDir) {
        var manager = new FileSessionManager(tempDir);
        manager.createSession("agent-a", Map.of("env", "prod"));
        manager.createSession("agent-a", Map.of("env", "test"));

        var prodSessions = manager.searchByMetadata("env", "prod");
        assertThat(prodSessions).hasSize(1);
    }

    @Test
    void fileSessionManagerLoadUnknownSession(@TempDir Path tempDir) {
        var manager = new FileSessionManager(tempDir);
        var loaded = manager.loadSession("non-existent");
        assertThat(loaded).isEmpty();
    }

    @Test
    void fileSessionManagerPreservesMessagesAfterRestore(@TempDir Path tempDir) {
        var m1 = new FileSessionManager(tempDir);
        var session = m1.createSession("persist-test", Map.of());
        var messages = List.<Message>of(
            new UserMessage("u1", Instant.now(), "Frage 1", Map.of())
        );
        var updated = new Session(
            session.sessionId(), session.agentName(), messages,
            new AgentState(session.sessionId(), messages, Map.of(), AgentStatus.COMPLETED),
            session.metadata(), session.createdAt(), Instant.now()
        );
        m1.saveSession(updated);

        var m2 = new FileSessionManager(tempDir);
        var loaded = m2.loadSession(session.sessionId());
        assertThat(loaded).isPresent();
        assertThat(loaded.get().messages()).hasSize(1);
        assertThat(loaded.get().messages().get(0).content()).isEqualTo("Frage 1");
    }

    // ── JdbcSessionManager ────────────────────────────────────────────

    @Test
    void jdbcSessionManagerCreateAndLoad() throws Exception {
        var ds = simpleDataSource();
        var manager = new JdbcSessionManager(ds);

        var session = manager.createSession("test-agent", Map.of("env", "test"));

        assertThat(session.sessionId()).isNotEmpty();
        assertThat(session.agentName()).isEqualTo("test-agent");
        assertThat(session.metadata()).containsEntry("env", "test");

        var loaded = manager.loadSession(session.sessionId());
        assertThat(loaded).isPresent();
    }

    @Test
    void jdbcSessionManagerSaveAndLoadMessages() throws Exception {
        var ds = simpleDataSource();
        var manager = new JdbcSessionManager(ds);
        var session = manager.createSession("msg-test", Map.of());

        var messages = List.<Message>of(
            new UserMessage("u1", Instant.now(), "Erste Nachricht", Map.of())
        );
        var updated = new Session(
            session.sessionId(), session.agentName(), messages,
            new AgentState(session.sessionId(), messages, Map.of(), AgentStatus.COMPLETED),
            session.metadata(), session.createdAt(), Instant.now()
        );
        manager.saveSession(updated);

        var loaded = manager.loadSession(session.sessionId());
        assertThat(loaded).isPresent();
        assertThat(loaded.get().messages()).hasSize(1);
        assertThat(loaded.get().messages().get(0).content()).isEqualTo("Erste Nachricht");
    }

    @Test
    void jdbcSessionManagerDelete() throws Exception {
        var ds = simpleDataSource();
        var manager = new JdbcSessionManager(ds);
        var session = manager.createSession("delete-test", Map.of());

        manager.deleteSession(session.sessionId());

        var loaded = manager.loadSession(session.sessionId());
        assertThat(loaded).isEmpty();
    }

    @Test
    void jdbcSessionManagerListSessions() throws Exception {
        var ds = simpleDataSource();
        var manager = new JdbcSessionManager(ds);
        manager.createSession("list-agent", Map.of());
        manager.createSession("list-agent", Map.of());
        manager.createSession("other-agent", Map.of());

        assertThat(manager.listSessions("list-agent")).hasSize(2);
        assertThat(manager.listSessions("other-agent")).hasSize(1);
        assertThat(manager.listSessions("unknown")).isEmpty();
    }

    @Test
    void jdbcSessionManagerSearchByMetadata() throws Exception {
        var ds = simpleDataSource();
        var manager = new JdbcSessionManager(ds);
        manager.createSession("agent", Map.of("env", "prod", "lang", "de"));
        manager.createSession("agent", Map.of("env", "test", "lang", "de"));

        var prod = manager.searchByMetadata("env", "prod");
        assertThat(prod).hasSize(1);
    }

    @Test
    void jdbcSessionManagerLoadUnknown() throws Exception {
        var ds = simpleDataSource();
        var manager = new JdbcSessionManager(ds);
        assertThat(manager.loadSession("nix-da")).isEmpty();
    }

    // ── Integration: Agent with SessionManager ────────────────────────

    @Test
    void agentWithFileSessionManagerPersistsConversation(@TempDir Path tempDir) {
        var sessionManager = new FileSessionManager(tempDir);
        var agent = new Agent(
            new MockChatModel(),
            new ToolRegistry(),
            new ToolExecutor(),
            null,
            sessionManager
        );

        var session = sessionManager.createSession("agent-test", Map.of());
        var result1 = agent.execute(session.sessionId(), "Hallo Welt");
        var result2 = agent.execute(session.sessionId(), "Wie geht es dir?");

        assertThat(result1.finalAnswer()).isNotEmpty();
        assertThat(result2.finalAnswer()).isNotEmpty();

        var loaded = sessionManager.loadSession(session.sessionId());
        assertThat(loaded).isPresent();
        assertThat(loaded.get().messages()).hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    void agentWithJdbcSessionManagerPersistsConversation() throws Exception {
        var ds = simpleDataSource();
        var sessionManager = new JdbcSessionManager(ds);
        var agent = new Agent(
            new MockChatModel(),
            new ToolRegistry(),
            new ToolExecutor(),
            null,
            sessionManager
        );

        var session = sessionManager.createSession("agent-jdbc-test", Map.of());
        var result = agent.execute(session.sessionId(), "Testfrage");

        assertThat(result.finalAnswer()).isNotEmpty();

        var loaded = sessionManager.loadSession(session.sessionId());
        assertThat(loaded).isPresent();
        assertThat(loaded.get().messages()).isNotEmpty();
    }

    // ── InMemorySessionManager ──────────────────────────────────────

    @Test
    void inMemorySessionManagerCreateAndLoad() {
        var manager = new InMemorySessionManager();

        var session = manager.createSession("test-agent", Map.of("user", "alice"));

        assertThat(session.sessionId()).isNotEmpty();
        assertThat(session.agentName()).isEqualTo("test-agent");
        assertThat(session.messages()).isEmpty();
        assertThat(session.metadata()).containsEntry("user", "alice");

        var loaded = manager.loadSession(session.sessionId());
        assertThat(loaded).isPresent();
        assertThat(loaded.get().sessionId()).isEqualTo(session.sessionId());
    }

    @Test
    void inMemorySessionManagerSaveAndLoadMessages() {
        var manager = new InMemorySessionManager();
        var session = manager.createSession("test-agent", Map.of());

        var messages = List.<Message>of(
            new UserMessage("id-1", Instant.now(), "Hallo", Map.of()),
            new UserMessage("id-2", Instant.now(), "Wie geht es?", Map.of())
        );
        var updated = new Session(
            session.sessionId(), session.agentName(), messages,
            new AgentState(session.sessionId(), messages, Map.of(), AgentStatus.COMPLETED),
            session.metadata(), session.createdAt(), Instant.now()
        );
        manager.saveSession(updated);

        var loaded = manager.loadSession(session.sessionId());
        assertThat(loaded).isPresent();
        assertThat(loaded.get().messages()).hasSize(2);
        assertThat(loaded.get().messages().get(0).content()).isEqualTo("Hallo");
    }

    @Test
    void inMemorySessionManagerDeleteSession() {
        var manager = new InMemorySessionManager();
        var session = manager.createSession("delete-me", Map.of());

        manager.deleteSession(session.sessionId());

        var loaded = manager.loadSession(session.sessionId());
        assertThat(loaded).isEmpty();
    }

    @Test
    void inMemorySessionManagerListSessions() {
        var manager = new InMemorySessionManager();
        manager.createSession("agent-a", Map.of());
        manager.createSession("agent-a", Map.of());
        manager.createSession("agent-b", Map.of());

        var sessionsA = manager.listSessions("agent-a");
        assertThat(sessionsA).hasSize(2);

        var sessionsB = manager.listSessions("agent-b");
        assertThat(sessionsB).hasSize(1);

        assertThat(manager.listSessions(null)).hasSize(3);
    }

    @Test
    void inMemorySessionManagerSearchByMetadata() {
        var manager = new InMemorySessionManager();
        manager.createSession("agent-a", Map.of("env", "prod"));
        manager.createSession("agent-a", Map.of("env", "test"));

        var prodSessions = manager.searchByMetadata("env", "prod");
        assertThat(prodSessions).hasSize(1);
    }

    @Test
    void inMemorySessionManagerLoadUnknownSession() {
        var manager = new InMemorySessionManager();
        var loaded = manager.loadSession("non-existent");
        assertThat(loaded).isEmpty();
    }

    @Test
    void inMemorySessionManagerTwoAgentsShareSession() {
        var sessionManager = new InMemorySessionManager();
        var model = new MockChatModel();

        var agent1 = new Agent(model, new ToolRegistry(), new ToolExecutor(), null, sessionManager);
        var agent2 = new Agent(model, new ToolRegistry(), new ToolExecutor(), null, sessionManager);

        var session = sessionManager.createSession("shared-agent", Map.of());
        String sid = session.sessionId();

        agent1.execute(sid, "Hallo, ich bin Agent 1.");
        agent2.execute(sid, "Ich bin Agent 2. Was hat Agent 1 gesagt?");

        var loaded = sessionManager.loadSession(sid);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().messages()).hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    void agentWithInMemorySessionManagerPersistsConversation() {
        var sessionManager = new InMemorySessionManager();
        var agent = new Agent(
            new MockChatModel(),
            new ToolRegistry(),
            new ToolExecutor(),
            null,
            sessionManager
        );

        var session = sessionManager.createSession("agent-test", Map.of());
        var result1 = agent.execute(session.sessionId(), "Hallo Welt");
        var result2 = agent.execute(session.sessionId(), "Wie geht es dir?");

        assertThat(result1.finalAnswer()).isNotEmpty();
        assertThat(result2.finalAnswer()).isNotEmpty();

        var loaded = sessionManager.loadSession(session.sessionId());
        assertThat(loaded).isPresent();
        assertThat(loaded.get().messages()).hasSizeGreaterThanOrEqualTo(4);
    }

    // ── CachingSessionManager ───────────────────────────────────────

    @Test
    void cachingSessionManagerCreateAndLoad(@TempDir Path tempDir) {
        var persistent = new FileSessionManager(tempDir);
        var manager = new CachingSessionManager(persistent);

        var session = manager.createSession("test-agent", Map.of("user", "alice"));

        assertThat(session.sessionId()).isNotEmpty();
        assertThat(session.agentName()).isEqualTo("test-agent");

        var loaded = manager.loadSession(session.sessionId());
        assertThat(loaded).isPresent();
    }

    @Test
    void cachingSessionManagerCacheHitDoesNotTouchFile(@TempDir Path tempDir) {
        var persistent = new FileSessionManager(tempDir);
        var manager = new CachingSessionManager(persistent);

        var session = manager.createSession("cache-hit", Map.of());
        manager.saveSession(session);

        // Delete from persistent to detect if cache serves
        persistent.deleteSession(session.sessionId());

        var loaded = manager.loadSession(session.sessionId());
        assertThat(loaded).isPresent();
    }

    @Test
    void cachingSessionManagerCacheMissPopulates(@TempDir Path tempDir) {
        var persistent = new FileSessionManager(tempDir);
        var manager = new CachingSessionManager(persistent);

        var session = persistent.createSession("cache-miss", Map.of());

        var loaded = manager.loadSession(session.sessionId());
        assertThat(loaded).isPresent();

        // Now served from cache
        persistent.deleteSession(session.sessionId());
        assertThat(manager.loadSession(session.sessionId())).isPresent();
    }

    @Test
    void cachingSessionManagerDeletePropagates(@TempDir Path tempDir) {
        var persistent = new FileSessionManager(tempDir);
        var manager = new CachingSessionManager(persistent);

        var session = manager.createSession("delete-me", Map.of());
        manager.deleteSession(session.sessionId());

        assertThat(persistent.loadSession(session.sessionId())).isEmpty();
        assertThat(manager.loadSession(session.sessionId())).isEmpty();
    }

    @Test
    void cachingSessionManagerTwoAgentsShareSession(@TempDir Path tempDir) {
        var persistent = new FileSessionManager(tempDir);
        var sessionManager = new CachingSessionManager(persistent);
        var model = new MockChatModel();

        var agent1 = new Agent(model, new ToolRegistry(), new ToolExecutor(), null, sessionManager);
        var agent2 = new Agent(model, new ToolRegistry(), new ToolExecutor(), null, sessionManager);

        var session = sessionManager.createSession("shared-agent", Map.of());
        String sid = session.sessionId();

        agent1.execute(sid, "Hallo von Agent 1");
        agent2.execute(sid, "Hallo von Agent 2");

        var loaded = sessionManager.loadSession(sid);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().messages()).hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    void cachingSessionManagerAsyncWrite(@TempDir Path tempDir) throws Exception {
        var persistent = new FileSessionManager(tempDir);
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        var manager = new CachingSessionManager(persistent, executor);

        var session = manager.createSession("async-test", Map.of());
        manager.saveSession(session);
        executor.shutdown();
        java.util.concurrent.TimeUnit.SECONDS.sleep(1);

        assertThat(persistent.loadSession(session.sessionId())).isPresent();
    }

    // ── copySession (default method) ────────────────────────────────

    @Test
    void inMemoryCopySessionFull() {
        var manager = new InMemorySessionManager();
        var original = manager.createSession("source", Map.of("key", "val"));
        var messages = List.<Message>of(
            new UserMessage("u1", Instant.now(), "eins", Map.of()),
            new UserMessage("u2", Instant.now(), "zwei", Map.of())
        );
        var updated = new Session(original.sessionId(), original.agentName(), messages,
            new AgentState(original.sessionId(), messages, Map.of(), AgentStatus.COMPLETED),
            original.metadata(), original.createdAt(), Instant.now());
        manager.saveSession(updated);

        var copy = manager.copySession(original.sessionId(), "copy-agent", null);

        assertThat(copy.sessionId()).isNotEqualTo(original.sessionId());
        assertThat(copy.agentName()).isEqualTo("copy-agent");
        assertThat(copy.messages()).hasSize(2);
        assertThat(copy.messages().get(0).content()).isEqualTo("eins");
        assertThat(copy.metadata()).containsEntry("key", "val");
    }

    @Test
    void inMemoryCopySessionWithCount() {
        var manager = new InMemorySessionManager();
        var original = manager.createSession("source", Map.of());
        var messages = List.<Message>of(
            new UserMessage("u1", Instant.now(), "eins", Map.of()),
            new UserMessage("u2", Instant.now(), "zwei", Map.of()),
            new UserMessage("u3", Instant.now(), "drei", Map.of())
        );
        var updated = new Session(original.sessionId(), original.agentName(), messages,
            new AgentState(original.sessionId(), messages, Map.of(), AgentStatus.COMPLETED),
            original.metadata(), original.createdAt(), Instant.now());
        manager.saveSession(updated);

        var copy = manager.copySession(original.sessionId(), null, 2);

        assertThat(copy.messages()).hasSize(2);
        assertThat(copy.messages().get(0).content()).isEqualTo("eins");
        assertThat(copy.messages().get(1).content()).isEqualTo("zwei");
    }

    @Test
    void inMemoryCopySessionCountExceedsMessages() {
        var manager = new InMemorySessionManager();
        var original = manager.createSession("source", Map.of());
        var messages = List.<Message>of(
            new UserMessage("u1", Instant.now(), "eins", Map.of())
        );
        var updated = new Session(original.sessionId(), original.agentName(), messages,
            new AgentState(original.sessionId(), messages, Map.of(), AgentStatus.COMPLETED),
            original.metadata(), original.createdAt(), Instant.now());
        manager.saveSession(updated);

        var copy = manager.copySession(original.sessionId(), null, 99);

        assertThat(copy.messages()).hasSize(1);
    }

    @Test
    void inMemoryCopySessionUnknownSession() {
        var manager = new InMemorySessionManager();
        assertThatThrownBy(() -> manager.copySession("nix", null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fileCopySessionFull(@TempDir Path tempDir) {
        var manager = new FileSessionManager(tempDir);
        var original = manager.createSession("source", Map.of());
        var messages = List.<Message>of(
            new UserMessage("u1", Instant.now(), "aa", Map.of())
        );
        var updated = new Session(original.sessionId(), original.agentName(), messages,
            new AgentState(original.sessionId(), messages, Map.of(), AgentStatus.COMPLETED),
            original.metadata(), original.createdAt(), Instant.now());
        manager.saveSession(updated);

        var copy = manager.copySession(original.sessionId(), "copy", null);

        assertThat(copy.sessionId()).isNotEqualTo(original.sessionId());
        assertThat(copy.agentName()).isEqualTo("copy");
        assertThat(copy.messages()).hasSize(1);

        // Both persist
        assertThat(manager.loadSession(original.sessionId())).isPresent();
        assertThat(manager.loadSession(copy.sessionId())).isPresent();
    }

    @Test
    void cachingCopySessionStaysInCache(@TempDir Path tempDir) {
        var persistent = new FileSessionManager(tempDir);
        var manager = new CachingSessionManager(persistent);

        var original = manager.createSession("source", Map.of());
        var messages = List.<Message>of(
            new UserMessage("u1", Instant.now(), "eins", Map.of()),
            new UserMessage("u2", Instant.now(), "zwei", Map.of())
        );
        var updated = new Session(original.sessionId(), original.agentName(), messages,
            new AgentState(original.sessionId(), messages, Map.of(), AgentStatus.COMPLETED),
            original.metadata(), original.createdAt(), Instant.now());
        manager.saveSession(updated);

        var copy = manager.copySession(original.sessionId(), "cached-copy", 1);

        // Delete from persistent to prove cache serves it
        persistent.deleteSession(copy.sessionId());
        assertThat(manager.loadSession(copy.sessionId())).isPresent();
        assertThat(copy.messages()).hasSize(1);
        assertThat(copy.messages().get(0).content()).isEqualTo("eins");
    }

    @Test
    void agentWithoutSessionManagerStillWorks() {
        var agent = new Agent(
            new MockChatModel()
        );

        var result = agent.execute("Hallo");

        assertThat(result.finalAnswer()).isNotEmpty();
        assertThat(result.stopReason())
            .isEqualTo(StopReason.COMPLETED);
    }

    // ── Helper ────────────────────────────────────────────────────────

    private javax.sql.DataSource simpleDataSource() {
        var ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:testdb_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }
}
