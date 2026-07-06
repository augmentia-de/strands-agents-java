package de.augmentia.strandsagents.tools.builtin;

/**
 * Constants for all builtin tool names.
 * <p>
 * These constants mirror the values returned by the tools' {@code name()} methods
 * (for {@code AgentTool} implementations) or by the method names of {@code @Tool}-annotated classes.
 * They are intended for external use (e.g., filtering, inclusion/exclusion in registries).
 */
public final class BaseToolNames {

    private BaseToolNames() {
    }

    // --- AgentTool implementations in de.augmentia.strandsagents.tools.builtin ---

    /** {@link ApplyPatchTool} */
    public static final String APPLY_PATCH = "apply_patch";

    /** {@link BashTool} */
    public static final String BASH = "bash";

    /** {@link CommandTool} */
    public static final String EXECUTE_COMMAND = "execute_command";

    /** {@link DockerRunTool} */
    public static final String RUN = "run";

    /** {@link FindTool} */
    public static final String GLOB_FILES = "glob_files";

    /** {@link GrepTool} */
    public static final String GREP_SEARCH = "grep_search";

    /** {@link LsTool} */
    public static final String LS = "ls";

    /** {@link MultiEditTool} */
    public static final String MULTI_EDIT = "multi_edit";

    /** {@link ReadTool} */
    public static final String READ_FILES = "read_files";

    /** {@link WebFetchTool} */
    public static final String WEB_FETCH = "web_fetch";

    /** {@link WebSearchTool} */
    public static final String WEB_SEARCH = "web_search";

    /** {@link WriteTool} */
    public static final String WRITE_FILE = "write_file";

    // --- @Tool-annotated (LangChain4j) tools in de.augmentia.strandsagents.tools.builtin ---

    /** {@link HttpTool#get(String)} */
    public static final String HTTP_GET = "get";

    /** {@link HttpTool#post(String, String)} */
    public static final String HTTP_POST = "post";

    /** {@link CalculatorTool#stringLength(String)} */
    public static final String STRING_LENGTH = "stringLength";

    /** {@link TimeTool#getCurrentTime()} */
    public static final String GET_CURRENT_TIME = "getCurrentTime";

    /** {@link TimeTool#getCurrentDate()} */
    public static final String GET_CURRENT_DATE = "getCurrentDate";

    public static final String CAPABILITY_SEARCH = "capability_search";

}
