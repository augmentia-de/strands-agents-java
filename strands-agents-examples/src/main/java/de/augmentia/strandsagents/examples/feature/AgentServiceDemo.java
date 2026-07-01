package de.augmentia.strandsagents.examples.feature;

import de.augmentia.strandsagents.core.MockChatModel;
import de.augmentia.strandsagents.core.Agent;
import de.augmentia.strandsagents.features.service.AgentService;
import de.augmentia.strandsagents.features.hitl.checkpoint.CheckpointService;
import de.augmentia.strandsagents.features.hitl.checkpoint.SSEChannel;
import de.augmentia.strandsagents.features.sessions.SessionManager;
import de.augmentia.strandsagents.model.api.*;
import de.augmentia.strandsagents.model.agent.StopReason;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class AgentServiceDemo {

    public static void main(String[] args) {
        run();
        System.out.println("=== AgentServiceDemo PASSED ===");
    }

    public static void run() {
        var service = new DemoAgentService();

        // ── 1. initAgent ──
        var initReq = new AgentInitRequest();
        initReq.tools = List.of("read", "write");
        initReq.skills = List.of("java-coding-standards");
        initReq.systemPrompt = "You are a demo assistant.";
        initReq.sessionId = "session-init-1";

        var initResponse = service.initAgent(initReq);
        System.out.println("  [initAgent] sessionId=" + initResponse.sessionId);
        System.out.println("  [initAgent] stopReason=" + initResponse.stopReason);
        assert initResponse.sessionId.equals("session-init-1") : "init session id";
        assert initResponse.stopReason == StopReason.COMPLETED : "init completed";

        // ── 2. chat ──
        var chatReq = new ChatRequest();
        chatReq.prompt = "Hello, how are you?";
        chatReq.sessionId = "session-chat-1";

        var chatResponse = service.chat(chatReq);
        System.out.println("  [chat] answer=" + chatResponse.answer);
        System.out.println("  [chat] tokens=" + chatResponse.inputTokens + " in / "
            + chatResponse.outputTokens + " out");
        assert chatResponse.answer != null : "chat produced answer";
        assert chatResponse.stopReason == StopReason.COMPLETED : "chat completed";
        assert chatResponse.inputTokens > 0 : "input tokens counted";
        assert chatResponse.outputTokens > 0 : "output tokens counted";

        // ── 3. chatSse (streaming) ──
        var sseReq = new ChatRequest();
        sseReq.prompt = "Tell me a short joke.";
        sseReq.sessionId = "session-sse-1";

        var tokenCollector = new StringBuilder();
        var phaseCollector = new CopyOnWriteArrayList<String>();
        var completeTracker = new AtomicBoolean(false);

        service.chatSse(sseReq,
            token -> {
                tokenCollector.append(token);
                System.out.print(token);
            },
            phases -> {
                phaseCollector.addAll(phases);
                System.out.println("  [SSE] phases=" + phases);
            },
            response -> {
                completeTracker.set(true);
                System.out.println("\n  [SSE] complete: tokens="
                    + response.inputTokens + "/" + response.outputTokens);
            }
        );

        assert tokenCollector.length() > 0 : "SSE produced tokens";
        assert completeTracker.get() : "SSE onComplete called";

        // ── 4. listTools / listSkills ──
        var tools = service.listTools();
        System.out.println("  [listTools] " + tools.size() + " tools");
        assert !tools.isEmpty() : "at least one tool listed";

        var skills = service.listSkills();
        System.out.println("  [listSkills] " + skills.size() + " skills");

        // ── 5. MCP discovery ──
        var mcpServers = service.getMcpServers();
        System.out.println("  [getMcpServers] " + mcpServers);

        // ── 6. activateModel / deactivateModel ──
        service.activateModel("sk-demo-key-123");
        assert service.isRuntimeKeyActive() : "runtime key active after activate";
        System.out.println("  [activateModel] active=" + service.isRuntimeKeyActive());

        service.deactivateModel();
        assert !service.isRuntimeKeyActive() : "runtime key inactive after deactivate";
        System.out.println("  [deactivateModel] active=" + service.isRuntimeKeyActive());

        // ── 7. releaseSession ──
        service.releaseSession("session-chat-1");
        System.out.println("  [releaseSession] session-chat-1 released");
    }

    /** Minimal inline AgentService implementation for demo purposes. */
    static class DemoAgentService implements AgentService {

        private final AtomicBoolean runtimeKeyActive = new AtomicBoolean(false);
        private final AtomicInteger tokenCounter = new AtomicInteger(0);

        @Override
        public ChatResponse chat(ChatRequest req) {
            var response = new ChatResponse();
            response.answer = "Demo response to: " + req.prompt;
            response.sessionId = req.sessionId != null ? req.sessionId : "default";
            response.stopReason = StopReason.COMPLETED;
            response.durationMs = 42;
            response.inputTokens = 15 + req.prompt.length();
            response.outputTokens = response.answer.length();
            response.toolCallsCount = 0;
            return response;
        }

        @Override
        public ChatResponse initAgent(AgentInitRequest req) {
            var response = new ChatResponse();
            response.sessionId = req.sessionId;
            response.stopReason = StopReason.COMPLETED;
            response.durationMs = 10;
            response.inputTokens = 5;
            response.outputTokens = 2;
            return response;
        }

        @Override
        public void chatSse(ChatRequest req,
                            Consumer<String> onToken,
                            Consumer<List<String>> onPhases,
                            Consumer<ChatResponse> onComplete) {
            var tokens = ("Demo streaming response to: " + req.prompt).split(" ");
            for (var token : tokens) {
                onToken.accept(token + " ");
            }
            onPhases.accept(List.of("PROCESSING", "COMPLETED"));
            var response = new ChatResponse();
            response.answer = String.join(" ", tokens);
            response.sessionId = req.sessionId;
            response.stopReason = StopReason.COMPLETED;
            response.inputTokens = 10;
            response.outputTokens = tokens.length;
            onComplete.accept(response);
        }

        @Override
        public CheckpointService getCheckpointService() {
            return null;
        }

        @Override
        public SSEChannel getSseChannel() {
            return null;
        }

        @Override
        public SessionManager getSessionManager() {
            return null;
        }

        @Override
        public List<ToolInfo> listTools() {
            return List.of(
                toolInfo("read", "Read file contents", "{\"path\": \"string\"}"),
                toolInfo("write", "Write file contents", "{\"path\": \"string\", \"content\": \"string\"}"),
                toolInfo("calculator", "Perform arithmetic", "{\"expression\": \"string\"}")
            );
        }

        @Override
        public List<SkillInfo> listSkills() {
            return List.of(
                skillInfo("java-coding-standards", "Java coding conventions"),
                skillInfo("test-driven-verification", "TDD best practices")
            );
        }

        @Override
        public List<Map<String, String>> getMcpServers() {
            return List.of(
                Map.of("name", "confluence", "transport", "sse", "url", "http://localhost:8081/mcp"),
                Map.of("name", "docs", "transport", "stdio", "command", "node mcp-server.js")
            );
        }

        @Override
        public List<ToolInfo> discoverMcpTools(String serverName) {
            return List.of();
        }

        @Override
        public List<ToolInfo> connectMcpUrl(String url, String serverName) {
            return List.of();
        }

        @Override
        public void releaseSession(String sessionId) {
        }

        @Override
        public void activateModel(String apiKey) {
            runtimeKeyActive.set(true);
        }

        @Override
        public void deactivateModel() {
            runtimeKeyActive.set(false);
        }

        @Override
        public boolean isRuntimeKeyActive() {
            return runtimeKeyActive.get();
        }

        private static ToolInfo toolInfo(String name, String desc, String params) {
            var info = new ToolInfo();
            info.name = name;
            info.description = desc;
            info.parameters = params;
            return info;
        }

        private static SkillInfo skillInfo(String name, String desc) {
            var info = new SkillInfo();
            info.name = name;
            info.description = desc;
            return info;
        }
    }
}
