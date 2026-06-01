package de.augmentia.strandsagents.core.config;

import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

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
    String hitlTools
) {

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
            env("STRANDS_AGENT_HITL_TOOLS", "")
        );
    }

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
            prop(props, "strands.agent.hitl.tools", "")
        );
    }

    public static StrandsAgentConfig fromMixed() {
        var props = new Properties();
        System.getProperties().forEach((k, v) -> {
            if (k instanceof String key && v instanceof String val) {
                props.setProperty(key, val);
            }
        });
        return fromProperties(props);
    }

    public Path resolvedWorkspace() {
        return workspace.isBlank() ? Path.of("").toAbsolutePath()
            : Path.of(workspace).toAbsolutePath();
    }

    private static String env(String key, String fallback) {
        var val = System.getenv(key);
        return val != null ? val : fallback;
    }

    private static String prop(Properties props, String key, String fallback) {
        return props.getProperty(key, fallback);
    }

    private static List<String> parseCsv(String s) {
        if (s == null || s.isBlank()) return List.of();
        return List.of(s.split(",")).stream().map(String::strip)
            .filter(x -> !x.isEmpty()).toList();
    }
}
