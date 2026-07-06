package de.augmentia.strandsagents.tools.security;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessControlException;

public class FileSandboxGuard {

    private final Path workspaceRoot;

    // Initialisierung über regulären Konstruktor
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
        Path resolvedPath = workspaceRoot.resolve(unsafeRequestedPath).toAbsolutePath().normalize();

        // Path-Traversal-Erkennung: Beginnt der Pfad mit dem erlaubten Root-Verzeichnis?
        if (!resolvedPath.startsWith(workspaceRoot)) {
            throw new AccessControlException("Access Denied: Path Traversal detected! Requested path '"
                    + unsafeRequestedPath + "' is outside the workspace sandbox.");
        }

        return resolvedPath;
    }

    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }
}