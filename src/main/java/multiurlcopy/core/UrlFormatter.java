package multiurlcopy.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Renders {@link UrlEntry} values into the text that lands on the clipboard.
 *
 * <p>Nothing here re-encodes or decodes: whatever percent-encoding the
 * source carried is passed through untouched, so a copied URL is the URL
 * that was actually requested.
 */
public final class UrlFormatter {

    private UrlFormatter() {
    }

    public static String format(List<UrlEntry> entries, UrlFormat format) {
        List<String> lines = new ArrayList<>(entries.size());
        for (UrlEntry entry : entries) {
            lines.add(renderLine(entry, format));
        }
        if (format == UrlFormat.FULL_URL_DEDUP) {
            lines = new ArrayList<>(new LinkedHashSet<>(lines));
        }
        return String.join("\n", lines);
    }

    /** {@code scheme://host[:port]/path[?query]}, default ports omitted. */
    public static String fullUrl(UrlEntry entry) {
        StringBuilder out = new StringBuilder();
        out.append(entry.scheme()).append("://").append(entry.host());
        if (!isDefaultPort(entry.scheme(), entry.port())) {
            out.append(':').append(entry.port());
        }
        return out.append(pathAndQuery(entry)).toString();
    }

    private static String renderLine(UrlEntry entry, UrlFormat format) {
        return switch (format) {
            case FULL_URL, FULL_URL_DEDUP -> fullUrl(entry);
            case METHOD_AND_URL -> method(entry) + " " + fullUrl(entry);
            case PATH_ONLY -> pathAndQuery(entry);
        };
    }

    private static String method(UrlEntry entry) {
        String method = entry.method();
        return (method == null || method.isBlank()) ? "-" : method.trim();
    }

    private static String pathAndQuery(UrlEntry entry) {
        String path = entry.path();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        String query = entry.query();
        return (query == null || query.isEmpty()) ? path : path + "?" + query;
    }

    private static boolean isDefaultPort(String scheme, int port) {
        if (port == UrlEntry.NO_PORT) {
            return true;
        }
        return ("https".equalsIgnoreCase(scheme) && port == 443)
                || ("http".equalsIgnoreCase(scheme) && port == 80);
    }
}
