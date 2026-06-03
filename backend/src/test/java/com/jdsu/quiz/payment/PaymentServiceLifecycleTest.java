package com.jdsu.quiz.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdsu.quiz.events.EventPublisher;
import com.jdsu.quiz.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentServiceLifecycleTest {
    private JdbcTemplate jdbcTemplate;
    private PaymentService paymentService;
    private JwtService.JwtPrincipal principal;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:subscription_lifecycle;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
        jdbcTemplate = new JdbcTemplate(dataSource);
        createTables();
        seedPlans();
        jdbcTemplate.update("INSERT INTO users (id, name, email, plan, created_at) VALUES (1, 'JagdiSu User', 'user@jagdisu.test', 'FREE', CURRENT_TIMESTAMP)");
        EventPublisher eventPublisher = event -> {
        };
        paymentService = new PaymentService(jdbcTemplate, new ObjectMapper(), eventPublisher, "", "", "", "INR");
        principal = new JwtService.JwtPrincipal(1L, "user@jagdisu.test", List.of("STUDENT"));
    }

    @Test
    void completesPaidPlanChangesCancelAndReactivate() {
        assertThat(currentUserPlan()).isEqualTo("FREE");

        activate("PRO");
        assertThat(currentUserPlan()).isEqualTo("PRO");
        assertThat(activeSubscriptionStatus()).isEqualTo("ACTIVE");

        activate("ADVANCED");
        assertThat(currentUserPlan()).isEqualTo("ADVANCED");
        assertThat(activeSubscriptionStatus()).isEqualTo("ACTIVE");

        activate("PRO");
        assertThat(currentUserPlan()).isEqualTo("PRO");
        assertThat(activeSubscriptionStatus()).isEqualTo("ACTIVE");

        PaymentService.SubscriptionActionResponse cancel = paymentService.cancel(principal);
        assertThat(cancel.status()).isEqualTo("CANCELED");
        assertThat(activeSubscriptionStatus()).isEqualTo("CANCELED");

        PaymentService.SubscriptionActionResponse reactivate = paymentService.reactivate(principal);
        assertThat(reactivate.status()).isEqualTo("ACTIVE");
        assertThat(activeSubscriptionStatus()).isEqualTo("ACTIVE");

        Integer paidInvoices = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM invoices WHERE status = 'PAID'", Integer.class);
        assertThat(paidInvoices).isEqualTo(3);
    }

    private void activate(String planCode) {
        PaymentService.OrderResponse order = paymentService.createOrder(principal, new PaymentService.OrderRequest(planCode));
        assertThat(order.status()).isEqualTo("MOCK_CREATED");
        PaymentService.ActivationResponse activation = paymentService.activateFromClient(
                principal,
                new PaymentService.ActivateRequest(order.razorpayOrderId(), "pay_mock_" + planCode.toLowerCase(), "mock_signature")
        );
        assertThat(activation.status()).isEqualTo("ACTIVE");
        assertThat(activation.planCode()).isEqualTo(planCode);
    }

    private String currentUserPlan() {
        return jdbcTemplate.queryForObject("SELECT plan FROM users WHERE id = 1", String.class);
    }

    private String activeSubscriptionStatus() {
        return jdbcTemplate.queryForObject("""
                        SELECT status
                        FROM user_subscriptions
                        WHERE user_id = 1 AND status IN ('ACTIVE', 'CANCELED')
                        ORDER BY id DESC
                        LIMIT 1
                        """,
                String.class);
    }

    private void createTables() {
        jdbcTemplate.execute("""
                CREATE TABLE users (
                    id BIGINT PRIMARY KEY,
                    name VARCHAR(255),
                    email VARCHAR(255),
                    plan VARCHAR(32),
                    created_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE subscription_plans (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    code VARCHAR(32) NOT NULL UNIQUE,
                    name VARCHAR(100) NOT NULL,
                    billing_cycle VARCHAR(32) NOT NULL,
                    price_inr DECIMAL(10,2) NOT NULL,
                    ai_quota INT NOT NULL DEFAULT 0,
                    quiz_quota INT NOT NULL DEFAULT 0,
                    notes_quota INT NOT NULL DEFAULT 0,
                    is_active BOOLEAN NOT NULL DEFAULT TRUE
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE user_subscriptions (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    plan_id BIGINT NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    starts_at TIMESTAMP,
                    ends_at TIMESTAMP,
                    canceled_at TIMESTAMP,
                    reactivated_at TIMESTAMP,
                    created_at TIMESTAMP,
                    updated_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE payments (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    provider VARCHAR(64),
                    provider_order_id VARCHAR(255),
                    provider_payment_id VARCHAR(255),
                    amount DECIMAL(10,2),
                    currency VARCHAR(8),
                    status VARCHAR(32),
                    provider_signature VARCHAR(255),
                    metadata_json CLOB,
                    created_at TIMESTAMP,
                    updated_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE invoices (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    payment_id BIGINT NOT NULL,
                    invoice_number VARCHAR(80),
                    amount DECIMAL(10,2),
                    currency VARCHAR(8),
                    status VARCHAR(32),
                    invoice_json CLOB,
                    created_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE user_plan_entitlements (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    plan_code VARCHAR(32),
                    feature VARCHAR(64),
                    limit_value INT,
                    period VARCHAR(32)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE user_usage_events (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    user_id BIGINT,
                    feature VARCHAR(64),
                    units INT,
                    created_at TIMESTAMP
                )
                """);
    }

    private void seedPlans() {
        jdbcTemplate.update("""
                INSERT INTO subscription_plans (code, name, billing_cycle, price_inr, ai_quota, quiz_quota, notes_quota)
                VALUES
                ('FREE', 'Free', 'MONTHLY', 0, 10000, 10, 5),
                ('PRO', 'Pro', 'MONTHLY', 20, 50000, 1000, 300),
                ('ADVANCED', 'Advanced', 'MONTHLY', 49, 200000, 5000, 1500)
                """);
        jdbcTemplate.update("""
                INSERT INTO user_plan_entitlements (plan_code, feature, limit_value, period)
                VALUES
                ('FREE', 'AI_TOKENS', 10000, 'MONTHLY'),
                ('FREE', 'NOTES', 5, 'DAILY'),
                ('FREE', 'QUIZ', 10, 'DAILY'),
                ('PRO', 'AI_TOKENS', 50000, 'MONTHLY'),
                ('PRO', 'NOTES', 300, 'MONTHLY'),
                ('PRO', 'QUIZ', 1000, 'MONTHLY'),
                ('ADVANCED', 'AI_TOKENS', 200000, 'MONTHLY'),
                ('ADVANCED', 'NOTES', 1500, 'MONTHLY'),
                ('ADVANCED', 'QUIZ', 5000, 'MONTHLY')
                """);
    }
}
