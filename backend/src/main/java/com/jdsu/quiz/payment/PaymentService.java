package com.jdsu.quiz.payment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdsu.quiz.events.EventPublisher;
import com.jdsu.quiz.events.PlatformEvent;
import com.jdsu.quiz.security.JwtService;
import io.micrometer.observation.annotation.Observed;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class PaymentService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final EventPublisher eventPublisher;
    private final String keyId;
    private final String keySecret;
    private final String webhookSecret;
    private final String currency;
    private final WebClient webClient;

    public PaymentService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            EventPublisher eventPublisher,
            @Value("${razorpay.key-id:}") String keyId,
            @Value("${razorpay.key-secret:}") String keySecret,
            @Value("${razorpay.webhook-secret:}") String webhookSecret,
            @Value("${razorpay.currency:INR}") String currency
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.keyId = keyId == null ? "" : keyId.trim();
        this.keySecret = keySecret == null ? "" : keySecret.trim();
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
        this.currency = currency == null || currency.isBlank() ? "INR" : currency.trim().toUpperCase(Locale.ROOT);
        this.webClient = WebClient.builder()
                .baseUrl("https://api.razorpay.com/v1")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public record OrderRequest(String planCode) {
    }

    public record OrderResponse(
            long paymentId,
            String razorpayOrderId,
            String keyId,
            int amountPaise,
            String currency,
            String status,
            String planCode
    ) {
    }

    public record ActivateRequest(String razorpayOrderId, String razorpayPaymentId, String razorpaySignature) {
    }

    public record ActivationResponse(String status, String planCode, String endsAt, String invoiceNumber) {
    }

    public record WebhookResponse(String status, String event, String orderId, String paymentId) {
    }

    public record InvoiceResponse(String invoiceNumber, BigDecimal amount, String currency, String status, String createdAt) {
    }

    public record UsageResponse(String feature, int used, int limit, int remaining, String period) {
    }

    public record EntitlementResponse(String feature, int limit, String period) {
    }

    public record SubscriptionOverview(
            String planCode,
            String planName,
            String status,
            String startsAt,
            String endsAt,
            String renewalDate,
            BigDecimal monthlyPriceInr,
            List<UsageResponse> usage,
            List<EntitlementResponse> entitlements,
            List<InvoiceResponse> invoices
    ) {
    }

    public record SubscriptionActionResponse(String status, String planCode, String endsAt, String message) {
    }

    public record UsageRecordRequest(String feature, int units) {
    }

    public record AdminBillingSummary(
            BigDecimal revenue30d,
            int activeSubscriptions,
            int canceledSubscriptions,
            int payments30d,
            BigDecimal aiCost30d,
            List<Map<String, Object>> revenueByPlan,
            List<Map<String, Object>> paymentsByStatus
    ) {
    }

    private record PlanPrice(
            long id,
            String code,
            String name,
            String billingCycle,
            BigDecimal priceInr,
            int aiQuota,
            int quizQuota,
            int notesQuota
    ) {
    }

    private record PaymentPlan(long id, BigDecimal amount, String planCode) {
    }

    private record CurrentSubscription(
            String planCode,
            String planName,
            String status,
            String startsAt,
            String endsAt,
            BigDecimal priceInr
    ) {
    }

    @Observed(name = "payment.razorpay.order")
    @Transactional
    public OrderResponse createOrder(JwtService.JwtPrincipal principal, OrderRequest request) {
        long userId = requirePrincipal(principal);
        PlanPrice plan = planPrice(request == null ? null : request.planCode());
        if ("FREE".equals(plan.code())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Free plan does not require payment");
        }

        int amountPaise = plan.priceInr().multiply(BigDecimal.valueOf(100)).intValueExact();
        String receipt = "jdsu_" + userId + "_" + plan.code().toLowerCase(Locale.ROOT) + "_" + System.currentTimeMillis();
        String orderId;
        String orderStatus;
        if (keyId.isBlank() || keySecret.isBlank()) {
            orderId = "order_mock_" + receipt;
            orderStatus = "MOCK_CREATED";
        } else {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("amount", amountPaise);
            payload.put("currency", currency);
            payload.put("receipt", receipt);
            payload.put("notes", Map.of("userId", String.valueOf(userId), "planCode", plan.code()));
            JsonNode response = webClient.post()
                    .uri("/orders")
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth())
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            if (response == null || !response.hasNonNull("id")) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Razorpay order creation failed");
            }
            orderId = response.path("id").asText();
            orderStatus = response.path("status").asText("CREATED").toUpperCase(Locale.ROOT);
        }

        String metadata = writeJson(Map.of(
                "planCode", plan.code(),
                "receipt", receipt,
                "razorpayStatus", orderStatus
        ));
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(paymentInsert(userId, orderId, plan.priceInr(), metadata), keyHolder);
        Number key = keyHolder.getKey();
        long paymentId = key == null ? 0L : key.longValue();
        return new OrderResponse(paymentId, orderId, keyId, amountPaise, currency, orderStatus, plan.code());
    }

    @Observed(name = "payment.razorpay.activate")
    @Transactional
    public ActivationResponse activateFromClient(JwtService.JwtPrincipal principal, ActivateRequest request) {
        long userId = requirePrincipal(principal);
        if (request == null || isBlank(request.razorpayOrderId()) || isBlank(request.razorpayPaymentId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing Razorpay payment details");
        }
        if (keySecret.isBlank()) {
            boolean mockPayment = request.razorpayOrderId().startsWith("order_mock_")
                    && (request.razorpayPaymentId().startsWith("pay_mock_") || "mock_signature".equals(request.razorpaySignature()));
            if (!mockPayment) {
                throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, "Razorpay key secret is not configured");
            }
        } else {
            String expected = hmac(request.razorpayOrderId() + "|" + request.razorpayPaymentId(), keySecret);
            if (!Objects.equals(expected, request.razorpaySignature())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Razorpay signature");
            }
        }
        return activatePayment(userId, request.razorpayOrderId(), request.razorpayPaymentId(), request.razorpaySignature(), "CAPTURED");
    }

    @Observed(name = "payment.razorpay.webhook")
    @Transactional
    public WebhookResponse handleWebhook(String body, String signature) {
        if (webhookSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, "Razorpay webhook secret is not configured");
        }
        if (!Objects.equals(hmac(body == null ? "" : body, webhookSecret), signature)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Razorpay webhook signature");
        }
        JsonNode root = readTree(body);
        String event = root.path("event").asText();
        JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
        String orderId = paymentEntity.path("order_id").asText(null);
        String paymentId = paymentEntity.path("id").asText(null);
        if ("payment.captured".equals(event) && !isBlank(orderId) && !isBlank(paymentId)) {
            Long userId = queryLong("SELECT user_id FROM payments WHERE provider_order_id = ? ORDER BY id DESC LIMIT 1", orderId);
            if (userId != null) {
                activatePayment(userId, orderId, paymentId, signature, "CAPTURED");
            }
        } else if ("payment.failed".equals(event) && !isBlank(orderId)) {
            jdbcTemplate.update("""
                            UPDATE payments
                            SET status = 'FAILED', provider_payment_id = ?, updated_at = CURRENT_TIMESTAMP
                            WHERE provider_order_id = ?
                            """,
                    paymentId, orderId);
        }
        return new WebhookResponse("OK", event, orderId, paymentId);
    }

    public List<InvoiceResponse> invoices(JwtService.JwtPrincipal principal) {
        long userId = requirePrincipal(principal);
        return invoicesForUser(userId);
    }

    public String invoiceHtml(JwtService.JwtPrincipal principal, String invoiceNumber) {
        long userId = requirePrincipal(principal);
        Map<String, Object> invoice = jdbcTemplate.queryForMap("""
                        SELECT i.invoice_number, i.amount, i.currency, i.status, i.created_at, u.email, u.name
                        FROM invoices i
                        JOIN users u ON u.id = i.user_id
                        WHERE i.user_id = ? AND i.invoice_number = ?
                        """,
                userId, invoiceNumber);
        return """
                <!doctype html>
                <html><head><meta charset="utf-8"><title>JagdiSu Invoice</title></head>
                <body style="font-family:Arial,sans-serif;color:#17202a;padding:32px">
                  <h1>JagdiSu Invoice</h1>
                  <p><strong>Invoice:</strong> %s</p>
                  <p><strong>Customer:</strong> %s (%s)</p>
                  <p><strong>Amount:</strong> %s %s</p>
                  <p><strong>Status:</strong> %s</p>
                  <p><strong>Date:</strong> %s</p>
                </body></html>
                """.formatted(
                escape(String.valueOf(invoice.get("invoice_number"))),
                escape(String.valueOf(invoice.getOrDefault("name", ""))),
                escape(String.valueOf(invoice.getOrDefault("email", ""))),
                escape(String.valueOf(invoice.get("currency"))),
                escape(String.valueOf(invoice.get("amount"))),
                escape(String.valueOf(invoice.get("status"))),
                escape(String.valueOf(invoice.get("created_at")))
        );
    }

    public SubscriptionOverview currentSubscription(JwtService.JwtPrincipal principal) {
        if (principal == null) {
            return freeOverview(0L);
        }
        return overviewForUser(principal.userId());
    }

    @Transactional
    public SubscriptionActionResponse cancel(JwtService.JwtPrincipal principal) {
        long userId = requirePrincipal(principal);
        CurrentSubscription current = currentSubscriptionRow(userId);
        if (current == null || "FREE".equals(current.planCode()) || !"ACTIVE".equals(current.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No active paid subscription found");
        }
        jdbcTemplate.update("""
                        UPDATE user_subscriptions
                        SET status = 'CANCELED', canceled_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                        WHERE user_id = ? AND status = 'ACTIVE'
                        """,
                userId);
        publish("subscription.canceled", userId, current.planCode(), current.endsAt());
        return new SubscriptionActionResponse("CANCELED", current.planCode(), current.endsAt(), "Subscription canceled. Access remains until renewal date.");
    }

    @Transactional
    public SubscriptionActionResponse reactivate(JwtService.JwtPrincipal principal) {
        long userId = requirePrincipal(principal);
        CurrentSubscription current = currentSubscriptionRow(userId);
        if (current == null || "FREE".equals(current.planCode()) || !"CANCELED".equals(current.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No canceled subscription found");
        }
        jdbcTemplate.update("""
                        UPDATE user_subscriptions
                        SET status = 'ACTIVE', reactivated_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                        WHERE user_id = ? AND status = 'CANCELED' AND ends_at > CURRENT_TIMESTAMP
                        """,
                userId);
        jdbcTemplate.update("UPDATE users SET plan = ? WHERE id = ?", current.planCode(), userId);
        publish("subscription.reactivated", userId, current.planCode(), current.endsAt());
        return new SubscriptionActionResponse("ACTIVE", current.planCode(), current.endsAt(), "Subscription reactivated.");
    }

    @Transactional
    public UsageResponse recordUsage(JwtService.JwtPrincipal principal, UsageRecordRequest request) {
        long userId = requirePrincipal(principal);
        String feature = normalizeFeature(request == null ? null : request.feature());
        int units = request == null || request.units() <= 0 ? 1 : request.units();
        jdbcTemplate.update("""
                        INSERT INTO user_usage_events (user_id, feature, units, created_at)
                        VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                        """,
                userId, feature, units);
        return usageForFeature(userId, currentPlanCode(userId), feature);
    }

    public AdminBillingSummary adminBillingSummary() {
        BigDecimal revenue30d = queryBigDecimal("""
                SELECT COALESCE(SUM(amount), 0) FROM payments
                WHERE status IN ('CAPTURED', 'PAID') AND created_at >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 30 DAY)
                """);
        Integer active = queryInt("SELECT COUNT(*) FROM user_subscriptions WHERE status = 'ACTIVE' AND ends_at > CURRENT_TIMESTAMP");
        Integer canceled = queryInt("SELECT COUNT(*) FROM user_subscriptions WHERE status = 'CANCELED' AND ends_at > CURRENT_TIMESTAMP");
        Integer payments30d = queryInt("SELECT COUNT(*) FROM payments WHERE created_at >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 30 DAY)");
        BigDecimal aiCost30d = queryBigDecimal("""
                SELECT COALESCE(SUM(cost_usd), 0) FROM ai_cost_tracking
                WHERE created_at >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 30 DAY)
                """);
        List<Map<String, Object>> revenueByPlan = jdbcTemplate.queryForList("""
                SELECT sp.code AS plan, COALESCE(SUM(p.amount), 0) AS revenue
                FROM payments p
                JOIN user_subscriptions us ON us.user_id = p.user_id
                JOIN subscription_plans sp ON sp.id = us.plan_id
                WHERE p.status IN ('CAPTURED', 'PAID')
                GROUP BY sp.code
                ORDER BY revenue DESC
                """);
        List<Map<String, Object>> paymentsByStatus = jdbcTemplate.queryForList("""
                SELECT status, COUNT(*) AS count, COALESCE(SUM(amount), 0) AS amount
                FROM payments
                GROUP BY status
                ORDER BY count DESC
                """);
        return new AdminBillingSummary(
                revenue30d,
                active == null ? 0 : active,
                canceled == null ? 0 : canceled,
                payments30d == null ? 0 : payments30d,
                aiCost30d,
                revenueByPlan,
                paymentsByStatus
        );
    }

    @Transactional
    ActivationResponse activatePayment(long userId, String orderId, String paymentId, String signature, String status) {
        PaymentPlan payment = paymentPlan(orderId);
        PlanPrice plan = planPrice(payment.planCode());
        Instant now = Instant.now();
        Instant endsAt = endsAt(now, plan.billingCycle());
        jdbcTemplate.update("""
                        UPDATE user_subscriptions
                        SET status = 'REPLACED', ends_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                        WHERE user_id = ? AND status IN ('ACTIVE', 'CANCELED')
                        """,
                userId);
        jdbcTemplate.update("""
                        INSERT INTO user_subscriptions (user_id, plan_id, status, starts_at, ends_at, created_at, updated_at)
                        VALUES (?, ?, 'ACTIVE', ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                userId, plan.id(), Timestamp.from(now), Timestamp.from(endsAt));
        jdbcTemplate.update("""
                        UPDATE payments
                        SET provider_payment_id = ?, status = ?, provider_signature = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                        """,
                paymentId, status, signature, payment.id());
        jdbcTemplate.update("UPDATE users SET plan = ? WHERE id = ?", plan.code(), userId);
        String invoiceNumber = "JDSU-" + now.getEpochSecond() + "-" + payment.id();
        jdbcTemplate.update("""
                        INSERT INTO invoices (user_id, payment_id, invoice_number, amount, currency, status, invoice_json, created_at)
                        VALUES (?, ?, ?, ?, ?, 'PAID', ?, CURRENT_TIMESTAMP)
                        """,
                userId, payment.id(), invoiceNumber, payment.amount(), currency,
                writeJson(Map.of("invoiceNumber", invoiceNumber, "planCode", plan.code(), "amount", payment.amount(), "currency", currency)));
        publish("subscription.activated", userId, plan.code(), endsAt.toString());
        return new ActivationResponse("ACTIVE", plan.code(), endsAt.toString(), invoiceNumber);
    }

    private SubscriptionOverview overviewForUser(long userId) {
        CurrentSubscription current = currentSubscriptionRow(userId);
        if (current == null) {
            return freeOverview(userId);
        }
        List<EntitlementResponse> entitlements = entitlements(current.planCode());
        List<UsageResponse> usage = new ArrayList<>();
        for (EntitlementResponse entitlement : entitlements) {
            usage.add(usageForFeature(userId, current.planCode(), entitlement.feature()));
        }
        String renewalDate = "ACTIVE".equals(current.status()) && !"FREE".equals(current.planCode()) ? current.endsAt() : null;
        return new SubscriptionOverview(
                current.planCode(),
                current.planName(),
                current.status(),
                current.startsAt(),
                current.endsAt(),
                renewalDate,
                current.priceInr(),
                usage,
                entitlements,
                invoicesForUser(userId)
        );
    }

    private SubscriptionOverview freeOverview(long userId) {
        List<EntitlementResponse> entitlements = entitlements("FREE");
        List<UsageResponse> usage = new ArrayList<>();
        for (EntitlementResponse entitlement : entitlements) {
            usage.add(userId > 0 ? usageForFeature(userId, "FREE", entitlement.feature()) :
                    new UsageResponse(entitlement.feature(), 0, entitlement.limit(), entitlement.limit(), entitlement.period()));
        }
        return new SubscriptionOverview("FREE", "Free", "ACTIVE", null, null, null, BigDecimal.ZERO, usage, entitlements, userId > 0 ? invoicesForUser(userId) : List.of());
    }

    private CurrentSubscription currentSubscriptionRow(long userId) {
        try {
            return jdbcTemplate.queryForObject("""
                            SELECT sp.code, sp.name, us.status, us.starts_at, us.ends_at, sp.price_inr
                            FROM user_subscriptions us
                            JOIN subscription_plans sp ON sp.id = us.plan_id
                            WHERE us.user_id = ? AND us.status IN ('ACTIVE', 'CANCELED') AND us.ends_at > CURRENT_TIMESTAMP
                            ORDER BY us.id DESC
                            LIMIT 1
                            """,
                    (rs, rowNum) -> new CurrentSubscription(
                            rs.getString("code"),
                            rs.getString("name"),
                            rs.getString("status"),
                            String.valueOf(rs.getTimestamp("starts_at").toInstant()),
                            String.valueOf(rs.getTimestamp("ends_at").toInstant()),
                            rs.getBigDecimal("price_inr")
                    ),
                    userId);
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private PlanPrice planPrice(String requestedPlanCode) {
        String planCode = normalizePlanCode(requestedPlanCode);
        try {
            return jdbcTemplate.queryForObject("""
                            SELECT id, code, name, billing_cycle, price_inr,
                                   COALESCE(ai_quota, 0) AS ai_quota,
                                   COALESCE(quiz_quota, 0) AS quiz_quota,
                                   COALESCE(notes_quota, 0) AS notes_quota
                            FROM subscription_plans
                            WHERE code = ? AND is_active = TRUE
                            """,
                    (rs, rowNum) -> new PlanPrice(
                            rs.getLong("id"),
                            rs.getString("code"),
                            rs.getString("name"),
                            rs.getString("billing_cycle"),
                            rs.getBigDecimal("price_inr"),
                            rs.getInt("ai_quota"),
                            rs.getInt("quiz_quota"),
                            rs.getInt("notes_quota")
                    ),
                    planCode);
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown subscription plan: " + planCode);
        }
    }

    private PaymentPlan paymentPlan(String orderId) {
        try {
            return jdbcTemplate.queryForObject("""
                            SELECT id, amount, metadata_json
                            FROM payments
                            WHERE provider_order_id = ?
                            ORDER BY id DESC
                            LIMIT 1
                            """,
                    (rs, rowNum) -> {
                        String metadata = rs.getString("metadata_json");
                        String planCode = "PRO";
                        if (metadata != null && !metadata.isBlank()) {
                            Map<String, Object> values = readMap(metadata);
                            Object value = values.get("planCode");
                            if (value != null) {
                                planCode = String.valueOf(value);
                            }
                        }
                        return new PaymentPlan(rs.getLong("id"), rs.getBigDecimal("amount"), planCode);
                    },
                    orderId);
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment order not found");
        }
    }

    private PreparedStatementCreator paymentInsert(long userId, String orderId, BigDecimal amount, String metadata) {
        return connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                            INSERT INTO payments (user_id, provider, provider_order_id, amount, currency, status, metadata_json, created_at)
                            VALUES (?, 'RAZORPAY', ?, ?, ?, 'CREATED', ?, CURRENT_TIMESTAMP)
                            """,
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, userId);
            statement.setString(2, orderId);
            statement.setBigDecimal(3, amount);
            statement.setString(4, currency);
            statement.setString(5, metadata);
            return statement;
        };
    }

    private List<InvoiceResponse> invoicesForUser(long userId) {
        return jdbcTemplate.query("""
                        SELECT invoice_number, amount, currency, status, created_at
                        FROM invoices
                        WHERE user_id = ?
                        ORDER BY created_at DESC
                        """,
                (rs, rowNum) -> new InvoiceResponse(
                        rs.getString("invoice_number"),
                        rs.getBigDecimal("amount"),
                        rs.getString("currency"),
                        rs.getString("status"),
                        String.valueOf(rs.getTimestamp("created_at").toInstant())
                ),
                userId);
    }

    private List<EntitlementResponse> entitlements(String planCode) {
        return jdbcTemplate.query("""
                        SELECT feature, limit_value, period
                        FROM user_plan_entitlements
                        WHERE plan_code = ?
                        ORDER BY feature
                        """,
                (rs, rowNum) -> new EntitlementResponse(
                        rs.getString("feature"),
                        rs.getInt("limit_value"),
                        rs.getString("period")
                ),
                normalizePlanCode(planCode));
    }

    private UsageResponse usageForFeature(long userId, String planCode, String feature) {
        EntitlementResponse entitlement = entitlements(planCode).stream()
                .filter(item -> item.feature().equals(normalizeFeature(feature)))
                .findFirst()
                .orElse(new EntitlementResponse(normalizeFeature(feature), 0, "MONTHLY"));
        String sql = switch (entitlement.period()) {
            case "DAILY" -> """
                    SELECT COALESCE(SUM(units), 0) FROM user_usage_events
                    WHERE user_id = ? AND feature = ? AND created_at >= CURRENT_DATE
                    """;
            default -> """
                    SELECT COALESCE(SUM(units), 0) FROM user_usage_events
                    WHERE user_id = ? AND feature = ? AND created_at >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 30 DAY)
                    """;
        };
        Integer used = jdbcTemplate.queryForObject(sql, Integer.class, userId, entitlement.feature());
        int usedValue = used == null ? 0 : used;
        int remaining = Math.max(0, entitlement.limit() - usedValue);
        return new UsageResponse(entitlement.feature(), usedValue, entitlement.limit(), remaining, entitlement.period());
    }

    private String currentPlanCode(long userId) {
        CurrentSubscription current = currentSubscriptionRow(userId);
        return current == null ? "FREE" : current.planCode();
    }

    private Instant endsAt(Instant startsAt, String billingCycle) {
        String cycle = billingCycle == null ? "MONTHLY" : billingCycle.toUpperCase(Locale.ROOT);
        return switch (cycle) {
            case "YEARLY" -> startsAt.plus(365, ChronoUnit.DAYS);
            case "WEEKLY" -> startsAt.plus(7, ChronoUnit.DAYS);
            default -> startsAt.plus(30, ChronoUnit.DAYS);
        };
    }

    private String normalizePlanCode(String planCode) {
        if (planCode == null || planCode.isBlank()) {
            return "PRO";
        }
        String normalized = planCode.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "MONTHLY", "BASIC" -> "PRO";
            case "ANNUAL", "YEARLY", "PREMIUM" -> "ADVANCED";
            default -> normalized;
        };
    }

    private String normalizeFeature(String feature) {
        if (feature == null || feature.isBlank()) {
            return "AI_TOKENS";
        }
        String normalized = feature.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "AI", "AI_USAGE", "TOKENS" -> "AI_TOKENS";
            case "QUIZZES" -> "QUIZ";
            case "NOTE", "NOTES_USAGE" -> "NOTES";
            default -> normalized;
        };
    }

    private long requirePrincipal(JwtService.JwtPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal.userId();
    }

    private Long queryLong(String sql, Object... args) {
        try {
            return jdbcTemplate.queryForObject(sql, Long.class, args);
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private Integer queryInt(String sql, Object... args) {
        try {
            return jdbcTemplate.queryForObject(sql, Integer.class, args);
        } catch (EmptyResultDataAccessException ignored) {
            return 0;
        }
    }

    private BigDecimal queryBigDecimal(String sql, Object... args) {
        try {
            BigDecimal value = jdbcTemplate.queryForObject(sql, BigDecimal.class, args);
            return value == null ? BigDecimal.ZERO : value;
        } catch (Exception ignored) {
            return BigDecimal.ZERO;
        }
    }

    private String hmac(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to verify signature", ex);
        }
    }

    private String basicAuth() {
        return Base64.getEncoder().encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode readTree(String body) {
        try {
            return objectMapper.readTree(body == null ? "{}" : body);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook payload", ex);
        }
    }

    private Map<String, Object> readMap(String body) {
        try {
            return objectMapper.readValue(body, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private void publish(String type, long userId, String planCode, String endsAt) {
        eventPublisher.publish(PlatformEvent.of(
                type,
                Map.of("userId", userId, "planCode", planCode, "endsAt", endsAt == null ? "" : endsAt)
        ));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String escape(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
