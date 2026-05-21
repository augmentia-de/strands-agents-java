package de.augmentia.strandsagents.testagent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Path;

public class ConfigParser {

    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory())
        .registerModule(new JavaTimeModule())
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    private ConfigParser() {}

    public static TestConfig fromYaml(Path file) throws IOException {
        return MAPPER.readValue(file.toFile(), TestConfig.class);
    }

    public static void toYaml(TestConfig config, Path file) throws IOException {
        var dir = file.getParent();
        if (dir != null) java.nio.file.Files.createDirectories(dir);
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), config);
    }
}
