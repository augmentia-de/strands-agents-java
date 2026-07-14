package de.augmentia.strandsagents.tools.security;

import de.augmentia.strandsagents.tools.AgentTool;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessControlException;

/**
 * Guards file access by resolving and validating paths against a sandboxed workspace root.
 */
public class FileSandboxGuard {

    private final Path workspaceRoot;

    /**
     * Creates a FileSandboxGuard with the given workspace root path.
     */
    public FileSandboxGuard(String rootPath) {
        if (rootPath == null || rootPath.isBlank()) {
            rootPath = "./workspace"; // Default Fallback
        }
        this.workspaceRoot = Paths.get(rootPath).toAbsolutePath().normalize();
    }

    /**
     * Löst einen potenziell unsicheren Pfad relativ zum Workspace auf und prüft, 
     * ob er sich innerhalb des sandboxed Verzeichnisses befindet.
     */
    public Path validateAndResolve(String unsafeRequestedPath) {
        if (unsafeRequestedPath == null || unsafeRequestedPath.isBlank()) {
            throw new IllegalArgumentException("Path cannot be empty");
        }

        // Absolute und normalisierte Auflösung (entfernt alle '..' und '.')
        Path resolvedPath = workspaceRoot.resolve(AgentTool.relativePath(unsafeRequestedPath)).toAbsolutePath().normalize();

        // Path-Traversal-Erkennung: Beginnt der Pfad mit dem erlaubten Root-Verzeichnis?
        if (!resolvedPath.startsWith(workspaceRoot)) {
            throw new AccessControlException("Access Denied: Path Traversal detected! Requested path '"
                    + unsafeRequestedPath + "' is outside the workspace sandbox.");
        }

        return resolvedPath;
    }

    /**
     * Returns the absolute, normalized workspace root path.
     */
    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }
}
