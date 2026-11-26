package org.sequeless.automation.api;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The {@link StoreEvent} for an object created, updated or deleted by a committed mutation.
 * {@link #change()} names which of the three it is.
 *
 * @param id the outbox row's own id; must not be {@code null}
 * @param change which mutation the event records; must not be {@code null}
 * @param payload the row's payload, keyed by field name; must not be {@code null}; stored as an
 *     unmodifiable copy so the event cannot change after construction
 * @param occurredAt when the event occurred; must not be {@code null}
 */
public record ObjectChangedEvent(UUID id, Change change, Map<String, Object> payload, Instant occurredAt)
        implements StoreEvent {

    public ObjectChangedEvent {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(change, "change must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        payload = Map.copyOf(payload);
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    /** Which mutation an {@link ObjectChangedEvent} records. */
    public enum Change {

        /** An object was created by a committed create mutation. */
        CREATED,

        /** An object was replaced by a committed update mutation. */
        UPDATED,

        /** An object was removed by a committed delete mutation. */
        DELETED
    }
}
