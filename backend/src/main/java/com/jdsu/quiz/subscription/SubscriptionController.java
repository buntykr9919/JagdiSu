package com.jdsu.quiz.subscription;

import com.jdsu.quiz.payment.PaymentService;
import com.jdsu.quiz.security.JwtService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/subscription")
public class SubscriptionController {
    private final PaymentService paymentService;

    public SubscriptionController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/me")
    public PaymentService.SubscriptionOverview currentPlan(@AuthenticationPrincipal JwtService.JwtPrincipal principal) {
        return paymentService.currentSubscription(principal);
    }

    @PostMapping("/subscribe")
    public PaymentService.OrderResponse subscribe(
            @AuthenticationPrincipal JwtService.JwtPrincipal principal,
            @RequestBody PaymentService.OrderRequest request
    ) {
        return paymentService.createOrder(principal, request);
    }

    @PostMapping("/activate")
    public PaymentService.ActivationResponse activate(
            @AuthenticationPrincipal JwtService.JwtPrincipal principal,
            @RequestBody PaymentService.ActivateRequest request
    ) {
        return paymentService.activateFromClient(principal, request);
    }

    @PostMapping("/cancel")
    public PaymentService.SubscriptionActionResponse cancel(@AuthenticationPrincipal JwtService.JwtPrincipal principal) {
        return paymentService.cancel(principal);
    }

    @PostMapping("/reactivate")
    public PaymentService.SubscriptionActionResponse reactivate(@AuthenticationPrincipal JwtService.JwtPrincipal principal) {
        return paymentService.reactivate(principal);
    }

    @PostMapping("/usage")
    public PaymentService.UsageResponse recordUsage(
            @AuthenticationPrincipal JwtService.JwtPrincipal principal,
            @RequestBody PaymentService.UsageRecordRequest request
    ) {
        return paymentService.recordUsage(principal, request);
    }
}
