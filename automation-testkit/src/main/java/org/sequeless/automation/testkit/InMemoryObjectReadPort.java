package org.sequeless.automation.testkit;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.sequeless.automation.api.ObjectReadDeniedException;
import org.sequeless.automation.api.ObjectReadPort;
import org.sequeless.automation.api.ObjectSnapshot;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.values.ObjectId;

/**
 * An in-memory {@link ObjectReadPort} test double. A test seeds the objects it wants visible with
 * {@link #put} and marks the objects a read must be refused for with {@link #deny}.
 *
 * <p>A denied object throws {@link ObjectReadDeniedException} whether or not it was seeded, so the
 * denial does not reveal whether the object exists, as the port contract requires. The denial applies
 * to every principal. The fake holds one flat map and does not model tenant or principal isolation,
 * so the {@link Scope} is checked for null and otherwise ignored.
 *
 * <p>This is a test double and is not thread-safe.
 */
public final class InMemoryObjectReadPort implements ObjectReadPort {

    private final Map<ObjectId, ObjectSnapshot> snapshots = new HashMap<>();
    private final Set<ObjectId> denied = new HashSet<>();

    /**
     * Seeds the snapshot {@link #find} returns for {@code objectId}, replacing any seeded before.
     *
     * @param objectId the object to seed; must not be {@code null}
     * @param snapshot the snapshot to return for it; must not be {@code null}
     */
    public void put(ObjectId objectId, ObjectSnapshot snapshot) {
        snapshots.put(
                Objects.requireNonNull(objectId, "objectId must not be null"),
                Objects.requireNonNull(snapshot, "snapshot must not be null"));
    }

    /**
     * Marks {@code objectId} as denied: every later {@link #find} for it throws {@link
     * ObjectReadDeniedException}, whether or not the object was seeded.
     *
     * @param objectId the object to deny reads of; must not be {@code null}
     */
    public void deny(ObjectId objectId) {
        denied.add(Objects.requireNonNull(objectId, "objectId must not be null"));
    }

    /**
     * Reads the seeded snapshot of {@code objectId}, unless the object is denied.
     *
     * @param scope the scope the read is made under; must not be {@code null}, otherwise ignored
     * @param objectId the object to read; must not be {@code null}
     * @return the seeded snapshot, or empty if the object was never seeded
     * @throws ObjectReadDeniedException if the object is marked denied
     */
    @Override
    public Optional<ObjectSnapshot> find(Scope scope, ObjectId objectId) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(objectId, "objectId must not be null");
        if (denied.contains(objectId)) {
            throw new ObjectReadDeniedException("read denied for object " + objectId.value());
        }
        return Optional.ofNullable(snapshots.get(objectId));
    }
}
