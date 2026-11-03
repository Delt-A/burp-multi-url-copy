package multiurlcopy.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UrlEntryTest {

    @Test
    void holdsEveryComponentItWasGiven() {
        UrlEntry entry = new UrlEntry("GET", "https", "api.target.com", 8443, "/v1/users", "id=1");

        assertEquals("GET", entry.method());
        assertEquals("https", entry.scheme());
        assertEquals("api.target.com", entry.host());
        assertEquals(8443, entry.port());
        assertEquals("/v1/users", entry.path());
        assertEquals("id=1", entry.query());
    }

    @Test
    void noPortIsMinusOne() {
        assertEquals(-1, UrlEntry.NO_PORT);
    }
}
