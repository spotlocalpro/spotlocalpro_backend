package com.servicepoint.core.service;

import com.servicepoint.core.dto.*;
import com.servicepoint.core.exception.ResourceNotFoundException;
import com.servicepoint.core.model.Feedback;
import com.servicepoint.core.model.User;
import com.servicepoint.core.repository.BookingRepository;
import com.servicepoint.core.repository.FeedbackRepository;
import com.servicepoint.core.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class FeedbackServieImpl implements FeedbackService {

    @Autowired
    FeedbackRepository feedbackRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    BookingRepository bookingRepository;

    @Override
    public List<FeedbackResponse> fetchAllFeedback() {
        return toFeedBackResponse(feedbackRepository.findAll());
    }

    @Override
    public FeedbackResponse findFeedbackById(Integer feedbackId) {
        return feedbackRepository.findById(feedbackId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Feedback not found"));
    }

    @Override
    public FeedbackResponse saveFeedback(FeedbackRequest request) {
        User provider = userRepository.findById(request.getProviderId())
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));

        User customer = userRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

        var booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        var newFeedback = new Feedback();
        newFeedback.setBooking(booking);
        newFeedback.setCustomer(customer);
        newFeedback.setProvider(provider);
        newFeedback.setComments(request.getComments());
        newFeedback.setRating(request.getRating()); // ✅ set rating
        newFeedback.setSubmissionDate(
                request.getSubmissionDate() != null
                        ? request.getSubmissionDate()
                        : Timestamp.valueOf(LocalDateTime.now())
        );

        var savedFeedback = feedbackRepository.save(newFeedback);

        // ✅ Recalculate provider average rating
        recalculateProviderRating(provider);

        return toResponse(savedFeedback);
    }

    @Override
    public void deleteFeedback(Integer feedbackId) {
        var feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new ResourceNotFoundException("Feedback not found"));
        feedbackRepository.delete(feedback);
    }

    @Override
    public List<FeedbackResponse> findFeedbackByCustomerId(Integer customerId) {
        userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        return feedbackRepository.findByCustomerUserId(customerId);
    }

    @Override
    public List<FeedbackResponse> findFeedbackByProviderId(Integer providerId) {
        userRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));
        return feedbackRepository.findByProviderUserId(providerId);
    }

    @Override
    public List<FeedbackResponse> findFeedbackByBookingId(Integer bookingId) {
        Optional<Feedback> feedback = feedbackRepository.findByBookingBookingId(bookingId);
        // ✅ list with one item
        // ✅ empty list not [null]
        return feedback.map(value -> Collections.singletonList(toResponse(value))).orElse(Collections.emptyList());
    }

    // ✅ Recalculate provider average rating from all their feedback
    private void recalculateProviderRating(User provider) {
        List<Feedback> allFeedback = feedbackRepository
                .findAll()
                .stream()
                .filter(f -> f.getProvider().getUserId().equals(provider.getUserId()))
                .collect(Collectors.toList());

        if (!allFeedback.isEmpty()) {
            double avgRating = allFeedback.stream()
                    .mapToInt(Feedback::getRating)
                    .average()
                    .orElse(0.0);

            provider.setRating(avgRating);
            provider.setReviewCount(allFeedback.size());
            userRepository.save(provider);
        }
    }

    // ✅ Single mapping method used everywhere
    private FeedbackResponse toResponse(Feedback f) {
        return new FeedbackResponse(
                new SimpleBookingInfo(
                        f.getBooking().getBookingId(),
                        f.getBooking().getBookingDate(),
                        f.getBooking().getServiceDateTime(),
                        f.getBooking().getStatus(),
                        f.getBooking().getNotes(),
                        f.getBooking().getPriceAtBooking(),
                        f.getBooking().getPricingTypeAtBooking()
                ),
                new CustomerInfo(
                        f.getCustomer().getUserId(),
                        f.getCustomer().getUsername(),
                        f.getCustomer().getEmail()
                ),
                new ProviderInfo(
                        f.getProvider().getUserId(),
                        f.getProvider().getUsername(),
                        f.getProvider().getEmail(),
                        f.getProvider().getRole()
                ),
                f.getComments(),
                f.getRating(),           // ✅ include rating
                f.getSubmissionDate()    // ✅ include date
        );
    }

    private List<FeedbackResponse> toFeedBackResponse(List<Feedback> allFeedback) {
        return allFeedback.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
}