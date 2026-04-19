package com.servicepoint.core.repository;

import com.servicepoint.core.model.ProviderRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProviderRegistrationRepository extends JpaRepository<ProviderRegistration, Integer> {

    Optional<ProviderRegistration> findByEmailAndStatus(
            String email,
            ProviderRegistration.RegistrationStatus status
    );

    List<ProviderRegistration> findByStatusOrderBySubmittedAtDesc(
            ProviderRegistration.RegistrationStatus status
    );

    Optional<ProviderRegistration> findByEmail(String email);

    List<ProviderRegistration> findByStatus(ProviderRegistration.RegistrationStatus status);

    boolean existsByEmail(String email);
}