package com.jdsu.quiz.auth;

import org.springframework.stereotype.Component;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class PasswordHasher {
    private static final String PREFIX = "pbkdf2";
    private static final int ITERATIONS = 120_000;
    private static final int KEY_LENGTH = 256;
    private final SecureRandom secureRandom = new SecureRandom();

    public String hash(String password) {
        byte[] salt = new byte[16];
        secureRandom.nextBytes(salt);
        byte[] derivedKey = deriveKey(password, salt, ITERATIONS);
        return String.join("$",
                PREFIX,
                String.valueOf(ITERATIONS),
                Base64.getEncoder().encodeToString(salt),
                Base64.getEncoder().encodeToString(derivedKey)
        );
    }

    public boolean matches(String password, String storedPassword) {
        if (storedPassword == null || storedPassword.isBlank()) {
            return false;
        }

        String[] parts = storedPassword.split("\\$");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return MessageDigest.isEqual(password.getBytes(), storedPassword.getBytes());
        }

        int iterations = Integer.parseInt(parts[1]);
        byte[] salt = Base64.getDecoder().decode(parts[2]);
        byte[] expectedKey = Base64.getDecoder().decode(parts[3]);
        byte[] actualKey = deriveKey(password, salt, iterations);
        return MessageDigest.isEqual(actualKey, expectedKey);
    }

    public boolean needsUpgrade(String storedPassword) {
        return storedPassword == null || !storedPassword.startsWith(PREFIX + "$");
    }

    private byte[] deriveKey(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception exception) {
            throw new IllegalStateException("Password hashing failed.", exception);
        }
    }
}
