package com.gridynamics.forge.guardrail.chain;

import com.gridynamics.forge.guardrail.GuardrailContext;
import com.gridynamics.forge.guardrail.cache.RedisEmbeddingCache;
import com.gridynamics.forge.guardrail.config.GuardrailProperties;
import com.gridynamics.forge.guardrail.embedding.EmbeddingProvider;
import com.gridynamics.forge.guardrail.model.ValidationAction;
import com.gridynamics.forge.guardrail.model.Violation;
import com.gridynamics.forge.guardrail.model.ViolationSeverity;
import com.gridynamics.forge.guardrail.semantic.SemanticCategory;
import com.gridynamics.forge.guardrail.semantic.SemanticMatch;
import com.gridynamics.forge.guardrail.semantic.SemanticStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/** Semantic validation at pipeline order 400 (after regex validators and PII sanitisation). */
public final class SemanticValidator implements GuardrailValidator {

    private static final Logger log = LoggerFactory.getLogger(SemanticValidator.class);
    private static final int LOG_TEXT_MAX = 120;

    private final GuardrailProperties props;
    private final EmbeddingProvider embeddingProvider;
    private final SemanticStore semanticStore;
    private final RedisEmbeddingCache redisCache;

    public SemanticValidator(GuardrailProperties props,
                              EmbeddingProvider embeddingProvider,
                              SemanticStore semanticStore,
                              RedisEmbeddingCache redisCache) {
        this.props = props;
        this.embeddingProvider = embeddingProvider;
        this.semanticStore = semanticStore;
        this.redisCache = redisCache;
    }

    @Override
    public String name() {
        return "SEMANTIC";
    }

    @Override
    public int order() {
        return 400;
    }

    @Override
    public boolean isEnabled() {
        return props.getSemantic().isEnabled();
    }

    @Override
    public List<Violation> validate(String text, GuardrailContext context) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        if (!embeddingProvider.isAvailable()) {
            log.warn("[GUARDRAIL] SemanticValidator skipped — embedding provider unavailable");
            return List.of();
        }

        try {
            var semantic = props.getSemantic();
            List<SemanticMatch> candidates = semanticStore.findNearest(
                    resolveEmbedding(text), semantic.getTopK());
            List<Violation> violations = new ArrayList<>();

            if (log.isDebugEnabled()) {
                StringBuilder scoreLog = new StringBuilder("[SemanticValidator] topK_scores=[");
                for (int i = 0; i < candidates.size(); i++) {
                    SemanticMatch m = candidates.get(i);
                    double th = semantic.thresholdFor(m.category());
                    scoreLog.append("{sim=").append(fmt(m.similarity()))
                            .append(",threshold=").append(fmt(th))
                            .append(",cat=").append(m.category())
                            .append(",blocked=").append(m.similarity() >= th).append("}");
                    if (i < candidates.size() - 1) scoreLog.append(",");
                }
                scoreLog.append("] input_snippet=")
                        .append(text.length() > 80 ? text.substring(0, 80) + "..." : text);
                log.debug("{}", scoreLog);
            }

            for (SemanticMatch match : candidates) {
                double threshold = semantic.thresholdFor(match.category());
                if (match.similarity() >= threshold) {
                    log.info("[SemanticValidator] BLOCK similarity={} threshold={} category={} pattern=\"{}\"",
                            fmt(match.similarity()), fmt(threshold), match.category(),
                            truncate(match.patternText()));
                    violations.add(toViolation(match, threshold));
                }
            }

            if (violations.isEmpty() && !candidates.isEmpty()) {
                SemanticMatch top = candidates.get(0);
                log.debug("[SemanticValidator] no block — top similarity={} threshold={} category={}",
                        fmt(top.similarity()), fmt(semantic.thresholdFor(top.category())), top.category());
            }

            return violations;

        } catch (Exception ex) {
            log.debug("[SemanticValidator] exception during validation — type={} message={} fail_open={}",
                    ex.getClass().getSimpleName(), ex.getMessage(), props.getSemantic().isFailOpen());
            if (props.getSemantic().isFailOpen()) {
                log.warn("[GUARDRAIL] Semantic validation failed (fail-open): {}", ex.getMessage());
                return List.of();
            }
            throw new RuntimeException("Semantic validation failed", ex);
        }
    }

    private float[] resolveEmbedding(String text) {
        if (redisCache != null) {
            var cached = redisCache.get(text);
            if (cached.isPresent()) {
                return cached.get();
            }
            float[] embedding = embeddingProvider.embed(text);
            redisCache.put(text, embedding);
            return embedding;
        }
        return embeddingProvider.embed(text);
    }

    private static Violation toViolation(SemanticMatch match, double threshold) {
        String message = "Semantic %s: similarity %.3f >= threshold %.3f, matched \"%s\" (%s)"
                .formatted(match.category(), match.similarity(), threshold,
                        match.patternText(), match.description());

        return Violation.of(
                semanticCode(match.category()),
                message,
                ViolationSeverity.HARD,
                "SEMANTIC",
                List.of(match.patternText()),
                match.similarity(),
                match.similarity(),
                match.category().name().toLowerCase(),
                ValidationAction.BLOCK
        );
    }

    private static String semanticCode(SemanticCategory category) {
        return switch (category) {
            case BIAS             -> "SEMANTIC_BIAS";
            case TOXICITY         -> "SEMANTIC_TOXICITY";
            case PROMPT_INJECTION -> "SEMANTIC_INJECTION";
            case JAILBREAK        -> "SEMANTIC_JAILBREAK";
        };
    }

    private static String fmt(double value) {
        return String.format("%.3f", value);
    }

    private static String truncate(String text) {
        return text.length() <= LOG_TEXT_MAX ? text : text.substring(0, LOG_TEXT_MAX) + "...";
    }
}
