package org.sequeless.automation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CommandProvenanceTest {

    private static final UUID SOURCE = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void rejectsNullSourceEventId() {
        assertThatNullPointerException()
                .isThrownBy(() -> new CommandProvenance(null, 0, 1))
                .withMessage("sourceEventId must not be null");
    }

    @Test
    void rejectsNegativeActionIndex() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CommandProvenance(SOURCE, -1, 1))
                .withMessage("actionIndex must not be negative");
    }

    @Test
    void acceptsZeroActionIndexAndKeepsItsFields() {
        CommandProvenance provenance = new CommandProvenance(SOURCE, 0, 3);

        assertThat(provenance.sourceEventId()).isEqualTo(SOURCE);
        assertThat(provenance.actionIndex()).isZero();
        assertThat(provenance.ontologyVersion()).isEqualTo(3);
    }
}
