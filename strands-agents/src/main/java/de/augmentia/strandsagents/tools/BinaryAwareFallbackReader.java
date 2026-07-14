package de.augmentia.strandsagents.tools;

import de.augmentia.strandsagents.tools.builtin.ReadTool;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Fallback reader that handles both text and binary files, returning metadata for binary content.
 */
public class BinaryAwareFallbackReader implements FileReader {

    private static final int SCAN_SIZE = 8192;
    private static final int MAX_CHARS = 20_000;
    private static final double BINARY_THRESHOLD = 0.30;

    @Override
    public String name() {
        return "fallback";
    }

    @Override
    public boolean supports(Path path) {
        return true;
    }

    @Override
    public ToolResult read(Path path, ReadTool.Params params) throws IOException {
        if (isBinary(path)) {
            var size = Files.size(path);
            var mime = Files.probeContentType(path);
            return new ToolResult(List.of(
                "type=binary",
                "mimeType=" + (mime != null ? mime : "application/octet-stream"),
                "size=" + size
            ), null);
        }

        var allText = Files.readString(path);

        Integer effectiveStartLine = params.offset();
        if (effectiveStartLine == null) effectiveStartLine = params.line_start();

        boolean hasExplicitRange = params.offset() != null || params.line_start() != null
            || params.limit() != null || params.line_end() != null;

        if (hasExplicitRange) {
            var start = effectiveStartLine != null ? Math.max(0, effectiveStartLine - 1) : 0;
            var lines = allText.split("\n", -1);
            if (start >= lines.length) {
                throw new IOException("Offset beyond file end");
            }
            int end;
            if (params.line_end() != null) {
                end = Math.min(params.line_end(), lines.length);
            } else if (params.limit() != null) {
                end = Math.min(start + params.limit(), lines.length);
            } else {
                end = lines.length;
            }
            if (end < start) end = start;

            var sb = new StringBuilder();
            for (int i = start; i < end; i++) {
                sb.append(lines[i]).append("\n");
            }
            return ToolResult.success(sb.toString());
        }

        var text = allText.length() <= MAX_CHARS ? allText : allText.substring(0, MAX_CHARS)
            + "\n[Truncated: read " + MAX_CHARS + " of " + allText.length() + " characters]";

        return ToolResult.success(text);
    }

    /**
     * Detects whether a file is binary by scanning for null bytes and non-printable characters.
     */
    private boolean isBinary(Path path) {
        try (InputStream is = Files.newInputStream(path)) {
            var buf = new byte[SCAN_SIZE];
            var bytesRead = is.read(buf);
            if (bytesRead <= 0) return false;

            var nonPrintable = 0;
            for (int i = 0; i < bytesRead; i++) {
                var b = buf[i] & 0xFF;
                if (b == 0x00) return true;
                if (b < 0x09 || (b > 0x0D && b < 0x20) || b == 0x7F) {
                    nonPrintable++;
                }
            }
            return (double) nonPrintable / bytesRead > BINARY_THRESHOLD;
        } catch (IOException e) {
            return false;
        }
    }
}
