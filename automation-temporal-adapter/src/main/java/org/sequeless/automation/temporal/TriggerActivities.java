package org.sequeless.automation.temporal;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import org.sequeless.automation.api.ObjectChangedEvent;
import org.sequeless.automation.api.StoreEventRow;
import org.sequeless.automation.api.TriggerEvaluator;
import org.sequeless.kernel.identity.Scope;

/**
 * The Temporal activities {@link TimerWorkflowImpl} and {@link ChangeEventWorkflowImpl} call, one per
 * {@link TriggerEvaluator} inbound-port method — mirroring {@link ActionActivities}' one-interface,
 * thin-pass-through pattern exactly, so each method could later be given its own {@code
 * ActivityOptions} without touching the others. Today all share the single {@code ActivityOptions}
 * that the Spring autoconfiguration builds from {@code sequeless.automation.temporal.retry.*}, the same
 * one {@link ActionActivities} shares.
 */
@ActivityInterface
public interface TriggerActivities {

    /** Evaluates on-change triggers. See {@link TriggerEvaluator#onChange}. */
    @ActivityMethod
    void onChange(Scope scope, ObjectChangedEvent changeEvent);

    /** Evaluates an elapsed timer. See {@link TriggerEvaluator#onTimerElapsed}. */
    @ActivityMethod
    void onTimerElapsed(Scope scope, StoreEventRow timerScheduled);
}
