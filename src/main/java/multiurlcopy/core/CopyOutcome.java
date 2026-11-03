package multiurlcopy.core;

/**
 * The result of one copy attempt, and the single place that decides how
 * it is announced.
 *
 * <p>Scraping a foreign table is a heuristic and can drop rows. A short
 * list that looks complete is worse than no feature at all - the missing
 * endpoint never gets tested - so a partial copy is never rendered as a
 * plain success, and a copy that recognized nothing never touches the
 * clipboard.
 */
public record CopyOutcome(String payload, int copied, int total, String reason) {

    public static CopyOutcome of(String payload, int copied, int total) {
        return new CopyOutcome(payload, copied, total, null);
    }

    public static CopyOutcome nothingSelected() {
        return new CopyOutcome("", 0, 0, "no rows selected");
    }

    public static CopyOutcome noTableFocused() {
        return new CopyOutcome("", 0, 0, "no table focused");
    }

    /**
     * Something threw where a copy was expected. Surfaced, never swallowed:
     * a silent no-op would be indistinguishable from an empty selection.
     */
    public static CopyOutcome error(String message) {
        return new CopyOutcome("", 0, 0, message);
    }

    /**
     * The text was built but the system clipboard refused it - an X11
     * clipboard owner can be busy. Not a success, but the payload is kept
     * so the settings tab can still show it for a manual copy.
     */
    public static CopyOutcome clipboardFailed(String payload, String message) {
        return new CopyOutcome(payload, 0, 0, "clipboard unavailable: " + message);
    }

    /** False whenever nothing was recognized, so an existing clipboard survives. */
    public boolean shouldWriteClipboard() {
        return copied > 0;
    }

    public boolean isPartial() {
        return copied > 0 && copied < total;
    }

    public String statusLine() {
        if (reason != null) {
            return "nothing copied - " + reason;
        }
        if (copied == 0) {
            return "nothing copied - none of the " + total + " selected rows had a recognizable URL";
        }
        if (isPartial()) {
            int failed = total - copied;
            return "copied " + copied + "/" + total + " - " + failed
                    + (failed == 1 ? " row had" : " rows had") + " no recognizable URL";
        }
        return "copied " + copied + (copied == 1 ? " URL" : " URLs");
    }
}
