package multiurlcopy.core;

/**
 * The one path every copy takes.
 *
 * <p>The clipboard is written only when something was actually
 * recognized, so a failed attempt leaves whatever the user had there
 * intact. Every attempt is reported, successful or not.
 */
public final class CopyService {

    private CopyService() {
    }

    public static void perform(CopyOutcome outcome, CopyReporter reporter) {
        CopyOutcome result = outcome;
        if (outcome.shouldWriteClipboard()) {
            try {
                ClipboardWriter.write(outcome.payload());
            } catch (RuntimeException | Error e) {
                result = CopyOutcome.clipboardFailed(outcome.payload(), String.valueOf(e.getMessage()));
            }
        }
        reporter.report(result);
    }
}
