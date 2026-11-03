package multiurlcopy.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * URLs that java.net.URI refuses but that are perfectly real. Observed in
 * the user's own Proxy history: Firefox addon update checks send an
 * unencoded {uuid} in the query on every run.
 */
class UrlParserLooseTest {

    @Test
    void acceptsUnencodedBracesSeenInFirefoxAddonUpdateChecks() {
        String url = "https://versioncheck-bg.addons.mozilla.org/update/VersionCheck.php"
                + "?id=foxyproxy@eric.h.jung&appID={ec8030f7-c20a-464f-9b0e-13a3a9e97384}&locale=en-US";

        UrlEntry e = UrlParser.parse(url, "GET").orElseThrow();

        assertEquals("versioncheck-bg.addons.mozilla.org", e.host());
        assertEquals("/update/VersionCheck.php", e.path());
        assertEquals("id=foxyproxy@eric.h.jung&appID={ec8030f7-c20a-464f-9b0e-13a3a9e97384}&locale=en-US", e.query());
        assertEquals(url, UrlFormatter.fullUrl(e));
    }

    @Test
    void acceptsASpaceInsideTheQueryWithoutTruncating() {
        UrlEntry e = UrlParser.parse("https://a.com/x?q=hello world&n=1", null).orElseThrow();

        assertEquals("q=hello world&n=1", e.query());
    }

    @Test
    void acceptsOtherCharactersUriRejects() {
        assertEquals("q=a|b", UrlParser.parse("https://a.com/x?q=a|b", null).orElseThrow().query());
        assertEquals("q=\"v\"", UrlParser.parse("https://a.com/x?q=\"v\"", null).orElseThrow().query());
        assertEquals("q=a^b", UrlParser.parse("https://a.com/x?q=a^b", null).orElseThrow().query());
    }

    @Test
    void acceptsASpaceInsideThePath() {
        UrlEntry e = UrlParser.parse("https://a.com/my file.pdf", null).orElseThrow();

        assertEquals("/my file.pdf", e.path());
    }

    @Test
    void stillKeepsPortAndStillDropsTheFragment() {
        UrlEntry e = UrlParser.parse("https://a.com:8443/x?q={1}#frag", null).orElseThrow();

        assertEquals(8443, e.port());
        assertEquals("q={1}", e.query());
    }

    @Test
    void ignoresUserInfoInTheAuthority() {
        assertEquals("a.com", UrlParser.parse("https://user:pw@a.com/x?q={1}", null).orElseThrow().host());
    }

    @Test
    void stillRejectsWhatIsNotAUrlAtAll() {
        assertTrue(UrlParser.parse("ftp://a.com/x", null).isEmpty());
        assertTrue(UrlParser.parse("Ziu4[ZJA=host, ZJ3=x]", null).isEmpty());
        assertTrue(UrlParser.parse("17:31:33 27 Oct 2026", null).isEmpty());
        assertTrue(UrlParser.parse("200", null).isEmpty());
    }

    @Test
    void theStrictPathStillRunsFirstAndStillPreservesPercentEncoding() {
        UrlEntry e = UrlParser.parse("https://a.com/a%2Fb?q=%20x", null).orElseThrow();

        assertEquals("/a%2Fb", e.path());
        assertEquals("q=%20x", e.query());
    }
}
