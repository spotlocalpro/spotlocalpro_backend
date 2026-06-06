package com.servicepoint.core.controller;

import com.servicepoint.core.dto.*;
import com.servicepoint.core.model.User;
import com.servicepoint.core.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/profile")
    public ResponseEntity<?> getCurrentUserProfile() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String email = auth.getName();

            Optional<User> userOptional = userService.findUserByEmail(email);
            if (userOptional.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            UserResponse userDTO = userService.getUserById(userOptional.get().getUserId());
            return ResponseEntity.ok(userDTO);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(new AuthController.ErrorResponse("Profile retrieval failed", e.getMessage()));
        }
    }

    @GetMapping("/{userId}")
    public ResponseEntity<?> getUserById(@PathVariable Integer userId) {
        try {
            UserResponse userDTO = userService.getUserById(userId);
            return ResponseEntity.ok(userDTO);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(new AuthController.ErrorResponse("User retrieval failed", e.getMessage()));
        }
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<User>> getAllUsers() {
        List<User> users = userService.findAllUsers();
        return ResponseEntity.ok(users);
    }

    @PutMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN') or authentication.name == @userService.findUserById(#userId).orElse(new com.servicepoint.core.model.User()).email")
    public ResponseEntity<?> updateUser(@PathVariable Integer userId, @RequestBody UserInfo userInfo) {
        try {
            Optional<User> existingUserOpt = userService.findUserById(userId);
            if (existingUserOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            User existingUser = getExistingUser(userInfo, existingUserOpt);

            // Save updated user
            User updatedUser = userService.saveUser(existingUser);

            return ResponseEntity.ok(updatedUser);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(new AuthController.ErrorResponse("User update failed", e.getMessage()));
        }
    }

    private static User getExistingUser(UserInfo userInfo, Optional<User> existingUserOpt) {
        User existingUser = existingUserOpt.get();

        // Merge incoming DTO fields into existing entity
        if (userInfo.getUsername() != null) existingUser.setUsername(userInfo.getUsername());
        if (userInfo.getEmail() != null) existingUser.setEmail(userInfo.getEmail());
        if (userInfo.getRole() != null) existingUser.setRole(userInfo.getRole());
        if (userInfo.getPhoneNumber() != null) existingUser.setPhoneNumber(userInfo.getPhoneNumber());
        if (userInfo.getProfilePicture() != null) existingUser.setProfilePicture(userInfo.getProfilePicture());
        return existingUser;
    }


    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteUser(@PathVariable Integer userId) {
        try {
            userService.deleteUser(userId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(new AuthController.ErrorResponse("User deletion failed", e.getMessage()));
        }
    }

    @PutMapping("/{userId}/profile")
    public ResponseEntity<UserResponse>
    updateUserProfile(@PathVariable Integer userId,
                      @RequestBody UpdateProfileRequest request) {
        var updatedProfile = userService.updateProfile(request, userId);
        return ResponseEntity.ok(updatedProfile);

    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        try {
            userService.changePassword(userDetails.getUsername(), request.getCurrentPassword(), request.getNewPassword());
            return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse(e.getMessage()));
        }
    }

    @PatchMapping("/{userId}/language")
    public ResponseEntity<?> updateLanguage(
            @PathVariable Integer userId,
            @RequestBody Map<String, String> body
    ) {
        String lang = body.getOrDefault("preferredLanguage", body.getOrDefault("language", "en"));
        if (!lang.equals("en") && !lang.equals("es")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unsupported language. Use 'en' or 'es'."));
        }
        userService.updateLanguage(userId, lang);

        Map<String, Object> response = new HashMap<>();
        response.put("language", lang);
        return ResponseEntity.ok(response);
    }
}