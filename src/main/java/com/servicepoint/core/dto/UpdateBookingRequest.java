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


public class UpdateBookingRequest {

    private Integer bookingId;
    private Timestamp serviceDateTime;
    private String status;
    private String notes;
    private Double priceAtBooking;
    private String pricingTypeAtBooking;
    private Double totalPrice;
}
