package de.augmentia.strandsagents.facade;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import de.augmentia.strandsagents.config.*;
import de.augmentia.strandsagents.core.Agent;
import de.augmentia.strandsagents.core.ToolExecutor;
import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.features.conversation.ConversationManager;
import de.augmentia.strandsagents.features.conversation.SlidingWindowConversationManager;
import de.augmentia.strandsagents.features.plugin.Plugin;
import de.augmentia.strandsagents.features.sessions.SessionManager;
import de.augmentia.strandsagents.features.structured.StructuredOutputConfig;
import de.augmentia.strandsagents.features.structured.StructuredOutputMode;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.util.*;
import java.util.function.Consumer;

public class StrandsAgentBuilder {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private ConfigModel config = ConfigModel.defaults();
    private boolean hasJson;
    private boolean hasProperties;
    private boolean hasEnv;
    private final ConfigModel programmaticOverrides = new ConfigModel();
    private final Set<String> programmaticTools = new LinkedHashSet<>();
    private final List<Object> programmaticToolInstances = new ArrayList<>();
    private final List<Plugin> programmaticPlugins = new ArrayList<>();
    private String agentName = "strands-agent";

    StrandsAgentBuilder() {}

    public StrandsAgentBuilder fromDefaults() {
        config = ConfigModel.defaults();
        hasJson = false;
        hasProperties = false;
        hasEnv = false;
        return this;
    }

    public StrandsAgentBuilder fromJson(String json) {
        try {
            var parsed = JSON_MAPPER.readValue(json, ConfigModel.class);
            mergeConfig(parsed);
            hasJson = true;
            return this;
        } catch (Exception e) {
            throw new StrandsAgentException("Failed to parse JSON config", e);
        }
    }

    public StrandsAgentBuilder fromYaml(String yaml) {
        try {
            var parsed = YAML_MAPPER.readValue(yaml, ConfigModel.class);
            mergeConfig(parsed);
            hasJson = true;
            return this;
        } catch (Exception e) {
            throw new StrandsAgentException("Failed to parse YAML config", e);
        }
    }

    public StrandsAgentBuilder fromProperties(Properties props) {
        var parsed = new ConfigModel();
        if (props.containsKey("strands.agent.model")) parsed.setModelName(props.getProperty("strands.agent.model"));
        if (props.containsKey("strands.agent.api-key")) parsed.setApiKey(props.getProperty("strands.agent.api-key"));
        if (props.containsKey("strands.agent.base-url")) parsed.setBaseUrl(props.getProperty("strands.agent.base-url"));
        if (props.containsKey("strands.agent.temperature")) parsed.setTemperature(Double.parseDouble(props.getProperty("strands.agent.temperature")));
        if (props.containsKey("strands.agent.max-retries")) parsed.setMaxRetries(Integer.parseInt(props.getProperty("strands.agent.max-retries")));
        if (props.containsKey("strands.agent.provider")) parsed.setProvider(ModelProviderType.valueOf(props.getProperty("strands.agent.provider").toUpperCase()));
        if (props.containsKey("strands.agent.system-prompt")) parsed.setSystemPrompt(props.getProperty("strands.agent.system-prompt"));
        if (props.containsKey("strands.agent.max-iterations")) parsed.setMaxIterations(Integer.parseInt(props.getProperty("strands.agent.max-iterations")));
        if (props.containsKey("strands.agent.max-messages")) parsed.setMaxMessages(Integer.parseInt(props.getProperty("strands.agent.max-messages")));
        if (props.containsKey("strands.agent.skills.dir")) parsed.setSkillsDir(props.getProperty("strands.agent.skills.dir"));
        if (props.containsKey("strands.agent.ollama.base-url")) parsed.setOllamaBaseUrl(props.getProperty("strands.agent.ollama.base-url"));
        if (props.containsKey("strands.agent.tools")) parsed.setTools(parseCsv(props.getProperty("strands.agent.tools")));
        if (props.containsKey("strands.agent.plugins")) parsed.setPlugins(parseCsv(props.getProperty("strands.agent.plugins")));
        if (props.containsKey("strands.agent.skills.initial")) parsed.setInitialSkills(parseCsv(props.getProperty("strands.agent.skills.initial")));
        mergeConfig(parsed);
        hasProperties = true;
        return this;
    }

    public StrandsAgentBuilder fromEnv() {
        var parsed = new ConfigModel();
        var apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey != null) parsed.setApiKey(apiKey);
        var baseUrl = System.getenv("OPENAI_BASE_URL");
        if (baseUrl != null) parsed.setBaseUrl(baseUrl);
        var model = System.getenv("OPENAI_MODEL");
        if (model != null) parsed.setModelName(model);
        var temp = System.getenv("LLM_TEMPERATURE");
        if (temp != null) parsed.setTemperature(Double.parseDouble(temp));
        var retries = System.getenv("LLM_MAX_RETRIES");
        if (retries != null) parsed.setMaxRetries(Integer.parseInt(retries));
        var prompt = System.getenv("STRANDS_SYSTEM_PROMPT");
        if (prompt != null) parsed.setSystemPrompt(prompt);
        var skillsDir = System.getenv("STRANDS_SKILLS_DIR");
        if (skillsDir != null) parsed.setSkillsDir(skillsDir);
        var ollamaBaseUrl = System.getenv("OLLAMA_BASE_URL");
        if (ollamaBaseUrl != null) parsed.setOllamaBaseUrl(ollamaBaseUrl);
        mergeConfig(parsed);
        hasEnv = true;
        return this;
    }

    public StrandsAgentBuilder fromRedis(String key) {
        throw new UnsupportedOperationException(
            "Redis config loading is not yet implemented. Key: " + key);
    }

    public StrandsAgentBuilder name(String name) {
        this.agentName = name;
        return this;
    }

    public StrandsAgentBuilder withModelName(String modelName) {
        programmaticOverrides.setModelName(modelName);
        return this;
    }

    public StrandsAgentBuilder withSystemPrompt(String systemPrompt) {
        programmaticOverrides.setSystemPrompt(systemPrompt);
        return this;
    }

    public StrandsAgentBuilder withApiKey(String apiKey) {
        programmaticOverrides.setApiKey(apiKey);
        return this;
    }

    public StrandsAgentBuilder withBaseUrl(String baseUrl) {
        programmaticOverrides.setBaseUrl(baseUrl);
        return this;
    }

    public StrandsAgentBuilder withTemperature(double temperature) {
        programmaticOverrides.setTemperature(temperature);
        return this;
    }

    public StrandsAgentBuilder withMaxRetries(int maxRetries) {
        programmaticOverrides.setMaxRetries(maxRetries);
        return this;
    }

    public StrandsAgentBuilder withProvider(ModelProviderType provider) {
        programmaticOverrides.setProvider(provider);
        return this;
    }

    public StrandsAgentBuilder withMaxIterations(int maxIterations) {
        programmaticOverrides.setMaxIterations(maxIterations);
        return this;
    }

    public StrandsAgentBuilder withMaxMessages(int maxMessages) {
        programmaticOverrides.setMaxMessages(maxMessages);
        return this;
    }

    public StrandsAgentBuilder withTool(String className) {
        programmaticTools.add(className);
        return this;
    }

    public StrandsAgentBuilder withToolInstance(Object toolInstance) {
        programmaticToolInstances.add(toolInstance);
        return this;
    }

    public StrandsAgentBuilder withPlugin(Plugin plugin) {
        programmaticPlugins.add(plugin);
        return this;
    }

    public StrandsAgentBuilder withSkillsDir(String skillsDir) {
        programmaticOverrides.setSkillsDir(skillsDir);
        return this;
    }

    public StrandsAgentBuilder withInitialSkills(List<String> skills) {
        programmaticOverrides.setInitialSkills(skills);
        return this;
    }

    public StrandsAgentBuilder withOllamaBaseUrl(String ollamaBaseUrl) {
        programmaticOverrides.setOllamaBaseUrl(ollamaBaseUrl);
        return this;
    }

    public StrandsAgent build() {
        var merged = resolveConfig();

        var chatModel = createChatModel(merged);
        var toolRegistry = buildToolRegistry(merged);
        var plugins = buildPlugins(merged);
        var convManager = buildConversationManager(merged);
        var sessionManager = buildSessionManager(merged);
        var chatMemoryStore = buildChatMemoryStore(merged);
        var resilienceConfig = merged.toResilienceConfig();
        var structuredOutputConfig = buildStructuredOutputConfig(merged);

        var agent = new Agent(chatModel, toolRegistry, new ToolExecutor(),
            convManager, sessionManager, chatMemoryStore, resilienceConfig, plugins);
        if (merged.getSystemPrompt() != null && !merged.getSystemPrompt().isBlank()) {
            agent.setSystemPrompt(merged.getSystemPrompt());
        }
        if (structuredOutputConfig != null) {
            agent.setStructuredOutputConfig(structuredOutputConfig);
        }
        return new DefaultStrandsAgent(agent, agentName);
    }

    private ConfigModel resolveConfig() {
        var result = ConfigModel.defaults();
        applyNonNull(result, config);
        applyNonNull(result, programmaticOverrides);

        if (!programmaticTools.isEmpty()) {
            var allTools = new ArrayList<String>();
            if (result.getTools() != null) allTools.addAll(result.getTools());
            for (var t : programmaticTools) {
                if (!allTools.contains(t)) allTools.add(t);
            }
            result.setTools(allTools);
        }

        result.withInterpolatedEnv();
        return result;
    }

    private static void applyNonNull(ConfigModel target, ConfigModel source) {
        if (source.getModelName() != null) target.setModelName(source.getModelName());
        if (source.getSystemPrompt() != null) target.setSystemPrompt(source.getSystemPrompt());
        if (source.getApiKey() != null) target.setApiKey(source.getApiKey());
        if (source.getBaseUrl() != null) target.setBaseUrl(source.getBaseUrl());
        if (source.getTemperature() != null) target.setTemperature(source.getTemperature());
        if (source.getMaxRetries() != null) target.setMaxRetries(source.getMaxRetries());
        if (source.getProvider() != null) target.setProvider(source.getProvider());
        if (source.getMaxIterations() != null) target.setMaxIterations(source.getMaxIterations());
        if (source.getMaxMessages() != null) target.setMaxMessages(source.getMaxMessages());
        if (source.getSessionManager() != null) target.setSessionManager(source.getSessionManager());
        if (source.getConversationManager() != null) target.setConversationManager(source.getConversationManager());
        if (source.getChatMemoryStore() != null) target.setChatMemoryStore(source.getChatMemoryStore());
        if (source.getSkillsDir() != null) target.setSkillsDir(source.getSkillsDir());
        if (source.getOllamaBaseUrl() != null) target.setOllamaBaseUrl(source.getOllamaBaseUrl());
        if (source.getInitialSkills() != null) target.setInitialSkills(source.getInitialSkills());
        if (source.getTools() != null) target.setTools(source.getTools());
        if (source.getPlugins() != null) target.setPlugins(source.getPlugins());
        if (source.getTiered() != null) target.setTiered(source.getTiered());
        if (source.getStructuredOutput() != null) target.setStructuredOutput(source.getStructuredOutput());
        if (source.getResilience() != null) {
            var srcRes = source.getResilience();
            var tgtRes = target.getResilience();
            if (tgtRes == null) tgtRes = new ConfigModel.ResilienceModel();
            if (srcRes.getRetry() != null) {
                var srcR = srcRes.getRetry();
                var tgtR = tgtRes.getRetry();
                if (tgtR == null) tgtR = new ConfigModel.RetryModel();
                if (srcR.getMaxAttempts() != null) tgtR.setMaxAttempts(srcR.getMaxAttempts());
                if (srcR.getBackoffDelayMs() != null) tgtR.setBackoffDelayMs(srcR.getBackoffDelayMs());
                if (srcR.getBackoffMultiplier() != null) tgtR.setBackoffMultiplier(srcR.getBackoffMultiplier());
                tgtRes.setRetry(tgtR);
            }
            if (srcRes.getCircuitBreaker() != null) {
                var srcC = srcRes.getCircuitBreaker();
                var tgtC = tgtRes.getCircuitBreaker();
                if (tgtC == null) tgtC = new ConfigModel.CircuitBreakerModel();
                if (srcC.getFailureRateThreshold() != null) tgtC.setFailureRateThreshold(srcC.getFailureRateThreshold());
                if (srcC.getSlidingWindowSeconds() != null) tgtC.setSlidingWindowSeconds(srcC.getSlidingWindowSeconds());
                if (srcC.getHalfOpenDelaySeconds() != null) tgtC.setHalfOpenDelaySeconds(srcC.getHalfOpenDelaySeconds());
                tgtRes.setCircuitBreaker(tgtC);
            }
            target.setResilience(tgtRes);
        }
    }

    private ChatModel createChatModel(ConfigModel cfg) {
        var modelConfig = toChatModelConfig(cfg);
        try {
            return ModelFactory.createChatModel(modelConfig);
        } catch (Exception e) {
            throw new StrandsAgentException("Failed to create ChatModel: " + e.getMessage(), e);
        }
    }

    private static ChatModelConfig toChatModelConfig(ConfigModel cfg) {
        return new ChatModelConfig(
            cfg.getProvider() != null ? cfg.getProvider() : ModelProviderType.OPENAI,
            cfg.getApiKey(),
            cfg.getBaseUrl(),
            cfg.getModelName() != null ? cfg.getModelName() : "gpt-4o-mini",
            cfg.getTemperature(),
            cfg.getMaxRetries(),
            cfg.getOllamaBaseUrl()
        );
    }

    private ToolRegistry buildToolRegistry(ConfigModel cfg) {
        var builder = ToolRegistry.builder();
        if (cfg.getTools() != null) {
            for (var toolName : cfg.getTools()) {
                registerToolByName(builder, toolName);
            }
        }
        for (var instance : programmaticToolInstances) {
            if (instance instanceof de.augmentia.strandsagents.features.tools.AgentTool<?> at) {
                builder.with(at);
            } else {
                builder.with(instance);
            }
        }
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private static void registerToolByName(ToolRegistry.Builder builder, String className) {
        try {
            var clazz = Class.forName(className);
            var instance = clazz.getDeclaredConstructor().newInstance();
            if (instance instanceof de.augmentia.strandsagents.features.tools.AgentTool<?> at) {
                builder.with(at);
            } else {
                builder.with(instance);
            }
        } catch (Exception e) {
            throw new StrandsAgentException("Failed to load tool: " + className, e);
        }
    }

    private List<Plugin> buildPlugins(ConfigModel cfg) {
        var result = new ArrayList<>(programmaticPlugins);
        if (cfg.getPlugins() != null) {
            for (var className : cfg.getPlugins()) {
                try {
                    var clazz = Class.forName(className);
                    var instance = (Plugin) clazz.getDeclaredConstructor().newInstance();
                    result.add(instance);
                } catch (Exception e) {
                    throw new StrandsAgentException("Failed to load plugin: " + className, e);
                }
            }
        }
        return result;
    }

    private ConversationManager buildConversationManager(ConfigModel cfg) {
        if (cfg.getConversationManager() != null) {
            try {
                var clazz = Class.forName(cfg.getConversationManager());
                return (ConversationManager) clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new StrandsAgentException("Failed to create ConversationManager: " + cfg.getConversationManager(), e);
            }
        }
        return new SlidingWindowConversationManager(cfg.getMaxMessages());
    }

    private SessionManager buildSessionManager(ConfigModel cfg) {
        if (cfg.getSessionManager() != null) {
            try {
                var clazz = Class.forName(cfg.getSessionManager());
                return (SessionManager) clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new StrandsAgentException("Failed to create SessionManager: " + cfg.getSessionManager(), e);
            }
        }
        return null;
    }

    private ChatMemoryStore buildChatMemoryStore(ConfigModel cfg) {
        if (cfg.getChatMemoryStore() != null) {
            try {
                var clazz = Class.forName(cfg.getChatMemoryStore());
                return (ChatMemoryStore) clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new StrandsAgentException("Failed to create ChatMemoryStore: " + cfg.getChatMemoryStore(), e);
            }
        }
        return null;
    }

    private StructuredOutputConfig buildStructuredOutputConfig(ConfigModel cfg) {
        var so = cfg.getStructuredOutput();
        if (so == null) return null;
        if ("static".equalsIgnoreCase(so.getMode()) && so.getOutputClass() != null) {
            try {
                var clazz = Class.forName(so.getOutputClass());
                return so.getForcePrompt() != null
                    ? StructuredOutputConfig.staticModel(clazz, so.getForcePrompt())
                    : StructuredOutputConfig.staticModel(clazz);
            } catch (ClassNotFoundException e) {
                throw new StrandsAgentException("Structured output class not found: " + so.getOutputClass(), e);
            }
        }
        if ("dynamic".equalsIgnoreCase(so.getMode()) && so.getJsonSchema() != null) {
            return so.getForcePrompt() != null
                ? StructuredOutputConfig.dynamicSchema(so.getJsonSchema(), so.getForcePrompt())
                : StructuredOutputConfig.dynamicSchema(so.getJsonSchema());
        }
        return null;
    }

    private static List<String> parseCsv(String s) {
        if (s == null || s.isBlank()) return List.of();
        return List.of(s.split(",")).stream().map(String::strip)
            .filter(x -> !x.isEmpty()).toList();
    }

    private void mergeConfig(ConfigModel source) {
        applyNonNull(config, source);
    }
}
