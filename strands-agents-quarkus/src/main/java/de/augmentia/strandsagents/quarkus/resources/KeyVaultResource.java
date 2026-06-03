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
    @Path("/authenticate")
    public Response authenticate(Map<String, String> body) {
        var password = body != null ? body.get("password") : null;
        if (password == null || password.isBlank()) {
            return Response.status(400).entity(Map.of("error", "password erforderlich")).build();
        }
        if (!ApiKeyVault.isStored()) {
            return Response.ok(Map.of("exists", false)).build();
        }
        try {
            var entries = ApiKeyVault.loadMap(password);
            return Response.ok(Map.of("exists", true, "keys", entries.keySet())).build();
        } catch (AEADBadTagException e) {
            return Response.status(401).entity(Map.of("error", "Falsches Passwort")).build();
        } catch (Exception e) {
            return Response.status(500).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @POST
    @Path("/write")
    public Response write(Map<String, String> body) {
        var password = body != null ? body.get("password") : null;
        if (password == null || password.isBlank()) {
            return Response.status(400).entity(Map.of("error", "password erforderlich")).build();
        }
        var key = body != null ? body.get("key") : null;
        if (key == null || key.isBlank()) {
            return Response.status(400).entity(Map.of("error", "key erforderlich")).build();
        }
        var value = body.get("value");
        try {
            var entries = new java.util.LinkedHashMap<String, String>();
            if (ApiKeyVault.isStored()) {
                entries.putAll(ApiKeyVault.loadMap(password));
            }
            if (value == null || value.isBlank()) {
                entries.remove(key);
            } else {
                entries.put(key, value);
            }
            if (entries.isEmpty()) {
                return Response.ok(Map.of("status", "ok", "entryCount", 0)).build();
            }
            ApiKeyVault.store(entries, password);
            return Response.ok(Map.of("status", "ok", "entryCount", entries.size())).build();
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
