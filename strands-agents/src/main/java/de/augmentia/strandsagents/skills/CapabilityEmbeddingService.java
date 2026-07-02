package de.augmentia.strandsagents.skills;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

public class CapabilityEmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final List<CapabilityRegistry.Capability> capabilities;
    private final List<Embedding> embeddings;
    private final double threshold;

    public CapabilityEmbeddingService(EmbeddingModel embeddingModel,
                                       List<CapabilityRegistry.Capability> capabilities,
                                       double threshold) {
        this.embeddingModel = embeddingModel;
        this.capabilities = List.copyOf(capabilities);
        this.threshold = threshold;
        this.embeddings = buildEmbeddings(capabilities);
    }

    private List<Embedding> buildEmbeddings(List<CapabilityRegistry.Capability> caps) {
        var segments = caps.stream()
            .map(c -> TextSegment.from(c.name() + ": " + c.description()))
            .toList();
        return embeddingModel.embedAll(segments).content();
    }

    public List<CapabilityRegistry.Capability> search(String task) {
        return searchTopN(task, Integer.MAX_VALUE);
    }

    public List<CapabilityRegistry.Capability> searchTopN(String task, int n) {
        if (task == null || task.isBlank()) return List.of();
        var queryEmbedding = embeddingModel.embed(task).content();
        return IntStream.range(0, capabilities.size())
            .mapToObj(i -> {
                var sim = cosineSimilarity(queryEmbedding, embeddings.get(i));
                return new Scored(i, sim);
            })
            .filter(s -> s.similarity >= threshold)
            .sorted(Comparator.comparingDouble(Scored::similarity).reversed())
            .limit(n)
            .map(s -> capabilities.get(s.index()))
            .toList();
    }

    private record Scored(int index, double similarity) {}

    static double cosineSimilarity(Embedding a, Embedding b) {
        float[] va = a.vector();
        float[] vb = b.vector();
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < va.length; i++) {
            dot += va[i] * vb[i];
            normA += va[i] * va[i];
            normB += vb[i] * vb[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
