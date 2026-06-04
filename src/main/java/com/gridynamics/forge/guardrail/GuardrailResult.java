package com.gridynamics.forge.guardrail;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gridynamics.forge.guardrail.model.Violation;
import com.gridynamics.forge.guardrail.model.ViolationSeverity;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable result of a guardrail pipeline run.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class GuardrailResult {

    @JsonProperty private final String sanitisedPrompt;
    @JsonProperty private final boolean allowed;
    @JsonProperty private final boolean fallbackRequired;
    @JsonProperty private final boolean piiStripped;
    @JsonProperty private final boolean biasBlocked;
    @JsonProperty private final boolean injectionBlocked;
    @JsonProperty private final boolean toxicityFlagged;
    @JsonProperty private final boolean semanticEnabled;
    @JsonProperty private final boolean semanticActive;
    @JsonProperty private final boolean truncated;
    @JsonProperty private final int originalLength;
    @JsonProperty private final int sanitisedLength;
    @JsonProperty private final int estimatedTokens;
    @JsonProperty private final List<Violation> violations;
    @JsonProperty private final Duration processingTime;
    @JsonProperty private final Integer riskScore;

    private GuardrailResult(Builder b) {
        this.sanitisedPrompt   = b.sanitisedPrompt;
        this.allowed           = b.allowed;
        this.fallbackRequired  = b.fallbackRequired;
        this.piiStripped       = b.piiStripped;
        this.biasBlocked       = b.biasBlocked;
        this.injectionBlocked  = b.injectionBlocked;
        this.toxicityFlagged   = b.toxicityFlagged;
        this.semanticEnabled   = b.semanticEnabled;
        this.semanticActive    = b.semanticActive;
        this.truncated         = b.truncated;
        this.originalLength    = b.originalLength;
        this.sanitisedLength   = b.sanitisedLength;
        this.estimatedTokens   = b.estimatedTokens;
        this.violations        = Collections.unmodifiableList(new ArrayList<>(b.violations));
        this.processingTime    = b.processingTime;
        this.riskScore         = b.riskScore;
    }

    public String getSanitisedPrompt()   { return sanitisedPrompt; }
    public boolean isAllowed()           { return allowed; }
    public boolean isFallbackRequired()  { return fallbackRequired; }
    public boolean isPiiStripped()       { return piiStripped; }
    public boolean isBiasBlocked()       { return biasBlocked; }
    public boolean isInjectionBlocked()  { return injectionBlocked; }
    public boolean isToxicityFlagged()   { return toxicityFlagged; }
    public boolean isSemanticEnabled()   { return semanticEnabled; }
    public boolean isSemanticActive()    { return semanticActive; }
    public boolean isTruncated()         { return truncated; }
    public int getOriginalLength()       { return originalLength; }
    public int getSanitisedLength()      { return sanitisedLength; }
    public int getEstimatedTokens()      { return estimatedTokens; }
    public List<Violation> getViolations(){ return violations; }
    public Duration getProcessingTime()  { return processingTime; }
    public Integer getRiskScore()        { return riskScore; }

    public boolean hasViolations() {
        return violations != null && !violations.isEmpty();
    }

    public boolean hasHardViolations() {
        return violations.stream().anyMatch(v -> v.severity() == ViolationSeverity.HARD);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String sanitisedPrompt = "";
        private boolean allowed = true;
        private boolean fallbackRequired;
        private boolean piiStripped;
        private boolean biasBlocked;
        private boolean injectionBlocked;
        private boolean toxicityFlagged;
        private boolean semanticEnabled;
        private boolean semanticActive;
        private boolean truncated;
        private int originalLength;
        private int sanitisedLength;
        private int estimatedTokens;
        private List<Violation> violations = new ArrayList<>();
        private Duration processingTime;
        private Integer riskScore;

        public Builder sanitisedPrompt(String v)   { this.sanitisedPrompt = v; return this; }
        public Builder allowed(boolean v)           { this.allowed = v; return this; }
        public Builder fallbackRequired(boolean v)  { this.fallbackRequired = v; return this; }
        public Builder piiStripped(boolean v)       { this.piiStripped = v; return this; }
        public Builder biasBlocked(boolean v)       { this.biasBlocked = v; return this; }
        public Builder injectionBlocked(boolean v)  { this.injectionBlocked = v; return this; }
        public Builder toxicityFlagged(boolean v)   { this.toxicityFlagged = v; return this; }
        public Builder semanticEnabled(boolean v)   { this.semanticEnabled = v; return this; }
        public Builder semanticActive(boolean v)    { this.semanticActive = v; return this; }
        public Builder truncated(boolean v)         { this.truncated = v; return this; }
        public Builder originalLength(int v)        { this.originalLength = v; return this; }
        public Builder sanitisedLength(int v)       { this.sanitisedLength = v; return this; }
        public Builder estimatedTokens(int v)       { this.estimatedTokens = v; return this; }
        public Builder violations(List<Violation> v){ this.violations = new ArrayList<>(v); return this; }
        public Builder addViolation(Violation v)    { this.violations.add(v); return this; }
        public Builder processingTime(Duration v)   { this.processingTime = v; return this; }
        public Builder riskScore(Integer v)       { this.riskScore = v; return this; }

        public GuardrailResult build() { return new GuardrailResult(this); }
    }

    @Override
    public String toString() {
        return "GuardrailResult{allowed=" + allowed
            + ", piiStripped=" + piiStripped
            + ", biasBlocked=" + biasBlocked
            + ", injectionBlocked=" + injectionBlocked
            + ", violations=" + violations.size()
            + '}';
    }
}
