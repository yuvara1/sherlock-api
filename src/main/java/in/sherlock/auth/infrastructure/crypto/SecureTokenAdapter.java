package in.sherlock.auth.infrastructure.crypto;

import in.sherlock.auth.application.port.out.SecureTokenPort;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class SecureTokenAdapter implements SecureTokenPort {
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public String randomToken(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    @Override
    public String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public boolean hashMatches(String raw, String expectedHex) {
        return MessageDigest.isEqual(
                sha256(raw).getBytes(StandardCharsets.US_ASCII), expectedHex.getBytes(StandardCharsets.US_ASCII));
    }
}
