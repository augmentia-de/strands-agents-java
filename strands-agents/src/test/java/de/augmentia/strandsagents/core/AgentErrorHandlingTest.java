package de.augmentia.strandsagents.core;

import static org.assertj.core.api.Assertions.assertThat;

import de.augmentia.strandsagents.core.agent.Agent;
import de.augmentia.strandsagents.core.model.agent.StopReason;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

class AgentErrorHandlingTest {

    static class ExceptionTool {
        @Tool("Throws an exception")
        public String fail(String msg) {
            throw new RuntimeException("KABOOM: " + msg);
        }
    }

    static class ErrorHandlingMockModel implements ChatModel {
        private int callCount = 0;

        @Override
        public ChatResponse chat(ChatRequest chatRequest) {
            callCount++;
            if (callCount == 1) {
                // First call: trigger the tool
                return ChatResponse.builder()
                    .aiMessage(AiMessage.from(ToolExecutionRequest.builder()
                        .name("fail")
                        .arguments("{\"msg\": \"test\"}")
                        .build()))
                    .build();
            } else {
                // Second call: after tool execution (successful or failed)
                boolean sawError = chatRequest.messages().stream()
                    .anyMatch(m -> m instanceof ToolExecutionResultMessage && ((ToolExecutionResultMessage) m).text().contains("KABOOM"));
                
                return ChatResponse.builder()
                    .aiMessage(AiMessage.from(sawError ? "I saw the error and I'm fine." : "No error seen."))
                    .build();
            }
        }
    }

    @Test
    void shouldRecoverFromToolException() {
        var model = new ErrorHandlingMockModel();
        var agent = new Agent(model);
        agent.addTool(new ExceptionTool());

        var result = agent.execute("Try to fail");

        assertThat(result.stopReason()).isEqualTo(StopReason.COMPLETED);
        assertThat(result.finalAnswer()).isEqualTo("I saw the error and I'm fine.");
    }
}
