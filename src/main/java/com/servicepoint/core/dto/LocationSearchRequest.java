package com.servicepoint.core.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LocationSearchRequest {

    @NotBlank(message = "Service category is required")
    private String category;

    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    private Double longitude;

    @DecimalMin(value = "0.1")
    @DecimalMax(value = "100.0")
    private Double radius = 25.0;

    @Min(value = 1)
    @Max(value = 100)
    private Integer limit = 20;

    @Min(value = 0)
    private Integer offset = 0;

    private String level; // ✅ for tutoring filter
}