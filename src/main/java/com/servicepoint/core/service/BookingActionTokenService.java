package com.servicepoint.core.service;

/**
 * Generates and verifies HMAC-signed tokens for provider booking approve/decline email links.
 *
 * Tokens are stateless — reuse prevention is handled at the controller level by checking
 * booking status (once a booking is no longer "pending", all its tokens become no-ops).
 */
public interface BookingActionTokenService {

    String ACTION_APPROVE = "approve";
    String ACTION_DECLINE = "decline";

    /**
     * Build a signed token for (bookingId, action). Valid for 48 hours.
     */
    String generateToken(Integer bookingId, String action);

    /**
     * Verify a token. Returns true iff signature matches, not expired,
     * and action/bookingId match what was signed.
     */
    boolean verifyToken(String token, Integer expectedBookingId, String expectedAction);
}