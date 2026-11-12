package vn.huflit.spm;

import org.junit.jupiter.api.Test;
import vn.huflit.spm.service.ApiException;
import vn.huflit.spm.service.TokenCrypto;

import static org.junit.jupiter.api.Assertions.*;

class TokenCryptoTest {
    @Test
    void roundTripAndRandomIv() {
        TokenCrypto c = new TokenCrypto("secret-a");
        String a = c.encrypt("ghp_abc123");
        String b = c.encrypt("ghp_abc123");
        assertNotEquals(a, b);
        assertEquals("ghp_abc123", c.decrypt(a));
        assertEquals("ghp_abc123", c.decrypt(b));
    }

    @Test
    void wrongKeyOrTamperedDataIsRejected() {
        String enc = new TokenCrypto("secret-a").encrypt("ghp_abc123");
        assertThrows(ApiException.class, () -> new TokenCrypto("secret-b").decrypt(enc));
        assertThrows(ApiException.class, () -> new TokenCrypto("secret-a").decrypt("not-base64!!"));
    }
}
