package de.augmentia.strandsagents.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.augmentia.strandsagents.core.agent.MockChatModel;
import de.augmentia.strandsagents.core.agent.StrandsAgent;
import de.augmentia.strandsagents.core.conversation.SlidingWindowConversationManager;
import de.augmentia.strandsagents.core.conversation.SummarizingConversationManager;
import de.augmentia.strandsagents.core.model.agent.StopReason;
import de.augmentia.strandsagents.core.model.message.Message;
import de.augmentia.strandsagents.core.model.message.SystemMessage;
import de.augmentia.strandsagents.core.model.message.UserMessage;
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

    // ── Summarizing ───────────────────────────────────────────────────

    @Test
    void summarizingDoesNothingWhenUnderMaxTokens() {
        var manager = new SummarizingConversationManager(new MockChatModel(), 10_000);
        var messages = createMessages(3);

        var pruned = manager.prune(messages);

        assertThat(pruned).hasSize(3);
        assertThat(pruned).allMatch(m -> !(m instanceof SystemMessage));
    }

    @Test
    void summarizingReplacesOldMessagesWhenOverBudget() {
        var manager = new SummarizingConversationManager(new MockChatModel(), 10);
        var messages = createLongMessages(4);

        var pruned = manager.prune(messages);

        assertThat(pruned).isNotEmpty();
        assertThat(pruned.get(0)).isInstanceOf(SystemMessage.class);
        var summary = (SystemMessage) pruned.get(0);
        assertThat(summary.content()).startsWith("Zusammenfassung");
    }

    @Test
    void summarizingPreservesRecentMessages() {
        var manager = new SummarizingConversationManager(new MockChatModel(), 10);
        var messages = createLongMessages(4);

        var pruned = manager.prune(messages);

        var contents = pruned.stream().map(Message::content).toList();
        boolean hasNewest = contents.stream().anyMatch(c -> c.contains("Nachricht Nummer 3"));
        assertThat(hasNewest)
            .as("Expected newest message in pruned contents: %s", contents)
            .isTrue();
    }

    @Test
    void summarizingResultStartsWithSystemMessage() {
        var manager = new SummarizingConversationManager(new MockChatModel(), 10);
        var messages = createLongMessages(4);

        var pruned = manager.prune(messages);

        assertThat(pruned).hasSizeGreaterThan(1);
        var types = pruned.stream().map(m -> m.getClass().getSimpleName()).toList();
        assertThat(types.get(0)).isEqualTo("SystemMessage");
    }

    @Test
    void summarizingRejectsInvalidMaxTokens() {
        assertThatThrownBy(() -> new SummarizingConversationManager(new MockChatModel(), 0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void summarizingKeepsSingleMessageAlways() {
        var manager = new SummarizingConversationManager(new MockChatModel(), 1);
        var messages = createLongMessages(1);

        var pruned = manager.prune(messages);

        assertThat(pruned).hasSize(1);
        assertThat(pruned.get(0)).isInstanceOf(UserMessage.class);
    }

    // ── Integration: SlidingWindow + StrandsAgent ─────────────────────

    @Test
    void agentWithSlidingWindowLimitsHistory() {
        var manager = new SlidingWindowConversationManager(2);
        var agent = new StrandsAgent(new MockChatModel(), new ToolRegistry(), new ToolExecutor(), manager);

        agent.execute("Frage 1");
        agent.execute("Frage 2");
        agent.execute("Frage 3");

        var memory = agent.getChatMemory();
        assertThat(memory.messages()).hasSizeLessThanOrEqualTo(4);
    }

    @Test
    void agentWithSlidingWindowWindowOne() {
        var manager = new SlidingWindowConversationManager(1);
        var agent = new StrandsAgent(new MockChatModel(), new ToolRegistry(), new ToolExecutor(), manager);

        agent.execute("Frage 1");
        agent.execute("Frage 2");

        var memory = agent.getChatMemory();
        assertThat(memory.messages()).hasSizeLessThanOrEqualTo(2);
    }

    // ── Integration: Summarizing + StrandsAgent ───────────────────────

    @Test
    void agentWithSummarizingProducesSummary() {
        var summarizer = new MockChatModel();
        var manager = new SummarizingConversationManager(summarizer, 5);
        var agent = new StrandsAgent(new MockChatModel(), new ToolRegistry(), new ToolExecutor(), manager);

        agent.execute("Dies ist eine sehr lange erste Nachricht, die viele Token verbraucht.");
        agent.execute("Dies ist eine weitere lange Nachricht, die hoffentlich das Limit ueberschreitet.");

        var result = agent.execute("Und noch eine dritte lange Nachricht fuer den Token-Counter.");

        assertThat(result.finalAnswer()).isNotEmpty();
        assertThat(result.stopReason()).isEqualTo(StopReason.COMPLETED);
    }

    // ── Backward Compatibility ────────────────────────────────────────

    @Test
    void agentWithoutConversationManagerStillWorks() {
        var agent = new StrandsAgent(new MockChatModel());

        var result1 = agent.execute("Hallo");
        var result2 = agent.execute("Wie geht es dir?");

        assertThat(result1.stopReason()).isEqualTo(StopReason.COMPLETED);
        assertThat(result2.stopReason()).isEqualTo(StopReason.COMPLETED);
        assertThat(agent.getChatMemory().messages()).hasSize(4);
    }

    @Test
    void agentWithNullConversationManagerWorks() {
        var agent = new StrandsAgent(new MockChatModel(), new ToolRegistry(), new ToolExecutor(), null);

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
