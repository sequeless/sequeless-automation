package org.sequeless.automation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;

class CreateObjectCommandTest {

    private static final ObjectId OBJECT_ID = ObjectId.random();
    private static final CommandProvenance PROVENANCE = new CommandProvenance(UUID.randomUUID(), 0, 1);

    @Test
    void rejectsNullObjectId() {
        assertThatNullPointerException()
                .isThrownBy(() -> new CreateObjectCommand(null, "ex:Project", Map.of(), PROVENANCE))
                .withMessage("objectId must not be null");
    }

    @Test
    void rejectsNullTypeIri() {
        assertThatNullPointerException()
                .isThrownBy(() -> new CreateObjectCommand(OBJECT_ID, null, Map.of(), PROVENANCE))
                .withMessage("typeIri must not be null");
    }

    @Test
    void rejectsBlankTypeIri() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CreateObjectCommand(OBJECT_ID, "", Map.of(), PROVENANCE))
                .withMessage("typeIri must not be blank");
    }

    @Test
    void rejectsNullProperties() {
        assertThatNullPointerException()
                .isThrownBy(() -> new CreateObjectCommand(OBJECT_ID, "ex:Project", null, PROVENANCE))
                .withMessage("properties must not be null");
    }

    @Test
    void rejectsNullProvenance() {
        assertThatNullPointerException()
                .isThrownBy(() -> new CreateObjectCommand(OBJECT_ID, "ex:Project", Map.of(), null))
                .withMessage("provenance must not be null");
    }

    @Test
    void propertiesAreACopyThatIgnoresLaterChangesToTheSourceMap() {
        Map<String, Value> source = new HashMap<>();
        source.put("ex:name", Value.text("first"));
        CreateObjectCommand command = new CreateObjectCommand(OBJECT_ID, "ex:Project", source, PROVENANCE);

        source.put("ex:name", Value.text("changed"));
        source.put("ex:added", Value.text("later"));

        assertThat(command.properties()).containsOnly(Map.entry("ex:name", Value.text("first")));
    }

    @Test
    void propertiesAreUnmodifiable() {
        CreateObjectCommand command =
                new CreateObjectCommand(OBJECT_ID, "ex:Project", Map.of("ex:name", Value.text("x")), PROVENANCE);

        assertThatThrownBy(() -> command.properties().put("ex:other", Value.text("y")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void acceptsAValidCommandAndKeepsItsFields() {
        CreateObjectCommand command =
                new CreateObjectCommand(OBJECT_ID, "ex:Project", Map.of("ex:name", Value.text("x")), PROVENANCE);

        assertThat(command.objectId()).isEqualTo(OBJECT_ID);
        assertThat(command.typeIri()).isEqualTo("ex:Project");
        assertThat(command.provenance()).isEqualTo(PROVENANCE);
    }
}
