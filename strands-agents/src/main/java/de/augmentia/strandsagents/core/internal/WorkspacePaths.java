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

        // Always check canonical path for security
        if (!resolved.startsWith(workspaceCanonical)) {
            throw new SecurityException("Access denied: path outside workspace: " + path);
        }

        try {
            var resolvedCanonical = resolved.toRealPath();
            if (!resolvedCanonical.startsWith(workspaceCanonical)) {
                throw new SecurityException("Access denied: symlink target outside workspace: " + path);
            }
            return resolvedCanonical;
        } catch (IOException e) {
            // Path doesn't exist yet - this is OK for write operations
            // But we still need to verify the parent directory is within workspace
            var parent = resolved.getParent();
            if (parent != null) {
                // Check if parent exists and is within workspace
                if (Files.exists(parent)) {
                    try {
                        var parentCanonical = parent.toRealPath();
                        if (!parentCanonical.startsWith(workspaceCanonical)) {
                            throw new SecurityException("Access denied: parent directory outside workspace: " + path);
                        }
                    } catch (IOException ignored) {
                        // Can't resolve parent, skip parent check for non-existent paths
                    }
                } else {
                    // Parent doesn't exist - verify the resolved path would be in workspace
                    if (!resolved.startsWith(workspaceCanonical)) {
                        throw new SecurityException("Access denied: path outside workspace: " + path);
                    }
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