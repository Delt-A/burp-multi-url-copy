package multiurlcopy.core;

import java.util.function.Consumer;

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
        perform(outcome, reporter, ClipboardWriter::write);
    }

    /**
     * The real logic, with the clipboard as a parameter.
     *
     * <p>Injectable so that the one property worth proving - that a copy
     * which recognized nothing never touches the clipboard - can be
     * asserted, and so the suite does not need a system clipboard to run.
     */
    static void perform(CopyOutcome outcome, CopyReporter reporter, Consumer<String> clipboard) {
        CopyOutcome result = outcome;
        if (outcome.shouldWriteClipboard()) {
            try {
                clipboard.accept(outcome.payload());
            } catch (RuntimeException | Error e) {
                result = CopyOutcome.clipboardFailed(outcome.payload(), String.valueOf(e.getMessage()));
            }
        }
        reporter.report(result);
    }
}
