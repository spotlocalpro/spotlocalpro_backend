package com.servicepoint.core.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProviderInfo {

    private Integer id;
    private String username;
    private String email;
    private String role;
    private String location;
    private String phoneNumber;
    private Integer rating;
    private Integer reviewCount;
    private String profilePicture;
    private String comments;


    public ProviderInfo(Integer userId, String username, String email, String role) {
        this.id = userId;
        this.username = username;
        this.email = email;
        this.role = role;
    }

    public ProviderInfo(Integer userId, String username, String email, String role,Integer rating,String comments) {
        this.id = userId;
        this.username = username;
        this.email = email;
        this.role = role;
        this.rating=rating;
        this.comments=comments;
    }


}
