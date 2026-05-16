package com.strands.agents.quarkus.resources;

import com.strands.agents.quarkus.dto.SkillInfo;
import com.strands.agents.quarkus.dto.ToolInfo;
import com.strands.agents.quarkus.service.AgentService;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/")
public class UiResource {

    @Inject
    Template index;

    @Inject
    AgentService agentService;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance index() {
        var tools = agentService.listTools();
        var skills = agentService.listSkills();
        return index
            .data("tools", tools)
            .data("skills", skills)
            .data("hasTools", !tools.isEmpty())
            .data("hasSkills", !skills.isEmpty());
    }
}
