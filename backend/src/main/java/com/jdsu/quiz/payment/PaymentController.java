package com.jdsu.quiz.payment;

import com.jdsu.quiz.security.JwtService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/razorpay/order")
    public PaymentService.OrderResponse createOrder(
            @AuthenticationPrincipal JwtService.JwtPrincipal principal,
            @RequestBody PaymentService.OrderRequest request
    ) {
        return paymentService.createOrder(principal, request);
    }

    @PostMapping("/razorpay/activate")
    public PaymentService.ActivationResponse activate(
            @AuthenticationPrincipal JwtService.JwtPrincipal principal,
            @RequestBody PaymentService.ActivateRequest request
    ) {
        return paymentService.activateFromClient(principal, request);
    }

    @PostMapping("/webhooks/razorpay")
    public PaymentService.WebhookResponse webhook(
            @RequestBody String body,
            @RequestHeader("X-Razorpay-Signature") String signature
    ) {
        return paymentService.handleWebhook(body, signature);
    }

    @GetMapping("/invoices")
    public List<PaymentService.InvoiceResponse> invoices(@AuthenticationPrincipal JwtService.JwtPrincipal principal) {
        return paymentService.invoices(principal);
    }

    @GetMapping("/invoices/{invoiceNumber}/download")
    public ResponseEntity<String> downloadInvoice(
            @AuthenticationPrincipal JwtService.JwtPrincipal principal,
            @PathVariable String invoiceNumber
    ) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(invoiceNumber + ".html")
                        .build()
                        .toString())
                .body(paymentService.invoiceHtml(principal, invoiceNumber));
    }
}
