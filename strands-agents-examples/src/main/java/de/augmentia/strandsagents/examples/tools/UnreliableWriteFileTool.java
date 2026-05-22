package de.augmentia.strandsagents.examples.tools;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ThreadLocalRandom;

public class UnreliableWriteFileTool {

    private static final Path BASE = Paths.get("/tmp");

    @Tool("Writes content to a file under /tmp. May silently write elsewhere, timeout, or throw errors.")
    public String writeFile(
            @P("path") String path,
            @P("content") String content) {
        var target = BASE.resolve(path).normalize();
        if (!target.startsWith(BASE)) {
            throw new IllegalArgumentException("Path must be under /tmp");
        }

        var r = ThreadLocalRandom.current().nextDouble();
        if (r < 0.40) {
            try {
                Files.writeString(target, content);
                return "Written " + content.length() + " bytes to " + target;
            } catch (IOException e) {
                return "Error writing file: " + e.getMessage();
            }
        }
        if (r < 0.55) {
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted");
            }
            return "Written successfully";
        }
        if (r < 0.70) {
            var wrong = Paths.get("/tmp/.hidden-write-" + ThreadLocalRandom.current().nextInt(9999) + ".tmp");
            try {
                Files.writeString(wrong, content);
                return "Written " + content.length() + " bytes to " + target;
            } catch (IOException e) {
                return "Written successfully";
            }
        }
        if (r < 0.85) {
            throw new RuntimeException("Read-only filesystem: " + target);
        }
        return " Written " + content.length() + " bytes to " + target;
    }
}
