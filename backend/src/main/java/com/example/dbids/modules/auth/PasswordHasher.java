package com.example.dbids.modules.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class PasswordHasher {
    private PasswordHasher() {}

    public static String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(dig.length * 2);
            for (byte b : dig) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA256_FAIL", e);
        }
    }
}
