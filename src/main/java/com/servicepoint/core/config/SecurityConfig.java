package com.servicepoint.core.config;

import com.servicepoint.core.security.JwtAuthenticationFilter;
import com.servicepoint.core.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(UserDetailsService userDetailsService,
                                                            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtUtil jwtUtil,
                                                           UserDetailsService uds) {
        return new JwtAuthenticationFilter(jwtUtil, uds);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           DaoAuthenticationProvider authenticationProvider,
                                           JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authz -> authz

                        // ✅ Allow ALL OPTIONS preflight requests
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Public endpoints - no authentication required
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/users/register").permitAll()
                        .requestMatchers("/api/users/login").permitAll()
                        .requestMatchers("/api/users/me").permitAll()
                        .requestMatchers("/api/tokens/renew_access").permitAll()
                        .requestMatchers("/api/tokens/validate").permitAll()
                        .requestMatchers("/api/auth/check-username").permitAll()
                        .requestMatchers("/api/auth/check-email").permitAll()
                        .requestMatchers("/api/bookings/*/approve").permitAll()  
                        .requestMatchers("/api/bookings/*/decline").permitAll()

                        // Health check and documentation endpoints
                        .requestMatchers("/health", "/actuator/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

                        // Provider Registration - public submission, admin-only management
                        .requestMatchers("/api/provider-registration/request-otp").permitAll()
                        .requestMatchers("/api/provider-registration/submit").permitAll()

                        // Provider Registration Admin endpoints - require ADMIN role
                        .requestMatchers("/api/provider-registration/all").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/provider-registration/pending").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/provider-registration/approved").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/provider-registration/rejected").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/provider-registration/approve/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/provider-registration/reject/**").hasAuthority("ROLE_ADMIN")

                        // Provider Auth - public status check and login
                        .requestMatchers("/api/provider-auth/status/**").permitAll()
                        .requestMatchers("/api/provider-auth/login").permitAll()

                        // Payments
                        .requestMatchers("/api/payments/**").hasAnyAuthority(
                                "ROLE_USER", "ROLE_ADMIN", "ROLE_PROVIDER", "ROLE_CUSTOMER"
                        )

                        // Admin endpoints
                        .requestMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/uploads/provider-documents/**").hasAuthority("ROLE_ADMIN")

                        // Services - public
                        .requestMatchers("/api/services/**").permitAll()

                        // Authenticated user endpoints
                        .requestMatchers("/api/bookings/**").hasAnyAuthority(
                                "ROLE_USER", "ROLE_ADMIN", "ROLE_PROVIDER", "ROLE_CUSTOMER"
                        )
                        .requestMatchers("/api/feedback/**").hasAnyAuthority(
                                "ROLE_USER", "ROLE_ADMIN", "ROLE_PROVIDER", "ROLE_CUSTOMER"
                        )
                        .requestMatchers("/api/users/**").hasAnyAuthority("ROLE_CUSTOMER", "ROLE_PROVIDER", "ROLE_ADMIN")
                        .requestMatchers(HttpMethod.GET,"/api/providers/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/providers/nearby-service").permitAll()
                        .requestMatchers("/api/auth/forgot-password", "/api/auth/reset-password").permitAll()

                        // All other requests require authentication
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // Add this at the top of your class
    @Value("${cors.allowed.origins:http://localhost:3000,http://localhost:5173,https://www.spotlocalpro.com}")
    private String allowedOriginsStr;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOriginPatterns(Arrays.asList(
                "https://www.spotlocalpro.com",
                "https://spotlocalpro.com",
                "http://localhost:5173",
                "http://localhost:3000"
        ));

        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }


}