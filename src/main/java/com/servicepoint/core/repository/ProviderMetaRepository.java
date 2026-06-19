package com.servicepoint.core.repository;

import com.servicepoint.core.model.ProviderMeta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProviderMetaRepository extends JpaRepository<ProviderMeta, Integer> {
    Optional<ProviderMeta> findByProviderId(Integer providerId);
}
