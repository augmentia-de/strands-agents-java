package de.augmentia.strandsagents.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ModelProviderTypeTest {

    @ParameterizedTest
    @CsvSource({
        "openai, OPENAI",
        "OPENAI, OPENAI",
        "ollama, OLLAMA",
        "OLLAMA, OLLAMA",
        "openai-compatible, OPENAI_COMPATIBLE",
        "'', OPENAI",
        "garbage, OPENAI",
    })
    void fromString_parsesCorrectly(String input, String expected) {
        assertThat(ModelProviderType.fromString(input)).isEqualTo(ModelProviderType.valueOf(expected));
    }
}
