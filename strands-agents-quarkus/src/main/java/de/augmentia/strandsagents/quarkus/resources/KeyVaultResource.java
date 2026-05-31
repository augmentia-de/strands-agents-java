package de.augmentia.strandsagents.quarkus.resources;

import de.augmentia.strandsagents.quarkus.service.ApiKeyVault;
import de.augmentia.strandsagents.quarkus.service.AgentService;
import de.augmentia.strandsagents.quarkus.service.KeyVaultHolder;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import javax.crypto.AEADBadTagException;

@Path("/api/vault")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class KeyVaultResource {

    @Inject
    KeyVaultHolder vaultHolder;

    @Inject
    AgentService agentService;

    @GET
    @Path("/status")
    public Response status() {
        return Response.ok(Map.of(
            "exists", ApiKeyVault.isStored(),
            "loaded", !vaultHolder.isEmpty()
        )).build();
    }

    @POST
    @Path("/read")
    public Response read(Map<String, String> body) {
        var password = body != null ? body.get("password") : null;
        if (password == null || password.isBlank()) {
            return Response.status(400).entity(Map.of("error", "password erforderlich")).build();
        }
        if (!ApiKeyVault.isStored()) {
            return Response.ok(Map.of("entries", Map.of())).build();
        }
        try {
            var entries = ApiKeyVault.loadMap(password);
            return Response.ok(Map.of("entries", entries)).build();
        } catch (AEADBadTagException e) {
            return Response.status(401).entity(Map.of("error", "Falsches Passwort")).build();
        } catch (Exception e) {
            return Response.status(500).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @POST
    @Path("/write")
    public Response write(Map<String, Object> body) {
        var password = body != null ? (String) body.get("password") : null;
        if (password == null || password.isBlank()) {
            return Response.status(400).entity(Map.of("error", "password erforderlich")).build();
        }
        @SuppressWarnings("unchecked")
        var entries = body != null ? (Map<String, String>) body.get("entries") : null;
        if (entries == null || entries.isEmpty()) {
            return Response.status(400).entity(Map.of("error", "entries darf nicht leer sein")).build();
        }
        try {
            ApiKeyVault.store(entries, password);
            return Response.ok(Map.of("status", "ok")).build();
        } catch (AEADBadTagException e) {
            return Response.status(401).entity(Map.of("error", "Falsches Passwort")).build();
        } catch (Exception e) {
            return Response.status(500).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @POST
    @Path("/reload")
    public Response reload(Map<String, String> body) {
        var password = body != null ? body.get("password") : null;
        if (password == null || password.isBlank()) {
            return Response.status(400).entity(Map.of("error", "password erforderlich")).build();
        }
        if (!ApiKeyVault.isStored()) {
            return Response.status(400).entity(Map.of("error", "Keine verschlüsselte Datei vorhanden")).build();
        }
        try {
            var entries = ApiKeyVault.loadMap(password);
            int count = 0;
            for (var entry : entries.entrySet()) {
                System.setProperty("vault." + entry.getKey(), entry.getValue());
                count++;
            }
            vaultHolder.setEntries(entries);
            if (entries.containsKey("OPENAI_API_KEY")) {
                var apiKey = entries.get("OPENAI_API_KEY");
                if (apiKey != null && !apiKey.isBlank()) {
                    agentService.activateModel(apiKey);
                }
            }
            return Response.ok(Map.of("status", "ok", "applied", count)).build();
        } catch (AEADBadTagException e) {
            return Response.status(401).entity(Map.of("error", "Falsches Passwort")).build();
        } catch (Exception e) {
            return Response.status(500).entity(Map.of("error", e.getMessage())).build();
        }
    }
}
