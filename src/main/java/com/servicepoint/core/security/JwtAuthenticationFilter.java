package com.servicepoint.core.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {

            System.out.println("REQUEST: " + request.getMethod() + " " + request.getRequestURI());
            System.out.println("AUTH HEADER: " + request.getHeader("Authorization"));

            String path = request.getRequestURI();
            String method = request.getMethod();
            String authHeader = request.getHeader("Authorization");
            System.out.println("=== FILTER HIT ===");
            System.out.println("METHOD: " + method);
            System.out.println("PATH: " + path);
            System.out.println("AUTH HEADER: " + authHeader);
            System.out.println("==================");

//            String authHeader = request.getHeader("Authorization");

            // If there is no Authorization header, continue filter chain
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                filterChain.doFilter(request, response);
                return;
            }

            String jwt = authHeader.substring(7);
            String username = jwtUtil.extractUsername(jwt);

            if (username != null &&
                    SecurityContextHolder.getContext().getAuthentication() == null) {

                UserDetails userDetails =
                        userDetailsService.loadUserByUsername(username);

                if (jwtUtil.validateToken(jwt, userDetails)) {

                    Collection<? extends GrantedAuthority> authorities =
                            extractAuthoritiesFromToken(jwt, userDetails);

                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    authorities
                            );

                    authToken.setDetails(
                            new WebAuthenticationDetailsSource()
                                    .buildDetails(request)
                    );

                    SecurityContextHolder
                            .getContext()
                            .setAuthentication(authToken);
                }
            }

        } catch (Exception e) {
            // Do NOT block request if JWT fails
            // Just continue filter chain
            System.out.println("JWT Authentication error: " + e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private Collection<? extends GrantedAuthority> extractAuthoritiesFromToken(
            String token,
            UserDetails userDetails) {

        try {
            String roles = jwtUtil.extractRoles(token);

            if (roles != null && !roles.isEmpty()) {
                return Arrays.stream(roles.split(","))
                        .map(role -> role.startsWith("ROLE_")
                                ? role
                                : "ROLE_" + role)
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());
            }

        } catch (Exception ignored) {}

        return userDetails.getAuthorities();
    }
}