package com.jdsu.quiz.auth;

import com.jdsu.quiz.security.JwtService;
import com.jdsu.quiz.security.MessageDigestUtil;
import com.jdsu.quiz.notification.EmailDeliveryService;
import com.jdsu.quiz.notification.SmsDeliveryService;
import com.jdsu.quiz.events.EventPublisher;
import com.jdsu.quiz.events.PlatformEvent;
import io.micrometer.observation.annotation.Observed;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {
    private final JdbcTemplate jdbcTemplate;
    private final PasswordHasher passwordHasher;
    private final JwtService jwtService;
    private final EmailDeliveryService emailDeliveryService;
    private final SmsDeliveryService smsDeliveryService;
    private final EventPublisher eventPublisher;
    private final RestClient restClient;
    private final StringRedisTemplate redisTemplate;
    private final String googleClientId;
    private final int refreshTokenTtlDays;
    private final int verificationTtlMinutes;
    private final int maxFailedAttempts;
    private final int accountLockMinutes;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            JdbcTemplate jdbcTemplate,
            PasswordHasher passwordHasher,
            JwtService jwtService,
            EmailDeliveryService emailDeliveryService,
            SmsDeliveryService smsDeliveryService,
            EventPublisher eventPublisher,
            ObjectProvider<StringRedisTemplate> redisTemplate,
            @Value("${app.google.client-id:}") String googleClientId,
            @Value("${app.security.jwt.refresh-token-ttl-days:30}") int refreshTokenTtlDays,
            @Value("${app.security.verification.token-ttl-minutes:15}") int verificationTtlMinutes,
            @Value("${app.security.account-lock.max-failed-attempts:5}") int maxFailedAttempts,
            @Value("${app.security.account-lock.lock-minutes:15}") int accountLockMinutes
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordHasher = passwordHasher;
        this.jwtService = jwtService;
        this.emailDeliveryService = emailDeliveryService;
        this.smsDeliveryService = smsDeliveryService;
        this.eventPublisher = eventPublisher;
        this.redisTemplate = redisTemplate.getIfAvailable();
        this.restClient = RestClient.create();
        this.googleClientId = googleClientId == null ? "" : googleClientId.trim();
        this.refreshTokenTtlDays = Math.max(1, refreshTokenTtlDays);
        this.verificationTtlMinutes = Math.max(1, verificationTtlMinutes);
        this.maxFailedAttempts = Math.max(1, maxFailedAttempts);
        this.accountLockMinutes = Math.max(1, accountLockMinutes);
    }

    @Transactional
    @Observed(name = "jagdisu.auth.signup")
    public UserResponse signup(SignupRequest request, DeviceContext deviceContext) {
        String name = firstPresent(request.name(), request.username());
        String email = normalizeEmail(request.email());
        String password = trimToNull(request.password());

        if (name == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required.");
        }
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required.");
        }
        validatePassword(password);

        try {
            jdbcTemplate.update(
                    "INSERT INTO users (name, email, password, mobile, plan) VALUES (?, ?, ?, ?, 'FREE')",
                    name,
                    email,
                    passwordHasher.hash(password),
                    trimToNull(request.mobile())
            );
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered.");
        }

        UserRecord user = findByIdentifier(email);
        ensureRole(user.id(), "STUDENT");
        eventPublisher.publish(PlatformEvent.of("UserRegistered", java.util.Map.of("userId", user.id(), "email", user.email())));
        VerificationToken emailToken = createVerificationToken(user.id(), "EMAIL", email, "EMAIL_VERIFICATION");
        emailDeliveryService.send(email, "Verify your JagdiSu email", "Your JagdiSu email verification token is: " + emailToken.plainToken());
        return toResponse(user, deviceContext, "Signup successful. Verify email with the issued token.", emailToken.plainToken());
    }

    @Transactional
    @Observed(name = "jagdisu.auth.login")
    public UserResponse login(LoginRequest request, DeviceContext deviceContext) {
        String identifier = trimToNull(request.usernameOrEmail());
        String password = trimToNull(request.password());

        if (identifier == null || password == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username/email and password are required.");
        }

        UserRecord user = findByIdentifier(identifier);
        enforceAccountNotLocked(user);
        if (!passwordHasher.matches(password, user.password())) {
            registerFailedLogin(user.id());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid login details.");
        }

        jdbcTemplate.update("UPDATE users SET failed_login_attempts = 0, locked_until = NULL WHERE id = ?", user.id());
        if (passwordHasher.needsUpgrade(user.password())) {
            jdbcTemplate.update("UPDATE users SET password = ? WHERE id = ?", passwordHasher.hash(password), user.id());
        }

        return toResponse(findByIdentifier(user.email()), deviceContext, "Login successful.", null);
    }

    @Transactional
    @Observed(name = "jagdisu.auth.google")
    public UserResponse googleLogin(GoogleLoginRequest request, DeviceContext deviceContext) {
        if (googleClientId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Google login is not configured. Add GOOGLE_CLIENT_ID.");
        }

        String credential = trimToNull(request.credential());
        if (credential == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Google credential is required.");
        }

        GoogleTokenInfo tokenInfo;
        try {
            tokenInfo = restClient.get()
                    .uri("https://oauth2.googleapis.com/tokeninfo?id_token={credential}", credential)
                    .retrieve()
                    .body(GoogleTokenInfo.class);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google login verification failed.");
        }

        if (tokenInfo == null || !googleClientId.equals(tokenInfo.aud())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google login verification failed.");
        }
        if (!"true".equalsIgnoreCase(tokenInfo.email_verified())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google email is not verified.");
        }

        String email = normalizeEmail(tokenInfo.email());
        String name = firstPresent(tokenInfo.name(), email);
        if (email == null || name == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google account details could not be read.");
        }

        try {
            jdbcTemplate.update(
                    "INSERT INTO users (name, email, password, plan, email_verified) VALUES (?, ?, ?, 'FREE', TRUE)",
                    name,
                    email,
                    passwordHasher.hash(UUID.randomUUID().toString())
            );
        } catch (DuplicateKeyException ignored) {
            jdbcTemplate.update("UPDATE users SET email_verified = TRUE WHERE email = ?", email);
        }

        UserRecord user = findByIdentifier(email);
        ensureRole(user.id(), "STUDENT");
        return toResponse(user, deviceContext, "Google login successful.", null);
    }

    @Transactional
    public VerificationResponse requestEmailVerification(EmailVerificationRequest request) {
        String email = normalizeEmail(request.email());
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required.");
        }
        UserRecord user = findByIdentifier(email);
        VerificationToken token = createVerificationToken(user.id(), "EMAIL", email, "EMAIL_VERIFICATION");
        emailDeliveryService.send(email, "Verify your JagdiSu email", "Your JagdiSu email verification token is: " + token.plainToken());
        return new VerificationResponse("EMAIL_VERIFICATION_SENT", token.expiresAt().toString(), token.plainToken());
    }

    @Transactional
    public VerificationResponse verifyEmail(VerifyTokenRequest request) {
        VerificationRecord record = consumeVerificationToken(normalizeEmail(request.target()), request.token(), "EMAIL_VERIFICATION");
        jdbcTemplate.update("UPDATE users SET email_verified = TRUE WHERE id = ?", record.userId());
        return new VerificationResponse("EMAIL_VERIFIED", Instant.now().toString(), null);
    }

    @Transactional
    public VerificationResponse sendOtp(OtpSendRequest request) {
        String target = normalizeTarget(request.target());
        if (target == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mobile or email is required.");
        }
        Long userId = findOptionalUserId(target);
        VerificationToken token = createVerificationToken(userId, target.contains("@") ? "EMAIL" : "SMS", target, "OTP_LOGIN");
        if (target.contains("@")) {
            emailDeliveryService.send(target, "Your JagdiSu OTP", "Your JagdiSu OTP is: " + token.plainToken());
        } else {
            smsDeliveryService.sendOtp(target, token.plainToken());
        }
        return new VerificationResponse("OTP_SENT", token.expiresAt().toString(), token.plainToken());
    }

    @Transactional
    @Observed(name = "jagdisu.auth.otp.verify")
    public UserResponse verifyOtp(OtpVerifyRequest request, DeviceContext deviceContext) {
        String target = normalizeTarget(request.target());
        VerificationRecord record = consumeVerificationToken(target, request.otp(), "OTP_LOGIN");
        UserRecord user = record.userId() == null ? createOtpUser(target) : findById(record.userId());
        if (target.contains("@")) {
            jdbcTemplate.update("UPDATE users SET email_verified = TRUE WHERE id = ?", user.id());
        } else {
            jdbcTemplate.update("UPDATE users SET mobile_verified = TRUE, mobile = ? WHERE id = ?", target, user.id());
        }
        ensureRole(user.id(), "STUDENT");
        return toResponse(findById(user.id()), deviceContext, "OTP login successful.", null);
    }

    @Transactional
    public VerificationResponse forgotPassword(ForgotPasswordRequest request) {
        String email = normalizeEmail(request.email());
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required.");
        }
        UserRecord user = findByIdentifier(email);
        VerificationToken token = createVerificationToken(user.id(), "EMAIL", email, "PASSWORD_RESET");
        emailDeliveryService.send(email, "Reset your JagdiSu password", "Use this token to reset your JagdiSu password: " + token.plainToken());
        return new VerificationResponse("PASSWORD_RESET_SENT", token.expiresAt().toString(), token.plainToken());
    }

    @Transactional
    public VerificationResponse resetPassword(ResetPasswordRequest request) {
        validatePassword(request.newPassword());
        VerificationRecord record = consumeVerificationToken(normalizeEmail(request.email()), request.token(), "PASSWORD_RESET");
        jdbcTemplate.update(
                "UPDATE users SET password = ?, failed_login_attempts = 0, locked_until = NULL WHERE id = ?",
                passwordHasher.hash(request.newPassword()),
                record.userId()
        );
        jdbcTemplate.update("UPDATE auth_sessions SET revoked_at = CURRENT_TIMESTAMP WHERE user_id = ? AND revoked_at IS NULL", record.userId());
        return new VerificationResponse("PASSWORD_RESET_DONE", Instant.now().toString(), null);
    }

    @Transactional
    @Observed(name = "jagdisu.auth.refresh")
    public UserResponse refresh(RefreshRequest request, DeviceContext deviceContext) {
        String refreshToken = trimToNull(request.refreshToken());
        if (refreshToken == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refresh token is required.");
        }
        String tokenHash = MessageDigestUtil.sha256(refreshToken);
        SessionRecord session = findSessionByRefreshToken(tokenHash);
        jdbcTemplate.update("UPDATE auth_sessions SET revoked_at = CURRENT_TIMESTAMP WHERE id = ?", session.id());
        UserRecord user = findById(session.userId());
        return toResponse(user, deviceContext.withDeviceId(session.deviceId()), "Token refreshed.", null);
    }

    @Transactional
    public VerificationResponse logout(RefreshRequest request) {
        String refreshToken = trimToNull(request.refreshToken());
        if (refreshToken != null) {
            jdbcTemplate.update("UPDATE auth_sessions SET revoked_at = CURRENT_TIMESTAMP WHERE refresh_token_hash = ?", MessageDigestUtil.sha256(refreshToken));
        }
        return new VerificationResponse("LOGGED_OUT", Instant.now().toString(), null);
    }

    public List<DeviceSessionResponse> sessions(long userId) {
        return jdbcTemplate.query(
                """
                SELECT id, device_id, device_name, ip_address, user_agent, expires_at, revoked_at, created_at
                FROM auth_sessions
                WHERE user_id = ?
                ORDER BY created_at DESC
                LIMIT 50
                """,
                (rs, rowNum) -> new DeviceSessionResponse(
                        rs.getLong("id"),
                        rs.getString("device_id"),
                        rs.getString("device_name"),
                        rs.getString("ip_address"),
                        rs.getString("user_agent"),
                        rs.getTimestamp("expires_at").toInstant().toString(),
                        rs.getTimestamp("revoked_at") != null,
                        rs.getTimestamp("created_at").toInstant().toString()
                ),
                userId
        );
    }

    @Transactional
    public VerificationResponse revokeSession(long userId, long sessionId) {
        int updated = jdbcTemplate.update(
                "UPDATE auth_sessions SET revoked_at = CURRENT_TIMESTAMP WHERE id = ? AND user_id = ? AND revoked_at IS NULL",
                sessionId,
                userId
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Active session not found.");
        }
        return new VerificationResponse("SESSION_REVOKED", Instant.now().toString(), null);
    }

    private UserResponse toResponse(UserRecord user, DeviceContext deviceContext, String message, String verificationToken) {
        List<String> roles = rolesFor(user.id());
        TokenPair tokenPair = createTokenPair(user, roles, deviceContext);
        return new UserResponse(
                user.id(),
                user.name(),
                user.email(),
                user.plan() == null ? "FREE" : user.plan(),
                tokenPair.accessToken(),
                tokenPair.refreshToken(),
                "Bearer",
                jwtService.accessTokenTtlSeconds(),
                roles,
                user.emailVerified(),
                user.mobileVerified(),
                tokenPair.deviceId(),
                message,
                verificationToken
        );
    }

    private TokenPair createTokenPair(UserRecord user, List<String> roles, DeviceContext deviceContext) {
        String accessToken = jwtService.createAccessToken(user.id(), user.email(), roles);
        String refreshToken = UUID.randomUUID() + "." + randomHex(32);
        String deviceId = trimToNull(deviceContext.deviceId()) == null ? UUID.randomUUID().toString() : deviceContext.deviceId().trim();
        Timestamp expiresAt = Timestamp.from(Instant.now().plus(Duration.ofDays(refreshTokenTtlDays)));
        jdbcTemplate.update(
                """
                INSERT INTO auth_sessions
                  (user_id, device_id, device_name, ip_address, user_agent, refresh_token_hash, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                user.id(),
                deviceId,
                trimToNull(deviceContext.deviceName()),
                trimToNull(deviceContext.ipAddress()),
                trimToNull(deviceContext.userAgent()),
                MessageDigestUtil.sha256(refreshToken),
                expiresAt
        );
        jdbcTemplate.update(
                """
                INSERT INTO device_sessions (user_id, device_fingerprint, device_name, ip_address)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE device_name = VALUES(device_name), ip_address = VALUES(ip_address), last_seen_at = CURRENT_TIMESTAMP, revoked_at = NULL
                """,
                user.id(),
                deviceId,
                trimToNull(deviceContext.deviceName()),
                trimToNull(deviceContext.ipAddress())
        );
        storeSessionInRedis(user.id(), deviceId, refreshTokenTtlDays);
        return new TokenPair(accessToken, refreshToken, deviceId);
    }

    private void storeSessionInRedis(long userId, String deviceId, int ttlDays) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set("session:" + userId + ":" + deviceId, "ACTIVE", Duration.ofDays(ttlDays));
        } catch (Exception ignored) {
            // Redis outage should not block login while MySQL sessions are available.
        }
    }

    private VerificationToken createVerificationToken(Long userId, String channel, String target, String purpose) {
        String plainToken = "OTP_LOGIN".equals(purpose) ? String.format("%06d", secureRandom.nextInt(1_000_000)) : randomHex(24);
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(verificationTtlMinutes));
        jdbcTemplate.update(
                """
                INSERT INTO verification_tokens (user_id, channel, target, purpose, token_hash, expires_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                userId,
                channel,
                target,
                purpose,
                MessageDigestUtil.sha256(plainToken),
                Timestamp.from(expiresAt)
        );
        return new VerificationToken(plainToken, expiresAt);
    }

    private VerificationRecord consumeVerificationToken(String target, String token, String purpose) {
        String normalizedTarget = normalizeTarget(target);
        String cleanToken = trimToNull(token);
        if (normalizedTarget == null || cleanToken == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target and token are required.");
        }
        try {
            VerificationRecord record = jdbcTemplate.queryForObject(
                    """
                    SELECT id, user_id
                    FROM verification_tokens
                    WHERE target = ? AND purpose = ? AND token_hash = ? AND consumed_at IS NULL AND expires_at > CURRENT_TIMESTAMP
                    ORDER BY created_at DESC
                    LIMIT 1
                    """,
                    (rs, rowNum) -> new VerificationRecord(rs.getLong("id"), rs.getObject("user_id") == null ? null : rs.getLong("user_id")),
                    normalizedTarget,
                    purpose,
                    MessageDigestUtil.sha256(cleanToken)
            );
            jdbcTemplate.update("UPDATE verification_tokens SET consumed_at = CURRENT_TIMESTAMP WHERE id = ?", record.id());
            return record;
        } catch (EmptyResultDataAccessException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired verification token.");
        }
    }

    private UserRecord createOtpUser(String target) {
        String email = target.contains("@") ? target : "otp-" + target.replaceAll("[^0-9]", "") + "@jagdisu.local";
        String name = target.contains("@") ? target.substring(0, target.indexOf("@")) : "Student " + target.substring(Math.max(0, target.length() - 4));
        try {
            jdbcTemplate.update(
                    "INSERT INTO users (name, email, mobile, password, plan, email_verified, mobile_verified) VALUES (?, ?, ?, ?, 'FREE', ?, ?)",
                    name,
                    email,
                    target.contains("@") ? null : target,
                    passwordHasher.hash(UUID.randomUUID().toString()),
                    target.contains("@"),
                    !target.contains("@")
            );
        } catch (DuplicateKeyException ignored) {
            // Continue by reading existing user.
        }
        return findByIdentifier(email);
    }

    private SessionRecord findSessionByRefreshToken(String refreshTokenHash) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                    SELECT id, user_id, device_id
                    FROM auth_sessions
                    WHERE refresh_token_hash = ? AND revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP
                    LIMIT 1
                    """,
                    (rs, rowNum) -> new SessionRecord(rs.getLong("id"), rs.getLong("user_id"), rs.getString("device_id")),
                    refreshTokenHash
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token.");
        }
    }

    private UserRecord findByIdentifier(String identifier) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                    SELECT id, name, email, password, plan, email_verified, mobile_verified, failed_login_attempts, locked_until
                    FROM users
                    WHERE email = ? OR name = ?
                    LIMIT 1
                    """,
                    (rs, rowNum) -> new UserRecord(
                            rs.getLong("id"),
                            rs.getString("name"),
                            rs.getString("email"),
                            rs.getString("password"),
                            rs.getString("plan"),
                            rs.getBoolean("email_verified"),
                            rs.getBoolean("mobile_verified"),
                            rs.getInt("failed_login_attempts"),
                            rs.getTimestamp("locked_until") == null ? null : rs.getTimestamp("locked_until").toInstant()
                    ),
                    normalizeEmail(identifier),
                    identifier.trim()
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid login details.");
        }
    }

    private UserRecord findById(long id) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                    SELECT id, name, email, password, plan, email_verified, mobile_verified, failed_login_attempts, locked_until
                    FROM users
                    WHERE id = ?
                    LIMIT 1
                    """,
                    (rs, rowNum) -> new UserRecord(
                            rs.getLong("id"),
                            rs.getString("name"),
                            rs.getString("email"),
                            rs.getString("password"),
                            rs.getString("plan"),
                            rs.getBoolean("email_verified"),
                            rs.getBoolean("mobile_verified"),
                            rs.getInt("failed_login_attempts"),
                            rs.getTimestamp("locked_until") == null ? null : rs.getTimestamp("locked_until").toInstant()
                    ),
                    id
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found.");
        }
    }

    private Long findOptionalUserId(String target) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM users WHERE email = ? OR mobile = ? LIMIT 1",
                    Long.class,
                    target,
                    target
            );
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private List<String> rolesFor(long userId) {
        List<String> roles = jdbcTemplate.query(
                """
                SELECT r.code
                FROM roles r
                JOIN user_roles ur ON ur.role_id = r.id
                WHERE ur.user_id = ?
                ORDER BY r.code
                """,
                (rs, rowNum) -> rs.getString("code"),
                userId
        );
        return roles.isEmpty() ? List.of("STUDENT") : roles;
    }

    private void ensureRole(long userId, String roleCode) {
        jdbcTemplate.update(
                """
                INSERT INTO user_roles (user_id, role_id)
                SELECT ?, id FROM roles WHERE code = ?
                ON DUPLICATE KEY UPDATE assigned_at = assigned_at
                """,
                userId,
                roleCode
        );
    }

    private void enforceAccountNotLocked(UserRecord user) {
        if (user.lockedUntil() != null && user.lockedUntil().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.LOCKED, "Account is temporarily locked. Try again later.");
        }
    }

    private void registerFailedLogin(long userId) {
        jdbcTemplate.update(
                """
                UPDATE users
                SET failed_login_attempts = failed_login_attempts + 1,
                    locked_until = CASE
                      WHEN failed_login_attempts + 1 >= ? THEN ?
                      ELSE locked_until
                    END
                WHERE id = ?
                """,
                maxFailedAttempts,
                Timestamp.from(Instant.now().plus(Duration.ofMinutes(accountLockMinutes))),
                userId
        );
    }

    private void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required.");
        }
        if (!password.matches("\\d{6}") && password.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be 6 digits or at least 8 characters.");
        }
    }

    private String normalizeTarget(String value) {
        String target = trimToNull(value);
        if (target == null) {
            return null;
        }
        return target.contains("@") ? normalizeEmail(target) : target.replaceAll("\\s+", "");
    }

    private String firstPresent(String first, String second) {
        String value = trimToNull(first);
        return value == null ? trimToNull(second) : value;
    }

    private String normalizeEmail(String email) {
        String value = trimToNull(email);
        return value == null ? null : value.toLowerCase();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String randomHex(int bytes) {
        byte[] buffer = new byte[bytes];
        secureRandom.nextBytes(buffer);
        return HexFormat.of().formatHex(buffer);
    }

    public record SignupRequest(String name, String username, String email, String mobile, String password) {
    }

    public record LoginRequest(String usernameOrEmail, String password) {
    }

    public record GoogleLoginRequest(String credential) {
    }

    public record RefreshRequest(String refreshToken) {
    }

    public record EmailVerificationRequest(String email) {
    }

    public record VerifyTokenRequest(String target, String token) {
    }

    public record OtpSendRequest(String target) {
    }

    public record OtpVerifyRequest(String target, String otp) {
    }

    public record ForgotPasswordRequest(String email) {
    }

    public record ResetPasswordRequest(String email, String token, String newPassword) {
    }

    public record UserResponse(
            Long id,
            String name,
            String email,
            String plan,
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            List<String> roles,
            boolean emailVerified,
            boolean mobileVerified,
            String deviceId,
            String message,
            String verificationToken
    ) {
    }

    public record VerificationResponse(String status, String expiresAt, String devToken) {
    }

    public record DeviceSessionResponse(
            long id,
            String deviceId,
            String deviceName,
            String ipAddress,
            String userAgent,
            String expiresAt,
            boolean revoked,
            String createdAt
    ) {
    }

    public record DeviceContext(String deviceId, String deviceName, String ipAddress, String userAgent) {
        DeviceContext withDeviceId(String nextDeviceId) {
            return new DeviceContext(nextDeviceId, deviceName, ipAddress, userAgent);
        }
    }

    private record UserRecord(
            Long id,
            String name,
            String email,
            String password,
            String plan,
            boolean emailVerified,
            boolean mobileVerified,
            int failedLoginAttempts,
            Instant lockedUntil
    ) {
    }

    private record TokenPair(String accessToken, String refreshToken, String deviceId) {
    }

    private record VerificationToken(String plainToken, Instant expiresAt) {
    }

    private record VerificationRecord(long id, Long userId) {
    }

    private record SessionRecord(long id, long userId, String deviceId) {
    }

    private record GoogleTokenInfo(String aud, String email, String email_verified, String name) {
    }
}
