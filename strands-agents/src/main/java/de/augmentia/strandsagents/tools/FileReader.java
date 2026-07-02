package de.augmentia.strandsagents.tools;

import de.augmentia.strandsagents.tools.builtin.ReadTool;

import java.io.IOException;
import java.nio.file.Path;

public interface FileReader {
    String name();
    boolean supports(Path path);
    ToolResult read(Path path, ReadTool.Params params) throws IOException;
}
