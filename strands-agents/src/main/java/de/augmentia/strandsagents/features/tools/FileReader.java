package de.augmentia.strandsagents.features.tools;

import java.io.IOException;
import java.nio.file.Path;

public interface FileReader {
    String name();
    boolean supports(Path path);
    ToolResult read(Path path, ReadTool.Params params) throws IOException;
}
