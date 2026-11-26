package org.sequeless.automation.api;

import java.util.Optional;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.values.ObjectId;

/**
 * The read-only port automation uses to see the current state of an object. The host implements it over
 * the Store, inside the same claim transaction as the writes it guards, so the read sees the claimant's
 * own uncommitted writes.
 *
 * <p>An empty result means the object is absent. A read the principal is not allowed to make throws
 * {@link ObjectReadDeniedException}, never an empty result.
 */
public interface ObjectReadPort {

    /**
     * Reads the current snapshot of one object.
     *
     * @param scope the tenant and principal the read is made under; must not be {@code null}
     * @param objectId the object to read; must not be {@code null}
     * @return the object's snapshot, or empty if the object is absent
     * @throws ObjectReadDeniedException if the principal is not allowed to read the object
     */
    Optional<ObjectSnapshot> find(Scope scope, ObjectId objectId);
}
