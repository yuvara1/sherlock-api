package in.sherlock.auth.infrastructure.crypto;

import in.sherlock.auth.application.port.out.TotpPort;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** RFC 6238 TOTP (SHA1, 6 digits, 30 s step, +/-1 step tolerance). */
@Component
public class TotpAdapter implements TotpPort {
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Clock clock;

    @Autowired
    public TotpAdapter() {
        this(Clock.systemUTC());
    }

    TotpAdapter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String newSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        StringBuilder out = new StringBuilder(32);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                out.append(BASE32.charAt((buffer >> (bitsLeft - 5)) & 31));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) out.append(BASE32.charAt((buffer << (5 - bitsLeft)) & 31));
        return out.toString();
    }

    @Override
    public boolean verify(String secret, String code) {
        if (code == null || !code.matches("\\d{6}")) return false;
        long step = clock.instant().getEpochSecond() / 30;
        int supplied = Integer.parseInt(code);
        boolean valid = false;
        for (long candidate = step - 1; candidate <= step + 1; candidate++) {
            valid |= generate(secret, candidate) == supplied;
        }
        return valid;
    }

    int generate(String secret, long counter) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decodeBase32(secret), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            return binary % 1_000_000;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] decodeBase32(String value) {
        int buffer = 0;
        int bitsLeft = 0;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (char raw : value.toUpperCase().toCharArray()) {
            if (raw == '=') break;
            int index = BASE32.indexOf(raw);
            if (index < 0) throw new IllegalArgumentException("Invalid Base32 value");
            buffer = (buffer << 5) | index;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                output.write((buffer >> (bitsLeft - 8)) & 0xff);
                bitsLeft -= 8;
            }
        }
        return output.toByteArray();
    }
}
