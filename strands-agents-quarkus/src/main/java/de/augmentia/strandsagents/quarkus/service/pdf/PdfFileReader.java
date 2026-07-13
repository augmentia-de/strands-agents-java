package de.augmentia.strandsagents.quarkus.service.pdf;

import de.augmentia.strandsagents.tools.FileReader;
import de.augmentia.strandsagents.tools.builtin.ReadTool;
import de.augmentia.strandsagents.tools.ToolResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PdfFileReader implements FileReader {
    private static final Logger log = LoggerFactory.getLogger(PdfFileReader.class);
    private static final int MAX_CHARS = 20_000;

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

            if (text.length() <= MAX_CHARS) {
                return ToolResult.success(text);
            }

            return ToolResult.success(text.substring(0, MAX_CHARS)
                + "\n[Truncated at " + MAX_CHARS + " chars — use limit=N to read more]");
        }
    }
}
