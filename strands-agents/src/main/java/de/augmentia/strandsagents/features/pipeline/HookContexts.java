package de.augmentia.strandsagents.features.pipeline;

import de.augmentia.strandsagents.model.agent.AgentResult;
import de.augmentia.strandsagents.model.message.Message;
import dev.langchain4j.agent.tool.ToolSpecification;
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

    public record AfterModelCallContext(
        String sessionId,
        String llmResponse,
        int inputTokens,
        int outputTokens
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
        boolean isError
    ) {}
}
