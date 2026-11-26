package org.sequeless.automation.api;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The {@link StoreEvent} for a state-machine transition that fired. The payload names the transition
 * and carries the ontology version it was committed under.
 *
 * @param id the outbox row's own id; must not be {@code null}
 * @param payload the row's payload, keyed by field name; must not be {@code null}; stored as an
 *     unmodifiable copy so the event cannot change after construction
 * @param occurredAt when the transition fired; must not be {@code null}
 */
public record TransitionFiredEvent(UUID id, Map<String, Object> payload, Instant occurredAt) implements StoreEvent {

    public TransitionFiredEvent {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        payload = Map.copyOf(payload);
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
