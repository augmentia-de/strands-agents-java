package de.augmentia.strandsagents.examples;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import de.augmentia.strandsagents.config.ModelProviderType;
import de.augmentia.strandsagents.core.Agent;
import de.augmentia.strandsagents.core.MockChatModel;
import de.augmentia.strandsagents.facade.ConfigModel;
import de.augmentia.strandsagents.facade.DefaultStrandsAgent;
import de.augmentia.strandsagents.facade.StrandsAgent;
import java.io.InputStream;
import java.util.Properties;

public class StrandsAgentBuilderDemo {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
        .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
        .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static void main(String[] args) throws Exception {
        System.out.println("=== StrandsAgentBuilder Demo ===");
        System.out.println();

        demoConfigFiles();
        demoBuilderFromJsonFile();
        demoBuilderFromYamlFile();
        demoBuilderFromPropertiesFile();
        demoProgrammatic();
        demoFullPipeline();
        demoBasicChat();
    }

    // ── Config file loading ──

    static void demoConfigFiles() throws Exception {
        System.out.println("─── 0. Loading config files from classpath ───");

        var jsonCfg = loadJsonConfig("/config/strands-agent.json");
        System.out.println("  JSON:    " + jsonCfg.getModelName() + " | " + jsonCfg.getSystemPrompt());

        var yamlCfg = loadYamlConfig("/config/strands-agent.yaml");
        System.out.println("  YAML:    " + yamlCfg.getModelName() + " | " + yamlCfg.getSystemPrompt());

        var propsCfg = loadPropertiesConfig("/config/strands-agent.properties");
        System.out.println("  Props:   " + propsCfg.getProperty("strands.agent.model", "?")
            + " | " + propsCfg.getProperty("strands.agent.system-prompt", "?"));
        System.out.println();
    }

    static ConfigModel loadJsonConfig(String classpath) throws Exception {
        try (InputStream is = StrandsAgentBuilderDemo.class.getResourceAsStream(classpath)) {
            if (is == null) throw new IllegalStateException("File not found: " + classpath);
            return JSON_MAPPER.readValue(is, ConfigModel.class);
        }
    }

    static ConfigModel loadYamlConfig(String classpath) throws Exception {
        try (InputStream is = StrandsAgentBuilderDemo.class.getResourceAsStream(classpath)) {
            if (is == null) throw new IllegalStateException("File not found: " + classpath);
            return YAML_MAPPER.readValue(is, ConfigModel.class);
        }
    }

    static Properties loadPropertiesConfig(String classpath) throws Exception {
        var props = new Properties();
        try (InputStream is = StrandsAgentBuilderDemo.class.getResourceAsStream(classpath)) {
            if (is == null) throw new IllegalStateException("File not found: " + classpath);
            props.load(is);
        }
        return props;
    }

    // ── Builder from config files ──

    static void demoBuilderFromJsonFile() throws Exception {
        System.out.println("─── 1. StrandsAgentBuilder.fromJson() from config file ───");

        var json = new String(getClasspathBytes("/config/strands-agent.json"));

        try {
            StrandsAgent agent = StrandsAgent.builder()
                .name("json-agent")
                .fromJson(json)
                .build();

            var delegate = ((DefaultStrandsAgent) agent).getDelegate();
            System.out.println("  Name:          " + ((DefaultStrandsAgent) agent).getName());
            System.out.println("  SystemPrompt: " + delegate.getSystemPrompt());
        } catch (Exception e) {
            System.out.println("  (Model init skipped — " + e.getClass().getSimpleName() + ")");
            System.out.println("  Config would load: systemPrompt="
                + loadJsonConfig("/config/strands-agent.json").getSystemPrompt());
        }
        System.out.println();
    }

    static void demoBuilderFromYamlFile() throws Exception {
        System.out.println("─── 2. StrandsAgentBuilder.fromYaml() from config file ───");

        var yaml = new String(getClasspathBytes("/config/strands-agent.yaml"));

        try {
            StrandsAgent agent = StrandsAgent.builder()
                .name("yaml-agent")
                .fromYaml(yaml)
                .build();

            var delegate = ((DefaultStrandsAgent) agent).getDelegate();
            System.out.println("  Name:          " + ((DefaultStrandsAgent) agent).getName());
            System.out.println("  SystemPrompt: " + delegate.getSystemPrompt());
        } catch (Exception e) {
            System.out.println("  (Model init skipped — " + e.getClass().getSimpleName() + ")");
        }
        System.out.println();
    }

    static void demoBuilderFromPropertiesFile() throws Exception {
        System.out.println("─── 3. StrandsAgentBuilder.fromProperties() from config file ───");

        var props = loadPropertiesConfig("/config/strands-agent.properties");

        try {
            StrandsAgent agent = StrandsAgent.builder()
                .name("props-agent")
                .fromProperties(props)
                .build();

            var delegate = ((DefaultStrandsAgent) agent).getDelegate();
            System.out.println("  Name:          " + ((DefaultStrandsAgent) agent).getName());
            System.out.println("  SystemPrompt: " + delegate.getSystemPrompt());
        } catch (Exception e) {
            System.out.println("  (Model init skipped — " + e.getClass().getSimpleName() + ")");
        }
        System.out.println();
    }

    // ── Programmatic builder ──

    static void demoProgrammatic() throws Exception {
        System.out.println("─── 4. Programmatic .with*() methods ───");

        try {
            StrandsAgent agent = StrandsAgent.builder()
                .name("programmatic-agent")
                .withSystemPrompt("You are a programmatic assistant")
                .withModelName("gpt-4o")
                .withTemperature(0.5)
                .withMaxIterations(20)
                .withMaxMessages(50)
                .withProvider(ModelProviderType.OPENAI)
                .build();

            var delegate = ((DefaultStrandsAgent) agent).getDelegate();
            System.out.println("  Name:          " + ((DefaultStrandsAgent) agent).getName());
            System.out.println("  SystemPrompt: " + delegate.getSystemPrompt());
        } catch (Exception e) {
            System.out.println("  (Model init skipped — " + e.getClass().getSimpleName() + ")");
        }
        System.out.println();
    }

    static void demoFullPipeline() throws Exception {
        System.out.println("─── 5. Merge: JSON-file + Properties-file + programmatic ───");

        var json = new String(getClasspathBytes("/config/strands-agent.json"));
        var props = loadPropertiesConfig("/config/strands-agent.properties");

        try {
            StrandsAgent agent = StrandsAgent.builder()
                .name("merged-agent")
                .fromJson(json)
                .fromProperties(props)
                .withSystemPrompt("final merged prompt")
                .build();

            var delegate = ((DefaultStrandsAgent) agent).getDelegate();
            System.out.println("  Name:          " + ((DefaultStrandsAgent) agent).getName());
            System.out.println("  SystemPrompt: " + delegate.getSystemPrompt());
        } catch (Exception e) {
            System.out.println("  (Model init skipped — " + e.getClass().getSimpleName() + ")");
        }
        System.out.println();
    }

    // ── Basic chat with DefaultStrandsAgent ──

    static void demoBasicChat() {
        System.out.println("─── 6. Basic chat with DefaultStrandsAgent (MockChatModel) ───");

        var innerAgent = new Agent(new MockChatModel("Echo: %s"));
        var agent = new DefaultStrandsAgent(innerAgent, "chat-demo");

        System.out.println("  User: Hello!");
        var answer = agent.ask("Hello!");
        System.out.println("  Agent: " + answer);

        System.out.println("  User: What is the weather?");
        answer = agent.ask("What is the weather?");
        System.out.println("  Agent: " + answer);

        System.out.println();
        System.out.println("  Streaming response (token by token):");
        var sb = new StringBuilder();
        agent.askStream("Tell me a short joke.", token -> {
            System.out.print(token);
            System.out.flush();
            sb.append(token);
        });
        System.out.println();
        System.out.println("  (captured: " + sb.length() + " chars)");
        System.out.println();
    }

    // ── Helpers ──

    private static byte[] getClasspathBytes(String path) throws Exception {
        try (var is = StrandsAgentBuilderDemo.class.getResourceAsStream(path)) {
            if (is == null) throw new IllegalStateException("File not found: " + path);
            return is.readAllBytes();
        }
    }
}
