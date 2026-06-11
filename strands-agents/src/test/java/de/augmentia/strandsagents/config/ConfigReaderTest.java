package de.augmentia.strandsagents.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ConfigReaderTest {

    @AfterEach
    void cleanup() {
        System.clearProperty("TEST_KEY");
        System.clearProperty("vault.TEST_KEY");
        System.clearProperty("vault.TEST_VAULT_KEY");
        System.clearProperty("LLM_TEMPERATURE");
        System.clearProperty("LLM_MAX_RETRIES");
        System.clearProperty("MY_PROVIDER");
        System.clearProperty("MY_API_KEY");
        System.clearProperty("MY_BASE_URL");
        System.clearProperty("MY_MODEL");
    }

    @Test
    void get_returnsSystemProperty() {
        System.setProperty("TEST_KEY", "prop_value");
        assertThat(ConfigReader.get("TEST_KEY")).isEqualTo("prop_value");
    }

    @Test
    void get_returnsVaultPropertyOverSystemProperty() {
        System.setProperty("vault.TEST_KEY", "vault_value");
        System.setProperty("TEST_KEY", "plain_value");
        assertThat(ConfigReader.get("TEST_KEY")).isEqualTo("vault_value");
    }

    @Test
    void get_returnsEnvironmentVariable() {
        // can't easily set env vars in tests, but should fall through to null
        assertThat(ConfigReader.get("NONEXISTENT_KEY_XYZ123")).isNull();
    }

    @Test
    void get_withFallback() {
        assertThat(ConfigReader.get("NONEXISTENT_KEY_XYZ123", "fallback")).isEqualTo("fallback");
    }

    @Test
    void get_withFallback_usesActualValue() {
        System.setProperty("TEST_KEY", "real_value");
        assertThat(ConfigReader.get("TEST_KEY", "fallback")).isEqualTo("real_value");
    }

    @Test
    void mask_shortStringReturnsAll() {
        assertThat(ConfigReader.mask("abc")).isEqualTo("abc");
        assertThat(ConfigReader.mask("12345678")).isEqualTo("12345678");
    }

    @Test
    void mask_longStringTruncates() {
        assertThat(ConfigReader.mask("abcdefghijklmnop")).isEqualTo("abcdefgh...");
    }

    @Test
    void mask_nullReturnsNull() {
        assertThat(ConfigReader.mask(null)).isNull();
    }

    @Test
    void parseDouble_valid() {
        assertThat(ConfigReader.parseDouble("3.14")).isEqualTo(3.14);
        assertThat(ConfigReader.parseDouble("0")).isEqualTo(0.0);
    }

    @Test
    void parseDouble_invalidReturnsNull() {
        assertThat(ConfigReader.parseDouble("not_a_number")).isNull();
        assertThat(ConfigReader.parseDouble("")).isNull();
        assertThat(ConfigReader.parseDouble(null)).isNull();
    }

    @Test
    void parseInt_valid() {
        assertThat(ConfigReader.parseInt("42")).isEqualTo(42);
        assertThat(ConfigReader.parseInt("0")).isEqualTo(0);
    }

    @Test
    void parseInt_invalidReturnsNull() {
        assertThat(ConfigReader.parseInt("not_a_number")).isNull();
        assertThat(ConfigReader.parseInt("")).isNull();
        assertThat(ConfigReader.parseInt(null)).isNull();
    }

    @Test
    void hasAny_returnsTrueIfAnyExists() {
        System.setProperty("MY_PROVIDER", "openai");
        assertThat(ConfigReader.hasAny("MY_")).isTrue();
    }

    @Test
    void hasAny_returnsFalseIfNoneExist() {
        assertThat(ConfigReader.hasAny("NONEXISTENT_PREFIX_XYZ_")).isFalse();
    }

    @Test
    void parseDouble_systemProperty() {
        System.setProperty("LLM_TEMPERATURE", "0.7");
        assertThat(ConfigReader.parseDouble(System.getProperty("LLM_TEMPERATURE"))).isEqualTo(0.7);
    }

    @Test
    void parseInt_systemProperty() {
        System.setProperty("LLM_MAX_RETRIES", "3");
        assertThat(ConfigReader.parseInt(System.getProperty("LLM_MAX_RETRIES"))).isEqualTo(3);
    }
}
