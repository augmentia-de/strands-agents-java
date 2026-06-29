package de.augmentia.strandsagents.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConfigReader {

    private static final Logger log = LoggerFactory.getLogger(ConfigReader.class);

    private ConfigReader() {}

    public static String get(String key) {
        var val = System.getProperty("vault." + key);
        if (val != null && !val.isBlank()) {
            log.info("ConfigReader.get({}) → vault.{} = {}", key, key, mask(val));
            return val;
        }
        val = System.getenv(key);
        if (val != null && !val.isBlank()) {
            log.info("ConfigReader.get({}) → env.{} = {}", key, key, mask(val));
            return val;
        }
        val = System.getProperty(key);
        if (val != null && !val.isBlank()) {
            log.info("ConfigReader.get({}) → sysprop.{} = {}", key, key, mask(val));
            return val;
        }
        log.info("ConfigReader.get({}) → null", key);
        return null;
    }

    static String mask(String s) {
        if (s == null) return null;
        if (s.length() <= 8) return s;
        return s.substring(0, 8) + "...";
    }

    public static String get(String key, String fallback) {
        var val = get(key);
        return val != null ? val : fallback;
    }

    public static boolean hasAny(String prefix) {
        return get(prefix + "PROVIDER") != null
            || get(prefix + "API_KEY") != null
            || get(prefix + "BASE_URL") != null
            || get(prefix + "MODEL") != null;
    }

    public static Double parseDouble(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
    }

    public static Integer parseInt(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }

    public static Boolean parseBoolean(String s) {
        if (s == null || s.isBlank()) return null;
        return Boolean.parseBoolean(s);
    }
}
