package com.servicepoint.core.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private BrevoEmailService brevoEmailService;

    @Value("${spring.mail.username}")
    private String fromEmail;

    /**
     * Send generic email - tries Brevo first, falls back to SMTP
     */
    public void sendEmail(String to, String subject, String body) throws MessagingException {
        // Try Brevo API first
        boolean brevoSuccess = brevoEmailService.sendEmail(to, subject, body);

        if (brevoSuccess) {
            return; // Email sent successfully via Brevo
        }

        // Fallback to SMTP (will likely fail on Railway but works locally)
        System.out.println("⚠️ Brevo failed, trying SMTP fallback...");
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true);

            mailSender.send(message);
            System.out.println("✅ SMTP fallback succeeded");
        } catch (Exception e) {
            System.err.println("❌ SMTP fallback also failed: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Send OTP email
     */
    public void sendOtpEmail(String to, String otpCode, String purpose) throws MessagingException {
        String subject = getOtpSubject(purpose);
        String body = buildOtpEmailBody(otpCode, purpose);

        sendEmail(to, subject, body);
    }


    /**
     * Send password reset email with a reset link.
     */
    public void sendPasswordResetEmail(String toEmail, String username, String resetLink) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(toEmail);
        helper.setSubject("Reset Your SpotLocalPro Password");

        String htmlContent = "<!DOCTYPE html>" +
                "<html><body style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;'>" +
                "<div style='background: linear-gradient(135deg, #fb923c, #f59e0b); padding: 30px; border-radius: 12px 12px 0 0; text-align: center;'>" +
                "<h1 style='color: white; margin: 0;'>Password Reset Request</h1>" +
                "</div>" +
                "<div style='background: #fff; padding: 30px; border: 1px solid #fed7aa; border-top: none; border-radius: 0 0 12px 12px;'>" +
                "<p style='color: #1f2937; font-size: 16px;'>Hi " + username + ",</p>" +
                "<p style='color: #4b5563; font-size: 14px; line-height: 1.6;'>" +
                "We received a request to reset your password. Click the button below to set a new one. " +
                "This link will expire in 1 hour." +
                "</p>" +
                "<div style='text-align: center; margin: 30px 0;'>" +
                "<a href='" + resetLink + "' style='background: #fb923c; color: white; padding: 12px 32px; text-decoration: none; border-radius: 8px; font-weight: 600; display: inline-block;'>Reset Password</a>" +
                "</div>" +
                "<p style='color: #6b7280; font-size: 13px; line-height: 1.6;'>" +
                "If the button doesn't work, copy and paste this link into your browser:<br>" +
                "<span style='color: #f59e0b; word-break: break-all;'>" + resetLink + "</span>" +
                "</p>" +
                "<hr style='border: none; border-top: 1px solid #fed7aa; margin: 20px 0;'>" +
                "<p style='color: #9ca3af; font-size: 12px;'>" +
                "If you didn't request this, you can safely ignore this email. Your password won't change." +
                "</p>" +
                "</div>" +
                "</body></html>";

        // Try Brevo first, fallback to SMTP
        boolean brevoSuccess = brevoEmailService.sendEmail(toEmail, "Reset Your SpotLocalPro Password", htmlContent);

        if (brevoSuccess) {
            return;
        }

        // Fallback to SMTP
        System.out.println("⚠️ Brevo failed for password reset, trying SMTP fallback...");
        helper.setText(htmlContent, true);
        mailSender.send(message);
    }

    /**
     * Send provider approval email
     */
    public void sendProviderApprovalEmail(String to, String firstName) throws MessagingException {
        String subject = "🎉 Your Service Provider Registration is Approved!";
        String body = String.format("""
                <html>
                <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                    <div style="background-color: #4CAF50; padding: 20px; text-align: center;">
                        <h1 style="color: white; margin: 0;">Congratulations, %s!</h1>
                    </div>
                    <div style="padding: 20px; background-color: #f9f9f9;">
                        <h2>Your Registration is Approved ✓</h2>
                        <p>We're excited to inform you that your service provider registration has been approved!</p>
                        
                        <div style="background-color: white; padding: 15px; border-left: 4px solid #4CAF50; margin: 20px 0;">
                            <h3>Next Steps:</h3>
                            <ol>
                                <li>Log in to your account</li>
                                <li>Complete your profile</li>
                                <li>Add your services</li>
                                <li>Start receiving booking requests</li>
                            </ol>
                        </div>
                        
                        <a href="http://localhost:3000/auth/login" 
                           style="display: inline-block; padding: 12px 30px; background-color: #4CAF50; 
                                  color: white; text-decoration: none; border-radius: 5px; margin-top: 20px;">
                            Login to Dashboard
                        </a>
                        
                        <p style="margin-top: 30px; color: #666;">
                            If you have any questions, feel free to reach out to our support team.
                        </p>
                    </div>
                    <div style="background-color: #333; color: white; padding: 15px; text-align: center;">
                        <p style="margin: 0;">© 2025 ServicePoint. All rights reserved.</p>
                    </div>
                </body>
                </html>
                """, firstName);

        sendEmail(to, subject, body);
    }

    /**
     * Send provider rejection email
     */
    public void sendProviderRejectionEmail(String to, String firstName, String reason) throws MessagingException {
        String subject = "Update on Your Service Provider Registration";
        String body = String.format("""
                <html>
                <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                    <div style="background-color: #f44336; padding: 20px; text-align: center;">
                        <h1 style="color: white; margin: 0;">Registration Update</h1>
                    </div>
                    <div style="padding: 20px; background-color: #f9f9f9;">
                        <h2>Dear %s,</h2>
                        <p>Thank you for your interest in becoming a service provider on ServicePoint.</p>
                        
                        <p>After careful review, we regret to inform you that we cannot approve your registration at this time.</p>
                        
                        <div style="background-color: white; padding: 15px; border-left: 4px solid #f44336; margin: 20px 0;">
                            <h3>Reason:</h3>
                            <p>%s</p>
                        </div>
                        
                        <div style="background-color: #fff3cd; padding: 15px; border-radius: 5px; margin: 20px 0;">
                            <p style="margin: 0;"><strong>You can reapply!</strong></p>
                            <p style="margin: 5px 0 0 0;">
                                Please address the concerns mentioned above and submit a new application.
                            </p>
                        </div>
                        
                        <p style="margin-top: 30px; color: #666;">
                            If you have any questions or need clarification, please contact our support team.
                        </p>
                    </div>
                    <div style="background-color: #333; color: white; padding: 15px; text-align: center;">
                        <p style="margin: 0;">© 2025 ServicePoint. All rights reserved.</p>
                    </div>
                </body>
                </html>
                """, firstName, reason);

        sendEmail(to, subject, body);
    }

    /**
     * Send booking confirmation email
     */
    public void sendBookingConfirmationEmail(String to, String customerName,
                                             String serviceName, String providerName,
                                             String dateTime, double totalPrice) throws MessagingException {
        String subject = "Booking Confirmation - " + serviceName;
        String body = String.format("""
                <html>
                <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                    <div style="background-color: #2196F3; padding: 20px; text-align: center;">
                        <h1 style="color: white; margin: 0;">Booking Confirmed!</h1>
                    </div>
                    <div style="padding: 20px; background-color: #f9f9f9;">
                        <h2>Hello %s,</h2>
                        <p>Your booking has been confirmed and payment processed successfully.</p>
                        
                        <div style="background-color: white; padding: 20px; border-radius: 5px; margin: 20px 0;">
                            <h3>Booking Details:</h3>
                            <table style="width: 100%%;">
                                <tr>
                                    <td style="padding: 8px 0;"><strong>Service:</strong></td>
                                    <td style="padding: 8px 0;">%s</td>
                                </tr>
                                <tr>
                                    <td style="padding: 8px 0;"><strong>Provider:</strong></td>
                                    <td style="padding: 8px 0;">%s</td>
                                </tr>
                                <tr>
                                    <td style="padding: 8px 0;"><strong>Date & Time:</strong></td>
                                    <td style="padding: 8px 0;">%s</td>
                                </tr>
                                <tr>
                                    <td style="padding: 8px 0;"><strong>Total Paid:</strong></td>
                                    <td style="padding: 8px 0; color: #4CAF50; font-size: 18px;">
                                        <strong>$%.2f</strong>
                                    </td>
                                </tr>
                            </table>
                        </div>
                        
                        <p style="color: #666;">
                            The provider will contact you shortly to confirm the appointment details.
                        </p>
                    </div>
                    <div style="background-color: #333; color: white; padding: 15px; text-align: center;">
                        <p style="margin: 0;">© 2025 ServicePoint. All rights reserved.</p>
                    </div>
                </body>
                </html>
                """, customerName, serviceName, providerName, dateTime, totalPrice);

        sendEmail(to, subject, body);
    }

    private String getOtpSubject(String purpose) {
        return switch (purpose) {
            case "provider_registration" -> "Verify Your Email - Service Provider Registration";
            case "registration" -> "Verify Your Email - ServicePoint Registration";
            case "login" -> "Your Login OTP Code";
            case "password_reset" -> "Password Reset OTP Code";
            default -> "Your OTP Code";
        };
    }

    private String buildOtpEmailBody(String otpCode, String purpose) {
        String action = switch (purpose) {
            case "provider_registration" -> "complete your service provider registration";
            case "registration" -> "complete your registration";
            case "login" -> "log in to your account";
            case "password_reset" -> "reset your password";
            default -> "verify your email";
        };

        return String.format("""
                <html>
                <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                    <div style="background-color: #2196F3; padding: 20px; text-align: center;">
                        <h1 style="color: white; margin: 0;">Email Verification</h1>
                    </div>
                    <div style="padding: 30px; background-color: #f9f9f9; text-align: center;">
                        <p style="font-size: 16px; color: #333;">
                            Use this code to %s:
                        </p>
                        <div style="background-color: white; padding: 20px; margin: 20px 0; 
                                    border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);">
                            <h1 style="color: #2196F3; font-size: 36px; letter-spacing: 8px; margin: 0;">
                                %s
                            </h1>
                        </div>
                        <p style="color: #666; font-size: 14px;">
                            This code will expire in 10 minutes.
                        </p>
                        <p style="color: #999; font-size: 12px; margin-top: 30px;">
                            If you didn't request this code, please ignore this email.
                        </p>
                    </div>
                    <div style="background-color: #333; color: white; padding: 15px; text-align: center;">
                        <p style="margin: 0; font-size: 12px;">© 2025 ServicePoint. All rights reserved.</p>
                    </div>
                </body>
                </html>
                """, action, otpCode);
    }
}