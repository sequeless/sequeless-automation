package org.sequeless.automation.api;

import java.util.Objects;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;

/**
 * A command to set one property of an existing object.
 *
 * @param objectId the object to change; must not be {@code null}
 * @param propertyIri the IRI of the property to set; must not be {@code null} or blank
 * @param value the new value; must not be {@code null}
 * @param provenance where the command came from; must not be {@code null}
 */
public record SetPropertyCommand(ObjectId objectId, String propertyIri, Value value, CommandProvenance provenance) {

    public SetPropertyCommand {
        Objects.requireNonNull(objectId, "objectId must not be null");
        Objects.requireNonNull(propertyIri, "propertyIri must not be null");
        if (propertyIri.isBlank()) {
            throw new IllegalArgumentException("propertyIri must not be blank");
        }
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(provenance, "provenance must not be null");
    }
}
