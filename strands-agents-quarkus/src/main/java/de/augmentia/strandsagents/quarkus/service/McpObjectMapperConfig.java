package de.augmentia.strandsagents.quarkus.service;

import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.lang.reflect.Field;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
@Startup
public class McpObjectMapperConfig {

    private static final Logger log = LoggerFactory.getLogger(McpObjectMapperConfig.class);

    @PostConstruct
    void init() {
        configure(HttpMcpTransport.class, "OBJECT_MAPPER");
        configure(StreamableHttpMcpTransport.class, "OBJECT_MAPPER");
    }

    private void configure(Class<?> clazz, String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            var mapper = (com.fasterxml.jackson.databind.ObjectMapper) field.get(null);
            mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
            mapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
            mapper.setVisibility(PropertyAccessor.GETTER, Visibility.ANY);
            log.info("{} OBJECT_MAPPER configured: FAIL_ON_EMPTY_BEANS=false, FIELD/GETTER visibility=ANY", clazz.getSimpleName());
        } catch (Exception e) {
            log.warn("Could not configure {} {}: {}", clazz.getSimpleName(), fieldName, e.getMessage());
        }
    }
}
