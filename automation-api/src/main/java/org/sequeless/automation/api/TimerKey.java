package org.sequeless.automation.api;

import java.util.Objects;
import org.sequeless.kernel.values.ObjectId;

/**
 * The stable key that names one timer transition's pending wait. The key is {@code
 * objectId|stateIri|transitionName}, where {@code stateIri} is the state the timer waits in. Adapters use
 * it as the durable timer's identity, so every writer and reader of a timer key must build it here: a
 * changed formula would orphan a running timer.
 */
public final class TimerKey {

    private TimerKey() {}

    /**
     * Builds the timer key for one object, state and timer transition.
     *
     * @param objectId the object the timer belongs to; must not be {@code null}
     * @param stateIri the IRI of the state the timer waits in; must not be {@code null}
     * @param transitionName the name of the timer transition; must not be {@code null}
     * @return the timer key for that object, state and transition
     */
    public static String of(ObjectId objectId, String stateIri, String transitionName) {
        Objects.requireNonNull(objectId, "objectId must not be null");
        Objects.requireNonNull(stateIri, "stateIri must not be null");
        Objects.requireNonNull(transitionName, "transitionName must not be null");
        return objectId.value().toString() + "|" + stateIri + "|" + transitionName;
    }
}
