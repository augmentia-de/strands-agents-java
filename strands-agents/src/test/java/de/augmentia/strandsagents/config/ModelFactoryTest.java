package de.augmentia.strandsagents.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ModelFactoryTest {

    @AfterEach
    void restoreDefaults() {
        ModelFactory.register(ModelProviderType.OPENAI, new ModelFactory.OpenAiProvider());
        ModelFactory.register(ModelProviderType.OLLAMA, new ModelFactory.OllamaProvider());
        ModelFactory.register(ModelProviderType.OPENAI_COMPATIBLE, new ModelFactory.OpenAiCompatibleProvider());
    }

    @Nested
    class providerRegistration {

        @Test
        void customProviderIsUsed() {
            var config = new ChatModelConfig(ModelProviderType.OPENAI, null, null, "test-model", null, null, Map.of(), null, null);
            ModelFactory.register(ModelProviderType.OPENAI, new CapturingProvider());
            var model = ModelFactory.createChatModel(config);
            assertThat(model).isNotNull();
        }

        @Test
        void nullProviderType_throws() {
            var config = new ChatModelConfig(null, null, null, null, null, null, Map.of(), null, null);
            assertThatThrownBy(() -> ModelFactory.createChatModel(config))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class tierBased {

        @Test
        void createChatModel_usesCorrectTier() {
            ModelFactory.register(ModelProviderType.OPENAI, new CapturingProvider("simple-model"));
            ModelFactory.register(ModelProviderType.OLLAMA, new CapturingProvider("advanced-model"));
            var simpleCfg = new ChatModelConfig(ModelProviderType.OPENAI, null, null, "simple", null, null, Map.of(), null, null);
            var advancedCfg = new ChatModelConfig(ModelProviderType.OLLAMA, null, null, "advanced", null, null, Map.of(), null, null);
            var tc = new TieredModelConfig(simpleCfg, advancedCfg, ModelTier.SIMPLE);
            var model = ModelFactory.createChatModel(ModelTier.ADVANCED, tc);
            assertThat(model.toString()).contains("advanced-model");
        }
    }

    static class MockSyncModel implements ChatModel {
        @Override
        public ChatResponse chat(ChatRequest request) {
            return ChatResponse.builder()
                .aiMessage(AiMessage.from("mock"))
                .tokenUsage(new TokenUsage(0, 0))
                .finishReason(FinishReason.STOP)
                .build();
        }
    }

    static class CapturingProvider implements ModelProvider {
        private final String label;

        CapturingProvider() { this("mock"); }
        CapturingProvider(String label) { this.label = label; }

        @Override
        public ChatModel createChatModel(ChatModelConfig config) {
            return new ChatModel() {
                @Override
                public ChatResponse chat(ChatRequest request) {
                    return ChatResponse.builder()
                        .aiMessage(AiMessage.from(label))
                        .tokenUsage(new TokenUsage(0, 0))
                        .finishReason(FinishReason.STOP)
                        .build();
                }

                @Override
                public String toString() {
                    return label;
                }
            };
        }

        @Override
        public StreamingChatModel createStreamingChatModel(ChatModelConfig config) {
            return null;
        }
    }
}
