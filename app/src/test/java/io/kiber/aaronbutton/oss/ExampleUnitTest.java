package io.kiber.aaronbutton.oss;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    @Test
    public void nfcPayloadRoundTrip() {
        String payload = NfcPayload.encode("abc123", "flash_light");
        assertEquals("flash_light", NfcPayload.actionFor(payload, "abc123"));
        assertEquals("flash_light", NfcPayload.actionFor("abc123:flash_light", "abc123"));
        assertEquals("open_link_https://example.com/A:B",
                NfcPayload.actionFor(NfcPayload.encode("abc123", "open_link_https://example.com/A:B"), "abc123"));
        String customValue = "hidden/action:custom\nvalue";
        assertEquals(customValue,
                NfcPayload.actionFor(NfcPayload.encode("abc123", customValue), "abc123"));
        assertNull(NfcPayload.actionFor(payload, "other"));
    }
}
