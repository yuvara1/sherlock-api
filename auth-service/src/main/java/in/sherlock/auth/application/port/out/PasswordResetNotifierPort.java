package in.sherlock.auth.application.port.out;

public interface PasswordResetNotifierPort {
    void send(String email, String rawToken);
}
