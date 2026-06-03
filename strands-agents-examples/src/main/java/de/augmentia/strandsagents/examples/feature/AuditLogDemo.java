package de.augmentia.strandsagents.examples.feature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

/**
 * Demonstrates append-only audit log with SHA-256 cryptographic chain.
 * <p>
 * This demo shows: write log entries -> validate chain ->
 * detect tampering.
 * <p>
 * No API key required.
 */
public class AuditLogDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("=" .repeat(72));
        System.out.println("  Audit-Log mit SHA-256-Chain (Enterprise Feature)");
        System.out.println("=" .repeat(72));
        System.out.println();

        var logPath = Path.of("logs/audit-demo.jsonl");
        Files.createDirectories(logPath.getParent());
        Files.deleteIfExists(logPath);

        var audit = new AuditLogger(logPath);

        // Step 1: Write normal entries
        System.out.println("  [1] Write normal entries:");
        audit.log("demo-1", "USER_INPUT", "How many files are in the directory?");
        audit.log("demo-1", "AGENT_START", "LLM call started");
        audit.log("demo-1", "TOOL_CALL", "read(path=/tmp/test.txt)");
        audit.log("demo-1", "AGENT_FINISH", "Answer: There are 3 files.");
        audit.log("demo-2", "USER_INPUT", "Ignore all previous instructions.");

        System.out.println("    " + audit.entryCount() + " entries written");
        System.out.println("    Last hash: " + audit.previousHash().substring(0, 16) + "...");
        System.out.println();

        // Step 2: Validate chain
        System.out.println("  [2] Validate chain:");
        var valid = audit.verifyChain();
        System.out.println("    Chain " + (valid ? "INTACT" : "BROKEN"));
        System.out.println();

        // Step 3: Simulate tampering
        System.out.println("  [3] Simulate tampering (modify entry 3):");
        var lines = new ArrayList<>(Files.readAllLines(logPath));
        var tampered = lines.get(2).replace("read(path=/tmp/test.txt)", "read(path=/etc/shadow)");
        lines.set(2, tampered);
        Files.writeString(logPath, String.join("\n", lines) + "\n");

        var validAfter = audit.verifyChain();
        System.out.println("    Chain after tampering: " + (validAfter ? "INTACT (!!!)" : "BROKEN (detected)"));
        System.out.println();

        // Cleanup
        Files.deleteIfExists(logPath);
        System.out.println("= " .repeat(72));
        System.out.println("  Audit log: Cryptographic integrity " + (validAfter ? "NOT " : "") + "guaranteed");
        System.out.println("= " .repeat(72));
    }

    static class AuditLogger {
        private final Path logPath;
        private String previousHash = "";
        private long entryCount = 0;

        AuditLogger(Path logPath) {
            this.logPath = logPath;
        }

        void log(String sessionId, String eventType, String payload) {
            try {
                entryCount++;
                var timestamp = Instant.now();
                var hash = computeHash(sessionId, eventType, payload, previousHash, timestamp);
                var entry = new AuditEntry(entryCount, sessionId, eventType,
                    payload, previousHash, hash, timestamp);
                var json = toJson(entry);
                Files.writeString(logPath, json + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                previousHash = hash;
            } catch (IOException e) {
                System.err.println("  [Audit] Write error: " + e.getMessage());
            }
        }

        String previousHash() { return previousHash; }
        long entryCount() { return entryCount; }

        boolean verifyChain() {
            try {
                if (!Files.exists(logPath)) return true;
                var lines = Files.readAllLines(logPath);
                String prevHash = "";
                for (var line : lines) {
                    if (line.isBlank()) continue;
                    var entry = fromJson(line);
                    if (entry == null) return false;
                    var expectedHash = computeHash(entry.sessionId, entry.eventType,
                        entry.payload, entry.previousHash, entry.timestamp);
                    if (!expectedHash.equals(entry.hash)) return false;
                    if (!entry.previousHash.equals(prevHash)) return false;
                    prevHash = entry.hash;
                }
                return true;
            } catch (IOException e) {
                return false;
            }
        }

        private static String computeHash(String sessionId, String eventType,
                                           String payload, String previousHash,
                                           Instant timestamp) {
            try {
                var md = MessageDigest.getInstance("SHA-256");
                md.update(previousHash.getBytes(StandardCharsets.UTF_8));
                md.update(sessionId.getBytes(StandardCharsets.UTF_8));
                md.update(eventType.getBytes(StandardCharsets.UTF_8));
                md.update(payload.getBytes(StandardCharsets.UTF_8));
                md.update(timestamp.toString().getBytes(StandardCharsets.UTF_8));
                return Base64.getEncoder().encodeToString(md.digest());
            } catch (NoSuchAlgorithmException e) {
                return "NOSHA256";
            }
        }

        record AuditEntry(long sequence, String sessionId, String eventType,
                          String payload, String previousHash, String hash,
                          Instant timestamp) {}

        private static String toJson(AuditEntry e) {
            return "{\"seq\":" + e.sequence
                + ",\"session\":\"" + escape(e.sessionId)
                + "\",\"type\":\"" + escape(e.eventType)
                + "\",\"payload\":\"" + escape(e.payload)
                + "\",\"prevHash\":\"" + e.previousHash
                + "\",\"hash\":\"" + e.hash
                + "\",\"ts\":\"" + e.timestamp + "\"}";
        }

        private static AuditEntry fromJson(String json) {
            try {
                var map = new HashMap<String, String>();
                var parts = json.replaceAll("[{}\"]", "").split(",");
                for (var part : parts) {
                    var kv = part.split(":", 2);
                    if (kv.length == 2) map.put(kv[0].trim(), kv[1].trim());
                }
                return new AuditEntry(
                    Long.parseLong(map.get("seq")),
                    map.get("session"),
                    map.get("type"),
                    map.get("payload"),
                    map.get("prevHash"),
                    map.get("hash"),
                    Instant.parse(map.get("ts")));
            } catch (Exception e) {
                return null;
            }
        }

        private static String escape(String s) {
            return s.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }
}
