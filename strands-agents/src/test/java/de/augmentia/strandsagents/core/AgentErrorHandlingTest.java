package de.augmentia.strandsagents.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.augmentia.strandsagents.core.Agent;
import de.augmentia.strandsagents.model.agent.StopReason;
import de.augmentia.strandsagents.features.tools.AgentTool;
import de.augmentia.strandsagents.features.tools.ToolResult;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class AgentErrorHandlingTest {

    static class ExceptionTool implements AgentTool<ExceptionTool.Params> {
        @Override
        public String name() { return "fail"; }
        @Override
        public String description() { return "Throws an exception"; }
        @Override
        public Class<Params> parameterType() { return Params.class; }
        @Override
        public ObjectNode parameterSchema() {
            var factory = JsonNodeFactory.instance;
            var schema = factory.objectNode();
            schema.put("type", "object");
            var props = factory.objectNode();
            var msgProp = factory.objectNode();
            msgProp.put("type", "string");
            msgProp.put("description", "The message");
            props.set("msg", msgProp);
            schema.set("properties", props);
            var required = factory.arrayNode();
            required.add("msg");
            schema.set("required", required);
            return schema;
        }
        @Override
        public ToolResult execute(String toolCallId, Params params, AtomicBoolean abortFlag, Consumer<ToolResult> onUpdate) {
            throw new RuntimeException("KABOOM: " + params.msg());
        }
        public record Params(String msg) {}
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
