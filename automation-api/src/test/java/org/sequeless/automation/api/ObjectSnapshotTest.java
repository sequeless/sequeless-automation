package org.sequeless.automation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.values.Value;

class ObjectSnapshotTest {

    @Test
    void copiesThePropertiesMapFromTheSource() {
        Map<String, Value> source = new HashMap<>();
        source.put("ex:name", Value.text("first"));
        ObjectSnapshot snapshot = new ObjectSnapshot("ex:Project", Optional.of("ex:Open"), source);

        source.put("ex:name", Value.text("changed"));
        source.put("ex:added", Value.text("later"));

        assertThat(snapshot.properties()).containsOnly(Map.entry("ex:name", Value.text("first")));
    }

    @Test
    void propertiesAreUnmodifiable() {
        ObjectSnapshot snapshot =
                new ObjectSnapshot("ex:Project", Optional.empty(), Map.of("ex:name", Value.text("first")));

        assertThatThrownBy(() -> snapshot.properties().put("ex:other", Value.text("x")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullTypeIri() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ObjectSnapshot(null, Optional.empty(), Map.of()))
                .withMessage("typeIri must not be null");
    }

    @Test
    void rejectsBlankTypeIri() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ObjectSnapshot("  ", Optional.empty(), Map.of()))
                .withMessage("typeIri must not be blank");
    }

    @Test
    void rejectsNullState() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ObjectSnapshot("ex:Project", null, Map.of()))
                .withMessage("state must not be null");
    }

    @Test
    void rejectsNullProperties() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ObjectSnapshot("ex:Project", Optional.empty(), null))
                .withMessage("properties must not be null");
    }
}
