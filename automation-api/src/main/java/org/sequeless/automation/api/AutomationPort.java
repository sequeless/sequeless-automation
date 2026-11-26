package org.sequeless.automation.api;

import org.sequeless.kernel.identity.Scope;

/**
 * The outbound port a relay calls to start the handling of one {@link StoreEvent}: an object created,
 * updated or deleted, or a transition fired. The relay reads each outbox row, maps it to its event
 * type, and calls {@link #ingest}. An adapter implements this port, matches on the sealed event type,
 * and calls back into the automation core to do the work. Exactly one implementation is wired into the
 * running application at a time.
 *
 * <p>Call order: for an object lifecycle row, the host runs its derivation recompute before it calls
 * {@code ingest}. That step is not part of this API.
 *
 * <p>An {@link ObjectChangedEvent} triggers trigger evaluation. A {@link TransitionFiredEvent} applies
 * the transition's actions and starts the timers of the state it enters.
 */
public interface AutomationPort {

    /**
     * Durably starts (or, for a synchronous adapter, completes) the handling of {@code event}, routed
     * by its sealed type. A repeat call with the same event id must not run the underlying operation a
     * second time. Each action of a {@link TransitionFiredEvent} is keyed by the event id and its action
     * index, as described on {@link StoreCommandPort}.
     *
     * @param scope the tenant and principal to ingest the event on behalf of; must not be {@code null}
     * @param event the Store event to ingest; must not be {@code null}
     * @throws NullPointerException if either argument is {@code null}
     * @throws IllegalStateException if a {@link TransitionFiredEvent}'s {@code ontologyVersion} stamp is
     *     missing, or does not match the ontology version of the automation or behaviour the adapter reads
     */
    void ingest(Scope scope, StoreEvent event);
}
