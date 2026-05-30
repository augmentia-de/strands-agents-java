package de.augmentia.strandsagents.quarkus.resources;

import de.augmentia.strandsagents.quarkus.service.ApiKeyVault;
import de.augmentia.strandsagents.quarkus.service.AgentService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import javax.crypto.AEADBadTagException;

@Path("/api/admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminResource {

    @Inject
    AgentService agentService;

    @POST
    @Path("/setup")
    public Response setup(Map<String, String> body) {
        var apiKey = body != null ? body.get("apiKey") : null;
        var password = body != null ? body.get("password") : null;
        if (apiKey == null || apiKey.isBlank()) {
            return Response.status(400).entity(Map.of("error", "apiKey darf nicht leer sein")).build();
        }
        if (password == null || password.isBlank()) {
            return Response.status(400).entity(Map.of("error", "password darf nicht leer sein")).build();
        }
        try {
            ApiKeyVault.store(apiKey, password);
            return Response.ok(Map.of("status", "ok")).build();
        } catch (IllegalStateException e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        } catch (Exception e) {
            var msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return Response.status(500).entity(Map.of("error", "Fehler beim Speichern: " + msg)).build();
        }
    }

    @POST
    @Path("/activate")
    public Response activate(Map<String, String> body) {
        var password = body != null ? body.get("password") : null;
        if (password == null || password.isBlank()) {
            return Response.status(400).entity(Map.of("error", "password darf nicht leer sein")).build();
        }
        if (!ApiKeyVault.isStored()) {
            return Response.status(400).entity(Map.of("error", "Kein verschlüsselter API-Key gefunden. Führe zuerst Setup durch.")).build();
        }
        try {
            var apiKey = ApiKeyVault.load(password);
            agentService.activateModel(apiKey);
            return Response.ok(Map.of("status", "ok")).build();
        } catch (AEADBadTagException e) {
            return Response.status(401).entity(Map.of("error", "Falsches Passwort")).build();
        } catch (Exception e) {
            return Response.status(500).entity(Map.of("error", "Fehler beim Aktivieren: " + e.getMessage())).build();
        }
    }

    @POST
    @Path("/deactivate")
    public Response deactivate() {
        agentService.deactivateModel();
        return Response.ok(Map.of("status", "ok")).build();
    }

    @GET
    @Path("/status")
    public Response status() {
        return Response.ok(Map.of(
            "stored", ApiKeyVault.isStored(),
            "active", agentService.isRuntimeKeyActive()
        )).build();
    }
}
