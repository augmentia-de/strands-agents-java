package de.augmentia.agenttest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CodeAssemblerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void twoStepWorkflow() throws Exception {
        var result = assemble(
            """
            {
              "name": "write-and-read",
              "description": "write a file then read it",
              "systemPrompt": "You are a file assistant.",
              "testPrompt": "Write hello to test.txt",
              "workflow": [
                {"step": 1, "action": "write file", "tool": "mcp_localhost_8099_write"},
                {"step": 2, "action": "read file", "tool": "mcp_localhost_8099_read"}
              ],
              "tools": {"include": ["mcp_localhost_8099_write", "mcp_localhost_8099_read"], "exclude": []},
              "asserts": {"finalAnswerNotNull": true, "expectedOutputContains": null}
            }
            """,
            """
            {
              "stepSchemas": {
                "1": {"type": "object", "properties": {"result": {"type": "string"}}, "required": ["result"]},
                "2": {"type": "object", "properties": {"content": {"type": "string"}}, "required": ["content"]}
              }
            }
            """
        );
        assertAll(
            () -> assertTrue(result.startsWith("package de.augmentia.generated;"), "package"),
            () -> assertFalse(result.contains("${"), "no placeholders"),
            () -> assertTrue(result.contains("MCP_SERVER_URL"), "MCP connection"),
            () -> assertTrue(result.contains("mcp_localhost_8099_write"), "tool1 in Set.of"),
            () -> assertTrue(result.contains("mcp_localhost_8099_read"), "tool2 in Set.of"),
            () -> assertTrue(result.contains("StructuredOutputConfig.dynamicSchema"), "dynamicSchema"),
            () -> assertTrue(result.contains("var step1 = agent.execute(\"Write hello to test.txt\");"), "step1 execute"),
            () -> assertTrue(result.contains("var step2 = agent.execute(\"Next: \" + step1.finalAnswer());"), "step2 execute"),
            () -> assertTrue(result.contains("out.put(\"step1\", step1.finalAnswer());"), "collect step1"),
            () -> assertTrue(result.contains("out.put(\"step2\", step2.finalAnswer());"), "collect step2"),
            () -> assertTrue(result.contains("out.put(\"stopReason\", step2.stopReason().name());"), "stopReason"),
            () -> assertTrue(result.contains("out.put(\"toolCalls\""), "toolCalls"),
            () -> assertTrue(result.contains("import dev.langchain4j.mcp.client.DefaultMcpClient;"), "MCP import")
        );
    }

    @Test
    void oneStepWorkflow() throws Exception {
        var result = assemble(
            """
            {
              "name": "simple-read",
              "description": "read a file",
              "systemPrompt": "Read the file.",
              "testPrompt": "Read test.txt",
              "workflow": [
                {"step": 1, "action": "read file", "tool": "mcp_localhost_8099_read"}
              ],
              "tools": {"include": ["mcp_localhost_8099_read"], "exclude": []},
              "asserts": {"finalAnswerNotNull": true, "expectedOutputContains": null}
            }
            """,
            """
            {
              "stepSchemas": {
                "1": {"type": "object", "properties": {"content": {"type": "string"}}, "required": ["content"]}
              }
            }
            """
        );
        assertAll(
            () -> assertTrue(result.startsWith("package de.augmentia.generated;"), "package"),
            () -> assertFalse(result.contains("${"), "no placeholders"),
            () -> assertTrue(result.contains("var step1 = agent.execute(\"Read test.txt\");"), "single step"),
            () -> assertTrue(result.contains("out.put(\"stopReason\", step1.stopReason().name());"), "stopReason from step1"),
            () -> assertEquals(1, countOccurrences(result, "agent.setStructuredOutputConfig"), "one schema call")
        );
    }

    @Test
    void threeStepWorkflow() throws Exception {
        var result = assemble(
            """
            {
              "name": "three-step-test",
              "description": "Three step workflow",
              "systemPrompt": "You are a helpful assistant.",
              "testPrompt": "Test prompt",
              "workflow": [
                {"step": 1, "action": "use_tool", "tool": "mcp_localhost_8099_step1_tool"},
                {"step": 2, "action": "use_tool", "tool": "mcp_localhost_8099_step2_tool"},
                {"step": 3, "action": "use_tool", "tool": "mcp_localhost_8099_step3_tool"}
              ],
              "tools": {"include": ["step1_tool", "step2_tool", "step3_tool"], "exclude": []},
              "asserts": {"finalAnswerNotNull": true, "expectedOutputContains": "hello"}
            }
            """,
            """
            {
              "stepSchemas": {
                "1": {"type": "object", "properties": {"r": {"type": "string"}}, "required": ["r"]},
                "2": {"type": "object", "properties": {"r": {"type": "string"}}, "required": ["r"]},
                "3": {"type": "object", "properties": {"r": {"type": "string"}}, "required": ["r"]}
              }
            }
            """
        );
        assertAll(
            () -> assertEquals(3, countOccurrences(result, "agent.setStructuredOutputConfig"), "3 schema calls"),
            () -> assertTrue(result.contains("var step3 = agent.execute(\"Next: \" + step2.finalAnswer());"), "step3 chained"),
            () -> assertTrue(result.contains("out.put(\"stopReason\", step3.stopReason().name());"), "stopReason from step3"),
            () -> assertTrue(result.contains("(step3.metrics() != null ? step3.metrics().toolCallsCount() : 0)"), "step3 metrics summed")
        );
    }

    @Test
    void escapedCharacters() throws Exception {
        var result = assemble(
            """
            {
              "name": "escape-test",
              "description": "test escaping",
              "systemPrompt": "Please say \\"hello\\"\\nNew line here.",
              "testPrompt": "Write 'test' to file.txt",
              "workflow": [
                {"step": 1, "action": "write", "tool": "mcp_localhost_8099_write"}
              ],
              "tools": {"include": ["mcp_localhost_8099_write"], "exclude": []},
              "asserts": {"finalAnswerNotNull": true, "expectedOutputContains": null}
            }
            """,
            """
            {
              "stepSchemas": {
                "1": {"type": "object", "properties": {"r": {"type": "string"}}, "required": ["r"]}
              }
            }
            """
        );
        assertAll(
            () -> assertTrue(result.contains("Please say \\\"hello\\\"\\nNew line here."), "escaped quotes and newline"),
            () -> assertTrue(result.contains("Write 'test' to file.txt"), "test prompt with single quotes"),
            () -> assertFalse(result.contains("${"), "no placeholders")
        );
    }

    @Test
    void complexNestedSchema() throws Exception {
        var result = assemble(
            """
            {
              "name": "complex-schema",
              "description": "nested schema test",
              "systemPrompt": "Do the task.",
              "testPrompt": "Go",
              "workflow": [
                {"step": 1, "action": "search", "tool": "mcp_localhost_8099_grep"}
              ],
              "tools": {"include": ["mcp_localhost_8099_grep"], "exclude": []},
              "asserts": {"finalAnswerNotNull": true, "expectedOutputContains": null}
            }
            """,
            """
            {
              "stepSchemas": {
                "1": {
                  "type": "object",
                  "properties": {
                    "results": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "properties": {
                          "file": {"type": "string"},
                          "line": {"type": "integer"}
                        },
                        "required": ["file", "line"]
                      }
                    }
                  },
                  "required": ["results"]
                }
              }
            }
            """
        );
        assertAll(
            () -> assertTrue(result.contains("dynamicSchema"), "dynamicSchema call"),
            () -> assertTrue(result.contains("properties"), "schema properties"),
            () -> assertFalse(result.contains("${"), "no placeholders")
        );
    }

    @Test
    void emptyInclude() throws Exception {
        var result = assemble(
            """
            {
              "name": "no-tools",
              "description": "empty include test",
              "systemPrompt": "No tools.",
              "testPrompt": "Do nothing",
              "workflow": [
                {"step": 1, "action": "idle", "tool": "mcp_localhost_8099_none"}
              ],
              "tools": {"include": [], "exclude": []},
              "asserts": {"finalAnswerNotNull": true, "expectedOutputContains": null}
            }
            """,
            """
            {
              "stepSchemas": {
                "1": {"type": "object", "properties": {"r": {"type": "string"}}, "required": ["r"]}
              }
            }
            """
        );
        assertAll(
            () -> assertTrue(result.matches("(?s).*Set\\.of\\(\\s*\\).*"), "empty Set.of"),
            () -> assertFalse(result.contains("${"), "no placeholders")
        );
    }

    @Test
    void jsonDeserialization() throws Exception {
        var config = MAPPER.readValue("""
            {
              "name": "test",
              "description": "desc",
              "systemPrompt": "prompt",
              "testPrompt": "prompt",
              "workflow": [{"step": 1, "action": "act", "tool": "mcp_tool"}],
              "tools": {"include": ["mcp_tool"], "exclude": []},
              "asserts": {"finalAnswerNotNull": true, "expectedOutputContains": null}
            }
            """, WorkflowConfig.class);
        assertEquals("test", config.name());
        assertEquals(1, config.workflow().size());
        assertEquals("mcp_tool", config.workflow().get(0).tool());
        assertEquals(1, config.tools().include().size());

        var schemas = MAPPER.readValue("""
            {"stepSchemas": {"1": {"type": "object"}}}
            """, StepSchemas.class);
        assertTrue(schemas.stepSchemas().containsKey("1"));
        assertEquals("object", schemas.stepSchemas().get("1").get("type").asText());
    }

    @Test
    void escapeJavaEdgeCases() {
        assertEquals("", CodeAssembler.escapeJava(null));
        assertEquals("hello", CodeAssembler.escapeJava("hello"));
        assertEquals("hello \\\"world\\\"", CodeAssembler.escapeJava("hello \"world\""));
        assertEquals("line1\\nline2", CodeAssembler.escapeJava("line1\nline2"));
        assertEquals("path\\\\to\\\\file", CodeAssembler.escapeJava("path\\to\\file"));
        assertEquals("tab\\there", CodeAssembler.escapeJava("tab\there"));
    }

    @Test
    void importsAreComplete() throws Exception {
        var result = assemble(minimalConfig(), minimalSchemas());
        var imports = List.of(
            "import de.augmentia.strandsagents.core.*;",
            "import de.augmentia.strandsagents.core.agent.*;",
            "import de.augmentia.strandsagents.core.config.*;",
            "import de.augmentia.strandsagents.core.structured.StructuredOutputConfig;",
            "import de.augmentia.strandsagents.core.tools.McpToolMethod;",
            "import dev.langchain4j.agent.tool.ToolSpecification;",
            "import dev.langchain4j.model.chat.ChatModel;",
            "import dev.langchain4j.mcp.client.DefaultMcpClient;",
            "import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;",
            "import java.util.LinkedHashMap;",
            "import java.util.Set;",
            "import com.fasterxml.jackson.databind.ObjectMapper;"
        );
        for (var imp : imports) {
            assertTrue(result.contains(imp), "Missing import: " + imp);
        }
    }

    // --- helpers ---

    private static String assemble(String configJson, String schemasJson) throws Exception {
        var config = MAPPER.readValue(configJson, WorkflowConfig.class);
        var schemas = MAPPER.readValue(schemasJson, StepSchemas.class);
        return CodeAssembler.assemble(config, schemas);
    }

    private static String minimalConfig() {
        return """
            {
              "name": "minimal",
              "description": "",
              "systemPrompt": "prompt",
              "testPrompt": "test",
              "workflow": [{"step": 1, "action": "act", "tool": "mcp_localhost_8099_tool"}],
              "tools": {"include": ["mcp_localhost_8099_tool"], "exclude": []},
              "asserts": {"finalAnswerNotNull": true, "expectedOutputContains": null}
            }
            """;
    }

    private static String minimalSchemas() {
        return """
            {"stepSchemas": {"1": {"type": "object", "properties": {"r": {"type": "string"}}, "required": ["r"]}}}
            """;
    }

    private static int countOccurrences(String s, String sub) {
        int count = 0, idx = 0;
        while ((idx = s.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
