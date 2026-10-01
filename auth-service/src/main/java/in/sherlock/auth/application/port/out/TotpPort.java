package in.sherlock.auth.application.port.out;

public interface TotpPort {
    String newSecret();
    boolean verify(String base32Secret, String code);
}
