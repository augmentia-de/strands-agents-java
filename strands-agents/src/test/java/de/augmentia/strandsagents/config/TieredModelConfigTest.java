package de.augmentia.strandsagents.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TieredModelConfigTest {

    private final ChatModelConfig simple = new ChatModelConfig(ModelProviderType.OPENAI, "sk-simple", null, "gpt-4o-mini", null, null, Map.of(), null, null);
    private final ChatModelConfig advanced = new ChatModelConfig(ModelProviderType.OPENAI, "sk-adv", null, "gpt-4o", null, null, Map.of(), null, null);

    @Test
    void forTier_simple_returnsSimple() {
        var tc = new TieredModelConfig(simple, advanced, ModelTier.SIMPLE);
        assertThat(tc.forTier(ModelTier.SIMPLE)).isSameAs(simple);
    }

    @Test
    void forTier_advanced_returnsAdvanced() {
        var tc = new TieredModelConfig(simple, advanced, ModelTier.SIMPLE);
        assertThat(tc.forTier(ModelTier.ADVANCED)).isSameAs(advanced);
    }

    @Test
    void forTier_routing_fallsBackToSimple() {
        var tc = new TieredModelConfig(simple, advanced, ModelTier.SIMPLE);
        assertThat(tc.forTier(ModelTier.ROUTING)).isSameAs(simple);
    }

    @Test
    void defaultTier_isAccessible() {
        var tc = new TieredModelConfig(simple, advanced, ModelTier.ADVANCED);
        assertThat(tc.defaultTier()).isEqualTo(ModelTier.ADVANCED);
    }
}
