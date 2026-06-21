package de.augmentia.strandsagents.features.tools;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FileReaderFactory {
    private final List<FileReader> readers = new ArrayList<>();

    public FileReaderFactory register(FileReader reader) {
        readers.add(reader);
        return this;
    }

    public FileReader findReader(Path path) {
        for (var reader : readers) {
            if (reader.supports(path)) {
                return reader;
            }
        }
        return null;
    }

    public List<FileReader> readers() {
        return List.copyOf(readers);
    }

    public static FileReaderFactory withDefaults() {
        return new FileReaderFactory()
            .register(new ImageFileReader())
            .register(new TextFileReader());
    }
}
