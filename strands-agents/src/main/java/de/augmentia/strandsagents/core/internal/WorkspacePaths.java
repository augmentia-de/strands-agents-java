package de.augmentia.strandsagents.core.internal;

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
