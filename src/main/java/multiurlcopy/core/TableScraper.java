package multiurlcopy.core;

import javax.swing.JTable;
import javax.swing.table.TableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads URLs out of an arbitrary {@link JTable}, including tables drawn
 * by other Burp extensions, which Burp's own context menu never reaches.
 *
 * <p>Only the public {@link TableModel} is touched - no reflection into
 * another extension's internals - so anything this class can read is
 * also visible on screen.
 *
 * <p>A cell is not a string just because its column is called "Host".
 * Burp's own Proxy history model answers {@code getValueAt} for that
 * column with an internal object whose {@code toString} is a debug dump,
 * so every candidate is validated before it is believed.
 */
public final class TableScraper {

    /** A plain hostname or IPv4 literal. */
    private static final Pattern HOSTNAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    /** An IPv6 literal in its bracketed form. */
    private static final Pattern IPV6 = Pattern.compile("\\[[0-9A-Fa-f:]+]");

    /** Scheme and authority of a URL embedded anywhere in a cell's text. */
    private static final Pattern EMBEDDED_ORIGIN =
            Pattern.compile("(https?)://([A-Za-z0-9][A-Za-z0-9._-]*)(?::(\\d{1,5}))?");

    private TableScraper() {
    }

    public static ScrapeResult scrape(JTable table) {
        return scrape(table, table.getSelectedRows());
    }

    /**
     * Scrapes an explicit set of view rows rather than the live selection.
     *
     * <p>A right-click makes Swing collapse a multi-row selection to the
     * clicked row before any listener on the table sees it, so the popup
     * path snapshots the rows first and passes them here.
     */
    public static ScrapeResult scrape(JTable table, int[] viewRows) {
        TableModel model = table.getModel();
        List<UrlEntry> entries = new ArrayList<>(viewRows.length);
        int failed = 0;

        for (int viewRow : viewRows) {
            Optional<UrlEntry> entry;
            try {
                entry = scrapeRow(model, table.convertRowIndexToModel(viewRow));
            } catch (RuntimeException e) {
                // getValueAt and getColumnName belong to another extension.
                // A row that throws is a row we could not read - count it,
                // do not let it abort the rows that did work.
                entry = Optional.empty();
            }
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

        String method = method(model, values);

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
        Origin origin = origin(valueOfColumn(model, values, "host", false));
        if (origin == null) {
            return Optional.empty();
        }
        String path = firstNonEmpty(
                valueOfColumn(model, values, "path", false),
                valueOfColumn(model, values, "url", false));
        if (path == null || !path.startsWith("/")) {
            return Optional.empty();
        }

        // Only a column named exactly "port" counts. Burp's history model
        // also carries a "Listener port" holding the proxy's own port, and
        // reading that produced URLs pointing at :8080 instead of the target.
        int port = parsePort(valueOfColumn(model, values, "port", true));
        if (port == UrlEntry.NO_PORT) {
            port = origin.port();
        }
        String scheme = origin.scheme() != null ? origin.scheme() : (port == 80 ? "http" : "https");

        int queryStart = path.indexOf('?');
        String query = queryStart < 0 ? null : path.substring(queryStart + 1);
        String pathOnly = queryStart < 0 ? path : path.substring(0, queryStart);

        return Optional.of(new UrlEntry(method, scheme, origin.host(), port, pathOnly, query));
    }

    /** Scheme, host and port as far as a "host" cell actually reveals them. */
    private record Origin(String scheme, String host, int port) {
    }

    /**
     * Interprets a host cell. A clean hostname is taken as-is; anything
     * else is searched for an embedded {@code http(s)://host}, which is
     * how Burp's history survives its Host column being an opaque object.
     * Returns null when the cell reveals no host at all.
     */
    private static Origin origin(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        if (HOSTNAME.matcher(raw).matches() || IPV6.matcher(raw).matches()) {
            return new Origin(null, raw, UrlEntry.NO_PORT);
        }
        Matcher matcher = EMBEDDED_ORIGIN.matcher(raw);
        if (matcher.find()) {
            return new Origin(matcher.group(1).toLowerCase(), matcher.group(2), parsePort(matcher.group(3)));
        }
        return null;
    }

    /** An HTTP method is short and alphabetic; anything else is a mislabelled column. */
    private static String method(TableModel model, String[] values) {
        String raw = valueOfColumn(model, values, "method", false);
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.matches("[A-Za-z]{3,10}") ? trimmed.toUpperCase() : null;
    }

    /**
     * Value of the column named {@code needle}. An exact name match always
     * wins; a merely-containing match is a fallback, and is refused
     * outright when {@code exactOnly}.
     */
    private static String valueOfColumn(TableModel model, String[] values, String needle, boolean exactOnly) {
        for (int c = 0; c < values.length; c++) {
            String name = model.getColumnName(c);
            if (name != null && name.trim().equalsIgnoreCase(needle)) {
                return values[c];
            }
        }
        if (exactOnly) {
            return null;
        }
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
            int port = Integer.parseInt(raw.trim());
            return (port >= 1 && port <= 65535) ? port : UrlEntry.NO_PORT;
        } catch (NumberFormatException e) {
            return UrlEntry.NO_PORT;
        }
    }
}
