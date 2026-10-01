package in.sherlock.project.infrastructure.mail;

import in.sherlock.project.application.port.InvitationNotifier;
import in.sherlock.project.domain.ProjectException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

@Component
public class SmtpInvitationNotifier implements InvitationNotifier {
    private final JavaMailSender mail;
    private final boolean enabled;
    private final String from;
    private final String inviteUrl;

    public SmtpInvitationNotifier(JavaMailSender mail, @Value("${sherlock.mail-enabled:false}") boolean enabled,
            @Value("${sherlock.mail-from}") String from, @Value("${sherlock.invite-url}") String inviteUrl) {
        this.mail = mail; this.enabled = enabled; this.from = from; this.inviteUrl = inviteUrl;
    }
    @Override public void send(String email, String organizationName, String token) {
        if (!enabled) throw new ProjectException(503, "MAIL_NOT_CONFIGURED", "Configure SMTP to send team invitations.");
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Join " + organizationName + " on Sherlock");
        message.setText("You have been invited to " + organizationName + ". This invitation expires in 7 days.\n\n" +
                inviteUrl + (inviteUrl.contains("?") ? "&" : "?") + "token=" + UriUtils.encodeQueryParam(token, StandardCharsets.UTF_8));
        try { mail.send(message); }
        catch (org.springframework.mail.MailException exception) { throw new ProjectException(503, "MAIL_UNAVAILABLE", "The invitation could not be delivered."); }
    }
}
