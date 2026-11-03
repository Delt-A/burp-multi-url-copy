package multiurlcopy.core;

import javax.swing.JTable;
import javax.swing.table.TableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads URLs out of an arbitrary {@link JTable}, including tables drawn
 * by other Burp extensions, which Burp's own context menu never reaches.
 *
 * <p>Only the public {@link TableModel} is touched - no reflection into
 * another extension's internals - so anything this class can read is
 * also visible on screen.
 */
public final class TableScraper {

    private TableScraper() {
    }

    public static ScrapeResult scrape(JTable table) {
        int[] viewRows = table.getSelectedRows();
        TableModel model = table.getModel();
        List<UrlEntry> entries = new ArrayList<>(viewRows.length);
        int failed = 0;

        for (int viewRow : viewRows) {
            int modelRow = table.convertRowIndexToModel(viewRow);
            Optional<UrlEntry> entry = scrapeRow(model, modelRow);
            if (entry.isPresent()) {
                entries.add(entry.get());
            } else {
                failed++;
            }
        }
        return new ScrapeResult(List.copyOf(entries), failed);
    }

    private static Optional<UrlEntry> scrapeRow(TableModel model, int row) {
        int columns = model.getColumnCount();
        String[] values = new String[columns];
        for (int c = 0; c < columns; c++) {
            Object value = model.getValueAt(row, c);
            values[c] = value == null ? "" : value.toString().trim();
        }

        String method = valueOfColumn(model, values, "method");

        // Rule 2: a cell that is itself an absolute URL.
        for (String value : values) {
            Optional<UrlEntry> parsed = UrlParser.parse(value, method);
            if (parsed.isPresent()) {
                return parsed;
            }
        }

        // Rule 3: a host column joined with a path column.
        Optional<UrlEntry> joined = joinHostAndPath(model, values, method);
        if (joined.isPresent()) {
            return joined;
        }

        // Rule 4: a URL embedded inside a longer cell.
        for (String value : values) {
            Optional<UrlEntry> found = UrlParser.findFirst(value);
            if (found.isPresent()) {
                UrlEntry e = found.get();
                return Optional.of(new UrlEntry(method, e.scheme(), e.host(), e.port(), e.path(), e.query()));
            }
        }
        return Optional.empty();
    }

    private static Optional<UrlEntry> joinHostAndPath(TableModel model, String[] values, String method) {
        String host = valueOfColumn(model, values, "host");
        if (host == null || host.isEmpty()) {
            return Optional.empty();
        }
        String path = firstNonEmpty(
                valueOfColumn(model, values, "path"),
                valueOfColumn(model, values, "url"));
        if (path == null || !path.startsWith("/")) {
            return Optional.empty();
        }

        int port = parsePort(valueOfColumn(model, values, "port"));
        String scheme = port == 80 ? "http" : "https";

        int queryStart = path.indexOf('?');
        String query = queryStart < 0 ? null : path.substring(queryStart + 1);
        String pathOnly = queryStart < 0 ? path : path.substring(0, queryStart);

        return Optional.of(new UrlEntry(method, scheme, host, port, pathOnly, query));
    }

    /** Value of the first column whose name contains {@code needle}, case-insensitively. */
    private static String valueOfColumn(TableModel model, String[] values, String needle) {
        for (int c = 0; c < values.length; c++) {
            String name = model.getColumnName(c);
            if (name != null && name.toLowerCase().contains(needle)) {
                return values[c];
            }
        }
        return null;
    }

    private static String firstNonEmpty(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isEmpty()) {
                return candidate;
            }
        }
        return null;
    }

    private static int parsePort(String raw) {
        if (raw == null || raw.isEmpty()) {
            return UrlEntry.NO_PORT;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return UrlEntry.NO_PORT;
        }
    }
}
