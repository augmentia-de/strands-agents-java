package de.augmentia.strandsagents.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PromptRegistryTest {

    @Test
    void configure_thenGet_returnsConfiguredValue() {
        var pm = new PromptManager() {
            @Override
            public String get(String key, Object... args) {
                return "custom:" + key;
            }
        };
        PromptRegistry.configure(pm);
        try {
            assertThat(PromptRegistry.get("test.key")).isEqualTo("custom:test.key");
        } finally {
            PromptRegistry.configure(null);
        }
    }

    @Test
    void getOrDefault_returnsFallbackWhenMissing() {
        var pm = new PromptManager() {
            @Override
            public String get(String key, Object... args) {
                return null;
            }
        };
        PromptRegistry.configure(pm);
        try {
            assertThat(PromptRegistry.getOrDefault("missing.key", "fallback")).isEqualTo("fallback");
        } finally {
            PromptRegistry.configure(null);
        }
    }

    @Test
    void getOrDefault_returnsActualWhenPresent() {
        var pm = new PromptManager() {
            @Override
            public String get(String key, Object... args) {
                return "actual";
            }
        };
        PromptRegistry.configure(pm);
        try {
            assertThat(PromptRegistry.getOrDefault("present.key", "fallback")).isEqualTo("actual");
        } finally {
            PromptRegistry.configure(null);
        }
    }
}
