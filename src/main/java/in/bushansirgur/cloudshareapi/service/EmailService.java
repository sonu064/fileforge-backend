package in.bushansirgur.cloudshareapi.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends transactional emails. When SMTP is not configured (spring.mail.host blank) it falls back to
 * logging the message so the verification / reset flow still works end-to-end in local development.
 */
@Slf4j
@Service
public class EmailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String mailHost;
    private final String from;
    private final String frontendUrl;
    private final boolean devExposeTokens;

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                        @Value("${spring.mail.host:}") String mailHost,
                        @Value("${app.mail.from}") String from,
                        @Value("${app.frontend-url}") String frontendUrl,
                        @Value("${app.auth.dev-expose-tokens}") boolean devExposeTokens) {
        this.mailSenderProvider = mailSenderProvider;
        this.mailHost = mailHost;
        this.from = from;
        this.frontendUrl = frontendUrl;
        this.devExposeTokens = devExposeTokens;
    }

    public void sendVerificationEmail(String to, String token) {
        String link = frontendUrl + "/verify-email?token=" + token;
        send(to, "Verify your CloudShare email",
                "Welcome to CloudShare!\n\nPlease verify your email by opening the link below:\n" + link
                        + "\n\nThis link expires in 24 hours.");
    }

    public void sendPasswordResetEmail(String to, String token) {
        String link = frontendUrl + "/reset-password?token=" + token;
        send(to, "Reset your CloudShare password",
                "We received a request to reset your password.\n\nReset it using the link below:\n" + link
                        + "\n\nThis link expires in 1 hour. If you didn't request this, you can ignore this email.");
    }

    private void send(String to, String subject, String body) {
        boolean smtpConfigured = mailHost != null && !mailHost.isBlank();
        JavaMailSender sender = smtpConfigured ? mailSenderProvider.getIfAvailable() : null;

        if (sender == null) {
            log.warn("===== EMAIL (SMTP not configured) =====");
            log.warn("To: {}", to);
            log.warn("Subject: {}", subject);
            if (devExposeTokens) {
                log.warn("Body:\n{}", body);
            } else {
                log.warn("Body: [redacted — configure SMTP in production]");
            }
            log.warn("=======================================");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
            log.info("Sent '{}' email to {}", subject, to);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}
