package com.servicepoint.core.repository;

import com.servicepoint.core.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    List<User> findByRole(String role);

    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    @Query(value = """
        SELECT 
            u.user_id, u.email, u.password_hash, u.username, u.phone_number, 
            u.role, u.location, u.latitude, u.longitude, u.rating, u.review_count, 
            u.last_login, u.created_at, u.updated_at, u.profile_picture,
            ST_Distance(
                ST_SetSRID(ST_MakePoint(u.longitude, u.latitude), 4326)::geography,
                ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography
            ) / 1609.34 as distance_miles,
            MIN(sc.price) as min_price
        FROM users u 
        JOIN services sc ON u.user_id = sc.provider_id 
        WHERE u.role = 'provider' 
        AND u.latitude IS NOT NULL 
        AND u.longitude IS NOT NULL 
        AND ST_DWithin(
            ST_SetSRID(ST_MakePoint(u.longitude, u.latitude), 4326)::geography,
            ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
            :radius * 1609.34
        )
        AND (:category IS NULL OR LOWER(sc.category) = LOWER(:category))
        GROUP BY u.user_id, u.username, u.email, u.password_hash, u.phone_number, u.role, 
                 u.location, u.latitude, u.longitude, u.rating, u.review_count, 
                 u.last_login, u.created_at, u.updated_at, u.profile_picture
        ORDER BY distance_miles ASC, u.rating DESC NULLS LAST, min_price ASC
        LIMIT :limit OFFSET :offset
        """, nativeQuery = true)
    List<Object[]> findProvidersNearbyByServiceWithFilters(
            @Param("category") String category,
            @Param("latitude") Double latitude,
            @Param("longitude") Double longitude,
            @Param("radius") Double radius,
            @Param("limit") Integer limit,
            @Param("offset") Integer offset
    );

    @Query(value = """
        SELECT COUNT(DISTINCT u.user_id) 
        FROM users u 
        JOIN services sc ON u.user_id = sc.provider_id 
        WHERE u.role = 'provider' 
        AND u.latitude IS NOT NULL 
        AND u.longitude IS NOT NULL 
        AND ST_DWithin(
            ST_SetSRID(ST_MakePoint(u.longitude, u.latitude), 4326)::geography,
            ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
            :radius * 1609.34
        )
        AND (:category IS NULL OR LOWER(sc.category) = LOWER(:category))
        """, nativeQuery = true)
    Long countProvidersNearbyByServiceWithFilters(
            @Param("category") String category,
            @Param("latitude") Double latitude,
            @Param("longitude") Double longitude,
            @Param("radius") Double radius
    );

    @Query(value = """
        SELECT 
            u.user_id, u.email, u.password_hash, u.username, u.phone_number, 
            u.role, u.location, u.latitude, u.longitude, u.rating, u.review_count, 
            u.last_login, u.created_at, u.updated_at, u.profile_picture,
            NULL as distance_miles,
            MIN(sc.price) as min_price
        FROM users u 
        JOIN services sc ON u.user_id = sc.provider_id 
        WHERE u.role = 'provider' 
        AND (:category IS NULL OR LOWER(sc.category) = LOWER(:category))
        GROUP BY u.user_id
        ORDER BY u.rating DESC NULLS LAST, min_price ASC
        LIMIT :limit OFFSET :offset
        """, nativeQuery = true)
    List<Object[]> findProvidersByServiceWithoutDistance(
            @Param("category") String category,
            @Param("limit") Integer limit,
            @Param("offset") Integer offset
    );
}