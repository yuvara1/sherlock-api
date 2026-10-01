package in.sherlock.gateway.domain.exception;

public class InvalidIdentityException extends RuntimeException {
    public InvalidIdentityException(String message, Throwable cause) {
        super(message, cause);
    }
}
