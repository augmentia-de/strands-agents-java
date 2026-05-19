package de.augmentia.strandsagents.quarkus.resources;

import de.augmentia.strandsagents.quarkus.dto.SkillInfo;
import de.augmentia.strandsagents.quarkus.dto.ToolInfo;
import de.augmentia.strandsagents.quarkus.service.AgentService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
public class ToolResource {

    @Inject
    AgentService agentService;

    @GET
    @Path("/tools")
    public List<ToolInfo> listTools() {
        return agentService.listTools();
    }

    @GET
    @Path("/skills")
    public List<SkillInfo> listSkills() {
        return agentService.listSkills();
    }
}
