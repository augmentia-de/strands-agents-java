package de.augmentia.strandsagents.features.gdpr;

import de.augmentia.strandsagents.features.pipeline.AgentHook;
import de.augmentia.strandsagents.features.pipeline.HookContexts;
import de.augmentia.strandsagents.features.pipeline.HookResult;
import de.augmentia.strandsagents.model.message.*;

import java.util.*;
import java.util.regex.Pattern;

public class PiiAnonymizerHook implements AgentHook {

    public enum MaskType { EMAIL, PHONE_NUMBER, NAME_DE, CREDIT_CARD, ADDRESS }
    public enum BlockAction { REDACT, THROW, MOCK }

    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE_PATTERN =
        Pattern.compile("(?:\\+49|0)[\\s-]?[1-9][0-9\\.\\-\\s/]{6,20}");
    private static final Pattern CREDIT_CARD_PATTERN =
        Pattern.compile("\\b(?:\\d[ -]*?){13,16}\\b");
    private static final Pattern NAME_PATTERN_DE =
        Pattern.compile("\\b(?:Herr|Frau|Dr\\.?|Prof\\.?)\\s+[A-Z][a-zäöüß]+(?:\\s+[A-Z][a-zäöüß]+)*\\b");
    private static final Pattern ADDRESS_PATTERN_DE =
        Pattern.compile("\\b[A-Za-zäöüß]+(?:\\.)?\\s+\\d+\\s*,\\s*\\d{5}\\s+[A-Za-zäöüß]+\\b");

    private final Set<MaskType> maskTypes;
    private final BlockAction blockAction;
    private final String replacement;

    public PiiAnonymizerHook(Set<MaskType> maskTypes, BlockAction blockAction, String replacement) {
        this.maskTypes = maskTypes;
        this.blockAction = blockAction;
        this.replacement = replacement;
    }

    @Override
    public String name() {
        return "gdpr-pii-anonymizer";
    }

    @Override
    public HookResult beforeModelCall(HookContexts.BeforeModelCallContext ctx) {
        var messages = ctx.messages();
        var modified = false;

        for (int i = 0; i < messages.size(); i++) {
            var msg = messages.get(i);
            var content = msg.content();
            if (content == null) continue;

            var sanitized = maskPii(content);
            if (!sanitized.equals(content)) {
                modified = true;
                messages.set(i, createMaskedMessage(msg, sanitized));
            }
        }

        if (!modified) {
            return new HookResult.Continue();
        }

        if (blockAction == BlockAction.THROW) {
            return new HookResult.Cancel(
                "Prompt enthält personenbezogene Daten: Anfrage blockiert");
        }

        return new HookResult.Continue();
    }

    private String maskPii(String text) {
        var result = text;
        if (maskTypes.contains(MaskType.EMAIL)) {
            result = EMAIL_PATTERN.matcher(result).replaceAll(replacement);
        }
        if (maskTypes.contains(MaskType.PHONE_NUMBER)) {
            result = PHONE_PATTERN.matcher(result).replaceAll(replacement);
        }
        if (maskTypes.contains(MaskType.CREDIT_CARD)) {
            result = CREDIT_CARD_PATTERN.matcher(result).replaceAll(replacement);
        }
        if (maskTypes.contains(MaskType.NAME_DE)) {
            result = NAME_PATTERN_DE.matcher(result).replaceAll(replacement);
        }
        if (maskTypes.contains(MaskType.ADDRESS)) {
            result = ADDRESS_PATTERN_DE.matcher(result).replaceAll(replacement);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private <T extends Message> T createMaskedMessage(T original, String sanitized) {
        return switch (original) {
            case UserMessage m -> (T) new UserMessage(m.id(), m.timestamp(), sanitized, m.metadata());
            case SystemMessage m -> (T) new SystemMessage(m.id(), m.timestamp(), sanitized, m.metadata());
            case AssistantMessage m -> (T) new AssistantMessage(
                m.id(), m.timestamp(), sanitized, m.metadata(), m.toolCalls());
            case ToolMessage m -> (T) new ToolMessage(
                m.id(), m.timestamp(), sanitized, m.metadata(), m.toolCallId(), m.toolName());
            default -> original;
        };
    }
}
