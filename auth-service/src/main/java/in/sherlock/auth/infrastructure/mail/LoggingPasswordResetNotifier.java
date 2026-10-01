package in.sherlock.auth.infrastructure.mail;

import in.sherlock.auth.application.port.out.PasswordResetNotifierPort;
import in.sherlock.auth.infrastructure.config.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Used when SMTP is disabled. By default only a masked notice is logged; with
 * {@code sherlock.mail.log-reset-links=true} (dev profile only) the full reset link is printed.
 */
@Component
@ConditionalOnProperty(name = "sherlock.mail.enabled", havingValue = "false", matchIfMissing = true)
public class LoggingPasswordResetNotifier implements PasswordResetNotifierPort {
    private static final Logger LOG = LoggerFactory.getLogger(LoggingPasswordResetNotifier.class);
    private final AuthProperties properties;
    private final boolean logLinks;

    public LoggingPasswordResetNotifier(
            AuthProperties properties, @Value("${sherlock.mail.log-reset-links:false}") boolean logLinks) {
        this.properties = properties;
        this.logLinks = logLinks;
    }

    @Override
    public void send(String email, String token) {
        if (logLinks) {
            LOG.warn("[DEV] Password reset link for {}: {}", email, ResetLinks.build(properties.resetUrl(), token));
        } else {
            LOG.warn("Password-reset email delivery is disabled; request accepted for {}", mask(email));
        }
    }

    private static String mask(String email) {
        int separator = email.indexOf('@');
        return separator < 1 ? "***" : email.charAt(0) + "***" + email.substring(separator);
    }
}
