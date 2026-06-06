package com.servicepoint.core.service;

import com.servicepoint.core.model.Booking;

/**
 * Sends booking-related emails (async) to providers and customers.
 * All methods fail-safe: mail errors are logged but never propagate.
 */
public interface BookingNotificationService {

    /**
     * Initial "new booking request" email to the provider.
     * Privacy-safe: no customer phone number or address is included.
     */
    void sendNewBookingRequestToProvider(Booking booking);

    /**
     * Follow-up email to the provider after they approve — reveals customer phone + location.
     */
    void sendApprovalFollowUpToProvider(Booking booking);

    /**
     * Notification to the customer that their booking request was approved.
     */
    void sendApprovalNotificationToCustomer(Booking booking);

    /**
     * Notification to the customer that their booking request was declined.
     */
    void sendDeclineNotificationToCustomer(Booking booking);

    /**
     * Notification to the customer that the service has been completed (ask for review).
     */
    void sendBookingCompletedToCustomer(Booking booking);

    /**
     * Notification to the provider that the booking has been marked as completed.
     */
    void sendBookingCompletedToProvider(Booking booking);
}