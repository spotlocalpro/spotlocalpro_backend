package com.servicepoint.core.service;

import com.servicepoint.core.dto.*;
import com.servicepoint.core.exception.ResourceNotFoundException;
import com.servicepoint.core.model.ServiceCatalog;
import com.servicepoint.core.model.User;
import com.servicepoint.core.repository.ServiceCatalogRepository;
import com.servicepoint.core.repository.UserRepository;
import jakarta.persistence.Column;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ServiceCatalogServiceImpl implements ServiceCatalogService {

    @Autowired
    private ServiceCatalogRepository serviceRepository;
    @Autowired
    private UserRepository userRepository;

    @Override
    public List<ServiceCatalogResponse> findAllServices() {
        return serviceRepository.findAll().stream()
                .map(service -> new ServiceCatalogResponse(
                        service.getServiceId(),
                        service.getName(),
                        service.getDescription(),
                        service.getCategory(),
                        service.getSubCategory(),
                        service.getPrice(),
                        service.getPricingType(),
                        service.getAvailability(),
                        service.getIcon(),
                        service.getLevel(),
                        service.getSubject(),
                        new ProviderInfo(
                                service.getProvider().getUserId(),
                                service.getProvider().getUsername(),
                                service.getProvider().getEmail(),
                                service.getProvider().getRole()
                        )
                ))
                .collect(Collectors.toList());
    }

    @Override
    public List<ServiceCatalogResponse> findServiceById(Integer serviceId) {
        return serviceRepository.findById(serviceId).stream()
                .map(service -> new ServiceCatalogResponse(
                        service.getServiceId(),
                        service.getName(),
                        service.getDescription(),
                        service.getCategory(),
                        service.getSubCategory(),
                        service.getPrice(),
                        service.getPricingType(),
                        service.getAvailability(),
                        service.getIcon(),
                        service.getLevel(),
                        service.getSubject(),
                        new ProviderInfo(
                                service.getProvider().getUserId(),
                                service.getProvider().getUsername(),
                                service.getProvider().getEmail(),
                                service.getProvider().getRole()
                        )
                ))
                .collect(Collectors.toList());
    }

    @Override
    public ServiceCatalogResponse saveService(NewServiceRequest request) {
        // Fetch the provider (User) from the database using providerId
        User provider = userRepository.findById(request.getProviderId())
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));

        // Create a new ServiceCatalog entity
        ServiceCatalog serviceCatalog = new ServiceCatalog();

        // Map required fields (non-nullable)
        serviceCatalog.setName(request.getName());
        serviceCatalog.setCategory(request.getCategory());
        serviceCatalog.setPricingType(request.getPricingType());
        serviceCatalog.setPrice(request.getPrice());
        serviceCatalog.setProvider(provider);

        // Map optional fields (nullable)
        if (request.getDescription() != null) {
            serviceCatalog.setDescription(request.getDescription());
        }
        if (request.getSubCategory() != null) {
            serviceCatalog.setSubCategory(request.getSubCategory());
        }
        if (request.getAvailability() != null) {
            serviceCatalog.setAvailability(request.getAvailability());
        }
        if (request.getIcon() != null) {
            serviceCatalog.setIcon(request.getIcon());
        }
        if (request.getLevel() != null) {
            serviceCatalog.setLevel(request.getLevel());
        }
        if (request.getSubject() != null) {
            serviceCatalog.setSubject(request.getSubject());
        }

        // Save and return the entity
        var createdService = serviceRepository.save(serviceCatalog);

        return new ServiceCatalogResponse(
                createdService.getServiceId(),
                createdService.getName(),
                createdService.getDescription(),
                createdService.getCategory(),
                createdService.getSubCategory(),
                createdService.getPrice(),
                createdService.getPricingType(),
                createdService.getAvailability(),
                createdService.getIcon(),
                createdService.getLevel(),
                createdService.getSubject(),
                new ProviderInfo(
                        createdService.getProvider().getUserId(),
                        createdService.getProvider().getUsername(),
                        createdService.getProvider().getEmail(),
                        createdService.getProvider().getRole()
                ));
    }


    @Override
    public void deleteService(Integer serviceId) {

       var service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));
        serviceRepository.deleteById(service.getServiceId());
    }

    @Override
    public List<ServiceCatalog> findServicesByProviderId(Integer providerId) {
        return serviceRepository.findByProviderUserId(providerId);
    }

    @Override
    public ServiceCatalogResponse updateService(Integer serviceId, UpdateServiceRequest request) {
        ServiceCatalog existing = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

        if (request.getName() != null) existing.setName(request.getName());
        if (request.getDescription() != null) existing.setDescription(request.getDescription());
        if (request.getPricingType() != null) existing.setPricingType(request.getPricingType());
        if (request.getCategory() != null) existing.setCategory(request.getCategory());
        if (request.getSubCategory() != null) existing.setSubCategory(request.getSubCategory());
        if (request.getPrice() != null) existing.setPrice(request.getPrice());
        if (request.getAvailability() != null) existing.setAvailability(request.getAvailability());
        if (request.getIcon() != null) existing.setIcon(request.getIcon());
        if (request.getLevel() != null) existing.setLevel(request.getLevel());
        if (request.getSubject() != null) existing.setSubject(request.getSubject());

        var updatedService = serviceRepository.save(existing);
        return new ServiceCatalogResponse(
                updatedService.getServiceId(),
                updatedService.getName(),
                updatedService.getDescription(),
                updatedService.getCategory(),
                updatedService.getSubCategory(),
                updatedService.getPrice(),
                updatedService.getPricingType(),
                updatedService.getAvailability(),
                updatedService.getIcon(),
                updatedService.getLevel(),
                updatedService.getSubject(),
                new ProviderInfo(
                        updatedService.getProvider().getUserId(),
                        updatedService.getProvider().getUsername(),
                        updatedService.getProvider().getEmail(),
                        updatedService.getProvider().getRole()
                ));
    }


}