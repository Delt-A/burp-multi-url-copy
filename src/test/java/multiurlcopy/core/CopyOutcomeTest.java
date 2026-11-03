package multiurlcopy.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopyOutcomeTest {

    @Test
    void completeCopyReportsPlainSuccess() {
        CopyOutcome outcome = CopyOutcome.of("https://a.com/x", 1, 1);

        assertTrue(outcome.shouldWriteClipboard());
        assertFalse(outcome.isPartial());
        assertEquals("copied 1 URL", outcome.statusLine());
    }

    @Test
    void successLineIsPluralizedForSeveralUrls() {
        assertEquals("copied 15 URLs", CopyOutcome.of("x", 15, 15).statusLine());
    }

    @Test
    void partialCopyIsNeverReportedAsPlainSuccess() {
        CopyOutcome outcome = CopyOutcome.of("x", 12, 15);

        assertTrue(outcome.shouldWriteClipboard());
        assertTrue(outcome.isPartial());
        assertEquals("copied 12/15 - 3 rows had no recognizable URL", outcome.statusLine());
    }

    @Test
    void partialLineIsSingularForASingleFailedRow() {
        assertEquals("copied 2/3 - 1 row had no recognizable URL",
                CopyOutcome.of("x", 2, 3).statusLine());
    }

    @Test
    void totalFailureLeavesTheClipboardAlone() {
        CopyOutcome outcome = CopyOutcome.of("", 0, 5);

        assertFalse(outcome.shouldWriteClipboard());
        assertEquals("nothing copied - none of the 5 selected rows had a recognizable URL",
                outcome.statusLine());
    }

    @Test
    void nothingSelectedLeavesTheClipboardAlone() {
        CopyOutcome outcome = CopyOutcome.nothingSelected();

        assertFalse(outcome.shouldWriteClipboard());
        assertEquals("nothing copied - no rows selected", outcome.statusLine());
    }

    @Test
    void noTableFocusedLeavesTheClipboardAlone() {
        CopyOutcome outcome = CopyOutcome.noTableFocused();

        assertFalse(outcome.shouldWriteClipboard());
        assertEquals("nothing copied - no table focused", outcome.statusLine());
    }
}
