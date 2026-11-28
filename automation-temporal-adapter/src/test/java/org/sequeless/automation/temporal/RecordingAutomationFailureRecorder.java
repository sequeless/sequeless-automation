package org.sequeless.automation.temporal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.sequeless.automation.api.AutomationFailure;
import org.sequeless.automation.api.AutomationFailureRecorder;

/**
 * An {@link AutomationFailureRecorder} test double that keeps every failure it is given, in order. {@link
 * #setFailRecording} makes {@link #record} throw, to test that a failing recorder does not hide the original failure.
 */
final class RecordingAutomationFailureRecorder implements AutomationFailureRecorder {

    private final List<AutomationFailure> failures = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean failRecording;

    @Override
    public void record(AutomationFailure failure) {
        if (failRecording) {
            throw new IllegalStateException("RecordingAutomationFailureRecorder is set to fail");
        }
        failures.add(failure);
    }

    /** @return a snapshot of every recorded failure, in the order recorded, unmodifiable */
    List<AutomationFailure> failures() {
        synchronized (failures) {
            return List.copyOf(failures);
        }
    }

    /** @param failRecording {@code true} to make every later {@link #record} throw */
    void setFailRecording(boolean failRecording) {
        this.failRecording = failRecording;
    }

    /** Clears the recorded failures and stops failing. */
    void reset() {
        failures.clear();
        failRecording = false;
    }
}
