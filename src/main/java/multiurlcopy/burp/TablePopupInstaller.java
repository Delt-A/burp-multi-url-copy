package multiurlcopy.burp;

import multiurlcopy.core.CopyOutcome;
import multiurlcopy.core.CopyReporter;
import multiurlcopy.core.CopyService;
import multiurlcopy.core.ScrapeResult;
import multiurlcopy.core.TableScraper;
import multiurlcopy.core.UrlFormat;
import multiurlcopy.core.UrlFormatter;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * Puts "Copy URLs" on the right-click of tables Burp cannot reach.
 *
 * <p>Burp routes context-menu events only to its own components, so an
 * extension's table never sees a registered
 * {@code ContextMenuItemsProvider}. Listening on the toolkit reaches
 * every table in the JVM.
 *
 * <h2>Why this waits for the mouse release</h2>
 *
 * <p>On X11 a right-click is a popup trigger on {@code MOUSE_PRESSED},
 * but extensions commonly build their menu in {@code mouseReleased} -
 * Auth Analyzer does, in {@code CenterPanel$5}. Acting on the press meant
 * opening a menu first and swallowing the release, which made the host
 * extension's own menu disappear. So the gesture is only acted on once
 * the release has been dispatched and whoever owns the table has had its
 * turn.
 *
 * <h2>Why it adds to the host's menu instead of replacing it</h2>
 *
 * <p>If the table already produced a popup, that menu is the user's, not
 * ours: this appends a "Copy URLs" submenu to it and re-shows it at the
 * same spot. Only a table that produced nothing gets a standalone menu.
 * On Burp's own tables it does nothing at all, because the items are
 * already contributed through the proper provider.
 *
 * <h2>Why the selection is snapshotted</h2>
 *
 * <p>This listener runs before the event reaches the component, and
 * Swing's own handler collapses a multi-row selection to the clicked row.
 * Reading the selection later would turn "copy these eighteen rows" into
 * "copy one".
 */
public class TablePopupInstaller implements AWTEventListener {

    /** Marks the components this class added, so a reused menu is not double-filled. */
    private static final String OURS = "multiurlcopy.copy-urls";

    private final CopyReporter reporter;
    private final BurpMenuActivity burpMenuActivity;
    private final Consumer<Component> applyTheme;

    private Gesture pending;

    public TablePopupInstaller(CopyReporter reporter, BurpMenuActivity burpMenuActivity,
                               Consumer<Component> applyTheme) {
        this.reporter = reporter;
        this.burpMenuActivity = burpMenuActivity;
        this.applyTheme = applyTheme;
    }

    /** A right-click on a table, captured before Swing could disturb it. */
    private record Gesture(JTable table, Point point, int[] rows) {
    }

    @Override
    public void eventDispatched(AWTEvent event) {
        try {
            if (!(event instanceof MouseEvent mouse) || !isRightButton(mouse)) {
                return;
            }
            if (mouse.getID() == MouseEvent.MOUSE_PRESSED) {
                pending = capture(mouse);
            } else if (mouse.getID() == MouseEvent.MOUSE_RELEASED) {
                Gesture gesture = pending != null ? pending : capture(mouse);
                pending = null;
                if (gesture != null) {
                    SwingUtilities.invokeLater(() -> offer(gesture));
                }
            }
        } catch (RuntimeException e) {
            // This listener sees every mouse event in the JVM. Nothing may
            // escape it, or an unrelated click elsewhere in Burp breaks.
            pending = null;
            CopyService.perform(CopyOutcome.error("popup failed: " + e), reporter);
        }
    }

    private static boolean isRightButton(MouseEvent mouse) {
        return mouse.isPopupTrigger() || mouse.getButton() == MouseEvent.BUTTON3;
    }

    private static Gesture capture(MouseEvent mouse) {
        JTable table = enclosingTable(mouse.getComponent());
        if (table == null) {
            return null;
        }
        Point point = SwingUtilities.convertPoint(mouse.getComponent(), mouse.getPoint(), table);
        int[] rows = rowsForClick(table, point);
        return rows.length == 0 ? null : new Gesture(table, point, rows);
    }

    /**
     * The rows the user means: the live selection when the click landed
     * inside it, otherwise just the clicked row.
     */
    private static int[] rowsForClick(JTable table, Point point) {
        int clicked = table.rowAtPoint(point);
        int[] selected = table.getSelectedRows();
        if (clicked < 0) {
            return selected;
        }
        for (int row : selected) {
            if (row == clicked) {
                return selected;
            }
        }
        return new int[]{clicked};
    }

    private void offer(Gesture gesture) {
        // Burp asks extensions for items only on components it owns, so a
        // very recent call means this click already has a Burp menu with
        // our entries in it.
        if (burpMenuActivity.invokedRecently() || !gesture.table().isShowing()) {
            return;
        }

        JPopupMenu host = openPopupFor(gesture.table());
        if (host != null) {
            augment(host, gesture);
        } else {
            showStandalone(gesture);
        }
    }

    /** The popup the table itself just opened, or null if it opened none. */
    private static JPopupMenu openPopupFor(JTable table) {
        for (MenuElement element : MenuSelectionManager.defaultManager().getSelectedPath()) {
            if (element instanceof JPopupMenu popup && belongsTo(popup, table)) {
                return popup;
            }
        }
        return null;
    }

    private static boolean belongsTo(JPopupMenu popup, JTable table) {
        Component invoker = popup.getInvoker();
        return invoker != null && (invoker == table || SwingUtilities.isDescendingFrom(invoker, table)
                || SwingUtilities.isDescendingFrom(table, invoker));
    }

    /**
     * Appends our submenu to the host extension's own menu and re-shows it,
     * which is what re-packs an already-visible popup to the new size.
     */
    private void augment(JPopupMenu host, Gesture gesture) {
        for (Component existing : host.getComponents()) {
            if (OURS.equals(existing.getName())) {
                return;
            }
        }

        Component invoker = host.getInvoker();
        Point location = host.isShowing() ? host.getLocationOnScreen() : null;

        JMenu submenu = buildSubmenu(gesture);
        host.addSeparator();
        host.add(submenu);
        applyTheme.accept(submenu);

        if (location != null && invoker != null) {
            SwingUtilities.convertPointFromScreen(location, invoker);
            host.setVisible(false);
            host.show(invoker, location.x, location.y);
        } else {
            host.pack();
        }
    }

    private void showStandalone(Gesture gesture) {
        restoreSelection(gesture);

        JPopupMenu menu = new JPopupMenu();
        menu.setName(OURS);
        JMenuItem header = new JMenuItem(rowLabel(gesture));
        header.setEnabled(false);
        menu.add(header);
        menu.addSeparator();
        for (UrlFormat format : UrlFormat.values()) {
            menu.add(formatItem(gesture, format));
        }
        applyTheme.accept(menu);
        menu.show(gesture.table(), gesture.point().x, gesture.point().y);
    }

    private JMenu buildSubmenu(Gesture gesture) {
        JMenu submenu = new JMenu("Copy URLs (" + rowLabel(gesture) + ")");
        submenu.setName(OURS);
        for (UrlFormat format : UrlFormat.values()) {
            submenu.add(formatItem(gesture, format));
        }
        return submenu;
    }

    private JMenuItem formatItem(Gesture gesture, UrlFormat format) {
        JMenuItem item = new JMenuItem(format.displayName());
        item.addActionListener(e -> copy(gesture, format));
        return item;
    }

    private static String rowLabel(Gesture gesture) {
        int count = gesture.rows().length;
        return count + (count == 1 ? " row" : " rows");
    }

    /**
     * Puts back the selection Swing collapsed, so what is highlighted
     * matches what the menu is about to copy. Only done for our own menu:
     * a host extension's menu may act on the selection itself, and
     * rewriting it underneath would change what its items do.
     */
    private static void restoreSelection(Gesture gesture) {
        JTable table = gesture.table();
        table.getSelectionModel().setValueIsAdjusting(true);
        try {
            table.clearSelection();
            for (int row : gesture.rows()) {
                if (row >= 0 && row < table.getRowCount()) {
                    table.addRowSelectionInterval(row, row);
                }
            }
        } finally {
            table.getSelectionModel().setValueIsAdjusting(false);
        }
    }

    private void copy(Gesture gesture, UrlFormat format) {
        try {
            ScrapeResult result = TableScraper.scrape(gesture.table(), gesture.rows());
            String payload = UrlFormatter.format(result.entries(), format);
            CopyService.perform(
                    CopyOutcome.of(payload, result.entries().size(), result.total()), reporter);
        } catch (RuntimeException e) {
            CopyService.perform(CopyOutcome.error("copy failed: " + e), reporter);
        }
    }

    private static JTable enclosingTable(Component start) {
        Component component = start;
        while (component != null) {
            if (component instanceof JTable table) {
                return table;
            }
            component = component.getParent();
        }
        return null;
    }

    /** Installs this listener; returns a runnable that removes it again. */
    public Runnable install() {
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        SwingUtilities.invokeLater(() -> toolkit.addAWTEventListener(this, AWTEvent.MOUSE_EVENT_MASK));
        return () -> SwingUtilities.invokeLater(() -> toolkit.removeAWTEventListener(this));
    }
}
