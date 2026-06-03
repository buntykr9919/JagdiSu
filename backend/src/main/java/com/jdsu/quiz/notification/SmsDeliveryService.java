package com.jdsu.quiz.notification;

import com.jdsu.quiz.events.EventPublisher;
import com.jdsu.quiz.events.PlatformEvent;
import io.micrometer.observation.annotation.Observed;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
public class SmsDeliveryService {
    private final boolean enabled;
    private final String gatewayUrl;
    private final String apiKey;
    private final String senderId;
    private final WebClient webClient;
    private final EventPublisher eventPublisher;

    public SmsDeliveryService(
            @Value("${app.notification.sms.enabled:false}") boolean enabled,
            @Value("${app.notification.sms.gateway-url:}") String gatewayUrl,
            @Value("${app.notification.sms.api-key:}") String apiKey,
            @Value("${app.notification.sms.sender-id:JAGDSU}") String senderId,
            EventPublisher eventPublisher
    ) {
        this.enabled = enabled;
        this.gatewayUrl = gatewayUrl == null ? "" : gatewayUrl.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.senderId = senderId == null ? "JAGDSU" : senderId.trim();
        this.webClient = WebClient.builder().build();
        this.eventPublisher = eventPublisher;
    }

    @Observed(name = "jagdisu.notification.sms.send")
    public boolean sendOtp(String mobile, String otp) {
        if (!enabled || gatewayUrl.isBlank() || mobile == null || mobile.isBlank()) {
            return false;
        }
        try {
            eventPublisher.publish(PlatformEvent.of("NotificationRequested", Map.of(
                    "channel", "SMS",
                    "target", mobile,
                    "template", "OTP"
            )));
            webClient.post()
                    .uri(gatewayUrl)
                    .header(HttpHeaders.AUTHORIZATION, apiKey.isBlank() ? "" : "Bearer " + apiKey)
                    .bodyValue(Map.of(
                            "to", mobile,
                            "senderId", senderId,
                            "message", "Your JagdiSu OTP is " + otp + ". It expires soon."
                    ))
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
