package com.gridynamics.forge.guardrail.semantic;

import com.gridynamics.forge.guardrail.util.EmbeddingNormalizationUtil;

import java.util.Comparator;
import java.util.List;

/**
 * In-memory {@link SemanticStore} backed by a fixed list of seed entries.
 *
 * <p>Seed embeddings are computed once at startup by {@code SemanticStartupInitializer}
 * and held in the JVM heap. At 119 seeds × 384 dimensions × 4 bytes the total
 * footprint is ~183 KB — negligible. Nearest-neighbour search is a linear scan
 * over normalised vectors (cosine similarity = dot product for unit vectors),
 * which takes ~5 µs per request and requires no external infrastructure.
 *
 * <p>The store starts empty; call {@link #initialize(List)} once on startup.
 */
public final class InMemorySemanticStore implements SemanticStore {

    /** Immutable snapshot of embedded seed entries; replaced atomically on init. */
    private volatile List<SeedEntry> entries = List.of();

    /**
     * Populates the store with pre-embedded seed entries.
     * Called once by {@code SemanticStartupInitializer} on {@code ApplicationReadyEvent}.
     */
    public void initialize(List<SeedEntry> seedEntries) {
        this.entries = List.copyOf(seedEntries);
    }

    /** Returns the number of loaded seed patterns. */
    public int size() {
        return entries.size();
    }

    @Override
    public List<SemanticMatch> findNearest(float[] queryEmbedding, int topK) {
        float[] normalized = EmbeddingNormalizationUtil.normalize(queryEmbedding);
        List<SeedEntry> snapshot = entries;

        return snapshot.stream()
                .map(e -> new SemanticMatch(
                        e.text(),
                        e.description(),
                        e.category(),
                        dotProduct(normalized, e.embedding())))
                .sorted(Comparator.comparingDouble(SemanticMatch::similarity).reversed())
                .limit(topK)
                .toList();
    }

    /**
     * Dot product of two L2-normalised vectors equals their cosine similarity.
     * Both vectors are guaranteed to be unit-length: the query is normalised in
     * {@link #findNearest} and seed embeddings were normalised before being stored.
     */
    private static double dotProduct(float[] a, float[] b) {
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            sum += (double) a[i] * b[i];
        }
        return sum;
    }

    /**
     * A single seed entry: raw text, human-readable description, semantic category,
     * and its L2-normalised embedding vector produced by the ONNX model.
     */
    public record SeedEntry(SemanticCategory category, String description, String text, float[] embedding) {}
}
