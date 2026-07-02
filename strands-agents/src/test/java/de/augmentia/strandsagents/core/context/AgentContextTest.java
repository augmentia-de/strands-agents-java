package de.augmentia.strandsagents.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import de.augmentia.strandsagents.core.context.AgentContext;
import org.junit.jupiter.api.Test;

class AgentContextTest {

    @Test
    void sessionThreadLocal_isInitiallyNull() {
        assertThat(AgentContext.SESSION.get()).isNull();
    }

    @Test
    void sessionThreadLocal_setAndGet() {
        var ctx = Map.<String, Object>of("key", "value");
        AgentContext.SESSION.set(ctx);
        try {
            assertThat(AgentContext.SESSION.get()).containsEntry("key", "value");
        } finally {
            AgentContext.SESSION.remove();
        }
    }

    @Test
    void sessionThreadLocal_isolationBetweenThreads() throws Exception {
        var main = Map.<String, Object>of("main", "thread");
        AgentContext.SESSION.set(main);
        try {
            var other = Map.<String, Object>of("other", "thread");
            var fromOther = new String[1];
            var t = new Thread(() -> {
                AgentContext.SESSION.set(other);
                fromOther[0] = (String) AgentContext.SESSION.get().get("other");
                AgentContext.SESSION.remove();
            });
            t.start();
            t.join();
            assertThat(fromOther[0]).isEqualTo("thread");
            assertThat(AgentContext.SESSION.get()).containsEntry("main", "thread");
        } finally {
            AgentContext.SESSION.remove();
        }
    }

    @Test
    void sessionThreadLocal_removeClears() {
        AgentContext.SESSION.set(Map.<String, Object>of("k", "v"));
        AgentContext.SESSION.remove();
        assertThat(AgentContext.SESSION.get()).isNull();
    }
}
