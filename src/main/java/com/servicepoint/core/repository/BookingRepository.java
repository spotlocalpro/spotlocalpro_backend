package com.servicepoint.core.repository;
import com.servicepoint.core.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.sql.Timestamp;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Integer> {
    List<Booking> findByCustomerUserId(Integer customerId);
    List<Booking> findByProviderUserId(Integer providerId);

    // Returns true if provider already has a confirmed booking at this exact time
    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END FROM Booking b WHERE b.provider.userId = :providerId AND b.serviceDateTime = :serviceDateTime AND b.status = :status")
    boolean existsConflict(@Param("providerId") Integer providerId, @Param("serviceDateTime") Timestamp serviceDateTime, @Param("status") String status);

    // Returns all pending bookings for a provider at a given time (for auto-decline)
    @Query("SELECT b FROM Booking b WHERE b.provider.userId = :providerId AND b.serviceDateTime = :serviceDateTime AND b.status = :status")
    List<Booking> findConflicting(@Param("providerId") Integer providerId, @Param("serviceDateTime") Timestamp serviceDateTime, @Param("status") String status);
}
