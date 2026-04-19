package com.servicepoint.core.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

/**
 * HMAC-SHA256 implementation of {@link BookingActionTokenService}.
 *
 * Token format (base64url-encoded):
 *   bookingId|action|expiryEpochSeconds|signature
 *
 * Signature = HMAC-SHA256(secret, "bookingId|action|expiryEpochSeconds")
 */
@Service
public class BookingActionTokenServiceImpl implements BookingActionTokenService {

    private static final long TOKEN_TTL_SECONDS = 48L * 60 * 60; // 48 hours
    private static final String HMAC_ALGO = "HmacSHA256";
    private static final String DELIM = "|";

    private final byte[] secretBytes;

    public BookingActionTokenServiceImpl(
            @Value("${booking.action.secret:${BOOKING_ACTION_SECRET:}}") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "BOOKING_ACTION_SECRET env var must be set (min 32 chars recommended).");
        }
        this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String generateToken(Integer bookingId, String action) {
        if (!ACTION_APPROVE.equals(action) && !ACTION_DECLINE.equals(action)) {
            throw new IllegalArgumentException("Invalid action: " + action);
        }
        long expiry = Instant.now().getEpochSecond() + TOKEN_TTL_SECONDS;
        String payload = bookingId + DELIM + action + DELIM + expiry;
        String signature = sign(payload);
        String raw = payload + DELIM + signature;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean verifyToken(String token, Integer expectedBookingId, String expectedAction) {
        if (token == null || token.isBlank()) return false;
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(token);
            String raw = new String(decoded, StandardCharsets.UTF_8);
            String[] parts = raw.split("\\" + DELIM);
            if (parts.length != 4) return false;

            Integer bookingId = Integer.parseInt(parts[0]);
            String action = parts[1];
            long expiry = Long.parseLong(parts[2]);
            String providedSig = parts[3];

            if (!bookingId.equals(expectedBookingId)) return false;
            if (!action.equals(expectedAction)) return false;
            if (Instant.now().getEpochSecond() > expiry) return false;

            String expectedSig = sign(parts[0] + DELIM + parts[1] + DELIM + parts[2]);
            return constantTimeEquals(providedSig, expectedSig);
        } catch (Exception e) {
            return false;
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secretBytes, HMAC_ALGO));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}