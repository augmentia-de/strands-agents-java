package de.augmentia.strandsagents.features.internal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class WorkspacePaths {

    private final Path workspace;
    private final Path workspaceCanonical;

    public WorkspacePaths(Path workspace) throws IOException {
        this.workspace = workspace.toAbsolutePath().normalize();
        this.workspaceCanonical = this.workspace.toRealPath();
    }

    public Path resolve(String path) {
        var p = Paths.get(path);
        var resolved = (p.isAbsolute() ? p : workspace.resolve(p))
                .normalize().toAbsolutePath();

        if (!resolved.startsWith(workspaceCanonical)) {
            throw new RuntimeException("Access denied: path outside working directory: " + path);
        }

        try {
            var resolvedCanonical = resolved.toRealPath();
            if (!resolvedCanonical.startsWith(workspaceCanonical)) {
                throw new RuntimeException("Access denied: symlink target outside working directory: " + path);
            }
            return resolvedCanonical;
        } catch (IOException e) {
            // Workspace-relative path doesn't exist on disk.
            // Try CWD-relative (matches LLM's mental model, e.g. "workspace/File.java"
            // from project root when the tool base is already "workspace/").
            if (!p.isAbsolute() && !path.isEmpty()) {
                var cwd = Paths.get("").toAbsolutePath().normalize();
                var fromCwd = cwd.resolve(p).normalize();
                if (fromCwd.startsWith(workspaceCanonical)) {
                    try {
                        var fromCwdCanonical = fromCwd.toRealPath();
                        if (fromCwdCanonical.startsWith(workspaceCanonical)) {
                            return fromCwdCanonical;
                        }
                    } catch (IOException ignored) {
                    }
                    return fromCwd;
                }
            }

            var parent = resolved.getParent();
            if (parent != null && Files.exists(parent)) {
                try {
                    var parentCanonical = parent.toRealPath();
                    if (!parentCanonical.startsWith(workspaceCanonical)) {
                        throw new RuntimeException("Access denied: symlink target outside working directory: " + path);
                    }
                } catch (IOException ignored) {
                }
            }
            return resolved;
        }
    }

    public Path workspace() {
        return workspace;
    }

    public Path workspaceCanonical() {
        return workspaceCanonical;
    }
}
