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
        if (outcome.shouldWriteClipboard()) {
            ClipboardWriter.write(outcome.payload());
        }
        reporter.report(outcome);
    }
}
