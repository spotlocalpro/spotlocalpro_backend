package com.servicepoint.core.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private Integer userId;
    private String username;
    private String email;
    private String role;
    private String bio;
    private Object profilePicture;
    private Object location;
    private Double latitude;
    private Double longitude;
    private Object phoneNumber;
    private Double rating;
    private Integer reviewCount;
    private Double distanceMiles;
    private String preferredLanguage;
    private String lastLogin;
    private String createdAt;
    private String updatedAt;

    //  constructor for required fields
    public UserResponse(Integer userId, String username, String email, String role) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.role = role;
    }
}
