package de.augmentia.strandsagents.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

class MultiSystemMessageChatMemoryTest {

    @Test
    void preservesSystemMessagesDuringPrune() {
        var mem = new MultiSystemMessageChatMemory(2);
        mem.add(SystemMessage.from("Du bist ein Helfer."));
        mem.add(UserMessage.from("Hallo"));
        mem.add(AiMessage.from("Hi"));
        mem.add(UserMessage.from("Wie gehts?"));
        mem.add(AiMessage.from("Gut"));

        var msgs = mem.messages();
        assertThat(msgs).hasSize(3);
        assertThat(msgs.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(((SystemMessage) msgs.get(0)).text()).isEqualTo("Du bist ein Helfer.");
        assertThat(msgs.get(1)).isInstanceOf(UserMessage.class);
        assertThat(msgs.get(2)).isInstanceOf(AiMessage.class);
    }

    @Test
    void evictsOldestNonSystemFirst() {
        var mem = new MultiSystemMessageChatMemory(3);
        mem.add(UserMessage.from("Eins"));
        mem.add(UserMessage.from("Zwei"));
        mem.add(UserMessage.from("Drei"));
        mem.add(UserMessage.from("Vier"));

        assertThat(mem.messages()).hasSize(3);
        assertThat(((UserMessage) mem.messages().get(0)).singleText()).isEqualTo("Zwei");
        assertThat(((UserMessage) mem.messages().get(2)).singleText()).isEqualTo("Vier");
    }

    @Test
    void replacesFirstSystemMessage() {
        var mem = new MultiSystemMessageChatMemory(10);
        mem.add(SystemMessage.from("original"));
        mem.add(UserMessage.from("Hallo"));
        mem.replaceFirstSystemMessage(SystemMessage.from("neu"));

        assertThat(((SystemMessage) mem.messages().get(0)).text()).isEqualTo("neu");
        assertThat(mem.messages()).hasSize(2);
    }

    @Test
    void replaceFirstSystemMessageOnEmptyDoesNothing() {
        var mem = new MultiSystemMessageChatMemory(10);
        mem.replaceFirstSystemMessage(SystemMessage.from("neu"));
        assertThat(mem.messages()).isEmpty();
    }

    @Test
    void replacesOnlyFirstSystemMessage() {
        var mem = new MultiSystemMessageChatMemory(10);
        mem.add(SystemMessage.from("erste"));
        mem.add(SystemMessage.from("zweite"));
        mem.replaceFirstSystemMessage(SystemMessage.from("ersetzt"));

        assertThat(((SystemMessage) mem.messages().get(0)).text()).isEqualTo("ersetzt");
        assertThat(((SystemMessage) mem.messages().get(1)).text()).isEqualTo("zweite");
    }

    @Test
    void rejectsInvalidMaxMessages() {
        assertThatThrownBy(() -> new MultiSystemMessageChatMemory(0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MultiSystemMessageChatMemory(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void clearRemovesAll() {
        var mem = new MultiSystemMessageChatMemory(10);
        mem.add(SystemMessage.from("sys"));
        mem.add(UserMessage.from("Hallo"));
        mem.clear();
        assertThat(mem.messages()).isEmpty();
    }

    @Test
    void handlesMixedTypes() {
        var mem = new MultiSystemMessageChatMemory(10);
        mem.add(SystemMessage.from("S"));
        mem.add(UserMessage.from("U1"));
        mem.add(AiMessage.from("A1"));
        mem.add(UserMessage.from("U2"));
        mem.add(AiMessage.from("A2"));

        assertThat(mem.messages()).hasSize(5);
        assertThat(mem.messages()).map(ChatMessage::type)
            .containsExactly(
                dev.langchain4j.data.message.ChatMessageType.SYSTEM,
                dev.langchain4j.data.message.ChatMessageType.USER,
                dev.langchain4j.data.message.ChatMessageType.AI,
                dev.langchain4j.data.message.ChatMessageType.USER,
                dev.langchain4j.data.message.ChatMessageType.AI);
    }

    @Test
    void idIsUnique() {
        var mem1 = new MultiSystemMessageChatMemory(10);
        var mem2 = new MultiSystemMessageChatMemory(10);
        assertThat(mem1.id()).isNotNull();
        assertThat(mem1.id()).isNotEqualTo(mem2.id());
    }
}
