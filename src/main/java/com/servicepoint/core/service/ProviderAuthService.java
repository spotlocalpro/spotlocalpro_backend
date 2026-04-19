package com.servicepoint.core.service;

import com.servicepoint.core.dto.*;
import com.servicepoint.core.model.ProviderRegistration;
import com.servicepoint.core.model.User;
import com.servicepoint.core.repository.ProviderRegistrationRepository;
import com.servicepoint.core.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class ProviderAuthService {

    @Autowired
    private ProviderRegistrationRepository registrationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OtpService otpService;

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Check provider registration status
     */
    public ProviderStatusResponse checkProviderStatus(String email) {
        // Check if already a registered user
        if (userRepository.findByEmail(email).isPresent()) {
            User user = userRepository.findByEmail(email).get();
            if ("provider".equals(user.getRole())) {
                return new ProviderStatusResponse(
                        "approved",
                        "Provider account is active. You can login.",
                        true
                );
            }
            if ("admin".equals(user.getRole())){
                return new ProviderStatusResponse(
                        "approved",
                        "Welcome Admin!! You can login.",
                        true
                );
            }

        }

        // Check pending registration
        var pendingReg = registrationRepository.findByEmailAndStatus(
                email,
                ProviderRegistration.RegistrationStatus.PENDING
        );

        if (pendingReg.isPresent()) {
            return new ProviderStatusResponse(
                    "pending",
                    "Your registration is under review. We'll notify you via email once approved.",
                    false
            );
        }

        // Check rejected registration
        var rejectedReg = registrationRepository.findByEmail(email);
        if (rejectedReg.isPresent() &&
                rejectedReg.get().getStatus() == ProviderRegistration.RegistrationStatus.REJECTED) {
            return new ProviderStatusResponse(
                    "rejected",
                    "Registration was rejected: " + rejectedReg.get().getRejectionReason(),
                    false
            );
        }

        return new ProviderStatusResponse(
                "not_found",
                "No provider registration found. Please register first.",
                false
        );
    }

    /**
     * Provider can login using the same login endpoint once approved
     * This method validates that the provider exists and is approved
     */
    public boolean canProviderLogin(String email) {
        return userRepository.findByEmail(email)
                .map(user -> "provider".equals(user.getRole()))
                .orElse(false);
    }

    /**
     * Alternative: Provider-specific login with status check
     */
    public LoginResponse providerLogin(LoginRequest request, HttpServletRequest httpRequest) {
        String email = request.getEmail();

        // First check if provider is approved
        ProviderStatusResponse status = checkProviderStatus(email);

        if (!"approved".equals(status.getStatus())) {
            throw new RuntimeException(status.getMessage());
        }

        // Use the regular login service
        return userService.loginUser(request, httpRequest);
    }
}
