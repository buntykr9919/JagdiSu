package com.jdsu.quiz.subscription;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/subscription")
public class SubscriptionController {
    @GetMapping("/me")
    public SubscriptionResponse currentPlan() {
        return new SubscriptionResponse("FREE", 10, 0, false, "Free plan includes limited quiz generations per day.");
    }

    @PostMapping("/subscribe")
    public SubscriptionResponse subscribe(@RequestBody SubscriptionRequest request) {
        if (request.paymentReference() == null || request.paymentReference().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UPI payment reference is required to activate a paid plan.");
        }

        String plan = "ADVANCED".equalsIgnoreCase(request.plan()) ? "ADVANCED" : "PRO";
        int monthlyPriceInr = "ADVANCED".equals(plan) ? 49 : 20;
        return new SubscriptionResponse(
                plan,
                0,
                monthlyPriceInr,
                false,
                plan + " subscription active through " + normalizePaymentMethod(request.paymentMethod()) + "."
        );
    }

    public record SubscriptionResponse(String plan, int dailyQuizLimit, int monthlyPriceInr, boolean paymentRequired, String note) {
    }

    public record SubscriptionRequest(String plan, String paymentMethod, String paymentReference) {
    }

    private String normalizePaymentMethod(String paymentMethod) {
        return paymentMethod == null || paymentMethod.isBlank() ? "UPI" : paymentMethod.trim().toUpperCase();
    }
}
