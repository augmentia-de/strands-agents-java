package de.augmentia.strandsagents.core.prompt;

import java.util.ArrayList;
import java.util.List;

public class CompositePromptManager implements PromptManager {

    private final List<PromptManager> managers = new ArrayList<>();

    public CompositePromptManager() {}

    public CompositePromptManager(PromptManager... managers) {
        for (var m : managers) {
            if (m != null) {
                this.managers.add(m);
            }
        }
    }

    public CompositePromptManager add(PromptManager manager) {
        if (manager != null) {
            managers.add(manager);
        }
        return this;
    }

    @Override
    public String get(String key, Object... args) {
        for (var mgr : managers) {
            String result = mgr.get(key, args);
            if (result != null) {
                return result;
            }
        }
        return null;
    }
}
