package com.servicepoint.core.service;

import com.servicepoint.core.model.Booking;
import com.servicepoint.core.model.User;
import jakarta.mail.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class BookingNotificationServiceImpl implements BookingNotificationService {

    private static final Logger log = LoggerFactory.getLogger(BookingNotificationServiceImpl.class);

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("EEEE, MMM d 'at' h:mm a", Locale.US);

    private final EmailService emailService;
    private final BookingActionTokenService tokenService;
    private final String frontendBaseUrl;

    public BookingNotificationServiceImpl(
            EmailService emailService,
            BookingActionTokenService tokenService,
            @Value("${frontend.base-url:${FRONTEND_BASE_URL:http://localhost:5173}}") String frontendBaseUrl) {
        this.emailService = emailService;
        this.tokenService = tokenService;
        // strip trailing slash
        this.frontendBaseUrl = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
                : frontendBaseUrl;
    }

    // ---------------------------------------------------------------------
    //  1. Initial "new booking request" email to provider (safe details only)
    // ---------------------------------------------------------------------
    @Override
    @Async
    public void sendNewBookingRequestToProvider(Booking booking) {
        try {
            User provider = booking.getProvider();
            User customer = booking.getCustomer();
            if (provider == null || provider.getEmail() == null) {
                log.warn("Skipping provider notification: missing provider/email for booking {}",
                        booking.getBookingId());
                return;
            }

            String serviceName = booking.getService() != null ? booking.getService().getName() : "Service";
            String category = booking.getService() != null ? booking.getService().getCategory() : "";
            String pricingType = booking.getService() != null ? booking.getService().getPricingType() : "";
            String priceText = formatPrice(booking.getTotalPrice(), pricingType);
            String dateTimeText = formatDateTime(booking);
            String notes = (booking.getNotes() == null || booking.getNotes().isBlank())
                    ? "None" : escape(booking.getNotes());
            String customerDisplay = escape(firstNameOrUsername(customer));
            String distanceText = formatDistance(provider, customer);

            String approveToken = tokenService.generateToken(
                    booking.getBookingId(), BookingActionTokenService.ACTION_APPROVE);
            String declineToken = tokenService.generateToken(
                    booking.getBookingId(), BookingActionTokenService.ACTION_DECLINE);

            // Links point to the FRONTEND — React handles the click, extracts the token,
            // calls the backend, and shows the result page.
            String approveUrl = frontendBaseUrl + "/api/bookings/" + booking.getBookingId()
                    + "/approve?token=" + approveToken;
            String declineUrl = frontendBaseUrl + "/api/bookings/" + booking.getBookingId()
                    + "/decline?token=" + declineToken;

            String subject = "New Booking Request — " + serviceName;
            String body = buildNewRequestHtml(
                    escape(firstNameOrUsername(provider)),
                    customerDisplay, distanceText,
                    escape(serviceName), escape(category),
                    dateTimeText, priceText, notes,
                    approveUrl, declineUrl);

            emailService.sendEmail(provider.getEmail(), subject, body);
            log.info("Sent new booking request email for booking {} to provider {}",
                    booking.getBookingId(), provider.getEmail());
        } catch (MessagingException e) {
            log.error("Failed to send new booking request email for booking {}: {}",
                    booking.getBookingId(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error sending booking request email for booking {}",
                    booking.getBookingId(), e);
        }
    }

    // ---------------------------------------------------------------------
    //  2. Approval follow-up to provider (reveals customer contact details)
    // ---------------------------------------------------------------------
    @Override
    @Async
    public void sendApprovalFollowUpToProvider(Booking booking) {
        try {
            User provider = booking.getProvider();
            User customer = booking.getCustomer();
            if (provider == null || provider.getEmail() == null) return;

            String serviceName = booking.getService() != null ? booking.getService().getName() : "Service";
            String dateTimeText = formatDateTime(booking);
            String phone = (customer != null && customer.getPhoneNumber() != null)
                    ? escape(customer.getPhoneNumber()) : "Not provided";
            String location = (customer != null && customer.getLocation() != null)
                    ? escape(customer.getLocation()) : "Not provided";

            String subject = "Booking Approved — Customer Contact Details";
            String body = buildApprovalFollowUpHtml(
                    escape(firstNameOrUsername(provider)),
                    escape(firstNameOrUsername(customer)),
                    phone, location, dateTimeText, escape(serviceName));

            emailService.sendEmail(provider.getEmail(), subject, body);
            log.info("Sent approval follow-up email for booking {} to provider {}",
                    booking.getBookingId(), provider.getEmail());
        } catch (MessagingException e) {
            log.error("Failed to send approval follow-up for booking {}: {}",
                    booking.getBookingId(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error sending approval follow-up for booking {}",
                    booking.getBookingId(), e);
        }
    }

    // ---------------------------------------------------------------------
    //  3. Approval notification to customer
    // ---------------------------------------------------------------------
    @Override
    @Async
    public void sendApprovalNotificationToCustomer(Booking booking) {
        try {
            User customer = booking.getCustomer();
            User provider = booking.getProvider();
            if (customer == null || customer.getEmail() == null) return;

            String serviceName = booking.getService() != null ? booking.getService().getName() : "Service";
            String providerName = provider != null ? firstNameOrUsername(provider) : "Your provider";
            String dateTimeText = formatDateTime(booking);
            String priceText = booking.getTotalPrice() != null
                    ? String.format(Locale.US, "$%.2f", booking.getTotalPrice())
                    : "TBD";

            String subject = "Your Booking Request Was Approved!";
            String body = buildApprovalCustomerHtml(
                    escape(firstNameOrUsername(customer)),
                    escape(providerName),
                    escape(serviceName),
                    dateTimeText,
                    priceText);

            emailService.sendEmail(customer.getEmail(), subject, body);
            log.info("Sent approval notification for booking {} to customer {}",
                    booking.getBookingId(), customer.getEmail());
        } catch (MessagingException e) {
            log.error("Failed to send approval notification for booking {}: {}",
                    booking.getBookingId(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error sending approval notification for booking {}",
                    booking.getBookingId(), e);
        }
    }

    // ---------------------------------------------------------------------
    //  4. Decline notification to customer
    // ---------------------------------------------------------------------
    @Override
    @Async
    public void sendDeclineNotificationToCustomer(Booking booking) {
        try {
            User customer = booking.getCustomer();
            User provider = booking.getProvider();
            if (customer == null || customer.getEmail() == null) return;

            String serviceName = booking.getService() != null ? booking.getService().getName() : "Service";
            String providerName = provider != null ? firstNameOrUsername(provider) : "The provider";
            String dateTimeText = formatDateTime(booking);

            String subject = "Update on Your Booking Request";
            String body = buildDeclineCustomerHtml(
                    escape(firstNameOrUsername(customer)),
                    escape(providerName),
                    escape(serviceName),
                    dateTimeText);

            emailService.sendEmail(customer.getEmail(), subject, body);
            log.info("Sent decline notification for booking {} to customer {}",
                    booking.getBookingId(), customer.getEmail());
        } catch (MessagingException e) {
            log.error("Failed to send decline notification for booking {}: {}",
                    booking.getBookingId(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error sending decline notification for booking {}",
                    booking.getBookingId(), e);
        }
    }

    // =====================================================================
    //  Helpers
    // =====================================================================

    private static String firstNameOrUsername(User u) {
        if (u == null) return "there";
        return u.getUsername() != null ? u.getUsername() : "there";
    }

    private static String formatDateTime(Booking booking) {
        if (booking.getServiceDateTime() == null) return "Not specified";
        return booking.getServiceDateTime().toLocalDateTime().format(DATE_FMT);
    }

    private static String formatPrice(Double total, String pricingType) {
        if (total == null) return "Price TBD";
        if ("hourly".equalsIgnoreCase(pricingType)) {
            return String.format(Locale.US, "$%.2f/hr", total);
        }
        return String.format(Locale.US, "$%.2f flat", total);
    }

    /**
     * Haversine distance in miles between provider and customer coords.
     * Returns "Distance unavailable" if either side is missing coordinates.
     */
    static String formatDistance(User provider, User customer) {
        if (provider == null || customer == null) return "Distance unavailable";
        Double pLat = provider.getLatitude(), pLng = provider.getLongitude();
        Double cLat = customer.getLatitude(), cLng = customer.getLongitude();
        if (pLat == null || pLng == null || cLat == null || cLng == null) {
            return "Distance unavailable";
        }
        double miles = haversineMiles(pLat, pLng, cLat, cLng);
        return String.format(Locale.US, "%.1f miles away", miles);
    }

    private static double haversineMiles(double lat1, double lng1, double lat2, double lng2) {
        final double R_MILES = 3958.7613;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R_MILES * c;
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    // =====================================================================
    //  Email body templates
    // =====================================================================

    private String buildNewRequestHtml(String providerName, String customerName, String distance,
                                       String serviceName, String category, String dateTime,
                                       String price, String notes,
                                       String approveUrl, String declineUrl) {
        return "<!DOCTYPE html><html><body style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;'>"
                + "<div style='background:linear-gradient(135deg,#fb923c,#f59e0b);padding:24px;text-align:center;border-radius:12px 12px 0 0;'>"
                + "<h1 style='color:white;margin:0;'>New Booking Request</h1></div>"
                + "<div style='background:#fff;padding:28px;border:1px solid #fed7aa;border-top:none;border-radius:0 0 12px 12px;'>"
                + "<p style='font-size:16px;color:#1f2937;'>Hi " + providerName + ",</p>"
                + "<p style='color:#4b5563;'>You have a new booking request on ServicePoint!</p>"
                + "<table style='width:100%;border-collapse:collapse;margin:20px 0;'>"
                + row("Customer", customerName)
                + row("Distance", distance)
                + row("Service", serviceName + (category.isEmpty() ? "" : " (" + category + ")"))
                + row("Requested", dateTime)
                + row("Price", price)
                + row("Notes", notes)
                + "</table>"
                + "<p style='color:#4b5563;font-size:14px;'>To view the customer's contact details "
                + "(phone number and location), you must first approve this request.</p>"
                + "<div style='text-align:center;margin:30px 0;'>"
                + "<a href='" + approveUrl + "' style='background:#16a34a;color:white;padding:12px 28px;"
                + "text-decoration:none;border-radius:8px;font-weight:600;display:inline-block;margin:4px 8px;'>"
                + "APPROVE BOOKING</a>"
                + "<a href='" + declineUrl + "' style='background:#dc2626;color:white;padding:12px 28px;"
                + "text-decoration:none;border-radius:8px;font-weight:600;display:inline-block;margin:4px 8px;'>"
                + "DECLINE BOOKING</a>"
                + "</div>"
                + "<p style='color:#9ca3af;font-size:12px;text-align:center;'>These links expire in 48 hours.</p>"
                + "<hr style='border:none;border-top:1px solid #fed7aa;margin:20px 0;'>"
                + "<p style='color:#9ca3af;font-size:12px;text-align:center;'>— The ServicePoint Team</p>"
                + "</div></body></html>";
    }

    private String buildApprovalFollowUpHtml(String providerName, String customerName,
                                             String phone, String location,
                                             String dateTime, String serviceName) {
        return "<!DOCTYPE html><html><body style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;'>"
                + "<div style='background:#16a34a;padding:24px;text-align:center;border-radius:12px 12px 0 0;'>"
                + "<h1 style='color:white;margin:0;'>Booking Approved</h1></div>"
                + "<div style='background:#fff;padding:28px;border:1px solid #bbf7d0;border-top:none;border-radius:0 0 12px 12px;'>"
                + "<p style='font-size:16px;color:#1f2937;'>Hi " + providerName + ",</p>"
                + "<p style='color:#4b5563;'>You approved the booking request. Here are the customer's full contact details:</p>"
                + "<table style='width:100%;border-collapse:collapse;margin:20px 0;'>"
                + row("Customer", customerName)
                + row("Phone", phone)
                + row("Location", location)
                + row("Requested", dateTime)
                + row("Service", serviceName)
                + "</table>"
                + "<p style='color:#4b5563;'>Please reach out to confirm the appointment.</p>"
                + "<hr style='border:none;border-top:1px solid #bbf7d0;margin:20px 0;'>"
                + "<p style='color:#9ca3af;font-size:12px;text-align:center;'>— The ServicePoint Team</p>"
                + "</div></body></html>";
    }

    private String buildApprovalCustomerHtml(String customerName, String providerName,
                                             String serviceName, String dateTime, String price) {
        return "<!DOCTYPE html><html><body style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;'>"
                + "<div style='background:linear-gradient(135deg,#16a34a,#22c55e);padding:24px;text-align:center;border-radius:12px 12px 0 0;'>"
                + "<h1 style='color:white;margin:0;'>Your Booking Was Approved!</h1></div>"
                + "<div style='background:#fff;padding:28px;border:1px solid #bbf7d0;border-top:none;border-radius:0 0 12px 12px;'>"
                + "<p style='font-size:16px;color:#1f2937;'>Hi " + customerName + ",</p>"
                + "<p style='color:#4b5563;font-size:15px;'>Great news! <strong>" + providerName
                + "</strong> has approved your booking request.</p>"
                + "<table style='width:100%;border-collapse:collapse;margin:20px 0;'>"
                + row("Service", serviceName)
                + row("Date & Time", dateTime)
                + row("Price", price)
                + "</table>"
                + "<div style='background:#f0fdf4;border-left:3px solid #16a34a;padding:14px 18px;border-radius:6px;margin:20px 0;'>"
                + "<p style='color:#166534;margin:0;font-size:14px;'>"
                + "Your provider will be in touch soon. Make sure you're available at the scheduled time."
                + "</p></div>"
                + "<hr style='border:none;border-top:1px solid #bbf7d0;margin:20px 0;'>"
                + "<p style='color:#9ca3af;font-size:12px;text-align:center;'>— The ServicePoint Team</p>"
                + "</div></body></html>";
    }

    private String buildDeclineCustomerHtml(String customerName, String providerName,
                                            String serviceName, String dateTime) {
        return "<!DOCTYPE html><html><body style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;'>"
                + "<div style='background:#6b7280;padding:24px;text-align:center;border-radius:12px 12px 0 0;'>"
                + "<h1 style='color:white;margin:0;'>Update on Your Booking Request</h1></div>"
                + "<div style='background:#fff;padding:28px;border:1px solid #e5e7eb;border-top:none;border-radius:0 0 12px 12px;'>"
                + "<p style='font-size:16px;color:#1f2937;'>Hi " + customerName + ",</p>"
                + "<p style='color:#4b5563;font-size:15px;'>Unfortunately, <strong>" + providerName
                + "</strong> is unable to take your booking request at this time.</p>"
                + "<table style='width:100%;border-collapse:collapse;margin:20px 0;'>"
                + row("Service", serviceName)
                + row("Requested", dateTime)
                + "</table>"
                + "<div style='background:#f9fafb;border-left:3px solid #fb923c;padding:14px 18px;border-radius:6px;margin:20px 0;'>"
                + "<p style='color:#374151;margin:0;font-size:14px;'>"
                + "You can browse other providers for this service on ServicePoint."
                + "</p></div>"
                + "<hr style='border:none;border-top:1px solid #e5e7eb;margin:20px 0;'>"
                + "<p style='color:#9ca3af;font-size:12px;text-align:center;'>— The ServicePoint Team</p>"
                + "</div></body></html>";
    }

    private static String row(String label, String value) {
        return "<tr>"
                + "<td style='padding:8px 12px;color:#6b7280;font-size:14px;border-bottom:1px solid #f3f4f6;width:130px;'>"
                + label + "</td>"
                + "<td style='padding:8px 12px;color:#1f2937;font-size:14px;border-bottom:1px solid #f3f4f6;'>"
                + value + "</td>"
                + "</tr>";
    }
}