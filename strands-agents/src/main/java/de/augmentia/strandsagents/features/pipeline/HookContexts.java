package de.augmentia.strandsagents.features.pipeline;

import de.augmentia.strandsagents.model.agent.AgentResult;
import de.augmentia.strandsagents.model.message.Message;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class HookContexts {

    private HookContexts() {}

    public record BeforeAgentContext(
        String sessionId,
        String prompt,
        Map<String, Object> contextVariables
    ) {}

    public record AfterAgentContext(
        String sessionId,
        AgentResult result
    ) {}

    public record BeforeModelCallContext(
        String sessionId,
        StringBuilder systemPrompt,
        List<Message> messages,
        List<ToolSpecification> tools,
        List<Message> additionalMessages
    ) {}

    // TODO: ChatResponse here is a demo shortcut.
    //   In production, prefer an abstraction like ModelCallMetadata (cachedTokens,
    //   modelName, responseId) to avoid exposing rawHttpResponse with
    //   potential organization/request-IDs from the HTTP round-trip.
    public record AfterModelCallContext(
        String sessionId,
        String llmResponse,
        int inputTokens,
        int outputTokens,
        ChatResponse chatResponse
    ) {}

    public record BeforeToolCallContext(
        String sessionId,
        String toolName,
        Map<String, Object> arguments
    ) {}

    public record AfterToolCallContext(
        String sessionId,
        String toolName,
        String result,
        boolean isError,
        List<Message> additionalMessages
    ) {
        public AfterToolCallContext(String sessionId, String toolName, String result, boolean isError) {
            this(sessionId, toolName, result, isError, new ArrayList<>());
        }
    }
}
