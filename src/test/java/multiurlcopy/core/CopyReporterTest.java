package multiurlcopy.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopyReporterTest {

    private static final class RecordingReporter implements CopyReporter {
        final List<CopyOutcome> seen = new ArrayList<>();

        @Override
        public void report(CopyOutcome outcome) {
            seen.add(outcome);
        }
    }

    /** Stands in for the system clipboard, which a headless build does not have. */
    private final List<String> clipboard = new ArrayList<>();

    @Test
    void everyOutcomeIsReportedIncludingTheFailures() {
        RecordingReporter reporter = new RecordingReporter();

        CopyService.perform(CopyOutcome.nothingSelected(), reporter, clipboard::add);
        CopyService.perform(CopyOutcome.noTableFocused(), reporter, clipboard::add);
        CopyService.perform(CopyOutcome.of("", 0, 3), reporter, clipboard::add);

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

        CopyService.perform(CopyOutcome.of("https://a.com/x", 12, 15), reporter, clipboard::add);

        assertEquals("copied 12/15 - 3 rows had no recognizable URL", reporter.seen.get(0).statusLine());
        assertEquals(List.of("https://a.com/x"), clipboard, "a partial copy still copies what it recognized");
    }

    /**
     * The property the whole design turns on: a copy that recognized
     * nothing must leave whatever the user already had on the clipboard.
     */
    @Test
    void aCopyThatRecognizedNothingNeverTouchesTheClipboard() {
        RecordingReporter reporter = new RecordingReporter();

        CopyService.perform(CopyOutcome.nothingSelected(), reporter, clipboard::add);
        CopyService.perform(CopyOutcome.noTableFocused(), reporter, clipboard::add);
        CopyService.perform(CopyOutcome.of("", 0, 7), reporter, clipboard::add);
        CopyService.perform(CopyOutcome.error("boom"), reporter, clipboard::add);

        assertTrue(clipboard.isEmpty(), "clipboard was written despite nothing being recognized");
    }

    @Test
    void aClipboardThatRefusesTheWriteIsReportedAsAFailureKeepingThePayload() {
        RecordingReporter reporter = new RecordingReporter();

        CopyService.perform(CopyOutcome.of("https://a.com/x", 1, 1), reporter, text -> {
            throw new IllegalStateException("owner is busy");
        });

        CopyOutcome seen = reporter.seen.get(0);
        assertEquals("nothing copied - clipboard unavailable: owner is busy", seen.statusLine());
        assertEquals("https://a.com/x", seen.payload());
    }
}
