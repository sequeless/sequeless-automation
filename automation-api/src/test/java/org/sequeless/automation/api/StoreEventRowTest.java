package org.sequeless.automation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StoreEventRowTest {

    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String KIND = StoreEventRow.KIND_OBJECT_CREATED;
    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");

    private static Map<String, Object> payload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("objectId", "abc");
        payload.put("ontologyVersion", 3);
        return payload;
    }

    @Test
    void kindConstantsHaveTheStoreOutboxValues() {
        assertThat(StoreEventRow.KIND_OBJECT_CREATED).isEqualTo("ObjectCreated");
        assertThat(StoreEventRow.KIND_OBJECT_UPDATED).isEqualTo("ObjectUpdated");
        assertThat(StoreEventRow.KIND_OBJECT_DELETED).isEqualTo("ObjectDeleted");
        assertThat(StoreEventRow.KIND_TRANSITION_FIRED).isEqualTo("TransitionFired");
    }

    @Test
    void rejectsNullId() {
        assertThatNullPointerException()
                .isThrownBy(() -> new StoreEventRow(null, KIND, payload(), NOW))
                .withMessage("id must not be null");
    }

    @Test
    void rejectsNullKind() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StoreEventRow(ID, null, payload(), NOW))
                .withMessage("StoreEventRow kind must not be null or blank");
    }

    @Test
    void rejectsBlankKind() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StoreEventRow(ID, "  ", payload(), NOW))
                .withMessage("StoreEventRow kind must not be null or blank");
    }

    @Test
    void rejectsNullPayload() {
        assertThatNullPointerException()
                .isThrownBy(() -> new StoreEventRow(ID, KIND, null, NOW))
                .withMessage("payload must not be null");
    }

    @Test
    void rejectsNullOccurredAt() {
        assertThatNullPointerException()
                .isThrownBy(() -> new StoreEventRow(ID, KIND, payload(), null))
                .withMessage("occurredAt must not be null");
    }

    @Test
    void rejectsANullPayloadValueNamingItsPath() {
        Map<String, Object> payload = payload();
        payload.put("ex:name", null);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StoreEventRow(ID, KIND, payload, NOW))
                .withMessageContaining("payload value at 'ex:name' must not be null");
    }

    @Test
    void rejectsANonJsonValueNamingItsPath() {
        Map<String, Object> payload = payload();
        payload.put("ex:bad", new Object());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StoreEventRow(ID, KIND, payload, NOW))
                .withMessageContaining("payload value at 'ex:bad' is not JSON-compatible");
    }

    @Test
    void rejectsANonJsonValueNestedInAListAndAMapNamingTheFullPath() {
        Map<String, Object> inner = new HashMap<>();
        inner.put("items", List.of("ok", new Object()));
        Map<String, Object> payload = payload();
        payload.put("ex:outer", inner);

        assertThatThrownBy(() -> new StoreEventRow(ID, KIND, payload, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ex:outer.items[1]");
    }

    @Test
    void acceptsJsonCompatibleValuesAtAnyDepth() {
        Map<String, Object> payload = payload();
        payload.put("ex:flag", true);
        payload.put("ex:nested", Map.of("ex:list", List.of("a", 2, 3.5)));

        StoreEventRow row = new StoreEventRow(ID, KIND, payload, NOW);

        assertThat(row.payload()).isEqualTo(payload);
    }

    @Test
    void payloadIsDefensivelyCopiedFromTheSource() {
        Map<String, Object> source = payload();

        StoreEventRow row = new StoreEventRow(ID, KIND, source, NOW);
        source.put("objectId", "changed");
        source.put("added", "later");

        assertThat(row.payload()).containsOnly(Map.entry("objectId", "abc"), Map.entry("ontologyVersion", 3));
    }

    @Test
    void payloadIsUnmodifiable() {
        StoreEventRow row = new StoreEventRow(ID, KIND, payload(), NOW);

        assertThatThrownBy(() -> row.payload().put("ex:other", "x")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void acceptsAValidRowAndKeepsItsFields() {
        StoreEventRow row = new StoreEventRow(ID, KIND, payload(), NOW);

        assertThat(row.id()).isEqualTo(ID);
        assertThat(row.kind()).isEqualTo("ObjectCreated");
        assertThat(row.occurredAt()).isEqualTo(NOW);
        assertThat(row.payload()).containsEntry("objectId", "abc");
    }
}
