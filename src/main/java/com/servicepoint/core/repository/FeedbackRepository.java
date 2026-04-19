package com.servicepoint.core.repository;

import com.servicepoint.core.dto.FeedbackResponse;
import com.servicepoint.core.model.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback, Integer> {
    // ✅ These return FeedbackResponse via Spring Data projection
    List<FeedbackResponse> findByProviderUserId(Integer providerId);
    List<FeedbackResponse> findByCustomerUserId(Integer customerId);

    // ✅ Add this for recalculating rating — returns Feedback entities
    List<Feedback> findByProviderUserIdAndRatingIsNotNull(Integer providerId);

    Optional<Feedback> findByBookingBookingId(Integer bookingId);
}