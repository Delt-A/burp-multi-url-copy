package multiurlcopy.core;

/**
 * One request's URL, broken into the pieces the formatters need.
 *
 * <p>{@code method} may be null when the source table does not expose one.
 * {@code query} may be null or empty when the URL carries no query string.
 */
public record UrlEntry(String method, String scheme, String host, int port, String path, String query) {

    /** Port value meaning "the scheme's default port". */
    public static final int NO_PORT = -1;
}
