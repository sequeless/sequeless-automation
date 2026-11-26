package org.sequeless.automation.temporal;

import io.temporal.workflow.Workflow;
import org.sequeless.automation.api.ObjectChangedEvent;
import org.sequeless.kernel.identity.Scope;

/**
 * The real {@link ChangeEventWorkflow} implementation. It builds its activity stub with the options that
 * {@link TemporalActivityOptions} publishes.
 */
public final class ChangeEventWorkflowImpl implements ChangeEventWorkflow {

    @Override
    public void run(ObjectChangedEvent event, Scope scope) {
        TriggerActivities activities = Workflow.newActivityStub(TriggerActivities.class, TemporalActivityOptions.get());
        activities.onChange(scope, event);
    }
}
