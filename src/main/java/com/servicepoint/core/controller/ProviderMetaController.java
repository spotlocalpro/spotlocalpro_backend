package com.servicepoint.core.controller;

import com.servicepoint.core.model.ProviderMeta;
import com.servicepoint.core.repository.ProviderMetaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/provider-meta")
@CrossOrigin(origins = "*")
public class ProviderMetaController {

    @Autowired
    private ProviderMetaRepository providerMetaRepository;

    @GetMapping("/{providerId}")
    public ResponseEntity<?> getMeta(@PathVariable Integer providerId) {
        ProviderMeta meta = providerMetaRepository.findByProviderId(providerId)
                .orElse(new ProviderMeta());
        return ResponseEntity.ok(Map.of(
                "providerId", providerId,
                "yearsOfExperience", meta.getYearsOfExperience() != null ? meta.getYearsOfExperience() : 0,
                "offersQuote", meta.getOffersQuote() != null ? meta.getOffersQuote() : false
        ));
    }

    @PutMapping("/{providerId}")
    public ResponseEntity<?> upsertMeta(
            @PathVariable Integer providerId,
            @RequestBody Map<String, Object> body
    ) {
        ProviderMeta meta = providerMetaRepository.findByProviderId(providerId)
                .orElse(new ProviderMeta());

        meta.setProviderId(providerId);

        if (body.containsKey("yearsOfExperience") && body.get("yearsOfExperience") != null) {
            meta.setYearsOfExperience(((Number) body.get("yearsOfExperience")).intValue());
        }
        if (body.containsKey("offersQuote") && body.get("offersQuote") != null) {
            meta.setOffersQuote((Boolean) body.get("offersQuote"));
        }

        ProviderMeta saved = providerMetaRepository.save(meta);

        return ResponseEntity.ok(Map.of(
                "providerId", providerId,
                "yearsOfExperience", saved.getYearsOfExperience() != null ? saved.getYearsOfExperience() : 0,
                "offersQuote", saved.getOffersQuote() != null ? saved.getOffersQuote() : false
        ));
    }
}
