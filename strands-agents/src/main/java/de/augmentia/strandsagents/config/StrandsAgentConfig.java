package de.augmentia.strandsagents.config;

import de.augmentia.strandsagents.tools.builtin.BaseToolNames;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/**
 * Top-level agent configuration loaded from environment variables, Java Properties, or YAML.
 */
public record StrandsAgentConfig(
    String skillsDir,
    String sessionDir,
    boolean llmLogEnabled,
    String llmLogPath,
    List<String> initialSkills,
    boolean skillSearchEnabled,
    boolean mcpIngestEnabled,
    String mcpConfigPath,
    String workspace,
    boolean bashAllowed,
    boolean httpAllowPrivate,
    String extraTools,
    String hitlTools,
    String hitlEmailRecipient
) {

    /** Loads configuration from environment variables. */
    public static StrandsAgentConfig fromEnv() {
        return new StrandsAgentConfig(
            env("STRANDS_SKILLS_DIR", "skills"),
            env("STRANDS_SESSION_DIR", ".sessions"),
            Boolean.parseBoolean(env("STRANDS_LLM_LOG_ENABLED", "true")),
            env("STRANDS_LLM_LOG_PATH", "logs/llm-calls.log"),
            parseCsv(env("STRANDS_SKILLS_INITIAL", "")),
            Boolean.parseBoolean(env("STRANDS_SKILLS_SEARCH", "false")),
            Boolean.parseBoolean(env("STRANDS_MCP_INGEST", "false")),
            env("STRANDS_MCP_CONFIG", "config/MCP_SERVER_CONFIG.json"),
            env("STRANDS_AGENT_WORKSPACE", ""),
            Boolean.parseBoolean(env("STRANDS_AGENT_BASH_ALLOW", "false")),
            !Boolean.parseBoolean(env("STRANDS_AGENT_HTTP_ALLOW_PRIVATE", "false")),
            env("STRANDS_AGENT_TOOLS", ""),
            env("STRANDS_AGENT_HITL_TOOLS", ""),
            env("STRANDS_HITL_EMAIL_RECIPIENT", "")
        );
    }

    /** Loads configuration from Java Properties. */
    public static StrandsAgentConfig fromProperties(Properties props) {
        return new StrandsAgentConfig(
            prop(props, "strands.agent.skills.dir", "skills"),
            prop(props, "strands.agent.session.dir", ".sessions"),
            Boolean.parseBoolean(prop(props, "strands.agent.llm-log.enabled", "true")),
            prop(props, "strands.agent.llm-log.path", "logs/llm-calls.log"),
            parseCsv(prop(props, "strands.agent.skills.initial", "")),
            Boolean.parseBoolean(prop(props, "strands.agent.skills.search", "false")),
            Boolean.parseBoolean(prop(props, "strands.agent.mcp.ingest", "false")),
            prop(props, "strands.agent.mcp.config", "config/MCP_SERVER_CONFIG.json"),
            prop(props, "strands.agent.workspace", ""),
            Boolean.parseBoolean(prop(props, "strands.agent.bash.allow", "false")),
            !Boolean.parseBoolean(prop(props, "strands.agent.http.allow-private", "false")),
            prop(props, "strands.agent.tools", ""),
            prop(props, "strands.agent.hitl.tools", ""),
            prop(props, "strands.hitl.email.recipient", "")
        );
    }

    /** Loads configuration from YAML via FeatureConfig. */
    public static StrandsAgentConfig fromYaml() {
        return fromYaml(FeatureConfig.load());
    }

    /** Internal: builds config from a FeatureConfig. */
    static StrandsAgentConfig fromYaml(FeatureConfig fc) {
        return new StrandsAgentConfig(
            env("STRANDS_SKILLS_DIR", "skills"),
            env("STRANDS_SESSION_DIR", ".sessions"),
            resolveBool("llm_logging", fc, env("STRANDS_LLM_LOG_ENABLED", null)),
            env("STRANDS_LLM_LOG_PATH", "logs/llm-calls.log"),
            parseCsv(env("STRANDS_SKILLS_INITIAL", "")),
            resolveBool("skill_search", fc, env("STRANDS_SKILLS_SEARCH", null)),
            resolveBool("mcp_ingest", fc, env("STRANDS_MCP_INGEST", null)),
            env("STRANDS_MCP_CONFIG", "config/MCP_SERVER_CONFIG.json"),
            env("STRANDS_AGENT_WORKSPACE", ""),
            resolveBool(BaseToolNames.BASH, fc, env("STRANDS_AGENT_BASH_ALLOW", null)),
            !resolveBool("http_allow_private", fc, env("STRANDS_AGENT_HTTP_ALLOW_PRIVATE", null)),
            env("STRANDS_AGENT_TOOLS", ""),
            env("STRANDS_AGENT_HITL_TOOLS", ""),
            env("STRANDS_HITL_EMAIL_RECIPIENT", "")
        );
    }

    /** Loads from YAML with system property overrides. */
    public static StrandsAgentConfig fromMixed() {
        var cfg = fromYaml();
        return new StrandsAgentConfig(
            System.getProperty("strands.agent.skills.dir", cfg.skillsDir()),
            System.getProperty("strands.agent.session.dir", cfg.sessionDir()),
            Boolean.parseBoolean(System.getProperty("strands.agent.llm-log.enabled", String.valueOf(cfg.llmLogEnabled()))),
            System.getProperty("strands.agent.llm-log.path", cfg.llmLogPath()),
            cfg.initialSkills(),
            Boolean.parseBoolean(System.getProperty("strands.agent.skills.search", String.valueOf(cfg.skillSearchEnabled()))),
            Boolean.parseBoolean(System.getProperty("strands.agent.mcp.ingest", String.valueOf(cfg.mcpIngestEnabled()))),
            System.getProperty("strands.agent.mcp.config", cfg.mcpConfigPath()),
            System.getProperty("strands.agent.workspace", cfg.workspace()),
            Boolean.parseBoolean(System.getProperty("strands.agent.bash.allow", String.valueOf(cfg.bashAllowed()))),
            !Boolean.parseBoolean(System.getProperty("strands.agent.http.allow-private", String.valueOf(!cfg.httpAllowPrivate()))),
            System.getProperty("strands.agent.tools", cfg.extraTools()),
            System.getProperty("strands.agent.hitl.tools", cfg.hitlTools()),
            System.getProperty("strands.hitl.email.recipient", cfg.hitlEmailRecipient())
        );
    }

    /** Resolves a boolean from env var (priority) or YAML feature flag. */
    private static boolean resolveBool(String feature, FeatureConfig fc, String envVal) {
        if (envVal != null) return Boolean.parseBoolean(envVal);
        return fc.isEnabled(feature);
    }

    /** Resolves the workspace path, defaulting to the current directory. */
    public Path resolvedWorkspace() {
        return workspace.isBlank() ? Path.of("").toAbsolutePath()
            : Path.of(workspace).toAbsolutePath();
    }

    /** Reads an environment variable with a fallback default. */
    private static String env(String key, String fallback) {
        var val = System.getenv(key);
        return val != null ? val : fallback;
    }

    /** Reads a property with a fallback default. */
    private static String prop(Properties props, String key, String fallback) {
        return props.getProperty(key, fallback);
    }

    /** Parses a comma-separated string into a list. */
    private static List<String> parseCsv(String s) {
        if (s == null || s.isBlank()) return List.of();
        return List.of(s.split(",")).stream().map(String::strip)
            .filter(x -> !x.isEmpty()).toList();
    }
}
