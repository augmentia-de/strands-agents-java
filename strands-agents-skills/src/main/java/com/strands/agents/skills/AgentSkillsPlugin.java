package com.strands.agents.skills;

import com.strands.agents.core.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AgentSkillsPlugin implements Plugin {

    private static final List<String> RESOURCE_DIRS = List.of("scripts", "references", "assets");

    private final Map<String, Skill> skills;
    private final int maxResourceFiles;
    private final String stateKey;

    private String lastInjectedXml = "";

    public AgentSkillsPlugin(AgentSkillsConfig config) {
        this.skills = new ConcurrentHashMap<>();
        for (var skill : config.skills()) {
            skills.put(skill.name(), skill);
        }
        this.maxResourceFiles = config.maxResourceFiles();
        this.stateKey = config.stateKey();
    }

    public AgentSkillsPlugin(List<Skill> skills) {
        this(new AgentSkillsConfig(skills));
    }

    @Override
    public String name() {
        return "strands:agent-skills";
    }

    @Override
    public void initAgent(StrandsAgent agent) {
        if (skills.isEmpty()) return;

        agent.setPluginHook(sb -> {
            injectSkillsXml(sb);
        });
    }

    @Override
    public List<ToolRegistry.ToolMethod> getTools() {
        return List.of(new SkillsToolMethod(this));
    }

    public String activateSkill(String skillName) {
        var skill = skills.get(skillName);
        if (skill == null) {
            var available = String.join(", ", skills.keySet());
            return "Skill '" + skillName + "' not found. Available skills: " + available;
        }
        return formatSkillResponse(skill);
    }

    private void injectSkillsXml(StringBuilder sb) {
        var xml = generateSkillsXml();
        if (!lastInjectedXml.isEmpty()) {
            int idx = sb.indexOf(lastInjectedXml);
            if (idx != -1) {
                sb.delete(idx, idx + lastInjectedXml.length());
            }
        }
        var injection = "\n\n" + xml;
        sb.append(injection);
        lastInjectedXml = sb.toString();
    }

    private String generateSkillsXml() {
        if (skills.isEmpty()) {
            return "<available_skills>\nNo skills are currently available.\n</available_skills>";
        }

        var lines = new ArrayList<String>();
        lines.add("<available_skills>");
        for (var skill : skills.values()) {
            lines.add("<skill>");
            lines.add("<name>" + escapeXml(skill.name()) + "</name>");
            lines.add("<description>" + escapeXml(skill.description()) + "</description>");
            if (skill.path() != null) {
                lines.add("<location>" + escapeXml(skill.path().resolve("SKILL.md").toString()) + "</location>");
            }
            lines.add("</skill>");
        }
        lines.add("</available_skills>");
        return String.join("\n", lines);
    }

    private String formatSkillResponse(Skill skill) {
        if (skill.instructions() == null || skill.instructions().isBlank())
            return "Skill '" + skill.name() + "' activated (no instructions available).";

        var parts = new ArrayList<String>();
        parts.add(skill.instructions());

        var meta = new ArrayList<String>();
        if (skill.allowedTools() != null && !skill.allowedTools().isEmpty())
            meta.add("Allowed tools: " + String.join(", ", skill.allowedTools()));
        if (skill.compatibility() != null)
            meta.add("Compatibility: " + skill.compatibility());
        if (skill.path() != null)
            meta.add("Location: " + skill.path().resolve("SKILL.md"));

        if (!meta.isEmpty())
            parts.add("\n---\n" + String.join("\n", meta));

        if (skill.path() != null) {
            var resources = listResources(skill.path());
            if (!resources.isEmpty()) {
                parts.add("\nAvailable resources:");
                resources.forEach(r -> parts.add("  " + r));
            }
        }

        return String.join("\n", parts);
    }

    private List<String> listResources(Path skillPath) {
        var files = new ArrayList<String>();
        for (var dirName : RESOURCE_DIRS) {
            var dir = skillPath.resolve(dirName);
            if (!Files.exists(dir) || !Files.isDirectory(dir)) continue;
            try (var stream = Files.walk(dir)) {
                stream
                    .filter(Files::isRegularFile)
                    .limit(maxResourceFiles - files.size())
                    .forEach(f -> files.add(skillPath.relativize(f).toString()));
            } catch (Exception ignored) {}
            if (files.size() >= maxResourceFiles) {
                files.add("... (truncated at " + maxResourceFiles + " files)");
                break;
            }
        }
        return files;
    }

    private static String escapeXml(String text) {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }
}
