package de.augmentia.strandsagents.core.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspacePathsTest {

    @TempDir
    Path tempDir;

    private WorkspacePaths workspacePaths;

    @BeforeEach
    void setUp() throws IOException {
        workspacePaths = new WorkspacePaths(tempDir);
    }

    @Test
    void allowsFileInsideWorkspace() throws IOException {
        var file = tempDir.resolve("test.txt");
        write(file, "hello");

        var result = workspacePaths.resolve("test.txt");

        assertThat(result).isEqualTo(file.toRealPath());
    }

    @Test
    void allowsFileInSubdirectory() throws IOException {
        var subdir = tempDir.resolve("sub");
        subdir.toFile().mkdirs();
        var file = subdir.resolve("test.txt");
        write(file, "hello");

        var result = workspacePaths.resolve("sub/test.txt");

        assertThat(result).isEqualTo(file.toRealPath());
    }

    @Test
    void allowsAbsolutePathInsideWorkspace() throws IOException {
        var file = tempDir.resolve("test.txt");
        write(file, "hello");

        var result = workspacePaths.resolve(file.toAbsolutePath().toString());

        assertThat(result).isEqualTo(file.toRealPath());
    }

    @Test
    void blocksPathTraversalWithDotDot() throws IOException {
        assertThatThrownBy(() -> workspacePaths.resolve("../../etc/passwd"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    void blocksAbsolutePathOutsideWorkspace() throws IOException {
        assertThatThrownBy(() -> workspacePaths.resolve("/etc/passwd"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    void blocksSymlinkToOutside(@TempDir Path outsideDir) throws IOException {
        var target = outsideDir.resolve("secret.txt");
        write(target, "secret data");
        var link = tempDir.resolve("evil-link");
        Files.createSymbolicLink(link, target);

        assertThatThrownBy(() -> workspacePaths.resolve("evil-link"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    void blocksSymlinkChainToOutside(@TempDir Path outsideDir) throws IOException {
        var target = outsideDir.resolve("secret.txt");
        write(target, "secret data");
        var middle = outsideDir.resolve("middle-link");
        Files.createSymbolicLink(middle, target);
        var link = tempDir.resolve("chain-link");
        Files.createSymbolicLink(link, middle);

        assertThatThrownBy(() -> workspacePaths.resolve("chain-link"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    void blocksSymlinkInPath(@TempDir Path outsideDir) throws IOException {
        var targetDir = outsideDir.resolve("sub");
        targetDir.toFile().mkdirs();
        var target = targetDir.resolve("secret.txt");
        write(target, "secret");
        var linkDir = tempDir.resolve("linkdir");
        Files.createSymbolicLink(linkDir, targetDir);

        assertThatThrownBy(() -> workspacePaths.resolve("linkdir/secret.txt"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    void returnsWorkspacePath() {
        assertThat(workspacePaths.workspace()).isEqualTo(tempDir.toAbsolutePath().normalize());
    }

    @Test
    void allowsNewFileInWorkspace() {
        var result = workspacePaths.resolve("newfile.txt");

        assertThat(result).isEqualTo(tempDir.resolve("newfile.txt").normalize().toAbsolutePath());
    }

    @Test
    void allowsNewFileInSubdirectory() {
        var result = workspacePaths.resolve("sub/newfile.txt");

        assertThat(result).isEqualTo(tempDir.resolve("sub/newfile.txt").normalize().toAbsolutePath());
    }

    @Test
    void blocksSymlinkParentForNewFile(@TempDir Path outsideDir) throws IOException {
        var linkDir = tempDir.resolve("mydir");
        Files.createSymbolicLink(linkDir, outsideDir);

        assertThatThrownBy(() -> workspacePaths.resolve("mydir/newfile.txt"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    void blocksDotDotWithSymlinkInPath(@TempDir Path outsideDir) throws IOException {
        var linkDir = tempDir.resolve("inner");
        Files.createSymbolicLink(linkDir, outsideDir);

        assertThatThrownBy(() -> workspacePaths.resolve("inner/../inner/secret.txt"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Access denied");
    }

    private static void write(Path path, String content) {
        try {
            Files.writeString(path, content);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
