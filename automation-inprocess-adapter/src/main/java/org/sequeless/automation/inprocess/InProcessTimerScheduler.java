package org.sequeless.automation.inprocess;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.sequeless.automation.api.ResolvedTimer;
import org.sequeless.automation.api.StoreEventRow;
import org.sequeless.automation.api.TimerScheduledEntry;
import org.sequeless.automation.api.TriggerEvaluator;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.values.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The in-process substitute for a durable timer engine (a Temporal adapter's timer workflow). It keeps every
 * pending timer that a {@code TransitionFired} row starts in an in-memory map keyed by {@link
 * ResolvedTimer#timerKey()}, and fires {@link TriggerEvaluator#onTimerElapsed} for whichever of them are due
 * whenever {@link #sweep()} runs. A host runs {@link #tick()} on a schedule, which calls {@link #sweep()}.
 * {@link InProcessAutomationPort} calls {@link #schedule} once for each timer the row starts, before it applies
 * the row's actions. There is no cancel: a timer runs to its due instant, and the staleness check in {@link
 * TriggerEvaluator#onTimerElapsed} is the only guard against firing after the object has left the timer's state.
 *
 * <p><b>Not crash-safe.</b> A process restart forgets every pending timer, exactly like {@link
 * InProcessAutomationPort}'s own idempotency sets. Stopping the process loses every pending timer. This is an
 * accepted limitation of the in-process adapter, which exists only for tests and local development. Durability
 * across restarts is a durable engine's job.
 *
 * <p>The {@link Clock} is constructor-injected so that tests can substitute a mutable fake and call {@link
 * #sweep()} synchronously after advancing it. There is no sleeping, no polling, and firing is deterministic
 * the instant a due timer's time has passed.
 */
public final class InProcessTimerScheduler {

    /**
     * A pending timer: the {@link ResolvedTimer} it was scheduled from (which carries the timer key, the state
     * and the transition name), its due instant, and the object and scope the sweep needs to rebuild the
     * {@code TimerScheduled} entry at fire time.
     */
    private record ScheduledTimer(
            Instant dueAt, Scope scope, ObjectId objectId, String typeIri, int ontologyVersion, ResolvedTimer timer) {}

    private static final Logger log = LoggerFactory.getLogger(InProcessTimerScheduler.class);

    private final Clock clock;
    private final TriggerEvaluator triggerEvaluator;
    private final Map<String, ScheduledTimer> pending = new ConcurrentHashMap<>();

    /**
     * The keys whose failure has already been logged with a stack trace and not since succeeded. A timer that
     * keeps failing logs its trace once, then one line per later failure, so a stuck timer does not flood the
     * log every tick.
     */
    private final Set<String> reportedFailures = ConcurrentHashMap.newKeySet();

    /**
     * @param clock this scheduler's notion of "now"; {@link Clock#systemUTC()} in production, a mutable fake
     *     in tests; must not be {@code null}
     * @param triggerEvaluator the port {@link #sweep()} calls {@link TriggerEvaluator#onTimerElapsed} on for
     *     each due timer; must not be {@code null}
     */
    public InProcessTimerScheduler(Clock clock, TriggerEvaluator triggerEvaluator) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.triggerEvaluator = Objects.requireNonNull(triggerEvaluator, "triggerEvaluator must not be null");
    }

    /**
     * Records a pending timer that a {@code TransitionFired} row starts, due {@code timer.after()} from {@link
     * #clock}'s current instant. The timer is keyed by {@link ResolvedTimer#timerKey()}.
     *
     * <p>A {@code timerKey} that is already pending is left alone: the map uses {@code putIfAbsent}, so the
     * original due instant survives. A {@code TransitionFired} row is redelivered when a retry re-enters
     * {@link InProcessAutomationPort#ingest}, and this keeps such a retry from moving the due time. A timer whose
     * firing keeps failing stays pending under its key, so a later {@code schedule} for that key does not replace
     * it and does not move its due instant. There is no retry cap: a failing timer is retried on every sweep, by
     * decision of the Navigator. A timer that {@link #sweep()} has already fired and removed is armed again. The
     * staleness check in {@link TriggerEvaluator#onTimerElapsed} makes that second firing a no-op.
     *
     * @param scope the tenant and principal {@link #sweep()} fires {@code onTimerElapsed} under for this
     *     timer; must not be {@code null}
     * @param objectId the object the timer belongs to, from which {@link #sweep()} rebuilds the entry with
     *     {@link TimerScheduledEntry#of}; must not be {@code null}
     * @param typeIri the IRI of the object's type, for the same rebuilt entry; must not be {@code null}
     * @param ontologyVersion the ontology version the {@code TransitionFired} row is stamped with, for the same
     *     rebuilt entry
     * @param timer the timer the {@link TriggerEvaluator} resolved for that row; its key, duration, state and
     *     transition name are stored; must not be {@code null}
     */
    public void schedule(Scope scope, ObjectId objectId, String typeIri, int ontologyVersion, ResolvedTimer timer) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(objectId, "objectId must not be null");
        Objects.requireNonNull(typeIri, "typeIri must not be null");
        Objects.requireNonNull(timer, "timer must not be null");
        ScheduledTimer armed = new ScheduledTimer(
                clock.instant().plus(timer.after()), scope, objectId, typeIri, ontologyVersion, timer);
        if (pending.putIfAbsent(timer.timerKey(), armed) == null) {
            // A newly armed timer starts with no failure on record, so its first failure logs a full trace.
            reportedFailures.remove(timer.timerKey());
        }
    }

    /**
     * Fires {@link TriggerEvaluator#onTimerElapsed} for every pending timer whose {@code dueAt} is not after
     * {@link #clock}'s current instant, and removes each one only after its firing returns normally. The entry
     * passed to {@code onTimerElapsed} is a {@code TimerScheduled} entry rebuilt from the stored timer with
     * {@link TimerScheduledEntry#of}, and its {@code occurredAt} is the instant of this sweep.
     *
     * <p>Each timer is isolated. A {@link RuntimeException} from {@code onTimerElapsed} is logged, and that timer
     * stays pending, so the next sweep retries it. Every other due timer still fires in this sweep, and the sweep
     * itself does not throw because of one timer. A timer is removed only after its {@code onTimerElapsed}
     * returns normally.
     *
     * <p>Tests call this directly, synchronously, after advancing the clock. A production host does not call it
     * itself: it calls {@link #tick()}, which guards the periodic schedule.
     */
    public void sweep() {
        Instant now = clock.instant();
        for (Map.Entry<String, ScheduledTimer> pendingEntry : pending.entrySet()) {
            ScheduledTimer timer = pendingEntry.getValue();
            if (timer.dueAt().isAfter(now)) {
                continue;
            }
            String key = pendingEntry.getKey();
            try {
                StoreEventRow elapsed = TimerScheduledEntry.of(
                        timer.scope(), timer.objectId(), timer.typeIri(), timer.ontologyVersion(), timer.timer(), now);
                triggerEvaluator.onTimerElapsed(timer.scope(), elapsed);
                pending.remove(key, timer);
                reportedFailures.remove(key);
            } catch (RuntimeException failure) {
                if (reportedFailures.add(key)) {
                    log.error(
                            "Timer {} failed to elapse; it stays pending and the next sweep retries it", key, failure);
                } else {
                    log.error(
                            "Timer {} failed to elapse again: {}: {}",
                            key,
                            failure.getClass().getName(),
                            failure.getMessage());
                }
            }
        }
    }

    /**
     * The periodic entry point a host schedules: runs one {@link #sweep()} and keeps the schedule alive.
     *
     * <p>A {@link java.util.concurrent.ScheduledExecutorService} stops running a periodic task for good once one
     * of its runs throws, so a periodic caller must schedule {@code tick()}, not {@code sweep()}. {@link #sweep()}
     * already isolates each timer, so a {@link RuntimeException} that reaches here is a failure outside any single
     * timer's firing. It is logged, and the next tick runs as scheduled. An {@link Error} is not caught: it is not a
     * per-timer failure, and a virtual-machine error should not be swallowed. It still ends the periodic task. The
     * Spring autoconfiguration that schedules this method lives in {@code automation-spring-boot-autoconfig}, so the
     * guard lives here, where that module can call it.
     */
    public void tick() {
        try {
            sweep();
        } catch (RuntimeException failure) {
            log.error("Timer sweep failed outside a single timer; the next tick runs as scheduled", failure);
        }
    }
}
