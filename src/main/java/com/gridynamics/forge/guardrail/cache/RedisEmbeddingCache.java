package com.gridynamics.forge.guardrail.cache;

import com.gridynamics.forge.guardrail.util.EmbeddingNormalizationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Redis L2 cache for generated embeddings.
 *
 * <p>Flow per request:
 * <pre>
 *   SHA-256(input text) → Redis key
 *     HIT  → decode Base64 → float[]   (skip ONNX inference)
 *     MISS → generate embedding → store Base64 in Redis with TTL
 * </pre>
 *
 * <p>Serialisation: a {@code float[384]} is stored as Base64-encoded raw IEEE-754
 * bytes (384 × 4 = 1536 bytes → ~2 KB Base64 string per entry).
 *
 * <p>This is an optional Spring bean. When Redis is absent the
 * {@link com.gridynamics.forge.guardrail.chain.SemanticValidator} falls through to
 * direct inference every time.
 */
public final class RedisEmbeddingCache {

    private static final Logger log = LoggerFactory.getLogger(RedisEmbeddingCache.class);

    private final StringRedisTemplate redis;
    private final Duration ttl;
    private final String keyPrefix;

    public RedisEmbeddingCache(StringRedisTemplate redis, Duration ttl, String keyPrefix) {
        this.redis = redis;
        this.ttl = ttl;
        this.keyPrefix = keyPrefix;
    }

    /**
     * Return the cached embedding for {@code text}, if present.
     *
     * <p>Embeddings are stored pre-normalized via {@link #put}, so no
     * second normalization is required on the read path.
     */
    public Optional<float[]> get(String text) {
        String key = buildKey(text);
        try {
            String encoded = redis.opsForValue().get(key);
            if (encoded == null) {
                return Optional.empty();
            }
            // Embedding was normalized before encoding — return decoded bytes directly.
            return Optional.of(decode(encoded));
        } catch (Exception ex) {
            log.debug("[GUARDRAIL] Redis cache read failed for key={}: {}", key, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Store {@code embedding} in Redis keyed by the SHA-256 of {@code text}.
     */
    public void put(String text, float[] embedding) {
        String key = buildKey(text);
        try {
            redis.opsForValue().set(key, encode(EmbeddingNormalizationUtil.normalize(embedding)), ttl);
        } catch (Exception ex) {
            log.debug("[GUARDRAIL] Redis cache write failed for key={}: {}", key, ex.getMessage());
        }
    }

    // ── Internal ────────────────────────────────────────────────────────────

    private String buildKey(String text) {
        return keyPrefix + sha256Hex(text);
    }

    private static String sha256Hex(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    /** Encode float[] as Base64(raw IEEE-754 bytes). */
    private static String encode(float[] embedding) {
        ByteBuffer buf = ByteBuffer.allocate(embedding.length * Float.BYTES);
        for (float f : embedding) {
            buf.putFloat(f);
        }
        return Base64.getEncoder().encodeToString(buf.array());
    }

    /** Inverse of {@link #encode}. */
    private static float[] decode(String encoded) {
        byte[] bytes = Base64.getDecoder().decode(encoded);
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        float[] result = new float[bytes.length / Float.BYTES];
        for (int i = 0; i < result.length; i++) {
            result[i] = buf.getFloat();
        }
        return result;
    }
}
