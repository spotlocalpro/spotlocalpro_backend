package com.servicepoint.core.controller;

import com.servicepoint.core.dto.NewServiceRequest;
import com.servicepoint.core.dto.ServiceCatalogResponse;
import com.servicepoint.core.dto.UpdateServiceRequest;
import com.servicepoint.core.model.ServiceCatalog;
import com.servicepoint.core.model.User;
import com.servicepoint.core.repository.UserRepository;
import com.servicepoint.core.service.ServiceCatalogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/services")
public class ServiceController {

    @Autowired
    private ServiceCatalogService serviceCatalogService;
    @Autowired
    private UserRepository userRepository;

    @GetMapping
    public List<ServiceCatalogResponse> getAllServices() {
        return serviceCatalogService.findAllServices();
    }

    @GetMapping("/{serviceId}")
    public ResponseEntity<List<ServiceCatalogResponse>> getServiceById(@PathVariable Integer serviceId) {
        List<ServiceCatalogResponse> services = serviceCatalogService.findServiceById(serviceId);
        return ResponseEntity.ok(services);
    }

    @PostMapping
    public ResponseEntity<ServiceCatalogResponse> createService(@RequestBody NewServiceRequest service) {

      //  log.debug(service.getName());
        var createdService =  serviceCatalogService.saveService(service);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(createdService.serviceId() )
                .toUri();

        return ResponseEntity.created(location).body(createdService);
    }
    @PutMapping("/{serviceId}")
    public ResponseEntity<ServiceCatalogResponse> updateService(
            @PathVariable Integer serviceId,
            @RequestBody UpdateServiceRequest request) {
        ServiceCatalogResponse updatedService = serviceCatalogService.updateService(serviceId, request);
        return ResponseEntity.ok(updatedService);
    }


    @DeleteMapping("/{serviceId}")
    public ResponseEntity<Void> deleteService(@PathVariable Integer serviceId) {

        serviceCatalogService.deleteService(serviceId);
        return ResponseEntity.noContent().build();
    }


    @GetMapping("/provider/{providerId}")
    public ResponseEntity<Map<String, Object>> getServicesByProviderId(
            @PathVariable Integer providerId) {
        List<ServiceCatalog> services = serviceCatalogService.findServicesByProviderId(providerId);

        List<Map<String, Object>> serviceList = services.stream()
                .map(service -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("serviceId", service.getServiceId());
                    map.put("name", service.getName());
                    return map;
                })
                .toList();

        Map<String, Object> response = new HashMap<>();
        response.put("services", serviceList);

        return ResponseEntity.ok(response);
    }

}
