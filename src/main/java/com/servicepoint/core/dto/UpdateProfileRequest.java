package com.servicepoint.core.dto;

public record UpdateProfileRequest (
        String username,
        String email,
        String phoneNumber,
        String bio
){}
