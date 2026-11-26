package org.sequeless.automation.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One row of the Store's outbox, as the automation side reads it. The host maps each outbox row to
 * this record before it calls the automation API, so the API never sees a Store type.
 *
 * <p>{@code kind} is a string, not an enum, so the Store can add a kind without changing this record.
 * The kinds the Store records for object mutations and fired transitions are the {@code KIND_} constants.
 *
 * <p>{@code payload} must be JSON-compatible end to end: every value, at any depth, is a {@code String},
 * a {@code Number}, a {@code Boolean}, a {@code List} of such values, or a {@code Map} of such values, and
 * {@code null} never appears anywhere in it. The check runs in the constructor, so an incompatible value
 * fails here, naming its path, rather than later inside a JSON codec.
 *
 * @param id the outbox row's own identity; must not be {@code null}
 * @param kind the kind of event the row records; must not be {@code null} or blank
 * @param payload the row's payload, keyed by field name; must not be {@code null}; every value must be
 *     JSON-compatible; stored as an unmodifiable copy so the row cannot change after construction
 * @param occurredAt when the event occurred; must not be {@code null}
 */
public record StoreEventRow(UUID id, String kind, Map<String, Object> payload, Instant occurredAt) {

    /** The kind recorded when an object is created by a committed create mutation. */
    public static final String KIND_OBJECT_CREATED = "ObjectCreated";

    /** The kind recorded when an object is replaced by a committed update mutation. */
    public static final String KIND_OBJECT_UPDATED = "ObjectUpdated";

    /** The kind recorded when an object is removed by a committed delete mutation. */
    public static final String KIND_OBJECT_DELETED = "ObjectDeleted";

    /** The kind recorded once for each fired state-machine transition. */
    public static final String KIND_TRANSITION_FIRED = "TransitionFired";

    public StoreEventRow {
        Objects.requireNonNull(id, "id must not be null");
        if (kind == null || kind.isBlank()) {
            throw new IllegalArgumentException("StoreEventRow kind must not be null or blank");
        }
        Objects.requireNonNull(payload, "payload must not be null");
        payload.forEach(StoreEventRow::requireJsonCompatible);
        payload = Map.copyOf(payload);
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    /**
     * Recursively checks that {@code value}, found at {@code path} within the payload, is JSON-compatible.
     *
     * @param path the payload path {@code value} was found at, for the exception message
     * @param value the value to check
     * @throws IllegalArgumentException naming the path, and the offending type, if the value is {@code null}
     *     or is not a {@code String}, {@code Number}, {@code Boolean}, {@code List} or {@code Map}
     */
    private static void requireJsonCompatible(String path, Object value) {
        if (value == null) {
            throw new IllegalArgumentException("StoreEventRow payload value at '" + path + "' must not be null");
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return;
        }
        if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                requireJsonCompatible(path + "[" + i + "]", list.get(i));
            }
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                requireJsonCompatible(path + "." + entry.getKey(), entry.getValue());
            }
            return;
        }
        throw new IllegalArgumentException(
                "StoreEventRow payload value at '" + path + "' is not JSON-compatible: " + value.getClass());
    }
}
