package com.gridynamics.forge.guardrail.config;

import com.gridynamics.forge.guardrail.registry.GuardrailPatternRegistry;
import com.gridynamics.forge.guardrail.registry.PatternCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Validates guardrail configuration and pattern overrides at application startup.
 * Fails fast before the pipeline accepts traffic.
 */
public final class GuardrailStartupValidator implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(GuardrailStartupValidator.class);

    private final GuardrailProperties props;
    private final GuardrailPatternRegistry patternRegistry;
    private final Environment environment;

    public GuardrailStartupValidator(GuardrailProperties props,
                                      GuardrailPatternRegistry patternRegistry,
                                      Environment environment) {
        this.props = props;
        this.patternRegistry = patternRegistry;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        applyProductionModeDefaults();
        logSemanticPropertyBinding();
        validate();
        log.info("[GUARDRAIL] Startup validation passed (production-mode={})",
                props.isProductionMode());
    }

    private void logSemanticPropertyBinding() {
        String envValue = environment.getProperty("forge.guardrail.semantic.enabled");
        boolean bound = props.getSemantic().isEnabled();
        log.info("[GUARDRAIL] forge.guardrail.semantic.enabled — env={}, bound={}",
                envValue != null ? envValue : "<unset>", bound);
        if (envValue != null && !envValue.equalsIgnoreCase(String.valueOf(bound))) {
            log.warn("[GUARDRAIL] semantic.enabled env ({}) differs from bound GuardrailProperties ({}) — "
                    + "check profile order and spring.config.import precedence", envValue, bound);
        }
    }

    private void applyProductionModeDefaults() {
        if (!props.isProductionMode()) {
            return;
        }
        log.info("[GUARDRAIL] Applying production-mode defaults");
    }

    public void validate() {
        List<String> errors = new ArrayList<>();

        if (props.getMaxPromptLength() <= 0) {
            errors.add("max-prompt-length must be > 0");
        }
        validateThreshold(errors, "fallback-confidence-threshold", props.getFallbackConfidenceThreshold());

        validateThreshold(errors, "toxicity.hard-block-threshold", props.getToxicity().getHardBlockThreshold());
        validateThreshold(errors, "toxicity.warn-threshold", props.getToxicity().getWarnThreshold());
        if (props.getToxicity().getWarnThreshold() > props.getToxicity().getHardBlockThreshold()) {
            errors.add("toxicity.warn-threshold must be <= toxicity.hard-block-threshold");
        }

        validateRegexList(errors, "bias.extra-patterns", props.getBias().getExtraPatterns());
        validateRegexList(errors, "toxicity.extra-patterns", props.getToxicity().getExtraPatterns());

        validateSemanticConfig(errors, props);

        for (PatternCategory category : PatternCategory.values()) {
            patternRegistry.getPatterns(category);
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException("Invalid forge.guardrail configuration: " + errors);
        }
    }

    private static void validateThreshold(List<String> errors, String name, double value) {
        if (value < 0.0 || value > 1.0) {
            errors.add(name + " must be between 0.0 and 1.0 (was " + value + ")");
        }
    }

    private static void validateSemanticConfig(List<String> errors,
                                                GuardrailProperties props) {
        var semantic = props.getSemantic();
        if (!semantic.isEnabled()) {
            return;
        }
        validateThreshold(errors, "semantic.similarity-threshold", semantic.getSimilarityThreshold());
        if (semantic.getTopK() <= 0) {
            errors.add("semantic.top-k must be > 0");
        }
        var categoryThresholds = semantic.getCategoryThresholds();
        validateThreshold(errors, "semantic.category-thresholds.bias", categoryThresholds.getBias());
        validateThreshold(errors, "semantic.category-thresholds.toxicity", categoryThresholds.getToxicity());
        validateThreshold(errors, "semantic.category-thresholds.prompt-injection",
                categoryThresholds.getPromptInjection());
        validateThreshold(errors, "semantic.category-thresholds.jailbreak", categoryThresholds.getJailbreak());

        var onnx = semantic.getOnnx();
        validateFilePath(errors, "semantic.onnx.model-path", onnx.getModelPath());
        validateFilePath(errors, "semantic.onnx.tokenizer-path", onnx.getTokenizerPath());
        if (onnx.getMaxTokens() <= 0) {
            errors.add("semantic.onnx.max-tokens must be > 0");
        }
        if (onnx.getIntraOpThreads() <= 0) {
            errors.add("semantic.onnx.intra-op-threads must be > 0");
        }
    }

    private static void validateFilePath(List<String> errors, String name, String path) {
        if (path == null || path.isBlank()) {
            errors.add(name + " must be set when semantic validation is enabled");
            return;
        }
        if (!Files.isReadable(Path.of(path))) {
            errors.add(name + " does not exist or is not readable: " + path);
        }
    }

    private static void validateRegexList(List<String> errors, String name, List<String> patterns) {
        if (patterns == null) {
            return;
        }
        for (String pattern : patterns) {
            if (pattern == null || pattern.isBlank()) {
                continue;
            }
            try {
                Pattern.compile(pattern);
            } catch (PatternSyntaxException ex) {
                errors.add(name + " contains invalid regex '" + pattern + "': " + ex.getMessage());
            }
        }
    }
}
