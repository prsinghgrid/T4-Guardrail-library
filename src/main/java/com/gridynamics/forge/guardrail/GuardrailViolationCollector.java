package com.gridynamics.forge.guardrail;

import com.gridynamics.forge.guardrail.model.Violation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mutable violation accumulator shared across the validator chain for a single request.
 */
public final class GuardrailViolationCollector {

    private final List<Violation> violations = new ArrayList<>();

    public void addAll(List<Violation> found) {
        if (found == null || found.isEmpty()) {
            return;
        }
        violations.addAll(found);
    }

    public List<Violation> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(violations));
    }

    public void clear() {
        violations.clear();
    }

    public boolean isEmpty() {
        return violations.isEmpty();
    }
}
