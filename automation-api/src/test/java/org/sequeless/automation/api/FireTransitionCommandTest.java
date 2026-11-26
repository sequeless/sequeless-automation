package org.sequeless.automation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;
import org.sequeless.kernel.values.ObjectId;

class FireTransitionCommandTest {

    private static final ObjectId OBJECT_ID = ObjectId.random();

    @Test
    void rejectsNullObjectId() {
        assertThatNullPointerException()
                .isThrownBy(() -> new FireTransitionCommand(null, "approve", FireTransitionCommand.Trigger.ON_CHANGE))
                .withMessage("objectId must not be null");
    }

    @Test
    void rejectsNullTransitionName() {
        assertThatNullPointerException()
                .isThrownBy(() -> new FireTransitionCommand(OBJECT_ID, null, FireTransitionCommand.Trigger.TIMER))
                .withMessage("transitionName must not be null");
    }

    @Test
    void rejectsBlankTransitionName() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FireTransitionCommand(OBJECT_ID, " ", FireTransitionCommand.Trigger.TIMER))
                .withMessage("transitionName must not be blank");
    }

    @Test
    void rejectsNullTrigger() {
        assertThatNullPointerException()
                .isThrownBy(() -> new FireTransitionCommand(OBJECT_ID, "approve", null))
                .withMessage("trigger must not be null");
    }

    @Test
    void acceptsAValidCommandAndKeepsItsFields() {
        FireTransitionCommand command =
                new FireTransitionCommand(OBJECT_ID, "approve", FireTransitionCommand.Trigger.ON_CHANGE);

        assertThat(command.objectId()).isEqualTo(OBJECT_ID);
        assertThat(command.transitionName()).isEqualTo("approve");
        assertThat(command.trigger()).isEqualTo(FireTransitionCommand.Trigger.ON_CHANGE);
    }
}
