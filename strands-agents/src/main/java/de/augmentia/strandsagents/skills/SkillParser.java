package de.augmentia.strandsagents.skills;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Parses SKILL.md files, extracting YAML frontmatter and instruction body.
 */
public class SkillParser {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    /**
     * Parses a skill from a SKILL.md content string (YAML frontmatter delimited by ---).
     */
    public static Skill fromContent(String content) {
        var stripped = content.strip();
        if (!stripped.startsWith("---"))
            throw new IllegalArgumentException("SKILL.md must start with ---");

        var endMatch = stripped.indexOf("\n---", 3);
        if (endMatch == -1)
            throw new IllegalArgumentException("Missing closing --- delimiter");

        var yamlStr = stripped.substring(3, endMatch).strip();
        var body = stripped.substring(endMatch + 4).strip();

        @SuppressWarnings("unchecked")
        Map<String, Object> frontmatter;
        try {
            frontmatter = YAML.readValue(yamlStr, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid YAML frontmatter: " + e.getMessage(), e);
        }

        var name = strField(frontmatter, "name");
        var description = strField(frontmatter, "description");
        if (name == null) throw new IllegalArgumentException("Missing 'name' in frontmatter");
        if (description == null) throw new IllegalArgumentException("Missing 'description' in frontmatter");

        var allowedTools = parseAllowedTools(frontmatter);
        var declaredTools = parseDeclaredTools(frontmatter);
        var metadata = parseMetadata(frontmatter);
        var license = strField(frontmatter, "license");
        var compatibility = strField(frontmatter, "compatibility");

        return new Skill(name, description, body, null, allowedTools, metadata, license, compatibility, declaredTools);
    }

    /**
     * Loads and parses a skill from a SKILL.md file or a directory containing one.
     */
    public static Skill fromFile(Path path) {
        Path skillMd;
        if (Files.isDirectory(path)) {
            skillMd = findSkillMdFile(path);
        } else if (path.getFileName().toString().equalsIgnoreCase("SKILL.md")) {
            skillMd = path;
        } else {
            throw new IllegalArgumentException("Not a skill directory or SKILL.md file: " + path);
        }
        String content;
        try {
            content = Files.readString(skillMd);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + skillMd, e);
        }
        var skill = fromContent(content);
        var dir = Files.isDirectory(path) ? path : path.getParent();
        return new Skill(skill.name(), skill.description(), skill.instructions(),
            dir, skill.allowedTools(), skill.metadata(),
            skill.license(), skill.compatibility(), skill.declaredTools());
    }

    /**
     * Scans a directory for subdirectories each containing a SKILL.md and returns parsed skills.
     */
    public static List<Skill> fromDirectory(Path dir) throws IOException {
        try (var files = Files.list(dir)) {
            return files
                .filter(Files::isDirectory)
                .map(child -> {
                    try { return fromFile(child); }
                    catch (Exception ignored) { return null; }
                })
                .filter(Objects::nonNull)
                .toList();
        }
    }

    /**
     * Fetches and parses a skill from a remote URL asynchronously.
     */
    public static CompletableFuture<Skill> fromUrl(String url) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var uri = new java.net.URI(url);
                try (var stream = uri.toURL().openStream()) {
                    var content = new String(stream.readAllBytes());
                    return fromContent(content);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to fetch skill from " + url, e);
            }
        });
    }

    /**
     * Locates SKILL.md (case-insensitive) within the given directory.
     */
    public static Path findSkillMdFile(Path dir) {
        for (var name : List.of("SKILL.md", "skill.md")) {
            var candidate = dir.resolve(name);
            if (Files.exists(candidate) && Files.isRegularFile(candidate))
                return candidate;
        }
        throw new IllegalArgumentException("No SKILL.md found in " + dir);
    }

    private static String strField(Map<String, Object> map, String key) {
        var v = map.get(key);
        return v != null ? v.toString() : null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> parseAllowedTools(Map<String, Object> fm) {
        var raw = fm.getOrDefault("allowed-tools", fm.get("allowed_tools"));
        if (raw instanceof String s && !s.isBlank())
            return List.of(s.trim().split("\\s+"));
        if (raw instanceof Collection<?> c)
            return c.stream().map(Object::toString).toList();
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> parseDeclaredTools(Map<String, Object> fm) {
        var raw = fm.get("tools");
        if (raw instanceof Collection<?> c)
            return c.stream().map(Object::toString).toList();
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseMetadata(Map<String, Object> fm) {
        var raw = fm.get("metadata");
        if (raw instanceof Map<?, ?> m) {
            var result = new LinkedHashMap<String, Object>();
            m.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        return Map.of();
    }
}
