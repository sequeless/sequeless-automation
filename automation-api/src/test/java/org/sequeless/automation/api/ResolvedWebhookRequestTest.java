package org.sequeless.automation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class ResolvedWebhookRequestTest {

    @Test
    void rejectsNullOrBlankUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ResolvedWebhookRequest(null, "POST", Optional.empty()))
                .withMessage("ResolvedWebhookRequest url must not be blank");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ResolvedWebhookRequest(" ", "POST", Optional.empty()))
                .withMessage("ResolvedWebhookRequest url must not be blank");
    }

    @Test
    void rejectsNullOrBlankMethod() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ResolvedWebhookRequest("https://example.test/hook", null, Optional.empty()))
                .withMessage("ResolvedWebhookRequest method must not be blank");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ResolvedWebhookRequest("https://example.test/hook", "", Optional.empty()))
                .withMessage("ResolvedWebhookRequest method must not be blank");
    }

    @Test
    void rejectsNullBody() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ResolvedWebhookRequest("https://example.test/hook", "POST", null))
                .withMessage("body must not be null");
    }

    @Test
    void acceptsAValidRequestWithOrWithoutABody() {
        ResolvedWebhookRequest withBody =
                new ResolvedWebhookRequest("https://example.test/hook", "POST", Optional.of("{\"id\":1}"));
        ResolvedWebhookRequest withoutBody =
                new ResolvedWebhookRequest("https://example.test/hook", "GET", Optional.empty());

        assertThat(withBody.url()).isEqualTo("https://example.test/hook");
        assertThat(withBody.method()).isEqualTo("POST");
        assertThat(withBody.body()).contains("{\"id\":1}");
        assertThat(withoutBody.body()).isEmpty();
    }
}
