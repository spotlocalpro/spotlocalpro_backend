package com.servicepoint.core.controller;

import com.servicepoint.core.dto.FeedbackRequest;
import com.servicepoint.core.dto.FeedbackResponse;
import com.servicepoint.core.service.FeedbackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/feedback")
@CrossOrigin(origins = "*")
public class FeedbackController {

    @Autowired
    private FeedbackService feedbackService;



    /**
     * Get all feedback OR filter by customer_id or provider_id using query parameters
     * Examples:
     * GET /api/feedback - returns all feedback
     * GET /api/feedback?customer_id=123 - returns feedback for customer 123
     * GET /api/feedback?provider_id=123 - returns feedback for provider 123
     *
     * @param customerId Optional customer ID filter
     * @param providerId Optional provider ID filter
     * @return List of feedback responses
     */
    @GetMapping
    public ResponseEntity<List<FeedbackResponse>> getFeedback(
            @RequestParam(value = "customer_id", required = false) Integer customerId,
            @RequestParam(value = "provider_id", required = false) Integer providerId) {

        // If customer_id is provided, filter by customer
        if (customerId != null) {
            List<FeedbackResponse> customerFeedback = feedbackService.findFeedbackByCustomerId(customerId);
            return ResponseEntity.ok(customerFeedback);
        }

        // If provider_id is provided, filter by provider
        if (providerId != null) {
            List<FeedbackResponse> providerFeedback = feedbackService.findFeedbackByProviderId(providerId);
            return ResponseEntity.ok(providerFeedback);
        }

        // If no filters, return all feedback
        List<FeedbackResponse> feedbackList = feedbackService.fetchAllFeedback();
        return ResponseEntity.ok(feedbackList);
    }

    /**
     * Get feedback by ID
     * @param feedbackId The ID of the feedback to retrieve
     * @return FeedbackResponse or 404 if not found
     */
    @GetMapping("/{feedbackId}")
    public ResponseEntity<FeedbackResponse> getFeedbackById(@PathVariable Integer feedbackId) {
        FeedbackResponse feedback = feedbackService.findFeedbackById(feedbackId);
        return ResponseEntity.ok(feedback);
    }

    /**
     * Create new feedback
     * @param feedbackRequest The feedback data to create
     * @return Created feedback response
     */
    @PostMapping
    public ResponseEntity<FeedbackResponse> createFeedback(@Valid @RequestBody FeedbackRequest feedbackRequest) {
        FeedbackResponse createdFeedback = feedbackService.saveFeedback(feedbackRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdFeedback);
    }

    /**
     * Delete feedback by ID
     * @param feedbackId The ID of the feedback to delete
     * @return 204 No Content on successful deletion
     */
    @DeleteMapping("/{feedbackId}")
    public ResponseEntity<Void> deleteFeedback(@PathVariable Integer feedbackId) {
        feedbackService.deleteFeedback(feedbackId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get feedback by booking ID
     * @param bookingId The booking ID to filter feedback by
     * @return List of feedback for the specific booking
     */
    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<List<FeedbackResponse>> getFeedbackByBooking(
            @PathVariable Integer bookingId) {
        List<FeedbackResponse> bookingFeedback =
                feedbackService.findFeedbackByBookingId(bookingId);
        return ResponseEntity.ok(bookingFeedback);
    }
}