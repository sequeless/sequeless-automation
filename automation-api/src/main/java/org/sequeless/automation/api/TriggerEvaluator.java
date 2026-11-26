package org.sequeless.automation.api;

import java.util.List;
import org.sequeless.kernel.identity.Scope;

/**
 * The inbound port an automation adapter calls to evaluate the state machine against a change or an
 * elapsed timer, and fire at most one available transition per candidate object. The automation core
 * implements this port. Adapters call it and stay deliberately simple: all candidate resolution, guard
 * evaluation and firing happen inside the implementation.
 *
 * <p>A stale trigger is always a silent no-op, never an exception. The object may have moved on, been
 * deleted, or left the state a timer was scheduled against by the time the adapter calls in.
 */
public interface TriggerEvaluator {

    /**
     * Evaluates the change-triggered transitions that could react to the object named by {@code
     * changeEvent} having been created, updated or deleted. For each candidate object, at most one
     * available change-triggered transition fires.
     *
     * @param scope the tenant and principal to evaluate and fire on behalf of; must not be {@code null}
     * @param changeEvent the event describing the object's change; must not be {@code null}
     */
    void onChange(Scope scope, ObjectChangedEvent changeEvent);

    /**
     * Fires the timer transition named by a previously scheduled timer entry, unless the object's
     * current state no longer matches the state recorded in the entry. That staleness check is the only
     * guard against a timer firing twice or in the wrong state.
     *
     * @param scope the tenant and principal to fire on behalf of; must not be {@code null}
     * @param timerScheduled the timer entry built with {@link TimerScheduledEntry#of}; must not be {@code null}
     */
    void onTimerElapsed(Scope scope, StoreEventRow timerScheduled);

    /**
     * Resolves the timer transitions that a {@link TransitionFiredEvent} starts: one {@link ResolvedTimer}
     * for each timer transition departing the state the object moved into, in declaration order. A move
     * into a state with no timer transition resolves to an empty list.
     *
     * @param scope the tenant to read the behaviour for; must not be {@code null}
     * @param transitionFired the event describing the move; must not be {@code null}
     * @return the timers the event starts, in declaration order; never {@code null}
     * @throws IllegalStateException if the event has no {@code ontologyVersion} stamp, or if the stamp
     *     differs from the active behaviour's ontology version
     */
    List<ResolvedTimer> timersStartedBy(Scope scope, TransitionFiredEvent transitionFired);
}
