package com.servicepoint.core.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.sql.Timestamp;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class FeedbackRequest {
    private Integer bookingId;
    private Integer customerId;
    private Integer providerId;
    private String comments;
    private Integer rating; // ✅ add this
    private Timestamp submissionDate;
}