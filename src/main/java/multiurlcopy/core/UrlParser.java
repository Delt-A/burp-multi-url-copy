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
            return Optional.empty();
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

    /** Finds the first http(s) URL embedded anywhere in {@code text}. */
    public static Optional<UrlEntry> findFirst(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher matcher = EMBEDDED_URL.matcher(text);
        return matcher.find() ? parse(matcher.group(), null) : Optional.empty();
    }
}
