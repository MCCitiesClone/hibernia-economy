package io.paradaux.treasuryrestapi.ratelimit;

import io.swagger.v3.oas.models.Operation;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

/**
 * Reads {@link RateLimit} off each controller method at OpenAPI-generation
 * time and appends a "Rate limits" section to the operation's description.
 * Single source of truth: the annotation itself — change the numbers there
 * and the published API docs follow automatically.
 */
@Component
public class RateLimitOperationCustomizer implements OperationCustomizer {

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        RateLimit ann = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (ann == null) return operation;

        String existing = operation.getDescription() == null ? "" : operation.getDescription();
        String rateLimitDoc = String.format(
                "%s\n\n**Rate limits:** %d requests/minute for PERSONAL keys, "
                        + "%d requests/minute for BUSINESS keys. Exceeding the limit returns "
                        + "HTTP 429 with a `Retry-After` header (in seconds). The response also "
                        + "includes `X-RateLimit-Limit` and `X-RateLimit-Remaining` headers on "
                        + "every successful call.",
                existing.isBlank() ? "" : existing,
                ann.personalPerMinute(),
                ann.businessPerMinute());
        operation.setDescription(rateLimitDoc.trim());
        return operation;
    }
}
