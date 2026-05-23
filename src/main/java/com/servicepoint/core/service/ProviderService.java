package com.servicepoint.core.service;

import com.servicepoint.core.dto.*;
import com.servicepoint.core.exception.ResourceNotFoundException;
import com.servicepoint.core.model.ServiceCatalog;
import com.servicepoint.core.model.User;
import com.servicepoint.core.repository.ServiceCatalogRepository;
import com.servicepoint.core.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProviderService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ServiceCatalogService catalogService;

    @Autowired
    private ServiceCatalogRepository serviceRepository;

    // ✅ Haversine formula — calculates distance in miles between two lat/lng points
    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int EARTH_RADIUS_MILES = 3959;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_MILES * c;
    }

    public List<ProviderWithUser> getProviders() {
        return userRepository.findAll().stream()
                .filter(user -> "provider".equals(user.getRole()))
                .map(this::mapToProviderWithUser)
                .collect(Collectors.toList());
    }

    // ✅ Main nearby search using Haversine — no PostGIS needed
    public List<ProviderWithUser> getProvidersNearbyByService(LocationSearchRequest request) {
        if (request.getLatitude() == null || request.getLongitude() == null) {
            return getProvidersByServiceWithoutLocation(request);
        }

        List<User> allProviders = userRepository.findAll().stream()
                .filter(user -> "provider".equals(user.getRole()))
                .collect(Collectors.toList());

        return allProviders.stream()
                .map(provider -> {
                    Double distance = null;
                    if (provider.getLatitude() != null && provider.getLongitude() != null) {
                        distance = haversineDistance(
                                request.getLatitude(), request.getLongitude(),
                                provider.getLatitude(), provider.getLongitude()
                        );
                    }
                    return new Object[]{provider, distance};
                })
                // ✅ Filter by category — provider must have at least one matching service
                .filter(pair -> {
                    User provider = (User) pair[0];
                    List<ServiceCatalog> services = serviceRepository
                            .findByProviderUserId(provider.getUserId());
                    return services.stream().anyMatch(s ->
                            request.getCategory() == null ||
                                    s.getCategory().equalsIgnoreCase(request.getCategory())
                    );
                })
                // ✅ Filter by level if tutoring
                .filter(pair -> {
                    if (request.getLevel() == null || request.getLevel().isEmpty()) return true;
                    User provider = (User) pair[0];
                    List<ServiceCatalog> services = serviceRepository
                            .findByProviderUserId(provider.getUserId());
                    return services.stream().anyMatch(s ->
                            request.getLevel().equalsIgnoreCase(s.getLevel())
                    );
                })
                // ✅ Sort by distance — providers with no coords go to end
                .sorted((a, b) -> {
                    Double distA = (Double) a[1];
                    Double distB = (Double) b[1];
                    if (distA == null && distB == null) return 0;
                    if (distA == null) return 1;
                    if (distB == null) return -1;
                    return Double.compare(distA, distB);
                })
                .skip(request.getOffset())
                .limit(request.getLimit())
                .map(pair -> mapToProviderWithUserAndDistance(
                        (User) pair[0], (Double) pair[1], request.getCategory()
                ))
                .collect(Collectors.toList());
    }

    private List<ProviderWithUser> getProvidersByServiceWithoutLocation(LocationSearchRequest request) {
        return userRepository.findAll().stream()
                .filter(user -> "provider".equals(user.getRole()))
                .filter(provider -> {
                    List<ServiceCatalog> services = serviceRepository
                            .findByProviderUserId(provider.getUserId());
                    return services.stream().anyMatch(s ->
                            request.getCategory() == null ||
                                    s.getCategory().equalsIgnoreCase(request.getCategory())
                    );
                })
                .skip(request.getOffset())
                .limit(request.getLimit())
                .map(this::mapToProviderWithUser)
                .collect(Collectors.toList());
    }

    public LocationSearchResponse searchProvidersNearbyByService(LocationSearchRequest request) {
        long startTime = System.currentTimeMillis();

        List<ProviderWithUser> providers = getProvidersNearbyByService(request);

        long totalCount = userRepository.findAll().stream()
                .filter(user -> "provider".equals(user.getRole()))
                .filter(provider -> {
                    List<ServiceCatalog> services = serviceRepository
                            .findByProviderUserId(provider.getUserId());
                    return services.stream().anyMatch(s ->
                            request.getCategory() == null ||
                                    s.getCategory().equalsIgnoreCase(request.getCategory())
                    );
                })
                .count();

        long executionTime = System.currentTimeMillis() - startTime;

        LocationSearchResponse.SearchMetadata metadata = new LocationSearchResponse.SearchMetadata(
                request.getLatitude(),
                request.getLongitude(),
                executionTime + "ms",
                providers.size() == request.getLimit()
        );

        return new LocationSearchResponse(
                providers,
                (int) totalCount,
                request.getLimit(),
                request.getOffset(),
                request.getRadius(),
                request.getCategory(),
                metadata
        );
    }

    public LocationSearchResponse advancedSearchProviders(LocationSearchRequest request) {
        return searchProvidersNearbyByService(request);
    }

    public List<ServiceProvider> getServicesByProvider(Integer providerId) {
        User provider = userRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));

        return catalogService.findServicesByProviderId(provider.getUserId())
                .stream()
                .map(service -> new ServiceProvider(
                        service.getProvider().getUserId(),
                        service.getProvider().getUsername(),
                        service.getProvider().getEmail(),
                        service.getProvider().getRole(),
                        new ServiceInfo(
                                service.getServiceId(),
                                service.getName(),
                                service.getDescription(),
                                service.getCategory(),
                                service.getAvailability(),
                                service.getPrice(),
                                service.getPricingType(),
                                service.getLevel(),
                                service.getSubject()
                        )
                ))
                .collect(Collectors.toList());
    }

    // ✅ Now maps ALL services for the provider — filtered by category if provided
    private ProviderWithUser mapToProviderWithUserAndDistance(
            User provider, Double distanceMiles, String category) {

        // ✅ Collect ALL matching services instead of just the first one
        List<ServiceInfo> serviceInfoList = serviceRepository
                .findByProviderUserId(provider.getUserId())
                .stream()
                .filter(s -> category == null || s.getCategory().equalsIgnoreCase(category))
                .map(s -> new ServiceInfo(
                        s.getServiceId(),
                        s.getName(),
                        s.getDescription(),
                        s.getCategory(),
                        s.getAvailability(),
                        s.getPrice(),
                        s.getPricingType(),
                        s.getLevel(),
                        s.getSubject()
                ))
                .collect(Collectors.toList());

        UserResponse userDTO = new UserResponse(
                provider.getUserId(),
                provider.getUsername(),
                provider.getEmail(),
                provider.getRole(),
                provider.getBio(),
                provider.getProfilePicture(),
                provider.getLocation(),
                provider.getLatitude(),
                provider.getLongitude(),
                provider.getPhoneNumber(),
                provider.getRating(),
                provider.getReviewCount(),
                distanceMiles,
                provider.getLastLogin() != null ? provider.getLastLogin().toString() : null,
                provider.getCreatedAt().toString(),
                provider.getUpdatedAt().toString()
        );

        return new ProviderWithUser(provider.getUserId(), userDTO, serviceInfoList);
    }

    private ProviderWithUser mapToProviderWithUser(User provider) {
        return mapToProviderWithUserAndDistance(provider, null, null);
    }
}