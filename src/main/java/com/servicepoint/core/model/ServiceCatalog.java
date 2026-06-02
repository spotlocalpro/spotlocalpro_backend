package com.servicepoint.core.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor


@Setter
@Getter

@Entity
@Table(name = "services")
public class ServiceCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer serviceId;

    @ManyToOne
    @JoinColumn(name = "provider_id")
    private User provider;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String pricingType; // hourly, per_work

    @Column(nullable = false)
    private Double price;

    private String subCategory;

    private String availability;

    private String icon;

    private String level;

    private String subject;

    public Integer getServiceId() {
        return serviceId;
    }

    public void setServiceId(Integer serviceId) {
        this.serviceId = serviceId;
    }

}
