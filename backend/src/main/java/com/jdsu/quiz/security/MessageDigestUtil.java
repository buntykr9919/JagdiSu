package com.jdsu.quiz.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

public final class MessageDigestUtil {
    private MessageDigestUtil() {
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 hashing failed.", exception);
        }
    }

    public static boolean constantTimeEquals(String first, String second) {
        byte[] firstBytes = first == null ? new byte[0] : first.getBytes(StandardCharsets.UTF_8);
        byte[] secondBytes = second == null ? new byte[0] : second.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(firstBytes, secondBytes);
    }
}
