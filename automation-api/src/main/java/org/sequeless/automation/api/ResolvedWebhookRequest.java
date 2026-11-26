package org.sequeless.automation.api;

import java.util.Objects;
import java.util.Optional;

/**
 * The fully-rendered HTTP request a webhook action describes, as returned by
 * {@link ActionExecutor#resolveWebhookAction}. The template placeholders in {@code url} and {@code body}
 * are already substituted. This record carries no HTTP I/O: the calling adapter owns the call, its retry
 * policy and its timeout.
 *
 * @param url the fully-rendered target URL; must not be blank
 * @param method the HTTP method, for example {@code "POST"}; must not be blank
 * @param body the fully-rendered request body, if the action defined one; must not be {@code null}
 *     (the {@link Optional} wrapper itself, not just its contents)
 */
public record ResolvedWebhookRequest(String url, String method, Optional<String> body) {

    public ResolvedWebhookRequest {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("ResolvedWebhookRequest url must not be blank");
        }
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("ResolvedWebhookRequest method must not be blank");
        }
        Objects.requireNonNull(body, "body must not be null");
    }
}
