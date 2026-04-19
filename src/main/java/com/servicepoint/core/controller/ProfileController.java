package com.servicepoint.core.controller;

import com.servicepoint.core.dto.ErrorResponse;
import com.servicepoint.core.model.Address;
import com.servicepoint.core.model.User;
import com.servicepoint.core.repository.AddressRepository;
import com.servicepoint.core.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class ProfileController {

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private UserRepository userRepository;


    // GET all addresses for a user
    @GetMapping("/{userId}/addresses")
    public ResponseEntity<?> getUserAddresses(@PathVariable Integer userId) {
        try {
            List<Address> addresses = addressRepository.findByUser(userId);
            return ResponseEntity.ok(addresses);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch addresses"));
        }
    }

    // POST — add new address
    @PostMapping("/{userId}/addresses")
    public ResponseEntity<?> addAddress(
            @PathVariable Integer userId,
            @RequestBody Address address) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            address.setUser(user.getUserId());

            if (Boolean.TRUE.equals(address.getIsDefault())) {
                addressRepository.findByUser(userId)
                        .forEach(a -> {
                            a.setIsDefault(false);
                            addressRepository.save(a);
                        });
            }

            if (addressRepository.findByUser(userId).isEmpty()) {
                address.setIsDefault(true);
            }

            Address saved = addressRepository.save(address);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to add address: " + e.getMessage()));
        }
    }

    // PUT — update existing address
    @PutMapping("/{userId}/addresses/{addressId}")
    public ResponseEntity<?> updateAddress(
            @PathVariable Integer userId,
            @PathVariable Integer addressId,
            @RequestBody Address updatedAddress) {
        try {
            Address existing = addressRepository.findById(addressId)
                    .orElseThrow(() -> new RuntimeException("Address not found"));

            existing.setLabel(updatedAddress.getLabel());
            existing.setStreetAddress(updatedAddress.getStreetAddress());
            existing.setCity(updatedAddress.getCity());
            existing.setState(updatedAddress.getState());
            existing.setZipCode(updatedAddress.getZipCode());
            existing.setCountry(updatedAddress.getCountry());

            Address saved = addressRepository.save(existing);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to update address: " + e.getMessage()));
        }
    }

    // DELETE — remove address
    @DeleteMapping("/{userId}/addresses/{addressId}")
    public ResponseEntity<?> deleteAddress(
            @PathVariable Integer userId,
            @PathVariable Integer addressId) {
        try {
            Address address = addressRepository.findById(addressId)
                    .orElseThrow(() -> new RuntimeException("Address not found"));

            boolean wasDefault = Boolean.TRUE.equals(address.getIsDefault());
            addressRepository.delete(address);

            if (wasDefault) {
                List<Address> remaining = addressRepository.findByUser(userId);
                if (!remaining.isEmpty()) {
                    remaining.get(0).setIsDefault(true);
                    addressRepository.save(remaining.get(0));
                }
            }

            return ResponseEntity.ok(Map.of("message", "Address deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to delete address: " + e.getMessage()));
        }
    }

    // PUT — set address as default
    @PutMapping("/{userId}/addresses/{addressId}/default")
    public ResponseEntity<?> setDefaultAddress(
            @PathVariable Integer userId,
            @PathVariable Integer addressId) {
        try {
            addressRepository.findByUser(userId)
                    .forEach(a -> {
                        a.setIsDefault(false);
                        addressRepository.save(a);
                    });

            Address address = addressRepository.findById(addressId)
                    .orElseThrow(() -> new RuntimeException("Address not found"));
            address.setIsDefault(true);
            addressRepository.save(address);

            return ResponseEntity.ok(Map.of("message", "Default address updated"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to set default address: " + e.getMessage()));
        }
    }
}