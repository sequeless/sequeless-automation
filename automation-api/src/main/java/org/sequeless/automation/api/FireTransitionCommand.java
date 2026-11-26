package org.sequeless.automation.api;

import java.util.Objects;
import org.sequeless.kernel.values.ObjectId;

/**
 * A command to fire one transition on an object. The Store fires it only if the transition is available
 * from the object's current state.
 *
 * @param objectId the object to transition; must not be {@code null}
 * @param transitionName the name of the transition to fire; must not be {@code null} or blank
 * @param trigger what caused the transition; must not be {@code null}
 */
public record FireTransitionCommand(ObjectId objectId, String transitionName, Trigger trigger) {

    public FireTransitionCommand {
        Objects.requireNonNull(objectId, "objectId must not be null");
        Objects.requireNonNull(transitionName, "transitionName must not be null");
        if (transitionName.isBlank()) {
            throw new IllegalArgumentException("transitionName must not be blank");
        }
        Objects.requireNonNull(trigger, "trigger must not be null");
    }

    /** What caused a {@link FireTransitionCommand}. */
    public enum Trigger {

        /** A change to an object caused the transition. */
        ON_CHANGE,

        /** An elapsed timer caused the transition. */
        TIMER
    }
}
