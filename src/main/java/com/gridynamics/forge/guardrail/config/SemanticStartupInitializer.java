package com.gridynamics.forge.guardrail.config;

import com.gridynamics.forge.guardrail.embedding.EmbeddingProvider;
import com.gridynamics.forge.guardrail.semantic.InMemorySemanticStore;
import com.gridynamics.forge.guardrail.semantic.SemanticCategory;
import com.gridynamics.forge.guardrail.util.EmbeddingNormalizationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates the semantic validation layer at startup and populates the
 * {@link InMemorySemanticStore} by embedding every phrase in
 * {@code classpath:semantic/semantic_seeds.csv} using the ONNX model.
 *
 * <p>No database is required. All seed embeddings live in the JVM heap
 * (~183 KB for 119 phrases × 384 dimensions).
 */
public final class SemanticStartupInitializer {

    private static final Logger log = LoggerFactory.getLogger(SemanticStartupInitializer.class);
    private static final String SEEDS_RESOURCE = "semantic/semantic_seeds.csv";
    private static final String DELIMITER = "\\|";

    private final GuardrailProperties props;
    private final EmbeddingProvider embeddingProvider;
    private final InMemorySemanticStore semanticStore;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    public SemanticStartupInitializer(GuardrailProperties props,
                                       EmbeddingProvider embeddingProvider,
                                       InMemorySemanticStore semanticStore,
                                       ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.props = props;
        this.embeddingProvider = embeddingProvider;
        this.semanticStore = semanticStore;
        this.redisTemplateProvider = redisTemplateProvider;
    }

    /**
     * Run all semantic-layer startup checks and populate the in-memory store.
     * Degrades gracefully when {@code forge.guardrail.semantic.fail-open=true}.
     */
    public void initialize() {
        if (!props.getSemantic().isEnabled()) {
            log.debug("[GUARDRAIL] Semantic layer initializer skipped — semantic.enabled=false");
            return;
        }
        log.info("[GUARDRAIL] Initializing semantic validation layer (in-memory mode)...");

        if (!verifyEmbeddingProvider()) {
            return;
        }
        verifyRedis();

        List<SeedRow> rows = loadSeedRows();
        if (rows.isEmpty()) {
            fail("No seed phrases found in " + SEEDS_RESOURCE);
            return;
        }
        log.info("[GUARDRAIL] Loaded {} seed phrases from {}", rows.size(), SEEDS_RESOURCE);

        List<InMemorySemanticStore.SeedEntry> entries = embedRows(rows);
        semanticStore.initialize(entries);

        log.info("[GUARDRAIL] Semantic validation layer ready — {} patterns loaded in-memory",
                semanticStore.size());
    }

    // ── Seed loading ─────────────────────────────────────────────────────────

    private List<SeedRow> loadSeedRows() {
        List<SeedRow> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(SEEDS_RESOURCE).getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split(DELIMITER, 3);
                if (parts.length != 3) {
                    log.warn("[GUARDRAIL] Skipping malformed seed line {}: {}", lineNum, line);
                    continue;
                }
                SemanticCategory category = parseCategory(parts[0].trim(), lineNum);
                if (category == null) continue;
                rows.add(new SeedRow(category, parts[1].trim(), parts[2].trim()));
            }
        } catch (IOException ex) {
            fail("Failed to read seed file " + SEEDS_RESOURCE + ": " + ex.getMessage());
        }
        return rows;
    }

    private SemanticCategory parseCategory(String raw, int lineNum) {
        try {
            return SemanticCategory.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("[GUARDRAIL] Unknown category '{}' at seed line {} — skipping", raw, lineNum);
            return null;
        }
    }

    // ── Embedding ────────────────────────────────────────────────────────────

    private List<InMemorySemanticStore.SeedEntry> embedRows(List<SeedRow> rows) {
        List<InMemorySemanticStore.SeedEntry> entries = new ArrayList<>(rows.size());
        int failed = 0;
        for (SeedRow row : rows) {
            try {
                float[] embedding = EmbeddingNormalizationUtil.normalize(
                        embeddingProvider.embed(row.text()));
                entries.add(new InMemorySemanticStore.SeedEntry(
                        row.category(), row.description(), row.text(), embedding));
            } catch (Exception ex) {
                log.warn("[GUARDRAIL] Failed to embed seed '{}': {}", row.description(), ex.getMessage());
                failed++;
            }
        }
        if (failed > 0) {
            log.warn("[GUARDRAIL] {} seed phrase(s) could not be embedded and were skipped", failed);
        }
        return entries;
    }

    // ── Infrastructure checks ────────────────────────────────────────────────

    private boolean verifyEmbeddingProvider() {
        if (!embeddingProvider.isAvailable()) {
            fail("ONNX embedding provider is not available — check forge.guardrail.semantic.onnx.*");
            return false;
        }
        log.info("[GUARDRAIL] ONNX model loaded successfully");
        return true;
    }

    private void verifyRedis() {
        if (!props.getSemantic().getRedis().isEnabled()) {
            log.info("[GUARDRAIL] Redis embedding cache disabled — skipping connection check");
            return;
        }
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        if (redis == null) {
            log.warn("[GUARDRAIL] Redis enabled but StringRedisTemplate is not available");
            return;
        }
        try {
            Boolean ok = redis.execute((RedisCallback<Boolean>) connection -> {
                String pong = connection.ping();
                return pong != null && pong.equalsIgnoreCase("PONG");
            });
            if (Boolean.TRUE.equals(ok)) {
                log.info("[GUARDRAIL] Redis embedding cache connection is active");
            } else {
                log.warn("[GUARDRAIL] Redis ping returned unexpected response");
            }
        } catch (Exception ex) {
            log.warn("[GUARDRAIL] Redis connection check failed (non-fatal): {}", ex.getMessage());
        }
    }

    private void fail(String message) {
        if (props.getSemantic().isFailOpen()) {
            log.error("[GUARDRAIL] {} — continuing with degraded semantic validation (fail-open=true)",
                    message);
        } else {
            throw new IllegalStateException("[GUARDRAIL] " + message);
        }
    }

    private record SeedRow(SemanticCategory category, String description, String text) {}
}
