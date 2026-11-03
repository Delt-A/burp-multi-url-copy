package multiurlcopy.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CopyReporterTest {

    private static final class RecordingReporter implements CopyReporter {
        final List<CopyOutcome> seen = new ArrayList<>();

        @Override
        public void report(CopyOutcome outcome) {
            seen.add(outcome);
        }
    }

    @Test
    void everyOutcomeIsReportedIncludingTheFailures() {
        RecordingReporter reporter = new RecordingReporter();

        CopyService.perform(CopyOutcome.nothingSelected(), reporter);
        CopyService.perform(CopyOutcome.noTableFocused(), reporter);
        CopyService.perform(CopyOutcome.of("", 0, 3), reporter);

        assertEquals(3, reporter.seen.size());
        assertEquals(List.of(
                        "nothing copied - no rows selected",
                        "nothing copied - no table focused",
                        "nothing copied - none of the 3 selected rows had a recognizable URL"),
                reporter.seen.stream().map(CopyOutcome::statusLine).toList());
    }

    @Test
    void aPartialCopyStillReachesTheReporterWithItsWarning() {
        RecordingReporter reporter = new RecordingReporter();

        CopyService.perform(CopyOutcome.of("https://a.com/x", 12, 15), reporter);

        assertEquals("copied 12/15 - 3 rows had no recognizable URL", reporter.seen.get(0).statusLine());
    }
}
