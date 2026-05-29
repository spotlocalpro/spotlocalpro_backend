package com.servicepoint.core.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ServiceInfo {
    private Integer serviceId;
    private String name;
    private String description;
    private String category;
    private String subCategory;
    private String availability;
    private Double price;
    private String pricingType;
    private String level;
    private String subject;
}