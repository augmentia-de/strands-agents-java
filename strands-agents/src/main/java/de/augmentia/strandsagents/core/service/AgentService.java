package de.augmentia.strandsagents.core.service;

import de.augmentia.strandsagents.core.model.api.AgentInitRequest;
import de.augmentia.strandsagents.core.model.api.ChatRequest;
import de.augmentia.strandsagents.core.model.api.ChatResponse;
import de.augmentia.strandsagents.core.model.api.SkillInfo;
import de.augmentia.strandsagents.core.model.api.ToolInfo;
import de.augmentia.strandsagents.core.plugin.hitl.checkpoint.CheckpointService;
import de.augmentia.strandsagents.core.plugin.hitl.checkpoint.SSEChannel;
import de.augmentia.strandsagents.sessions.SessionManager;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface AgentService {

    ChatResponse chat(ChatRequest req);

    ChatResponse initAgent(AgentInitRequest req);

    void chatSse(ChatRequest req,
                 Consumer<String> onToken,
                 Consumer<List<String>> onPhases,
                 Consumer<ChatResponse> onComplete);

    CheckpointService getCheckpointService();

    SSEChannel getSseChannel();

    SessionManager getSessionManager();

    List<ToolInfo> listTools();

    List<SkillInfo> listSkills();

    List<Map<String, String>> getMcpServers();

    List<ToolInfo> discoverMcpTools(String serverName);

    List<ToolInfo> connectMcpUrl(String url, String serverName);

    void releaseSession(String sessionId);

    void activateModel(String apiKey);

    void deactivateModel();

    boolean isRuntimeKeyActive();
}
