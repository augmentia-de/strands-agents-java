package de.augmentia.strandsagents.tools.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.augmentia.strandsagents.tools.ToolResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DockerRunToolTest {

    private static final String IMAGE = "strands-agent:latest";
    private static boolean dockerAvailable;
    private static boolean imageAvailable;

    private Path workspace;
    private DockerRunTool tool;
    private final AtomicBoolean abortFlag = new AtomicBoolean(false);

    @BeforeAll
    static void checkDocker() {
        try {
            var proc = new ProcessBuilder("docker", "info")
                    .redirectErrorStream(true)
                    .start();
            dockerAvailable = proc.waitFor() == 0;
        } catch (Exception e) {
            dockerAvailable = false;
        }
        if (dockerAvailable) {
            try {
                var proc = new ProcessBuilder("docker", "image", "inspect", IMAGE)
                        .redirectErrorStream(true)
                        .start();
                imageAvailable = proc.waitFor() == 0;
            } catch (Exception e) {
                imageAvailable = false;
            }
        }
    }

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        org.junit.jupiter.api.Assumptions.assumeTrue(dockerAvailable, "Docker not available");
        org.junit.jupiter.api.Assumptions.assumeTrue(imageAvailable, "Image " + IMAGE + " not found");

        workspace = tempDir.resolve("workspace");
        try {
            Files.createDirectories(workspace);
            new ProcessBuilder("chmod", "777", workspace.toString())
                    .redirectErrorStream(true).start().waitFor();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        tool = new DockerRunTool(workspace, "256m", 30_000, IMAGE);
    }

    @Test
    void shouldEchoCommand() {
        var result = tool.execute("id-1", new DockerRunTool.Params("echo hello", null), abortFlag, null);
        assertThat(details(result)).isNull();
        assertThat(text(result)).contains("hello");
    }

    @Test
    void shouldListWorkspaceContents() throws IOException {
        Files.writeString(workspace.resolve("test.txt"), "hello from workspace");

        var result = tool.execute("id-2", new DockerRunTool.Params("ls", null), abortFlag, null);
        assertThat(details(result)).isNull();
        assertThat(text(result)).contains("test.txt");
    }

    @Test
    void shouldReadWorkspaceFile() throws IOException {
        Files.writeString(workspace.resolve("data.txt"), "workspace content");

        var result = tool.execute("id-3", new DockerRunTool.Params("cat data.txt", null), abortFlag, null);
        assertThat(details(result)).isNull();
        assertThat(text(result)).contains("workspace content");
    }

    @Test
    void shouldWriteFileToWorkspace() {
        var result = tool.execute("id-4",
                new DockerRunTool.Params("echo 'written inside container' > from-container.txt "
                        + "&& cat from-container.txt", null),
                abortFlag, null);
        assertThat(details(result)).isNull();
        assertThat(text(result)).contains("written inside container");
    }

    @Test
    void shouldRespectTimeout() {
        assertThatThrownBy(() ->
                tool.execute("id-5", new DockerRunTool.Params("sleep 60", 3), abortFlag, null)
        ).isInstanceOf(RuntimeException.class)
         .hasMessageContaining("timed out");
    }

    @Test
    void shouldReportNonZeroExitCode() {
        var result = tool.execute("id-6", new DockerRunTool.Params("exit 42", null), abortFlag, null);
        assertThat(details(result)).isNotNull();
        assertThat(text(result)).contains("exited with code 42");
    }

    @Test
    void shouldAbortOnFlag() {
        abortFlag.set(true);
        var result = tool.execute("id-7", new DockerRunTool.Params("echo should not run", null), abortFlag, null);
        assertThat(text(result)).contains("aborted");
    }

    private static Object details(ToolResult r) {
        return r.details();
    }

    private static String text(ToolResult r) {
        if (r.content() == null || r.content().isEmpty()) return "";
        return (String) r.content().get(0);
    }
}