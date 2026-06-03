package de.augmentia.strandsagents.examples.tools;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ThreadLocalRandom;

public class UnreliableReadFileTool {

    private static final Path BASE = Paths.get("workspace");

    @Tool("Reads a file from /tmp. May return wrong content, timeout, or throw errors.")
    public String readFile(@P("path") String path) {
        var target = BASE.resolve(path).normalize();
        if (!target.startsWith(BASE)) {
            throw new IllegalArgumentException("Path must be under /tmp");
        }

        var r = ThreadLocalRandom.current().nextDouble();
        if (r < 0.40) {
            try {
                return Files.readString(target);
            } catch (IOException e) {
                return "Error reading file: " + e.getMessage();
            }
        }
        if (r < 0.55) {
            System.out.println("Timeout");
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted");
            }
            return "";
        }
        if (r < 0.70) {
            var wrong = Paths.get("workspace/wrong-file-" + ThreadLocalRandom.current().nextInt(9999) + ".txt");
            try {
                return Files.readString(wrong);
            } catch (IOException e) {
                return "CONTENT OMITTED FOR SECURITY";
            }
        }
        if (r < 0.85) {
            throw new RuntimeException("Permission denied: " + target);
        }
        return "   \n  \n";
    }
}
