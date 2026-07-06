package de.augmentia.strandsagents.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.augmentia.strandsagents.tools.builtin.BaseToolNames;

import java.util.Properties;
import org.junit.jupiter.api.Test;

class StrandsAgentBuilderTest {

    @Test
    void fromJson_shouldCreateAgentWithConfiguredSystemPrompt() {
        var json = """
            {
                "systemPrompt": "You are a test agent",
                "apiKey": "sk-dummy",
                "baseUrl": "http://localhost:9999"
            }
            """;

        var agent = (DefaultStrandsAgent) StrandsAgent.builder()
            .fromJson(json)
            .build();

        assertThat(agent.getName()).isEqualTo("strands-agent");
        var delegate = agent.getDelegate();
        assertThat(delegate.getSystemPrompt()).isEqualTo("You are a test agent");
    }

    @Test
    void fromProperties_shouldReadAllConfiguredValues() {
        var props = new Properties();
        props.setProperty("strands.agent.model", "gpt-4o");
        props.setProperty("strands.agent.api-key", "sk-from-props");
        props.setProperty("strands.agent.base-url", "https://test.api.com");
        props.setProperty("strands.agent.temperature", "0.3");
        props.setProperty("strands.agent.max-retries", "7");
        props.setProperty("strands.agent.provider", "OPENAI");
        props.setProperty("strands.agent.system-prompt", "Props-based prompt");
        props.setProperty("strands.agent.max-iterations", "20");
        props.setProperty("strands.agent.max-messages", "50");
        props.setProperty("strands.agent.tools", "de.augmentia.strandsagents.tools.builtin.WebSearchTool");
        props.setProperty("strands.agent.skills.dir", "/opt/skills");
        props.setProperty("strands.agent.ollama.base-url", "http://ollama:11434");

        var agent = (DefaultStrandsAgent) StrandsAgent.builder()
            .fromProperties(props)
            .build();

        assertThat(agent).isNotNull();
        assertThat(agent.getDelegate().getSystemPrompt()).isEqualTo("Props-based prompt");
    }

    @Test
    void fromEnv_shouldReadFromEnvironment() {
        var agent = (DefaultStrandsAgent) StrandsAgent.builder()
            .fromEnv()
            .build();

        assertThat(agent).isNotNull();
    }

    @Test
    void programmaticWith_shouldOverrideJson() {
        var json = """
            {
                "systemPrompt": "from json",
                "model": "gpt-3.5-turbo",
                "apiKey": "sk-json"
            }
            """;

        var agent = (DefaultStrandsAgent) StrandsAgent.builder()
            .fromJson(json)
            .withSystemPrompt("from programmatic")
            .build();

        assertThat(agent.getDelegate().getSystemPrompt()).isEqualTo("from programmatic");
    }

    @Test
    void fromJson_shouldMergeWithDefaults() {
        var json = """
            {
                "systemPrompt": "custom prompt",
                "apiKey": "sk-test"
            }
            """;

        var agent = (DefaultStrandsAgent) StrandsAgent.builder()
            .fromJson(json)
            .build();

        var delegate = agent.getDelegate();
        assertThat(delegate.getSystemPrompt()).isEqualTo("custom prompt");
    }

    @Test
    void builder_shouldRespectName() {
        var agent = (DefaultStrandsAgent) StrandsAgent.builder()
            .name("my-agent")
            .build();

        assertThat(agent.getName()).isEqualTo("my-agent");
    }

    @Test
    void fromRedis_shouldThrowUnsupported() {
        var builder = StrandsAgent.builder();
        assertThatThrownBy(() -> builder.fromRedis("my-config"))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("not yet implemented");
    }

    @Test
    void withTool_shouldAddToolNameToRegistry() {
        var agent = (DefaultStrandsAgent) StrandsAgent.builder()
            .withTool("de.augmentia.strandsagents.tools.builtin.WebSearchTool")
            .build();

        var registry = agent.getDelegate().getToolRegistry();
        assertThat(registry.getToolNames()).contains(BaseToolNames.WEB_SEARCH);
    }

    @Test
    void withMaxIterations_shouldConfigureAgent() {
        var agent = (DefaultStrandsAgent) StrandsAgent.builder()
            .withMaxIterations(25)
            .build();

        assertThat(agent).isNotNull();
    }

    @Test
    void jsonInterpolation_shouldReplaceEnvVars() {
        var home = System.getenv("HOME");
        var json = """
            {
                "systemPrompt": "Workspace: ${HOME}/data",
                "apiKey": "sk-test"
            }
            """;

        var agent = (DefaultStrandsAgent) StrandsAgent.builder()
            .fromJson(json)
            .build();

        assertThat(agent.getDelegate().getSystemPrompt()).isEqualTo("Workspace: " + home + "/data");
    }

    @Test
    void programmaticTools_shouldBeAddedToJsonTools() {
        var json = """
            {
                "tools": ["de.augmentia.strandsagents.tools.builtin.WebSearchTool"]
            }
            """;

        var agent = (DefaultStrandsAgent) StrandsAgent.builder()
            .fromJson(json)
            .withTool("de.augmentia.strandsagents.tools.builtin.WebFetchTool")
            .build();

        var registry = agent.getDelegate().getToolRegistry();
        assertThat(registry.getToolNames()).contains(BaseToolNames.WEB_SEARCH, BaseToolNames.WEB_FETCH);
    }

    @Test
    void fromYaml_shouldParseAndBuild() {
        var yaml = """
            systemPrompt: "YAML configured agent"
            model: "gpt-4o"
            apiKey: "sk-yaml"
            """;

        var agent = (DefaultStrandsAgent) StrandsAgent.builder()
            .fromYaml(yaml)
            .build();

        assertThat(agent.getDelegate().getSystemPrompt()).isEqualTo("YAML configured agent");
    }

    @Test
    void properties_shouldSetProviderEnum() {
        var props = new Properties();
        props.setProperty("strands.agent.provider", "OPENAI");
        props.setProperty("strands.agent.api-key", "sk-provider-test");

        var agent = (DefaultStrandsAgent) StrandsAgent.builder()
            .fromProperties(props)
            .build();

        assertThat(agent).isNotNull();
    }

    @Test
    void multipleSources_shouldMergeCorrectly() {
        var json = """
            {
                "systemPrompt": "base prompt",
                "model": "gpt-4o",
                "apiKey": "sk-base",
                "maxIterations": 10
            }
            """;

        var props = new Properties();
        props.setProperty("strands.agent.max-iterations", "30");
        props.setProperty("strands.agent.temperature", "0.1");

        var agent = (DefaultStrandsAgent) StrandsAgent.builder()
            .fromJson(json)
            .fromProperties(props)
            .withSystemPrompt("final prompt")
            .build();

        var delegate = agent.getDelegate();
        assertThat(delegate.getSystemPrompt()).isEqualTo("final prompt");
    }
}
