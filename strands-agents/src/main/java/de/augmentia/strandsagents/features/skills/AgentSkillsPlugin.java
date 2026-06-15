package de.augmentia.strandsagents.features.skills;

import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.Agent;
import de.augmentia.strandsagents.features.pipeline.HookContexts;
import de.augmentia.strandsagents.features.pipeline.HookResult;
import de.augmentia.strandsagents.features.plugin.Plugin;
import de.augmentia.strandsagents.model.message.SystemMessage;
import de.augmentia.strandsagents.prompt.PromptRegistry;
import java.time.Instant;
import java.util.UUID;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

public class AgentSkillsPlugin implements Plugin {

    private final Map<String, Skill> skills;
    private final int maxResourceFiles;
    private final String stateKey;
    private final List<String> initialSkills;
    String lastInjectedXml = "";
    private boolean skillSearchEnabled;

    public AgentSkillsPlugin(List<Skill> skills) {
        this(skills, List.of());
    }

    public AgentSkillsPlugin(List<Skill> skills, List<String> initialSkills) {
        this(skills, 20, "agent_skills", initialSkills);
    }

    public AgentSkillsPlugin(List<Skill> skills, int maxResourceFiles, String stateKey,
                             List<String> initialSkills) {
        this.skills = new LinkedHashMap<>();
        for (var s : skills) this.skills.put(s.name(), s);
        this.maxResourceFiles = maxResourceFiles;
        this.stateKey = stateKey;
        this.initialSkills = initialSkills != null ? List.copyOf(initialSkills) : List.of();
        if (initialSkills != null && initialSkills.size() > 3) {
            throw new IllegalArgumentException("initialSkills max 3");
        }
    }

    public Map<String, Skill> getSkills() {
        return skills;
    }

    public void setSkillSearchEnabled(boolean enabled) {
        this.skillSearchEnabled = enabled;
    }

    public boolean isSkillSearchEnabled() {
        return skillSearchEnabled;
    }

    @Override
    public String name() {
        return "strands:agent-skills";
    }

    @Override
    public void initAgent(Agent agent) {
    }

    @Override
    public HookResult beforeModelCall(HookContexts.BeforeModelCallContext ctx) {
        if (skills.isEmpty()) return new HookResult.Continue();
        var xml = generateSkillsXml();
        if (xml.equals(lastInjectedXml)) {
            return new HookResult.Continue();
        }
        ctx.additionalMessages().add(new SystemMessage(UUID.randomUUID().toString(), Instant.now(), xml, Map.of()));
        lastInjectedXml = xml;
        return new HookResult.Continue();
    }

    @Override
    public List<ToolRegistry.ToolMethod> getTools() {
        if (!skillSearchEnabled) return List.of();
        var tool = new SkillSearchTool(skills, this, null);
        return List.of(ToolRegistry.createMethod(tool));
    }

    void injectSkillsXml(StringBuilder sb) {
        var xml = generateSkillsXml();
        if (!lastInjectedXml.isEmpty()) {
            int idx = sb.indexOf(lastInjectedXml);
            if (idx >= 0) sb.delete(idx, idx + lastInjectedXml.length());
        }
        sb.append(xml);
        lastInjectedXml = xml;
    }

    private String generateSkillsXml() {
        var buf = new StringBuilder();

        if (!initialSkills.isEmpty()) {
            buf.append("<activated_skills>\n");
            for (var name : initialSkills) {
                var s = skills.get(name);
                if (s != null) {
                    buf.append("<skill name=\"").append(escapeXml(s.name())).append("\">\n");
                    buf.append(s.instructions()).append("\n");
                    if (s.allowedTools() != null && !s.allowedTools().isEmpty()) {
                        buf.append("<allowed_tools>")
                            .append(String.join(", ", s.allowedTools()))
                            .append("</allowed_tools>\n");
                    }
                    buf.append("</skill>\n");
                }
            }
            buf.append("</activated_skills>\n\n");
        }

        buf.append("<available_skills>\n");
        if (skills.isEmpty()) {
            buf.append(PromptRegistry.get("agent_skills_plugin.no_skills")).append("\n");
        } else {
            for (var s : skills.values()) {
                buf.append("<skill>\n");
                buf.append("<name>").append(escapeXml(s.name())).append("</name>\n");
                buf.append("<description>").append(escapeXml(s.description())).append("</description>\n");
                if (s.path() != null) {
                    buf.append("<location>").append(escapeXml(s.path().toString())).append("</location>\n");
                }
                buf.append("</skill>\n");
            }
        }
        buf.append("</available_skills>\n");

        if (skillSearchEnabled) {
            buf.append("\n").append(PromptRegistry.get("agent_skills_plugin.use_skill_search")).append("\n");
        }

        return buf.toString();
    }

    public String activateSkill(String skillName) {
        var skill = skills.get(skillName);
        if (skill == null) {
            var available = skills.keySet().stream().sorted().collect(Collectors.joining(", "));
            return PromptRegistry.get("agent_skills_plugin.error_not_found", skillName, available);
        }
        return formatSkillResponse(skill);
    }

    private String formatSkillResponse(Skill skill) {
        var sb = new StringBuilder();
        sb.append("Skill activated: ").append(skill.name()).append("\n\n");
        sb.append(skill.instructions()).append("\n");

        if (skill.allowedTools() != null && !skill.allowedTools().isEmpty()) {
            sb.append("\nAllowed tools: ").append(String.join(", ", skill.allowedTools())).append("\n");
        }
        if (skill.compatibility() != null) {
            sb.append("Compatibility: ").append(skill.compatibility()).append("\n");
        }
        if (skill.path() != null) {
            sb.append("Location: ").append(skill.path()).append("\n\n");

            for (var sub : List.of("scripts", "references", "assets")) {
                var subDir = skill.path().resolve(sub);
                if (Files.isDirectory(subDir)) {
                    try (var files = Files.list(subDir)) {
                        var list = files.limit(maxResourceFiles).toList();
                        if (!list.isEmpty()) {
                            sb.append(sub).append(":\n");
                            for (var f : list) {
                                sb.append("  - ").append(f.getFileName()).append("\n");
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        return sb.toString();
    }

    private static String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
