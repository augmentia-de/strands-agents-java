package de.augmentia.strandsagents.tools;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Registry that selects a FileReader capable of handling a given file path.
 */
public class FileReaderFactory {
    private final List<FileReader> readers = new ArrayList<>();
    private final FileReader fallback = new BinaryAwareFallbackReader();

    /**
     * Registers a FileReader, returning this factory for chaining.
     */
    public FileReaderFactory register(FileReader reader) {
        readers.add(reader);
        return this;
    }

    /**
     * Finds the first registered reader supporting the path, falling back to a default.
     */
    public FileReader findReader(Path path) {
        for (var reader : readers) {
            if (reader.supports(path)) {
                return reader;
            }
        }
        return fallback;
    }

    public List<FileReader> readers() {
        return List.copyOf(readers);
    }

    /**
     * Creates a factory pre-registered with default image and text readers.
     */
    public static FileReaderFactory withDefaults() {
        return new FileReaderFactory()
            .register(new ImageFileReader())
            .register(new TextFileReader());
    }
}
