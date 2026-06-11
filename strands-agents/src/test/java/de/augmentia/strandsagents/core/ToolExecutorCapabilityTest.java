package de.augmentia.strandsagents.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.augmentia.strandsagents.features.security.CapabilityToken;
import de.augmentia.strandsagents.features.tools.ToolCapability;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ToolExecutorCapabilityTest {

    private ToolRegistry registry;
    private ToolExecutor executor;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        executor = new ToolExecutor();
    }

    static class SecureTool {
        @Tool
        @ToolCapability(CapabilityToken.FILE_READ)
        public String readFile(String path) {
            return "content of " + path;
        }

        @Tool
        @ToolCapability(CapabilityToken.NETWORK)
        public String fetch(String url) {
            return "data from " + url;
        }

        @Tool
        public String ping(String target) {
            return "pong " + target;
        }
    }

    @Test
    void toolWithRequiredCapability_blockedWhenNotGranted() {
        registry.register(new SecureTool());
        var req = ToolExecutionRequest.builder()
            .id("1").name("readFile").arguments("{\"path\":\"/tmp/x\"}").build();

        assertThatThrownBy(() -> executor.execute(req, registry))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("FILE_READ");
    }

    @Test
    void toolWithRequiredCapability_allowedWhenGranted() throws Exception {
        executor.withGrantedCapabilities(Set.of(CapabilityToken.FILE_READ));
        registry.register(new SecureTool());
        var req = ToolExecutionRequest.builder()
            .id("1").name("readFile").arguments("{\"path\":\"/tmp/x\"}").build();

        var result = executor.execute(req, registry);

        assertThat(result.result()).isEqualTo("content of /tmp/x");
    }

    @Test
    void toolWithoutCapability_alwaysAllowed() throws Exception {
        registry.register(new SecureTool());
        var req = ToolExecutionRequest.builder()
            .id("1").name("ping").arguments("{\"target\":\"localhost\"}").build();

        var result = executor.execute(req, registry);

        assertThat(result.result()).isEqualTo("pong localhost");
    }

    @Test
    void executorWithEmptyGrantedSet_blocksAllCapabilityTools() {
        executor.withGrantedCapabilities(Set.of());
        registry.register(new SecureTool());
        var req = ToolExecutionRequest.builder()
            .id("1").name("fetch").arguments("{\"url\":\"http://x\"}").build();

        assertThatThrownBy(() -> executor.execute(req, registry))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("NETWORK");
    }

    @Test
    void executorWithNullGrantedSet_treatedAsEmpty() {
        executor.withGrantedCapabilities(null);
        registry.register(new SecureTool());
        var req = ToolExecutionRequest.builder()
            .id("1").name("readFile").arguments("{\"path\":\"/tmp/x\"}").build();

        assertThatThrownBy(() -> executor.execute(req, registry))
            .isInstanceOf(SecurityException.class);
    }

    @Test
    void toolRegistryStoresCapabilityFromAnnotation() {
        registry.register(new SecureTool());
        var tm = registry.get("readFile");
        assertThat(tm.requiredCapability()).isEqualTo(CapabilityToken.FILE_READ);
    }

    @Test
    void toolRegistry_plainToolHasNullCapability() {
        registry.register(new SecureTool());
        var tm = registry.get("ping");
        assertThat(tm.requiredCapability()).isNull();
    }
}
