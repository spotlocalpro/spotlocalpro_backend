package com.servicepoint.core.controller;

import com.servicepoint.core.dto.ProviderRegistrationDTO;
import com.servicepoint.core.model.ProviderRegistration;
import com.servicepoint.core.model.User;
import com.servicepoint.core.service.ProviderRegistrationService;
import com.servicepoint.core.service.OtpService;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/provider-registration")
@CrossOrigin(origins = "*")
public class ProviderRegistrationController {

    @Autowired
    private ProviderRegistrationService registrationService;

    @Autowired
    private OtpService otpService;

    /**
     * Request OTP for provider registration
     */
    @PostMapping("/request-otp")
    public ResponseEntity<?> requestOtp(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");

            if (email == null || email.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
            }
            String caseTypeOfSendingOtp = otpService.canResendOtp(email, "provider_registration");
            // Check rate limiting
            if ("TIME_LEFT".equalsIgnoreCase(caseTypeOfSendingOtp)) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(Map.of("error", "Please wait before requesting another OTP"));
            }

            otpService.generateAndSendOtp(email, "provider_registration");

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "OTP sent to " + email
            ));

        } catch (MessagingException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to send OTP email"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Submit provider registration with documents
     */
    @PostMapping("/submit")
    public ResponseEntity<?> submitRegistration(
            @RequestParam("firstName") String firstName,
            @RequestParam("lastName") String lastName,
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            @RequestParam(value = "phoneNumber", required = false) String phoneNumber,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "latitude", required = false) Double latitude,
            @RequestParam(value = "longitude", required = false) Double longitude,
            @RequestParam("otpCode") String otpCode,
            @RequestParam(value = "documents", required = false) List<MultipartFile> documents
    ) {
        try {
            ProviderRegistration registration = registrationService.submitRegistration(
                    firstName, lastName, email, password, phoneNumber,
                    location, latitude, longitude, otpCode, documents
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Registration submitted successfully. Awaiting admin approval.");
            response.put("registrationId", registration.getRegistrationId());

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to submit registration: " + e.getMessage()));
        }
    }

    /**
     * Get all registrations (Admin only)
     */
    @GetMapping("/all")
    public ResponseEntity<?> getAllRegistrations() {
        try {
            List<ProviderRegistrationDTO> registrations = registrationService.getAllRegistrationsDTO();
            return ResponseEntity.ok(registrations);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch all registrations: " + e.getMessage()));
        }
    }

    /**
     * Get pending registrations (Admin only)
     */
    @GetMapping("/pending")
    public ResponseEntity<?> getPendingRegistrations() {
        try {
            List<ProviderRegistrationDTO> registrations = registrationService.getPendingRegistrationsDTO();
            return ResponseEntity.ok(registrations);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch pending registrations: " + e.getMessage()));
        }
    }

    /**
     * Get approved registrations (Admin only)
     */
    @GetMapping("/approved")
    public ResponseEntity<?> getApprovedRegistrations() {
        try {
            List<ProviderRegistrationDTO> registrations = registrationService.getApprovedRegistrationsDTO();
            return ResponseEntity.ok(registrations);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch approved registrations: " + e.getMessage()));
        }
    }

    /**
     * Get rejected registrations (Admin only)
     */
    @GetMapping("/rejected")
    public ResponseEntity<?> getRejectedRegistrations() {
        try {
            List<ProviderRegistrationDTO> registrations = registrationService.getRejectedRegistrationsDTO();
            return ResponseEntity.ok(registrations);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch rejected registrations: " + e.getMessage()));
        }
    }

    /**
     * Approve provider registration (Admin only)
     */
    @PostMapping("/approve/{registrationId}")
    public ResponseEntity<?> approveRegistration(
            @PathVariable Integer registrationId,
            @RequestParam Integer adminId
    ) {
        try {
            User user = registrationService.approveRegistration(registrationId, adminId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Registration approved successfully");
            response.put("user", user);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to approve registration: " + e.getMessage()));
        }
    }

    /**
     * Reject provider registration (Admin only)
     */
    @PostMapping("/reject/{registrationId}")
    public ResponseEntity<?> rejectRegistration(
            @PathVariable Integer registrationId,
            @RequestParam Integer adminId,
            @RequestBody Map<String, String> request
    ) {
        try {
            String reason = request.get("reason");
            if (reason == null || reason.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Rejection reason is required"));
            }

            registrationService.rejectRegistration(registrationId, adminId, reason);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Registration rejected successfully"
            ));

        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to reject registration: " + e.getMessage()));
        }
    }
}