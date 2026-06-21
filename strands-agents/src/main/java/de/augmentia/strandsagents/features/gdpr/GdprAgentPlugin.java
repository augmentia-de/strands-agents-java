package de.augmentia.strandsagents.features.gdpr;

import de.augmentia.strandsagents.core.Agent;
import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.features.gdpr.tools.GdprDeleteTool;
import de.augmentia.strandsagents.features.gdpr.tools.GdprExportTool;
import de.augmentia.strandsagents.features.plugin.Plugin;
import de.augmentia.strandsagents.features.sessions.SessionManager;

import java.util.List;
import java.util.Set;

public class GdprAgentPlugin implements Plugin {

    private final SessionManager sessionManager;
    private final Set<PiiAnonymizerHook.MaskType> maskTypes;
    private final PiiAnonymizerHook.BlockAction blockAction;
    private final String replacement;
    private final AuditTrailHook.AuditStore auditStore;

    public GdprAgentPlugin(
            SessionManager sessionManager,
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
        return List.of(
            ToolRegistry.createMethod(new GdprExportTool(sessionManager)),
            ToolRegistry.createMethod(new GdprDeleteTool(sessionManager))
        );
    }

    @Override
    public void initAgent(Agent agent) {
        agent.addHook(new PiiAnonymizerHook(maskTypes, blockAction, replacement));
        if (auditStore != null) {
            agent.addHook(new AuditTrailHook(auditStore));
        }
    }
}
