package de.augmentia.strandsagents.model.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolInfoTest {

    @Test
    void fieldsAreAccessible() {
        var info = new ToolInfo();
        info.name = "calculator";
        info.description = "does math";
        info.parameters = "{\"a\":\"int\"}";
        assertThat(info.name).isEqualTo("calculator");
        assertThat(info.description).isEqualTo("does math");
        assertThat(info.parameters).contains("a");
    }
}

class ChatRequestTest {

    @Test
    void fieldsAreAccessible() {
        var req = new ChatRequest();
        req.prompt = "hello";
        req.sessionId = "sid1";
        req.tools = List.of("tool1");
        req.skills = List.of("skill1");
        req.systemPrompt = "You are a helper";
        assertThat(req.prompt).isEqualTo("hello");
        assertThat(req.sessionId).isEqualTo("sid1");
        assertThat(req.tools).containsExactly("tool1");
        assertThat(req.skills).containsExactly("skill1");
        assertThat(req.systemPrompt).isEqualTo("You are a helper");
    }
}

class ChatResponseTest {

    @Test
    void fieldsAreAccessible() {
        var resp = new ChatResponse();
        resp.answer = "the answer";
        resp.sessionId = "sid1";
        resp.durationMs = 100L;
        resp.inputTokens = 10;
        resp.outputTokens = 20;
        resp.toolCallsCount = 3;
        resp.error = null;
        assertThat(resp.answer).isEqualTo("the answer");
        assertThat(resp.sessionId).isEqualTo("sid1");
        assertThat(resp.durationMs).isEqualTo(100L);
    }

    @Test
    void toolCallInfo_fields() {
        var tci = new ChatResponse.ToolCallInfo();
        tci.name = "calc";
        tci.result = "42";
        tci.durationMs = 5L;
        tci.success = true;
        assertThat(tci.name).isEqualTo("calc");
        assertThat(tci.result).isEqualTo("42");
        assertThat(tci.success).isTrue();
    }
}

class AgentInitRequestTest {

    @Test
    void fieldsAreAccessible() {
        var req = new AgentInitRequest();
        req.sessionId = "sid1";
        req.systemPrompt = "You are a helper";
        req.tools = List.of("tool1");
        req.skills = List.of("skill1");
        req.skillSearchEnabled = true;
        req.mcpIngestEnabled = false;
        assertThat(req.sessionId).isEqualTo("sid1");
        assertThat(req.systemPrompt).isEqualTo("You are a helper");
        assertThat(req.skillSearchEnabled).isTrue();
        assertThat(req.mcpIngestEnabled).isFalse();
    }
}

class SkillInfoTest {

    @Test
    void fieldsAreAccessible() {
        var info = new SkillInfo();
        info.name = "skill1";
        info.description = "A skill";
        assertThat(info.name).isEqualTo("skill1");
        assertThat(info.description).isEqualTo("A skill");
    }
}

class McpServerSelectionTest {

    @Test
    void fieldsAreAccessible() {
        var sel = new McpServerSelection();
        sel.serverName = "srv1";
        sel.tools = List.of("tool_a");
        sel.url = "http://localhost";
        assertThat(sel.serverName).isEqualTo("srv1");
        assertThat(sel.tools).containsExactly("tool_a");
        assertThat(sel.url).isEqualTo("http://localhost");
    }
}
