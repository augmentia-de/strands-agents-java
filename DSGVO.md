# DSGVO/GDPR-Compliance-Modul für Strands Agents

## Architekturüberblick

Ein unabhängiges Maven-Modul `strands-agents-gdpr` stellt DSGVO-Funktionen bereit, die ohne Änderungen am Core in jede bestehende Augmentia-Installation integriert werden können.

```
strands-agents-java (Parent)
  ├── strands-agents                     ← Core (unverändert)
  ├── strands-agents-quarkus             ← Bestehende App
  ├── strands-agents-mcp-server          ← MCP-Adapter
  └── strands-agents-gdpr                ← NEU: optionales GDPR-Modul
        ├── pom.xml
        └── src/main/java/
            └── de/augmentia/strandsagents/extensions/gdpr/
                ├── GdprAgentPlugin.java          ← Plugin: Hooks + Tools registrieren
                ├── PiiAnonymizerHook.java        ← AgentHook: PII in Prompts maskieren
                ├── AuditTrailHook.java           ← AgentHook: Datenzugriffe protokollieren
                ├── tools/
                │   ├── GdprExportTool.java       ← AgentTool<P>: Session exportieren
                │   └── GdprDeleteTool.java       ← AgentTool<P>: Session löschen
                └── service/
                    └── GdprComplianceService.java ← CDI-Bean für REST-API
```

---

## 1. Schnittstellen des Core (unverändert nutzbar)

### AgentHook (`strands-agents/.../core/hook/AgentHook.java`)
6 Lebenszyklus-Punkte, alle mit `default`-Implementierung.

```java
public interface AgentHook {
    String name();

    default HookResult beforeAgent(HookContexts.BeforeAgentContext ctx) { ... }
    default HookResult afterAgent(HookContexts.AfterAgentContext ctx, String response) { ... }
    default HookResult beforeModelCall(HookContexts.BeforeModelCallContext ctx) { ... }
    default HookResult afterModelCall(HookContexts.AfterModelCallContext ctx, String llmResponse) { ... }
    default HookResult beforeToolCall(HookContexts.BeforeToolCallContext ctx) { ... }
    default HookResult afterToolCall(HookContexts.AfterToolCallContext ctx, String toolResult) { ... }
}
```

**Für DSGVO relevante Kontexte:**

```java
// beforeModelCall → PII-Filterung: messages sind mutable
record BeforeModelCallContext(
    String sessionId,
    StringBuilder systemPrompt,
    List<Message> messages,        // ← kann verändert werden
    List<ToolSpecification> tools
) {}

// afterToolCall → Audit-Log: alle Tool-Aufrufe sichtbar
record AfterToolCallContext(
    String sessionId,
    String toolName,
    String result,
    boolean isError
) {}

// afterAgent → Audit-Log: gesamte Agent-Antwort
record AfterAgentContext(
    String sessionId,
    AgentResult result
) {}
```

### HookResult – 4 mögliche Rückgabewerte

```java
public sealed interface HookResult
    permits HookResult.Continue, HookResult.Cancel, HookResult.Modify, HookResult.Retry {
    record Continue() implements HookResult {}                          // → weitermachen
    record Cancel(String reason) implements HookResult {}               // → abbrechen
    record Modify<T>(T value) implements HookResult {}                  // → Daten transformieren
    record Retry(String reason) implements HookResult {}                // → LLM-Call wiederholen
}
```

**Für DSGVO:**
- `Modify` → PII-maskierte Prompts an LLM weitergeben
- `Cancel` → Prompt mit personenbezogenen Daten blocken
- `Continue` → Audit-Log-Eintrag ohne Datenänderung

### Plugin (`strands-agents/.../core/plugin/Plugin.java`)
```java
public interface Plugin {
    String name();
    default void initAgent(Agent agent) {}
    default List<ToolRegistry.ToolMethod> getTools() { return List.of(); }
}
```

**Hinweis:** `Plugin` hat derzeit keinen `getHooks()`. Ein GDPR-Plugin registriert seine Hooks in `initAgent()` über `agent.addHook()`.

### SessionManager (`strands-agents/.../sessions/SessionManager.java`)
```java
public interface SessionManager {
    Session createSession(String agentName, Map<String, Object> metadata);
    Optional<Session> loadSession(String sessionId);
    void saveSession(Session session);
    void deleteSession(String sessionId);              // ← Art. 17 DSGVO
    List<Session> listSessions(String agentName);
    List<Session> searchByMetadata(String key, String value);
}
```

Bereits vorhanden: `deleteSession()` für Recht auf Löschung.

### Session (`strands-agents/.../core/model/session/Session.java`)
```java
public record Session(
    String sessionId,
    String agentName,
    List<Message> messages,
    AgentState state,
    Map<String, Object> metadata,
    Instant createdAt,
    Instant updatedAt
) {}
```

### AgentTool (`strands-agents/.../core/tools/AgentTool.java`)
```java
public interface AgentTool<P> {
    String name();
    String description();
    Class<P> parameterType();
    JsonNode parameterSchema();
    ToolResult execute(String toolCallId, P params, AtomicBoolean abortFlag,
                       Consumer<ToolResult> onUpdate) throws Exception;
}
```

---

## 2. Modul-Implementierung

### 2.1 pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>de.augmentia.strandsagents</groupId>
        <artifactId>strands-agents-java</artifactId>
        <version>0.1.1-SNAPSHOT</version>
    </parent>

    <artifactId>strands-agents-gdpr</artifactId>
    <version>0.1.1-SNAPSHOT</version>

    <dependencies>
        <!-- Core-Abhängigkeit -->
        <dependency>
            <groupId>de.augmentia.strandsagents</groupId>
            <artifactId>strands-agents</artifactId>
            <version>0.1.1-SNAPSHOT</version>
        </dependency>

        <!-- Quarkus (optional, nur für REST-API + CDI) -->
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-resteasy-jackson</artifactId>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-arc</artifactId>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</project>
```

### 2.2 PiiAnonymizerHook – PII-Filterung vor LLM-Call

```java
package de.augmentia.strandsagents.extensions.gdpr;

import de.augmentia.strandsagents.core.hook.*;
import de.augmentia.strandsagents.core.model.message.*;

import java.util.*;
import java.util.regex.Pattern;

public class PiiAnonymizerHook implements AgentHook {

    public enum MaskType { EMAIL, PHONE_NUMBER, NAME_DE, CREDIT_CARD, ADDRESS }
    public enum BlockAction { REDACT, THROW, MOCK }

    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE_PATTERN =
        Pattern.compile("(?:\\+49|0)[1-9][0-9\\.\\-\\s/]{6,20}");
    private static final Pattern CREDIT_CARD_PATTERN =
        Pattern.compile("\\b(?:\\d[ -]*?){13,16}\\b");
    private static final Pattern NAME_PATTERN_DE =
        Pattern.compile("\\b(?:Herr|Frau|Dr\\.?|Prof\\.?)\\s+[A-Z][a-zäöüß]+(?:\\s+[A-Z][a-zäöüß]+)*\\b");

    private final Set<MaskType> maskTypes;
    private final BlockAction blockAction;
    private final String replacement;

    public PiiAnonymizerHook(Set<MaskType> maskTypes, BlockAction blockAction, String replacement) {
        this.maskTypes = maskTypes;
        this.blockAction = blockAction;
        this.replacement = replacement;
    }

    @Override
    public String name() {
        return "gdpr-pii-anonymizer";
    }

    @Override
    public HookResult beforeModelCall(HookContexts.BeforeModelCallContext ctx) {
        var messages = ctx.messages();
        var modified = false;

        for (int i = 0; i < messages.size(); i++) {
            var msg = messages.get(i);
            var content = msg.text();
            if (content == null) continue;

            var sanitized = maskPii(content);
            if (!sanitized.equals(content)) {
                modified = true;
                messages.set(i, createMaskedMessage(msg, sanitized));
            }
        }

        if (!modified) {
            return new HookResult.Continue();
        }

        if (blockAction == BlockAction.THROW) {
            return new HookResult.Cancel(
                "Prompt enthält personenbezogene Daten: Anfrage blockiert");
        }

        // REDACT oder MOCK: veränderte Message-Liste zurückgeben
        // Da messages im BeforeModelCallContext bereits mutable sind (List<Message>),
        // werden sie direkt verändert. Rückgabe Continue reicht.
        return new HookResult.Continue();
    }

    private String maskPii(String text) {
        var result = text;
        if (maskTypes.contains(MaskType.EMAIL)) {
            result = EMAIL_PATTERN.matcher(result).replaceAll(replacement);
        }
        if (maskTypes.contains(MaskType.PHONE_NUMBER)) {
            result = PHONE_PATTERN.matcher(result).replaceAll(replacement);
        }
        if (maskTypes.contains(MaskType.CREDIT_CARD)) {
            result = CREDIT_CARD_PATTERN.matcher(result).replaceAll(replacement);
        }
        if (maskTypes.contains(MaskType.NAME_DE)) {
            result = NAME_PATTERN_DE.matcher(result).replaceAll(replacement);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private <T extends Message> T maskMessage(T original, String sanitized) {
        // Message-Typen sind Records → with-Syntax für Kopie mit geändertem content
        return switch (original) {
            case UserMessage m -> (T) new UserMessage(m.id(), m.timestamp(), sanitized, m.metadata());
            case SystemMessage m -> (T) new SystemMessage(m.id(), m.timestamp(), sanitized, m.metadata());
            case AssistantMessage m -> (T) new AssistantMessage(m.id(), m.timestamp(), sanitized, m.metadata(), m.toolCalls());
            default -> original;
        };
    }
}
```

**Integration in HookRegistry:**
- `beforeModelCall` wird im Agent aufgerufen, BEVOR die Messages an das LLM gehen (siehe `Agent.java` Zeile ~515)
- Die `List<Message> messages` im Context sind mutable → direkte Modifikation möglich
- `HookResult.Cancel` blockiert die Anfrage komplett (für BlockAction.THROW)
- `HookResult.Continue` lässt die (nun maskierten) Messages passieren

### 2.3 AuditTrailHook – Revisionssicheres Logging

```java
package de.augmentia.strandsagents.extensions.gdpr;

import de.augmentia.strandsagents.core.hook.*;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

public class AuditTrailHook implements AgentHook {

    public interface AuditStore {
        void write(AuditEntry entry);
        List<AuditEntry> findByUserId(String userId);
        List<AuditEntry> findBySessionId(String sessionId);
    }

    public record AuditEntry(
        String id,
        Instant timestamp,
        String sessionId,
        String userId,
        String action,         // "tool_call" | "agent_response" | "session_load" | "session_delete"
        String toolName,
        boolean isError,
        String hashPrevious,   // SHA-256 des vorherigen Eintrags (für Kette)
        String hashPayload     // SHA-256 des aktion-spezifischen Payloads (ohne Klartext)
    ) {}

    private final AuditStore store;
    private String lastHash = "";

    public AuditTrailHook(AuditStore store) {
        this.store = store;
    }

    @Override
    public String name() {
        return "gdpr-audit-trail";
    }

    private String sha256(String input) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(md.digest(input.getBytes()));
        } catch (Exception e) {
            return "ERR:" + e.getMessage();
        }
    }

    @Override
    public HookResult afterToolCall(HookContexts.AfterToolCallContext ctx, String result) {
        // Nur Metadaten loggen, nicht die sensiblen Inhalte
        var payload = ctx.toolName() + ":" + (ctx.isError() ? "ERROR" : "OK");
        var hash = sha256(payload);
        var entry = new AuditEntry(
            UUID.randomUUID().toString(),
            Instant.now(),
            ctx.sessionId(),
            extractUserId(ctx),        // aus sessionId oder Kontext
            "tool_call",
            ctx.toolName(),
            ctx.isError(),
            lastHash,
            hash
        );
        lastHash = hash;
        store.write(entry);
        return new HookResult.Continue();
    }

    private String extractUserId(HookContexts.AfterToolCallContext ctx) {
        // userId aus sessionId extrahieren (z.B. "user_42::session_abc" → "user_42")
        if (ctx.sessionId() != null && ctx.sessionId().contains("::")) {
            return ctx.sessionId().substring(0, ctx.sessionId().indexOf("::"));
        }
        return ctx.sessionId() != null ? ctx.sessionId() : "unknown";
    }
}
```

**Eigenschaften:**
- SHA-256-Ketten-Hash → Manipulationsnachweis (wenn Eintrag N nachträglich geändert, passt `hashPrevious` von N+1 nicht)
- Keine Klartext-Personendaten im Audit-Log
- Nur Hash der Aktion, nicht der Input-Daten
- `AuditStore` ist als Interface definiert → austauschbar (Datei, Datenbank, Kafka)

### 2.4 GdprExportTool – Datenübertragbarkeit (Art. 20 DSGVO)

```java
package de.augmentia.strandsagents.extensions.gdpr.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.tools.*;
import de.augmentia.strandsagents.sessions.SessionManager;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class GdprExportTool implements AgentTool<GdprExportTool.Params> {

    private final SessionManager sessionManager;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record Params(String sessionId) {}

    public GdprExportTool(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public String name() {
        return "gdpr_export";
    }

    @Override
    public String description() {
        return "Exportiert eine Session in maschinenlesbarem JSON-Format (Art. 20 DSGVO)";
    }

    @Override
    public Class<Params> parameterType() {
        return Params.class;
    }

    @Override
    public JsonNode parameterSchema() {
        var schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        var props = schema.putObject("properties");
        var sid = props.putObject("sessionId");
        sid.put("type", "string");
        sid.put("description", "ID der zu exportierenden Session");
        schema.putArray("required").add("sessionId");
        return schema;
    }

    @Override
    public ToolResult execute(String toolCallId, Params params,
                              AtomicBoolean abortFlag, Consumer<ToolResult> onUpdate) throws Exception {
        var session = sessionManager.loadSession(params.sessionId())
            .orElseThrow(() -> new RuntimeException("Session nicht gefunden: " + params.sessionId()));

        var export = MAPPER.createObjectNode();
        export.put("exportType", "GDPR_DATA_EXPORT");
        export.put("exportDate", java.time.Instant.now().toString());
        export.put("sessionId", session.sessionId());
        export.put("agentName", session.agentName());
        export.put("createdAt", session.createdAt().toString());
        export.put("updatedAt", session.updatedAt().toString());

        var messagesArray = export.putArray("messages");
        for (var msg : session.messages()) {
            var msgNode = messagesArray.addObject();
            msgNode.put("role", msg.getClass().getSimpleName().replace("Message", "").toLowerCase());
            msgNode.put("timestamp", msg.timestamp() != null ? msg.timestamp().toString() : null);
            msgNode.put("content", msg.content());
        }

        var metadata = export.putObject("metadata");
        session.metadata().forEach(metadata::put);

        var json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(export);
        return new ToolResult(List.of(new TextContent(json)),
            new ExportDetails(params.sessionId()));
    }

    public record ExportDetails(String sessionId) {}
}
```

### 2.5 GdprDeleteTool – Recht auf Löschung (Art. 17 DSGVO)

```java
package de.augmentia.strandsagents.extensions.gdpr.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.augmentia.strandsagents.core.tools.*;
import de.augmentia.strandsagents.sessions.SessionManager;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class GdprDeleteTool implements AgentTool<GdprDeleteTool.Params> {

    private final SessionManager sessionManager;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record Params(String sessionId, boolean cascade) {}

    public GdprDeleteTool(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public String name() {
        return "gdpr_delete";
    }

    @Override
    public String description() {
        return "Löscht eine Session und alle zugehörigen Daten (Art. 17 DSGVO)";
    }

    @Override
    public Class<Params> parameterType() {
        return Params.class;
    }

    @Override
    public JsonNode parameterSchema() {
        var schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        var props = schema.putObject("properties");
        var sid = props.putObject("sessionId");
        sid.put("type", "string");
        sid.put("description", "ID der zu löschenden Session");
        var cascade = props.putObject("cascade");
        cascade.put("type", "boolean");
        cascade.put("description", "Kaskadierend löschen (auch abhängige Daten)");
        schema.putArray("required").add("sessionId");
        return schema;
    }

    @Override
    public ToolResult execute(String toolCallId, Params params,
                              AtomicBoolean abortFlag, Consumer<ToolResult> onUpdate) throws Exception {
        if (params.sessionId() == null || params.sessionId().isBlank()) {
            throw new RuntimeException("sessionId darf nicht leer sein");
        }

        var session = sessionManager.loadSession(params.sessionId());
        if (session.isEmpty()) {
            return new ToolResult(List.of(new TextContent(
                "Session " + params.sessionId() + " nicht gefunden – nichts zu löschen")),
                new DeleteDetails(params.sessionId(), false));
        }

        sessionManager.deleteSession(params.sessionId());

        var result = new ObjectMapper().createObjectNode();
        result.put("action", "DELETE");
        result.put("sessionId", params.sessionId());
        result.put("cascade", params.cascade() != null && params.cascade());
        result.put("deletedAt", java.time.Instant.now().toString());
        result.put("status", "DELETED");

        return new ToolResult(List.of(new TextContent(result.toPrettyString())),
            new DeleteDetails(params.sessionId(), true));
    }

    public record DeleteDetails(String sessionId, boolean deleted) {}
}
```

### 2.6 GdprAgentPlugin – Zusammenfassung aller Hooks und Tools

```java
package de.augmentia.strandsagents.extensions.gdpr;

import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.agent.Agent;
import de.augmentia.strandsagents.core.plugin.Plugin;
import de.augmentia.strandsagents.extensions.gdpr.tools.*;
import de.augmentia.strandsagents.sessions.SessionManager;

import java.util.*;

public class GdprAgentPlugin implements Plugin {

    private final SessionManager sessionManager;
    private final Set<PiiAnonymizerHook.MaskType> maskTypes;
    private final PiiAnonymizerHook.BlockAction blockAction;
    private final String replacement;
    private final AuditTrailHook.AuditStore auditStore;

    public GdprAgentPlugin(SessionManager sessionManager,
                           Set<PiiAnonymizerHook.MaskType> maskTypes,
                           PiiAnonymizerHook.BlockAction blockAction,
                           String replacement,
                           AuditTrailHook.AuditStore auditStore) {
        this.sessionManager = sessionManager;
        this.maskTypes = maskTypes;
        this.blockAction = blockAction;
        this.replacement = replacement;
        this.auditStore = auditStore;
    }

    @Override
    public String name() {
        return "gdpr-compliance";
    }

    @Override
    public List<ToolRegistry.ToolMethod> getTools() {
        var tools = new ToolRegistry();
        tools.register(new GdprExportTool(sessionManager));
        tools.register(new GdprDeleteTool(sessionManager));
        return List.copyOf(tools.getTools().values());
    }

    @Override
    public void initAgent(Agent agent) {
        // Hooks registrieren
        agent.addHook(new PiiAnonymizerHook(maskTypes, blockAction, replacement));
        if (auditStore != null) {
            agent.addHook(new AuditTrailHook(auditStore));
        }
    }
}
```

### 2.7 GdprComplianceService – CDI-Bean mit REST-API

```java
package de.augmentia.strandsagents.extensions.gdpr.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import de.augmentia.strandsagents.sessions.SessionManager;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class GdprComplianceService {

    private final SessionManager sessionManager;
    private final ObjectMapper mapper = new ObjectMapper();

    @Inject
    public GdprComplianceService(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    /**
     * Löscht alle Sessions eines Users (kaskadierend).
     * @param userId Die User-ID (wird in session.metadata erwartet)
     * @return Anzahl gelöschter Sessions
     */
    public int deleteUserData(String userId) {
        var sessions = sessionManager.searchByMetadata("userId", userId);
        for (var session : sessions) {
            sessionManager.deleteSession(session.sessionId());
        }
        return sessions.size();
    }

    /**
     * Exportiert alle Daten eines Users als JSON.
     */
    public String exportUserData(String userId) {
        var sessions = sessionManager.searchByMetadata("userId", userId);
        var root = mapper.createObjectNode();
        root.put("exportType", "GDPR_DATA_EXPORT");
        root.put("exportDate", java.time.Instant.now().toString());
        root.put("userId", userId);
        var sessionsArray = root.putArray("sessions");
        for (var session : sessions) {
            var s = sessionsArray.addObject();
            s.put("sessionId", session.sessionId());
            s.put("agentName", session.agentName());
            s.put("createdAt", session.createdAt().toString());
            var msgArray = s.putArray("messages");
            for (var msg : session.messages()) {
                var m = msgArray.addObject();
                m.put("role", msg.getClass().getSimpleName().replace("Message", "").toLowerCase());
                m.put("content", msg.content());
            }
        }
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    }
}
```

**Hinweis:** `searchByMetadata("userId", ...)` setzt voraus, dass bei Session-Erstellung ein `userId`-Metadatum gespeichert wird:
```java
var session = sessionManager.createSession("agentName",
    Map.of("userId", authenticatedUserId));
```

### 2.8 GdprResource – REST-Endpunkte (Quarkus JAX-RS)

```java
package de.augmentia.strandsagents.extensions.gdpr.resources;

import de.augmentia.strandsagents.extensions.gdpr.service.GdprComplianceService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/v1/gdpr")
@Produces(MediaType.APPLICATION_JSON)
public class GdprResource {

    @Inject
    GdprComplianceService complianceService;

    @GET
    @Path("/export/{userId}")
    public Response exportUserData(@PathParam("userId") String userId) {
        var json = complianceService.exportUserData(userId);
        return Response.ok(json).build();
    }

    @DELETE
    @Path("/cleanup/{userId}")
    public Response deleteUserData(@PathParam("userId") String userId) {
        var deleted = complianceService.deleteUserData(userId);
        var result = new java.util.LinkedHashMap<String, Object>();
        result.put("action", "GDPR_DELETE");
        result.put("userId", userId);
        result.put("deletedSessions", deleted);
        result.put("timestamp", java.time.Instant.now().toString());
        return Response.ok(result).build();
    }
}
```

---

## 3. Integration in bestehende Applikation

### 3.1 In AgentService.java (Quarkus)

```java
// Bestehende imports erweitern:
import de.augmentia.strandsagents.extensions.gdpr.GdprAgentPlugin;
import de.augmentia.strandsagents.extensions.gdpr.PiiAnonymizerHook;
import de.augmentia.strandsagents.extensions.gdpr.AuditTrailHook;

// buildPlugins() erweitern:
private List<Plugin> buildPlugins(List<Skill> skills, List<String> initialSkills) {
    var plugins = new ArrayList<Plugin>();
    // ... bestehende plugins (skills, hitl, guardrail) ...

    // GDPR-Plugin (optional, per Konfiguration steuerbar)
    if ("true".equals(System.getProperty("strands.agent.gdpr.enabled", "false"))) {
        var maskTypes = Set.of(
            PiiAnonymizerHook.MaskType.EMAIL,
            PiiAnonymizerHook.MaskType.PHONE_NUMBER,
            PiiAnonymizerHook.MaskType.NAME_DE
        );
        var blockAction = PiiAnonymizerHook.BlockAction.REDACT;
        AuditTrailHook.AuditStore auditStore = new FileAuditStore(
            Path.of(System.getProperty("strands.agent.gdpr.audit-dir", "gdpr-audit")));
        plugins.add(new GdprAgentPlugin(sessionManager, maskTypes, blockAction,
            "[PII REDACTED]", auditStore));
    }
    return plugins;
}
```

### 3.2 In application.properties

```properties
# GDPR-Compliance aktivieren
strands.agent.gdpr.enabled=true

# PII-Maskierung
strands.agent.gdpr.pii-mask-types=EMAIL,PHONE_NUMBER,NAME_DE
strands.agent.gdpr.pii-block-action=REDACT
strands.agent.gdpr.pii-replacement=[PII REDACTED]

# Audit-Trail (Art. 5 Abs. 2 DSGVO – Rechenschaftspflicht)
strands.agent.gdpr.audit-dir=gdpr-audit

# Session-Metadaten: userId wird bei createSession() gesetzt
strands.agent.gdpr.user-id-header=X-User-ID
```

### 3.3 In strands-agents-quarkus/pom.xml

```xml
<dependency>
    <groupId>de.augmentia.strandsagents</groupId>
    <artifactId>strands-agents-gdpr</artifactId>
    <version>0.1.1-SNAPSHOT</version>
</dependency>
```

---

## 4. DSGVO-Anforderungs-Matrix

| Art. | Anforderung | Implementierung | Ort |
|------|------------|----------------|-----|
| Art. 5 Abs. 1c | Datenminimierung | PiiAnonymizerHook maskiert PII vor LLM-Call | `beforeModelCall` |
| Art. 5 Abs. 2 | Rechenschaftspflicht | AuditTrailHook mit SHA-256-Kette | `afterToolCall`, `afterAgent` |
| Art. 15 | Auskunftsrecht | GdprComplianceService.exportUserData() | REST + Tool |
| Art. 17 | Recht auf Löschung | GdprDeleteTool + sessionManager.deleteSession() | Tool + REST |
| Art. 20 | Datenübertragbarkeit | GdprExportTool + GdprComplianceService | Tool + REST |
| Art. 25 | Privacy by Design | PII-Filter als Hook → keine Core-Änderung | `beforeModelCall` |
| Art. 32 | Sicherheit der Verarbeitung | Keine Klartext-PII im Audit-Log, Hash-Kette | AuditTrailHook |
| Art. 33 | Meldung von Verletzungen | AuditTrailHook ermöglicht nachträgliche Analyse | AuditStore |

---

## 5. Optionale Core-Verbesserungen (nicht notwendig, aber nützlich)

Diese Änderungen am Core sind **nicht erforderlich**, würden die Integration aber eleganter machen:

| Änderung | Nutzen | Aufwand |
|---------|--------|---------|
| `Plugin.getHooks()` Default-Methode hinzufügen | Plugin kann Hooks direkt liefern statt über `initAgent()` | 1 Zeile |
| `SessionManager.searchByUserId(String userId)` | Typsichere Usersuche statt `searchByMetadata` | 1 Interface + 2 Impls |
| `SessionManager.exportSession(String sessionId)` | Standardisierter Export | 1 Interface + 2 Impls |
| YAML-Konfiguration für GDPR-Plugin | Deklarative Aktivierung | Config-Parser + Builder |

---

## 6. Fazit

Das GDPR-Modul `strands-agents-gdpr` ist ein **unabhängiges Maven-Modul** ohne Änderungen am Core:

- **3 Dateien** für den Core-Mechanismus (Plugin + 2 Hooks)
- **2 Tools** (Export + Delete) als `AgentTool<P>` implementiert
- **1 CDI-Service** für REST-API
- **1 REST-Resource** für Verwaltungsendpunkte
- **1 pom.xml** (abhängig von `strands-agents`)

Integration per:
1. Dependency in `strands-agents-quarkus/pom.xml`
2. 5 Zeilen Konfiguration in `application.properties`
3. ~15 Zeilen in `AgentService.buildPlugins()`

Das Modul folgt exakt den existierenden Patterns (`GuardrailPlugin`, `AgentSkillsPlugin`, `LoggingHook`).
