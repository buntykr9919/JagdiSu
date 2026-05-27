package com.jdsu.quiz.auth;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class AuthService {
    private final JdbcTemplate jdbcTemplate;
    private final PasswordHasher passwordHasher;
    private final RestClient restClient;
    private final String googleClientId;

    public AuthService(
            JdbcTemplate jdbcTemplate,
            PasswordHasher passwordHasher,
            @Value("${app.google.client-id:}") String googleClientId
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordHasher = passwordHasher;
        this.restClient = RestClient.create();
        this.googleClientId = googleClientId == null ? "" : googleClientId.trim();
    }

    public UserResponse signup(SignupRequest request) {
        String name = firstPresent(request.name(), request.username());
        String email = normalizeEmail(request.email());
        String password = trimToNull(request.password());

        if (name == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required.");
        }
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required.");
        }
        if (password == null || password.length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 6 characters.");
        }

        try {
            jdbcTemplate.update(
                    "INSERT INTO users (name, email, password, plan) VALUES (?, ?, ?, 'FREE')",
                    name,
                    email,
                    passwordHasher.hash(password)
            );
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered.");
        }

        UserRecord user = findByIdentifier(email);
        return toResponse(user);
    }

    public UserResponse login(LoginRequest request) {
        String identifier = trimToNull(request.usernameOrEmail());
        String password = trimToNull(request.password());

        if (identifier == null || password == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username/email and password are required.");
        }

        UserRecord user = findByIdentifier(identifier);
        if (!passwordHasher.matches(password, user.password())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid login details.");
        }

        if (passwordHasher.needsUpgrade(user.password())) {
            jdbcTemplate.update("UPDATE users SET password = ? WHERE id = ?", passwordHasher.hash(password), user.id());
        }

        return toResponse(user);
    }

    public UserResponse googleLogin(GoogleLoginRequest request) {
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
            return toResponse(findByIdentifier(email));
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() != HttpStatus.UNAUTHORIZED) {
                throw exception;
            }
        }

        try {
            jdbcTemplate.update(
                    "INSERT INTO users (name, email, password, plan) VALUES (?, ?, ?, 'FREE')",
                    name,
                    email,
                    passwordHasher.hash(UUID.randomUUID().toString())
            );
        } catch (DuplicateKeyException ignored) {
            return toResponse(findByIdentifier(email));
        }

        return toResponse(findByIdentifier(email));
    }

    private UserRecord findByIdentifier(String identifier) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id, name, email, password, plan FROM users WHERE email = ? OR name = ? LIMIT 1",
                    (rs, rowNum) -> new UserRecord(
                            rs.getLong("id"),
                            rs.getString("name"),
                            rs.getString("email"),
                            rs.getString("password"),
                            rs.getString("plan")
                    ),
                    normalizeEmail(identifier),
                    identifier.trim()
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid login details.");
        }
    }

    private UserResponse toResponse(UserRecord user) {
        return new UserResponse(user.id(), user.name(), user.email(), user.plan() == null ? "FREE" : user.plan());
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

    public record SignupRequest(String name, String username, String email, String password) {
    }

    public record LoginRequest(String usernameOrEmail, String password) {
    }

    public record GoogleLoginRequest(String credential) {
    }

    public record UserResponse(Long id, String name, String email, String plan) {
    }

    private record UserRecord(Long id, String name, String email, String password, String plan) {
    }

    private record GoogleTokenInfo(String aud, String email, String email_verified, String name) {
    }
}
