package in.sherlock.project.infrastructure.security;

import in.sherlock.project.application.port.SecretProvider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class SecureSecretProvider implements SecretProvider {
    private final SecureRandom random = new SecureRandom();
    @Override public String generate() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    @Override public String hash(String plaintext) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(plaintext.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
