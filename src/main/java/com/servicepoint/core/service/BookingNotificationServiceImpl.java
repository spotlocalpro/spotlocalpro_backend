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
        this.frontendBaseUrl = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
                : frontendBaseUrl;
    }

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
    //  SIMPLIFIED EMAIL TEMPLATES - OPTIMIZED FOR GMAIL
    // =====================================================================

    private String buildNewRequestHtml(String providerName, String customerName, String distance,
                                       String serviceName, String category, String dateTime,
                                       String price, String notes,
                                       String approveUrl, String declineUrl) {
        return "<!DOCTYPE html><html><body style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;background:#f3f4f6;padding:20px;'>"
                + "<div style='background:#fb923c;padding:35px 20px;text-align:center;border-radius:12px 12px 0 0;'>"
                + "<div style='font-size:42px;margin-bottom:8px;'>🔔</div>"
                + "<h1 style='color:white;margin:0;font-size:26px;'>New Booking Request!</h1>"
                + "<p style='color:#fff7ed;margin:6px 0 0 0;font-size:15px;'>A customer wants to book your service</p>"
                + "</div>"

                + "<div style='background:white;padding:28px;border-radius:0 0 12px 12px;'>"
                + "<p style='font-size:17px;color:#1f2937;margin:0 0 6px 0;'>Hi <strong>" + providerName + "</strong>,</p>"
                + "<p style='color:#4b5563;font-size:15px;margin:0 0 20px 0;'>You have a new booking request on SpotLocalPro!</p>"

                + "<div style='background:#fffbeb;border:2px solid #fbbf24;border-radius:10px;padding:20px;margin:20px 0;'>"
                + "<h3 style='color:#92400e;margin:0 0 14px 0;font-size:15px;'>👤 CUSTOMER DETAILS</h3>"
                + "<table style='width:100%;'>"
                + simpleRow("Customer", customerName)
                + simpleRow("Distance", distance)
                + "</table></div>"

                + "<div style='background:#f0f9ff;border:2px solid #7dd3fc;border-radius:10px;padding:20px;margin:20px 0;'>"
                + "<h3 style='color:#075985;margin:0 0 14px 0;font-size:15px;'>📋 BOOKING DETAILS</h3>"
                + "<table style='width:100%;'>"
                + simpleRow("Service", serviceName + (category.isEmpty() ? "" : " (" + category + ")"))
                + simpleRow("Requested", dateTime)
                + simpleRow("Price", price)
                + simpleRow("Notes", notes)
                + "</table></div>"

                + "<div style='background:#fef2f2;border-left:3px solid #f87171;padding:14px 18px;border-radius:6px;margin:20px 0;'>"
                + "<p style='color:#991b1b;margin:0;font-size:13px;'>"
                + "🔒 <strong>Privacy:</strong> Approve to view customer's phone and location."
                + "</p></div>"

                + "<div style='text-align:center;margin:28px 0;padding:20px;background:#f9fafb;border-radius:10px;'>"
                + "<p style='color:#374151;font-size:15px;font-weight:600;margin:0 0 16px 0;'>Choose your action:</p>"

                + "<table width='100%' cellpadding='0' cellspacing='0' style='margin-bottom:10px;'>"
                + "<tr><td align='center'>"
                + "<a href='" + approveUrl + "' style='background:#16a34a;color:white;padding:14px 45px;text-decoration:none;border-radius:8px;font-weight:700;font-size:15px;display:inline-block;'>"
                + "✅ APPROVE</a>"
                + "</td></tr></table>"

                + "<table width='100%' cellpadding='0' cellspacing='0'>"
                + "<tr><td align='center'>"
                + "<a href='" + declineUrl + "' style='background:#dc2626;color:white;padding:14px 45px;text-decoration:none;border-radius:8px;font-weight:700;font-size:15px;display:inline-block;'>"
                + "❌ DECLINE</a>"
                + "</td></tr></table>"

                + "<p style='color:#9ca3af;font-size:12px;margin:16px 0 0 0;'>Links expire in 48 hours</p>"
                + "</div>"

                + "<div style='text-align:center;margin-top:32px;padding-top:20px;border-top:2px solid #e5e7eb;'>"
                + "<p style='color:#9ca3af;font-size:13px;margin:0 0 6px 0;'>Thank you for being a valued provider</p>"
                + "<p style='color:#6b7280;font-size:15px;font-weight:600;margin:0;'>The SpotLocalPro Team</p>"
                + "</div></div></body></html>";
    }

    private String buildApprovalFollowUpHtml(String providerName, String customerName,
                                             String phone, String location,
                                             String dateTime, String serviceName) {
        return "<!DOCTYPE html><html><body style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;background:#f3f4f6;padding:20px;'>"
                + "<div style='background:#16a34a;padding:35px 20px;text-align:center;border-radius:12px 12px 0 0;'>"
                + "<div style='font-size:42px;margin-bottom:8px;'>✅</div>"
                + "<h1 style='color:white;margin:0;font-size:26px;'>Booking Approved!</h1>"
                + "<p style='color:#f0fdf4;margin:6px 0 0 0;font-size:15px;'>Customer contact details</p>"
                + "</div>"

                + "<div style='background:white;padding:28px;border-radius:0 0 12px 12px;'>"
                + "<p style='font-size:17px;color:#1f2937;margin:0 0 6px 0;'>Hi <strong>" + providerName + "</strong>,</p>"
                + "<p style='color:#4b5563;font-size:15px;margin:0 0 20px 0;'>You approved the booking. Here are the details:</p>"

                + "<div style='background:#dcfce7;border:2px solid #86efac;border-radius:10px;padding:20px;margin:20px 0;'>"
                + "<h3 style='color:#15803d;margin:0 0 14px 0;font-size:15px;'>📞 CONTACT INFO</h3>"
                + "<table style='width:100%;'>"
                + simpleRow("Customer", customerName)
                + simpleRow("Phone", phone)
                + simpleRow("Location", location)
                + "</table></div>"

                + "<div style='background:#f0f9ff;border:2px solid #7dd3fc;border-radius:10px;padding:20px;margin:20px 0;'>"
                + "<h3 style='color:#075985;margin:0 0 14px 0;font-size:15px;'>📋 SERVICE DETAILS</h3>"
                + "<table style='width:100%;'>"
                + simpleRow("Service", serviceName)
                + simpleRow("Scheduled", dateTime)
                + "</table></div>"

                + "<div style='background:#fffbeb;border-left:3px solid #f59e0b;padding:14px 18px;border-radius:6px;margin:20px 0;'>"
                + "<p style='color:#92400e;margin:0 0 6px 0;font-size:14px;font-weight:700;'>⏭️ What's Next?</p>"
                + "<p style='color:#78350f;margin:0;font-size:13px;'>Contact the customer to confirm the appointment.</p>"
                + "</div>"

                + "<div style='text-align:center;margin-top:32px;padding-top:20px;border-top:2px solid #e5e7eb;'>"
                + "<p style='color:#9ca3af;font-size:13px;margin:0 0 6px 0;'>Good luck with your service!</p>"
                + "<p style='color:#6b7280;font-size:15px;font-weight:600;margin:0;'>The SpotLocalPro Team</p>"
                + "</div></div></body></html>";
    }

    private String buildApprovalCustomerHtml(String customerName, String providerName,
                                             String serviceName, String dateTime, String price) {
        return "<!DOCTYPE html><html><body style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;background:#f3f4f6;padding:20px;'>"
                + "<div style='background:#10b981;padding:35px 20px;text-align:center;border-radius:12px 12px 0 0;'>"
                + "<div style='font-size:42px;margin-bottom:8px;'>🎉</div>"
                + "<h1 style='color:white;margin:0;font-size:26px;'>Booking Confirmed!</h1>"
                + "<p style='color:#f0fdf4;margin:6px 0 0 0;font-size:15px;'>Your request has been approved</p>"
                + "</div>"

                + "<div style='background:white;padding:28px;border-radius:0 0 12px 12px;'>"
                + "<p style='font-size:17px;color:#1f2937;margin:0 0 6px 0;'>Hi <strong>" + customerName + "</strong>,</p>"
                + "<p style='color:#4b5563;font-size:15px;margin:0 0 20px 0;'>Great news! <strong style='color:#10b981;'>" + providerName
                + "</strong> has accepted your booking!</p>"

                + "<div style='background:#f0fdf4;border:2px solid #86efac;border-radius:10px;padding:20px;margin:20px 0;'>"
                + "<h3 style='color:#15803d;margin:0 0 14px 0;font-size:15px;'>📋 BOOKING DETAILS</h3>"
                + "<table style='width:100%;'>"
                + simpleRow("Service", serviceName)
                + simpleRow("Date & Time", dateTime)
                + simpleRow("Total Price", "<span style='color:#10b981;font-weight:700;font-size:17px;'>" + price + "</span>")
                + "</table></div>"

                + "<div style='background:#fffbeb;border-left:3px solid #f59e0b;padding:14px 18px;border-radius:6px;margin:20px 0;'>"
                + "<p style='color:#92400e;margin:0 0 6px 0;font-size:14px;font-weight:700;'>✨ What's Next?</p>"
                + "<p style='color:#78350f;margin:0;font-size:13px;'>Your provider will contact you shortly to confirm details.</p>"
                + "</div>"

                + "<div style='background:#fef3c7;border:2px solid #fbbf24;padding:18px;border-radius:10px;margin:20px 0;text-align:center;'>"
                + "<div style='font-size:28px;margin-bottom:6px;'>💬</div>"
                + "<p style='color:#92400e;margin:0 0 6px 0;font-size:14px;font-weight:700;'>Questions?</p>"
                + "<a href='mailto:hello@spotlocalpro.com' style='display:inline-block;margin-top:10px;background:#f59e0b;color:white;padding:9px 22px;text-decoration:none;border-radius:7px;font-weight:600;font-size:13px;'>Contact Support</a>"
                + "</div>"

                + "<div style='text-align:center;margin-top:32px;padding-top:20px;border-top:2px solid #e5e7eb;'>"
                + "<p style='color:#9ca3af;font-size:13px;margin:0 0 6px 0;'>Thank you for choosing SpotLocalPro</p>"
                + "<p style='color:#6b7280;font-size:15px;font-weight:600;margin:0;'>The SpotLocalPro Team</p>"
                + "</div></div></body></html>";
    }

    private String buildDeclineCustomerHtml(String customerName, String providerName,
                                            String serviceName, String dateTime) {
        return "<!DOCTYPE html><html><body style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;background:#f3f4f6;padding:20px;'>"
                + "<div style='background:#64748b;padding:35px 20px;text-align:center;border-radius:12px 12px 0 0;'>"
                + "<div style='font-size:42px;margin-bottom:8px;'>📬</div>"
                + "<h1 style='color:white;margin:0;font-size:26px;'>Booking Update</h1>"
                + "<p style='color:#e2e8f0;margin:6px 0 0 0;font-size:15px;'>About your request</p>"
                + "</div>"

                + "<div style='background:white;padding:28px;border-radius:0 0 12px 12px;'>"
                + "<p style='font-size:17px;color:#1f2937;margin:0 0 6px 0;'>Hi <strong>" + customerName + "</strong>,</p>"
                + "<p style='color:#4b5563;font-size:15px;margin:0 0 20px 0;'>Unfortunately, <strong>" + providerName
                + "</strong> is unable to accept your booking at this time.</p>"

                + "<div style='background:#f8fafc;border:2px solid #e2e8f0;border-radius:10px;padding:20px;margin:20px 0;'>"
                + "<h3 style='color:#475569;margin:0 0 14px 0;font-size:15px;'>📋 ORIGINAL REQUEST</h3>"
                + "<table style='width:100%;'>"
                + simpleRow("Service", serviceName)
                + simpleRow("Requested For", dateTime)
                + "</table></div>"

                + "<div style='background:#fef3c7;border-left:3px solid #f59e0b;padding:18px;border-radius:6px;margin:20px 0;'>"
                + "<p style='color:#92400e;margin:0 0 6px 0;font-size:14px;font-weight:700;'>🔍 Don't worry!</p>"
                + "<p style='color:#78350f;margin:0;font-size:13px;'>Many other providers are available on SpotLocalPro.</p>"
                + "</div>"

                + "<div style='background:#dbeafe;border:2px solid #60a5fa;padding:18px;border-radius:10px;margin:20px 0;text-align:center;'>"
                + "<div style='font-size:28px;margin-bottom:6px;'>🤝</div>"
                + "<p style='color:#1e40af;margin:0 0 6px 0;font-size:14px;font-weight:700;'>Need help?</p>"
                + "<a href='mailto:hello@spotlocalpro.com' style='display:inline-block;background:#3b82f6;color:white;padding:9px 22px;text-decoration:none;border-radius:7px;font-weight:600;font-size:13px;margin-top:8px;'>Get Support</a>"
                + "</div>"

                + "<div style='text-align:center;margin-top:32px;padding-top:20px;border-top:2px solid #e5e7eb;'>"
                + "<p style='color:#9ca3af;font-size:13px;margin:0 0 6px 0;'>We appreciate your understanding</p>"
                + "<p style='color:#6b7280;font-size:15px;font-weight:600;margin:0;'>The SpotLocalPro Team</p>"
                + "</div></div></body></html>";
    }

    // Simple row helper - more compact
    private static String simpleRow(String label, String value) {
        return "<tr><td style='padding:8px 0;color:#6b7280;font-size:14px;'>" + label + "</td>"
                + "<td style='padding:8px 0;color:#1f2937;font-size:14px;font-weight:600;text-align:right;'>" + value + "</td></tr>";
    }

    private static String row(String label, String value) {
        return "<tr><td style='padding:8px 0;color:#6b7280;font-size:14px;'>" + label + "</td>"
                + "<td style='padding:8px 0;color:#1f2937;font-size:14px;'>" + value + "</td></tr>";
    }
}