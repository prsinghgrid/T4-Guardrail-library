package com.gridynamics.forge.guardrail.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gridynamics.forge.guardrail.GuardrailContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Normalises inbound text so validators always receive the user prompt, not a raw JSON envelope.
 */
public final class PromptInputResolver {

    private static final Logger log = LoggerFactory.getLogger(PromptInputResolver.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PromptInputResolver() {}

    public record Resolved(String prompt, GuardrailContext context) {}

    /**
     * If {@code input} is a JSON object containing {@code prompt}, extracts the prompt and
     * optionally rebuilds context from {@code featureType} / {@code teamId} fields.
     */
    public static Resolved resolve(String input, GuardrailContext context) {
        if (input == null) {
            return new Resolved("", context);
        }
        String trimmed = input.strip();
        if (!looksLikeJsonRequest(trimmed)) {
            return new Resolved(trimmed, context);
        }

        try {
            JsonNode root = MAPPER.readTree(trimmed);
            if (!root.isObject()) {
                return new Resolved(trimmed, context);
            }

            String prompt = textField(root, "prompt");
            if (prompt == null || prompt.isBlank()) {
                return new Resolved(trimmed, context);
            }

            GuardrailContext resolvedContext = contextFromJson(root, context);
            log.debug("[GUARDRAIL] Extracted prompt from JSON envelope (length={})", prompt.length());
            return new Resolved(prompt.strip(), resolvedContext);

        } catch (Exception ex) {
            log.debug("[GUARDRAIL] JSON prompt extraction skipped: {}", ex.getMessage());
            return new Resolved(trimmed, context);
        }
    }

    private static boolean looksLikeJsonRequest(String text) {
        return text.startsWith("{") && text.contains("\"prompt\"");
    }

    private static GuardrailContext contextFromJson(JsonNode root, GuardrailContext fallback) {
        String featureType = textField(root, "featureType");
        String teamId = textField(root, "teamId");
        if (featureType != null && !featureType.isBlank()
                && teamId != null && !teamId.isBlank()) {
            return GuardrailContext.of(featureType.strip(), teamId.strip());
        }
        return fallback;
    }

    private static String textField(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText();
    }
}
