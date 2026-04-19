package com.servicepoint.core.service;

import com.servicepoint.core.dto.*;
import com.servicepoint.core.model.User;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Optional;

public interface UserService {
    SendOtpResponse initiateRegistration(String email) throws Exception;
    SendOtpResponse initiateLogin(String username) throws Exception;

    UserResponse createUser(RegisterRequest request, HttpServletRequest httpRequest) throws Exception;
    LoginResponse loginUser(LoginRequest request, HttpServletRequest httpRequest);

    // Profile management
    UserResponse updateProfile(UpdateProfileRequest request, int userId);
    UserResponse getUserById(Integer userId);

    // User CRUD operations
    List<User> findAllUsers();
    Optional<User> findUserById(Integer userId);

    Optional<User> findUserByUsername(String username);
    boolean existsByUsername(String username);
    Optional<User> findUserByEmail(String email);
    boolean existsByEmail(String email);
    User saveUser(User user);
    void deleteUser(Integer userId);
    void changePassword(String username, String currentPassword, String newPassword);




}



