package com.servicepoint.core.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NewServiceRequest {
    private String name;
    private Integer providerId;
    private String description;
    private String category;
    private String subCategory;
    private String pricingType;
    private Double price;
    private String availability;
    private String icon;
    private String level;
    private String subject;
}
