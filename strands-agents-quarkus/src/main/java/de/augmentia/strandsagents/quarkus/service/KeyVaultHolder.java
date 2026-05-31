package de.augmentia.strandsagents.quarkus.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
public class KeyVaultHolder {

    private volatile Map<String, String> entries = Map.of();

    public Map<String, String> getEntries() {
        return entries;
    }

    public void setEntries(Map<String, String> entries) {
        this.entries = entries != null ? Map.copyOf(entries) : Map.of();
    }

    public String get(String key) {
        return entries.get(key);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
