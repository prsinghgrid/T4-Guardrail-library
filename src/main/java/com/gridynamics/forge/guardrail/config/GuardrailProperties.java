package com.gridynamics.forge.guardrail.config;

import com.gridynamics.forge.guardrail.semantic.SemanticCategory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Externalised configuration under {@code forge.guardrail.*}.
 */
@ConfigurationProperties(prefix = "forge.guardrail")
public class GuardrailProperties {

    private int maxPromptLength = 8000;
    private double fallbackConfidenceThreshold = 0.7;
    /** When true, applies fail-closed defaults suitable for production deployments. */
    private boolean productionMode = false;

    private FuzzyProperties fuzzy = new FuzzyProperties();
    private PiiProperties pii = new PiiProperties();
    private BiasProperties bias = new BiasProperties();
    private InjectionProperties injection = new InjectionProperties();
    private ToxicityProperties toxicity = new ToxicityProperties();
    private OutputProperties output = new OutputProperties();
    private InputProperties inputSecrets = new InputProperties();
    private SemanticProperties semantic = new SemanticProperties();
    private ApiProperties api = new ApiProperties();

    public int getMaxPromptLength() { return maxPromptLength; }
    public void setMaxPromptLength(int v) { this.maxPromptLength = v; }

    public double getFallbackConfidenceThreshold() { return fallbackConfidenceThreshold; }
    public void setFallbackConfidenceThreshold(double v) { this.fallbackConfidenceThreshold = v; }

    public boolean isProductionMode() { return productionMode; }
    public void setProductionMode(boolean v) { this.productionMode = v; }

    public FuzzyProperties getFuzzy() { return fuzzy; }
    public void setFuzzy(FuzzyProperties v) { this.fuzzy = v; }

    public PiiProperties getPii() { return pii; }
    public void setPii(PiiProperties v) { this.pii = v; }

    public BiasProperties getBias() { return bias; }
    public void setBias(BiasProperties v) { this.bias = v; }

    public InjectionProperties getInjection() { return injection; }
    public void setInjection(InjectionProperties v) { this.injection = v; }

    public ToxicityProperties getToxicity() { return toxicity; }
    public void setToxicity(ToxicityProperties v) { this.toxicity = v; }

    public OutputProperties getOutput() { return output; }
    public void setOutput(OutputProperties v) { this.output = v; }

    public InputProperties getInputSecrets() { return inputSecrets; }
    public void setInputSecrets(InputProperties v) { this.inputSecrets = v; }

    public SemanticProperties getSemantic() { return semantic; }
    public void setSemantic(SemanticProperties v) { this.semantic = v; }

    public ApiProperties getApi() { return api; }
    public void setApi(ApiProperties v) { this.api = v; }

    // ── Nested ──

    /** Optional built-in REST endpoints for Postman / HTTP testing. */
    public static class ApiProperties {
        private boolean enabled = true;
        private String evaluatePath = "/api/guardrail/evaluate";
        private String processPath = "/api/guardrail/process";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public String getEvaluatePath() { return evaluatePath; }
        public void setEvaluatePath(String v) { this.evaluatePath = v; }
        public String getProcessPath() { return processPath; }
        public void setProcessPath(String v) { this.processPath = v; }
    }

    public static class InputProperties {
        private boolean enabled = true;
        private double confidenceThreshold = 0.90;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public double getConfidenceThreshold() { return confidenceThreshold; }
        public void setConfidenceThreshold(double v) { this.confidenceThreshold = v; }
    }

    public static class FuzzyProperties {
        private boolean enabled = true;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
    }

    public static class PiiProperties {
        private boolean enabled = true;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
    }

    public static class BiasProperties {
        private boolean enabled = true;
        /** Legacy plain-text terms — converted to word-boundary regex at runtime. */
        private List<String> extraTerms = List.of();
        /** Configurable regex overrides for discriminatory intent detection. */
        private List<String> extraPatterns = List.of();
        private BiasCategoryProperties categories = new BiasCategoryProperties();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public List<String> getExtraTerms() { return extraTerms; }
        public void setExtraTerms(List<String> v) { this.extraTerms = v; }
        public List<String> getExtraPatterns() { return extraPatterns; }
        public void setExtraPatterns(List<String> v) { this.extraPatterns = v; }
        public BiasCategoryProperties getCategories() { return categories; }
        public void setCategories(BiasCategoryProperties v) { this.categories = v; }
    }

    public static class BiasCategoryProperties {
        private boolean gender = true;
        private boolean religion = true;
        private boolean caste = true;
        private boolean nationality = true;
        private boolean disability = true;
        private boolean sexuality = true;
        private boolean age = true;
        private boolean education = true;

        public boolean getGender() { return gender; }
        public void setGender(boolean v) { this.gender = v; }
        public boolean getReligion() { return religion; }
        public void setReligion(boolean v) { this.religion = v; }
        public boolean getCaste() { return caste; }
        public void setCaste(boolean v) { this.caste = v; }
        public boolean getNationality() { return nationality; }
        public void setNationality(boolean v) { this.nationality = v; }
        public boolean getDisability() { return disability; }
        public void setDisability(boolean v) { this.disability = v; }
        public boolean getSexuality() { return sexuality; }
        public void setSexuality(boolean v) { this.sexuality = v; }
        public boolean getAge() { return age; }
        public void setAge(boolean v) { this.age = v; }
        public boolean getEducation() { return education; }
        public void setEducation(boolean v) { this.education = v; }
    }

    public static class InjectionProperties {
        private boolean enabled = true;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
    }

    public static class ToxicityProperties {
        private boolean enabled = true;
        /** Legacy flag — when true, always HARD block regardless of score thresholds. */
        private boolean hardBlock = false;
        private double hardBlockThreshold = 0.75;
        private double warnThreshold = 0.4;
        private List<String> extraPatterns = List.of();
        private ToxicityCategoryProperties categories = new ToxicityCategoryProperties();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public boolean isHardBlock() { return hardBlock; }
        public void setHardBlock(boolean v) { this.hardBlock = v; }
        public double getHardBlockThreshold() { return hardBlockThreshold; }
        public void setHardBlockThreshold(double v) { this.hardBlockThreshold = v; }
        public double getWarnThreshold() { return warnThreshold; }
        public void setWarnThreshold(double v) { this.warnThreshold = v; }
        public List<String> getExtraPatterns() { return extraPatterns; }
        public void setExtraPatterns(List<String> v) { this.extraPatterns = v; }
        public ToxicityCategoryProperties getCategories() { return categories; }
        public void setCategories(ToxicityCategoryProperties v) { this.categories = v; }
    }

    public static class ToxicityCategoryProperties {
        private boolean violence = true;
        private boolean hateSpeech = true;
        private boolean selfHarm = true;
        private boolean cyberAbuse = true;
        private boolean illegalActivities = true;

        public boolean getViolence() { return violence; }
        public void setViolence(boolean v) { this.violence = v; }
        public boolean getHateSpeech() { return hateSpeech; }
        public void setHateSpeech(boolean v) { this.hateSpeech = v; }
        public boolean getSelfHarm() { return selfHarm; }
        public void setSelfHarm(boolean v) { this.selfHarm = v; }
        public boolean getCyberAbuse() { return cyberAbuse; }
        public void setCyberAbuse(boolean v) { this.cyberAbuse = v; }
        public boolean getIllegalActivities() { return illegalActivities; }
        public void setIllegalActivities(boolean v) { this.illegalActivities = v; }
    }

    public static class OutputProperties {
        private boolean enabled = true;
        private boolean checkPiiLeakage = true;
        private boolean checkSystemLeak = true;
        private boolean checkSecrets = true;
        private double secretConfidenceThreshold = 0.90;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public boolean isCheckPiiLeakage() { return checkPiiLeakage; }
        public void setCheckPiiLeakage(boolean v) { this.checkPiiLeakage = v; }
        public boolean isCheckSystemLeak() { return checkSystemLeak; }
        public void setCheckSystemLeak(boolean v) { this.checkSystemLeak = v; }
        public boolean isCheckSecrets() { return checkSecrets; }
        public void setCheckSecrets(boolean v) { this.checkSecrets = v; }
        public double getSecretConfidenceThreshold() { return secretConfidenceThreshold; }
        public void setSecretConfidenceThreshold(double v) { this.secretConfidenceThreshold = v; }
    }

    // ── Semantic layer ────────────────────────────────────────────────────

    /**
     * Top-level switch for the semantic validation layer.
     * Disabled by default — enable by setting {@code forge.guardrail.semantic.enabled=true}
     * and providing an ONNX model, pgvector table, and (optionally) Redis.
     */
    public static class SemanticProperties {
        /** Master switch. Semantic validation is opt-in. */
        private boolean enabled = false;
        /**
         * When {@code true} (default), any ONNX/DB failure is logged and the
         * validator returns no violations instead of throwing.
         * Set {@code false} in production if semantic coverage is mandatory.
         */
        private boolean failOpen = true;
        /**
         * Default cosine similarity cutoff when a category-specific threshold is unset.
         * Category thresholds below override this per violation type.
         */
        private double similarityThreshold = 0.65;
        /** Maximum pgvector nearest-neighbour candidates per request (filtered by category threshold). */
        private int topK = 10;
        /**
         * When {@code false} (default), PII category matches from the semantic store are
         * suppressed — PII is already fully covered by regex in {@code PiiSanitizingValidator}.
         * Enable only if you have seeded PII-specific obfuscation patterns that regex cannot catch.
         */
        private boolean piiEnabled = false;
        private CategoryThresholdProperties categoryThresholds = new CategoryThresholdProperties();

        private OnnxProperties onnx = new OnnxProperties();
        private PgVectorProperties pgvector = new PgVectorProperties();
        private RedisCacheProperties redis = new RedisCacheProperties();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public boolean isFailOpen() { return failOpen; }
        public void setFailOpen(boolean v) { this.failOpen = v; }
        public double getSimilarityThreshold() { return similarityThreshold; }
        public void setSimilarityThreshold(double v) { this.similarityThreshold = v; }
        public int getTopK() { return topK; }
        public void setTopK(int v) { this.topK = v; }
        public boolean isPiiEnabled() { return piiEnabled; }
        public void setPiiEnabled(boolean v) { this.piiEnabled = v; }
        public CategoryThresholdProperties getCategoryThresholds() { return categoryThresholds; }
        public void setCategoryThresholds(CategoryThresholdProperties v) { this.categoryThresholds = v; }
        public OnnxProperties getOnnx() { return onnx; }
        public void setOnnx(OnnxProperties v) { this.onnx = v; }
        public PgVectorProperties getPgvector() { return pgvector; }
        public void setPgvector(PgVectorProperties v) { this.pgvector = v; }
        public RedisCacheProperties getRedis() { return redis; }
        public void setRedis(RedisCacheProperties v) { this.redis = v; }

        public double thresholdFor(SemanticCategory category) {
            var t = categoryThresholds;
            return switch (category) {
                case BIAS             -> t.getBias();
                case TOXICITY         -> t.getToxicity();
                case PROMPT_INJECTION -> t.getPromptInjection();
                case JAILBREAK        -> t.getJailbreak();
            };
        }
    }

    /**
     * Per-category cosine similarity cutoffs.
     * Bias uses a lower threshold; toxicity uses a higher one to reduce false positives.
     */
    public static class CategoryThresholdProperties {
        private double bias = 0.65;
        private double toxicity = 0.78;
        private double promptInjection = 0.70;
        private double jailbreak = 0.70;

        public double getBias() { return bias; }
        public void setBias(double v) { this.bias = v; }
        public double getToxicity() { return toxicity; }
        public void setToxicity(double v) { this.toxicity = v; }
        public double getPromptInjection() { return promptInjection; }
        public void setPromptInjection(double v) { this.promptInjection = v; }
        public double getJailbreak() { return jailbreak; }
        public void setJailbreak(double v) { this.jailbreak = v; }
    }

    /** ONNX Runtime + bge-small-en model configuration. */
    public static class OnnxProperties {
        /** Absolute path to the {@code .onnx} model file. */
        private String modelPath;
        /** Absolute path to the HuggingFace {@code tokenizer.json} file. */
        private String tokenizerPath;
        /** Input sequence truncation limit (tokens). 128 is sufficient for most prompts. */
        private int maxTokens = 128;
        /** ONNX Runtime intra-op thread count. Keep at 1–2 to avoid CPU contention. */
        private int intraOpThreads = 1;

        public String getModelPath() { return modelPath; }
        public void setModelPath(String v) { this.modelPath = v; }
        public String getTokenizerPath() { return tokenizerPath; }
        public void setTokenizerPath(String v) { this.tokenizerPath = v; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int v) { this.maxTokens = v; }
        public int getIntraOpThreads() { return intraOpThreads; }
        public void setIntraOpThreads(int v) { this.intraOpThreads = v; }
    }

    /** pgvector table settings. */
    public static class PgVectorProperties {
        /** Name of the pgvector table holding unsafe semantic patterns. */
        private String tableName = "guardrail_semantic_patterns";

        public String getTableName() { return tableName; }
        public void setTableName(String v) { this.tableName = v; }
    }

    /** Redis embedding cache settings. */
    public static class RedisCacheProperties {
        /** Whether the Redis embedding cache is active. */
        private boolean enabled = true;
        /** TTL for cached embeddings (seconds). Default 1 hour. */
        private long ttlSeconds = 3600;
        /** Redis key prefix. Change if you share a Redis instance across services. */
        private String keyPrefix = "guardrail:emb:";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public long getTtlSeconds() { return ttlSeconds; }
        public void setTtlSeconds(long v) { this.ttlSeconds = v; }
        public String getKeyPrefix() { return keyPrefix; }
        public void setKeyPrefix(String v) { this.keyPrefix = v; }
    }

}
