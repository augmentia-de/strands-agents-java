package de.augmentia.strandsagents.quarkus.service.pdf;

import de.augmentia.strandsagents.features.tools.FileReader;
import de.augmentia.strandsagents.features.tools.ReadTool;
import de.augmentia.strandsagents.features.tools.TextContent;
import de.augmentia.strandsagents.features.tools.ToolResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PdfFileReader implements FileReader {
    private static final Logger log = LoggerFactory.getLogger(PdfFileReader.class);
    private static final int MAX_LINES = 200;
    private static final int MAX_BYTES = 51_200;

    @Override
    public String name() {
        return "pdf";
    }

    @Override
    public boolean supports(Path path) {
        var n = path.getFileName().toString().toLowerCase();
        return n.endsWith(".pdf");
    }

    @Override
    public ToolResult read(Path path, ReadTool.Params params) throws IOException {
        log.debug("Reading PDF: {}", path);

        byte[] pdfBytes = Files.readAllBytes(path);

        try (var document = Loader.loadPDF(pdfBytes)) {
            var stripper = new PDFTextStripper();

            if (params.offset() != null && params.offset() > 1) {
                stripper.setStartPage(params.offset());
            }
            if (params.line_end() != null) {
                stripper.setEndPage(params.line_end());
            } else if (params.limit() != null) {
                stripper.setEndPage(params.offset() != null ? params.offset() + params.limit() - 1 : params.limit());
            }

            var text = stripper.getText(document);
            if (text == null) text = "";

            var sb = new StringBuilder();
            var lines = text.split("\n", -1);
            var totalLines = lines.length;
            var outLines = 0;
            var outBytes = 0L;
            var truncatedLines = false;
            var truncatedBytes = false;

            for (var line : lines) {
                var lb = line.getBytes().length + 1;
                if (outLines >= MAX_LINES) {
                    truncatedLines = true;
                    break;
                }
                if (outBytes + lb > MAX_BYTES) {
                    truncatedBytes = true;
                    break;
                }
                sb.append(line).append("\n");
                outLines++;
                outBytes += lb;
            }

            if (truncatedLines || truncatedBytes) {
                sb.append("\n[Truncated: read ")
                    .append(outLines).append(" of ").append(totalLines).append(" lines")
                    .append(" (").append(outBytes / 1024).append("KB of ")
                    .append(pdfBytes.length / 1024).append("KB)")
                    .append(" — limit is ").append(MAX_LINES).append(" lines / ")
                    .append(MAX_BYTES / 1024).append("KB]");
            }

            return ToolResult.success(sb.toString());
        }
    }
}
