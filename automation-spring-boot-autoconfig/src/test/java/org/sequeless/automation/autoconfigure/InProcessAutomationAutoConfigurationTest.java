package org.sequeless.automation.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.sequeless.automation.api.AutomationPort;
import org.sequeless.automation.api.ResolvedTimer;
import org.sequeless.automation.api.TriggerEvaluator;
import org.sequeless.automation.inprocess.InProcessAutomationPort;
import org.sequeless.automation.inprocess.InProcessAutomationProperties;
import org.sequeless.automation.inprocess.InProcessTimerScheduler;
import org.sequeless.automation.testkit.RecordingTriggerEvaluator;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.kernel.values.ObjectId;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Exercises {@link InProcessAutomationAutoConfiguration} through an {@link ApplicationContextRunner}. */
class InProcessAutomationAutoConfigurationTest {

    private static final String SELECT_IN_PROCESS = "sequeless.automation.adapter=inprocess";

    private final ApplicationContextRunner runner = HostPorts.add(new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AutomationCoreAutoConfiguration.class, InProcessAutomationAutoConfiguration.class)));

    @Test
    void wiresTheInProcessPortAndTimerSchedulerWhenTheAdapterIsSelected() {
        runner.withPropertyValues(SELECT_IN_PROCESS).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AutomationPort.class);
            assertThat(context.getBean(AutomationPort.class)).isInstanceOf(InProcessAutomationPort.class);
            assertThat(context).hasSingleBean(InProcessTimerScheduler.class);
        });
    }

    @Test
    void doesNotWireWhenTheAdapterPropertyIsAbsent() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(AutomationPort.class);
            assertThat(context).doesNotHaveBean(InProcessTimerScheduler.class);
        });
    }

    @Test
    void doesNotWireWhenTheAdapterPropertyNamesAnotherAdapter() {
        runner.withPropertyValues("sequeless.automation.adapter=temporal").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(AutomationPort.class);
        });
    }

    @Test
    void defaultTimerTickIs100Milliseconds() {
        runner.withPropertyValues(SELECT_IN_PROCESS).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(InProcessAutomationProperties.class).getTimerTickMs())
                    .isEqualTo(100L);
        });
    }

    @Test
    void bindsTheTimerTickAndTheRetrySettingsUnderTheInProcessPrefix() {
        runner.withPropertyValues(
                        SELECT_IN_PROCESS,
                        "sequeless.automation.inprocess.timer-tick-ms=250",
                        "sequeless.automation.inprocess.retry-attempts=5")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    InProcessAutomationProperties properties = context.getBean(InProcessAutomationProperties.class);
                    assertThat(properties.getTimerTickMs()).isEqualTo(250L);
                    assertThat(properties.getRetryAttempts()).isEqualTo(5);
                });
    }

    /**
     * The periodic task must be scheduled on {@code tick}, which survives a failed run. Here the clock's first reading
     * fails. Until a timer is scheduled, only the periodic task reads the clock, so the first tick takes the failure.
     * The test then schedules a due timer and asserts that a later tick fires it. A periodic task scheduled on
     * {@code sweep} would be cancelled by that first failure, and the timer would never fire.
     */
    @Test
    void thePeriodicTickFiresADueTimer() throws Exception {
        FirstReadingFails clock = new FirstReadingFails();
        runner.withPropertyValues(SELECT_IN_PROCESS, "sequeless.automation.inprocess.timer-tick-ms=10")
                .withBean(TriggerEvaluator.class, RecordingTriggerEvaluator::new)
                .withBean(Clock.class, () -> clock)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    RecordingTriggerEvaluator evaluator =
                            (RecordingTriggerEvaluator) context.getBean(TriggerEvaluator.class);

                    awaitUntil(clock::hasFailed);
                    assertThat(clock.hasFailed())
                            .as("a tick read the clock and failed")
                            .isTrue();

                    // A zero-duration timer is due at once, so only a scheduled tick after the failure can fire it.
                    context.getBean(InProcessTimerScheduler.class)
                            .schedule(
                                    new Scope(TenantId.DEFAULT, Principal.ANONYMOUS),
                                    ObjectId.random(),
                                    "urn:sequeless:test:Type",
                                    1,
                                    new ResolvedTimer("timer-key-1", Duration.ZERO, "urn:sequeless:test:State", "go"));

                    awaitUntil(() -> evaluator.countOf(RecordingTriggerEvaluator.METHOD_ON_TIMER_ELAPSED) > 0);
                    assertThat(evaluator.countOf(RecordingTriggerEvaluator.METHOD_ON_TIMER_ELAPSED))
                            .isPositive();
                });
    }

    /** Polls {@code condition} every 10 ms for at most two seconds, then returns whether or not it holds. */
    private static void awaitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(10);
        }
    }

    /**
     * A clock whose first reading throws. A scheduler sweep reads the clock once, so the first sweep fails and every
     * later reading returns the time normally.
     */
    private static final class FirstReadingFails extends Clock {

        private final AtomicBoolean failed = new AtomicBoolean();

        /** @return whether the first reading has thrown */
        boolean hasFailed() {
            return failed.get();
        }

        @Override
        public Instant instant() {
            if (!failed.getAndSet(true)) {
                throw new IllegalStateException("the first clock reading fails");
            }
            return Instant.now();
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException("the scheduler never changes the zone");
        }
    }
}
