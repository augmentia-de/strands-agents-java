package de.augmentia.strandsagents.config;

import de.augmentia.strandsagents.interceptor.resilience.ResilienceConfig;
import de.augmentia.strandsagents.model.structured.StructuredOutputConfig;
import java.nio.file.Path;
import java.util.List;

public record AgentSettings(
    String name,
    String modelName,
    String systemPrompt,
    int maxToolIterations,
    ResilienceConfig resilienceConfig,
    Path skillsDir,
    List<String> initialSkills,
    StructuredOutputConfig structuredOutputConfig,
    Path llmLogPath,
    TieredModelConfig tieredConfig,
    ModelTier modelTier
) {
    public static final int DEFAULT_MAX_ITERATIONS = 10;
    public static final int DEFAULT_MAX_TOOL_ITERATIONS = 20;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name = "unnamed";
        private String modelName = "gpt-4o";
        private String systemPrompt = "";
        private int maxToolIterations = DEFAULT_MAX_TOOL_ITERATIONS;
        private ResilienceConfig resilienceConfig = ResilienceConfig.DEFAULT;
        private Path skillsDir = null;
        private List<String> initialSkills = List.of();
        private StructuredOutputConfig structuredOutputConfig = null;
        private Path llmLogPath = null;
        private TieredModelConfig tieredConfig = null;
        private ModelTier modelTier = null;

        Builder() {}

        public Builder name(String name) { this.name = name; return this; }
        public Builder modelName(String modelName) { this.modelName = modelName; return this; }
        public Builder systemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; return this; }
        public Builder maxToolIterations(int maxToolIterations) { this.maxToolIterations = maxToolIterations; return this; }
        public Builder resilienceConfig(ResilienceConfig resilienceConfig) { this.resilienceConfig = resilienceConfig; return this; }
        public Builder skillsDir(Path skillsDir) { this.skillsDir = skillsDir; return this; }
        public Builder initialSkills(List<String> initialSkills) { this.initialSkills = initialSkills; return this; }
        public Builder structuredOutputConfig(StructuredOutputConfig config) { this.structuredOutputConfig = config; return this; }
        public Builder structuredOutputModel(Class<?> modelClass) { this.structuredOutputConfig = StructuredOutputConfig.staticModel(modelClass); return this; }
        public Builder structuredOutputSchema(String jsonSchema) { this.structuredOutputConfig = StructuredOutputConfig.dynamicSchema(jsonSchema); return this; }
        public Builder logLlmCalls(Path path) { this.llmLogPath = path; return this; }
        public Builder tieredConfig(TieredModelConfig tieredConfig) { this.tieredConfig = tieredConfig; return this; }
        public Builder modelTier(ModelTier modelTier) { this.modelTier = modelTier; return this; }

        public AgentSettings build() {
            return new AgentSettings(name, modelName, systemPrompt, maxToolIterations, resilienceConfig,
                skillsDir, initialSkills, structuredOutputConfig, llmLogPath, tieredConfig, modelTier);
        }
    }
}
