package de.augmentia.strandsagents.examples.feature;

import de.augmentia.strandsagents.config.vault.CompositeSecretProvider;
import de.augmentia.strandsagents.config.vault.FileSecretProvider;
import de.augmentia.strandsagents.config.secrets.SecretProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

public class SecretVaultDemo {

    public static void main(String[] args) throws IOException {
        run();
        System.out.println("=== SecretVaultDemo PASSED ===");
    }

    public static void run() throws IOException {
        // ── 1. FileSecretProvider: write + read ──
        var secretsFile = Files.createTempFile("secrets-", ".json");
        Files.delete(secretsFile); // let FileSecretProvider create fresh
        try {
            var fileProvider = new FileSecretProvider(secretsFile, true);

            fileProvider.setSecret("openai", "api_key", "sk-real-456");
            fileProvider.setSecret("openai", "org_id", "org-abc");
            fileProvider.setSecret("database", "url", "jdbc:postgresql://localhost/mydb");
            fileProvider.setSecret("database", "password", "s3cret!");

            var apiKey = fileProvider.getSecret("openai", "api_key");
            System.out.println("  [File] openai/api_key=" + apiKey);
            assert "sk-real-456".equals(apiKey) : "file secret read";

            var allDb = fileProvider.getSecrets("database");
            System.out.println("  [File] database secrets: " + allDb);
            assert allDb.size() == 2 : "two db secrets";
            assert "s3cret!".equals(allDb.get("password")) : "db password read";

            // ── 2. CompositeSecretProvider: fallback chain ──
            var primaryFile = Files.createTempFile("primary-", ".json");
            var fallbackFile = Files.createTempFile("fallback-", ".json");

            try {
                // Primary has only "app" path
                Files.delete(primaryFile);
                var primary = new FileSecretProvider(primaryFile, true);
                primary.setSecret("app", "timeout", "30s");

                // Fallback has "app" + "database"
                Files.delete(fallbackFile);
                var fallback = new FileSecretProvider(fallbackFile, true);
                fallback.setSecret("app", "retry", "3");
                fallback.setSecret("database", "url", "jdbc:fallback://localhost/db");

                var composite = new CompositeSecretProvider(primary, fallback);

                // App timeout comes from primary
                var timeout = composite.getSecret("app", "timeout");
                System.out.println("  [Composite] app/timeout=" + timeout + " (from primary)");
                assert "30s".equals(timeout) : "timeout from primary";

                // App retry falls through to fallback
                var retry = composite.getSecret("app", "retry");
                System.out.println("  [Composite] app/retry=" + retry + " (from fallback)");
                assert "3".equals(retry) : "retry from fallback";

                // Database only in fallback
                var dbUrl = composite.getSecret("database", "url");
                System.out.println("  [Composite] database/url=" + dbUrl + " (from fallback)");
                assert dbUrl.contains("fallback") : "db url from fallback";

            } finally {
                Files.deleteIfExists(primaryFile);
                Files.deleteIfExists(fallbackFile);
            }

            // ── 3. Built-in SecretProvider that wraps Map ──
            var mapProvider = new MapSecretProvider(Map.of(
                "api", Map.of("key", "sk-map", "secret", "abc"),
                "features", Map.of("guardrails", "enabled", "audit", "enabled")
            ));
            assert "sk-map".equals(mapProvider.getSecret("api", "key")) : "map provider key";
            assert "enabled".equals(mapProvider.getSecret("features", "guardrails")) : "map provider guardrails";
            System.out.println("  [MapProvider] api/key=" + mapProvider.getSecret("api", "key"));
            System.out.println("  [MapProvider] features=" + mapProvider.getSecrets("features"));

        } finally {
            Files.deleteIfExists(secretsFile);
        }
    }

    /** In-memory SecretProvider backed by a Map (for demo purposes). */
    public static class MapSecretProvider implements SecretProvider {
        private final Map<String, Map<String, String>> store;

        public MapSecretProvider(Map<String, Map<String, String>> store) {
            this.store = store;
        }

        @Override
        public String getSecret(String path, String key) {
            var secrets = store.get(path);
            if (secrets == null || !secrets.containsKey(key)) {
                throw new RuntimeException("Secret not found: " + path + "/" + key);
            }
            return secrets.get(key);
        }

        @Override
        public Map<String, String> getSecrets(String path) {
            return store.getOrDefault(path, Map.of());
        }
    }
}
