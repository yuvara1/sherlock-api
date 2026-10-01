package in.sherlock.auth.infrastructure.crypto;

import in.sherlock.auth.application.port.out.EncryptionPort;
import in.sherlock.auth.infrastructure.config.AuthProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** AES-256-GCM encryption used for MFA secrets at rest. Output: Base64(iv[12] || ciphertext+tag). */
@Component
public class AesGcmEncryptionAdapter implements EncryptionPort {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec encryptionKey;

    public AesGcmEncryptionAdapter(AuthProperties properties) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(properties.encryptionKeyBase64());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("encryption key must be Base64 encoded", e);
        }
        if (key.length != 32) throw new IllegalStateException("MFA encryption key must be exactly 32 bytes");
        this.encryptionKey = new SecretKeySpec(key, "AES");
    }

    @Override
    public String encrypt(String value) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to encrypt MFA secret", e);
        }
    }

    @Override
    public String decrypt(String encrypted) {
        try {
            byte[] combined = Base64.getDecoder().decode(encrypted);
            byte[] iv = Arrays.copyOfRange(combined, 0, 12);
            byte[] value = Arrays.copyOfRange(combined, 12, combined.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(value), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Unable to decrypt MFA secret", e);
        }
    }
}
