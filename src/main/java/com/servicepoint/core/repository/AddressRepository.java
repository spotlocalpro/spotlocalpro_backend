package com.servicepoint.core.repository;

import com.servicepoint.core.model.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AddressRepository extends JpaRepository<Address, Integer> {
    List<Address> findByUser(Integer userId);
}