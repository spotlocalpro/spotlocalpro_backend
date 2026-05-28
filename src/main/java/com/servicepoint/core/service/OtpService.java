package com.servicepoint.core.service;

import com.servicepoint.core.model.OtpCode;
import com.servicepoint.core.repository.OtpRepository;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class OtpService {

    @Autowired
    private OtpRepository otpRepository;

    @Autowired
    private EmailService emailService;

    private static final SecureRandom random = new SecureRandom();
    private static final int OTP_LENGTH = 6;
    private static final int MAX_ATTEMPTS = 5;

    /**
     * Generate and send OTP to user's email
     */
    public void generateAndSendOtp(String email, String purpose) throws MessagingException {
        String otpCode = generateOtpCode();

        OtpCode otp = new OtpCode();
        otp.setEmail(email);
        otp.setOtpCode(otpCode);
        otp.setPurpose(purpose);
        otp.setIsUsed(false);

        // Send email first — if it throws, nothing is saved and the rate limit is not consumed
        emailService.sendOtpEmail(email, otpCode, purpose);

        // Remove stale OTPs (expired or already used) for this email+purpose
        otpRepository.deleteStaleByEmailAndPurpose(email, purpose);

        // Save the fresh OTP in its own transaction
        otpRepository.save(otp);
    }

    /**
     * Verify OTP code
     */
    @Transactional
    public boolean verifyOtp(String email, String otpCode, String purpose) {
        Optional<OtpCode> otpOptional = otpRepository
                .findByEmailAndOtpCodeAndPurposeAndIsUsedFalse(email, otpCode, purpose);

        if (otpOptional.isEmpty()) {
            return false;
        }

        OtpCode otp = otpOptional.get();

        // Check if expired
        if (otp.isExpired()) {
            return false;
        }

        // Mark as used
        otp.setIsUsed(true);
        otp.setUsedAt(Timestamp.valueOf(LocalDateTime.now()));
        otpRepository.save(otp);

        return true;
    }

    /**
     * Returns remaining cooldown seconds before a new OTP can be sent.
     * Returns 0 if an OTP can be sent immediately.
     */
    public long canResendOtp(String email, String purpose) {
        Optional<OtpCode> lastOtp = otpRepository
                .findTopByEmailAndPurposeOrderByCreatedAtDesc(email, purpose);

        if (lastOtp.isPresent()) {
            Instant availableAt = lastOtp.get().getCreatedAt().toInstant().plus(10, ChronoUnit.MINUTES);
            long secondsLeft = Instant.now().until(availableAt, ChronoUnit.SECONDS);
            return Math.max(0, secondsLeft);
        }

        return 0;
    }

    /**
     * Generate random 6-digit OTP
     */
    private String generateOtpCode() {
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }

    /**
     * Scheduled task to clean up expired OTPs (runs every hour)
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    @Transactional
    public void cleanupExpiredOtps() {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        otpRepository.deleteByExpiresAtBefore(now);
        otpRepository.deleteByIsUsedTrue();
    }
}