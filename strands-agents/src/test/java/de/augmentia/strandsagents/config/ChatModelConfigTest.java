package de.augmentia.strandsagents.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ChatModelConfigTest {

    @Nested
    class fromEnv {

        @Test
        void readsWithGivenPrefix() {
            var prefix = "TEST_CFG_";
            System.setProperty(prefix + "API_KEY", "sk-test");
            System.setProperty(prefix + "MODEL", "gpt-4o");
            try {
                var cfg = ChatModelConfig.fromEnv(prefix);
                assertThat(cfg.apiKey()).isEqualTo("sk-test");
                assertThat(cfg.modelName()).isEqualTo("gpt-4o");
            } finally {
                System.clearProperty(prefix + "API_KEY");
                System.clearProperty(prefix + "MODEL");
            }
        }

        @Test
        void missingFieldsAreNull() {
            var cfg = ChatModelConfig.fromEnv("NONEXISTENT_");
            assertThat(cfg.apiKey()).isNull();
            assertThat(cfg.baseUrl()).isNull();
            assertThat(cfg.modelName()).isNull();
            assertThat(cfg.temperature()).isNull();
            assertThat(cfg.maxRetries()).isNull();
        }
    }

    @Nested
    class fromEnvWithFallback {

        @Test
        void returnsFallbackWhenPrefixHasNoConfig() {
            var fallback = new ChatModelConfig(ModelProviderType.OPENAI, "sk-fallback", "http://fallback", "gpt-4", 0.5, 3, null);
            var result = ChatModelConfig.fromEnvWithFallback("NONEXISTENT_", fallback);
            assertThat(result.apiKey()).isEqualTo("sk-fallback");
            assertThat(result.modelName()).isEqualTo("gpt-4");
        }

        @Test
        void usesPrefixValuesWhenPresent() {
            var prefix = "TEST_PFX_";
            System.setProperty(prefix + "API_KEY", "sk-prefix");
            System.setProperty(prefix + "MODEL", "gpt-4o");
            var fallback = new ChatModelConfig(ModelProviderType.OPENAI, "sk-fallback", null, "gpt-4", null, null, null);
            try {
                var result = ChatModelConfig.fromEnvWithFallback(prefix, fallback);
                assertThat(result.apiKey()).isEqualTo("sk-prefix");
                assertThat(result.modelName()).isEqualTo("gpt-4o");
            } finally {
                System.clearProperty(prefix + "API_KEY");
                System.clearProperty(prefix + "MODEL");
            }
        }

        @Test
        void returnsNullWhenNoPrefixAndNoFallback() {
            var result = ChatModelConfig.fromEnvWithFallback("NONEXISTENT_", null);
            assertThat(result).isNull();
        }
    }

    @Nested
    class fromVault {

        @Test
        void overwritesFallbackWithSecrets() {
            var fallback = new ChatModelConfig(ModelProviderType.OPENAI, "sk-fallback", "http://fallback", "gpt-4", 0.5, 3, null);
            var secrets = Map.of(
                "api_key", "sk-vault",
                "base_url", "http://vault",
                "model", "gpt-4o"
            );
            var result = ChatModelConfig.fromVault(secrets, fallback);
            assertThat(result.apiKey()).isEqualTo("sk-vault");
            assertThat(result.baseUrl()).isEqualTo("http://vault");
            assertThat(result.modelName()).isEqualTo("gpt-4o");
            assertThat(result.temperature()).isNull();
            assertThat(result.maxRetries()).isNull();
        }

        @Test
        void keepsFallbackWhenSecretMissing() {
            var fallback = new ChatModelConfig(ModelProviderType.OPENAI, "sk-fallback", null, "gpt-4", 0.5, null, null);
            var result = ChatModelConfig.fromVault(Map.of(), fallback);
            assertThat(result.apiKey()).isEqualTo("sk-fallback");
            assertThat(result.modelName()).isEqualTo("gpt-4");
        }
    }

    @Nested
    class withMethods {

        @Test
        void withApiKey_returnsNewCopy() {
            var original = new ChatModelConfig(ModelProviderType.OPENAI, "sk-old", null, "gpt-4", null, null, null);
            var modified = original.withApiKey("sk-new");
            assertThat(modified.apiKey()).isEqualTo("sk-new");
            assertThat(original.apiKey()).isEqualTo("sk-old");
        }

        @Test
        void withModelName_returnsNewCopy() {
            var original = new ChatModelConfig(ModelProviderType.OPENAI, "sk-test", null, "gpt-4", null, null, null);
            var modified = original.withModelName("gpt-4o");
            assertThat(modified.modelName()).isEqualTo("gpt-4o");
            assertThat(original.modelName()).isEqualTo("gpt-4");
        }
    }

    @Nested
    class toLlmConfig {

        @Test
        void convertsFields() {
            var cfg = new ChatModelConfig(ModelProviderType.OPENAI, "sk-test", "http://test", "gpt-4", 0.5, 3, null);
            var llm = cfg.toLlmConfig();
            assertThat(llm.apiKey()).isEqualTo("sk-test");
            assertThat(llm.baseUrl()).isEqualTo("http://test");
            assertThat(llm.modelName()).isEqualTo("gpt-4");
            assertThat(llm.temperature()).isEqualTo(0.5);
            assertThat(llm.maxRetries()).isEqualTo(3);
        }
    }
}
