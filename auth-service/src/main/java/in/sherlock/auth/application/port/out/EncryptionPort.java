package in.sherlock.auth.application.port.out;

public interface EncryptionPort {
    String encrypt(String plaintext);
    String decrypt(String ciphertext);
}
