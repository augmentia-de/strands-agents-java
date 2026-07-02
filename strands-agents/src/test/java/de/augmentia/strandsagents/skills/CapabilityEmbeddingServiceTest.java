package de.augmentia.strandsagents.skills;

import static org.assertj.core.api.Assertions.assertThat;

import de.augmentia.strandsagents.skills.CapabilityEmbeddingService;
import de.augmentia.strandsagents.skills.CapabilityRegistry;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CapabilityEmbeddingServiceTest {

    static class FixedEmbeddingModel implements EmbeddingModel {
        private final Map<String, Embedding> embeddings = new HashMap<>();

        void set(String text, Embedding embedding) {
            embeddings.put(text, embedding);
        }

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
            var list = segments.stream()
                .map(s -> embeddings.getOrDefault(s.text(), zeroEmbedding()))
                .toList();
            return Response.from(list);
        }

        @Override
        public Response<Embedding> embed(String text) {
            return Response.from(embeddings.getOrDefault(text, zeroEmbedding()));
        }
    }

    private static Embedding zeroEmbedding() {
        return new Embedding(new float[]{0, 0, 0});
    }

    private static Embedding emb(float... v) {
        return new Embedding(v);
    }

    @Test
    void cosineSimilarity_identical_isOne() {
        var a = emb(1, 2, 3);
        assertThat(CapabilityEmbeddingService.cosineSimilarity(a, a)).isEqualTo(1.0);
    }

    @Test
    void cosineSimilarity_orthogonal_isZero() {
        var a = emb(1, 0, 0);
        var b = emb(0, 1, 0);
        assertThat(CapabilityEmbeddingService.cosineSimilarity(a, b)).isCloseTo(0.0, offset(1e-10));
    }

    @Test
    void cosineSimilarity_opposite_isMinusOne() {
        var a = emb(1, 0, 0);
        var b = emb(-1, 0, 0);
        assertThat(CapabilityEmbeddingService.cosineSimilarity(a, b)).isCloseTo(-1.0, offset(1e-10));
    }

    @Test
    void search_returnsMatchingAboveThreshold() {
        var model = new FixedEmbeddingModel();
        var writeCap = new CapabilityRegistry.Capability("write", "Write content to a file", "default",
            CapabilityRegistry.CapabilityType.DEFAULT);
        var readCap = new CapabilityRegistry.Capability("read", "Read content from a file", "default",
            CapabilityRegistry.CapabilityType.DEFAULT);

        model.set("write: Write content to a file", emb(1, 0, 0));
        model.set("read: Read content from a file", emb(0, 1, 0));
        model.set("write a file", emb(0.9f, 0.1f, 0));

        var service = new CapabilityEmbeddingService(model, List.of(writeCap, readCap), 0.75);
        var results = service.search("write a file");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).name()).isEqualTo("write");
    }

    @Test
    void search_noMatch_returnsEmpty() {
        var model = new FixedEmbeddingModel();
        var writeCap = new CapabilityRegistry.Capability("write", "Write content", "default",
            CapabilityRegistry.CapabilityType.DEFAULT);

        model.set("write: Write content", emb(1, 0, 0));
        model.set("unrelated task", emb(0, 0, 1));

        var service = new CapabilityEmbeddingService(model, List.of(writeCap), 0.75);
        var results = service.search("unrelated task");

        assertThat(results).isEmpty();
    }

    @Test
    void search_emptyTask_returnsEmpty() {
        var model = new FixedEmbeddingModel();
        var cap = new CapabilityRegistry.Capability("x", "desc", "src",
            CapabilityRegistry.CapabilityType.DEFAULT);
        model.set("x: desc", emb(1, 0, 0));

        var service = new CapabilityEmbeddingService(model, List.of(cap), 0.5);
        assertThat(service.search(null)).isEmpty();
        assertThat(service.search("  ")).isEmpty();
    }

    private static org.assertj.core.data.Offset<Double> offset(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }
}
