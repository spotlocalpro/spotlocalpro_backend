package com.servicepoint.core.controller;

import com.servicepoint.core.dto.*;
import com.servicepoint.core.service.ProviderService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/providers")
@CrossOrigin("*")
public class ProviderController {


    @Autowired
    private ProviderService providerService;

    @GetMapping
    public List<ProviderWithUser> getProviders() {
        return providerService.getProviders();
    }


    @GetMapping("/{providerId}")
    public List<ServiceProvider> getProviderServices(@PathVariable Integer providerId){
        return providerService.getServicesByProvider(providerId);
    }
    @PostMapping("/nearby-service")
    public ResponseEntity<LocationSearchResponse> searchProvidersNearbyByService(
            @Valid @RequestBody LocationSearchRequest request) {

        long startTime = System.currentTimeMillis();

        LocationSearchResponse response = providerService.searchProvidersNearbyByService(request);

        long executionTime = System.currentTimeMillis() - startTime;

        // Add metadata
        LocationSearchResponse.SearchMetadata metadata = new LocationSearchResponse.SearchMetadata(
                request.getLatitude(),
                request.getLongitude(),
                executionTime + "ms",
                response.getProviders().size() == request.getLimit()
        );
        response.setMetadata(metadata);

        return ResponseEntity.ok(response);
    }

    // GET method for simple searches (backward compatibility)
    @GetMapping("/nearby-service")
    public ResponseEntity<List<ProviderWithUser>> getProvidersNearbyByService(
            @RequestParam String category,
            @RequestParam Double latitude,
            @RequestParam Double longitude,
            @RequestParam(defaultValue = "10.0") Double radius,
            @RequestParam(defaultValue = "20") Integer limit,
            @RequestParam(defaultValue = "0") Integer offset) {

        LocationSearchRequest request = new LocationSearchRequest(
                category, latitude, longitude, radius, limit, offset,null
        );

        List<ProviderWithUser> providers = providerService.getProvidersNearbyByService(request);
        return ResponseEntity.ok(providers);
    }

    // Advanced search endpoint with filters
    @PostMapping("/search-advanced")
    public ResponseEntity<LocationSearchResponse> advancedSearch(
            @Valid @RequestBody LocationSearchRequest request) {

        long startTime = System.currentTimeMillis();

        LocationSearchResponse response = providerService.advancedSearchProviders(request);

        long executionTime = System.currentTimeMillis() - startTime;

        LocationSearchResponse.SearchMetadata metadata = new LocationSearchResponse.SearchMetadata(
                request.getLatitude(),
                request.getLongitude(),
                executionTime + "ms",
                response.getProviders().size() == request.getLimit()
        );
        response.setMetadata(metadata);

        return ResponseEntity.ok(response);
    }
}
