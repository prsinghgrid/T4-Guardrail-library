package com.gridynamics.forge.guardrail.web;

import com.gridynamics.forge.guardrail.GuardrailContext;
import com.gridynamics.forge.guardrail.GuardrailEngine;
import com.gridynamics.forge.guardrail.GuardrailResult;
import com.gridynamics.forge.guardrail.exception.GuardrailViolationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Optional REST endpoint for evaluating prompts (Postman-friendly).
 * Disable with {@code forge.guardrail.api.enabled=false} when you provide your own controller.
 */
@RestController
@ConditionalOnWebApplication
@ConditionalOnBean(GuardrailEngine.class)
@ConditionalOnProperty(prefix = "forge.guardrail.api", name = "enabled", matchIfMissing = true)
public class GuardrailController {

    private final GuardrailEngine guardrailEngine;

    public GuardrailController(GuardrailEngine guardrailEngine) {
        this.guardrailEngine = guardrailEngine;
    }

    @PostMapping("${forge.guardrail.api.evaluate-path:/api/guardrail/evaluate}")
    public ResponseEntity<GuardrailResult> evaluate(@RequestBody GuardrailEvaluateRequest request) {
        var ctx = GuardrailContext.of(request.featureType(), request.teamId());
        GuardrailResult result = guardrailEngine.evaluate(request.prompt(), ctx);
        return ResponseEntity.ok(result);
    }

    /**
     * Same as evaluate but throws {@link GuardrailViolationException} → HTTP 422 via
     * {@link GuardrailExceptionHandler} when blocked.
     */
    @PostMapping("${forge.guardrail.api.process-path:/api/guardrail/process}")
    public ResponseEntity<GuardrailResult> process(@RequestBody GuardrailEvaluateRequest request) {
        var ctx = GuardrailContext.of(request.featureType(), request.teamId());
        GuardrailResult result = guardrailEngine.process(request.prompt(), ctx);
        return ResponseEntity.ok(result);
    }
}
