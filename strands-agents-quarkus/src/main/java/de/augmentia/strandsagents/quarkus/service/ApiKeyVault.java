package de.augmentia.strandsagents.quarkus.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.*;

public class ApiKeyVault {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int SALT_LENGTH = 16;
    private static final int KEY_LENGTH = 256;
    private static final int ITERATIONS = 100_000;
    private static final String KEY_ALGO = "PBKDF2WithHmacSHA256";

    private static Path storagePath() {
        var env = System.getenv("JSTRANDS_KEY_PATH");
        if (env != null && !env.isBlank()) return Path.of(env);
        return Path.of("api-key.enc");
    }

    public static boolean isStored() {
        return Files.exists(storagePath());
    }

    public static void store(String apiKey, String password) throws Exception {
        store(Map.of("OPENAI_API_KEY", apiKey), password);
    }

    public static void store(Map<String, String> entries, String password) throws Exception {
        var json = MAPPER.writeValueAsString(entries);

        var salt = new byte[SALT_LENGTH];
        new SecureRandom().nextBytes(salt);
        var key = deriveKey(password, salt);

        var iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);

        var cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

        var ciphertext = cipher.doFinal(json.getBytes(StandardCharsets.UTF_8));

        var path = storagePath();
        var parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        try (var out = new FileOutputStream(path.toFile())) {
            out.write(salt);
            out.write(iv);
            out.write(ciphertext);
        }
        Files.setPosixFilePermissions(path, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
    }

    public static String load(String password) throws Exception {
        var map = loadMap(password);
        var val = map.get("OPENAI_API_KEY");
        if (val == null) {
            throw new IllegalStateException("Kein OPENAI_API_KEY im Vault gefunden");
        }
        return val;
    }

    public static Map<String, String> loadMap(String password) throws Exception {
        if (!isStored()) {
            throw new FileNotFoundException("Keine verschlüsselte Datei gefunden unter " + storagePath());
        }
        var bytes = Files.readAllBytes(storagePath());
        if (bytes.length < SALT_LENGTH + GCM_IV_LENGTH) {
            throw new IOException("Datei zu kurz: " + bytes.length + " Bytes");
        }

        var salt = new byte[SALT_LENGTH];
        System.arraycopy(bytes, 0, salt, 0, SALT_LENGTH);
        var iv = new byte[GCM_IV_LENGTH];
        System.arraycopy(bytes, SALT_LENGTH, iv, 0, GCM_IV_LENGTH);
        var ciphertext = new byte[bytes.length - SALT_LENGTH - GCM_IV_LENGTH];
        System.arraycopy(bytes, SALT_LENGTH + GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

        var key = deriveKey(password, salt);

        var cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

        var plaintext = cipher.doFinal(ciphertext);
        var json = new String(plaintext, StandardCharsets.UTF_8);

        try {
            return new LinkedHashMap<>(MAPPER.readValue(json, new TypeReference<Map<String, String>>() {}));
        } catch (Exception e) {
            var result = new LinkedHashMap<String, String>();
            result.put("OPENAI_API_KEY", json);
            return result;
        }
    }

    private static SecretKey deriveKey(String password, byte[] salt) throws Exception {
        var spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
        var factory = SecretKeyFactory.getInstance(KEY_ALGO);
        var pbkdf2 = factory.generateSecret(spec);
        return new SecretKeySpec(pbkdf2.getEncoded(), "AES");
    }
}
