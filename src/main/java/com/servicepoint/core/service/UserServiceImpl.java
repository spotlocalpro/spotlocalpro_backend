package com.servicepoint.core.service;

import com.servicepoint.core.dto.*;
import com.servicepoint.core.exception.ResourceNotFoundException;
import com.servicepoint.core.model.Session;
import com.servicepoint.core.model.User;
import com.servicepoint.core.repository.SessionRepository;
import com.servicepoint.core.repository.UserRepository;
import com.servicepoint.core.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserServiceImpl implements UserService, UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private OtpService otpService;
    /**
     * Step 1: Initiate registration by sending OTP
     */
    public SendOtpResponse initiateRegistration(String email) throws Exception {
        // Check if email already exists
        if (userRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("User with this email already exists");
        }

        // Check rate limiting
        if ("TIME_LEFT".equalsIgnoreCase(otpService.canResendOtp(email, "registration"))) {
            throw new RuntimeException("Please wait before requesting new OTP");
        }
        // Generate and send OTP
        otpService.generateAndSendOtp(email, "registration");

        return new SendOtpResponse(true, "OTP sent to your email", 600L); // 10 minutes
    }

    /**
     * Step 2: Complete registration with OTP verification
     */
    @Override
    public UserResponse createUser(RegisterRequest request, HttpServletRequest httpRequest) throws Exception {
        // Verify OTP first
        boolean otpValid = otpService.verifyOtp(request.getEmail(), request.getOtpCode(), "registration");
        if (!otpValid) {
            throw new RuntimeException("Invalid or expired OTP code");
        }

        // Check if user already exists
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("User with this email already exists");
        }
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new RuntimeException("User with this username already exists");
        }

        // Create user
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
        user.setLatitude(request.getLatitude());
        user.setLongitude(request.getLongitude());
        user.setLocation(request.getLocation());
        user.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
        user.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));

        User savedUser = userRepository.save(user);
        return convertToDTO(savedUser);
    }

    /**
     * Step 1: Initiate login by sending OTP
     */
    public SendOtpResponse initiateLogin(String email) throws Exception {
        User user = findUserByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Check rate limiting
        if ("TIME_LEFT".equalsIgnoreCase(otpService.canResendOtp(user.getEmail(), "login"))) {
            throw new RuntimeException("Please wait before requesting a new OTP");
        }

        // Generate and send OTP
        otpService.generateAndSendOtp(user.getEmail(), "login");

        return new SendOtpResponse(true, "OTP sent to your registered email", 600L);
    }

    /**
     * Step 2: Complete login with password and OTP verification
     */
    @Override
    public LoginResponse loginUser(LoginRequest request, HttpServletRequest httpRequest) {
        System.out.println("DEBUG >> Login attempt for email: " + request.getEmail());

        User user = findUserByEmail(request.getEmail())
                .orElseThrow(() -> {
                    System.out.println("DEBUG >> User not found for email: " + request.getEmail());
                    return new RuntimeException("User not found");
                });

        System.out.println("DEBUG >> Found user in DB: " + user.getUsername() + " with role: " + user.getRole());

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            System.out.println("DEBUG >> Password verification failed");

            throw new RuntimeException("Invalid credentials");
        }

        String selectedRole = request.getRole();
        String actualRole = user.getRole();

        if (selectedRole != null && !selectedRole.isBlank()) {
            boolean isAdmin = "admin".equalsIgnoreCase(actualRole);

            if (!isAdmin && !selectedRole.equalsIgnoreCase(actualRole)) {
                System.out.println("DEBUG >> Role mismatch. Selected: " + selectedRole + ", Actual: " + actualRole);

                if ("customer".equalsIgnoreCase(selectedRole) && "provider".equalsIgnoreCase(actualRole)) {
                    throw new RuntimeException("This account is registered as a Service Provider. Please switch to the Provider role to log in.");
                }
                if ("provider".equalsIgnoreCase(selectedRole) && "customer".equalsIgnoreCase(actualRole)) {
                    throw new RuntimeException("This account is registered as a Customer. Please switch to the Customer role to log in.");
                }
                throw new RuntimeException("Invalid credentials");
            }
        }

        // Verify OTP
//        boolean otpValid = otpService.verifyOtp(user.getEmail(), request.getOtpCode(), "login");
//        if (!otpValid) {
//            System.out.println("DEBUG >> OTP verification failed for code: " + request.getOtpCode());
//            throw new RuntimeException("Invalid or expired OTP code");
//        }

        // Update last login
        user.setLastLogin(Timestamp.valueOf(LocalDateTime.now()));
        userRepository.save(user);


        System.out.println("DEBUG >> Calling loadUserByUsername with email: " + request.getEmail());
        UserDetails userDetails = loadUserByUsername(request.getEmail()); // Use the email

        System.out.println("DEBUG >> UserDetails loaded: " + userDetails.getUsername() + " with authorities: " + userDetails.getAuthorities());

        // Generate tokens with UserDetails to include roles
        String accessToken = jwtUtil.generateToken(userDetails);
        String refreshToken = jwtUtil.generateRefreshToken(user.getUsername());

        System.out.println("DEBUG >> Generated access token for: " + userDetails.getUsername());

        // Create and save session
        Session session = new Session();
        session.setSessionId(UUID.randomUUID().toString());
        session.setUser(user);
        session.setRefreshToken(refreshToken);
        session.setUserAgent(httpRequest.getHeader("User-Agent") != null ?
                httpRequest.getHeader("User-Agent") : "Unknown");
        session.setClientIp(getClientIpAddress(httpRequest));
        session.setIsBlocked(false);
        session.setExpiresAt(Timestamp.valueOf(LocalDateTime.now().plusDays(7)));
        session.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));

        sessionRepository.save(session);

        System.out.println("DEBUG >> Login successful for user: " + user.getUsername());

        return new LoginResponse(accessToken, refreshToken, 900000L, 604800000L,
                new UserInfo(user.getUserId(), user.getUsername(), user.getEmail(),
                        user.getRole(), user.getPhoneNumber(), user.getProfilePicture()));
    }

    @Override
    public UserResponse updateProfile(UpdateProfileRequest request, int userId) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPhoneNumber(request.phoneNumber());

        var updatedUser = userRepository.save(user);
        return convertToDTO(updatedUser);
    }

    @Override
    public UserResponse getUserById(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return convertToDTO(user);
    }

    @Override
    public List<User> findAllUsers() {
        return userRepository.findAll();
    }

    @Override
    public Optional<User> findUserById(Integer userId) {
        return userRepository.findById(userId);
    }

    @Override
    public Optional<User> findUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Override
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }




    @Override
    public Optional<User> findUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }


    @Override
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        System.out.println("DEBUG >> loadUserByUsername called with: " + usernameOrEmail);

        // Try to find by email first (support both username and email)
        Optional<User> userOptional = userRepository.findByEmail(usernameOrEmail);

        // If not found by email, try by username
        if (userOptional.isEmpty()) {
            userOptional = userRepository.findByUsername(usernameOrEmail);
        }

        User user = userOptional
                .orElseThrow(() -> {
                    System.out.println("DEBUG >> User not found with username/email: " + usernameOrEmail);
                    return new UsernameNotFoundException("User not found with username/email: " + usernameOrEmail);
                });

        System.out.println("DEBUG >> Found user: " + user.getUsername() + " with email: " + user.getEmail() + " and role: " + user.getRole());

        // Use .authorities() for explicit control
        String roleWithPrefix = "ROLE_" + user.getRole().toUpperCase();

        System.out.println("DEBUG >> Final authority for Spring Security: " + roleWithPrefix);

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPasswordHash())
                .authorities(roleWithPrefix)
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(false)
                .build();
    }

    @Override
    public User saveUser(User user) {
        if (user.getPasswordHash() != null && !user.getPasswordHash().startsWith("$2a$")) {
            user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
        }
        if (user.getUserId() == null) {
            user.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
        }
        user.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
        return userRepository.save(user);
    }

    @Override
    public void deleteUser(Integer userId) {
        if (!userRepository.existsById(userId)) {
            throw new RuntimeException("User not found");
        }
        userRepository.deleteById(userId);
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedForHeader = request.getHeader("X-Forwarded-For");
        if (xForwardedForHeader != null && !xForwardedForHeader.isEmpty()) {
            return xForwardedForHeader.split(",")[0].trim();
        }

        String xRealIpHeader = request.getHeader("X-Real-IP");
        if (xRealIpHeader != null && !xRealIpHeader.isEmpty()) {
            return xRealIpHeader;
        }

        return request.getRemoteAddr();
    }

    private UserResponse convertToDTO(User user) {
        UserResponse userDTO = new UserResponse();
        userDTO.setUserId(user.getUserId());
        userDTO.setUsername(user.getUsername());
        userDTO.setEmail(user.getEmail());
        userDTO.setRole(user.getRole());
        userDTO.setProfilePicture(user.getProfilePicture());
        userDTO.setLocation(user.getLocation());
        userDTO.setLatitude(user.getLatitude());
        userDTO.setLongitude(user.getLongitude());
        userDTO.setPhoneNumber(user.getPhoneNumber());
        userDTO.setRating(user.getRating());
        userDTO.setReviewCount(user.getReviewCount());
        userDTO.setLastLogin(user.getLastLogin() != null ? user.getLastLogin().toString() : null);
        userDTO.setCreatedAt(user.getCreatedAt().toString());
        userDTO.setUpdatedAt(user.getUpdatedAt().toString());
        return userDTO;
    }

    @Override
    public void changePassword(String username, String currentPassword, String newPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // ✅ Verify current password
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new RuntimeException("Current password is incorrect");
        }

        // ✅ Save new hashed password
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}