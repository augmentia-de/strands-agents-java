package de.augmentia.strandsagents.quarkus.service;

import de.augmentia.strandsagents.prompt.CompositePromptManager;
import de.augmentia.strandsagents.prompt.PromptRegistry;
import de.augmentia.strandsagents.prompt.YamlPromptManager;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@ApplicationScoped
public class PromptConfig {

    private static final Logger log = LoggerFactory.getLogger(PromptConfig.class);

    @ConfigProperty(name = "strands.agent.prompts.override-dir")
    Optional<String> overrideDir;

    @PostConstruct
    void configurePrompts() {
        var composite = new CompositePromptManager();

        // 1. External file overrides (highest priority)
        overrideDir.filter(d -> !d.isBlank()).ifPresent(dir -> {
            var path = Path.of(dir);
            if (Files.isDirectory(path)) {
                log.info("Loading prompt overrides from directory: {}", path.toAbsolutePath());
                composite.add(new YamlPromptManager(path));
            } else {
                log.warn("Prompt override directory not found: {}", path.toAbsolutePath());
            }
        });

        // 2. Optional: Redis overrides would go here
        //    (if Redis is configured via quarkus.redis, add RedisPromptManager)

        // 3. Classpath prompts (fallback)
        try {
            composite.add(new YamlPromptManager("prompts.yaml"));
        } catch (Exception e) {
            log.warn("Failed to load prompts.yaml from classpath: {}", e.getMessage());
        }

        PromptRegistry.configure(composite);
        log.info("PromptRegistry configured");
    }
}
