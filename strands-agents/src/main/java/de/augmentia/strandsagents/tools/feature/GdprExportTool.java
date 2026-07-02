package de.augmentia.strandsagents.tools.feature;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.augmentia.strandsagents.core.sessions.SessionManager;
import de.augmentia.strandsagents.tools.AgentTool;
import de.augmentia.strandsagents.tools.ToolResult;

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
        session.metadata().forEach((k, v) -> {
            if (v instanceof String s) metadata.put(k, s);
            else metadata.putPOJO(k, v);
        });

        var json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(export);
        return ToolResult.success(json, new ExportDetails(params.sessionId()));
    }

    public record ExportDetails(String sessionId) {}
}
