package multiurlcopy.core;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlParserTest {

    @Test
    void parsesSchemeHostPathAndQuery() {
        UrlEntry e = UrlParser.parse("https://api.target.com/v1/users?id=1", "GET").orElseThrow();

        assertEquals("https", e.scheme());
        assertEquals("api.target.com", e.host());
        assertEquals(UrlEntry.NO_PORT, e.port());
        assertEquals("/v1/users", e.path());
        assertEquals("id=1", e.query());
        assertEquals("GET", e.method());
    }

    @Test
    void keepsExplicitNonDefaultPort() {
        assertEquals(8443, UrlParser.parse("https://a.com:8443/x", null).orElseThrow().port());
    }

    @Test
    void emptyPathBecomesSlash() {
        assertEquals("/", UrlParser.parse("https://a.com", null).orElseThrow().path());
    }

    @Test
    void preservesPercentEncodingVerbatim() {
        UrlEntry e = UrlParser.parse("https://a.com/a%2Fb?q=%20x", null).orElseThrow();

        assertEquals("/a%2Fb", e.path());
        assertEquals("q=%20x", e.query());
    }

    @Test
    void roundTripsThroughTheFormatter() {
        String url = "https://a.com:8443/a%2Fb?q=1";
        assertEquals(url, UrlFormatter.fullUrl(UrlParser.parse(url, null).orElseThrow()));
    }

    @Test
    void rejectsNonHttpSchemes() {
        assertTrue(UrlParser.parse("ftp://a.com/x", null).isEmpty());
        assertTrue(UrlParser.parse("javascript:alert(1)", null).isEmpty());
    }

    @Test
    void rejectsTextThatIsNotAUrl() {
        assertEquals(Optional.empty(), UrlParser.parse("200", null));
        assertEquals(Optional.empty(), UrlParser.parse("", null));
        assertEquals(Optional.empty(), UrlParser.parse(null, null));
    }

    @Test
    void findsAUrlEmbeddedInLongerText() {
        UrlEntry e = UrlParser.findFirst("see https://a.com/x for details").orElseThrow();
        assertEquals("https://a.com/x", UrlFormatter.fullUrl(e));
    }

    @Test
    void findFirstReturnsEmptyWhenThereIsNoUrl() {
        assertTrue(UrlParser.findFirst("no link here").isEmpty());
    }
}
