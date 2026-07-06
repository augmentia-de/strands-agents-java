package de.augmentia.strandsagents.quarkus.config;

import de.augmentia.strandsagents.tools.feature.GdprDeleteTool;
import de.augmentia.strandsagents.tools.feature.GdprExportTool;
import de.augmentia.strandsagents.skills.CapabilitySearchTool;
import de.augmentia.strandsagents.skills.McpIngestTool;
import de.augmentia.strandsagents.skills.McpListTool;
import de.augmentia.strandsagents.skills.SkillSearchTool;
import de.augmentia.strandsagents.core.subagent.SubAgentTool;
import de.augmentia.strandsagents.tools.ToolActivator;
import de.augmentia.strandsagents.tools.builtin.BashTool;
import de.augmentia.strandsagents.tools.builtin.CommandTool;
import de.augmentia.strandsagents.tools.builtin.DockerRunTool;
import de.augmentia.strandsagents.tools.builtin.MultiEditTool;
import de.augmentia.strandsagents.tools.builtin.FindTool;
import de.augmentia.strandsagents.tools.builtin.GrepTool;
import de.augmentia.strandsagents.tools.ListToolsTool;
import de.augmentia.strandsagents.tools.builtin.LsTool;
import de.augmentia.strandsagents.tools.builtin.ReadTool;
import de.augmentia.strandsagents.tools.builtin.WebFetchTool;
import de.augmentia.strandsagents.tools.builtin.WebSearchTool;
import de.augmentia.strandsagents.tools.builtin.WriteTool;
import de.augmentia.strandsagents.model.message.AssistantMessage;
import de.augmentia.strandsagents.model.message.SystemMessage;
import de.augmentia.strandsagents.model.message.ToolMessage;
import de.augmentia.strandsagents.model.message.UserMessage;
import de.augmentia.strandsagents.model.tool.ToolCall;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection(targets = {
    AssistantMessage.class,
    UserMessage.class,
    SystemMessage.class,
    ToolMessage.class,
    ToolCall.class,

    BashTool.Params.class,
    CapabilitySearchTool.Params.class,
    CommandTool.Params.class,
    DockerRunTool.Params.class,
    MultiEditTool.Params.class,
    FindTool.Params.class,
    GdprDeleteTool.Params.class,
    GdprExportTool.Params.class,
    GrepTool.Params.class,
    ListToolsTool.Params.class,
    LsTool.Params.class,
    McpIngestTool.Params.class,
    McpListTool.Params.class,
    ReadTool.Params.class,
    SkillSearchTool.Params.class,
    SubAgentTool.Params.class,
    ToolActivator.Params.class,
    WebFetchTool.Params.class,
    WebSearchTool.Params.class,
    WriteTool.Params.class
})
public class NativeReflectionConfiguration {
}
