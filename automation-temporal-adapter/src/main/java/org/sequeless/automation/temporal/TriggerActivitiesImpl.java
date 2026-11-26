package org.sequeless.automation.temporal;

import java.util.Objects;
import org.sequeless.automation.api.ObjectChangedEvent;
import org.sequeless.automation.api.StoreEventRow;
import org.sequeless.automation.api.TriggerEvaluator;
import org.sequeless.kernel.identity.Scope;

/**
 * The real {@link TriggerActivities} implementation: two pure pass-throughs to the injected {@link
 * TriggerEvaluator}. The derivation recompute runs in the outbox relay before ingest, not here.
 */
public final class TriggerActivitiesImpl implements TriggerActivities {

    private final TriggerEvaluator triggerEvaluator;

    /**
     * @param triggerEvaluator the port {@link #onChange}/{@link #onTimerElapsed} forward to; must
     *     not be {@code null}
     */
    public TriggerActivitiesImpl(TriggerEvaluator triggerEvaluator) {
        this.triggerEvaluator = Objects.requireNonNull(triggerEvaluator, "triggerEvaluator must not be null");
    }

    @Override
    public void onChange(Scope scope, ObjectChangedEvent changeEvent) {
        triggerEvaluator.onChange(scope, changeEvent);
    }

    @Override
    public void onTimerElapsed(Scope scope, StoreEventRow timerScheduled) {
        triggerEvaluator.onTimerElapsed(scope, timerScheduled);
    }
}
