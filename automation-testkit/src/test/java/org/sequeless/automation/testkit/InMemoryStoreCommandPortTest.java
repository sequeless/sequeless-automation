package org.sequeless.automation.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.automation.api.CommandProvenance;
import org.sequeless.automation.api.CreateObjectCommand;
import org.sequeless.automation.api.FireTransitionCommand;
import org.sequeless.automation.api.SetPropertyCommand;
import org.sequeless.automation.api.StoreCommandRejectedException;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;

class InMemoryStoreCommandPortTest {

    private static final Scope SCOPE = new Scope(new TenantId("tenant-1"), new Principal("user-1", Set.of()));
    private static final String STATUS_IRI = "http://example.org/status";
    private static final String TASK_IRI = "http://example.org/Task";

    @Test
    void commandsOfEachKindAreRecordedInTheOrderReceived() {
        InMemoryStoreCommandPort port = new InMemoryStoreCommandPort();
        SetPropertyCommand firstSet = setProperty("first");
        SetPropertyCommand secondSet = setProperty("second");
        CreateObjectCommand create = createObject();
        FireTransitionCommand firstFire = fireTransition("activate");
        FireTransitionCommand secondFire = fireTransition("expire");

        port.setProperty(SCOPE, firstSet);
        port.createObject(SCOPE, create);
        port.setProperty(SCOPE, secondSet);
        port.fireTransition(SCOPE, firstFire);
        port.fireTransition(SCOPE, secondFire);

        assertThat(port.setProperties()).containsExactly(firstSet, secondSet);
        assertThat(port.createdObjects()).containsExactly(create);
        assertThat(port.fireRequests()).containsExactly(firstFire, secondFire);
    }

    @Test
    void theRecordedViewsAreUnmodifiable() {
        InMemoryStoreCommandPort port = new InMemoryStoreCommandPort();
        port.setProperty(SCOPE, setProperty("first"));

        assertThatThrownBy(() -> port.setProperties().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> port.createdObjects().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> port.fireRequests().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void aRepeatedSetPropertyWithTheSameProvenanceIsRecordedOnceAndDoesNotThrow() {
        InMemoryStoreCommandPort port = new InMemoryStoreCommandPort();
        SetPropertyCommand command = setProperty("first");

        port.setProperty(SCOPE, command);
        port.setProperty(SCOPE, command);

        assertThat(port.setProperties()).containsExactly(command);
    }

    @Test
    void aCreateObjectForAnObjectIdAlreadyCreatedIsRecordedOnceUnderAnyProvenance() {
        InMemoryStoreCommandPort port = new InMemoryStoreCommandPort();
        ObjectId objectId = new ObjectId(UUID.randomUUID());
        CreateObjectCommand first = createObject(objectId, freshProvenance());
        CreateObjectCommand repeatUnderAnotherClaim = createObject(objectId, freshProvenance());

        port.createObject(SCOPE, first);
        port.createObject(SCOPE, repeatUnderAnotherClaim);

        assertThat(port.createdObjects()).containsExactly(first);
    }

    @Test
    void aRejectedClaimDropsItsEarlierWritesSoARetryRecordsEachActionOnce() {
        InMemoryStoreCommandPort port = new InMemoryStoreCommandPort();
        UUID claim = UUID.randomUUID();
        SetPropertyCommand action0 = setProperty(provenance(claim, 0), "first");
        SetPropertyCommand action1 = setProperty(provenance(claim, 1), "second");
        port.setProperty(SCOPE, action0);
        port.rejectNext(1);

        assertThatExceptionOfType(StoreCommandRejectedException.class)
                .isThrownBy(() -> port.setProperty(SCOPE, action1));
        assertThat(port.setProperties()).isEmpty();

        port.setProperty(SCOPE, action0);
        port.setProperty(SCOPE, action1);

        assertThat(port.setProperties()).containsExactly(action0, action1);
    }

    @Test
    void aRollbackLeavesTheWritesOfOtherSourceEventsAlone() {
        InMemoryStoreCommandPort port = new InMemoryStoreCommandPort();
        SetPropertyCommand otherClaim = setProperty("other");
        UUID claim = UUID.randomUUID();
        SetPropertyCommand rolledBack = setProperty(provenance(claim, 0), "rolled-back");
        SetPropertyCommand rejected = setProperty(provenance(claim, 1), "rejected");
        port.setProperty(SCOPE, otherClaim);
        port.setProperty(SCOPE, rolledBack);
        port.rejectNext(1);

        assertThatExceptionOfType(StoreCommandRejectedException.class)
                .isThrownBy(() -> port.setProperty(SCOPE, rejected));

        assertThat(port.setProperties()).containsExactly(otherClaim);
    }

    @Test
    void rejectNextMakesTheNextSetPropertyThrowAndRecordsNothing() {
        InMemoryStoreCommandPort port = new InMemoryStoreCommandPort();
        port.rejectNext(1);

        assertThatExceptionOfType(StoreCommandRejectedException.class)
                .isThrownBy(() -> port.setProperty(SCOPE, setProperty("rejected")));
        assertThat(port.setProperties()).isEmpty();
    }

    @Test
    void rejectNextMakesTheNextCreateObjectThrowAndRecordsNothing() {
        InMemoryStoreCommandPort port = new InMemoryStoreCommandPort();
        port.rejectNext(1);

        assertThatExceptionOfType(StoreCommandRejectedException.class)
                .isThrownBy(() -> port.createObject(SCOPE, createObject()));
        assertThat(port.createdObjects()).isEmpty();
    }

    @Test
    void rejectNextCountsWriteCommandsAndThenStopsRejecting() {
        InMemoryStoreCommandPort port = new InMemoryStoreCommandPort();
        SetPropertyCommand accepted = setProperty("accepted");
        port.rejectNext(2);

        assertThatExceptionOfType(StoreCommandRejectedException.class)
                .isThrownBy(() -> port.setProperty(SCOPE, setProperty("rejected-1")));
        assertThatExceptionOfType(StoreCommandRejectedException.class)
                .isThrownBy(() -> port.createObject(SCOPE, createObject()));
        port.setProperty(SCOPE, accepted);

        assertThat(port.setProperties()).containsExactly(accepted);
        assertThat(port.createdObjects()).isEmpty();
    }

    @Test
    void rejectNextDoesNotAffectFireTransition() {
        InMemoryStoreCommandPort port = new InMemoryStoreCommandPort();
        FireTransitionCommand fire = fireTransition("activate");
        port.rejectNext(1);

        assertThat(port.fireTransition(SCOPE, fire)).isTrue();
        assertThat(port.fireRequests()).containsExactly(fire);
    }

    @Test
    void aPendingRejectionSurvivesAFireTransition() {
        InMemoryStoreCommandPort port = new InMemoryStoreCommandPort();
        FireTransitionCommand fire = fireTransition("activate");
        port.rejectNext(1);

        port.fireTransition(SCOPE, fire);

        assertThat(port.fireRequests()).containsExactly(fire);
        assertThatExceptionOfType(StoreCommandRejectedException.class)
                .isThrownBy(() -> port.setProperty(SCOPE, setProperty("rejected")));
        assertThat(port.setProperties()).isEmpty();
    }

    @Test
    void rejectNextRejectsANegativeCount() {
        InMemoryStoreCommandPort port = new InMemoryStoreCommandPort();

        assertThatIllegalArgumentException().isThrownBy(() -> port.rejectNext(-1));
    }

    @Test
    void fireTransitionReturnsTrueByDefaultAndStillRecordsTheRequest() {
        InMemoryStoreCommandPort port = new InMemoryStoreCommandPort();
        FireTransitionCommand fire = fireTransition("activate");

        assertThat(port.fireTransition(SCOPE, fire)).isTrue();
        assertThat(port.fireRequests()).containsExactly(fire);
    }

    @Test
    void setFireResultFalseMakesFireTransitionReturnFalseAndStillRecordsTheRequest() {
        InMemoryStoreCommandPort port = new InMemoryStoreCommandPort();
        FireTransitionCommand fire = fireTransition("activate");
        port.setFireResult(false);

        assertThat(port.fireTransition(SCOPE, fire)).isFalse();
        assertThat(port.fireRequests()).containsExactly(fire);
    }

    /** A provenance for a fresh claim's first action, so commands built with it do not collide. */
    private static CommandProvenance freshProvenance() {
        return provenance(UUID.randomUUID(), 0);
    }

    private static CommandProvenance provenance(UUID sourceEventId, int actionIndex) {
        return new CommandProvenance(sourceEventId, actionIndex, 1);
    }

    private static SetPropertyCommand setProperty(String value) {
        return setProperty(freshProvenance(), value);
    }

    private static SetPropertyCommand setProperty(CommandProvenance provenance, String value) {
        return new SetPropertyCommand(new ObjectId(UUID.randomUUID()), STATUS_IRI, Value.text(value), provenance);
    }

    private static CreateObjectCommand createObject() {
        return createObject(new ObjectId(UUID.randomUUID()), freshProvenance());
    }

    private static CreateObjectCommand createObject(ObjectId objectId, CommandProvenance provenance) {
        return new CreateObjectCommand(objectId, TASK_IRI, Map.of(STATUS_IRI, Value.text("new")), provenance);
    }

    private static FireTransitionCommand fireTransition(String transitionName) {
        return new FireTransitionCommand(
                new ObjectId(UUID.randomUUID()), transitionName, FireTransitionCommand.Trigger.ON_CHANGE);
    }
}
