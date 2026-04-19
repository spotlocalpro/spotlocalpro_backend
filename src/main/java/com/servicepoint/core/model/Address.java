package com.servicepoint.core.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "addresses")
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer addressId;

    @Column(name = "user_id", nullable = false)
    private Integer user;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private String streetAddress;

    @Column(nullable = false)
    private String city;

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    @Column
    private String state;

    @Column
    private String zipCode;

    @Column(nullable = false)
    private String country;

    @Column(columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean isDefault = false;
}