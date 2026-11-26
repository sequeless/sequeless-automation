package org.sequeless.automation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;

class SetPropertyCommandTest {

    private static final ObjectId OBJECT_ID = ObjectId.random();
    private static final CommandProvenance PROVENANCE = new CommandProvenance(UUID.randomUUID(), 0, 1);

    @Test
    void rejectsNullObjectId() {
        assertThatNullPointerException()
                .isThrownBy(() -> new SetPropertyCommand(null, "ex:name", Value.text("x"), PROVENANCE))
                .withMessage("objectId must not be null");
    }

    @Test
    void rejectsNullPropertyIri() {
        assertThatNullPointerException()
                .isThrownBy(() -> new SetPropertyCommand(OBJECT_ID, null, Value.text("x"), PROVENANCE))
                .withMessage("propertyIri must not be null");
    }

    @Test
    void rejectsBlankPropertyIri() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SetPropertyCommand(OBJECT_ID, " ", Value.text("x"), PROVENANCE))
                .withMessage("propertyIri must not be blank");
    }

    @Test
    void rejectsNullValue() {
        assertThatNullPointerException()
                .isThrownBy(() -> new SetPropertyCommand(OBJECT_ID, "ex:name", null, PROVENANCE))
                .withMessage("value must not be null");
    }

    @Test
    void rejectsNullProvenance() {
        assertThatNullPointerException()
                .isThrownBy(() -> new SetPropertyCommand(OBJECT_ID, "ex:name", Value.text("x"), null))
                .withMessage("provenance must not be null");
    }

    @Test
    void acceptsAValidCommandAndKeepsItsFields() {
        SetPropertyCommand command = new SetPropertyCommand(OBJECT_ID, "ex:name", Value.text("x"), PROVENANCE);

        assertThat(command.objectId()).isEqualTo(OBJECT_ID);
        assertThat(command.propertyIri()).isEqualTo("ex:name");
        assertThat(command.value()).isEqualTo(Value.text("x"));
        assertThat(command.provenance()).isEqualTo(PROVENANCE);
    }
}
