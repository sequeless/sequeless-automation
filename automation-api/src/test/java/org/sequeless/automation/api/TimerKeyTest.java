package org.sequeless.automation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.values.ObjectId;

class TimerKeyTest {

    private static final ObjectId OBJECT_ID = new ObjectId(UUID.fromString("11111111-2222-3333-4444-555555555555"));

    @Test
    void ofJoinsObjectIdStateAndTransitionWithPipes() {
        assertThat(TimerKey.of(OBJECT_ID, "ex:State", "transition"))
                .isEqualTo("11111111-2222-3333-4444-555555555555|ex:State|transition");
    }

    @Test
    void ofRejectsNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> TimerKey.of(null, "ex:State", "transition"));
        assertThatNullPointerException().isThrownBy(() -> TimerKey.of(OBJECT_ID, null, "transition"));
        assertThatNullPointerException().isThrownBy(() -> TimerKey.of(OBJECT_ID, "ex:State", null));
    }
}
