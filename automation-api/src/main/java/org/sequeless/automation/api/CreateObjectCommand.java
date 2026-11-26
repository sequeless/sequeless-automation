package org.sequeless.automation.api;

import java.util.Map;
import java.util.Objects;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;

/**
 * A command to create one object. The {@code objectId} is derived from the command's source, so a repeat
 * of the same command names the same object and the Store can treat it as the idempotency key.
 *
 * @param objectId the id of the object to create; must not be {@code null}
 * @param typeIri the IRI of the object's type; must not be {@code null} or blank
 * @param properties the initial property values, keyed by property IRI; must not be {@code null}; stored
 *     as an unmodifiable copy
 * @param provenance where the command came from; must not be {@code null}
 */
public record CreateObjectCommand(
        ObjectId objectId, String typeIri, Map<String, Value> properties, CommandProvenance provenance) {

    public CreateObjectCommand {
        Objects.requireNonNull(objectId, "objectId must not be null");
        Objects.requireNonNull(typeIri, "typeIri must not be null");
        if (typeIri.isBlank()) {
            throw new IllegalArgumentException("typeIri must not be blank");
        }
        Objects.requireNonNull(properties, "properties must not be null");
        properties = Map.copyOf(properties);
        Objects.requireNonNull(provenance, "provenance must not be null");
    }
}
