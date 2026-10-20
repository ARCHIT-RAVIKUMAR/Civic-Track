package com.civictrack.service.impl;

import com.civictrack.entity.User;
import com.civictrack.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * OPTIONAL second implementation. Active only when civictrack.notification.mode=email AND
 * spring.mail.* (SMTP host, username, password) is configured. Needs an SMTP provider.
 */
@Service
@ConditionalOnProperty(name = "civictrack.notification.mode", havingValue = "email")
public class EmailNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final JavaMailSender mailSender;
    private final String from;

    public EmailNotificationService(JavaMailSender mailSender,
                                    @Value("${civictrack.notification.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void notifyUser(User recipient, String subject, String message) {
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(from);
            mail.setTo(recipient.getEmail());
            mail.setSubject(subject);
            mail.setText(message);
            mailSender.send(mail);
        } catch (Exception ex) {
            // A failed e-mail must never break the complaint workflow
            log.warn("Could not send e-mail notification: {}", ex.getMessage());
        }
    }
}
