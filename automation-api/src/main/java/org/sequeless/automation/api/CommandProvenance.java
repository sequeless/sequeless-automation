package org.sequeless.automation.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Where a Store command came from: the event that caused it, the action's index in that event's
 * transition, and the ontology version the action was read under. The Store keeps it with the write, so
 * a write can be traced back to its cause.
 *
 * @param sourceEventId the id of the Store event that caused the command; must not be {@code null}
 * @param actionIndex the action's 0-based index in its transition's action list; must not be negative
 * @param ontologyVersion the ontology version the action was read under
 */
public record CommandProvenance(UUID sourceEventId, int actionIndex, int ontologyVersion) {

    public CommandProvenance {
        Objects.requireNonNull(sourceEventId, "sourceEventId must not be null");
        if (actionIndex < 0) {
            throw new IllegalArgumentException("actionIndex must not be negative");
        }
    }
}
