package de.augmentia.strandsagents.facade;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.augmentia.strandsagents.config.ModelProviderType;
import de.augmentia.strandsagents.config.ModelTier;
import de.augmentia.strandsagents.features.resilience.ResilienceConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigModelTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void defaults_shouldProvideSensibleDefaults() {
        var model = ConfigModel.defaults();
        assertThat(model.getModelName()).isEqualTo("gpt-4o-mini");
        assertThat(model.getProvider()).isEqualTo(ModelProviderType.OPENAI);
        assertThat(model.getMaxIterations()).isEqualTo(10);
        assertThat(model.getMaxMessages()).isEqualTo(20);
        assertThat(model.getTemperature()).isEqualTo(0.7);
    }

    @Test
    void fromJson_shouldDeserializeAllFields() throws Exception {
        var json = """
            {
                "model": "gpt-4o",
                "systemPrompt": "You are a helpful assistant",
                "apiKey": "sk-test",
                "baseUrl": "https://api.openai.com",
                "temperature": 0.5,
                "maxRetries": 5,
                "provider": "OPENAI",
                "maxIterations": 15,
                "maxMessages": 30,
                "tools": ["de.augmentia.strandsagents.features.tools.WebSearchTool"],
                "skillsDir": "/custom/skills",
                "initialSkills": ["skill1", "skill2"],
                "ollamaBaseUrl": "http://localhost:11434"
            }
            """;

        var model = MAPPER.readValue(json, ConfigModel.class);
        assertThat(model.getModelName()).isEqualTo("gpt-4o");
        assertThat(model.getSystemPrompt()).isEqualTo("You are a helpful assistant");
        assertThat(model.getApiKey()).isEqualTo("sk-test");
        assertThat(model.getBaseUrl()).isEqualTo("https://api.openai.com");
        assertThat(model.getTemperature()).isEqualTo(0.5);
        assertThat(model.getMaxRetries()).isEqualTo(5);
        assertThat(model.getProvider()).isEqualTo(ModelProviderType.OPENAI);
        assertThat(model.getMaxIterations()).isEqualTo(15);
        assertThat(model.getMaxMessages()).isEqualTo(30);
        assertThat(model.getTools()).containsExactly("de.augmentia.strandsagents.features.tools.WebSearchTool");
        assertThat(model.getSkillsDir()).isEqualTo("/custom/skills");
        assertThat(model.getInitialSkills()).containsExactly("skill1", "skill2");
        assertThat(model.getOllamaBaseUrl()).isEqualTo("http://localhost:11434");
    }

    @Test
    void fromJson_shouldIgnoreUnknownProperties() throws Exception {
        var json = """
            {
                "model": "gpt-4o",
                "unknownField": "shouldBeIgnored",
                "extraNested": {"foo": "bar"}
            }
            """;

        var model = MAPPER.readValue(json, ConfigModel.class);
        assertThat(model.getModelName()).isEqualTo("gpt-4o");
    }

    @Test
    void fromJson_shouldDeserializeResilienceConfig() throws Exception {
        var json = """
            {
                "resilience": {
                    "retry": {
                        "maxAttempts": 5,
                        "backoffDelayMs": 2000,
                        "backoffMultiplier": 3.0
                    },
                    "circuitBreaker": {
                        "failureRateThreshold": 0.3,
                        "slidingWindowSeconds": 60,
                        "halfOpenDelaySeconds": 15
                    }
                }
            }
            """;

        var model = MAPPER.readValue(json, ConfigModel.class);
        var res = model.getResilience();
        assertThat(res).isNotNull();
        assertThat(res.getRetry().getMaxAttempts()).isEqualTo(5);
        assertThat(res.getRetry().getBackoffDelayMs()).isEqualTo(2000);
        assertThat(res.getRetry().getBackoffMultiplier()).isEqualTo(3.0);
        assertThat(res.getCircuitBreaker().getFailureRateThreshold()).isEqualTo(0.3f);
        assertThat(res.getCircuitBreaker().getSlidingWindowSeconds()).isEqualTo(60);
        assertThat(res.getCircuitBreaker().getHalfOpenDelaySeconds()).isEqualTo(15);
    }

    @Test
    void toResilienceConfig_shouldConvertToDomainType() {
        var model = ConfigModel.defaults();
        var rc = model.toResilienceConfig();
        assertThat(rc).isNotNull();
        assertThat(rc.retryConfig().maxAttempts()).isEqualTo(3);
        assertThat(rc.retryConfig().backoffDelayMs()).isEqualTo(1000);
        assertThat(rc.retryConfig().backoffMultiplier()).isEqualTo(2.0);
        assertThat(rc.circuitBreakerConfig()).isNotNull();
    }

    @Test
    void interpolate_shouldReplaceEnvVars() {
        var original = System.getenv("HOME");
        var result = ConfigModel.interpolate("path: ${HOME}/subdir");
        assertThat(result).isEqualTo("path: " + original + "/subdir");
    }

    @Test
    void interpolate_shouldReturnOriginalWhenNoEnvVars() {
        assertThat(ConfigModel.interpolate("plain string")).isEqualTo("plain string");
    }

    @Test
    void interpolate_shouldReturnOriginalWhenNull() {
        assertThat(ConfigModel.interpolate(null)).isNull();
    }

    @Test
    void interpolate_shouldLeaveUnknownVarsAsIs() {
        var result = ConfigModel.interpolate("hello ${UNDEFINED_VAR_XYZ} world");
        assertThat(result).isEqualTo("hello ${UNDEFINED_VAR_XYZ} world");
    }

    @Test
    void withInterpolatedEnv_shouldReplaceInAllStringFields() {
        var home = System.getenv("HOME");
        var model = new ConfigModel();
        model.setApiKey("${HOME}_key");
        model.setBaseUrl("${HOME}/api");
        model.setModelName("${HOME}_model");
        model.setSystemPrompt("dir: ${HOME}");
        model.setSkillsDir("${HOME}/skills");

        model.withInterpolatedEnv();

        assertThat(model.getApiKey()).isEqualTo(home + "_key");
        assertThat(model.getBaseUrl()).isEqualTo(home + "/api");
        assertThat(model.getModelName()).isEqualTo(home + "_model");
        assertThat(model.getSystemPrompt()).isEqualTo("dir: " + home);
        assertThat(model.getSkillsDir()).isEqualTo(home + "/skills");
    }

    @Test
    void fromJson_shouldDeserializeStructuredOutput() throws Exception {
        var json = """
            {
                "structuredOutput": {
                    "mode": "static",
                    "outputClass": "java.lang.String",
                    "forcePrompt": "Please format as JSON"
                }
            }
            """;

        var model = MAPPER.readValue(json, ConfigModel.class);
        var so = model.getStructuredOutput();
        assertThat(so).isNotNull();
        assertThat(so.getMode()).isEqualTo("static");
        assertThat(so.getOutputClass()).isEqualTo("java.lang.String");
        assertThat(so.getForcePrompt()).isEqualTo("Please format as JSON");
    }

    @Test
    void fromJson_shouldDeserializeTieredModel() throws Exception {
        var json = """
            {
                "tiered": {
                    "simple": {
                        "modelName": "gpt-4o-mini",
                        "temperature": 0.3
                    },
                    "advanced": {
                        "modelName": "gpt-4o",
                        "temperature": 0.7
                    },
                    "defaultTier": "SIMPLE"
                }
            }
            """;

        var model = MAPPER.readValue(json, ConfigModel.class);
        var tiered = model.getTiered();
        assertThat(tiered).isNotNull();
        assertThat(tiered.getSimple().getModelName()).isEqualTo("gpt-4o-mini");
        assertThat(tiered.getSimple().getTemperature()).isEqualTo(0.3);
        assertThat(tiered.getAdvanced().getModelName()).isEqualTo("gpt-4o");
        assertThat(tiered.getAdvanced().getTemperature()).isEqualTo(0.7);
        assertThat(tiered.getDefaultTier()).isEqualTo(ModelTier.SIMPLE);
    }

    @Test
    void fromJson_shouldAcceptJsonPropertyAlias() throws Exception {
        var json = """
            {
                "model": "gpt-4o-turbo"
            }
            """;

        var model = MAPPER.readValue(json, ConfigModel.class);
        assertThat(model.getModelName()).isEqualTo("gpt-4o-turbo");
    }
}
