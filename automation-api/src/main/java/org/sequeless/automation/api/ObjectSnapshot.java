package org.sequeless.automation.api;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.sequeless.kernel.values.Value;

/**
 * A read-only view of one object's type, state and property values, as {@link ObjectReadPort} returns it.
 *
 * @param typeIri the IRI of the object's type; must not be {@code null} or blank
 * @param state the IRI of the object's current state, or empty if the object has none; must not
 *     be {@code null}
 * @param properties the property values, keyed by property IRI; must not be {@code null}; stored as an
 *     unmodifiable copy
 */
public record ObjectSnapshot(String typeIri, Optional<String> state, Map<String, Value> properties) {

    public ObjectSnapshot {
        Objects.requireNonNull(typeIri, "typeIri must not be null");
        if (typeIri.isBlank()) {
            throw new IllegalArgumentException("typeIri must not be blank");
        }
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(properties, "properties must not be null");
        properties = Map.copyOf(properties);
    }
}
