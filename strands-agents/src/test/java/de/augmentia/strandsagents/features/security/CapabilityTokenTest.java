package de.augmentia.strandsagents.features.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.augmentia.strandsagents.features.tools.ToolCapability;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class CapabilityTokenTest {

    @Test
    void enumHasAllTokens() {
        assertThat(CapabilityToken.values())
            .containsExactlyInAnyOrder(
                CapabilityToken.FILE_READ,
                CapabilityToken.FILE_WRITE,
                CapabilityToken.DB_READ,
                CapabilityToken.DB_WRITE,
                CapabilityToken.NETWORK,
                CapabilityToken.EXECUTE,
                CapabilityToken.LLM_CALL,
                CapabilityToken.S3_READ,
                CapabilityToken.S3_WRITE,
                CapabilityToken.KAFKA_PUBLISH,
                CapabilityToken.KAFKA_CONSUME,
                CapabilityToken.VAULT_READ,
                CapabilityToken.VAULT_WRITE);
    }

    @Test
    void enumValueOf_validToken_returnsCorrect() {
        assertThat(CapabilityToken.valueOf("FILE_READ")).isEqualTo(CapabilityToken.FILE_READ);
        assertThat(CapabilityToken.valueOf("NETWORK")).isEqualTo(CapabilityToken.NETWORK);
        assertThat(CapabilityToken.valueOf("EXECUTE")).isEqualTo(CapabilityToken.EXECUTE);
    }

    @Test
    void enumValueOf_invalidToken_throwsException() {
        assertThatThrownBy(() -> CapabilityToken.valueOf("UNKNOWN"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void annotationCanBeAppliedToMethod() throws Exception {
        class AnnotatedTool {
            @ToolCapability(CapabilityToken.FILE_READ)
            public String readFile(String path) { return path; }
        }
        Method method = AnnotatedTool.class.getMethod("readFile", String.class);
        var annotation = method.getAnnotation(ToolCapability.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo(CapabilityToken.FILE_READ);
    }

    @Test
    void annotationNotPresentOnPlainMethod() throws Exception {
        class PlainTool {
            public void doSomething() {}
        }
        Method method = PlainTool.class.getMethod("doSomething");
        var annotation = method.getAnnotation(ToolCapability.class);
        assertThat(annotation).isNull();
    }
}
