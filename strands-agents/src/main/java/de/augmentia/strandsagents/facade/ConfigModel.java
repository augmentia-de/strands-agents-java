package de.augmentia.strandsagents.facade;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.augmentia.strandsagents.config.ModelProviderType;
import de.augmentia.strandsagents.config.ModelTier;
import de.augmentia.strandsagents.interceptor.resilience.CircuitBreakerConfig;
import de.augmentia.strandsagents.interceptor.resilience.ResilienceConfig;
import de.augmentia.strandsagents.interceptor.resilience.RetryConfig;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ConfigModel {

    private String modelName;
    private String systemPrompt;
    private String apiKey;
    private String baseUrl;
    private Double temperature;
    private Integer maxRetries;
    private ModelProviderType provider;
    private Integer maxIterations;
    private Integer maxMessages;
    private Integer maxTokens;
    private Integer keepLastUserMessages;
    private String sessionManager;
    private String conversationManager;
    private String chatMemoryStore;
    private List<String> tools;
    private List<String> plugins;
    private String skillsDir;
    private List<String> initialSkills;
    private ResilienceModel resilience;
    private TieredModel tiered;
    private StructuredOutputModel structuredOutput;
    private String ollamaBaseUrl;
    private Boolean logRequests;
    private Boolean logResponses;

    public ConfigModel() {}

    @JsonProperty("model")
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public Integer getMaxRetries() { return maxRetries; }
    public void setMaxRetries(Integer maxRetries) { this.maxRetries = maxRetries; }

    public ModelProviderType getProvider() { return provider; }
    public void setProvider(ModelProviderType provider) { this.provider = provider; }

    public Integer getMaxIterations() { return maxIterations; }
    public void setMaxIterations(Integer maxIterations) { this.maxIterations = maxIterations; }

    public Integer getMaxMessages() { return maxMessages; }
    public void setMaxMessages(Integer maxMessages) { this.maxMessages = maxMessages; }

    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }

    public Integer getKeepLastUserMessages() { return keepLastUserMessages; }
    public void setKeepLastUserMessages(Integer keepLastUserMessages) { this.keepLastUserMessages = keepLastUserMessages; }

    public String getSessionManager() { return sessionManager; }
    public void setSessionManager(String sessionManager) { this.sessionManager = sessionManager; }

    public String getConversationManager() { return conversationManager; }
    public void setConversationManager(String conversationManager) { this.conversationManager = conversationManager; }

    public String getChatMemoryStore() { return chatMemoryStore; }
    public void setChatMemoryStore(String chatMemoryStore) { this.chatMemoryStore = chatMemoryStore; }

    public List<String> getTools() { return tools; }
    public void setTools(List<String> tools) { this.tools = tools; }

    public List<String> getPlugins() { return plugins; }
    public void setPlugins(List<String> plugins) { this.plugins = plugins; }

    public String getSkillsDir() { return skillsDir; }
    public void setSkillsDir(String skillsDir) { this.skillsDir = skillsDir; }

    public List<String> getInitialSkills() { return initialSkills; }
    public void setInitialSkills(List<String> initialSkills) { this.initialSkills = initialSkills; }

    public ResilienceModel getResilience() { return resilience; }
    public void setResilience(ResilienceModel resilience) { this.resilience = resilience; }

    public TieredModel getTiered() { return tiered; }
    public void setTiered(TieredModel tiered) { this.tiered = tiered; }

    public StructuredOutputModel getStructuredOutput() { return structuredOutput; }
    public void setStructuredOutput(StructuredOutputModel structuredOutput) { this.structuredOutput = structuredOutput; }

    public String getOllamaBaseUrl() { return ollamaBaseUrl; }
    public void setOllamaBaseUrl(String ollamaBaseUrl) { this.ollamaBaseUrl = ollamaBaseUrl; }

    public Boolean getLogRequests() { return logRequests; }
    public void setLogRequests(Boolean logRequests) { this.logRequests = logRequests; }

    public Boolean getLogResponses() { return logResponses; }
    public void setLogResponses(Boolean logResponses) { this.logResponses = logResponses; }

    public ConfigModel withInterpolatedEnv() {
        if (apiKey != null) apiKey = interpolate(apiKey);
        if (baseUrl != null) baseUrl = interpolate(baseUrl);
        if (modelName != null) modelName = interpolate(modelName);
        if (systemPrompt != null) systemPrompt = interpolate(systemPrompt);
        if (sessionManager != null) sessionManager = interpolate(sessionManager);
        if (conversationManager != null) conversationManager = interpolate(conversationManager);
        if (chatMemoryStore != null) chatMemoryStore = interpolate(chatMemoryStore);
        if (skillsDir != null) skillsDir = interpolate(skillsDir);
        if (ollamaBaseUrl != null) ollamaBaseUrl = interpolate(ollamaBaseUrl);
        if (initialSkills != null) initialSkills = initialSkills.stream().map(ConfigModel::interpolate).toList();
        if (tools != null) tools = tools.stream().map(ConfigModel::interpolate).toList();
        if (plugins != null) plugins = plugins.stream().map(ConfigModel::interpolate).toList();
        return this;
    }

    static String interpolate(String value) {
        if (value == null) return null;
        int start = value.indexOf("${");
        if (start < 0) return value;
        var sb = new StringBuilder(value.length());
        int pos = 0;
        while (start >= 0) {
            sb.append(value, pos, start);
            int end = value.indexOf('}', start + 2);
            if (end < 0) {
                sb.append(value.substring(start));
                return sb.toString();
            }
            String key = value.substring(start + 2, end);
            String envVal = System.getenv(key);
            sb.append(envVal != null ? envVal : "${" + key + "}");
            pos = end + 1;
            start = value.indexOf("${", pos);
        }
        sb.append(value.substring(pos));
        return sb.toString();
    }

    public ResilienceConfig toResilienceConfig() {
        if (resilience == null) return ResilienceConfig.DEFAULT;
        var retry = resilience.retry != null
            ? new RetryConfig(
                resilience.retry.maxAttempts != null ? resilience.retry.maxAttempts : 3,
                resilience.retry.backoffDelayMs != null ? resilience.retry.backoffDelayMs : 1000,
                resilience.retry.backoffMultiplier != null ? resilience.retry.backoffMultiplier : 2.0)
            : RetryConfig.DEFAULT;
        var cb = resilience.circuitBreaker != null
            ? new CircuitBreakerConfig(
                resilience.circuitBreaker.failureRateThreshold != null ? resilience.circuitBreaker.failureRateThreshold : 0.5f,
                resilience.circuitBreaker.slidingWindowSeconds != null ? resilience.circuitBreaker.slidingWindowSeconds : 10,
                resilience.circuitBreaker.halfOpenDelaySeconds != null ? resilience.circuitBreaker.halfOpenDelaySeconds : 30)
            : CircuitBreakerConfig.DEFAULT;
        return new ResilienceConfig(retry, cb);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResilienceModel {
        private RetryModel retry;
        private CircuitBreakerModel circuitBreaker;

        public RetryModel getRetry() { return retry; }
        public void setRetry(RetryModel retry) { this.retry = retry; }

        public CircuitBreakerModel getCircuitBreaker() { return circuitBreaker; }
        public void setCircuitBreaker(CircuitBreakerModel circuitBreaker) { this.circuitBreaker = circuitBreaker; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RetryModel {
        private Integer maxAttempts;
        private Long backoffDelayMs;
        private Double backoffMultiplier;

        public Integer getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(Integer maxAttempts) { this.maxAttempts = maxAttempts; }

        public Long getBackoffDelayMs() { return backoffDelayMs; }
        public void setBackoffDelayMs(Long backoffDelayMs) { this.backoffDelayMs = backoffDelayMs; }

        public Double getBackoffMultiplier() { return backoffMultiplier; }
        public void setBackoffMultiplier(Double backoffMultiplier) { this.backoffMultiplier = backoffMultiplier; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CircuitBreakerModel {
        private Float failureRateThreshold;
        private Long slidingWindowSeconds;
        private Long halfOpenDelaySeconds;

        public Float getFailureRateThreshold() { return failureRateThreshold; }
        public void setFailureRateThreshold(Float failureRateThreshold) { this.failureRateThreshold = failureRateThreshold; }

        public Long getSlidingWindowSeconds() { return slidingWindowSeconds; }
        public void setSlidingWindowSeconds(Long slidingWindowSeconds) { this.slidingWindowSeconds = slidingWindowSeconds; }

        public Long getHalfOpenDelaySeconds() { return halfOpenDelaySeconds; }
        public void setHalfOpenDelaySeconds(Long halfOpenDelaySeconds) { this.halfOpenDelaySeconds = halfOpenDelaySeconds; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TieredModel {
        private ChatModelConfigModel simple;
        private ChatModelConfigModel advanced;
        private ModelTier defaultTier;

        public ChatModelConfigModel getSimple() { return simple; }
        public void setSimple(ChatModelConfigModel simple) { this.simple = simple; }

        public ChatModelConfigModel getAdvanced() { return advanced; }
        public void setAdvanced(ChatModelConfigModel advanced) { this.advanced = advanced; }

        public ModelTier getDefaultTier() { return defaultTier; }
        public void setDefaultTier(ModelTier defaultTier) { this.defaultTier = defaultTier; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChatModelConfigModel {
        private ModelProviderType provider;
        private String apiKey;
        private String baseUrl;
        private String modelName;
        private Double temperature;
        private Integer maxRetries;
        private String ollamaBaseUrl;
        private Boolean logRequests;
        private Boolean logResponses;

        public ModelProviderType getProvider() { return provider; }
        public void setProvider(ModelProviderType provider) { this.provider = provider; }

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

        public String getModelName() { return modelName; }
        public void setModelName(String modelName) { this.modelName = modelName; }

        public Double getTemperature() { return temperature; }
        public void setTemperature(Double temperature) { this.temperature = temperature; }

        public Integer getMaxRetries() { return maxRetries; }
        public void setMaxRetries(Integer maxRetries) { this.maxRetries = maxRetries; }

        public String getOllamaBaseUrl() { return ollamaBaseUrl; }
        public void setOllamaBaseUrl(String ollamaBaseUrl) { this.ollamaBaseUrl = ollamaBaseUrl; }

        public Boolean getLogRequests() { return logRequests; }
        public void setLogRequests(Boolean logRequests) { this.logRequests = logRequests; }

        public Boolean getLogResponses() { return logResponses; }
        public void setLogResponses(Boolean logResponses) { this.logResponses = logResponses; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StructuredOutputModel {
        private String mode;
        private String outputClass;
        private String jsonSchema;
        private String forcePrompt;

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }

        public String getOutputClass() { return outputClass; }
        public void setOutputClass(String outputClass) { this.outputClass = outputClass; }

        public String getJsonSchema() { return jsonSchema; }
        public void setJsonSchema(String jsonSchema) { this.jsonSchema = jsonSchema; }

        public String getForcePrompt() { return forcePrompt; }
        public void setForcePrompt(String forcePrompt) { this.forcePrompt = forcePrompt; }
    }

    @Override
    public String toString() {
        return "ConfigModel{" +
            "modelName='" + modelName + '\'' +
            ", provider=" + provider +
            ", maxIterations=" + maxIterations +
            ", maxMessages=" + maxMessages +
            ", tools=" + tools +
            ", plugins=" + plugins +
            ", skillsDir='" + skillsDir + '\'' +
            '}';
    }

    public static ConfigModel defaults() {
        var model = new ConfigModel();
        model.modelName = "gpt-4o-mini";
        model.provider = ModelProviderType.OPENAI;
        model.maxIterations = 5;
        model.maxMessages = 20;
        model.maxTokens = 4000;
        model.keepLastUserMessages = 3;
        model.temperature = 0.7;
        model.resilience = new ResilienceModel();
        model.resilience.retry = new RetryModel();
        model.resilience.retry.maxAttempts = 3;
        model.resilience.retry.backoffDelayMs = 1000L;
        model.resilience.retry.backoffMultiplier = 2.0;
        return model;
    }
}
