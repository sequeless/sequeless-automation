package org.sequeless.automation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ResolvedTimerTest {

    private static final Duration AFTER = Duration.ofHours(72);

    @Test
    void rejectsNullOrBlankTimerKey() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ResolvedTimer(null, AFTER, "ex:Waiting", "expire"))
                .withMessage("ResolvedTimer timerKey must not be blank");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ResolvedTimer("  ", AFTER, "ex:Waiting", "expire"))
                .withMessage("ResolvedTimer timerKey must not be blank");
    }

    @Test
    void rejectsNullAfter() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ResolvedTimer("key", null, "ex:Waiting", "expire"))
                .withMessage("after must not be null");
    }

    @Test
    void rejectsNullOrBlankStateIri() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ResolvedTimer("key", AFTER, null, "expire"))
                .withMessage("ResolvedTimer stateIri must not be blank");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ResolvedTimer("key", AFTER, " ", "expire"))
                .withMessage("ResolvedTimer stateIri must not be blank");
    }

    @Test
    void rejectsNullOrBlankTransitionName() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ResolvedTimer("key", AFTER, "ex:Waiting", null))
                .withMessage("ResolvedTimer transitionName must not be blank");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ResolvedTimer("key", AFTER, "ex:Waiting", ""))
                .withMessage("ResolvedTimer transitionName must not be blank");
    }

    @Test
    void acceptsAValidTimerAndKeepsItsFields() {
        ResolvedTimer timer = new ResolvedTimer("key", AFTER, "ex:Waiting", "expire");

        assertThat(timer.timerKey()).isEqualTo("key");
        assertThat(timer.after()).isEqualTo(AFTER);
        assertThat(timer.stateIri()).isEqualTo("ex:Waiting");
        assertThat(timer.transitionName()).isEqualTo("expire");
    }
}
