package de.augmentia.strandsagents.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ModelTierTest {

    @ParameterizedTest
    @CsvSource({
        "simple, SIMPLE",
        "SIMPLE, SIMPLE",
        "advanced, ADVANCED",
        "ADVANCED, ADVANCED",
        "routing, ROUTING",
        "ROUTING, ROUTING",
        "'', SIMPLE",
        "unknown, SIMPLE",
        "null, SIMPLE",
    })
    void fromString_parsesCorrectly(String input, String expected) {
        if ("null".equals(input)) input = null;
        assertThat(ModelTier.fromString(input)).isEqualTo(ModelTier.valueOf(expected));
    }
}
