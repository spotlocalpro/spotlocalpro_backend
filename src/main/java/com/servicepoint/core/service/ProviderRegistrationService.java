package com.servicepoint.core.service;

import com.servicepoint.core.dto.ProviderRegistrationDTO;
import com.servicepoint.core.model.ProviderDocument;
import com.servicepoint.core.model.ProviderRegistration;
import com.servicepoint.core.model.User;
import com.servicepoint.core.repository.ProviderDocumentRepository;
import com.servicepoint.core.repository.ProviderRegistrationRepository;
import com.servicepoint.core.repository.UserRepository;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProviderRegistrationService {

    @Autowired
    private ProviderRegistrationRepository registrationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProviderDocumentRepository documentRepository;

    @Autowired
    private OtpService otpService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${file.upload.dir:uploads/provider-documents}")
    private String uploadDir;

    @Value("${admin.notification.email}")
    private String adminEmail;

    /**
     * Submit provider registration with documents
     */
    @Transactional
    public ProviderRegistration submitRegistration(
            String firstName,
            String lastName,
            String email,
            String password,
            String phoneNumber,
            String location,
            Double latitude,
            Double longitude,
            String otpCode,
            List<MultipartFile> documents
    ) throws Exception {



        // Verify OTP
        if (!otpService.verifyOtp(email, otpCode, "provider_registration")) {
            throw new IllegalArgumentException("Invalid or expired OTP");
        }

        // Check if email already registered
        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Email already registered");
        }

        // Check if pending registration exists
        if (registrationRepository.findByEmailAndStatus(email, ProviderRegistration.RegistrationStatus.PENDING).isPresent()) {
            throw new IllegalArgumentException("Registration already pending approval");
        }

        // Create registration
        ProviderRegistration registration = new ProviderRegistration();
        registration.setFirstName(firstName);
        registration.setLastName(lastName);
        registration.setEmail(email);
        registration.setPasswordHash(passwordEncoder.encode(password));
        registration.setPhoneNumber(phoneNumber);
        registration.setLocation(location);
        registration.setLatitude(latitude);
        registration.setLongitude(longitude);
        registration.setOtpCode(otpCode);
        registration.setStatus(ProviderRegistration.RegistrationStatus.PENDING);

        // Save registration first to get ID
        registration = registrationRepository.save(registration);

        // Upload and save documents to database
        if (documents != null && !documents.isEmpty()) {
            for (MultipartFile file : documents) {
                saveDocument(file, registration);
            }
        }

        // Reload registration to get documents count
        registration = registrationRepository.findById(registration.getRegistrationId())
                .orElseThrow(() -> new IllegalStateException("Failed to reload registration"));

        // Notify admin
        notifyAdminNewRegistration(registration);

        return registration;
    }

    /**
     * Approve provider registration
     */
    @Transactional
    public User approveRegistration(Integer registrationId, Integer adminId) throws MessagingException {
        ProviderRegistration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new IllegalArgumentException("Registration not found"));

        if (registration.getStatus() != ProviderRegistration.RegistrationStatus.PENDING) {
            throw new IllegalStateException("Registration already processed");
        }

        // Create user account
        User user = new User();
        user.setUsername(generateUniqueUsername(registration.getFirstName(),registration.getLastName()));
        user.setEmail(registration.getEmail());
        user.setPasswordHash(registration.getPasswordHash());
        user.setRole("provider");
        user.setPhoneNumber(registration.getPhoneNumber());
        user.setLocation(registration.getLocation());
        user.setLatitude(registration.getLatitude());
        user.setLongitude(registration.getLongitude());
        user.setRating(0.0);
        user.setReviewCount(0);

        user = userRepository.save(user);

        // Update registration status
        registration.setStatus(ProviderRegistration.RegistrationStatus.APPROVED);
        registration.setReviewedAt(Timestamp.valueOf(LocalDateTime.now()));
        registration.setReviewedBy(adminId);
        registrationRepository.save(registration);

        // Notify provider
        emailService.sendProviderApprovalEmail(registration.getEmail(), registration.getFirstName());

        return user;
    }

    /**
     * Reject provider registration
     */
    @Transactional
    public void rejectRegistration(Integer registrationId, Integer adminId, String reason) throws MessagingException {
        ProviderRegistration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new IllegalArgumentException("Registration not found"));

        if (registration.getStatus() != ProviderRegistration.RegistrationStatus.PENDING) {
            throw new IllegalStateException("Registration already processed");
        }

        registration.setStatus(ProviderRegistration.RegistrationStatus.REJECTED);
        registration.setRejectionReason(reason);
        registration.setReviewedAt(Timestamp.valueOf(LocalDateTime.now()));
        registration.setReviewedBy(adminId);
        registrationRepository.save(registration);

        // Notify provider
        emailService.sendProviderRejectionEmail(registration.getEmail(), registration.getFirstName(), reason);
    }

    /**
     * Get all pending registrations
     */
    public List<ProviderRegistration> getPendingRegistrations() {
        return registrationRepository.findByStatusOrderBySubmittedAtDesc(
                ProviderRegistration.RegistrationStatus.PENDING
        );
    }


    // Auto-generate unique username from firstName + lastName
    private String generateUniqueUsername(String firstName, String lastName) {
        // Base: john_doe
        String base = firstName.toLowerCase() + "_" + lastName.toLowerCase();
        // Remove special characters
        base = base.replaceAll("[^a-z0-9_]", "");

        String username = base;
        int counter = 1;

        // Keep trying until unique
        while (userRepository.existsByUsername(username)) {
            username = base + counter;
            counter++;
        }

        return username;
    }
    /**
     * Get documents for a registration
     */
    public List<ProviderDocument> getRegistrationDocuments(Integer registrationId) {
        return documentRepository.findByRegistration_RegistrationId(registrationId);
    }

    /**
     * Save document to file system and database
     */
    private void saveDocument(MultipartFile file, ProviderRegistration registration) throws IOException {
        // Create upload directory if not exists
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Generate unique filename
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null ?
                originalFilename.substring(originalFilename.lastIndexOf(".")) : ".bin";
        String filename = registration.getRegistrationId() + "_" + UUID.randomUUID() + extension;

        // Save file to disk
        Path filePath = uploadPath.resolve(filename);
        Files.copy(file.getInputStream(), filePath);

        // Determine document type based on file extension or name
        String documentType = determineDocumentType(originalFilename);

        // Create document record in database
        ProviderDocument document = new ProviderDocument();
        document.setRegistration(registration);
        document.setDocumentType(documentType);
        document.setFileName(originalFilename);
        document.setFileUrl("/uploads/provider-documents/" + filename);
        document.setFileSize(file.getSize());

        // Save to database
        documentRepository.save(document);

        // Add to registration's document list (for bidirectional relationship)
        registration.getDocuments().add(document);
    }

    /**
     * Determine document type from filename
     */
    private String determineDocumentType(String filename) {
        if (filename == null) return "other";

        String lowerName = filename.toLowerCase();

        if (lowerName.contains("certificate") || lowerName.contains("cert")) {
            return "certificate";
        } else if (lowerName.contains("id") || lowerName.contains("license") ||
                lowerName.contains("passport") || lowerName.contains("national")) {
            return "id_proof";
        } else {
            return "other";
        }
    }
    public List<ProviderRegistrationDTO> getPendingRegistrationsDTO() {
        List<ProviderRegistration> registrations = registrationRepository.findByStatus(ProviderRegistration.RegistrationStatus.PENDING);
        return registrations.stream()
                .map(ProviderRegistrationDTO::fromEntity)
                .collect(Collectors.toList());
    }
    /**
     * Notify admin about new registration
     */
    private void notifyAdminNewRegistration(ProviderRegistration registration) {
        try {
            String subject = "New Provider Registration - " +
                    registration.getFirstName() + " " + registration.getLastName();

            String body = String.format("""
                    <html>
                    <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                        <div style="background-color: #2196F3; padding: 20px; text-align: center;">
                            <h1 style="color: white; margin: 0;">New Provider Registration</h1>
                        </div>
                        <div style="padding: 20px; background-color: #f9f9f9;">
                            <h2>Registration Details</h2>
                            <table style="width: 100%%;">
                                <tr>
                                    <td style="padding: 8px 0;"><strong>Name:</strong></td>
                                    <td style="padding: 8px 0;">%s %s</td>
                                </tr>
                                <tr>
                                    <td style="padding: 8px 0;"><strong>Email:</strong></td>
                                    <td style="padding: 8px 0;">%s</td>
                                </tr>
                                <tr>
                                    <td style="padding: 8px 0;"><strong>Phone:</strong></td>
                                    <td style="padding: 8px 0;">%s</td>
                                </tr>
                                <tr>
                                    <td style="padding: 8px 0;"><strong>Location:</strong></td>
                                    <td style="padding: 8px 0;">%s</td>
                                </tr>
                                <tr>
                                    <td style="padding: 8px 0;"><strong>Documents:</strong></td>
                                    <td style="padding: 8px 0;">%d uploaded</td>
                                </tr>
                                <tr>
                                    <td style="padding: 8px 0;"><strong>Submitted:</strong></td>
                                    <td style="padding: 8px 0;">%s</td>
                                </tr>
                            </table>
                           \s
                            <div style="margin-top: 30px;">
                                <a href="http://localhost:5173/admin/registrations/%d"\s
                                   style="display: inline-block; padding: 12px 30px; background-color: #4CAF50;\s
                                          color: white; text-decoration: none; border-radius: 5px;">
                                    Review Registration
                                </a>
                            </div>
                        </div>
                        <div style="background-color: #333; color: white; padding: 15px; text-align: center;">
                            <p style="margin: 0;">© 2024 ServicePoint Admin</p>
                        </div>
                    </body>
                    </html>
                   \s""",
                    registration.getFirstName(),
                    registration.getLastName(),
                    registration.getEmail(),
                    registration.getPhoneNumber() != null ? registration.getPhoneNumber() : "N/A",
                    registration.getLocation() != null ? registration.getLocation() : "N/A",
                    registration.getDocuments().size(),
                    registration.getSubmittedAt(),
                    registration.getRegistrationId()
            );

            emailService.sendEmail(adminEmail, subject, body);
        } catch (MessagingException e) {
            // Log error but don't fail the registration
            System.err.println("Failed to send admin notification: " + e.getMessage());
        }
    }



    public List<ProviderRegistrationDTO> getAllRegistrationsDTO() {
        List<ProviderRegistration> registrations = registrationRepository.findAll();
        return registrations.stream()
                .map(ProviderRegistrationDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<ProviderRegistrationDTO> getApprovedRegistrationsDTO() {
        List<ProviderRegistration> registrations = registrationRepository.findByStatus(ProviderRegistration.RegistrationStatus.APPROVED);
        return registrations.stream()
                .map(ProviderRegistrationDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<ProviderRegistrationDTO> getRejectedRegistrationsDTO() {
        List<ProviderRegistration> registrations = registrationRepository.findByStatus(ProviderRegistration.RegistrationStatus.REJECTED);
        return registrations.stream()
                .map(ProviderRegistrationDTO::fromEntity)
                .collect(Collectors.toList());
    }
}