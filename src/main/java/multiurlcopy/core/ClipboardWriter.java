package multiurlcopy.core;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

/** Puts text on the system clipboard. */
public final class ClipboardWriter {

    private ClipboardWriter() {
    }

    public static void write(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }
}
