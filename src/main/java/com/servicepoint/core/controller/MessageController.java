package com.servicepoint.core.controller;

import com.servicepoint.core.dto.ContactMessageRequest;
import com.servicepoint.core.model.User;
import com.servicepoint.core.repository.UserRepository;
import com.servicepoint.core.service.BrevoEmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/messages")
@CrossOrigin(origins = "*")
public class MessageController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BrevoEmailService emailService;

    @PostMapping("/contact")
    public ResponseEntity<?> contactProvider(@RequestBody ContactMessageRequest req) {
        if (req.getProviderId() == null || req.getMessage() == null || req.getMessage().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "providerId and message are required"));
        }

        Optional<User> providerOpt = userRepository.findById(req.getProviderId());
        if (providerOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        User provider = providerOpt.get();

        String subject = req.getServiceName() != null
                ? "New inquiry about: " + req.getServiceName()
                : "New message via SpotLocalPro";

        String phone = req.getSenderPhone() != null && !req.getSenderPhone().isBlank()
                ? req.getSenderPhone()
                : "Not provided";

        String html = """
                <div style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;">
                  <div style="background:linear-gradient(135deg,#f97316,#f59e0b);padding:20px 24px;border-radius:12px 12px 0 0;">
                    <h2 style="color:#fff;margin:0;font-size:20px;">New Customer Inquiry</h2>
                    <p style="color:rgba(255,255,255,0.85);margin:4px 0 0;font-size:13px;">via SpotLocalPro</p>
                  </div>
                  <div style="background:#fff;border:1px solid #e5e7eb;border-top:none;padding:24px;border-radius:0 0 12px 12px;">
                    <table style="width:100%;border-collapse:collapse;">
                      <tr><td style="padding:8px 0;color:#6b7280;font-size:13px;width:110px;">From</td>
                          <td style="padding:8px 0;font-weight:600;font-size:14px;">%s</td></tr>
                      <tr><td style="padding:8px 0;color:#6b7280;font-size:13px;">Phone</td>
                          <td style="padding:8px 0;font-size:14px;">%s</td></tr>
                      %s
                    </table>
                    <div style="background:#f9fafb;border-left:3px solid #f97316;padding:14px 16px;border-radius:0 8px 8px 0;margin:16px 0;">
                      <p style="margin:0;font-size:14px;color:#111827;line-height:1.6;">%s</p>
                    </div>
                    <p style="margin:16px 0 0;font-size:12px;color:#9ca3af;">
                      Reply directly to the customer's phone or contact them through the SpotLocalPro platform.
                    </p>
                  </div>
                </div>
                """.formatted(
                escapeHtml(req.getSenderName()),
                escapeHtml(phone),
                req.getServiceName() != null
                        ? "<tr><td style=\"padding:8px 0;color:#6b7280;font-size:13px;\">Service</td><td style=\"padding:8px 0;font-size:14px;\">%s</td></tr>".formatted(escapeHtml(req.getServiceName()))
                        : "",
                escapeHtml(req.getMessage())
        );

        boolean sent = emailService.sendEmail(provider.getEmail(), subject, html);

        if (sent) {
            return ResponseEntity.ok(Map.of("success", true, "message", "Message delivered to provider"));
        } else {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to deliver message. Please try again."));
        }
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
