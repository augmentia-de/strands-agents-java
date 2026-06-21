package de.augmentia.strandsagents.examples.hooks;

import de.augmentia.strandsagents.features.pipeline.AgentHook;
import de.augmentia.strandsagents.features.pipeline.HookContexts;
import de.augmentia.strandsagents.features.pipeline.HookResult;
import dev.langchain4j.model.openai.OpenAiChatResponseMetadata;
import dev.langchain4j.model.openai.OpenAiTokenUsage;

public class CacheLoggingHook implements AgentHook {

    @Override
    public String name() {
        return "cache-logger";
    }

    @Override
    public HookResult afterModelCall(HookContexts.AfterModelCallContext ctx, String llmResponse) {
        var response = ctx.chatResponse();
        if (response == null) {
            return new HookResult.Continue();
        }

        var metadata = response.metadata();
        if (!(metadata instanceof OpenAiChatResponseMetadata oaiMeta)) {
            return new HookResult.Continue();
        }

        var tokenUsage = oaiMeta.tokenUsage();
        var modelName = oaiMeta.modelName();
        var inputTokens = tokenUsage != null ? tokenUsage.inputTokenCount() : 0;
        var outputTokens = tokenUsage != null ? tokenUsage.outputTokenCount() : 0;

        System.out.println("  [Cache] Model:         " + modelName);
        System.out.println("  [Cache] ID:            " + oaiMeta.id());

        if (tokenUsage instanceof OpenAiTokenUsage oaiUsage) {
            var details = oaiUsage.inputTokensDetails();
            var cached = details != null ? details.cachedTokens() : null;
            if (cached != null && cached > 0) {
                var pct = 100.0 * cached / Math.max(inputTokens, 1);
                System.out.println("  [Cache] Input tokens:  " + inputTokens + " (cached: " + cached + ")");
                System.out.printf("  [Cache] Cache hit:     %.1f%%%n", pct);
            } else {
                System.out.println("  [Cache] Input tokens:  " + inputTokens + " (no cache)");
            }
            var outDetails = oaiUsage.outputTokensDetails();
            var reasoning = outDetails != null ? outDetails.reasoningTokens() : null;
            if (reasoning != null && reasoning > 0) {
                System.out.println("  [Cache] Output tokens: " + outputTokens + " (reasoning: " + reasoning + ")");
            } else {
                System.out.println("  [Cache] Output tokens: " + outputTokens);
            }
        } else {
            System.out.println("  [Cache] Input tokens:  " + inputTokens);
            System.out.println("  [Cache] Output tokens: " + outputTokens);
            System.out.println("  [Cache] (token details not available for this model)");
        }
        return new HookResult.Continue();
    }
}
