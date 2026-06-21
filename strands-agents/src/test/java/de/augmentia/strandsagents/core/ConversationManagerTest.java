package de.augmentia.strandsagents.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.augmentia.strandsagents.core.MockChatModel;
import de.augmentia.strandsagents.core.Agent;
import de.augmentia.strandsagents.features.conversation.SlidingWindowConversationManager;
import de.augmentia.strandsagents.features.conversation.SummarizingSlidingWindowConversationManager;
import de.augmentia.strandsagents.model.agent.StopReason;
import de.augmentia.strandsagents.model.message.AssistantMessage;
import de.augmentia.strandsagents.model.message.Message;
import de.augmentia.strandsagents.model.message.SystemMessage;
import de.augmentia.strandsagents.model.message.ToolMessage;
import de.augmentia.strandsagents.model.message.UserMessage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConversationManagerTest {

    // ── SlidingWindow ─────────────────────────────────────────────────

    @Test
    void slidingWindowKeepsLastN() {
        var manager = new SlidingWindowConversationManager(3);
        var messages = createMessages(5);

        var pruned = manager.prune(messages);

        assertThat(pruned).hasSize(3);
        assertThat(pruned.get(0).content()).isEqualTo("Nachricht 2");
        assertThat(pruned.get(2).content()).isEqualTo("Nachricht 4");
    }

    @Test
    void slidingWindowReturnsAllIfUnderWindow() {
        var manager = new SlidingWindowConversationManager(10);
        var messages = createMessages(3);

        var pruned = manager.prune(messages);

        assertThat(pruned).hasSize(3);
    }

    @Test
    void slidingWindowWorksWithWindowSizeOne() {
        var manager = new SlidingWindowConversationManager(1);
        var messages = createMessages(5);

        var pruned = manager.prune(messages);

        assertThat(pruned).hasSize(1);
        assertThat(pruned.get(0).content()).isEqualTo("Nachricht 4");
    }

    @Test
    void slidingWindowRejectsInvalidWindowSize() {
        assertThatThrownBy(() -> new SlidingWindowConversationManager(0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SlidingWindowConversationManager(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void slidingWindowHandlesEmptyList() {
        var manager = new SlidingWindowConversationManager(5);
        var pruned = manager.prune(List.of());
        assertThat(pruned).isEmpty();
    }

    // ── SummarizingSlidingWindow ──────────────────────────────────────

    @Test
    void hybridDoesNothingWhenUnderMaxTokens() {
        var manager = new SummarizingSlidingWindowConversationManager(new MockChatModel(), 10_000, 3);
        var messages = createMessages(3);

        var pruned = manager.prune(messages);

        assertThat(pruned).hasSize(3);
        assertThat(pruned).allMatch(m -> !(m instanceof SystemMessage));
    }

    @Test
    void hybridReplacesOldMessagesWhenOverBudget() {
        var manager = new SummarizingSlidingWindowConversationManager(new MockChatModel(), 100, 3);
        var messages = createLongMessages(12);

        var pruned = manager.prune(messages);

        assertThat(pruned).isNotEmpty();
        assertThat(pruned.get(0)).isInstanceOf(SystemMessage.class);
        var summary = (SystemMessage) pruned.get(0);
        assertThat(summary.content()).startsWith("Conversation summary:");
    }

    @Test
    void hybridPreservesRecentMessages() {
        var manager = new SummarizingSlidingWindowConversationManager(new MockChatModel(), 100, 3);
        var messages = createLongMessages(12);

        var pruned = manager.prune(messages);

        var contents = pruned.stream().map(Message::content).toList();
        boolean hasNewest = contents.stream().anyMatch(c -> c.contains("Nachricht Nummer 11"));
        assertThat(hasNewest)
            .as("Expected newest message in pruned contents: %s", contents)
            .isTrue();
    }

    @Test
    void hybridResultStartsWithSystemMessage() {
        var manager = new SummarizingSlidingWindowConversationManager(new MockChatModel(), 100, 3);
        var messages = createLongMessages(12);

        var pruned = manager.prune(messages);

        assertThat(pruned).hasSizeGreaterThan(1);
        var types = pruned.stream().map(m -> m.getClass().getSimpleName()).toList();
        assertThat(types.get(0)).isEqualTo("SystemMessage");
    }

    @Test
    void hybridRejectsInvalidMaxTokens() {
        assertThatThrownBy(() -> new SummarizingSlidingWindowConversationManager(new MockChatModel(), 0, 3))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hybridKeepsSingleMessageAlways() {
        var manager = new SummarizingSlidingWindowConversationManager(new MockChatModel(), 1, 3);
        var messages = createLongMessages(1);

        var pruned = manager.prune(messages);

        assertThat(pruned).hasSize(1);
        assertThat(pruned.get(0)).isInstanceOf(UserMessage.class);
    }

    @Test
    void hybridKeepsAllWhenFewerUsersThanKeep() {
        var manager = new SummarizingSlidingWindowConversationManager(new MockChatModel(), 10_000, 5);
        var messages = createLongMessages(3);

        var pruned = manager.prune(messages);

        assertThat(pruned).hasSize(3);
    }

    @Test
    void hybridPreservesSystemMessages() {
        var manager = new SummarizingSlidingWindowConversationManager(new MockChatModel(), 10_000, 3);
        var messages = new ArrayList<Message>();
        messages.add(new SystemMessage("id-1", Instant.now(), "Sys-Anweisung", Map.of()));
        for (int i = 0; i < 5; i++) {
            messages.add(new UserMessage("id-" + (i + 2), Instant.now(), "Frage " + i, Map.of()));
        }

        var pruned = manager.prune(messages);

        assertThat(pruned).anyMatch(m -> m instanceof SystemMessage);
    }

    @Test
    void hybridHandlesEmptyList() {
        var manager = new SummarizingSlidingWindowConversationManager(new MockChatModel(), 100, 3);
        assertThat(manager.prune(List.of())).isEmpty();
    }

    @Test
    void hybridKeepsSystemMessagesUnaggregatedWhenUnderThreshold() {
        var manager = new SummarizingSlidingWindowConversationManager(new MockChatModel(), 10_000, 3);
        var messages = List.<Message>of(
            new SystemMessage("id-1", Instant.now(), "Regel A", Map.of()),
            new SystemMessage("id-2", Instant.now(), "Regel B", Map.of()));

        var pruned = manager.prune(messages);

        assertThat(pruned).hasSize(2);
        assertThat(pruned.get(0)).isInstanceOf(SystemMessage.class);
    }

    @Test
    void hybridAggregatesSystemMessagesWhenOverThreshold() {
        var manager = new SummarizingSlidingWindowConversationManager(new MockChatModel(), 1, 3);
        var messages = new ArrayList<Message>();
        for (int i = 0; i < 6; i++) {
            messages.add(new SystemMessage("s-" + i, Instant.now(), "Regel " + i, Map.of()));
        }
        for (int i = 0; i < 11; i++) {
            messages.add(new UserMessage("u-" + i, Instant.now(), "Frage " + i, Map.of()));
        }

        var pruned = manager.prune(messages);

        assertThat(pruned).hasSize(5);
        assertThat(pruned.get(0)).isInstanceOf(SystemMessage.class);
    }

    @Test
    void hybridRejectsInvalidKeepLast() {
        assertThatThrownBy(() -> new SummarizingSlidingWindowConversationManager(new MockChatModel(), 100, 0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SummarizingSlidingWindowConversationManager(new MockChatModel(), 100, -1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hybridHandlesMixedTypes() {
        var manager = new SummarizingSlidingWindowConversationManager(new MockChatModel(), 4000, 3);
        var messages = new ArrayList<Message>();
        messages.add(new SystemMessage("s1", Instant.now(), "Anweisung", Map.of()));
        messages.add(new UserMessage("u1", Instant.now(), "Hallo", Map.of()));
        messages.add(new AssistantMessage("a1", Instant.now(), "Hi!", Map.of(), List.of()));
        messages.add(new UserMessage("u2", Instant.now(), "Wetter?", Map.of()));
        messages.add(new AssistantMessage("a2", Instant.now(), "Sonnig", Map.of(), List.of()));
        messages.add(new ToolMessage("t1", Instant.now(), "tool-result", Map.of(), "call-1", "get_weather"));
        messages.add(new UserMessage("u3", Instant.now(), "Danke", Map.of()));

        var pruned = manager.prune(messages);

        assertThat(pruned).isNotEmpty();
        var sysCount = pruned.stream().filter(m -> m instanceof SystemMessage).count();
        assertThat(sysCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    void hybridPrunesWhenOverTokenBudget() {
        var manager = new SummarizingSlidingWindowConversationManager(new MockChatModel(), 50, 3);
        var messages = createLongMessages(12);

        var pruned = manager.prune(messages);

        assertThat(pruned).hasSizeLessThan(12);
        assertThat(pruned.get(0)).isInstanceOf(SystemMessage.class);
    }

    @Test
    void slidingWindowPreservesSystemMessages() {
        var manager = new SlidingWindowConversationManager(3);
        var messages = new ArrayList<Message>();
        messages.add(new SystemMessage("s1", Instant.now(), "System", Map.of()));
        for (int i = 1; i <= 5; i++) {
            messages.add(new UserMessage("id-" + i, Instant.now(), "Nachricht " + i, Map.of()));
        }

        var pruned = manager.prune(messages);

        assertThat(pruned.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(pruned).hasSize(4);
    }

    // ── Integration: SlidingWindow + Agent ─────────────────────

    @Test
    void agentWithSlidingWindowLimitsHistory() {
        var manager = new SlidingWindowConversationManager(2);
        var agent = new Agent(new MockChatModel(), new ToolRegistry(), new ToolExecutor(), manager);

        agent.execute("Frage 1");
        agent.execute("Frage 2");
        agent.execute("Frage 3");

        var memory = agent.getChatMemory();
        assertThat(memory.messages()).hasSizeLessThanOrEqualTo(4);
    }

    @Test
    void agentWithSlidingWindowWindowOne() {
        var manager = new SlidingWindowConversationManager(1);
        var agent = new Agent(new MockChatModel(), new ToolRegistry(), new ToolExecutor(), manager);

        agent.execute("Frage 1");
        agent.execute("Frage 2");

        var memory = agent.getChatMemory();
        assertThat(memory.messages()).hasSizeLessThanOrEqualTo(2);
    }

    // ── Integration: SummarizingSlidingWindow + Agent ──────────

    @Test
    void agentWithHybridProducesSummary() {
        var summarizer = new MockChatModel();
        var manager = new SummarizingSlidingWindowConversationManager(summarizer, 5, 3);
        var agent = new Agent(new MockChatModel(), new ToolRegistry(), new ToolExecutor(), manager);

        agent.execute("Dies ist eine sehr lange erste Nachricht, die viele Token verbraucht.");
        agent.execute("Dies ist eine weitere lange Nachricht, die hoffentlich das Limit ueberschreitet.");

        var result = agent.execute("Und noch eine dritte lange Nachricht fuer den Token-Counter.");

        assertThat(result.finalAnswer()).isNotEmpty();
        assertThat(result.stopReason()).isEqualTo(StopReason.COMPLETED);
    }

    // ── Backward Compatibility ────────────────────────────────────────

    @Test
    void agentWithoutConversationManagerStillWorks() {
        var agent = new Agent(new MockChatModel());

        var result1 = agent.execute("Hallo");
        var result2 = agent.execute("Wie geht es dir?");

        assertThat(result1.stopReason()).isEqualTo(StopReason.COMPLETED);
        assertThat(result2.stopReason()).isEqualTo(StopReason.COMPLETED);
        assertThat(agent.getChatMemory().messages()).hasSize(4);
    }

    @Test
    void agentWithNullConversationManagerWorks() {
        var agent = new Agent(new MockChatModel(), new ToolRegistry(), new ToolExecutor(), null);

        var result = agent.execute("Test");

        assertThat(result.stopReason()).isEqualTo(StopReason.COMPLETED);
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private static List<Message> createMessages(int count) {
        var messages = new ArrayList<Message>();
        for (int i = 0; i < count; i++) {
            messages.add(new UserMessage(
                "id-" + i, Instant.now(), "Nachricht " + i, Map.of()));
        }
        return messages;
    }

    private static List<Message> createLongMessages(int count) {
        var messages = new ArrayList<Message>();
        for (int i = 0; i < count; i++) {
            messages.add(new UserMessage(
                "id-" + i, Instant.now(),
                "Dies ist eine sehr lange Nachricht Nummer " + i
                + " mit vielen Zeichen, um den Token-Counter zu aktivieren. "
                + "Xyz abc def ghi jkl mno pqr stu vwx yz. "
                + "Noch mehr Text um die Laenge zu erhoehen.",
                Map.of()));
        }
        return messages;
    }
}
