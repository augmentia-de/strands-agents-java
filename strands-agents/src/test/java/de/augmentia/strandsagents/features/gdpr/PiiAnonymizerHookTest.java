package de.augmentia.strandsagents.features.gdpr;

import static org.assertj.core.api.Assertions.assertThat;

import de.augmentia.strandsagents.features.pipeline.HookContexts;
import de.augmentia.strandsagents.features.pipeline.HookResult;
import de.augmentia.strandsagents.features.gdpr.PiiAnonymizerHook.BlockAction;
import de.augmentia.strandsagents.features.gdpr.PiiAnonymizerHook.MaskType;
import de.augmentia.strandsagents.model.message.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class PiiAnonymizerHookTest {

    private static final Instant NOW = Instant.now();
    private static final Map<String, Object> EMPTY_META = Map.of();

    private HookContexts.BeforeModelCallContext contextWithMessages(List<Message> messages) {
        return new HookContexts.BeforeModelCallContext(
            "session-1", new StringBuilder(), messages, List.of(), new ArrayList<>());
    }

    @Test
    void masksEmailInUserMessage() {
        var hook = new PiiAnonymizerHook(
            EnumSet.of(MaskType.EMAIL), BlockAction.REDACT, "[EMAIL]");
        var msg = new UserMessage("1", NOW, "Kontakt: hans@example.com", EMPTY_META);
        var ctx = contextWithMessages(new ArrayList<>(List.of(msg)));

        var result = hook.beforeModelCall(ctx);

        assertThat(result).isInstanceOf(HookResult.Continue.class);
        assertThat(ctx.messages().getFirst().content()).doesNotContain("hans@example.com");
        assertThat(ctx.messages().getFirst().content()).contains("[EMAIL]");
    }

    @Test
    void masksPhoneNumber() {
        var hook = new PiiAnonymizerHook(
            EnumSet.of(MaskType.PHONE_NUMBER), BlockAction.REDACT, "[PHONE]");
        var msg = new UserMessage("1", NOW, "Meine Nummer: +49 170 1234567", EMPTY_META);
        var ctx = contextWithMessages(new ArrayList<>(List.of(msg)));

        hook.beforeModelCall(ctx);

        assertThat(ctx.messages().getFirst().content()).doesNotContain("+49 170 1234567");
        assertThat(ctx.messages().getFirst().content()).contains("[PHONE]");
    }

    @Test
    void masksGermanName() {
        var hook = new PiiAnonymizerHook(
            EnumSet.of(MaskType.NAME_DE), BlockAction.REDACT, "[NAME]");
        var msg = new UserMessage("1", NOW, "Ich bin Herr Max Mustermann", EMPTY_META);
        var ctx = contextWithMessages(new ArrayList<>(List.of(msg)));

        hook.beforeModelCall(ctx);

        assertThat(ctx.messages().getFirst().content()).doesNotContain("Herr Max Mustermann");
        assertThat(ctx.messages().getFirst().content()).contains("[NAME]");
    }

    @Test
    void masksCreditCard() {
        var hook = new PiiAnonymizerHook(
            EnumSet.of(MaskType.CREDIT_CARD), BlockAction.REDACT, "[CC]");
        var msg = new UserMessage("1", NOW, "Karte: 4111 1111 1111 1111", EMPTY_META);
        var ctx = contextWithMessages(new ArrayList<>(List.of(msg)));

        hook.beforeModelCall(ctx);

        assertThat(ctx.messages().getFirst().content()).doesNotContain("4111");
        assertThat(ctx.messages().getFirst().content()).contains("[CC]");
    }

    @Test
    void masksAddress() {
        var hook = new PiiAnonymizerHook(
            EnumSet.of(MaskType.ADDRESS), BlockAction.REDACT, "[ADDRESS]");
        var msg = new UserMessage("1", NOW, "Musterstr. 42, 12345 Berlin", EMPTY_META);
        var ctx = contextWithMessages(new ArrayList<>(List.of(msg)));

        hook.beforeModelCall(ctx);

        assertThat(ctx.messages().getFirst().content()).doesNotContain("Musterstr.");
        assertThat(ctx.messages().getFirst().content()).contains("[ADDRESS]");
    }

    @Test
    void doesNotModifyMessagesWithoutPii() {
        var hook = new PiiAnonymizerHook(
            EnumSet.allOf(MaskType.class), BlockAction.REDACT, "[PII]");
        var msg = new UserMessage("1", NOW, "Hallo, wie geht es dir?", EMPTY_META);
        var ctx = contextWithMessages(new ArrayList<>(List.of(msg)));

        var result = hook.beforeModelCall(ctx);

        assertThat(result).isInstanceOf(HookResult.Continue.class);
        assertThat(ctx.messages().getFirst().content()).isEqualTo("Hallo, wie geht es dir?");
    }

    @Test
    void blockActionThrowReturnsCancel() {
        var hook = new PiiAnonymizerHook(
            EnumSet.of(MaskType.EMAIL), BlockAction.THROW, "[PII]");
        var msg = new UserMessage("1", NOW, "email@test.com", EMPTY_META);
        var ctx = contextWithMessages(new ArrayList<>(List.of(msg)));

        var result = hook.beforeModelCall(ctx);

        assertThat(result).isInstanceOf(HookResult.Cancel.class);
        assertThat(((HookResult.Cancel) result).reason()).contains("blockiert");
    }

    @Test
    void masksAssistantMessage() {
        var hook = new PiiAnonymizerHook(
            EnumSet.of(MaskType.EMAIL), BlockAction.REDACT, "[EMAIL]");
        var msg = new AssistantMessage("1", NOW, "Schreib an user@test.com",
            EMPTY_META, List.of());
        var ctx = contextWithMessages(new ArrayList<>(List.of(msg)));

        hook.beforeModelCall(ctx);

        assertThat(ctx.messages().getFirst().content()).contains("[EMAIL]");
    }

    @Test
    void masksSystemMessage() {
        var hook = new PiiAnonymizerHook(
            EnumSet.of(MaskType.EMAIL), BlockAction.REDACT, "[EMAIL]");
        var msg = new SystemMessage("1", NOW, "System: admin@example.com", EMPTY_META);
        var ctx = contextWithMessages(new ArrayList<>(List.of(msg)));

        hook.beforeModelCall(ctx);

        assertThat(ctx.messages().getFirst().content()).contains("[EMAIL]");
    }

    @Test
    void masksToolMessage() {
        var hook = new PiiAnonymizerHook(
            EnumSet.of(MaskType.EMAIL), BlockAction.REDACT, "[EMAIL]");
        var msg = new ToolMessage("1", NOW, "tool@result.com", EMPTY_META, "call-1", "test");
        var ctx = contextWithMessages(new ArrayList<>(List.of(msg)));

        hook.beforeModelCall(ctx);

        assertThat(ctx.messages().getFirst().content()).contains("[EMAIL]");
    }

    @Test
    void skipsNullContentGracefully() {
        var hook = new PiiAnonymizerHook(
            EnumSet.of(MaskType.EMAIL), BlockAction.REDACT, "[EMAIL]");
        var msg = new UserMessage("1", NOW, null, EMPTY_META);
        var ctx = contextWithMessages(new ArrayList<>(List.of(msg)));

        var result = hook.beforeModelCall(ctx);

        assertThat(result).isInstanceOf(HookResult.Continue.class);
    }

    @Test
    void doesNotModifyAdditionalMessages() {
        var hook = new PiiAnonymizerHook(
            EnumSet.of(MaskType.EMAIL), BlockAction.REDACT, "[EMAIL]");
        var userMsg = new UserMessage("1", NOW, "test@mail.com", EMPTY_META);
        var additional = new SystemMessage("2", NOW, "secret@data.com", EMPTY_META);
        var messages = new ArrayList<Message>(List.of(userMsg));
        var ctx = new HookContexts.BeforeModelCallContext(
            "s1", new StringBuilder(), messages, List.of(), new ArrayList<Message>(List.of(additional)));

        hook.beforeModelCall(ctx);

        assertThat(messages.getFirst().content()).contains("[EMAIL]");
        assertThat(ctx.additionalMessages().getFirst().content()).isEqualTo("secret@data.com");
    }

    @Test
    void nameReturnsCorrectIdentifier() {
        var hook = new PiiAnonymizerHook(EnumSet.of(MaskType.EMAIL), BlockAction.REDACT, "[PII]");
        assertThat(hook.name()).isEqualTo("gdpr-pii-anonymizer");
    }
}
