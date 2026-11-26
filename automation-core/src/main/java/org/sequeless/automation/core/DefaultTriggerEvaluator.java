package org.sequeless.automation.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.sequeless.automation.api.FireTransitionCommand;
import org.sequeless.automation.api.ObjectChangedEvent;
import org.sequeless.automation.api.ObjectReadPort;
import org.sequeless.automation.api.ObjectSnapshot;
import org.sequeless.automation.api.ResolvedTimer;
import org.sequeless.automation.api.StoreCommandPort;
import org.sequeless.automation.api.StoreEventRow;
import org.sequeless.automation.api.TimerKey;
import org.sequeless.automation.api.TransitionFiredEvent;
import org.sequeless.automation.api.TriggerEvaluator;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.values.ListValue;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.ReferenceValue;
import org.sequeless.kernel.values.Value;
import org.sequeless.ontology.behaviour.api.Behaviour;
import org.sequeless.ontology.behaviour.api.OnChangeTrigger;
import org.sequeless.ontology.behaviour.api.StateMachineDefinition;
import org.sequeless.ontology.behaviour.api.TimerTrigger;
import org.sequeless.ontology.behaviour.api.Transition;
import org.sequeless.ontology.behaviour.spi.BehaviourSource;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.api.TypeDefinition;
import org.sequeless.ontology.schema.spi.SchemaSource;

/**
 * Reference implementation of {@link TriggerEvaluator}. An automation adapter calls it once a domain-lifecycle change
 * or an elapsed timer might make an {@code OnChange} or {@code Timer} transition available.
 *
 * <p>This class never changes an object directly. Every state move is a {@link FireTransitionCommand} on
 * {@link StoreCommandPort}, and the Store owns the availability and guard checks. A stale trigger is a silent no-op,
 * never an exception. A malformed payload, a denied read, and a timer stamp that does not match the active behaviour
 * are the only things that throw.
 */
public final class DefaultTriggerEvaluator implements TriggerEvaluator {

    private final SchemaSource schemaSource;
    private final BehaviourSource behaviourSource;
    private final ObjectReadPort readPort;
    private final StoreCommandPort commandPort;

    /**
     * @param schemaSource the port the schema half is read from; must not be {@code null}
     * @param behaviourSource the port the behaviour half is read from; must not be {@code null}
     * @param readPort the port every candidate and changed-object read goes through; must not be {@code null}
     * @param commandPort the port every transition is fired through; must not be {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public DefaultTriggerEvaluator(
            SchemaSource schemaSource,
            BehaviourSource behaviourSource,
            ObjectReadPort readPort,
            StoreCommandPort commandPort) {
        this.schemaSource = Objects.requireNonNull(schemaSource, "schemaSource must not be null");
        this.behaviourSource = Objects.requireNonNull(behaviourSource, "behaviourSource must not be null");
        this.readPort = Objects.requireNonNull(readPort, "readPort must not be null");
        this.commandPort = Objects.requireNonNull(commandPort, "commandPort must not be null");
    }

    @Override
    public void onChange(Scope scope, ObjectChangedEvent changeEvent) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(changeEvent, "changeEvent must not be null");

        Map<String, Object> payload = changeEvent.payload();
        ObjectId objectId = ObjectId.parse((String) payload.get("objectId"));
        String typeIri = (String) payload.get("typeIri");

        SchemaAndBehaviour halves = SchemaAndBehaviour.read(scope.tenantId(), schemaSource, behaviourSource);
        Schema schema = halves.schema();
        Behaviour behaviour = halves.behaviour();
        Optional<TypeDefinition> maybeChangedType = schema.type(typeIri);
        if (maybeChangedType.isEmpty()) {
            return;
        }
        TypeDefinition changedType = maybeChangedType.get();

        boolean deleted = changeEvent.change() == ObjectChangedEvent.Change.DELETED;
        Map<String, Value> changedProperties;
        if (deleted) {
            changedProperties = decodePropertiesSnapshot(payload);
        } else {
            Optional<ObjectSnapshot> found = readPort.find(scope, objectId);
            if (found.isEmpty()) {
                // Stale event: the object has since been deleted. No-op.
                return;
            }
            changedProperties = found.get().properties();
        }

        Set<ObjectId> candidates = new LinkedHashSet<>();
        if (!deleted) {
            // A deleted object transitioning is meaningless; only a live change makes the changed
            // object itself a candidate for its own OnChange transitions.
            candidates.add(objectId);
        }
        for (TypeDefinition candidateType : schema.types()) {
            Optional<StateMachineDefinition> maybeMachine = behaviour.stateMachine(candidateType.iri());
            if (maybeMachine.isEmpty()) {
                continue;
            }
            for (Transition transition : maybeMachine.get().transitions()) {
                if (!(transition.trigger() instanceof OnChangeTrigger onChangeTrigger)) {
                    continue;
                }
                for (String watchIri : onChangeTrigger.watchIris()) {
                    if (!isDeclaredOn(changedType, watchIri)) {
                        continue;
                    }
                    collectReferencedIds(changedProperties.get(watchIri), candidates);
                }
            }
        }

        for (ObjectId candidateId : candidates) {
            fireFirstAvailableOnChange(scope, behaviour, candidateId);
        }
    }

    @Override
    public void onTimerElapsed(Scope scope, StoreEventRow timerScheduled) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(timerScheduled, "timerScheduled must not be null");

        Map<String, Object> payload = timerScheduled.payload();
        ObjectId objectId = ObjectId.parse((String) payload.get("objectId"));
        String state = (String) payload.get("state");
        String transitionName = (String) payload.get("transitionName");

        Optional<ObjectSnapshot> found = readPort.find(scope, objectId);
        if (found.isEmpty()) {
            // The object is gone entirely: stale timer, no-op.
            return;
        }
        ObjectSnapshot object = found.get();
        if (object.state().isEmpty() || !object.state().get().equals(state)) {
            // The object left the state the timer was waiting in: stale timer, no-op. This
            // staleness check is the only guard: no cancel is recorded when a state is left.
            return;
        }
        commandPort.fireTransition(
                scope, new FireTransitionCommand(objectId, transitionName, FireTransitionCommand.Trigger.TIMER));
    }

    /**
     * Returns one {@link ResolvedTimer} for every {@code TimerTrigger} transition departing the event's {@code toState},
     * keyed by {@link TimerKey#of} and carrying the timer's own {@code after}. The event's {@code ontologyVersion}
     * stamp must match the active behaviour's version, so the timers come from the same ontology the event was
     * written under.
     *
     * @throws IllegalStateException if the event has no {@code ontologyVersion} stamp, or if the stamp differs from
     *     the active behaviour's ontology version
     */
    @Override
    public List<ResolvedTimer> timersStartedBy(Scope scope, TransitionFiredEvent transitionFired) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(transitionFired, "transitionFired must not be null");

        Map<String, Object> payload = transitionFired.payload();
        if (!(payload.get("ontologyVersion") instanceof Number stamp)) {
            throw new IllegalStateException(
                    "TransitionFired row " + transitionFired.id() + " has no ontologyVersion stamp");
        }
        Behaviour behaviour = behaviourSource.behaviour(scope.tenantId());
        if (behaviour.ontologyVersion().value() != stamp.intValue()) {
            throw new IllegalStateException("TransitionFired row " + transitionFired.id()
                    + " is stamped at ontology version " + stamp.intValue()
                    + " but the active behaviour is at "
                    + behaviour.ontologyVersion().value()
                    + "; the ontology changed before the row's timers were resolved");
        }

        ObjectId objectId = ObjectId.parse((String) payload.get("objectId"));
        String typeIri = (String) payload.get("typeIri");
        String toState = (String) payload.get("toState");

        Optional<StateMachineDefinition> maybeMachine = behaviour.stateMachine(typeIri);
        if (maybeMachine.isEmpty()) {
            return List.of();
        }
        List<ResolvedTimer> timers = new ArrayList<>();
        for (Transition candidate : maybeMachine.get().transitions()) {
            if (candidate.trigger() instanceof TimerTrigger timer
                    && candidate.fromStateIri().equals(toState)) {
                String timerKey = TimerKey.of(objectId, candidate.fromStateIri(), candidate.name());
                timers.add(new ResolvedTimer(timerKey, timer.after(), candidate.fromStateIri(), candidate.name()));
            }
        }
        return List.copyOf(timers);
    }

    /**
     * Reads {@code candidateId} fresh, and fires at most one {@code OnChange} transition departing its current state, in
     * declaration order, stopping at the first one the Store reports as committed. A candidate that no longer exists,
     * has no current state, resolves to a type with no state machine, or has no {@code OnChange} transition departing
     * its current state is a silent no-op.
     */
    private void fireFirstAvailableOnChange(Scope scope, Behaviour behaviour, ObjectId candidateId) {
        Optional<ObjectSnapshot> found = readPort.find(scope, candidateId);
        if (found.isEmpty() || found.get().state().isEmpty()) {
            return;
        }
        ObjectSnapshot candidate = found.get();
        Optional<StateMachineDefinition> maybeMachine = behaviour.stateMachine(candidate.typeIri());
        if (maybeMachine.isEmpty()) {
            return;
        }
        StateMachineDefinition machine = maybeMachine.get();
        String currentState = candidate.state().get();

        for (Transition transition : machine.transitions()) {
            if (!(transition.trigger() instanceof OnChangeTrigger)) {
                continue;
            }
            if (!transition.fromStateIri().equals(currentState)) {
                continue;
            }
            boolean committed = commandPort.fireTransition(
                    scope,
                    new FireTransitionCommand(candidateId, transition.name(), FireTransitionCommand.Trigger.ON_CHANGE));
            if (committed) {
                return;
            }
        }
    }

    /**
     * @return {@code true} if {@code propertyIri} appears in {@code type.properties()}. That list is already the full,
     *     flattened set attributed to {@code type}, including everything inherited from its supertypes, so no separate
     *     supertype walk is needed here.
     */
    private static boolean isDeclaredOn(TypeDefinition type, String propertyIri) {
        return type.properties().stream().anyMatch(property -> property.iri().equals(propertyIri));
    }

    /**
     * Collects the {@link ObjectId}s a relationship {@link Value} currently references into {@code out}: a bare
     * {@link ReferenceValue}, or every {@link ReferenceValue} element of a {@link ListValue}. A {@code null} value (the
     * property is absent on the changed object), a scalar value, or a {@link ListValue} of non-references contributes
     * no ids.
     */
    private static void collectReferencedIds(Value value, Set<ObjectId> out) {
        if (value instanceof ReferenceValue reference) {
            out.add(reference.target());
        } else if (value instanceof ListValue list) {
            for (Value element : list.values()) {
                if (element instanceof ReferenceValue reference) {
                    out.add(reference.target());
                }
            }
        }
    }

    /**
     * Decodes an {@code ObjectDeleted} payload's {@code properties} snapshot: a full-IRI-keyed map of tagged values,
     * captured at delete time because the object can no longer be read back once it is deleted.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Value> decodePropertiesSnapshot(Map<String, Object> payload) {
        Map<String, Object> raw = (Map<String, Object>) payload.get("properties");
        Map<String, Value> result = new HashMap<>();
        raw.forEach((iri, tagged) -> result.put(iri, PayloadValueCodec.fromPayload(tagged)));
        return result;
    }
}
