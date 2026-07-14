package de.augmentia.strandsagents.tools;

import de.augmentia.strandsagents.tools.builtin.ReadTool;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Strategy interface for reading files matching a specific type or format.
 */
public interface FileReader {
    String name();
    /**
     * Returns true if this reader can handle the given file path.
     */
    boolean supports(Path path);
    /**
     * Reads the file at the given path with the specified parameters.
     *
     * @return the tool result containing the file content
     */
    ToolResult read(Path path, ReadTool.Params params) throws IOException;
}
