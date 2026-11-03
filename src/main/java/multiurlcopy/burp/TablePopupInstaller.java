package multiurlcopy.burp;

import multiurlcopy.core.CopyOutcome;
import multiurlcopy.core.CopyReporter;
import multiurlcopy.core.CopyService;
import multiurlcopy.core.ScrapeResult;
import multiurlcopy.core.TableScraper;
import multiurlcopy.core.UrlFormat;
import multiurlcopy.core.UrlFormatter;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
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
 * Gives a right-click menu to tables that have none.
 *
 * <p>Burp routes context-menu events only to its own components, so an
 * extension's table - Auth Analyzer's results grid, for one - has no
 * "Copy URLs" entry and often no popup menu at all. Listening on the
 * toolkit reaches every table in the JVM.
 *
 * <p>Two things make this safe to leave running. First, the menu is shown
 * from an {@code invokeLater}, after whoever owns the click has had its
 * turn: if any menu is already open, or if Burp just asked extensions for
 * menu items, this one stays shut rather than stacking a second menu on
 * Burp's. Second, nothing is mutated on the way in.
 *
 * <p>The selection is snapshotted during the toolkit callback because
 * that runs <em>before</em> the event reaches the table, and Swing's own
 * handler collapses a multi-row selection to the clicked row - which
 * would otherwise turn "copy these eighteen rows" into "copy one".
 */
public class TablePopupInstaller implements AWTEventListener {

    private final CopyReporter reporter;
    private final BurpMenuActivity burpMenuActivity;
    private final Consumer<Component> applyTheme;

    public TablePopupInstaller(CopyReporter reporter, BurpMenuActivity burpMenuActivity,
                               Consumer<Component> applyTheme) {
        this.reporter = reporter;
        this.burpMenuActivity = burpMenuActivity;
        this.applyTheme = applyTheme;
    }

    @Override
    public void eventDispatched(AWTEvent event) {
        try {
            if (!(event instanceof MouseEvent mouse) || !mouse.isPopupTrigger()) {
                return;
            }
            JTable table = enclosingTable(mouse.getComponent());
            if (table == null) {
                return;
            }

            Point point = SwingUtilities.convertPoint(mouse.getComponent(), mouse.getPoint(), table);
            int[] rows = rowsForClick(table, point);
            if (rows.length == 0) {
                return;
            }
            SwingUtilities.invokeLater(() -> show(table, rows, point));
        } catch (RuntimeException e) {
            // This listener sees every mouse event in the JVM. Nothing may
            // escape it, or an unrelated click elsewhere in Burp breaks.
            CopyService.perform(CopyOutcome.error("popup failed: " + e), reporter);
        }
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

    private void show(JTable table, int[] rows, Point point) {
        if (burpMenuActivity.invokedRecently() || isAMenuAlreadyOpen()) {
            return;
        }
        if (!table.isShowing()) {
            return;
        }
        restoreSelection(table, rows);

        JPopupMenu menu = new JPopupMenu();
        JMenuItem header = new JMenuItem("Copy URLs - " + rows.length + (rows.length == 1 ? " row" : " rows"));
        header.setEnabled(false);
        menu.add(header);
        menu.addSeparator();
        for (UrlFormat format : UrlFormat.values()) {
            JMenuItem item = new JMenuItem(format.displayName());
            item.addActionListener(e -> copy(table, rows, format));
            menu.add(item);
        }
        applyTheme.accept(menu);
        menu.show(table, point.x, point.y);
    }

    private static boolean isAMenuAlreadyOpen() {
        return MenuSelectionManager.defaultManager().getSelectedPath().length > 0;
    }

    /**
     * Puts back the selection Swing collapsed, so what is highlighted
     * matches what the menu is about to copy.
     */
    private static void restoreSelection(JTable table, int[] rows) {
        table.getSelectionModel().setValueIsAdjusting(true);
        try {
            table.clearSelection();
            for (int row : rows) {
                if (row >= 0 && row < table.getRowCount()) {
                    table.addRowSelectionInterval(row, row);
                }
            }
        } finally {
            table.getSelectionModel().setValueIsAdjusting(false);
        }
    }

    private void copy(JTable table, int[] rows, UrlFormat format) {
        try {
            ScrapeResult result = TableScraper.scrape(table, rows);
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
