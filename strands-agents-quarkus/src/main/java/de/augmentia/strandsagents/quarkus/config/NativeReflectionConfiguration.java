package de.augmentia.strandsagents.quarkus.config;

import de.augmentia.strandsagents.features.gdpr.tools.GdprDeleteTool;
import de.augmentia.strandsagents.features.gdpr.tools.GdprExportTool;
import de.augmentia.strandsagents.features.skills.CapabilitySearchTool;
import de.augmentia.strandsagents.features.skills.McpIngestTool;
import de.augmentia.strandsagents.features.skills.McpListTool;
import de.augmentia.strandsagents.features.skills.SkillSearchTool;
import de.augmentia.strandsagents.features.subagent.SubAgentTool;
import de.augmentia.strandsagents.features.tools.BashTool;
import de.augmentia.strandsagents.features.tools.CommandTool;
import de.augmentia.strandsagents.features.tools.DockerRunTool;
import de.augmentia.strandsagents.features.tools.EditTool;
import de.augmentia.strandsagents.features.tools.FindTool;
import de.augmentia.strandsagents.features.tools.GrepTool;
import de.augmentia.strandsagents.features.tools.ListToolsTool;
import de.augmentia.strandsagents.features.tools.LsTool;
import de.augmentia.strandsagents.features.tools.ReadTool;
import de.augmentia.strandsagents.features.tools.ToolActivator;
import de.augmentia.strandsagents.features.tools.WebFetchTool;
import de.augmentia.strandsagents.features.tools.WebSearchTool;
import de.augmentia.strandsagents.features.tools.WriteTool;
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
    EditTool.Params.class,
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
