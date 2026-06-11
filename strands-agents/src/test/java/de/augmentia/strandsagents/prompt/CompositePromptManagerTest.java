package de.augmentia.strandsagents.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CompositePromptManagerTest {

    @Test
    void emptyManager_returnsNull() {
        var cm = new CompositePromptManager();
        assertThat(cm.get("key")).isNull();
    }

    @Test
    void delegatesToFirstMatch() {
        var first = new PromptManager() {
            @Override
            public String get(String key, Object... args) {
                return "first:" + key;
            }
        };
        var second = new PromptManager() {
            @Override
            public String get(String key, Object... args) {
                return "second:" + key;
            }
        };
        var cm = new CompositePromptManager(first, second);
        assertThat(cm.get("test")).isEqualTo("first:test");
    }

    @Test
    void fallsThroughToSecondWhenFirstReturnsNull() {
        var first = new PromptManager() {
            @Override
            public String get(String key, Object... args) {
                return null;
            }
        };
        var second = new PromptManager() {
            @Override
            public String get(String key, Object... args) {
                return "second:" + key;
            }
        };
        var cm = new CompositePromptManager(first, second);
        assertThat(cm.get("test")).isEqualTo("second:test");
    }

    @Test
    void addMethod_chains() {
        var pm = new PromptManager() {
            @Override
            public String get(String key, Object... args) {
                return "added:" + key;
            }
        };
        var cm = new CompositePromptManager().add(pm);
        assertThat(cm.get("key")).isEqualTo("added:key");
    }

    @Test
    void add_null_isIgnored() {
        var cm = new CompositePromptManager().add(null);
        assertThat(cm.get("key")).isNull();
    }

    @Test
    void varargs_nullElementsAreSkipped() {
        var pm = new PromptManager() {
            @Override
            public String get(String key, Object... args) {
                return "val";
            }
        };
        var cm = new CompositePromptManager(null, pm, null);
        assertThat(cm.get("key")).isEqualTo("val");
    }
}
