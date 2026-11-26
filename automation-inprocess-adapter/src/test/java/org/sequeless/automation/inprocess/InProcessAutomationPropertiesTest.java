package org.sequeless.automation.inprocess;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class InProcessAutomationPropertiesTest {

    @Test
    void defaultsAreThreeRetryAttemptsATwoHundredMillisecondRetryDelayAndAHundredMillisecondTimerTick() {
        InProcessAutomationProperties properties = new InProcessAutomationProperties();

        assertThat(properties.getRetryAttempts()).isEqualTo(3);
        assertThat(properties.getRetryDelay()).isEqualTo(Duration.ofMillis(200));
        assertThat(properties.getTimerTickMs()).isEqualTo(100L);
    }
}
