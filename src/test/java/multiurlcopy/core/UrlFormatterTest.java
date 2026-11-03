package multiurlcopy.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UrlFormatterTest {

    private static UrlEntry entry(String method, String scheme, String host, int port, String path, String query) {
        return new UrlEntry(method, scheme, host, port, path, query);
    }

    @Test
    void fullUrlJoinsEveryPart() {
        String out = UrlFormatter.format(
                List.of(entry("GET", "https", "api.target.com", UrlEntry.NO_PORT, "/v1/users", "id=1")),
                UrlFormat.FULL_URL);

        assertEquals("https://api.target.com/v1/users?id=1", out);
    }

    @Test
    void defaultPortsAreOmitted() {
        assertEquals("https://a.com/x", UrlFormatter.format(
                List.of(entry("GET", "https", "a.com", 443, "/x", null)), UrlFormat.FULL_URL));
        assertEquals("http://a.com/x", UrlFormatter.format(
                List.of(entry("GET", "http", "a.com", 80, "/x", null)), UrlFormat.FULL_URL));
    }

    @Test
    void nonDefaultPortIsKept() {
        assertEquals("https://a.com:8443/x", UrlFormatter.format(
                List.of(entry("GET", "https", "a.com", 8443, "/x", null)), UrlFormat.FULL_URL));
    }

    @Test
    void emptyPathBecomesSlash() {
        assertEquals("https://a.com/", UrlFormatter.format(
                List.of(entry("GET", "https", "a.com", UrlEntry.NO_PORT, "", null)), UrlFormat.FULL_URL));
    }

    @Test
    void missingQueryRendersNothingNotABareQuestionMark() {
        assertEquals("https://a.com/x", UrlFormatter.format(
                List.of(entry("GET", "https", "a.com", UrlEntry.NO_PORT, "/x", "")), UrlFormat.FULL_URL));
    }

    @Test
    void percentEncodingIsPreservedVerbatim() {
        assertEquals("https://a.com/a%2Fb?q=%20x", UrlFormatter.format(
                List.of(entry("GET", "https", "a.com", UrlEntry.NO_PORT, "/a%2Fb", "q=%20x")), UrlFormat.FULL_URL));
    }

    @Test
    void oneEntryPerLineWithNoTrailingNewline() {
        String out = UrlFormatter.format(List.of(
                entry("GET", "https", "a.com", UrlEntry.NO_PORT, "/1", null),
                entry("GET", "https", "a.com", UrlEntry.NO_PORT, "/2", null)), UrlFormat.FULL_URL);

        assertEquals("https://a.com/1\nhttps://a.com/2", out);
    }

    @Test
    void fullUrlKeepsDuplicates() {
        UrlEntry e = entry("GET", "https", "a.com", UrlEntry.NO_PORT, "/x", null);
        assertEquals("https://a.com/x\nhttps://a.com/x", UrlFormatter.format(List.of(e, e), UrlFormat.FULL_URL));
    }

    @Test
    void dedupCollapsesDuplicatesKeepingFirstOccurrenceOrder() {
        String out = UrlFormatter.format(List.of(
                entry("GET", "https", "a.com", UrlEntry.NO_PORT, "/b", null),
                entry("GET", "https", "a.com", UrlEntry.NO_PORT, "/a", null),
                entry("POST", "https", "a.com", UrlEntry.NO_PORT, "/b", null)), UrlFormat.FULL_URL_DEDUP);

        assertEquals("https://a.com/b\nhttps://a.com/a", out);
    }

    @Test
    void methodAndUrlPrefixesTheMethod() {
        assertEquals("POST https://a.com/x", UrlFormatter.format(
                List.of(entry("POST", "https", "a.com", UrlEntry.NO_PORT, "/x", null)), UrlFormat.METHOD_AND_URL));
    }

    @Test
    void unknownMethodRendersAsDashSoColumnCountStaysStable() {
        assertEquals("- https://a.com/x", UrlFormatter.format(
                List.of(entry(null, "https", "a.com", UrlEntry.NO_PORT, "/x", null)), UrlFormat.METHOD_AND_URL));
        assertEquals("- https://a.com/x", UrlFormatter.format(
                List.of(entry("  ", "https", "a.com", UrlEntry.NO_PORT, "/x", null)), UrlFormat.METHOD_AND_URL));
    }

    @Test
    void pathOnlyDropsSchemeAndHostButKeepsQuery() {
        assertEquals("/v1/users?id=1", UrlFormatter.format(
                List.of(entry("GET", "https", "a.com", UrlEntry.NO_PORT, "/v1/users", "id=1")), UrlFormat.PATH_ONLY));
    }

    @Test
    void pathOnlyKeepsDuplicates() {
        UrlEntry e = entry("GET", "https", "a.com", UrlEntry.NO_PORT, "/x", null);
        assertEquals("/x\n/x", UrlFormatter.format(List.of(e, e), UrlFormat.PATH_ONLY));
    }

    @Test
    void emptyInputProducesEmptyString() {
        assertEquals("", UrlFormatter.format(List.of(), UrlFormat.FULL_URL));
    }

    @Test
    void displayNamesAreHumanReadable() {
        assertEquals("Full URL", UrlFormat.FULL_URL.displayName());
        assertEquals("Full URL (dedup)", UrlFormat.FULL_URL_DEDUP.displayName());
        assertEquals("METHOD + URL", UrlFormat.METHOD_AND_URL.displayName());
        assertEquals("Path only", UrlFormat.PATH_ONLY.displayName());
    }
}
