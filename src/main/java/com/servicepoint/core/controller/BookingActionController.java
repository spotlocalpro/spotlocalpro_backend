package com.servicepoint.core.controller;

import com.servicepoint.core.model.Booking;
import com.servicepoint.core.repository.BookingRepository;
import com.servicepoint.core.service.BookingActionTokenService;
import com.servicepoint.core.service.BookingNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

/**
 * Handles provider approve / decline actions from booking notification emails.
 * Endpoints return simple HTML confirmation pages (not JSON) since they're opened
 * directly from email clients in a browser.
 *
 * Security: the token is HMAC-signed, expires in 48h, and is bound to (bookingId, action).
 * Reuse is prevented by checking booking.status — once the booking is no longer "pending",
 * the action is rejected with an already-processed page.
 */
@Controller
@RequestMapping("/api/bookings")
public class BookingActionController {

    private static final Logger log = LoggerFactory.getLogger(BookingActionController.class);

    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_CONFIRMED = "confirmed";
    private static final String STATUS_DECLINED = "declined";

    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private BookingActionTokenService tokenService;
    @Autowired
    private BookingNotificationService notificationService;


    @GetMapping(value = "/{bookingId}/approve", produces = MediaType.TEXT_HTML_VALUE)
    @Transactional
    public ResponseEntity<String> approve(@PathVariable Integer bookingId,
                                          @RequestParam("token") String token) {
        return handleAction(bookingId, token, BookingActionTokenService.ACTION_APPROVE);
    }

    @GetMapping(value = "/{bookingId}/decline", produces = MediaType.TEXT_HTML_VALUE)
    @Transactional
    public ResponseEntity<String> decline(@PathVariable Integer bookingId,
                                          @RequestParam("token") String token) {
        return handleAction(bookingId, token, BookingActionTokenService.ACTION_DECLINE);
    }

    private ResponseEntity<String> handleAction(Integer bookingId, String token, String action) {
        // 1. Verify token signature, expiry, and binding
        if (!tokenService.verifyToken(token, bookingId, action)) {
            log.warn("Invalid or expired token for booking {} action {}", bookingId, action);
            return ResponseEntity.status(403)
                    .contentType(MediaType.TEXT_HTML)
                    .body(page("Link Invalid or Expired",
                            "This approval link is no longer valid. It may have expired (links are "
                                    + "valid for 48 hours) or the link may have been tampered with. "
                                    + "Please ask the customer to submit a new request, or contact support.",
                            "#dc2626"));
        }

        // 2. Load booking
        Optional<Booking> opt = bookingRepository.findById(bookingId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(404)
                    .contentType(MediaType.TEXT_HTML)
                    .body(page("Booking Not Found",
                            "We couldn't find this booking. It may have been removed.",
                            "#dc2626"));
        }
        Booking booking = opt.get();

        // 3. Prevent reuse — only act on pending bookings
        String currentStatus = booking.getStatus();
        if (currentStatus == null || !STATUS_PENDING.equalsIgnoreCase(currentStatus)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(page("Already Processed",
                            "This booking has already been " + currentStatus
                                    + ". No further action is needed.",
                            "#6b7280"));
        }

        // 4. Apply the action
        if (BookingActionTokenService.ACTION_APPROVE.equals(action)) {
            booking.setStatus(STATUS_CONFIRMED);
            bookingRepository.save(booking);
            notificationService.sendApprovalFollowUpToProvider(booking);
            notificationService.sendApprovalNotificationToCustomer(booking);

            // Auto-decline all other pending bookings for same provider at the same time slot
            if (booking.getServiceDateTime() != null) {
                java.util.List<Booking> competing = bookingRepository
                        .findConflicting(
                                booking.getProvider().getUserId(),
                                booking.getServiceDateTime(),
                                STATUS_PENDING);
                for (Booking conflict : competing) {
                    if (!conflict.getBookingId().equals(bookingId)) {
                        conflict.setStatus(STATUS_DECLINED);
                        bookingRepository.save(conflict);
                        notificationService.sendDeclineNotificationToCustomer(conflict);
                        log.info("Auto-declined competing booking {} for provider {} at same time slot",
                                conflict.getBookingId(), booking.getProvider().getUserId());
                    }
                }
            }

            log.info("Booking {} approved by provider via email link", bookingId);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(page("Booking Approved",
                            "You have approved this booking. The customer's contact details "
                                    + "have been sent to your email. Please reach out shortly to confirm the appointment.",
                            "#16a34a"));
        } else {
            booking.setStatus(STATUS_DECLINED);
            bookingRepository.save(booking);
            notificationService.sendDeclineNotificationToCustomer(booking);
            log.info("Booking {} declined by provider via email link", bookingId);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(page("Booking Declined",
                            "You have declined this booking. The customer has been notified.",
                            "#6b7280"));
        }
    }

    /** Simple styled confirmation page - OPTIMIZED & SIMPLIFIED */
    private static String page(String title, String message, String accentColor) {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'><title>" + title + " — SpotLocalPro</title>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>"
                + "body{font-family:-apple-system,Segoe UI,Arial,sans-serif;background:#f9fafb;margin:0;padding:40px 20px;color:#1f2937;}"
                + ".card{max-width:500px;margin:50px auto;background:#fff;border-radius:12px;padding:35px;text-align:center;}"
                + ".bar{height:5px;background:" + accentColor + ";border-radius:12px 12px 0 0;margin:-35px -35px 25px;}"
                + "h1{font-size:24px;margin:0 0 14px;color:" + accentColor + ";}"
                + "p{font-size:15px;line-height:1.5;color:#4b5563;margin:0 0 20px;}"
                + ".brand{font-size:15px;color:#6b7280;margin-top:28px;font-weight:600;}"
                + "</style></head><body>"
                + "<div class='card'><div class='bar'></div>"
                + "<h1>" + title + "</h1><p>" + message + "</p>"
                + "<div class='brand'>The SpotLocalPro Team</div>"
                + "</div></body></html>";
    }
}