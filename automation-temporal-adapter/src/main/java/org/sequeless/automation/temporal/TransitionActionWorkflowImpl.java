package org.sequeless.automation.temporal;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import java.util.Map;
import java.util.Set;
import org.sequeless.automation.api.StoreEventRow;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;

/**
 * The real {@link TransitionActionWorkflow} implementation. Temporal instantiates this class
 * reflectively, once per execution, so it keeps the implicit public no-arg constructor.
 *
 * <p>Its {@link ActivityOptions} come from {@link TemporalActivityOptions}, the same published value
 * {@link TimerWorkflowImpl} reads, so every activity this workflow calls shares the retry policy the Spring
 * autoconfiguration builds.
 */
public final class TransitionActionWorkflowImpl implements TransitionActionWorkflow {

    @Override
    public void execute(StoreEventRow entry, int actionIndex) {
        ActionActivities activities = Workflow.newActivityStub(ActionActivities.class, TemporalActivityOptions.get());
        activities.applyTransitionAction(scopeFrom(entry), entry, actionIndex);
    }

    /**
     * Reconstructs a {@link Scope} from {@code entry.payload()}'s own {@code tenantId}/{@code
     * principalId} fields. The principal is rebuilt from its id alone, with no roles, as the prototype's
     * test fixtures rebuild it.
     */
    private static Scope scopeFrom(StoreEventRow entry) {
        Map<String, Object> payload = entry.payload();
        String tenantId = (String) payload.get("tenantId");
        String principalId = (String) payload.get("principalId");
        return new Scope(new TenantId(tenantId), new Principal(principalId, Set.of()));
    }
}
