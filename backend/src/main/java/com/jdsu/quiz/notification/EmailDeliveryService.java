package com.jdsu.quiz.notification;

import com.jdsu.quiz.events.EventPublisher;
import com.jdsu.quiz.events.PlatformEvent;
import io.micrometer.observation.annotation.Observed;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class EmailDeliveryService {
    private final JavaMailSender mailSender;
    private final boolean enabled;
    private final String from;
    private final EventPublisher eventPublisher;

    public EmailDeliveryService(
            JavaMailSender mailSender,
            @Value("${app.notification.email.enabled:true}") boolean enabled,
            @Value("${app.notification.email.from:no-reply@jagdisu.local}") String from,
            EventPublisher eventPublisher
    ) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.from = from;
        this.eventPublisher = eventPublisher;
    }

    @Observed(name = "jagdisu.notification.email.send")
    public boolean send(String to, String subject, String body) {
        if (!enabled || to == null || to.isBlank()) {
            return false;
        }
        try {
            eventPublisher.publish(PlatformEvent.of("NotificationRequested", Map.of(
                    "channel", "EMAIL",
                    "target", to,
                    "subject", subject == null ? "" : subject
            )));
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
