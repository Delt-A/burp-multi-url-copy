package multiurlcopy.burp;

import multiurlcopy.core.CopyOutcome;
import multiurlcopy.core.CopyReporter;
import multiurlcopy.core.CopyService;
import multiurlcopy.core.ScrapeResult;
import multiurlcopy.core.TableScraper;
import multiurlcopy.core.UrlFormatter;
import multiurlcopy.ui.Settings;

import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;

/**
 * The path into tables Burp does not own.
 *
 * <p>Burp routes context-menu events only to its own components, and its
 * own hotkey API is bound to a {@code HotKeyContext} whose five members
 * are all Burp-native. Neither reaches an extension's {@link JTable}. A
 * focus-manager dispatcher reaches all of them.
 */
public class HotkeyDispatcher implements KeyEventDispatcher {

    private final Settings settings;
    private final CopyReporter reporter;

    public HotkeyDispatcher(Settings settings, CopyReporter reporter) {
        this.settings = settings;
        this.reporter = reporter;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // This runs for every key press anywhere in Burp. An exception
        // escaping here would poison the event queue, so nothing is allowed
        // out: a failure is reported like any other unsuccessful copy.
        try {
            return dispatch(event);
        } catch (RuntimeException e) {
            CopyService.perform(CopyOutcome.error("copy failed: " + e), reporter);
            return true;
        }
    }

    private boolean dispatch(KeyEvent event) {
        if (!settings.hotkey().matches(event)) {
            return false;
        }

        JTable table = enclosingTable(event.getComponent());
        if (table == null) {
            CopyService.perform(CopyOutcome.noTableFocused(), reporter);
            return true;
        }

        ScrapeResult result = TableScraper.scrape(table);
        if (result.total() == 0) {
            CopyService.perform(CopyOutcome.nothingSelected(), reporter);
            return true;
        }

        String payload = UrlFormatter.format(result.entries(), settings.defaultFormat());
        CopyService.perform(CopyOutcome.of(payload, result.entries().size(), result.total()), reporter);
        return true;
    }

    private static JTable enclosingTable(Component start) {
        Component component = start != null
                ? start
                : KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        while (component != null) {
            if (component instanceof JTable table) {
                return table;
            }
            component = component.getParent();
        }
        return null;
    }

    /** Registers this dispatcher; returns a runnable that removes it again. */
    public Runnable register() {
        KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        SwingUtilities.invokeLater(() -> manager.addKeyEventDispatcher(this));
        return () -> SwingUtilities.invokeLater(() -> manager.removeKeyEventDispatcher(this));
    }
}
