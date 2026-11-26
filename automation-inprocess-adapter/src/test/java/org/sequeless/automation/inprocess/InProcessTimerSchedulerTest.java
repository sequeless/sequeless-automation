package org.sequeless.automation.inprocess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sequeless.automation.api.ObjectChangedEvent;
import org.sequeless.automation.api.ResolvedTimer;
import org.sequeless.automation.api.StoreEventRow;
import org.sequeless.automation.api.TransitionFiredEvent;
import org.sequeless.automation.api.TriggerEvaluator;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.kernel.values.ObjectId;

/**
 * Proves that one failing timer cannot stop {@link InProcessTimerScheduler#sweep()} or the other due timers,
 * that a failed timer stays pending until a later sweep fires it, and that {@link InProcessTimerScheduler#tick()}
 * keeps the periodic schedule alive: it catches a {@link RuntimeException} that escapes the sweep, and it lets an
 * {@link Error} propagate.
 */
class InProcessTimerSchedulerTest {

    private static final Scope SCOPE = new Scope(new TenantId("tenant-1"), new Principal("user-1", Set.of()));
    private static final String TYPE_IRI = "https://example.org/ns#Project";
    private static final String STATE_IRI = "https://example.org/ns#Active";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final TimerEvaluator evaluator = new TimerEvaluator();
    private final InProcessTimerScheduler scheduler = new InProcessTimerScheduler(clock, evaluator);

    @Test
    void aThrowingTimerDoesNotStopTheSweepOrTheOtherDueTimers() {
        schedule("a");
        schedule("b");
        evaluator.failNext("a", 1);
        clock.advance(Duration.ofSeconds(2));

        assertThatCode(scheduler::sweep).doesNotThrowAnyException();

        assertThat(evaluator.attempts).containsExactlyInAnyOrder("a", "b");
        assertThat(evaluator.completions).containsExactly("b");
    }

    @Test
    void aFailedTimerStaysPendingAndTheNextSweepRetriesIt() {
        schedule("a");
        evaluator.failNext("a", 1);
        clock.advance(Duration.ofSeconds(2));

        scheduler.sweep();
        scheduler.sweep();

        assertThat(evaluator.attempts).containsExactly("a", "a");
        assertThat(evaluator.completions).containsExactly("a");
    }

    @Test
    void aTimerThatElapsedIsNotRunAgain() {
        schedule("a");
        clock.advance(Duration.ofSeconds(2));

        scheduler.sweep();
        scheduler.sweep();

        assertThat(evaluator.attempts).containsExactly("a");
    }

    @Test
    void aSweepReturnsNormallyWhenEveryDueTimerFails() {
        schedule("a");
        schedule("b");
        evaluator.failNext("a", 5);
        evaluator.failNext("b", 5);
        clock.advance(Duration.ofSeconds(2));

        assertThatCode(scheduler::sweep).doesNotThrowAnyException();
        assertThatCode(scheduler::sweep).doesNotThrowAnyException();

        assertThat(evaluator.attempts).containsExactlyInAnyOrder("a", "a", "b", "b");
        assertThat(evaluator.completions).isEmpty();
    }

    @Test
    void aTimerThatIsNotYetDueIsNotAttempted() {
        schedule("a");

        scheduler.sweep();

        assertThat(evaluator.attempts).isEmpty();
    }

    @Test
    void aRuntimeExceptionEscapingTheSweepIsCaughtByTickAndTheNextTickStillFiresDueTimers() {
        MutableClock base = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        FailingReadClock failingClock = new FailingReadClock(base);
        InProcessTimerScheduler tickScheduler = new InProcessTimerScheduler(failingClock, evaluator);
        scheduleOn(tickScheduler, "a");
        base.advance(Duration.ofSeconds(2));
        failingClock.failNextReadWith(new IllegalStateException("clock read failed"));

        assertThatCode(tickScheduler::tick).doesNotThrowAnyException();
        assertThat(evaluator.attempts).isEmpty();

        tickScheduler.tick();

        assertThat(evaluator.attempts).containsExactly("a");
        assertThat(evaluator.completions).containsExactly("a");
    }

    @Test
    void tickDoesNotSwallowAnError() {
        MutableClock base = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        FailingReadClock failingClock = new FailingReadClock(base);
        InProcessTimerScheduler tickScheduler = new InProcessTimerScheduler(failingClock, evaluator);
        scheduleOn(tickScheduler, "a");
        base.advance(Duration.ofSeconds(2));
        Error failure = new Error("simulated Error escaping the sweep");
        failingClock.failNextReadWith(failure);

        assertThatThrownBy(tickScheduler::tick).isSameAs(failure);

        assertThat(evaluator.attempts).isEmpty();
    }

    private void schedule(String timerKey) {
        scheduleOn(scheduler, timerKey);
    }

    private static void scheduleOn(InProcessTimerScheduler target, String timerKey) {
        target.schedule(
                SCOPE,
                ObjectId.random(),
                TYPE_IRI,
                1,
                new ResolvedTimer(timerKey, Duration.ofSeconds(1), STATE_IRI, "expire"));
    }

    /**
     * Records each attempted and each completed {@code onTimerElapsed}, by timer key, and throws for the next
     * {@link #failNext} attempts of a given key.
     */
    private static final class TimerEvaluator implements TriggerEvaluator {

        private final List<String> attempts = new ArrayList<>();
        private final List<String> completions = new ArrayList<>();
        private final Map<String, Integer> failuresLeft = new HashMap<>();

        void failNext(String timerKey, int times) {
            failuresLeft.put(timerKey, times);
        }

        @Override
        public void onTimerElapsed(Scope scope, StoreEventRow timerScheduled) {
            String timerKey = (String) timerScheduled.payload().get("timerKey");
            attempts.add(timerKey);
            if (failuresLeft.getOrDefault(timerKey, 0) > 0) {
                failuresLeft.merge(timerKey, -1, Integer::sum);
                throw new IllegalStateException("onTimerElapsed failed for " + timerKey);
            }
            completions.add(timerKey);
        }

        @Override
        public void onChange(Scope scope, ObjectChangedEvent changeEvent) {}

        @Override
        public List<ResolvedTimer> timersStartedBy(Scope scope, TransitionFiredEvent transitionFired) {
            return List.of();
        }
    }

    /**
     * A {@link Clock} that delegates to a {@link MutableClock}, except that its next read throws the failure it
     * was armed with, once. {@link InProcessTimerScheduler#sweep()} reads the clock before it reaches any timer, so
     * an armed failure escapes the sweep, which is the case {@link InProcessTimerScheduler#tick()} guards. A
     * {@link TriggerEvaluator} failure never escapes, since the sweep isolates each timer.
     */
    private static final class FailingReadClock extends Clock {

        private final MutableClock delegate;
        private RuntimeException nextRuntimeFailure;
        private Error nextErrorFailure;

        FailingReadClock(MutableClock delegate) {
            this.delegate = delegate;
        }

        void failNextReadWith(RuntimeException failure) {
            nextRuntimeFailure = failure;
        }

        void failNextReadWith(Error failure) {
            nextErrorFailure = failure;
        }

        @Override
        public Instant instant() {
            if (nextRuntimeFailure != null) {
                RuntimeException failure = nextRuntimeFailure;
                nextRuntimeFailure = null;
                throw failure;
            }
            if (nextErrorFailure != null) {
                Error failure = nextErrorFailure;
                nextErrorFailure = null;
                throw failure;
            }
            return delegate.instant();
        }

        @Override
        public ZoneId getZone() {
            return delegate.getZone();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return delegate.withZone(zone);
        }
    }
}
