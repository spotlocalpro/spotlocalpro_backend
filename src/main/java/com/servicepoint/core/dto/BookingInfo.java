package com.servicepoint.core.dto;

import java.sql.Timestamp;

public record BookingInfo(
        Integer id,
        Timestamp bookingDate,
        Timestamp serviceDateTime,
        String status,
        String notes,
        Double priceAtBooking,
        String pricingTypeAtBooking,
        Double totalPrice,
        CustomerInfo customer,
        ProviderInfo provider,
        ServiceInfo service

) {}