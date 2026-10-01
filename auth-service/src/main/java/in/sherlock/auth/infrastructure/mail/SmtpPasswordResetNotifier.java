package in.sherlock.auth.infrastructure.mail;

import in.sherlock.auth.application.port.out.PasswordResetNotifierPort;
import in.sherlock.auth.infrastructure.config.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "sherlock.mail.enabled", havingValue = "true")
public class SmtpPasswordResetNotifier implements PasswordResetNotifierPort {
    private static final Logger LOG = LoggerFactory.getLogger(SmtpPasswordResetNotifier.class);
    private final JavaMailSender sender;
    private final AuthProperties properties;

    public SmtpPasswordResetNotifier(JavaMailSender sender, AuthProperties properties) {
        this.sender = sender;
        this.properties = properties;
    }

    @Override
    public void send(String email, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mailFrom());
        message.setTo(email);
        message.setSubject("Reset your Sherlock password");
        message.setText("Use this link within 30 minutes to reset your password:\n\n"
                + ResetLinks.build(properties.resetUrl(), token)
                + "\n\nIf you did not request this, you can ignore this email.");
        try {
            sender.send(message);
        } catch (RuntimeException exception) {
            // Keep the public response indistinguishable for existing and unknown accounts.
            LOG.error("Password-reset email delivery failed", exception);
        }
    }
}
