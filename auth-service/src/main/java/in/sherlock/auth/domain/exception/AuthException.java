package in.sherlock.auth.domain.exception;

/**
 * Domain-level failure carrying a stable error code and an HTTP-compatible status number.
 * The presentation layer maps the numeric status to a transport response.
 */
public class AuthException extends RuntimeException {
    public static final int BAD_REQUEST = 400;
    public static final int UNAUTHORIZED = 401;
    public static final int NOT_FOUND = 404;
    public static final int CONFLICT = 409;
    public static final int TOO_MANY_REQUESTS = 429;

    private final int status;
    private final String code;

    public AuthException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int getStatus() { return status; }
    public String getCode() { return code; }
}
