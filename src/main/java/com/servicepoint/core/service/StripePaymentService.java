package com.servicepoint.core.service;

import com.servicepoint.core.model.Booking;
import com.servicepoint.core.model.ServiceCatalog;
import com.servicepoint.core.repository.BookingRepository;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;

@Service
public class StripePaymentService {

    @Value("${stripe.api.key}")
    private String stripeApiKey;

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    @Value("${app.base.url}")
    private String baseUrl;

    @Autowired
    private BookingRepository bookingRepository;

    public StripePaymentService(@Value("${stripe.api.key}") String stripeApiKey) {
        Stripe.apiKey = stripeApiKey;
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        Stripe.apiKey = stripeApiKey;
    }

    /**
     * Create Stripe Checkout Session for booking payment
     */
    public Session createCheckoutSession(Booking booking, ServiceCatalog service) throws StripeException {
            // TODO: Review the amount
        // Calculate amount in cents
        long amountInCents = BigDecimal
                .valueOf(booking.getTotalPrice())
                .multiply(BigDecimal.valueOf(100))
                .longValue();


        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(baseUrl + "/booking/payment/success?session_id={CHECKOUT_SESSION_ID}&booking_id=" + booking.getBookingId())
                .setCancelUrl(baseUrl + "/booking/payment/cancel?booking_id=" + booking.getBookingId())
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency("usd")
                                                .setUnitAmount(amountInCents)
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName(service.getName())
                                                                .setDescription("Booking with " + service.getProvider().getUsername())
                                                                .build()
                                                )
                                                .build()
                                )
                                .setQuantity(1L)
                                .build()
                )
                .putMetadata("booking_id", booking.getBookingId().toString())
                .putMetadata("customer_id", booking.getCustomer().getUserId().toString())
                .putMetadata("provider_id", booking.getProvider().getUserId().toString())
                .setPaymentIntentData(
                        SessionCreateParams.PaymentIntentData.builder()
                                .putMetadata("booking_id", booking.getBookingId().toString())
                                .build()
                )
                // Enable multiple payment methods
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
//                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.APPLE_PAY)
//                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.GOOGLE_PAY)
                .build();

        return Session.create(params);
    }

    /**
     * Handle successful payment from Stripe webhook
     */
    @Transactional
    public void handlePaymentSuccess(String sessionId) throws StripeException {
        Session session = Session.retrieve(sessionId);

        String bookingIdStr = session.getMetadata().get("booking_id");
        if (bookingIdStr == null) {
            throw new IllegalArgumentException("Booking ID not found in session metadata");
        }

        Integer bookingId = Integer.parseInt(bookingIdStr);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));

        // Update booking status to paid
        booking.setStatus("paid");
        booking.setPaymentStatus("completed");
        booking.setStripeSessionId(sessionId);
        booking.setStripePaymentIntentId(session.getPaymentIntent());
        booking.setPaidAt(Timestamp.valueOf(LocalDateTime.now()));

        bookingRepository.save(booking);
    }


    /**
     * Handle expired checkout session from Stripe webhook
     */
    @Transactional
    public void handleSessionExpired(String sessionId) throws StripeException {
        Session session = Session.retrieve(sessionId);

        String bookingIdStr = session.getMetadata().get("booking_id");
        if (bookingIdStr == null) {
            System.err.println("Booking ID not found in expired session metadata");
            return;
        }

        Integer bookingId = Integer.parseInt(bookingIdStr);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));

        // Only update if payment hasn't been completed
        if (!"completed".equals(booking.getPaymentStatus())) {
            booking.setPaymentStatus("expired");
            // You might want to cancel the booking or keep it as pending
            // booking.setStatus("cancelled"); // Optional: cancel the booking
            bookingRepository.save(booking);

            System.out.println("Checkout session expired for booking: " + bookingId);
        } else {
            System.out.println("Booking already paid, ignoring expired session: " + bookingId);
        }
    }

    /**
     * Handle payment cancellation
     */
    @Transactional
    public void handlePaymentCancellation(Integer bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));

        booking.setPaymentStatus("cancelled");
        bookingRepository.save(booking);
    }

    /**
     * Verify webhook signature
     */
    public boolean verifyWebhookSignature(String payload, String sigHeader) {
        try {
            com.stripe.net.Webhook.constructEvent(payload, sigHeader, webhookSecret);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Process refund (if booking is cancelled)
     */
    public void processRefund(String paymentIntentId, Long amount) throws StripeException {
        com.stripe.param.RefundCreateParams params = com.stripe.param.RefundCreateParams.builder()
                .setPaymentIntent(paymentIntentId)
                .setAmount(amount)
                .setReason(com.stripe.param.RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER)
                .build();

        com.stripe.model.Refund.create(params);
    }

    /**
     * Construct and verify webhook event
     */
    public Event constructWebhookEvent(String payload, String sigHeader) {
        try {
            return Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            System.err.println("Webhook signature verification failed: " + e.getMessage());
            return null;
        }
    }
}