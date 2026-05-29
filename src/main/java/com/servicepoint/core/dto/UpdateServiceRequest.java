package com.servicepoint.core.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateServiceRequest {
//    private String serviceId; // No need to include the id since it will be passed via url parameter.
    private String name;
    private String description;
    private Double price;
    private String category;
    private String subCategory;
    private String pricingType;
    private String availability;
    private String icon;
    private String level;
    private String subject;
}
