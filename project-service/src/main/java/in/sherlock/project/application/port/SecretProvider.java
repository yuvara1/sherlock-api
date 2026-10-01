package in.sherlock.project.application.port;

public interface SecretProvider {
    String generate();
    String hash(String plaintext);
}
