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
        // Generate 6-digit OTP
        String otpCode = generateOtpCode();

        // Create OTP entity
        OtpCode otp = new OtpCode();
        otp.setEmail(email);
        otp.setOtpCode(otpCode);
        otp.setPurpose(purpose);
        otp.setIsUsed(false);

        // Send email
        emailService.sendOtpEmail(email, otpCode, purpose);

        // Save to database
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
     * Check if OTP can be resent (rate limiting)
     */
    public String canResendOtp(String email, String purpose) {
        Optional<OtpCode> lastOtp = otpRepository
                .findTopByEmailAndPurposeOrderByCreatedAtDesc(email, purpose);

        if (lastOtp.isPresent()) {
            Instant tenMinutesAgo = Instant.now().minus(10, ChronoUnit.MINUTES);
            Instant createdAt = lastOtp.get().getCreatedAt().toInstant();

            if (createdAt.isBefore(tenMinutesAgo)) {
                return "OTP_TO_BE_SENT"; // old → allow resend
            } else {
                return "TIME_LEFT"; // still within 10 min → block
            }
        }

        return "OTP_TO_BE_SENT"; // no OTP exists → allow
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
    }
}