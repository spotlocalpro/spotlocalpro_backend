package com.servicepoint.core.dto;

import java.sql.Timestamp;

public record FeedbackResponse(
        SimpleBookingInfo booking,
        CustomerInfo customer,
        ProviderInfo provider,
        String comments,
        Integer rating,        // ✅ already there
        Timestamp submissionDate  // ✅ add this — was missing
) {}