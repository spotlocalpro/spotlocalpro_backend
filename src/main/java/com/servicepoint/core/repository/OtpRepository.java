package com.servicepoint.core.repository;

import com.servicepoint.core.model.OtpCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.Optional;

@Repository
public interface OtpRepository extends JpaRepository<OtpCode, Integer> {

    Optional<OtpCode> findByEmailAndOtpCodeAndPurposeAndIsUsedFalse(
            String email,
            String otpCode,
            String purpose
    );

    Optional<OtpCode> findTopByEmailAndPurposeOrderByCreatedAtDesc(
            String email,
            String purpose
    );

    // Delete only stale OTPs (expired or already used) for an email+purpose.
    // Using explicit JPQL so the fresh OTP (not expired, isUsed=false) is never matched.
    @Transactional
    @Modifying
    @Query("DELETE FROM OtpCode o WHERE o.email = :email AND o.purpose = :purpose AND (o.isUsed = true OR o.expiresAt < CURRENT_TIMESTAMP)")
    void deleteStaleByEmailAndPurpose(@Param("email") String email, @Param("purpose") String purpose);

    // Clean up expired OTPs (hourly scheduler)
    void deleteByExpiresAtBefore(Timestamp timestamp);

    // Clean up used OTPs (hourly scheduler)
    void deleteByIsUsedTrue();
}