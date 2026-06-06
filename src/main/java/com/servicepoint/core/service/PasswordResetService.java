package com.servicepoint.core.service;

import com.servicepoint.core.model.PasswordResetToken;
import com.servicepoint.core.model.User;
import com.servicepoint.core.repository.PasswordResetTokenRepository;
import com.servicepoint.core.repository.UserRepository;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class PasswordResetService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;

    @Value("${app.base.url:http://localhost:5173}")
    private String appBaseUrl;

    private static final long TOKEN_EXPIRY_HOURS = 1;

    /**
     * Handle forgot password request.
     * Always completes silently — no exception if email doesn't exist (prevents enumeration).
     */
    @Transactional
    public void handleForgotPassword(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isEmpty()) {
            System.out.println("DEBUG >> Forgot password requested for non-existent email: " + email);
            return;
        }

        User user = userOpt.get();

        // Invalidate any existing tokens for this user (only one active reset link at a time)
        tokenRepository.invalidateAllForUser(user);

        // Generate new secure token
        String token = UUID.randomUUID().toString() + UUID.randomUUID().toString().replace("-", "");

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken(token);
        resetToken.setUser(user);
        resetToken.setExpiresAt(Timestamp.valueOf(LocalDateTime.now().plusHours(TOKEN_EXPIRY_HOURS)));
        resetToken.setUsed(false);

        tokenRepository.save(resetToken);

        // Build reset link
        String resetLink = appBaseUrl + "/auth/reset-password?token=" + token;

        // Send email
        try {
            String lang = user.getPreferredLanguage() != null ? user.getPreferredLanguage() : "en";
            emailService.sendPasswordResetEmail(user.getEmail(), user.getUsername(), resetLink, lang);
            System.out.println("DEBUG >> Password reset email sent to: " + email);
        } catch (MessagingException e) {
            System.out.println("DEBUG >> Failed to send password reset email: " + e.getMessage());
            // Don't throw — we still return 200 to prevent enumeration
        }
    }

    /**
     * Reset the password using a valid token.
     * @return true if reset succeeded, false if token invalid/expired
     */
    @Transactional
    public boolean resetPassword(String token, String newPassword) {
        Optional<PasswordResetToken> tokenOpt = tokenRepository.findByToken(token);

        if (tokenOpt.isEmpty()) {
            System.out.println("DEBUG >> Reset token not found: " + token);
            return false;
        }

        PasswordResetToken resetToken = tokenOpt.get();

        if (!resetToken.isValid()) {
            System.out.println("DEBUG >> Reset token invalid or expired. Used=" + resetToken.getUsed()
                    + " Expired=" + resetToken.isExpired());
            return false;
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
        userRepository.save(user);

        // Mark token as used (single-use)
        resetToken.setUsed(true);
        tokenRepository.save(resetToken);

        System.out.println("DEBUG >> Password reset successful for user: " + user.getEmail());
        return true;
    }

    /**
     * Clean up expired tokens every hour.
     */
    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void cleanupExpiredTokens() {
        tokenRepository.deleteExpiredTokens(Timestamp.valueOf(LocalDateTime.now()));
    }
}