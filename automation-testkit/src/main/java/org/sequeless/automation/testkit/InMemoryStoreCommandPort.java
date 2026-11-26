package org.sequeless.automation.testkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.sequeless.automation.api.CommandProvenance;
import org.sequeless.automation.api.CreateObjectCommand;
import org.sequeless.automation.api.FireTransitionCommand;
import org.sequeless.automation.api.SetPropertyCommand;
import org.sequeless.automation.api.StoreCommandPort;
import org.sequeless.automation.api.StoreCommandRejectedException;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.values.ObjectId;

/**
 * An in-memory {@link StoreCommandPort} test double. It records the commands it accepts and exposes each kind
 * through {@link #setProperties()}, {@link #createdObjects()} and {@link #fireRequests()}, each in the order it
 * was received.
 *
 * <p><b>What this fake models.</b>
 *
 * <ul>
 *   <li><em>Idempotent repeats.</em> A {@code setProperty} or {@code createObject} whose {@link
 *       org.sequeless.automation.api.CommandProvenance} (its {@code sourceEventId} and {@code actionIndex}) is
 *       already recorded is a no-op. It is not recorded again, it does not throw, and it does not use up a
 *       pending {@link #rejectNext} rejection. A {@code createObject} whose {@code objectId} is already recorded
 *       is a no-op too, under any provenance, because the Store's create is idempotent on the derived id.
 *   <li><em>Claim rollback on rejection.</em> One claim is one source event. When {@link #rejectNext} makes a
 *       write throw {@link StoreCommandRejectedException}, every write already recorded with the same {@code
 *       sourceEventId} is dropped, and the rejected command is not recorded. A dropped write releases its key,
 *       so a retry of the claim runs its actions again and records each one once.
 * </ul>
 *
 * <p><b>What this fake does not model.</b>
 *
 * <ul>
 *   <li><em>No real transaction.</em> The rollback is only the dropping of recorded writes. Nothing is committed
 *       and no other state is undone.
 *   <li><em>No concurrency.</em> Nothing is synchronised, and the fake is not thread-safe.
 *   <li><em>No Store validation or authorization.</em> Every command the fake accepts is treated as valid.
 *   <li><em>No provenance for {@link #fireTransition}.</em> A {@link
 *       org.sequeless.automation.api.FireTransitionCommand} carries no provenance, so the fake cannot key a
 *       repeat or attribute a fire to a claim. Every fire is recorded, and a rejection does not drop an earlier
 *       fire.
 * </ul>
 */
public final class InMemoryStoreCommandPort implements StoreCommandPort {

    private final List<SetPropertyCommand> recordedSetProperties = new ArrayList<>();
    private final List<CreateObjectCommand> recordedCreates = new ArrayList<>();
    private final List<FireTransitionCommand> recordedFires = new ArrayList<>();
    private int rejectionsPending;
    private boolean fireResult = true;

    /**
     * Makes the next {@code count} write commands throw {@link StoreCommandRejectedException}. The writes are
     * {@code setProperty} and {@code createObject}, counted together. A repeat is a no-op, not a write for this
     * purpose: it neither throws nor uses up a rejection. A rejected write rolls back its claim (see the class
     * javadoc) and is not recorded itself. This replaces any count still pending from an earlier call, and
     * {@code 0} clears it.
     *
     * @param count how many of the next write commands to reject; must not be negative
     * @throws IllegalArgumentException if {@code count} is negative
     */
    public void rejectNext(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
        rejectionsPending = count;
    }

    /**
     * Sets what {@link #fireTransition} returns from now on. The default is {@code true}, which models a
     * transition that committed.
     *
     * @param fireResult {@code true} if the transition should commit, {@code false} if it is not available
     */
    public void setFireResult(boolean fireResult) {
        this.fireResult = fireResult;
    }

    /**
     * Records the command unless its provenance is already recorded, in which case the call is a no-op.
     * A rejection drops the claim's earlier writes and throws without recording the command.
     */
    @Override
    public void setProperty(Scope scope, SetPropertyCommand command) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(command, "command must not be null");
        if (alreadyRecorded(command.provenance())) {
            return;
        }
        rejectIfPending("setProperty", command.provenance());
        recordedSetProperties.add(command);
    }

    /**
     * Records the command unless its provenance or its {@code objectId} is already recorded, in which case the
     * call is a no-op. A rejection drops the claim's earlier writes and throws without recording the command.
     */
    @Override
    public void createObject(Scope scope, CreateObjectCommand command) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(command, "command must not be null");
        if (alreadyRecorded(command.provenance()) || objectAlreadyCreated(command.objectId())) {
            return;
        }
        rejectIfPending("createObject", command.provenance());
        recordedCreates.add(command);
    }

    /**
     * Records the command, then returns the configured fire result. It is not subject to {@link #rejectNext}.
     * It is recorded whatever the result, because the request was made.
     */
    @Override
    public boolean fireTransition(Scope scope, FireTransitionCommand command) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(command, "command must not be null");
        recordedFires.add(command);
        return fireResult;
    }

    /**
     * @return the {@link SetPropertyCommand}s recorded so far, in the order they were received; an
     *     unmodifiable snapshot
     */
    public List<SetPropertyCommand> setProperties() {
        return List.copyOf(recordedSetProperties);
    }

    /**
     * @return the {@link CreateObjectCommand}s recorded so far, in the order they were received; an
     *     unmodifiable snapshot
     */
    public List<CreateObjectCommand> createdObjects() {
        return List.copyOf(recordedCreates);
    }

    /**
     * @return the {@link FireTransitionCommand}s recorded so far, in the order they were received; an
     *     unmodifiable snapshot
     */
    public List<FireTransitionCommand> fireRequests() {
        return List.copyOf(recordedFires);
    }

    /**
     * A write is a repeat when a recorded write of either kind has the same source event and action index. The
     * keys are the recorded writes themselves, so dropping a write on rollback releases its key.
     */
    private boolean alreadyRecorded(CommandProvenance provenance) {
        return Stream.concat(
                        recordedSetProperties.stream().map(SetPropertyCommand::provenance),
                        recordedCreates.stream().map(CreateObjectCommand::provenance))
                .anyMatch(recorded -> recorded.sourceEventId().equals(provenance.sourceEventId())
                        && recorded.actionIndex() == provenance.actionIndex());
    }

    private boolean objectAlreadyCreated(ObjectId objectId) {
        return recordedCreates.stream().anyMatch(command -> command.objectId().equals(objectId));
    }

    private void rejectIfPending(String operation, CommandProvenance provenance) {
        if (rejectionsPending > 0) {
            rejectionsPending--;
            rollBackClaim(provenance.sourceEventId());
            throw new StoreCommandRejectedException("store rejected " + operation + " (test double)");
        }
    }

    /** Drops every write recorded under the claim, because one claim is one source event and rolls back as a unit. */
    private void rollBackClaim(UUID sourceEventId) {
        recordedSetProperties.removeIf(
                command -> command.provenance().sourceEventId().equals(sourceEventId));
        recordedCreates.removeIf(command -> command.provenance().sourceEventId().equals(sourceEventId));
    }
}
