package org.sequeless.automation.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.automation.api.ObjectReadDeniedException;
import org.sequeless.automation.api.ObjectSnapshot;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;

class InMemoryObjectReadPortTest {

    private static final Scope SCOPE = new Scope(new TenantId("tenant-1"), new Principal("user-1", Set.of()));
    private static final ObjectId OBJECT = new ObjectId(UUID.fromString("11111111-2222-3333-4444-555555555555"));

    @Test
    void anAbsentObjectReadsAsEmpty() {
        InMemoryObjectReadPort port = new InMemoryObjectReadPort();

        assertThat(port.find(SCOPE, OBJECT)).isEmpty();
    }

    @Test
    void aDenialHoldsEvenForAnObjectThatWasNeverSeeded() {
        InMemoryObjectReadPort port = new InMemoryObjectReadPort();
        port.deny(OBJECT);

        assertThatExceptionOfType(ObjectReadDeniedException.class).isThrownBy(() -> port.find(SCOPE, OBJECT));
    }

    @Test
    void aDenialWinsOverASeededSnapshot() {
        InMemoryObjectReadPort port = new InMemoryObjectReadPort();
        port.put(OBJECT, snapshot());
        port.deny(OBJECT);

        assertThatExceptionOfType(ObjectReadDeniedException.class).isThrownBy(() -> port.find(SCOPE, OBJECT));
    }

    @Test
    void aSeededObjectReturnsItsSnapshot() {
        InMemoryObjectReadPort port = new InMemoryObjectReadPort();
        ObjectSnapshot snapshot = snapshot();
        port.put(OBJECT, snapshot);

        assertThat(port.find(SCOPE, OBJECT)).contains(snapshot);
    }

    private static ObjectSnapshot snapshot() {
        return new ObjectSnapshot(
                "http://example.org/Project",
                Optional.of("http://example.org/Draft"),
                Map.of("http://example.org/name", Value.text("Launch")));
    }
}
