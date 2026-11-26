package org.sequeless.automation.inprocess;

import java.time.Duration;

/**
 * The configuration this adapter reads at startup, under the {@code sequeless.automation.inprocess.*}
 * namespace: the small fixed retry loop {@link InProcessAutomationPort} uses around a webhook action's HTTP
 * call. The loop is fixed-delay rather than exponential, a deliberate contrast with a Temporal adapter's
 * configurable retry options.
 *
 * <p>This class carries no Spring annotation, because core modules do not depend on Spring. The
 * {@code automation-spring-boot-autoconfig} module binds it, with {@link #PREFIX} as the prefix. That module also
 * binds the timer tick, {@link #getTimerTickMs()}, under the same prefix; its default is 100 ms.
 */
public class InProcessAutomationProperties {

    /** The configuration prefix this adapter's properties are bound under. Public contract: do not change it. */
    public static final String PREFIX = "sequeless.automation.inprocess";

    /**
     * How many total attempts {@link InProcessAutomationPort} makes to deliver a webhook action's HTTP request
     * before giving up and throwing {@link WebhookDeliveryException}. Includes the first attempt, not just
     * retries. Defaults to 3.
     */
    private int retryAttempts = 3;

    /**
     * The fixed delay {@link InProcessAutomationPort} waits between successive webhook delivery attempts.
     * Defaults to 200 milliseconds.
     */
    private Duration retryDelay = Duration.ofMillis(200);

    /**
     * The interval, in milliseconds, at which the in-process timer scheduler sweeps for due timers. Defaults to 100.
     */
    private long timerTickMs = 100;

    /**
     * @return how many total attempts are made to deliver a webhook action's HTTP request
     */
    public int getRetryAttempts() {
        return retryAttempts;
    }

    /**
     * @param retryAttempts how many total attempts to make to deliver a webhook action's HTTP request
     */
    public void setRetryAttempts(int retryAttempts) {
        this.retryAttempts = retryAttempts;
    }

    /**
     * @return the fixed delay waited between successive webhook delivery attempts
     */
    public Duration getRetryDelay() {
        return retryDelay;
    }

    /**
     * @param retryDelay the fixed delay to wait between successive webhook delivery attempts
     */
    public void setRetryDelay(Duration retryDelay) {
        this.retryDelay = retryDelay;
    }

    /**
     * @return the interval, in milliseconds, at which the in-process timer scheduler sweeps for due timers
     */
    public long getTimerTickMs() {
        return timerTickMs;
    }

    /**
     * @param timerTickMs the interval, in milliseconds, at which the in-process timer scheduler sweeps for due timers
     */
    public void setTimerTickMs(long timerTickMs) {
        this.timerTickMs = timerTickMs;
    }
}
