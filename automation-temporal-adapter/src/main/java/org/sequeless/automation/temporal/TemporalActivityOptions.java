package org.sequeless.automation.temporal;

import io.temporal.activity.ActivityOptions;
import java.util.Objects;

/**
 * Holds the {@link ActivityOptions} every workflow implementation in this package builds its activity
 * stub with. The options carry the {@code RetryOptions} and timeout that the Spring autoconfiguration in
 * {@code automation-spring-boot-autoconfig} assembles from {@code sequeless.automation.temporal.retry.*}.
 *
 * <p>Temporal instantiates a workflow implementation itself, reflectively, once per workflow execution.
 * It therefore cannot receive these options through a constructor. Instead the Spring autoconfiguration
 * calls {@link #publish} once, before the worker starts, and every later execution reads the same value
 * through {@link #get}. Nothing about a workflow's deterministic replay depends on when the options were
 * published, only on what they were when the workflow runs.
 *
 * <p>{@link #get} must not be called before {@link #publish}. That would mean a worker started without the
 * autoconfiguration having run, which is a wiring bug, so {@link #get} throws.
 */
public final class TemporalActivityOptions {

    /** The {@link ActivityOptions} every activity stub is built with, published once before worker start. */
    private static volatile ActivityOptions ACTIVITY_OPTIONS;

    private TemporalActivityOptions() {}

    /**
     * Publishes the {@link ActivityOptions} that every later workflow execution builds its activity stubs
     * with. The Spring autoconfiguration calls this once, before the worker starts.
     *
     * @param options the activity options to publish; must not be {@code null}
     * @throws NullPointerException if {@code options} is {@code null}
     */
    public static void publish(ActivityOptions options) {
        ACTIVITY_OPTIONS = Objects.requireNonNull(options, "options");
    }

    /**
     * Returns the options last passed to {@link #publish}.
     *
     * @return the published activity options
     * @throws IllegalStateException if {@link #publish} has not been called
     */
    static ActivityOptions get() {
        ActivityOptions options = ACTIVITY_OPTIONS;
        if (options == null) {
            throw new IllegalStateException("TemporalActivityOptions.ACTIVITY_OPTIONS was never set - "
                    + "the Spring autoconfiguration must assign it before starting the worker");
        }
        return options;
    }
}
