package com.servicepoint.core.service;

import com.servicepoint.core.model.Booking;
import com.servicepoint.core.model.User;
import jakarta.mail.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

    // -------------------------------------------------------------------------
    //  New booking request → PROVIDER
    // -------------------------------------------------------------------------
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

            boolean es = isEs(provider);
            String serviceName = booking.getService() != null ? booking.getService().getName() : "Service";
            String category    = booking.getService() != null ? booking.getService().getCategory() : "";
            String pricingType = booking.getService() != null ? booking.getService().getPricingType() : "";
            String priceText   = formatPrice(booking.getTotalPrice(), pricingType);
            String dateTimeText = formatDateTime(booking);

            // Extract photos from notes before escaping
            String rawNotes = booking.getNotes() == null ? "" : booking.getNotes();
            String[] photoInfo = extractPhotoInfo(rawNotes);
            String cleanRawNotes = photoInfo[0];

            // Load photo file bytes for email attachments
            List<byte[]> photoContents = new ArrayList<>();
            List<String> photoNames = new ArrayList<>();
            if (photoInfo.length > 1) {
                String[] filenames = java.util.Arrays.copyOfRange(photoInfo, 1, photoInfo.length);
                loadPhotoAttachments(filenames, photoContents, photoNames);
            }

            String notes = cleanRawNotes.isBlank()
                    ? (es ? "Ninguna" : "None")
                    : escape(cleanRawNotes).replace("\n", "<br>");
            String customerDisplay = escape(firstNameOrUsername(customer));
            String distanceText    = formatDistance(provider, customer);

            String approveToken = tokenService.generateToken(
                    booking.getBookingId(), BookingActionTokenService.ACTION_APPROVE);
            String declineToken = tokenService.generateToken(
                    booking.getBookingId(), BookingActionTokenService.ACTION_DECLINE);

            String approveUrl = frontendBaseUrl + "/api/bookings/" + booking.getBookingId()
                    + "/approve?token=" + approveToken;
            String declineUrl = frontendBaseUrl + "/api/bookings/" + booking.getBookingId()
                    + "/decline?token=" + declineToken;

            String subject = es
                    ? "Nueva Solicitud de Reserva — " + serviceName
                    : "New Booking Request — " + serviceName;

            // photosHtml will just show "X photos attached" note in the email body
            String photosHtml = photoContents.isEmpty() ? "" :
                    "<div style='margin-top:10px;padding:10px;background:#f0fdf4;border:1px solid #86efac;border-radius:6px;'>"
                    + "<p style='margin:0;font-size:13px;color:#15803d;font-weight:600;'>📎 "
                    + photoContents.size() + " photo" + (photoContents.size() > 1 ? "s" : "")
                    + " attached — see attachments below</p></div>";

            String body = es
                    ? buildNewRequestHtmlEs(escape(firstNameOrUsername(provider)), customerDisplay, distanceText,
                            escape(serviceName), escape(category), dateTimeText, priceText, notes, photosHtml, approveUrl, declineUrl)
                    : buildNewRequestHtmlEn(escape(firstNameOrUsername(provider)), customerDisplay, distanceText,
                            escape(serviceName), escape(category), dateTimeText, priceText, notes, photosHtml, approveUrl, declineUrl);

            emailService.sendEmail(provider.getEmail(), subject, body,
                    photoContents.isEmpty() ? null : photoContents,
                    photoNames.isEmpty() ? null : photoNames);
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

    // -------------------------------------------------------------------------
    //  Approval follow-up (customer contact info) → PROVIDER
    // -------------------------------------------------------------------------
    @Override
    @Async
    public void sendApprovalFollowUpToProvider(Booking booking) {
        try {
            User provider = booking.getProvider();
            User customer = booking.getCustomer();
            if (provider == null || provider.getEmail() == null) return;

            boolean es = isEs(provider);
            String serviceName  = booking.getService() != null ? booking.getService().getName() : "Service";
            String dateTimeText = formatDateTime(booking);
            String phone     = (customer != null && customer.getPhoneNumber() != null)
                    ? escape(customer.getPhoneNumber()) : (es ? "No proporcionado" : "Not provided");
            String location  = (customer != null && customer.getLocation() != null)
                    ? escape(customer.getLocation()) : (es ? "No proporcionado" : "Not provided");

            String subject = es
                    ? "Reserva Aprobada — Datos de Contacto del Cliente"
                    : "Booking Approved — Customer Contact Details";

            String body = es
                    ? buildApprovalFollowUpHtmlEs(escape(firstNameOrUsername(provider)),
                            escape(firstNameOrUsername(customer)), phone, location, dateTimeText, escape(serviceName))
                    : buildApprovalFollowUpHtmlEn(escape(firstNameOrUsername(provider)),
                            escape(firstNameOrUsername(customer)), phone, location, dateTimeText, escape(serviceName));

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

    // -------------------------------------------------------------------------
    //  Booking confirmed → CUSTOMER
    // -------------------------------------------------------------------------
    @Override
    @Async
    public void sendApprovalNotificationToCustomer(Booking booking) {
        try {
            User customer = booking.getCustomer();
            User provider = booking.getProvider();
            if (customer == null || customer.getEmail() == null) return;

            boolean es = isEs(customer);
            String serviceName  = booking.getService() != null ? booking.getService().getName() : "Service";
            String providerName = provider != null ? firstNameOrUsername(provider) : (es ? "Tu proveedor" : "Your provider");
            String dateTimeText = formatDateTime(booking);
            String priceText    = booking.getTotalPrice() != null
                    ? String.format(Locale.US, "$%.2f", booking.getTotalPrice()) : "TBD";
            String bookingLink  = frontendBaseUrl + "/bookings/" + booking.getBookingId();

            String subject = es
                    ? "¡Tu Solicitud de Reserva fue Aprobada! - SpotLocalPro"
                    : "Your Booking Request Was Approved! - SpotLocalPro";

            String body = es
                    ? buildApprovalCustomerHtmlEs(escape(firstNameOrUsername(customer)),
                            escape(providerName), escape(serviceName), dateTimeText, priceText, bookingLink)
                    : buildApprovalCustomerHtmlEn(escape(firstNameOrUsername(customer)),
                            escape(providerName), escape(serviceName), dateTimeText, priceText, bookingLink);

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

    // -------------------------------------------------------------------------
    //  Booking declined → CUSTOMER
    // -------------------------------------------------------------------------
    @Override
    @Async
    public void sendDeclineNotificationToCustomer(Booking booking) {
        try {
            User customer = booking.getCustomer();
            User provider = booking.getProvider();
            if (customer == null || customer.getEmail() == null) return;

            boolean es = isEs(customer);
            String serviceName  = booking.getService() != null ? booking.getService().getName() : "Service";
            String providerName = provider != null ? firstNameOrUsername(provider) : (es ? "El proveedor" : "The provider");
            String dateTimeText = formatDateTime(booking);
            String searchLink   = frontendBaseUrl + "/search";

            String subject = es
                    ? "Actualización sobre tu Solicitud de Reserva - SpotLocalPro"
                    : "Update on Your Booking Request - SpotLocalPro";

            String body = es
                    ? buildDeclineCustomerHtmlEs(escape(firstNameOrUsername(customer)),
                            escape(providerName), escape(serviceName), dateTimeText, searchLink)
                    : buildDeclineCustomerHtmlEn(escape(firstNameOrUsername(customer)),
                            escape(providerName), escape(serviceName), dateTimeText, searchLink);

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

    // -------------------------------------------------------------------------
    //  Booking completed → CUSTOMER (review request)
    // -------------------------------------------------------------------------
    @Override
    @Async
    public void sendBookingCompletedToCustomer(Booking booking) {
        try {
            User customer = booking.getCustomer();
            User provider = booking.getProvider();
            if (customer == null || customer.getEmail() == null) return;

            boolean es = isEs(customer);
            String serviceName  = booking.getService() != null ? booking.getService().getName() : "Service";
            String providerName = provider != null ? firstNameOrUsername(provider) : (es ? "Tu proveedor" : "Your provider");
            String priceText    = booking.getTotalPrice() != null
                    ? String.format(Locale.US, "$%.2f", booking.getTotalPrice()) : "TBD";
            String feedbackLink = frontendBaseUrl + "/bookings/" + booking.getBookingId() + "/review";

            String subject = es
                    ? "Servicio Completado — ¡Deja tu Reseña! - SpotLocalPro"
                    : "Service Completed — Leave a Review! - SpotLocalPro";

            String body = es
                    ? buildCompletedCustomerHtmlEs(escape(firstNameOrUsername(customer)),
                            escape(serviceName), escape(providerName), priceText, feedbackLink)
                    : buildCompletedCustomerHtmlEn(escape(firstNameOrUsername(customer)),
                            escape(serviceName), escape(providerName), priceText, feedbackLink);

            emailService.sendEmail(customer.getEmail(), subject, body);
            log.info("Sent booking-completed email for booking {} to customer {}",
                    booking.getBookingId(), customer.getEmail());
        } catch (MessagingException e) {
            log.error("Failed to send booking-completed email to customer for booking {}: {}",
                    booking.getBookingId(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error sending booking-completed email to customer for booking {}",
                    booking.getBookingId(), e);
        }
    }

    // -------------------------------------------------------------------------
    //  Booking completed → PROVIDER (payment confirmation)
    // -------------------------------------------------------------------------
    @Override
    @Async
    public void sendBookingCompletedToProvider(Booking booking) {
        try {
            User provider = booking.getProvider();
            User customer = booking.getCustomer();
            if (provider == null || provider.getEmail() == null) return;

            boolean es = isEs(provider);
            String serviceName   = booking.getService() != null ? booking.getService().getName() : "Service";
            String customerName  = customer != null ? firstNameOrUsername(customer) : (es ? "el cliente" : "the customer");
            String priceText     = booking.getTotalPrice() != null
                    ? String.format(Locale.US, "$%.2f", booking.getTotalPrice()) : "TBD";
            String dashboardLink = frontendBaseUrl + "/dashboard";

            String subject = es
                    ? "Reserva Completada - SpotLocalPro"
                    : "Booking Completed - SpotLocalPro";

            String body = es
                    ? buildCompletedProviderHtmlEs(escape(firstNameOrUsername(provider)),
                            booking.getBookingId(), escape(serviceName), escape(customerName), priceText, dashboardLink)
                    : buildCompletedProviderHtmlEn(escape(firstNameOrUsername(provider)),
                            booking.getBookingId(), escape(serviceName), escape(customerName), priceText, dashboardLink);

            emailService.sendEmail(provider.getEmail(), subject, body);
            log.info("Sent booking-completed email for booking {} to provider {}",
                    booking.getBookingId(), provider.getEmail());
        } catch (MessagingException e) {
            log.error("Failed to send booking-completed email to provider for booking {}: {}",
                    booking.getBookingId(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error sending booking-completed email to provider for booking {}",
                    booking.getBookingId(), e);
        }
    }

    // =========================================================================
    //  Helpers
    // =========================================================================

    private static boolean isEs(User u) {
        return u != null && "es".equalsIgnoreCase(u.getPreferredLanguage());
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
        if ("quote".equalsIgnoreCase(pricingType)) return "Free Estimate";
        if (total == null || total == 0.0) return "Price TBD";
        if ("hourly".equalsIgnoreCase(pricingType)) {
            return String.format(Locale.US, "$%.2f/hr", total);
        }
        if ("starting_from".equalsIgnoreCase(pricingType)) {
            return String.format(Locale.US, "from $%.2f", total);
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

    private static String simpleRow(String label, String value) {
        return "<tr><td style='padding:8px 0;color:#6b7280;font-size:14px;'>" + label + "</td>"
                + "<td style='padding:8px 0;color:#1f2937;font-size:14px;font-weight:600;text-align:right;'>" + value + "</td></tr>";
    }

    private static final Path PHOTO_UPLOAD_DIR =
            Paths.get("uploads/estimate-photos").toAbsolutePath().normalize();

    // Strips "##PHOTOS##url1,url2##END_PHOTOS##" from notes, returns [cleanNotes, photoFilenames...]
    private static String[] extractPhotoInfo(String rawNotes) {
        if (rawNotes == null) return new String[]{""};
        String marker = "##PHOTOS##";
        String endMarker = "##END_PHOTOS##";
        int start = rawNotes.indexOf(marker);
        if (start == -1) return new String[]{rawNotes};
        int urlsStart = start + marker.length();
        int end = rawNotes.indexOf(endMarker, urlsStart);
        if (end == -1) return new String[]{rawNotes};
        String cleanNotes = rawNotes.substring(0, start).trim();
        String urlsPart = rawNotes.substring(urlsStart, end).trim();
        if (urlsPart.isEmpty()) return new String[]{cleanNotes};
        String[] urls = urlsPart.split(",");
        String[] result = new String[1 + urls.length];
        result[0] = cleanNotes;
        for (int i = 0; i < urls.length; i++) {
            String url = urls[i].trim();
            int slash = url.lastIndexOf('/');
            result[i + 1] = slash >= 0 ? url.substring(slash + 1) : url;
        }
        return result;
    }

    // Loads photo bytes from disk given filenames extracted from notes
    private static void loadPhotoAttachments(String[] photoFilenames,
                                             List<byte[]> contents, List<String> names) {
        for (String filename : photoFilenames) {
            if (filename == null || filename.isBlank()) continue;
            try {
                Path file = PHOTO_UPLOAD_DIR.resolve(filename).normalize();
                if (Files.exists(file)) {
                    contents.add(Files.readAllBytes(file));
                    names.add(filename);
                }
            } catch (IOException e) {
                log.warn("Could not read estimate photo {}: {}", filename, e.getMessage());
            }
        }
    }

    // Extracts photos, returns [cleanNotes, photosHtml] — kept for email template use
    private static String[] extractPhotos(String notes) {
        String[] info = extractPhotoInfo(notes);
        String cleanNotes = info[0];
        String photosHtml = "";
        if (info.length > 1) {
            photosHtml = "<div style='margin-top:10px;padding:10px;background:#f0fdf4;border-radius:6px;'>"
                    + "<p style='margin:0;font-size:13px;color:#15803d;font-weight:600;'>📎 "
                    + (info.length - 1) + " photo" + (info.length > 2 ? "s" : "") + " attached — see attachments in this email</p>"
                    + "</div>";
        }
        return new String[]{cleanNotes, photosHtml};
    }

    // =========================================================================
    //  New booking request templates
    // =========================================================================

    private String buildNewRequestHtmlEn(String providerName, String customerName, String distance,
                                         String serviceName, String category, String dateTime,
                                         String price, String notes, String photosHtml,
                                         String approveUrl, String declineUrl) {
        String displayNotes = (notes == null || notes.isBlank()) ? "None" : notes;
        return "<!DOCTYPE html><html><body style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;background:#f3f4f6;padding:20px;'>"
                + "<div style='background:#fb923c;padding:35px 20px;text-align:center;border-radius:12px 12px 0 0;'>"
                + "<div style='font-size:42px;margin-bottom:8px;'>🔔</div>"
                + "<h1 style='color:white;margin:0;font-size:26px;'>New Booking Request!</h1>"
                + "<p style='color:#fff7ed;margin:6px 0 0 0;font-size:15px;'>A customer wants to book your service</p>"
                + "</div>"
                + "<div style='background:white;padding:28px;border-radius:0 0 12px 12px;'>"
                + "<p style='font-size:17px;color:#1f2937;margin:0 0 6px 0;'>Hi <strong>" + providerName + "</strong>,</p>"
                + "<p style='color:#4b5563;font-size:15px;margin:0 0 20px 0;'>You have a new booking request on SpotLocalPro. Please approve or decline below.</p>"
                + "<div style='text-align:center;margin:0 0 24px 0;padding:20px;background:#f0fdf4;border:2px solid #86efac;border-radius:10px;'>"
                + "<p style='color:#15803d;font-size:15px;font-weight:700;margin:0 0 16px 0;'>Action Required — Choose your response:</p>"
                + "<table width='100%' cellpadding='0' cellspacing='0' style='margin-bottom:10px;'><tr><td align='center'>"
                + "<a href='" + approveUrl + "' style='background:#16a34a;color:white;padding:14px 45px;text-decoration:none;border-radius:8px;font-weight:700;font-size:15px;display:inline-block;'>✅ APPROVE</a>"
                + "</td></tr></table>"
                + "<table width='100%' cellpadding='0' cellspacing='0'><tr><td align='center'>"
                + "<a href='" + declineUrl + "' style='background:#dc2626;color:white;padding:14px 45px;text-decoration:none;border-radius:8px;font-weight:700;font-size:15px;display:inline-block;'>❌ DECLINE</a>"
                + "</td></tr></table>"
                + "<p style='color:#6b7280;font-size:12px;margin:14px 0 0 0;'>Links expire in 48 hours</p>"
                + "</div>"
                + "<div style='background:#fffbeb;border:2px solid #fbbf24;border-radius:10px;padding:20px;margin:0 0 20px 0;'>"
                + "<h3 style='color:#92400e;margin:0 0 14px 0;font-size:15px;'>👤 CUSTOMER DETAILS</h3>"
                + "<table style='width:100%;'>" + simpleRow("Customer", customerName) + simpleRow("Distance", distance) + "</table></div>"
                + "<div style='background:#f0f9ff;border:2px solid #7dd3fc;border-radius:10px;padding:20px;margin:0 0 20px 0;'>"
                + "<h3 style='color:#075985;margin:0 0 14px 0;font-size:15px;'>📋 BOOKING DETAILS</h3>"
                + "<table style='width:100%;'>"
                + simpleRow("Service", serviceName + (category.isEmpty() ? "" : " (" + category + ")"))
                + simpleRow("Requested", dateTime) + simpleRow("Price", price) + simpleRow("Notes", displayNotes)
                + "</table>" + photosHtml + "</div>"
                + "<div style='background:#fef2f2;border-left:3px solid #f87171;padding:14px 18px;border-radius:6px;margin:0 0 20px 0;'>"
                + "<p style='color:#991b1b;margin:0;font-size:13px;'>🔒 <strong>Privacy:</strong> Approve to view customer's phone and location.</p></div>"
                + "<div style='text-align:center;padding-top:20px;border-top:2px solid #e5e7eb;'>"
                + "<p style='color:#6b7280;font-size:15px;font-weight:600;margin:0;'>The SpotLocalPro Team</p>"
                + "</div></div></body></html>";
    }

    private String buildNewRequestHtmlEs(String providerName, String customerName, String distance,
                                         String serviceName, String category, String dateTime,
                                         String price, String notes, String photosHtml,
                                         String approveUrl, String declineUrl) {
        String displayNotes = (notes == null || notes.isBlank()) ? "Ninguna" : notes;
        return "<!DOCTYPE html><html><body style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;background:#f3f4f6;padding:20px;'>"
                + "<div style='background:#fb923c;padding:35px 20px;text-align:center;border-radius:12px 12px 0 0;'>"
                + "<div style='font-size:42px;margin-bottom:8px;'>🔔</div>"
                + "<h1 style='color:white;margin:0;font-size:26px;'>¡Nueva Solicitud de Reserva!</h1>"
                + "<p style='color:#fff7ed;margin:6px 0 0 0;font-size:15px;'>Un cliente quiere reservar tu servicio</p>"
                + "</div>"
                + "<div style='background:white;padding:28px;border-radius:0 0 12px 12px;'>"
                + "<p style='font-size:17px;color:#1f2937;margin:0 0 6px 0;'>Hola <strong>" + providerName + "</strong>,</p>"
                + "<p style='color:#4b5563;font-size:15px;margin:0 0 20px 0;'>Tienes una nueva solicitud de reserva en SpotLocalPro. Por favor aprueba o rechaza a continuación.</p>"
                + "<div style='text-align:center;margin:0 0 24px 0;padding:20px;background:#f0fdf4;border:2px solid #86efac;border-radius:10px;'>"
                + "<p style='color:#15803d;font-size:15px;font-weight:700;margin:0 0 16px 0;'>Acción Requerida — Elige tu respuesta:</p>"
                + "<table width='100%' cellpadding='0' cellspacing='0' style='margin-bottom:10px;'><tr><td align='center'>"
                + "<a href='" + approveUrl + "' style='background:#16a34a;color:white;padding:14px 45px;text-decoration:none;border-radius:8px;font-weight:700;font-size:15px;display:inline-block;'>✅ APROBAR</a>"
                + "</td></tr></table>"
                + "<table width='100%' cellpadding='0' cellspacing='0'><tr><td align='center'>"
                + "<a href='" + declineUrl + "' style='background:#dc2626;color:white;padding:14px 45px;text-decoration:none;border-radius:8px;font-weight:700;font-size:15px;display:inline-block;'>❌ RECHAZAR</a>"
                + "</td></tr></table>"
                + "<p style='color:#6b7280;font-size:12px;margin:14px 0 0 0;'>Los enlaces expiran en 48 horas</p>"
                + "</div>"
                + "<div style='background:#fffbeb;border:2px solid #fbbf24;border-radius:10px;padding:20px;margin:0 0 20px 0;'>"
                + "<h3 style='color:#92400e;margin:0 0 14px 0;font-size:15px;'>👤 DATOS DEL CLIENTE</h3>"
                + "<table style='width:100%;'>" + simpleRow("Cliente", customerName) + simpleRow("Distancia", distance) + "</table></div>"
                + "<div style='background:#f0f9ff;border:2px solid #7dd3fc;border-radius:10px;padding:20px;margin:0 0 20px 0;'>"
                + "<h3 style='color:#075985;margin:0 0 14px 0;font-size:15px;'>📋 DETALLES DE LA RESERVA</h3>"
                + "<table style='width:100%;'>"
                + simpleRow("Servicio", serviceName + (category.isEmpty() ? "" : " (" + category + ")"))
                + simpleRow("Solicitado", dateTime) + simpleRow("Precio", price) + simpleRow("Notas", displayNotes)
                + "</table>" + photosHtml + "</div>"
                + "<div style='background:#fef2f2;border-left:3px solid #f87171;padding:14px 18px;border-radius:6px;margin:0 0 20px 0;'>"
                + "<p style='color:#991b1b;margin:0;font-size:13px;'>🔒 <strong>Privacidad:</strong> Aprueba para ver el teléfono y ubicación del cliente.</p></div>"
                + "<div style='text-align:center;padding-top:20px;border-top:2px solid #e5e7eb;'>"
                + "<p style='color:#6b7280;font-size:15px;font-weight:600;margin:0;'>El equipo de SpotLocalPro</p>"
                + "</div></div></body></html>";
    }

    // =========================================================================
    //  Approval follow-up templates (provider gets customer contact info)
    // =========================================================================

    private String buildApprovalFollowUpHtmlEn(String providerName, String customerName,
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
                + "<table style='width:100%;'>" + simpleRow("Customer", customerName) + simpleRow("Phone", phone) + simpleRow("Location", location) + "</table></div>"
                + "<div style='background:#f0f9ff;border:2px solid #7dd3fc;border-radius:10px;padding:20px;margin:20px 0;'>"
                + "<h3 style='color:#075985;margin:0 0 14px 0;font-size:15px;'>📋 SERVICE DETAILS</h3>"
                + "<table style='width:100%;'>" + simpleRow("Service", serviceName) + simpleRow("Scheduled", dateTime) + "</table></div>"
                + "<div style='background:#fffbeb;border-left:3px solid #f59e0b;padding:14px 18px;border-radius:6px;margin:20px 0;'>"
                + "<p style='color:#92400e;margin:0 0 6px 0;font-size:14px;font-weight:700;'>⏭️ What's Next?</p>"
                + "<p style='color:#78350f;margin:0;font-size:13px;'>Contact the customer to confirm the appointment.</p></div>"
                + "<div style='text-align:center;margin-top:32px;padding-top:20px;border-top:2px solid #e5e7eb;'>"
                + "<p style='color:#6b7280;font-size:15px;font-weight:600;margin:0;'>The SpotLocalPro Team</p>"
                + "</div></div></body></html>";
    }

    private String buildApprovalFollowUpHtmlEs(String providerName, String customerName,
                                               String phone, String location,
                                               String dateTime, String serviceName) {
        return "<!DOCTYPE html><html><body style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;background:#f3f4f6;padding:20px;'>"
                + "<div style='background:#16a34a;padding:35px 20px;text-align:center;border-radius:12px 12px 0 0;'>"
                + "<div style='font-size:42px;margin-bottom:8px;'>✅</div>"
                + "<h1 style='color:white;margin:0;font-size:26px;'>¡Reserva Aprobada!</h1>"
                + "<p style='color:#f0fdf4;margin:6px 0 0 0;font-size:15px;'>Datos de contacto del cliente</p>"
                + "</div>"
                + "<div style='background:white;padding:28px;border-radius:0 0 12px 12px;'>"
                + "<p style='font-size:17px;color:#1f2937;margin:0 0 6px 0;'>Hola <strong>" + providerName + "</strong>,</p>"
                + "<p style='color:#4b5563;font-size:15px;margin:0 0 20px 0;'>Aprobaste la reserva. Aquí están los detalles:</p>"
                + "<div style='background:#dcfce7;border:2px solid #86efac;border-radius:10px;padding:20px;margin:20px 0;'>"
                + "<h3 style='color:#15803d;margin:0 0 14px 0;font-size:15px;'>📞 DATOS DE CONTACTO</h3>"
                + "<table style='width:100%;'>" + simpleRow("Cliente", customerName) + simpleRow("Teléfono", phone) + simpleRow("Ubicación", location) + "</table></div>"
                + "<div style='background:#f0f9ff;border:2px solid #7dd3fc;border-radius:10px;padding:20px;margin:20px 0;'>"
                + "<h3 style='color:#075985;margin:0 0 14px 0;font-size:15px;'>📋 DETALLES DEL SERVICIO</h3>"
                + "<table style='width:100%;'>" + simpleRow("Servicio", serviceName) + simpleRow("Programado", dateTime) + "</table></div>"
                + "<div style='background:#fffbeb;border-left:3px solid #f59e0b;padding:14px 18px;border-radius:6px;margin:20px 0;'>"
                + "<p style='color:#92400e;margin:0 0 6px 0;font-size:14px;font-weight:700;'>⏭️ ¿Qué sigue?</p>"
                + "<p style='color:#78350f;margin:0;font-size:13px;'>Contacta al cliente para confirmar la cita.</p></div>"
                + "<div style='text-align:center;margin-top:32px;padding-top:20px;border-top:2px solid #e5e7eb;'>"
                + "<p style='color:#6b7280;font-size:15px;font-weight:600;margin:0;'>El equipo de SpotLocalPro</p>"
                + "</div></div></body></html>";
    }

    // =========================================================================
    //  Booking confirmed templates (customer)
    // =========================================================================

    private String buildApprovalCustomerHtmlEn(String customerName, String providerName,
                                               String serviceName, String dateTime,
                                               String price, String bookingLink) {
        return "<!DOCTYPE html><html><body style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;background:#f3f4f6;padding:20px;'>"
                + "<div style='background:#10b981;padding:35px 20px;text-align:center;border-radius:12px 12px 0 0;'>"
                + "<div style='font-size:42px;margin-bottom:8px;'>🎉</div>"
                + "<h1 style='color:white;margin:0;font-size:26px;'>Booking Confirmed!</h1>"
                + "<p style='color:#f0fdf4;margin:6px 0 0 0;font-size:15px;'>Your request has been approved</p>"
                + "</div>"
                + "<div style='background:white;padding:28px;border-radius:0 0 12px 12px;'>"
                + "<p style='font-size:17px;color:#1f2937;margin:0 0 6px 0;'>Hi <strong>" + customerName + "</strong>,</p>"
                + "<p style='color:#4b5563;font-size:15px;margin:0 0 20px 0;'>Great news! <strong style='color:#10b981;'>" + providerName + "</strong> has accepted your booking!</p>"
                + "<div style='background:#f0fdf4;border:2px solid #86efac;border-radius:10px;padding:20px;margin:20px 0;'>"
                + "<h3 style='color:#15803d;margin:0 0 14px 0;font-size:15px;'>📋 BOOKING DETAILS</h3>"
                + "<table style='width:100%;'>"
                + simpleRow("Service", serviceName) + simpleRow("Provider", providerName)
                + simpleRow("Date &amp; Time", dateTime)
                + simpleRow("Total", "<span style='color:#10b981;font-weight:700;font-size:17px;'>" + price + "</span>")
                + "</table></div>"
                + "<div style='text-align:center;margin:20px 0;'>"
                + "<a href='" + bookingLink + "' style='background:#10b981;color:white;padding:12px 32px;text-decoration:none;border-radius:8px;font-weight:600;display:inline-block;'>View Booking</a></div>"
                + "<div style='text-align:center;margin-top:32px;padding-top:20px;border-top:2px solid #e5e7eb;'>"
                + "<p style='color:#6b7280;font-size:15px;font-weight:600;margin:0;'>The SpotLocalPro Team</p>"
                + "</div></div></body></html>";
    }

    private String buildApprovalCustomerHtmlEs(String customerName, String providerName,
                                               String serviceName, String dateTime,
                                               String price, String bookingLink) {
        return "<!DOCTYPE html><html><body style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;background:#f3f4f6;padding:20px;'>"
                + "<div style='background:#10b981;padding:35px 20px;text-align:center;border-radius:12px 12px 0 0;'>"
                + "<div style='font-size:42px;margin-bottom:8px;'>🎉</div>"
                + "<h1 style='color:white;margin:0;font-size:26px;'>¡Reserva Confirmada!</h1>"
                + "<p style='color:#f0fdf4;margin:6px 0 0 0;font-size:15px;'>Tu solicitud fue aprobada</p>"
                + "</div>"
                + "<div style='background:white;padding:28px;border-radius:0 0 12px 12px;'>"
                + "<p style='font-size:17px;color:#1f2937;margin:0 0 6px 0;'>Hola <strong>" + customerName + "</strong>,</p>"
                + "<p style='color:#4b5563;font-size:15px;margin:0 0 20px 0;'>¡Buenas noticias! <strong style='color:#10b981;'>" + providerName + "</strong> ha aceptado tu reserva.</p>"
                + "<div style='background:#f0fdf4;border:2px solid #86efac;border-radius:10px;padding:20px;margin:20px 0;'>"
                + "<h3 style='color:#15803d;margin:0 0 14px 0;font-size:15px;'>📋 DETALLES DE LA RESERVA</h3>"
                + "<table style='width:100%;'>"
                + simpleRow("Servicio", serviceName) + simpleRow("Proveedor", providerName)
                + simpleRow("Fecha y Hora", dateTime)
                + simpleRow("Total", "<span style='color:#10b981;font-weight:700;font-size:17px;'>" + price + "</span>")
                + "</table></div>"
                + "<div style='text-align:center;margin:20px 0;'>"
                + "<a href='" + bookingLink + "' style='background:#10b981;color:white;padding:12px 32px;text-decoration:none;border-radius:8px;font-weight:600;display:inline-block;'>Ver Reserva</a></div>"
                + "<div style='text-align:center;margin-top:32px;padding-top:20px;border-top:2px solid #e5e7eb;'>"
                + "<p style='color:#6b7280;font-size:15px;font-weight:600;margin:0;'>El equipo de SpotLocalPro</p>"
                + "</div></div></body></html>";
    }

    // =========================================================================
    //  Booking declined templates (customer)
    // =========================================================================

    private String buildDeclineCustomerHtmlEn(String customerName, String providerName,
                                              String serviceName, String dateTime, String searchLink) {
        return "<!DOCTYPE html><html><body style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;background:#f3f4f6;padding:20px;'>"
                + "<div style='background:#64748b;padding:35px 20px;text-align:center;border-radius:12px 12px 0 0;'>"
                + "<div style='font-size:42px;margin-bottom:8px;'>📬</div>"
                + "<h1 style='color:white;margin:0;font-size:26px;'>Booking Update</h1>"
                + "<p style='color:#e2e8f0;margin:6px 0 0 0;font-size:15px;'>About your request</p>"
                + "</div>"
                + "<div style='background:white;padding:28px;border-radius:0 0 12px 12px;'>"
                + "<p style='font-size:17px;color:#1f2937;margin:0 0 6px 0;'>Hi <strong>" + customerName + "</strong>,</p>"
                + "<p style='color:#4b5563;font-size:15px;margin:0 0 20px 0;'>Unfortunately, <strong>" + providerName + "</strong> is unable to accept your booking at this time.</p>"
                + "<div style='background:#f8fafc;border:2px solid #e2e8f0;border-radius:10px;padding:20px;margin:20px 0;'>"
                + "<h3 style='color:#475569;margin:0 0 14px 0;font-size:15px;'>📋 ORIGINAL REQUEST</h3>"
                + "<table style='width:100%;'>" + simpleRow("Service", serviceName) + simpleRow("Requested For", dateTime) + "</table></div>"
                + "<div style='background:#fef3c7;border-left:3px solid #f59e0b;padding:14px 18px;border-radius:6px;margin:20px 0;'>"
                + "<p style='color:#92400e;margin:0 0 6px 0;font-size:14px;font-weight:700;'>🔍 Don't worry!</p>"
                + "<p style='color:#78350f;margin:0;font-size:13px;'>Many other providers are available on SpotLocalPro.</p></div>"
                + "<div style='text-align:center;margin:20px 0;'>"
                + "<a href='" + searchLink + "' style='background:#3b82f6;color:white;padding:12px 32px;text-decoration:none;border-radius:8px;font-weight:600;display:inline-block;'>Find Another Provider</a></div>"
                + "<div style='text-align:center;margin-top:32px;padding-top:20px;border-top:2px solid #e5e7eb;'>"
                + "<p style='color:#6b7280;font-size:15px;font-weight:600;margin:0;'>The SpotLocalPro Team</p>"
                + "</div></div></body></html>";
    }

    private String buildDeclineCustomerHtmlEs(String customerName, String providerName,
                                              String serviceName, String dateTime, String searchLink) {
        return "<!DOCTYPE html><html><body style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;background:#f3f4f6;padding:20px;'>"
                + "<div style='background:#64748b;padding:35px 20px;text-align:center;border-radius:12px 12px 0 0;'>"
                + "<div style='font-size:42px;margin-bottom:8px;'>📬</div>"
                + "<h1 style='color:white;margin:0;font-size:26px;'>Actualización de Reserva</h1>"
                + "<p style='color:#e2e8f0;margin:6px 0 0 0;font-size:15px;'>Sobre tu solicitud</p>"
                + "</div>"
                + "<div style='background:white;padding:28px;border-radius:0 0 12px 12px;'>"
                + "<p style='font-size:17px;color:#1f2937;margin:0 0 6px 0;'>Hola <strong>" + customerName + "</strong>,</p>"
                + "<p style='color:#4b5563;font-size:15px;margin:0 0 20px 0;'>Lamentablemente, <strong>" + providerName + "</strong> no puede aceptar tu reserva en este momento.</p>"
                + "<div style='background:#f8fafc;border:2px solid #e2e8f0;border-radius:10px;padding:20px;margin:20px 0;'>"
                + "<h3 style='color:#475569;margin:0 0 14px 0;font-size:15px;'>📋 SOLICITUD ORIGINAL</h3>"
                + "<table style='width:100%;'>" + simpleRow("Servicio", serviceName) + simpleRow("Solicitado Para", dateTime) + "</table></div>"
                + "<div style='background:#fef3c7;border-left:3px solid #f59e0b;padding:14px 18px;border-radius:6px;margin:20px 0;'>"
                + "<p style='color:#92400e;margin:0 0 6px 0;font-size:14px;font-weight:700;'>🔍 ¡No te preocupes!</p>"
                + "<p style='color:#78350f;margin:0;font-size:13px;'>Hay muchos otros proveedores disponibles en SpotLocalPro.</p></div>"
                + "<div style='text-align:center;margin:20px 0;'>"
                + "<a href='" + searchLink + "' style='background:#3b82f6;color:white;padding:12px 32px;text-decoration:none;border-radius:8px;font-weight:600;display:inline-block;'>Buscar Otro Proveedor</a></div>"
                + "<div style='text-align:center;margin-top:32px;padding-top:20px;border-top:2px solid #e5e7eb;'>"
                + "<p style='color:#6b7280;font-size:15px;font-weight:600;margin:0;'>El equipo de SpotLocalPro</p>"
                + "</div></div></body></html>";
    }

    // =========================================================================
    //  Booking completed templates (customer — review request)
    // =========================================================================

    private String buildCompletedCustomerHtmlEn(String customerName, String serviceName,
                                                String providerName, String price, String feedbackLink) {
        return "<!DOCTYPE html><html><body style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;background:#f3f4f6;padding:20px;'>"
                + "<div style='background:#7c3aed;padding:35px 20px;text-align:center;border-radius:12px 12px 0 0;'>"
                + "<div style='font-size:42px;margin-bottom:8px;'>⭐</div>"
                + "<h1 style='color:white;margin:0;font-size:26px;'>Service Completed!</h1>"
                + "<p style='color:#ede9fe;margin:6px 0 0 0;font-size:15px;'>How was your experience?</p>"
                + "</div>"
                + "<div style='background:white;padding:28px;border-radius:0 0 12px 12px;'>"
                + "<p style='font-size:17px;color:#1f2937;margin:0 0 6px 0;'>Hi <strong>" + customerName + "</strong>,</p>"
                + "<p style='color:#4b5563;font-size:15px;margin:0 0 20px 0;'>Your service has been completed!</p>"
                + "<div style='background:#f5f3ff;border:2px solid #c4b5fd;border-radius:10px;padding:20px;margin:20px 0;'>"
                + "<h3 style='color:#6d28d9;margin:0 0 14px 0;font-size:15px;'>📋 SERVICE SUMMARY</h3>"
                + "<table style='width:100%;'>"
                + simpleRow("Service", serviceName) + simpleRow("Provider", providerName)
                + simpleRow("Total", "<span style='color:#7c3aed;font-weight:700;font-size:17px;'>" + price + "</span>")
                + "</table></div>"
                + "<div style='background:#fef3c7;border-left:3px solid #f59e0b;padding:14px 18px;border-radius:6px;margin:20px 0;'>"
                + "<p style='color:#92400e;margin:0;font-size:13px;'>Your feedback helps other customers find great providers.</p></div>"
                + "<div style='text-align:center;margin:24px 0;'>"
                + "<a href='" + feedbackLink + "' style='background:#7c3aed;color:white;padding:14px 36px;text-decoration:none;border-radius:8px;font-weight:700;font-size:15px;display:inline-block;'>⭐ Leave a Review</a></div>"
                + "<div style='text-align:center;margin-top:32px;padding-top:20px;border-top:2px solid #e5e7eb;'>"
                + "<p style='color:#6b7280;font-size:15px;font-weight:600;margin:0;'>The SpotLocalPro Team</p>"
                + "</div></div></body></html>";
    }

    private String buildCompletedCustomerHtmlEs(String customerName, String serviceName,
                                                String providerName, String price, String feedbackLink) {
        return "<!DOCTYPE html><html><body style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;background:#f3f4f6;padding:20px;'>"
                + "<div style='background:#7c3aed;padding:35px 20px;text-align:center;border-radius:12px 12px 0 0;'>"
                + "<div style='font-size:42px;margin-bottom:8px;'>⭐</div>"
                + "<h1 style='color:white;margin:0;font-size:26px;'>¡Servicio Completado!</h1>"
                + "<p style='color:#ede9fe;margin:6px 0 0 0;font-size:15px;'>¿Cómo fue tu experiencia?</p>"
                + "</div>"
                + "<div style='background:white;padding:28px;border-radius:0 0 12px 12px;'>"
                + "<p style='font-size:17px;color:#1f2937;margin:0 0 6px 0;'>Hola <strong>" + customerName + "</strong>,</p>"
                + "<p style='color:#4b5563;font-size:15px;margin:0 0 20px 0;'>¡Tu servicio ha sido completado!</p>"
                + "<div style='background:#f5f3ff;border:2px solid #c4b5fd;border-radius:10px;padding:20px;margin:20px 0;'>"
                + "<h3 style='color:#6d28d9;margin:0 0 14px 0;font-size:15px;'>📋 RESUMEN DEL SERVICIO</h3>"
                + "<table style='width:100%;'>"
                + simpleRow("Servicio", serviceName) + simpleRow("Proveedor", providerName)
                + simpleRow("Total", "<span style='color:#7c3aed;font-weight:700;font-size:17px;'>" + price + "</span>")
                + "</table></div>"
                + "<div style='background:#fef3c7;border-left:3px solid #f59e0b;padding:14px 18px;border-radius:6px;margin:20px 0;'>"
                + "<p style='color:#92400e;margin:0;font-size:13px;'>Tus comentarios ayudan a otros clientes a encontrar buenos proveedores.</p></div>"
                + "<div style='text-align:center;margin:24px 0;'>"
                + "<a href='" + feedbackLink + "' style='background:#7c3aed;color:white;padding:14px 36px;text-decoration:none;border-radius:8px;font-weight:700;font-size:15px;display:inline-block;'>⭐ Dejar una Reseña</a></div>"
                + "<div style='text-align:center;margin-top:32px;padding-top:20px;border-top:2px solid #e5e7eb;'>"
                + "<p style='color:#6b7280;font-size:15px;font-weight:600;margin:0;'>El equipo de SpotLocalPro</p>"
                + "</div></div></body></html>";
    }

    // =========================================================================
    //  Booking completed templates (provider — payment confirmation)
    // =========================================================================

    private String buildCompletedProviderHtmlEn(String providerName, Integer bookingId,
                                                String serviceName, String customerName,
                                                String price, String dashboardLink) {
        return "<!DOCTYPE html><html><body style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;background:#f3f4f6;padding:20px;'>"
                + "<div style='background:#0369a1;padding:35px 20px;text-align:center;border-radius:12px 12px 0 0;'>"
                + "<div style='font-size:42px;margin-bottom:8px;'>✅</div>"
                + "<h1 style='color:white;margin:0;font-size:26px;'>Booking Completed!</h1>"
                + "<p style='color:#e0f2fe;margin:6px 0 0 0;font-size:15px;'>Payment summary</p>"
                + "</div>"
                + "<div style='background:white;padding:28px;border-radius:0 0 12px 12px;'>"
                + "<p style='font-size:17px;color:#1f2937;margin:0 0 6px 0;'>Hi <strong>" + providerName + "</strong>,</p>"
                + "<p style='color:#4b5563;font-size:15px;margin:0 0 20px 0;'>Booking #" + bookingId + " has been marked as completed.</p>"
                + "<div style='background:#e0f2fe;border:2px solid #7dd3fc;border-radius:10px;padding:20px;margin:20px 0;'>"
                + "<h3 style='color:#075985;margin:0 0 14px 0;font-size:15px;'>📋 BOOKING SUMMARY</h3>"
                + "<table style='width:100%;'>"
                + simpleRow("Service", serviceName) + simpleRow("Customer", customerName)
                + simpleRow("Total", "<span style='color:#0369a1;font-weight:700;font-size:17px;'>" + price + "</span>")
                + "</table></div>"
                + "<div style='text-align:center;margin:20px 0;'>"
                + "<a href='" + dashboardLink + "' style='background:#0369a1;color:white;padding:12px 32px;text-decoration:none;border-radius:8px;font-weight:600;display:inline-block;'>View Dashboard</a></div>"
                + "<div style='text-align:center;margin-top:32px;padding-top:20px;border-top:2px solid #e5e7eb;'>"
                + "<p style='color:#6b7280;font-size:15px;font-weight:600;margin:0;'>The SpotLocalPro Team</p>"
                + "</div></div></body></html>";
    }

    private String buildCompletedProviderHtmlEs(String providerName, Integer bookingId,
                                                String serviceName, String customerName,
                                                String price, String dashboardLink) {
        return "<!DOCTYPE html><html><body style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;background:#f3f4f6;padding:20px;'>"
                + "<div style='background:#0369a1;padding:35px 20px;text-align:center;border-radius:12px 12px 0 0;'>"
                + "<div style='font-size:42px;margin-bottom:8px;'>✅</div>"
                + "<h1 style='color:white;margin:0;font-size:26px;'>¡Reserva Completada!</h1>"
                + "<p style='color:#e0f2fe;margin:6px 0 0 0;font-size:15px;'>Resumen de pago</p>"
                + "</div>"
                + "<div style='background:white;padding:28px;border-radius:0 0 12px 12px;'>"
                + "<p style='font-size:17px;color:#1f2937;margin:0 0 6px 0;'>Hola <strong>" + providerName + "</strong>,</p>"
                + "<p style='color:#4b5563;font-size:15px;margin:0 0 20px 0;'>La reserva #" + bookingId + " ha sido marcada como completada.</p>"
                + "<div style='background:#e0f2fe;border:2px solid #7dd3fc;border-radius:10px;padding:20px;margin:20px 0;'>"
                + "<h3 style='color:#075985;margin:0 0 14px 0;font-size:15px;'>📋 RESUMEN DE RESERVA</h3>"
                + "<table style='width:100%;'>"
                + simpleRow("Servicio", serviceName) + simpleRow("Cliente", customerName)
                + simpleRow("Total", "<span style='color:#0369a1;font-weight:700;font-size:17px;'>" + price + "</span>")
                + "</table></div>"
                + "<div style='text-align:center;margin:20px 0;'>"
                + "<a href='" + dashboardLink + "' style='background:#0369a1;color:white;padding:12px 32px;text-decoration:none;border-radius:8px;font-weight:600;display:inline-block;'>Ver Panel</a></div>"
                + "<div style='text-align:center;margin-top:32px;padding-top:20px;border-top:2px solid #e5e7eb;'>"
                + "<p style='color:#6b7280;font-size:15px;font-weight:600;margin:0;'>El equipo de SpotLocalPro</p>"
                + "</div></div></body></html>";
    }
}
