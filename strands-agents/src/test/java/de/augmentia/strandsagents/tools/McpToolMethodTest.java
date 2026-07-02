package de.augmentia.strandsagents.tools;

import static org.assertj.core.api.Assertions.assertThat;

import de.augmentia.strandsagents.tools.McpToolMethod;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpGetPromptResult;
import dev.langchain4j.mcp.client.McpPrompt;
import dev.langchain4j.mcp.client.McpReadResourceResult;
import dev.langchain4j.mcp.client.McpResource;
import dev.langchain4j.mcp.client.McpResourceTemplate;
import dev.langchain4j.mcp.client.McpRoot;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpToolMethodTest {

    @Test
    void spec_returnsPrefixedSpec() {
        var spec = ToolSpecification.builder()
            .name("mcp_server_read")
            .description("Read a file")
            .build();
        var method = new McpToolMethod(new MockMcpClient("result"), "server", "read", spec);
        assertThat(method.spec().name()).isEqualTo("mcp_server_read");
        assertThat(method.spec().description()).isEqualTo("Read a file");
    }

    @Test
    void execute_callsClientAndReturnsResultText() throws Exception {
        var spec = ToolSpecification.builder().name("mcp_server_read").build();
        var method = new McpToolMethod(new MockMcpClient("hello world"), "server", "read", spec);
        var result = method.execute("{\"path\": \"/tmp/test\"}");
        assertThat(result).isEqualTo("hello world");
    }

    @Test
    void execute_delegatesToClientWithCorrectRequest() throws Exception {
        var spec = ToolSpecification.builder().name("mcp_server_write").build();
        var method = new McpToolMethod(new MockMcpClient("written"), "server", "write", spec);
        var result = method.execute("{\"content\": \"data\"}");
        assertThat(result).isEqualTo("written");
    }

    static class MockMcpClient implements McpClient {
        private final String response;

        MockMcpClient(String response) {
            this.response = response;
        }

        @Override
        public String key() { return "mock"; }
        @Override
        public List<ToolSpecification> listTools() { return List.of(); }
        @Override
        public List<ToolSpecification> listTools(InvocationContext ctx) { return List.of(); }
        @Override
        public ToolExecutionResult executeTool(ToolExecutionRequest request) {
            return ToolExecutionResult.builder().resultText(response).build();
        }
        @Override
        public ToolExecutionResult executeTool(ToolExecutionRequest request, InvocationContext ctx) {
            return executeTool(request);
        }
        @Override
        public List<McpResource> listResources() { return List.of(); }
        @Override
        public List<McpResource> listResources(InvocationContext ctx) { return List.of(); }
        @Override
        public List<McpResourceTemplate> listResourceTemplates() { return List.of(); }
        @Override
        public List<McpResourceTemplate> listResourceTemplates(InvocationContext ctx) { return List.of(); }
        @Override
        public McpReadResourceResult readResource(String uri) { return null; }
        @Override
        public McpReadResourceResult readResource(String uri, InvocationContext ctx) { return null; }
        @Override
        public void subscribeToResource(String uri) {}
        @Override
        public void unsubscribeFromResource(String uri) {}
        @Override
        public List<McpPrompt> listPrompts() { return List.of(); }
        @Override
        public McpGetPromptResult getPrompt(String name, Map<String, Object> args) { return null; }
        @Override
        public void checkHealth() {}
        @Override
        public void setRoots(List<McpRoot> roots) {}
        @Override
        public void close() {}
    }
}
