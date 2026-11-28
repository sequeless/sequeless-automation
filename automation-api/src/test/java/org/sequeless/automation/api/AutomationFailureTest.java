package org.sequeless.automation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;

class AutomationFailureTest {

    private static final Scope SCOPE = new Scope(new TenantId("tenant-1"), new Principal("user-1", Set.of()));

    @Test
    void rejectsANullScopeOrCause() {
        assertThatNullPointerException()
                .isThrownBy(() -> new AutomationFailure(null, "id:0", "TransitionActionWorkflow", null, "cause"))
                .withMessage("scope must not be null");
        assertThatNullPointerException()
                .isThrownBy(() -> new AutomationFailure(SCOPE, "id:0", "TransitionActionWorkflow", null, null))
                .withMessage("cause must not be null");
    }

    @Test
    void rejectsABlankWorkflowIdOrType() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AutomationFailure(SCOPE, " ", "TransitionActionWorkflow", null, "cause"))
                .withMessage("AutomationFailure workflowId must not be blank");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AutomationFailure(SCOPE, "id:0", "", null, "cause"))
                .withMessage("AutomationFailure workflowType must not be blank");
    }

    @Test
    void acceptsAnEmptyCauseAndANullTransition() {
        AutomationFailure failure = new AutomationFailure(SCOPE, "id:0", "TransitionActionWorkflow", null, "");

        assertThat(failure.cause()).isEmpty();
        assertThat(failure.transition()).isNull();
    }
}
