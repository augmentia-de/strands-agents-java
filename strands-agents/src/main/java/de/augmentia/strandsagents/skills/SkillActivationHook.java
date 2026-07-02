package de.augmentia.strandsagents.skills;

import de.augmentia.strandsagents.interceptor.pipeline.AgentHook;
import de.augmentia.strandsagents.interceptor.pipeline.HookContexts;
import de.augmentia.strandsagents.interceptor.pipeline.HookResult;
import de.augmentia.strandsagents.model.message.SystemMessage;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SkillActivationHook implements AgentHook {

    private final Map<String, Skill> toolToSkill;

    public SkillActivationHook(List<Skill> skills) {
        this.toolToSkill = new HashMap<>();
        for (var skill : skills) {
            if (skill.allowedTools() != null) {
                for (var tool : skill.allowedTools()) {
                    toolToSkill.put(tool, skill);
                }
            }
        }
    }

    @Override
    public String name() {
        return "skill-activation";
    }

    @Override
    public HookResult afterToolCall(HookContexts.AfterToolCallContext ctx, String toolResult) {
        if (!"tool_activator".equals(ctx.toolName())) {
            return new HookResult.Continue();
        }

        var skill = findActivatedSkill(toolResult);
        if (skill == null) {
            return new HookResult.Continue();
        }

        var xml = "<activated_skill name=\"" + escapeXml(skill.name()) + "\">\n"
            + skill.instructions() + "\n"
            + "<allowed_tools>" + String.join(", ", skill.allowedTools()) + "</allowed_tools>\n"
            + "</activated_skill>";

        ctx.additionalMessages().add(new SystemMessage(
            UUID.randomUUID().toString(), Instant.now(), xml, Map.of()));

        return new HookResult.Continue();
    }

    private Skill findActivatedSkill(String result) {
        for (var entry : toolToSkill.entrySet()) {
            if (result.contains("'" + entry.getKey() + "'")) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
