package com.servicepoint.core.service;

import com.servicepoint.core.dto.*;

import java.util.List;

public interface FeedbackService {
    List<FeedbackResponse> fetchAllFeedback();
    FeedbackResponse findFeedbackById(Integer feedbackId);
//    Feedback updateFeedback(Integer feedbackId, UpdateBookingRequest request);
FeedbackResponse saveFeedback(FeedbackRequest feedback);
    void deleteFeedback(Integer feedbackId);
    List<FeedbackResponse>  findFeedbackByCustomerId(Integer customerId);
    List<FeedbackResponse>  findFeedbackByProviderId(Integer providerId);
    List<FeedbackResponse> findFeedbackByBookingId(Integer bookingId);
}
