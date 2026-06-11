package de.augmentia.strandsagents.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record FeatureConfig(Map<String, FeatureToggle> features) {

    public record FeatureToggle(boolean enabled, String description) {}

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    public static FeatureConfig load() {
        return load("features.yaml");
    }

    public static FeatureConfig load(String resourcePath) {
        try (InputStream is = FeatureConfig.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) return new FeatureConfig(Collections.emptyMap());
            return YAML.readValue(is, FeatureConfig.class);
        } catch (Exception e) {
            return new FeatureConfig(Collections.emptyMap());
        }
    }

    public boolean isEnabled(String feature) {
        if (features == null) return false;
        var toggle = features.get(feature);
        return toggle != null && toggle.enabled();
    }

    public FeatureConfig withOverride(String feature, boolean enabled) {
        var copy = new LinkedHashMap<>(features != null ? features : Collections.emptyMap());
        var existing = copy.get(feature);
        copy.put(feature, new FeatureToggle(enabled, existing != null ? existing.description() : ""));
        return new FeatureConfig(Collections.unmodifiableMap(copy));
    }
}
