package multiurlcopy.core;

import java.net.URI;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns an absolute http(s) URL string into a {@link UrlEntry}.
 *
 * <p>Parsing goes through the raw accessors of {@link URI} so that
 * percent-encoding survives untouched; {@code getPath()} would decode it
 * and hand back a URL that was never requested.
 */
public final class UrlParser {

    private static final Pattern EMBEDDED_URL = Pattern.compile("https?://\\S+");

    /**
     * A whole-string URL, parsed without {@link URI}'s strictness.
     *
     * <p>Path and query deliberately allow spaces and every other
     * character {@code URI} refuses, because the input is one table cell
     * that is already known to be a URL - stopping at the first odd
     * character would silently truncate it.
     */
    private static final Pattern LOOSE = Pattern.compile(
            "(?i)^(https?)://(?:[^@/?#]*@)?([A-Za-z0-9._-]+|\\[[0-9A-Fa-f:]+])(?::(\\d{1,5}))?"
                    + "([^?#]*)(?:\\?([^#]*))?(?:#.*)?$");

    private UrlParser() {
    }

    /** Parses {@code url} when the whole string is an absolute http(s) URL. */
    public static Optional<UrlEntry> parse(String url, String method) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        String trimmed = url.trim();
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (Exception e) {
            // URI rejects characters that real traffic carries unencoded -
            // {} in Firefox addon update checks, spaces, | " ^ in queries.
            // Those URLs work fine; refusing them would drop whole rows.
            return loose(trimmed, method);
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            return Optional.empty();
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            return Optional.empty();
        }
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        return Optional.of(new UrlEntry(
                method, scheme.toLowerCase(), host, uri.getPort(), path, uri.getRawQuery()));
    }

    /** Last resort for a URL {@link URI} will not accept. */
    private static Optional<UrlEntry> loose(String url, String method) {
        Matcher matcher = LOOSE.matcher(url);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String path = matcher.group(4);
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        int port = matcher.group(3) == null ? UrlEntry.NO_PORT : Integer.parseInt(matcher.group(3));
        return Optional.of(new UrlEntry(
                method, matcher.group(1).toLowerCase(), matcher.group(2), port, path, matcher.group(5)));
    }

    /** Finds the first http(s) URL embedded anywhere in {@code text}. */
    public static Optional<UrlEntry> findFirst(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher matcher = EMBEDDED_URL.matcher(text);
        return matcher.find() ? parse(matcher.group(), null) : Optional.empty();
    }
}
