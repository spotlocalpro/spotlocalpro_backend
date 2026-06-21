package com.servicepoint.core.controller;

import com.servicepoint.core.dto.*;
import com.servicepoint.core.model.User;
import com.servicepoint.core.security.JwtUtil;
import com.servicepoint.core.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.Optional;

@RestController
@RequestMapping("/api/tokens")
public class TokenController {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserService userService;

    @Autowired
    private UserDetailsService userDetailsService; // ✅ add this

    @PostMapping("/renew_access")
    public ResponseEntity<?> renewAccessToken(@RequestBody RenewAccessTokenRequest request) {
        try {
            System.out.println("=== RENEW ACCESS TOKEN ===");
            System.out.println("Refresh token received: " + (request.getRefreshToken() != null ? "YES" : "NO"));

            if (request.getRefreshToken() == null || request.getRefreshToken().trim().isEmpty()) {
                System.out.println("❌ Refresh token is null or empty");
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse("Refresh token is required"));
            }

            String userName;
            try {
                userName =jwtUtil.extractUsername(request.getRefreshToken());
                System.out.println("✅ Email extracted: " + userName);
            } catch (Exception e) {
                System.out.println("❌ Failed to extract username: " + e.getMessage());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("Invalid refresh token"));
            }

            boolean isExpired = jwtUtil.extractExpiration(request.getRefreshToken()).before(new Date());
            System.out.println("Token expired: " + isExpired);

            if (isExpired) {
                System.out.println("❌ Refresh token is expired");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("Refresh token has expired"));
            }

            Optional<User> userOptional = userService.findUserByUsername(userName);
            System.out.println("User found: " + userOptional.isPresent());

            if (userOptional.isEmpty()) {
                System.out.println("❌ User not found for email: " + userName);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("User not found"));
            }

            boolean isValid = jwtUtil.validateToken(request.getRefreshToken(), userName);
            System.out.println("Token valid: " + isValid);

            if (!isValid) {
                System.out.println("❌ Token validation failed");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("Invalid refresh token"));
            }

            UserDetails userDetails = userDetailsService.loadUserByUsername(userName);
            System.out.println("✅ UserDetails loaded: " + userDetails.getUsername());

            String newAccessToken = jwtUtil.generateToken(userDetails);
            System.out.println("✅ New access token generated");

            long expirationTimeMs = 60 * 60 * 1000;
            Date expirationDate = new Date(System.currentTimeMillis() + expirationTimeMs);

            RenewAccessTokenResponse response = new RenewAccessTokenResponse(
                    newAccessToken,
                    expirationDate,
                    expirationTimeMs
            );

            System.out.println("✅ Returning new access token");
            System.out.println("==========================");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.out.println("❌ Exception: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("An error occurred: " + e.getMessage()));
        }
    }

    @PostMapping("/validate")
    public ResponseEntity<?> validateToken(@RequestBody ValidateTokenRequest request) {
        try {
            if (request.getToken() == null || request.getToken().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse("Token is required"));
            }

            String email = jwtUtil.extractUsername(request.getToken());
            Date expiration = jwtUtil.extractExpiration(request.getToken());
            boolean isExpired = expiration.before(new Date());

            Optional<User> userOptional = userService.findUserByEmail(email);
            if (userOptional.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("User not found"));
            }

            // ✅ Validate against email string directly
            boolean isValid = jwtUtil.validateToken(request.getToken(), email);

            ValidateTokenResponse response = new ValidateTokenResponse(
                    isValid && !isExpired,
                    email,
                    expiration,
                    isExpired
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid token"));
        }
    }

    @PostMapping("/revoke")
    public ResponseEntity<?> revokeToken(@RequestBody RevokeTokenRequest request) {
        try {
            if (request.getToken() == null || request.getToken().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse("Token is required"));
            }
            return ResponseEntity.ok(new RevokeTokenResponse("Token revoked successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("An error occurred while revoking token"));
        }
    }
}