package in.sherlock.auth.application.port.out;

/** Random token generation and one-way hashing for opaque secrets. */
public interface SecureTokenPort {
    String randomToken(int bytes);
    String sha256(String value);
    boolean hashMatches(String raw, String expectedHex);
}
