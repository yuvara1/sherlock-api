package in.sherlock.project.application.port;

public interface InvitationNotifier {
    void send(String email, String organizationName, String token);
}
