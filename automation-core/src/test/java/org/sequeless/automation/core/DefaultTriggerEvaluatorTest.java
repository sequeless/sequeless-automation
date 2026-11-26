package org.sequeless.automation.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.automation.api.FireTransitionCommand;
import org.sequeless.automation.api.ObjectChangedEvent;
import org.sequeless.automation.api.ObjectReadDeniedException;
import org.sequeless.automation.api.ObjectSnapshot;
import org.sequeless.automation.api.ResolvedTimer;
import org.sequeless.automation.api.StoreEventRow;
import org.sequeless.automation.api.TimerKey;
import org.sequeless.automation.api.TimerScheduledEntry;
import org.sequeless.automation.api.TransitionFiredEvent;
import org.sequeless.automation.testkit.InMemoryObjectReadPort;
import org.sequeless.automation.testkit.InMemoryStoreCommandPort;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;
import org.sequeless.ontology.behaviour.api.Behaviour;
import org.sequeless.ontology.behaviour.api.ExternalSignalTrigger;
import org.sequeless.ontology.behaviour.api.OnChangeTrigger;
import org.sequeless.ontology.behaviour.api.State;
import org.sequeless.ontology.behaviour.api.StateMachineDefinition;
import org.sequeless.ontology.behaviour.api.TimerTrigger;
import org.sequeless.ontology.behaviour.api.Transition;
import org.sequeless.ontology.schema.api.Cardinality;
import org.sequeless.ontology.schema.api.DisplayHints;
import org.sequeless.ontology.schema.api.OntologyVersion;
import org.sequeless.ontology.schema.api.RelationshipDefinition;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.api.TypeDefinition;

/**
 * Unit tests for {@link DefaultTriggerEvaluator}, built against a hand-written {@code Project} (an {@code OnChange}
 * transition {@code autoClose}, a {@code Timer} transition {@code expireHold} and an {@code ExternalSignal} transition
 * {@code reopen}) and a {@code Task} type whose {@code belongsToProject} relationship is the watched property.
 *
 * <p>The evaluator's transitions are asserted as the {@link FireTransitionCommand}s the in-memory command port
 * recorded. The fake does not apply them, so a test that needs a second candidate seeds it explicitly.
 */
class DefaultTriggerEvaluatorTest {

    private static final String NS = "https://sequeless.test/trigger-evaluator#";

    private static final String PROJECT_IRI = NS + "Project";
    private static final String TASK_IRI = NS + "Task";
    private static final String BELONGS_TO_PROJECT_IRI = NS + "belongsToProject";

    private static final String ACTIVE_IRI = NS + "Active";
    private static final String CLOSED_IRI = NS + "Closed";
    private static final String ON_HOLD_IRI = NS + "OnHold";

    private static final State ACTIVE = new State(ACTIVE_IRI, "Active", 0);
    private static final State CLOSED = new State(CLOSED_IRI, "Closed", 1);
    private static final State ON_HOLD = new State(ON_HOLD_IRI, "OnHold", 2);

    private static final RelationshipDefinition BELONGS_TO_PROJECT = new RelationshipDefinition(
            BELONGS_TO_PROJECT_IRI,
            "belongsToProject",
            Cardinality.optional(),
            false,
            false,
            false,
            false,
            false,
            DisplayHints.none(),
            PROJECT_IRI,
            Optional.empty(),
            false);

    /** {@code OnChange}, unguarded: fires whenever any watched {@code Task} changes. */
    private static final Transition AUTO_CLOSE = new Transition(
            "autoClose",
            ACTIVE_IRI,
            CLOSED_IRI,
            new OnChangeTrigger(List.of(BELONGS_TO_PROJECT_IRI)),
            Optional.empty(),
            Optional.empty());

    private static final Transition EXPIRE_HOLD = new Transition(
            "expireHold",
            ON_HOLD_IRI,
            CLOSED_IRI,
            new TimerTrigger(Duration.ofHours(72)),
            Optional.empty(),
            Optional.empty());

    private static final Transition REOPEN = new Transition(
            "reopen", CLOSED_IRI, ACTIVE_IRI, new ExternalSignalTrigger("reopen"), Optional.empty(), Optional.empty());

    private static final StateMachineDefinition PROJECT_LIFECYCLE = new StateMachineDefinition(
            NS + "ProjectLifecycle",
            List.of(ACTIVE, CLOSED, ON_HOLD),
            ACTIVE,
            List.of(AUTO_CLOSE, EXPIRE_HOLD, REOPEN));

    /** Two {@code OnChange} transitions departing the same state, in declaration order. */
    private static final Transition FIRST_ON_CHANGE = new Transition(
            "first",
            ACTIVE_IRI,
            CLOSED_IRI,
            new OnChangeTrigger(List.of(BELONGS_TO_PROJECT_IRI)),
            Optional.empty(),
            Optional.empty());

    private static final Transition SECOND_ON_CHANGE = new Transition(
            "second",
            ACTIVE_IRI,
            ON_HOLD_IRI,
            new OnChangeTrigger(List.of(BELONGS_TO_PROJECT_IRI)),
            Optional.empty(),
            Optional.empty());

    private static final StateMachineDefinition ORDERED_LIFECYCLE = new StateMachineDefinition(
            NS + "OrderedLifecycle",
            List.of(ACTIVE, CLOSED, ON_HOLD),
            ACTIVE,
            List.of(FIRST_ON_CHANGE, SECOND_ON_CHANGE));

    private static final TypeDefinition PROJECT =
            new TypeDefinition(PROJECT_IRI, "Project", List.of(), List.of(), DisplayHints.none(), false);

    private static final TypeDefinition TASK =
            new TypeDefinition(TASK_IRI, "Task", List.of(), List.of(BELONGS_TO_PROJECT), DisplayHints.none(), false);

    private static final Schema SCHEMA = new Schema(
            new OntologyVersion(1),
            NS.substring(0, NS.length() - 1),
            Optional.empty(),
            Map.of(),
            List.of(PROJECT, TASK));

    /** The state machine governs {@code Project}; no other type has one. */
    private static final Behaviour BEHAVIOUR =
            new Behaviour(new OntologyVersion(1), Map.of(PROJECT_IRI, PROJECT_LIFECYCLE), Map.of());

    private static final Behaviour ORDERED_BEHAVIOUR =
            new Behaviour(new OntologyVersion(1), Map.of(PROJECT_IRI, ORDERED_LIFECYCLE), Map.of());

    private static final Instant NOW = Instant.parse("2026-09-19T00:00:00Z");

    private static final Scope ALICE = new Scope(new TenantId("acme"), new Principal("alice", Set.of("member")));

    // --- onChange ---

    @Test
    void onChangeFiresTheWatchingProjectsOnChangeTransition() {
        ObjectId projectId = ObjectId.random();
        ObjectId taskId = ObjectId.random();
        InMemoryObjectReadPort readPort = new InMemoryObjectReadPort();
        readPort.put(projectId, project(ACTIVE_IRI));
        readPort.put(taskId, task(projectId));
        InMemoryStoreCommandPort commandPort = new InMemoryStoreCommandPort();

        evaluator(BEHAVIOUR, readPort, commandPort).onChange(ALICE, objectUpdatedEvent(taskId));

        assertThat(commandPort.fireRequests())
                .containsExactly(
                        new FireTransitionCommand(projectId, "autoClose", FireTransitionCommand.Trigger.ON_CHANGE));
    }

    @Test
    void onChangeForAnObjectDeletedEventResolvesTheWatchFromThePayloadSnapshotNotFromFind() {
        ObjectId projectId = ObjectId.random();
        ObjectId taskId = ObjectId.random();
        InMemoryObjectReadPort readPort = new InMemoryObjectReadPort();
        readPort.put(projectId, project(ACTIVE_IRI));
        // Deliberately do NOT seed the task: the watch is resolved from the ObjectDeleted payload's embedded
        // properties snapshot, not from a read of the deleted task.
        InMemoryStoreCommandPort commandPort = new InMemoryStoreCommandPort();

        evaluator(BEHAVIOUR, readPort, commandPort).onChange(ALICE, objectDeletedEvent(taskId, projectId));

        assertThat(commandPort.fireRequests())
                .containsExactly(
                        new FireTransitionCommand(projectId, "autoClose", FireTransitionCommand.Trigger.ON_CHANGE));
    }

    @Test
    void onChangeConsidersTheChangedObjectItselfACandidateForItsOwnOnChangeTransitions() {
        ObjectId projectId = ObjectId.random();
        InMemoryObjectReadPort readPort = new InMemoryObjectReadPort();
        readPort.put(projectId, project(ACTIVE_IRI));
        InMemoryStoreCommandPort commandPort = new InMemoryStoreCommandPort();
        ObjectChangedEvent projectChanged = new ObjectChangedEvent(
                UUID.randomUUID(),
                ObjectChangedEvent.Change.UPDATED,
                Map.of("objectId", projectId.value().toString(), "typeIri", PROJECT_IRI),
                NOW);

        evaluator(BEHAVIOUR, readPort, commandPort).onChange(ALICE, projectChanged);

        // AUTO_CLOSE departs Active with no guard, so the changed Project itself is a candidate and fires directly.
        assertThat(commandPort.fireRequests())
                .containsExactly(
                        new FireTransitionCommand(projectId, "autoClose", FireTransitionCommand.Trigger.ON_CHANGE));
    }

    @Test
    void onChangeFiresAtMostOneTransitionPerCandidate() {
        ObjectId projectId = ObjectId.random();
        ObjectId taskId = ObjectId.random();
        InMemoryObjectReadPort readPort = new InMemoryObjectReadPort();
        readPort.put(projectId, project(ACTIVE_IRI));
        readPort.put(taskId, task(projectId));
        InMemoryStoreCommandPort commandPort = new InMemoryStoreCommandPort();

        evaluator(BEHAVIOUR, readPort, commandPort).onChange(ALICE, objectUpdatedEvent(taskId));

        // The changed Task is a candidate too, but it has no state, so only the Project fires, once.
        assertThat(commandPort.fireRequests()).hasSize(1);
    }

    @Test
    void onChangeIgnoresAWatchIriNotDeclaredOnTheChangedObjectsType() {
        ObjectId projectId = ObjectId.random();
        InMemoryObjectReadPort readPort = new InMemoryObjectReadPort();
        readPort.put(projectId, project(ACTIVE_IRI));
        InMemoryStoreCommandPort commandPort = new InMemoryStoreCommandPort();
        // A type the schema does not declare: no candidate, and no fire.
        ObjectChangedEvent unrelatedChange = new ObjectChangedEvent(
                UUID.randomUUID(),
                ObjectChangedEvent.Change.UPDATED,
                Map.of("objectId", ObjectId.random().value().toString(), "typeIri", "urn:unknown:Type"),
                NOW);

        evaluator(BEHAVIOUR, readPort, commandPort).onChange(ALICE, unrelatedChange);

        assertThat(commandPort.fireRequests()).isEmpty();
    }

    @Test
    void onChangeIsANoOpWhenTheChangedObjectIsAbsent() {
        ObjectId taskId = ObjectId.random();
        InMemoryStoreCommandPort commandPort = new InMemoryStoreCommandPort();

        evaluator(BEHAVIOUR, new InMemoryObjectReadPort(), commandPort).onChange(ALICE, objectUpdatedEvent(taskId));

        assertThat(commandPort.fireRequests()).isEmpty();
    }

    @Test
    void onChangeFiresNothingWhenNoOnChangeTransitionDepartsTheCurrentState() {
        ObjectId projectId = ObjectId.random();
        ObjectId taskId = ObjectId.random();
        InMemoryObjectReadPort readPort = new InMemoryObjectReadPort();
        readPort.put(projectId, project(CLOSED_IRI)); // AUTO_CLOSE departs Active only
        readPort.put(taskId, task(projectId));
        InMemoryStoreCommandPort commandPort = new InMemoryStoreCommandPort();

        evaluator(BEHAVIOUR, readPort, commandPort).onChange(ALICE, objectUpdatedEvent(taskId));

        assertThat(commandPort.fireRequests()).isEmpty();
    }

    @Test
    void onChangeTriesOnChangeTransitionsInDeclarationOrderAndStopsAtTheFirstThatCommits() {
        ObjectId projectId = ObjectId.random();
        ObjectId taskId = ObjectId.random();
        InMemoryObjectReadPort readPort = new InMemoryObjectReadPort();
        readPort.put(projectId, project(ACTIVE_IRI));
        readPort.put(taskId, task(projectId));
        InMemoryStoreCommandPort commandPort = new InMemoryStoreCommandPort(); // every fire commits

        evaluator(ORDERED_BEHAVIOUR, readPort, commandPort).onChange(ALICE, objectUpdatedEvent(taskId));

        assertThat(commandPort.fireRequests())
                .containsExactly(
                        new FireTransitionCommand(projectId, "first", FireTransitionCommand.Trigger.ON_CHANGE));
    }

    @Test
    void onChangeTriesEveryOnChangeTransitionInOrderWhenNoneCommits() {
        ObjectId projectId = ObjectId.random();
        ObjectId taskId = ObjectId.random();
        InMemoryObjectReadPort readPort = new InMemoryObjectReadPort();
        readPort.put(projectId, project(ACTIVE_IRI));
        readPort.put(taskId, task(projectId));
        InMemoryStoreCommandPort commandPort = new InMemoryStoreCommandPort();
        commandPort.setFireResult(false); // no transition is available

        evaluator(ORDERED_BEHAVIOUR, readPort, commandPort).onChange(ALICE, objectUpdatedEvent(taskId));

        assertThat(commandPort.fireRequests())
                .containsExactly(
                        new FireTransitionCommand(projectId, "first", FireTransitionCommand.Trigger.ON_CHANGE),
                        new FireTransitionCommand(projectId, "second", FireTransitionCommand.Trigger.ON_CHANGE));
    }

    @Test
    void onChangePropagatesADeniedReadAndFiresNothing() {
        ObjectId projectId = ObjectId.random();
        ObjectId taskId = ObjectId.random();
        InMemoryObjectReadPort readPort = new InMemoryObjectReadPort();
        readPort.put(projectId, project(ACTIVE_IRI));
        readPort.deny(taskId);
        InMemoryStoreCommandPort commandPort = new InMemoryStoreCommandPort();

        assertThatThrownBy(
                        () -> evaluator(BEHAVIOUR, readPort, commandPort).onChange(ALICE, objectUpdatedEvent(taskId)))
                .isInstanceOf(ObjectReadDeniedException.class);
        assertThat(commandPort.fireRequests()).isEmpty();
    }

    // --- onTimerElapsed ---

    @Test
    void onTimerElapsedFiresWhenTheObjectIsStillInTheWaitedOnState() {
        ObjectId projectId = ObjectId.random();
        InMemoryObjectReadPort readPort = new InMemoryObjectReadPort();
        readPort.put(projectId, project(ON_HOLD_IRI));
        InMemoryStoreCommandPort commandPort = new InMemoryStoreCommandPort();

        evaluator(BEHAVIOUR, readPort, commandPort)
                .onTimerElapsed(ALICE, timerScheduledEntry(projectId, ON_HOLD_IRI, "expireHold"));

        assertThat(commandPort.fireRequests())
                .containsExactly(
                        new FireTransitionCommand(projectId, "expireHold", FireTransitionCommand.Trigger.TIMER));
    }

    @Test
    void onTimerElapsedIsANoOpWhenTheObjectHasLeftTheWaitedOnState() {
        ObjectId projectId = ObjectId.random();
        InMemoryObjectReadPort readPort = new InMemoryObjectReadPort();
        readPort.put(projectId, project(ACTIVE_IRI)); // left OnHold before the timer fired
        InMemoryStoreCommandPort commandPort = new InMemoryStoreCommandPort();

        evaluator(BEHAVIOUR, readPort, commandPort)
                .onTimerElapsed(ALICE, timerScheduledEntry(projectId, ON_HOLD_IRI, "expireHold"));

        assertThat(commandPort.fireRequests()).isEmpty();
    }

    @Test
    void onTimerElapsedIsANoOpWhenTheObjectHasNoState() {
        ObjectId projectId = ObjectId.random();
        InMemoryObjectReadPort readPort = new InMemoryObjectReadPort();
        readPort.put(projectId, new ObjectSnapshot(PROJECT_IRI, Optional.empty(), Map.of()));
        InMemoryStoreCommandPort commandPort = new InMemoryStoreCommandPort();

        evaluator(BEHAVIOUR, readPort, commandPort)
                .onTimerElapsed(ALICE, timerScheduledEntry(projectId, ON_HOLD_IRI, "expireHold"));

        assertThat(commandPort.fireRequests()).isEmpty();
    }

    @Test
    void onTimerElapsedIsANoOpWhenTheObjectIsGone() {
        InMemoryStoreCommandPort commandPort = new InMemoryStoreCommandPort();

        evaluator(BEHAVIOUR, new InMemoryObjectReadPort(), commandPort)
                .onTimerElapsed(ALICE, timerScheduledEntry(ObjectId.random(), ON_HOLD_IRI, "expireHold"));

        assertThat(commandPort.fireRequests()).isEmpty();
    }

    // --- timersStartedBy ---

    @Test
    void timersStartedByResolvesTheTimerTheToStateStartsWithItsKeyDurationAndState() {
        ObjectId projectId = ObjectId.random();
        DefaultTriggerEvaluator evaluator =
                evaluator(BEHAVIOUR, new InMemoryObjectReadPort(), new InMemoryStoreCommandPort());

        List<ResolvedTimer> timers = evaluator.timersStartedBy(ALICE, transitionFiredEvent(projectId, ON_HOLD_IRI, 1));

        assertThat(timers)
                .containsExactly(new ResolvedTimer(
                        projectId.value() + "|" + ON_HOLD_IRI + "|expireHold",
                        Duration.ofHours(72),
                        ON_HOLD_IRI,
                        "expireHold"));
    }

    @Test
    void timersStartedByIsEmptyWhenTheToStateHasNoTimerTransitionDepartingIt() {
        DefaultTriggerEvaluator evaluator =
                evaluator(BEHAVIOUR, new InMemoryObjectReadPort(), new InMemoryStoreCommandPort());

        List<ResolvedTimer> timers =
                evaluator.timersStartedBy(ALICE, transitionFiredEvent(ObjectId.random(), ACTIVE_IRI, 1));

        assertThat(timers).isEmpty();
    }

    @Test
    void timersStartedByThrowsWhenTheStampDiffersFromTheActiveBehaviourVersion() {
        DefaultTriggerEvaluator evaluator =
                evaluator(BEHAVIOUR, new InMemoryObjectReadPort(), new InMemoryStoreCommandPort());
        TransitionFiredEvent staleRow = transitionFiredEvent(ObjectId.random(), ON_HOLD_IRI, 2);

        assertThatThrownBy(() -> evaluator.timersStartedBy(ALICE, staleRow))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stamped at ontology version 2");
    }

    @Test
    void timersStartedByThrowsWhenTheRowHasNoOntologyVersionStamp() {
        DefaultTriggerEvaluator evaluator =
                evaluator(BEHAVIOUR, new InMemoryObjectReadPort(), new InMemoryStoreCommandPort());
        TransitionFiredEvent unstampedRow = transitionFiredEvent(ObjectId.random(), ON_HOLD_IRI, null);

        assertThatThrownBy(() -> evaluator.timersStartedBy(ALICE, unstampedRow))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has no ontologyVersion stamp");
    }

    // --- helpers ---

    private static DefaultTriggerEvaluator evaluator(
            Behaviour behaviour, InMemoryObjectReadPort readPort, InMemoryStoreCommandPort commandPort) {
        return new DefaultTriggerEvaluator(tenant -> SCHEMA, tenant -> behaviour, readPort, commandPort);
    }

    private static ObjectSnapshot project(String state) {
        return new ObjectSnapshot(PROJECT_IRI, Optional.of(state), Map.of());
    }

    private static ObjectSnapshot task(ObjectId belongsToProjectId) {
        Map<String, Value> properties = Map.of(BELONGS_TO_PROJECT_IRI, Value.ref(belongsToProjectId));
        return new ObjectSnapshot(TASK_IRI, Optional.empty(), properties);
    }

    private static ObjectChangedEvent objectUpdatedEvent(ObjectId taskId) {
        return new ObjectChangedEvent(
                UUID.randomUUID(),
                ObjectChangedEvent.Change.UPDATED,
                Map.of("objectId", taskId.value().toString(), "typeIri", TASK_IRI),
                NOW);
    }

    private static ObjectChangedEvent objectDeletedEvent(ObjectId taskId, ObjectId belongsToProjectId) {
        Map<String, Object> properties = Map.of(
                BELONGS_TO_PROJECT_IRI, Map.of("ref", belongsToProjectId.value().toString()));
        return new ObjectChangedEvent(
                UUID.randomUUID(),
                ObjectChangedEvent.Change.DELETED,
                Map.of("objectId", taskId.value().toString(), "typeIri", TASK_IRI, "properties", properties),
                NOW);
    }

    private static StoreEventRow timerScheduledEntry(ObjectId projectId, String state, String transitionName) {
        ResolvedTimer timer = new ResolvedTimer(
                TimerKey.of(projectId, state, transitionName), Duration.ofHours(72), state, transitionName);
        return TimerScheduledEntry.of(ALICE, projectId, PROJECT_IRI, 1, timer, NOW);
    }

    /** A {@code TransitionFired} event moving {@code projectId} into {@code toState}; a null stamp omits the key. */
    private static TransitionFiredEvent transitionFiredEvent(
            ObjectId projectId, String toState, Integer ontologyVersion) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("objectId", projectId.value().toString());
        payload.put("typeIri", PROJECT_IRI);
        payload.put("toState", toState);
        payload.put("transitionName", "move");
        if (ontologyVersion != null) {
            payload.put("ontologyVersion", ontologyVersion);
        }
        return new TransitionFiredEvent(UUID.randomUUID(), payload, NOW);
    }
}
