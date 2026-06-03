package de.augmentia.strandsagents.examples.feature;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.agent.Agent;
import de.augmentia.strandsagents.core.config.AgentConfig;
import de.augmentia.strandsagents.core.config.ModelFactory;
import de.augmentia.strandsagents.core.model.agent.AgentResult;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class WorkflowDemo {

    record ResearchData(String topic, List<String> keyPoints, List<String> sources) {}
    record ArticleDraft(String title, String introduction, List<String> sections) {}
    record ReviewFeedback(String verdict, List<String> changes, boolean approved) {}
    record PublishedArticle(String title, String content, String slug, int wordCount) {}

    private static ObjectMapper createMapper() {
        var m = new ObjectMapper();
        m.registerModule(new JavaTimeModule());
        m.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        m.configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
        return m;
    }

    private static final ObjectMapper MAPPER = createMapper();
    private static final String TOPIC = "The Future of AI-Assisted Software Development";

    public static void main(String[] args) {
        if (System.getenv("OPENAI_API_KEY") == null || System.getenv("OPENAI_API_KEY").isBlank()) {
            System.out.println("Error: OPENAI_API_KEY is not set.");
            System.exit(1);
        }
        new WorkflowDemo().run();
    }

    void run() {
        Map<String, Object> state = new HashMap<>();

        var model = ModelFactory.createOpenAiFromEnv();

        System.out.println("=== CONTENT PIPELINE WORKFLOW ===\n");
        System.out.println("Thema: " + TOPIC + "\n");

        // ---------------------------------------------------------------
        // Step 1: Research – via web_search echte Informationen sammeln
        // ---------------------------------------------------------------
        System.out.println("[Step 1/4] Research (web_search) ────");

        var researchAgent = AgentConfig.builder()
            .structuredOutputModel(ResearchData.class)
            .toolRegistry(ToolRegistry.builder().standard().include("web_search", "web_fetch").build())
            .logLlmCalls(Path.of("logs/llm-calls.log"))
            .build()
            .createAgent(model);

        var r1 = exec(researchAgent,
            "Use the web_search tool to find current information about the topic '" + TOPIC + "'.\n" +
            "Search for recent developments, key trends, and relevant sources.\n" +
            "Then use web_fetch to retrieve the full content of at least one relevant source.\n" +
            "Finally, extract 3-5 key points and list all sources you found.");

        var research = parse(r1, ResearchData.class);
        state.put("research", research);
        if (research == null) { System.out.println("  Research failed"); return; }
        System.out.println("  Topic: " + research.topic());
        System.out.println("  Key Points: " + String.join(" | ", research.keyPoints()));
        System.out.println("  Sources: " + String.join(", ", research.sources()));
        System.out.println();

        // ---------------------------------------------------------------
        // Step 2: Draft – Artikel auf Basis der Research-Daten schreiben
        // ---------------------------------------------------------------
        System.out.println("[Step 2/4] Draft ────────────────────");

        var draftAgent = AgentConfig.builder()
            .structuredOutputModel(ArticleDraft.class)
            .build()
            .createAgent(model);

        var r2 = exec(draftAgent,
            "Write an article draft based on this research data:\n" +
            "Key Points: " + String.join(", ", research.keyPoints()) + "\n" +
            "Sources: "    + String.join(", ", research.sources()));

        var draft = parse(r2, ArticleDraft.class);
        state.put("draft", draft);
        if (draft == null) { System.out.println("  Draft failed"); return; }
        System.out.println("  Title: " + draft.title());
        System.out.println("  Sections: " + String.join(" | ", draft.sections()));
        System.out.println();

        // ---------------------------------------------------------------
        // Step 3: Review – Entwurf bewerten (liest aus state["draft"])
        // ---------------------------------------------------------------
        System.out.println("[Step 3/4] Review ───────────────────");

        var reviewAgent = AgentConfig.builder()
            .structuredOutputModel(ReviewFeedback.class)
            .build()
            .createAgent(model);

        var r3 = exec(reviewAgent,
            "Review the following article draft:\n" +
            "Title: "        + draft.title() + "\n" +
            "Introduction: " + draft.introduction() + "\n" +
            "Sections: "     + String.join(", ", draft.sections()) + "\n\n" +
            "Give constructive feedback and list necessary changes.");

        var review = parse(r3, ReviewFeedback.class);
        state.put("review", review);
        if (review == null) { System.out.println("  Review failed"); return; }
        System.out.println("  Verdict: " + review.verdict());
        System.out.println("  Changes: " + String.join(" | ", review.changes()));
        System.out.println("  Approved: " + review.approved());
        System.out.println();

        // ---------------------------------------------------------------
        // Step 4: Publish – finaler Artikel via write-Tool speichern
        // ---------------------------------------------------------------
        System.out.println("[Step 4/4] Publish (write tool) ────");

        var publishAgent = AgentConfig.builder()
            .structuredOutputModel(PublishedArticle.class)
            .toolRegistry(ToolRegistry.builder().standard().include("write").build())
            .build()
            .createAgent(model);

        var prevDraft  = (ArticleDraft)  state.get("draft");
        var prevReview = (ReviewFeedback) state.get("review");

        var r4 = exec(publishAgent,
            "Write the final article to the file 'output/workflow/article.md' using the write tool.\n" +
            "The article must include the full content.\n\n" +
            "Use this draft as the basis:\n" +
            "Title: "       + prevDraft.title() + "\n" +
            "Introduction: " + prevDraft.introduction() + "\n" +
            "Sections: "    + String.join(", ", prevDraft.sections()) + "\n\n" +
            "Apply these review changes:\n" +
            "  " + String.join("\n  ", prevReview.changes()) + "\n\n" +
            "After writing the file, return the published article metadata " +
            "(title, content, slug, word count) as structured data.");

        var published = parse(r4, PublishedArticle.class);
        if (published == null) { System.out.println("  Publish failed"); return; }
        System.out.println("  Title: " + published.title());
        System.out.println("  Slug: " + published.slug());
        System.out.println("  WordCount: " + published.wordCount());
        System.out.println("  Content: " + truncate(published.content(), 200));
        System.out.println();

        // ---------------------------------------------------------------
        // Zusammenfassung
        // ---------------------------------------------------------------
        System.out.println("=== WORKFLOW COMPLETE ===");
        System.out.println("State-Map Inhalt:");
        state.forEach((key, value) -> {
            System.out.print("  " + key + " -> ");
            if (value instanceof ResearchData rd)
                System.out.println("ResearchData(topic=" + rd.topic() + ", points=" + rd.keyPoints().size() + ")");
            else if (value instanceof ArticleDraft ad)
                System.out.println("ArticleDraft(title=" + ad.title() + ", sections=" + ad.sections().size() + ")");
            else if (value instanceof ReviewFeedback rf)
                System.out.println("ReviewFeedback(verdict=" + rf.verdict() + ", approved=" + rf.approved() + ")");
            else
                System.out.println(value);
        });
        System.out.println("\nArtifact saved to: " + Path.of("output/workflow/article.md").toAbsolutePath().normalize());
        System.out.println("Demonstriert: per-agent Tools (web_search, write), Structured Output & Cross-Step Access");
    }

    private AgentResult exec(Agent agent, String prompt) {
        var result = agent.execute(prompt);
        System.out.println("  Tokens: " + result.metrics().inputTokens()
            + " in / " + result.metrics().outputTokens() + " out, "
            + result.metrics().durationMs() + " ms");
        return result;
    }

    private <T> T parse(AgentResult result, Class<T> type) {
        try {
            return MAPPER.readValue(result.structuredOutput(), type);
        } catch (Exception e) {
            System.out.println("  Parse error for " + type.getSimpleName()
                + ": " + e.getMessage());
            return null;
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "null";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
