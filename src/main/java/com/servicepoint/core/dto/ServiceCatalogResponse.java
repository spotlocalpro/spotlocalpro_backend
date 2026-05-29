package com.servicepoint.core.dto;

public record ServiceCatalogResponse(
        Integer serviceId,
        String name,
        String description,
        String category,
        String subCategory,
        Double price,
        String pricingType,
        String availability,
        String icon,
        String level,
        String subject,
        ProviderInfo provider
) {}
