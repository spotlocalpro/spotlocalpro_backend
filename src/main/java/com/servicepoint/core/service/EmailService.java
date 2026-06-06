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

    private static boolean isEs(String lang) {
        return "es".equalsIgnoreCase(lang);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Core send — tries Brevo first, falls back to SMTP
    // ─────────────────────────────────────────────────────────────────────────
    public void sendEmail(String to, String subject, String body) throws MessagingException {
        boolean brevoSuccess = brevoEmailService.sendEmail(to, subject, body);
        if (brevoSuccess) return;

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

    // ─────────────────────────────────────────────────────────────────────────
    //  OTP email (EN + ES)
    // ─────────────────────────────────────────────────────────────────────────
    public void sendOtpEmail(String to, String otpCode, String purpose) throws MessagingException {
        sendOtpEmail(to, otpCode, purpose, "en");
    }

    public void sendOtpEmail(String to, String otpCode, String purpose, String lang) throws MessagingException {
        String subject = getOtpSubject(purpose, lang);
        String body    = buildOtpEmailBody(otpCode, purpose, lang);
        sendEmail(to, subject, body);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Password reset email (EN + ES)
    // ─────────────────────────────────────────────────────────────────────────
    public void sendPasswordResetEmail(String toEmail, String username, String resetLink) throws MessagingException {
        sendPasswordResetEmail(toEmail, username, resetLink, "en");
    }

    public void sendPasswordResetEmail(String toEmail, String username, String resetLink, String lang) throws MessagingException {
        boolean es = isEs(lang);
        String subject = es ? "Restablece tu Contraseña en SpotLocalPro" : "Reset Your SpotLocalPro Password";

        String htmlContent = es
                ? buildPasswordResetBodyEs(username, resetLink)
                : buildPasswordResetBodyEn(username, resetLink);

        boolean brevoSuccess = brevoEmailService.sendEmail(toEmail, subject, htmlContent);
        if (brevoSuccess) return;

        System.out.println("⚠️ Brevo failed for password reset, trying SMTP fallback...");
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setTo(toEmail);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);
        mailSender.send(message);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Provider approval email (EN + ES)
    // ─────────────────────────────────────────────────────────────────────────
    public void sendProviderApprovalEmail(String to, String firstName) throws MessagingException {
        sendProviderApprovalEmail(to, firstName, "en");
    }

    public void sendProviderApprovalEmail(String to, String firstName, String lang) throws MessagingException {
        boolean es = isEs(lang);
        String subject = es
                ? "🎉 ¡Tu Registro como Proveedor ha sido Aprobado!"
                : "🎉 Your Service Provider Registration is Approved!";
        String body = es ? buildProviderApprovalBodyEs(firstName) : buildProviderApprovalBodyEn(firstName);
        sendEmail(to, subject, body);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Provider rejection email (EN + ES)
    // ─────────────────────────────────────────────────────────────────────────
    public void sendProviderRejectionEmail(String to, String firstName, String reason) throws MessagingException {
        sendProviderRejectionEmail(to, firstName, reason, "en");
    }

    public void sendProviderRejectionEmail(String to, String firstName, String reason, String lang) throws MessagingException {
        boolean es = isEs(lang);
        String subject = es
                ? "Actualización sobre tu Registro como Proveedor"
                : "Update on Your Service Provider Registration";
        String body = es ? buildProviderRejectionBodyEs(firstName, reason) : buildProviderRejectionBodyEn(firstName, reason);
        sendEmail(to, subject, body);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Booking confirmation email (EN + ES)
    // ─────────────────────────────────────────────────────────────────────────
    public void sendBookingConfirmationEmail(String to, String customerName,
                                             String serviceName, String providerName,
                                             String dateTime, double totalPrice) throws MessagingException {
        sendBookingConfirmationEmail(to, customerName, serviceName, providerName, dateTime, totalPrice, "en");
    }

    public void sendBookingConfirmationEmail(String to, String customerName,
                                             String serviceName, String providerName,
                                             String dateTime, double totalPrice, String lang) throws MessagingException {
        boolean es = isEs(lang);
        String subject = es
                ? "Confirmación de Reserva — " + serviceName
                : "Booking Confirmation — " + serviceName;
        String body = es
                ? buildBookingConfirmationBodyEs(customerName, serviceName, providerName, dateTime, totalPrice)
                : buildBookingConfirmationBodyEn(customerName, serviceName, providerName, dateTime, totalPrice);
        sendEmail(to, subject, body);
    }

    // =========================================================================
    //  OTP helpers
    // =========================================================================

    private String getOtpSubject(String purpose, String lang) {
        if (isEs(lang)) {
            return switch (purpose) {
                case "provider_registration" -> "Verifica tu Correo — Registro de Proveedor de Servicios";
                case "registration"          -> "Verifica tu Correo — Registro en SpotLocalPro";
                case "login"                 -> "Tu Código OTP de Inicio de Sesión";
                case "password_reset"        -> "Código OTP para Restablecer Contraseña";
                default                      -> "Tu Código OTP";
            };
        }
        return switch (purpose) {
            case "provider_registration" -> "Verify Your Email - Service Provider Registration";
            case "registration"          -> "Verify Your Email - SpotLocalPro Registration";
            case "login"                 -> "Your Login OTP Code";
            case "password_reset"        -> "Password Reset OTP Code";
            default                      -> "Your OTP Code";
        };
    }

    private String buildOtpEmailBody(String otpCode, String purpose, String lang) {
        if (isEs(lang)) {
            String action = switch (purpose) {
                case "provider_registration" -> "completar tu registro como proveedor de servicios";
                case "registration"          -> "completar tu registro";
                case "login"                 -> "iniciar sesión en tu cuenta";
                case "password_reset"        -> "restablecer tu contraseña";
                default                      -> "verificar tu correo electrónico";
            };
            return String.format("""
                    <html>
                    <body style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;">
                        <div style="background-color:#fb923c;padding:20px;text-align:center;">
                            <h1 style="color:white;margin:0;">Verificación de Correo</h1>
                        </div>
                        <div style="padding:30px;background-color:#f9f9f9;text-align:center;">
                            <p style="font-size:16px;color:#333;">
                                Usa este código para %s:
                            </p>
                            <div style="background-color:white;padding:20px;margin:20px 0;border-radius:8px;box-shadow:0 2px 4px rgba(0,0,0,0.1);">
                                <h1 style="color:#fb923c;font-size:36px;letter-spacing:8px;margin:0;">%s</h1>
                            </div>
                            <p style="color:#666;font-size:14px;">Este código expirará en 10 minutos.</p>
                            <p style="color:#999;font-size:12px;margin-top:30px;">
                                Si no solicitaste este código, ignora este correo.
                            </p>
                        </div>
                        <div style="background-color:#333;color:white;padding:15px;text-align:center;">
                            <p style="margin:0;font-size:12px;">© 2026 SpotLocalPro. Todos los derechos reservados.</p>
                        </div>
                    </body>
                    </html>
                    """, action, otpCode);
        }

        String action = switch (purpose) {
            case "provider_registration" -> "complete your service provider registration";
            case "registration"          -> "complete your registration";
            case "login"                 -> "log in to your account";
            case "password_reset"        -> "reset your password";
            default                      -> "verify your email";
        };
        return String.format("""
                <html>
                <body style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;">
                    <div style="background-color:#2196F3;padding:20px;text-align:center;">
                        <h1 style="color:white;margin:0;">Email Verification</h1>
                    </div>
                    <div style="padding:30px;background-color:#f9f9f9;text-align:center;">
                        <p style="font-size:16px;color:#333;">Use this code to %s:</p>
                        <div style="background-color:white;padding:20px;margin:20px 0;border-radius:8px;box-shadow:0 2px 4px rgba(0,0,0,0.1);">
                            <h1 style="color:#2196F3;font-size:36px;letter-spacing:8px;margin:0;">%s</h1>
                        </div>
                        <p style="color:#666;font-size:14px;">This code will expire in 10 minutes.</p>
                        <p style="color:#999;font-size:12px;margin-top:30px;">If you didn't request this code, please ignore this email.</p>
                    </div>
                    <div style="background-color:#333;color:white;padding:15px;text-align:center;">
                        <p style="margin:0;font-size:12px;">© 2026 SpotLocalPro. All rights reserved.</p>
                    </div>
                </body>
                </html>
                """, action, otpCode);
    }

    // =========================================================================
    //  Password reset templates
    // =========================================================================

    private String buildPasswordResetBodyEn(String username, String resetLink) {
        return "<!DOCTYPE html><html><body style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px;'>"
                + "<div style='background:linear-gradient(135deg,#fb923c,#f59e0b);padding:30px;border-radius:12px 12px 0 0;text-align:center;'>"
                + "<h1 style='color:white;margin:0;'>Password Reset Request</h1></div>"
                + "<div style='background:#fff;padding:30px;border:1px solid #fed7aa;border-top:none;border-radius:0 0 12px 12px;'>"
                + "<p style='color:#1f2937;font-size:16px;'>Hi " + username + ",</p>"
                + "<p style='color:#4b5563;font-size:14px;line-height:1.6;'>We received a request to reset your password. Click the button below to set a new one. This link will expire in 1 hour.</p>"
                + "<div style='text-align:center;margin:30px 0;'><a href='" + resetLink + "' style='background:#fb923c;color:white;padding:12px 32px;text-decoration:none;border-radius:8px;font-weight:600;display:inline-block;'>Reset Password</a></div>"
                + "<p style='color:#6b7280;font-size:13px;line-height:1.6;'>If the button doesn't work, copy and paste this link:<br><span style='color:#f59e0b;word-break:break-all;'>" + resetLink + "</span></p>"
                + "<hr style='border:none;border-top:1px solid #fed7aa;margin:20px 0;'>"
                + "<p style='color:#9ca3af;font-size:12px;'>If you didn't request this, you can safely ignore this email.</p>"
                + "</div></body></html>";
    }

    private String buildPasswordResetBodyEs(String username, String resetLink) {
        return "<!DOCTYPE html><html><body style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px;'>"
                + "<div style='background:linear-gradient(135deg,#fb923c,#f59e0b);padding:30px;border-radius:12px 12px 0 0;text-align:center;'>"
                + "<h1 style='color:white;margin:0;'>Solicitud de Restablecimiento de Contraseña</h1></div>"
                + "<div style='background:#fff;padding:30px;border:1px solid #fed7aa;border-top:none;border-radius:0 0 12px 12px;'>"
                + "<p style='color:#1f2937;font-size:16px;'>Hola " + username + ",</p>"
                + "<p style='color:#4b5563;font-size:14px;line-height:1.6;'>Recibimos una solicitud para restablecer tu contraseña. Haz clic en el botón de abajo para establecer una nueva. Este enlace expirará en 1 hora.</p>"
                + "<div style='text-align:center;margin:30px 0;'><a href='" + resetLink + "' style='background:#fb923c;color:white;padding:12px 32px;text-decoration:none;border-radius:8px;font-weight:600;display:inline-block;'>Restablecer Contraseña</a></div>"
                + "<p style='color:#6b7280;font-size:13px;line-height:1.6;'>Si el botón no funciona, copia y pega este enlace:<br><span style='color:#f59e0b;word-break:break-all;'>" + resetLink + "</span></p>"
                + "<hr style='border:none;border-top:1px solid #fed7aa;margin:20px 0;'>"
                + "<p style='color:#9ca3af;font-size:12px;'>Si no solicitaste esto, puedes ignorar este correo con seguridad.</p>"
                + "</div></body></html>";
    }

    // =========================================================================
    //  Provider approval templates
    // =========================================================================

    private String buildProviderApprovalBodyEn(String firstName) {
        return String.format("""
                <html><body style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;">
                    <div style="background-color:#4CAF50;padding:20px;text-align:center;">
                        <h1 style="color:white;margin:0;">Congratulations, %s!</h1>
                    </div>
                    <div style="padding:20px;background-color:#f9f9f9;">
                        <h2>Your Registration is Approved ✓</h2>
                        <p>We're excited to inform you that your service provider registration has been approved!</p>
                        <div style="background-color:white;padding:15px;border-left:4px solid #4CAF50;margin:20px 0;">
                            <h3>Next Steps:</h3>
                            <ol><li>Log in to your account</li><li>Complete your profile</li><li>Add your services</li><li>Start receiving booking requests</li></ol>
                        </div>
                        <a href="https://spotlocalpro.com/auth/login" style="display:inline-block;padding:12px 30px;background-color:#4CAF50;color:white;text-decoration:none;border-radius:5px;margin-top:20px;">Login to Dashboard</a>
                        <p style="margin-top:30px;color:#666;">If you have any questions, feel free to reach out to our support team.</p>
                    </div>
                    <div style="background-color:#333;color:white;padding:15px;text-align:center;">
                        <p style="margin:0;">© 2026 SpotLocalPro. All rights reserved.</p>
                    </div>
                </body></html>
                """, firstName);
    }

    private String buildProviderApprovalBodyEs(String firstName) {
        return String.format("""
                <html><body style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;">
                    <div style="background-color:#4CAF50;padding:20px;text-align:center;">
                        <h1 style="color:white;margin:0;">¡Felicitaciones, %s!</h1>
                    </div>
                    <div style="padding:20px;background-color:#f9f9f9;">
                        <h2>Tu Registro ha sido Aprobado ✓</h2>
                        <p>¡Nos complace informarte que tu registro como proveedor de servicios ha sido aprobado!</p>
                        <div style="background-color:white;padding:15px;border-left:4px solid #4CAF50;margin:20px 0;">
                            <h3>Próximos Pasos:</h3>
                            <ol><li>Inicia sesión en tu cuenta</li><li>Completa tu perfil</li><li>Agrega tus servicios</li><li>Comienza a recibir solicitudes de reserva</li></ol>
                        </div>
                        <a href="https://spotlocalpro.com/auth/login" style="display:inline-block;padding:12px 30px;background-color:#4CAF50;color:white;text-decoration:none;border-radius:5px;margin-top:20px;">Acceder al Panel</a>
                        <p style="margin-top:30px;color:#666;">Si tienes alguna pregunta, no dudes en contactar a nuestro equipo de soporte.</p>
                    </div>
                    <div style="background-color:#333;color:white;padding:15px;text-align:center;">
                        <p style="margin:0;">© 2026 SpotLocalPro. Todos los derechos reservados.</p>
                    </div>
                </body></html>
                """, firstName);
    }

    // =========================================================================
    //  Provider rejection templates
    // =========================================================================

    private String buildProviderRejectionBodyEn(String firstName, String reason) {
        return String.format("""
                <html><body style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;">
                    <div style="background-color:#f44336;padding:20px;text-align:center;">
                        <h1 style="color:white;margin:0;">Registration Update</h1>
                    </div>
                    <div style="padding:20px;background-color:#f9f9f9;">
                        <h2>Dear %s,</h2>
                        <p>Thank you for your interest in becoming a service provider on SpotLocalPro.</p>
                        <p>After careful review, we regret to inform you that we cannot approve your registration at this time.</p>
                        <div style="background-color:white;padding:15px;border-left:4px solid #f44336;margin:20px 0;">
                            <h3>Reason:</h3><p>%s</p>
                        </div>
                        <div style="background-color:#fff3cd;padding:15px;border-radius:5px;margin:20px 0;">
                            <p style="margin:0;"><strong>You can reapply!</strong></p>
                            <p style="margin:5px 0 0 0;">Please address the concerns mentioned above and submit a new application.</p>
                        </div>
                        <p style="margin-top:30px;color:#666;">If you have any questions, please contact our support team.</p>
                    </div>
                    <div style="background-color:#333;color:white;padding:15px;text-align:center;">
                        <p style="margin:0;">© 2026 SpotLocalPro. All rights reserved.</p>
                    </div>
                </body></html>
                """, firstName, reason);
    }

    private String buildProviderRejectionBodyEs(String firstName, String reason) {
        return String.format("""
                <html><body style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;">
                    <div style="background-color:#f44336;padding:20px;text-align:center;">
                        <h1 style="color:white;margin:0;">Actualización de Registro</h1>
                    </div>
                    <div style="padding:20px;background-color:#f9f9f9;">
                        <h2>Estimado/a %s,</h2>
                        <p>Gracias por tu interés en convertirte en proveedor de servicios en SpotLocalPro.</p>
                        <p>Después de una revisión cuidadosa, lamentamos informarte que no podemos aprobar tu registro en este momento.</p>
                        <div style="background-color:white;padding:15px;border-left:4px solid #f44336;margin:20px 0;">
                            <h3>Motivo:</h3><p>%s</p>
                        </div>
                        <div style="background-color:#fff3cd;padding:15px;border-radius:5px;margin:20px 0;">
                            <p style="margin:0;"><strong>¡Puedes volver a solicitar!</strong></p>
                            <p style="margin:5px 0 0 0;">Por favor, atiende los puntos mencionados y envía una nueva solicitud.</p>
                        </div>
                        <p style="margin-top:30px;color:#666;">Si tienes alguna pregunta, contáctanos.</p>
                    </div>
                    <div style="background-color:#333;color:white;padding:15px;text-align:center;">
                        <p style="margin:0;">© 2026 SpotLocalPro. Todos los derechos reservados.</p>
                    </div>
                </body></html>
                """, firstName, reason);
    }

    // =========================================================================
    //  Booking confirmation templates
    // =========================================================================

    private String buildBookingConfirmationBodyEn(String customerName, String serviceName,
                                                   String providerName, String dateTime, double totalPrice) {
        return String.format("""
                <html><body style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;">
                    <div style="background-color:#2196F3;padding:20px;text-align:center;">
                        <h1 style="color:white;margin:0;">Booking Confirmed!</h1>
                    </div>
                    <div style="padding:20px;background-color:#f9f9f9;">
                        <h2>Hello %s,</h2>
                        <p>Your booking has been confirmed and payment processed successfully.</p>
                        <div style="background-color:white;padding:20px;border-radius:5px;margin:20px 0;">
                            <h3>Booking Details:</h3>
                            <table style="width:100%%;">
                                <tr><td><strong>Service:</strong></td><td>%s</td></tr>
                                <tr><td><strong>Provider:</strong></td><td>%s</td></tr>
                                <tr><td><strong>Date &amp; Time:</strong></td><td>%s</td></tr>
                                <tr><td><strong>Total Paid:</strong></td><td style="color:#4CAF50;font-size:18px;"><strong>$%.2f</strong></td></tr>
                            </table>
                        </div>
                        <p style="color:#666;">The provider will contact you shortly to confirm the appointment details.</p>
                    </div>
                    <div style="background-color:#333;color:white;padding:15px;text-align:center;">
                        <p style="margin:0;">© 2026 SpotLocalPro. All rights reserved.</p>
                    </div>
                </body></html>
                """, customerName, serviceName, providerName, dateTime, totalPrice);
    }

    private String buildBookingConfirmationBodyEs(String customerName, String serviceName,
                                                   String providerName, String dateTime, double totalPrice) {
        return String.format("""
                <html><body style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;">
                    <div style="background-color:#2196F3;padding:20px;text-align:center;">
                        <h1 style="color:white;margin:0;">¡Reserva Confirmada!</h1>
                    </div>
                    <div style="padding:20px;background-color:#f9f9f9;">
                        <h2>Hola %s,</h2>
                        <p>Tu reserva ha sido confirmada y el pago procesado exitosamente.</p>
                        <div style="background-color:white;padding:20px;border-radius:5px;margin:20px 0;">
                            <h3>Detalles de la Reserva:</h3>
                            <table style="width:100%%;">
                                <tr><td><strong>Servicio:</strong></td><td>%s</td></tr>
                                <tr><td><strong>Proveedor:</strong></td><td>%s</td></tr>
                                <tr><td><strong>Fecha y Hora:</strong></td><td>%s</td></tr>
                                <tr><td><strong>Total Pagado:</strong></td><td style="color:#4CAF50;font-size:18px;"><strong>$%.2f</strong></td></tr>
                            </table>
                        </div>
                        <p style="color:#666;">El proveedor se pondrá en contacto contigo para confirmar los detalles de la cita.</p>
                    </div>
                    <div style="background-color:#333;color:white;padding:15px;text-align:center;">
                        <p style="margin:0;">© 2026 SpotLocalPro. Todos los derechos reservados.</p>
                    </div>
                </body></html>
                """, customerName, serviceName, providerName, dateTime, totalPrice);
    }
}
