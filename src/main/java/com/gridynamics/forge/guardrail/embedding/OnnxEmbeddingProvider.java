package com.gridynamics.forge.guardrail.embedding;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.gridynamics.forge.guardrail.util.EmbeddingNormalizationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.LongBuffer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fully local embedding provider using ONNX Runtime Java + bge-small-en.
 *
 * <p>Architecture:
 * <pre>
 *   PII-sanitised text
 *     → HuggingFace tokenizer (Java/Rust binding)
 *     → ONNX session (bge-small-en, 384-dim)
 *     → mean pooling + L2 normalisation
 *     → float[384]
 * </pre>
 *
 * <p>The ONNX session is created once at startup and reused across all threads
 * (ONNX Runtime sessions are thread-safe for inference).
 */
public final class OnnxEmbeddingProvider implements EmbeddingProvider, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(OnnxEmbeddingProvider.class);

    private static final int DIMENSIONS = 384;

    private final OrtEnvironment env;
    private final OrtSession session;
    private final HuggingFaceTokenizer tokenizer;
    private final int maxTokens;
    private volatile boolean available = true;
    private final AtomicBoolean firstEmbedLogged = new AtomicBoolean(false);

    public OnnxEmbeddingProvider(String modelPath, String tokenizerPath,
                                  int maxTokens, int intraOpThreads) throws OrtException, IOException {
        this.maxTokens = maxTokens;
        this.env = OrtEnvironment.getEnvironment();

        var opts = new OrtSession.SessionOptions();
        opts.setIntraOpNumThreads(intraOpThreads);
        opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT);

        this.session = env.createSession(modelPath, opts);
        this.tokenizer = HuggingFaceTokenizer.newInstance(Path.of(tokenizerPath));

        log.info("[GUARDRAIL] ONNX model loaded successfully");
        log.info("[GUARDRAIL] Tokenizer loaded successfully — model={} dims={}", modelPath, DIMENSIONS);

        if (log.isDebugEnabled()) {
            try {
                var outputNames = session.getOutputNames();
                log.debug("[GUARDRAIL] ONNX model outputs — count={} names={}", outputNames.size(), outputNames);
            } catch (Exception ex) {
                log.debug("[GUARDRAIL] Could not read ONNX output names: {}", ex.getMessage());
            }
        }
    }

    @Override
    public float[] embed(String text) {
        try {
            // DJL HuggingFaceTokenizer.encode(String) — truncation is handled manually below
            Encoding encoding = tokenizer.encode(text);

            long[] inputIds = encoding.getIds();
            long[] attentionMask = encoding.getAttentionMask();
            long[] tokenTypeIds = encoding.getTypeIds();

            // Truncate to configured max tokens (safety guard)
            if (inputIds.length > maxTokens) {
                inputIds = truncate(inputIds, maxTokens);
                attentionMask = truncate(attentionMask, maxTokens);
                tokenTypeIds = truncate(tokenTypeIds, maxTokens);
            }

            long[] shape = {1, inputIds.length};

            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("input_ids",
                    OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), shape));
            inputs.put("attention_mask",
                    OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMask), shape));
            inputs.put("token_type_ids",
                    OnnxTensor.createTensor(env, LongBuffer.wrap(tokenTypeIds), shape));

            try (OrtSession.Result result = session.run(inputs)) {
                Object rawOutput = result.get(0).getValue();

                // Log the output shape once at DEBUG level on first inference.
                if (log.isDebugEnabled() && firstEmbedLogged.compareAndSet(false, true)) {
                    String outputType = rawOutput == null ? "null" : rawOutput.getClass().getSimpleName();
                    int[] dims = rawOutput instanceof float[][][] lhs
                            ? new int[]{lhs.length, lhs[0].length, lhs[0][0].length}
                            : rawOutput instanceof float[][] lhs2
                            ? new int[]{lhs2.length, lhs2[0].length}
                            : new int[]{-1};
                    log.debug("[GUARDRAIL] First ONNX inference — output_type={} dims={} token_count={} truncated={}",
                            outputType, Arrays.toString(dims), inputIds.length, inputIds.length == maxTokens);
                }

                float[][][] lastHiddenState = (float[][][]) rawOutput;
                float[] pooled = meanPool(lastHiddenState[0], attentionMask);
                return EmbeddingNormalizationUtil.normalize(pooled);
            } finally {
                inputs.values().forEach(t -> {
                    try { t.close(); } catch (Exception ignored) {}
                });
            }
        } catch (OrtException ex) {
            log.error("[GUARDRAIL] ONNX inference failed", ex);
            throw new EmbeddingException("ONNX inference failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    @Override
    public String name() {
        return "bge-small-en-onnx";
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public void close() {
        available = false;
        try { session.close(); } catch (Exception ignored) {}
    }

    // ── Internal ────────────────────────────────────────────────────────────

    /**
     * Mean pooling: average token embeddings weighted by the attention mask,
     * so padding tokens are excluded from the pooled representation.
     */
    private static float[] meanPool(float[][] hiddenState, long[] attentionMask) {
        int dims = hiddenState[0].length;
        float[] pooled = new float[dims];
        int count = 0;
        for (int i = 0; i < hiddenState.length; i++) {
            if (attentionMask[i] == 1) {
                for (int j = 0; j < dims; j++) {
                    pooled[j] += hiddenState[i][j];
                }
                count++;
            }
        }
        if (count > 0) {
            for (int j = 0; j < dims; j++) {
                pooled[j] /= count;
            }
        }
        return pooled;
    }

    private static long[] truncate(long[] src, int maxLen) {
        long[] out = new long[maxLen];
        System.arraycopy(src, 0, out, 0, maxLen);
        return out;
    }

    public static final class EmbeddingException extends RuntimeException {
        public EmbeddingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
