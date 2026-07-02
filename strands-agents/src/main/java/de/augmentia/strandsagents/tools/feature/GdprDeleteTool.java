package de.augmentia.strandsagents.tools.feature;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.augmentia.strandsagents.core.sessions.SessionManager;
import de.augmentia.strandsagents.tools.AgentTool;
import de.augmentia.strandsagents.tools.ToolResult;

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
            return ToolResult.success(
                "Session " + params.sessionId() + " nicht gefunden – nichts zu löschen",
                new DeleteDetails(params.sessionId(), false));
        }

        sessionManager.deleteSession(params.sessionId());

        var result = MAPPER.createObjectNode();
        result.put("action", "DELETE");
        result.put("sessionId", params.sessionId());
        result.put("cascade", params.cascade());
        result.put("deletedAt", java.time.Instant.now().toString());
        result.put("status", "DELETED");

        return ToolResult.success(result.toPrettyString(), new DeleteDetails(params.sessionId(), true));
    }

    public record DeleteDetails(String sessionId, boolean deleted) {}
}
