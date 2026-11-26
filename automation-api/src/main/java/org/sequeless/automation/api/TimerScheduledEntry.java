package org.sequeless.automation.api;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.values.ObjectId;

/**
 * The timer entry a transition-fired row starts, built from a {@link ResolvedTimer} by {@link #of}. An
 * automation adapter passes this entry to {@link TriggerEvaluator#onTimerElapsed} once the timer elapses,
 * so the elapsed-timer call reads the same payload the timer was scheduled with.
 *
 * <p>The kind is {@link #KIND}. The payload carries {@code objectId}, {@code tenantId}, {@code
 * principalId}, {@code typeIri}, {@code state}, {@code transitionName}, {@code after} (ISO-8601), {@code
 * timerKey} (the timer's own {@link ResolvedTimer#timerKey()}, copied unchanged) and {@code ontologyVersion}.
 */
public final class TimerScheduledEntry {

    /** The kind string of a timer-scheduled entry, {@code "TimerScheduled"}. */
    public static final String KIND = "TimerScheduled";

    private TimerScheduledEntry() {}

    /**
     * Builds the timer entry for {@code timer}, a timer that the transition-fired row for {@code objectId}
     * starts.
     *
     * @param scope the tenant and principal the timer is started under; must not be {@code null}
     * @param objectId the object the timer belongs to; must not be {@code null}
     * @param typeIri the IRI of the object's type; must not be {@code null}
     * @param ontologyVersion the ontology version the timer was resolved at
     * @param timer the resolved timer to describe; must not be {@code null}
     * @param occurredAt when the entry is recorded as occurring; must not be {@code null}
     * @return the entry, of kind {@link #KIND}
     */
    public static StoreEventRow of(
            Scope scope,
            ObjectId objectId,
            String typeIri,
            int ontologyVersion,
            ResolvedTimer timer,
            Instant occurredAt) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(objectId, "objectId must not be null");
        Objects.requireNonNull(typeIri, "typeIri must not be null");
        Objects.requireNonNull(timer, "timer must not be null");
        Map<String, Object> payload = new HashMap<>();
        payload.put("objectId", objectId.value().toString());
        payload.put("tenantId", scope.tenantId().value());
        payload.put("principalId", scope.principal().id());
        payload.put("typeIri", typeIri);
        payload.put("state", timer.stateIri());
        payload.put("transitionName", timer.transitionName());
        payload.put("after", timer.after().toString());
        payload.put("timerKey", timer.timerKey());
        payload.put("ontologyVersion", ontologyVersion);
        return new StoreEventRow(UUID.randomUUID(), KIND, payload, occurredAt);
    }
}
