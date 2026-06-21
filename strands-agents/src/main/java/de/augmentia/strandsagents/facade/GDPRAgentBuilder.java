package de.augmentia.strandsagents.facade;

import de.augmentia.strandsagents.features.gdpr.AuditTrailHook;
import de.augmentia.strandsagents.features.gdpr.GdprAgentPlugin;
import de.augmentia.strandsagents.features.gdpr.PiiAnonymizerHook;
import de.augmentia.strandsagents.features.plugin.Plugin;
import de.augmentia.strandsagents.features.sessions.SessionManager;

import java.nio.file.Path;
import java.util.*;

public class GDPRAgentBuilder {

    public enum AuditStoreType { NONE, FILE, JDBC, IN_MEMORY }

    private final StrandsAgentBuilder delegate;
    private boolean gdprEnabled = true;
    private Set<PiiAnonymizerHook.MaskType> maskTypes = EnumSet.of(
        PiiAnonymizerHook.MaskType.EMAIL,
        PiiAnonymizerHook.MaskType.PHONE_NUMBER,
        PiiAnonymizerHook.MaskType.NAME_DE
    );
    private PiiAnonymizerHook.BlockAction blockAction = PiiAnonymizerHook.BlockAction.REDACT;
    private String replacement = "[PII REDACTED]";
    private AuditStoreType auditStoreType = AuditStoreType.FILE;
    private String auditDir = "gdpr-audit";
    private AuditTrailHook.AuditStore customAuditStore;

    public GDPRAgentBuilder() {
        this.delegate = new StrandsAgentBuilder();
    }

    public GDPRAgentBuilder withDelegate(StrandsAgentBuilder delegate) {
        return this;
    }

    public GDPRAgentBuilder enableGdpr(boolean enabled) {
        this.gdprEnabled = enabled;
        return this;
    }

    public GDPRAgentBuilder maskTypes(PiiAnonymizerHook.MaskType... types) {
        this.maskTypes = types.length > 0
            ? EnumSet.copyOf(Arrays.asList(types))
            : EnumSet.noneOf(PiiAnonymizerHook.MaskType.class);
        return this;
    }

    public GDPRAgentBuilder blockAction(PiiAnonymizerHook.BlockAction action) {
        this.blockAction = action;
        return this;
    }

    public GDPRAgentBuilder replacement(String replacement) {
        this.replacement = replacement;
        return this;
    }

    public GDPRAgentBuilder auditStore(AuditStoreType type) {
        this.auditStoreType = type;
        return this;
    }

    public GDPRAgentBuilder auditDir(String path) {
        this.auditDir = path;
        return this;
    }

    public GDPRAgentBuilder withAuditStore(AuditTrailHook.AuditStore store) {
        this.customAuditStore = store;
        this.auditStoreType = AuditStoreType.IN_MEMORY;
        return this;
    }

    public StrandsAgentBuilder and() {
        if (gdprEnabled) {
            delegate.withPlugin(buildPlugin());
        }
        return delegate;
    }

    public StrandsAgent build() {
        return and().build();
    }

    private Plugin buildPlugin() {
        return new GdprAgentPlugin(
            resolveSessionManager(),
            maskTypes,
            blockAction,
            replacement,
            resolveAuditStore()
        );
    }

    private SessionManager resolveSessionManager() {
        return null;
    }

    private AuditTrailHook.AuditStore resolveAuditStore() {
        if (customAuditStore != null) return customAuditStore;
        return switch (auditStoreType) {
            case NONE -> null;
            case FILE -> new FileAuditStore(Path.of(auditDir));
            case IN_MEMORY -> new InMemoryAuditStore();
            case JDBC -> null;
        };
    }

    public static class FileAuditStore implements AuditTrailHook.AuditStore {
        private final Path dir;
        public FileAuditStore(Path dir) { this.dir = dir; }
        @Override
        public void write(AuditTrailHook.AuditEntry entry) {
            try {
                java.nio.file.Files.createDirectories(dir);
                var file = dir.resolve(entry.id() + ".json");
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                    .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
                mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), entry);
            } catch (Exception e) {
                throw new RuntimeException("Failed to write audit entry: " + entry.id(), e);
            }
        }
        @Override
        public List<AuditTrailHook.AuditEntry> findByUserId(String userId) {
            return List.of();
        }
        @Override
        public List<AuditTrailHook.AuditEntry> findBySessionId(String sessionId) {
            return List.of();
        }
        @Override
        public List<AuditTrailHook.AuditEntry> findAll() {
            try {
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                try (var files = java.nio.file.Files.list(dir)) {
                    return files
                        .filter(f -> f.toString().endsWith(".json"))
                        .map(f -> {
                            try { return mapper.readValue(f.toFile(), AuditTrailHook.AuditEntry.class); }
                            catch (Exception e) { return null; }
                        })
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparing(AuditTrailHook.AuditEntry::timestamp))
                        .toList();
                }
            } catch (Exception e) {
                return List.of();
            }
        }
        @Override
        public boolean verifyChain() {
            var entries = findAll();
            if (entries.isEmpty()) return true;
            for (int i = 1; i < entries.size(); i++) {
                if (!entries.get(i).hashPrevious().equals(entries.get(i - 1).hashPayload())) {
                    return false;
                }
            }
            return true;
        }
    }

    public static class InMemoryAuditStore implements AuditTrailHook.AuditStore {
        private final List<AuditTrailHook.AuditEntry> entries =
            java.util.Collections.synchronizedList(new ArrayList<>());
        @Override
        public void write(AuditTrailHook.AuditEntry entry) {
            entries.add(entry);
        }
        @Override
        public List<AuditTrailHook.AuditEntry> findByUserId(String userId) {
            return entries.stream()
                .filter(e -> userId.equals(e.userId()))
                .toList();
        }
        @Override
        public List<AuditTrailHook.AuditEntry> findBySessionId(String sessionId) {
            return entries.stream()
                .filter(e -> sessionId.equals(e.sessionId()))
                .toList();
        }
        @Override
        public List<AuditTrailHook.AuditEntry> findAll() {
            return List.copyOf(entries);
        }
        @Override
        public boolean verifyChain() {
            var copy = List.copyOf(entries);
            for (int i = 1; i < copy.size(); i++) {
                if (!copy.get(i).hashPrevious().equals(copy.get(i - 1).hashPayload())) {
                    return false;
                }
            }
            return true;
        }
    }
}
