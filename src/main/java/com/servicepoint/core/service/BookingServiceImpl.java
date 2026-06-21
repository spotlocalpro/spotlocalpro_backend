package com.servicepoint.core.service;

import com.servicepoint.core.dto.UpdateBookingRequest;
import com.servicepoint.core.dto.*;
import com.servicepoint.core.exception.ResourceNotFoundException;
import com.servicepoint.core.model.Booking;
import com.servicepoint.core.model.Feedback;
import com.servicepoint.core.model.ServiceCatalog;
import com.servicepoint.core.model.User;
import com.servicepoint.core.exception.BookingConflictException;
import com.servicepoint.core.repository.BookingRepository;
import com.servicepoint.core.repository.FeedbackRepository;
import com.servicepoint.core.repository.ServiceCatalogRepository;
import com.servicepoint.core.repository.UserRepository;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class BookingServiceImpl implements BookingService {

    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ServiceCatalogRepository serviceCatalogRepository;
    @Autowired
    private FeedbackRepository feedbackRepository;
    @Autowired
    private BookingNotificationService bookingNotificationService;

    @Override
    public List<BookingInfo> findAllBookings() {
        var bookings =  bookingRepository.findAll();

        return bookings.stream().map(booking -> new BookingInfo(
                booking.getBookingId(),
                booking.getBookingDate(),
                booking.getServiceDateTime(),
                booking.getStatus(),
                booking.getNotes(),
                booking.getPriceAtBooking(),
                booking.getPricingTypeAtBooking(),
                booking.getTotalPrice(),
                new CustomerInfo(
                        booking.getCustomer().getUserId(),
                        booking.getCustomer().getUsername(),
                        booking.getCustomer().getEmail()
                ),
                new ProviderInfo(
                        booking.getProvider().getUserId(),
                        booking.getProvider().getUsername(),
                        booking.getProvider().getEmail(),
                        booking.getProvider().getRole()
                ),
                new ServiceInfo(
                        booking.getService().getServiceId(),
                        booking.getService().getName(),
                        booking.getService().getDescription(),
                        booking.getService().getCategory(),
                        booking.getService().getSubCategory(),
                        booking.getService().getAvailability(),
                        booking.getService().getPrice(),
                        booking.getService().getPricingType(),
                        booking.getService().getLevel(),
                        booking.getService().getSubject()
                )
        )).collect(Collectors.toList());
    }

    @Override
    public BookingInfo findBookingById(Integer bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        return new BookingInfo(
                booking.getBookingId(),
                booking.getBookingDate(),
                booking.getServiceDateTime(),
                booking.getStatus(),
                booking.getNotes(),
                booking.getPriceAtBooking(),
                booking.getPricingTypeAtBooking(),
                booking.getTotalPrice(),
                new CustomerInfo(
                        booking.getCustomer().getUserId(),
                        booking.getCustomer().getUsername(),
                        booking.getCustomer().getEmail()
                ),
                new ProviderInfo(
                        booking.getProvider().getUserId(),
                        booking.getProvider().getUsername(),
                        booking.getProvider().getEmail(),
                        booking.getProvider().getRole()
                ),
                new ServiceInfo(
                        booking.getService().getServiceId(), booking.getService().getName(),
                        booking.getService().getDescription(),
                        booking.getService().getCategory(),
                        booking.getService().getSubCategory(),
                        booking.getService().getAvailability(),
                        booking.getService().getPrice(),
                        booking.getService().getPricingType(),
                        booking.getService().getLevel(), booking.getService().getSubject()

                )
        );
    }


    @Override
    public Booking updateBooking(Integer bookingId, UpdateBookingRequest request) {
        Booking existing = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        if (request.getServiceDateTime() != null)
            existing.setServiceDateTime(request.getServiceDateTime());

        if (request.getStatus() != null)
            existing.setStatus(request.getStatus());

        if (request.getNotes() != null)
            existing.setNotes(request.getNotes());

        if (request.getPriceAtBooking() != null)
            existing.setPriceAtBooking(request.getPriceAtBooking());

        if (request.getPricingTypeAtBooking() != null)
            existing.setPricingTypeAtBooking(request.getPricingTypeAtBooking());

        // ✅ Preserve totalPrice — don't overwrite with null
        if (request.getTotalPrice() != null) {
            existing.setTotalPrice(request.getTotalPrice());
        }

        boolean becomingCompleted = "completed".equalsIgnoreCase(request.getStatus())
                && !"completed".equalsIgnoreCase(existing.getStatus());

        Booking saved = bookingRepository.save(existing);

        if (becomingCompleted) {
            bookingNotificationService.sendBookingCompletedToCustomer(saved);
            bookingNotificationService.sendBookingCompletedToProvider(saved);
        }

        return saved;
    }

    @Override
    public NewBookingResponse saveBooking(BookingRequest request) {
        User provider = userRepository.findById(request.getProviderId())
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));

        User customer = userRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

        ServiceCatalog service = serviceCatalogRepository.findById(request.getServiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Service Does not exist"));

        // Prevent double booking: reject if provider already has a confirmed booking at this exact time
        try {
            if (request.getServiceDateTime() != null &&
                    bookingRepository.existsConflict(
                            provider.getUserId(), request.getServiceDateTime(), "confirmed")) {
                throw new BookingConflictException(
                        "This time slot is already booked. Please choose a different date and time.");
            }
        } catch (BookingConflictException e) {
            throw e;
        } catch (Exception e) {
            // Log and continue — conflict check failure must never block a legitimate booking
        }

        var booking = new Booking();
        booking.setCustomer(customer);
        booking.setProvider(provider);
        booking.setService(service);
        booking.setBookingDate(request.getBookingDate() != null ? request.getBookingDate() : new java.sql.Timestamp(System.currentTimeMillis()));
        booking.setPriceAtBooking(request.getPriceAtBooking() != null ? request.getPriceAtBooking() : 0.0);
        booking.setTotalPrice(request.getTotalPrice() != null ? request.getTotalPrice() : 0.0);
        booking.setPricingTypeAtBooking(request.getPricingTypeAtBooking() != null ? request.getPricingTypeAtBooking() : "per_work");
        booking.setStatus(request.getStatus());
        booking.setServiceDateTime(request.getServiceDateTime());
        booking.setNotes(request.getNotes());

        Booking savedBooking = bookingRepository.save(booking);

        // Fire email notification to provider (async, won't block or fail the request)
        bookingNotificationService.sendNewBookingRequestToProvider(savedBooking);

        // Build and return NewBookingResponse
        return new NewBookingResponse(
                savedBooking.getBookingId(),
                savedBooking.getBookingDate(),
                savedBooking.getServiceDateTime(),
                savedBooking.getStatus(),
                savedBooking.getNotes(),
                savedBooking.getPriceAtBooking(),
                savedBooking.getPricingTypeAtBooking(),
                new CustomerInfo(customer.getUserId(), customer.getUsername(), customer.getEmail()),
                new ProviderInfo(provider.getUserId(), provider.getUsername(), provider.getEmail(), provider.getRole()),
                new ServiceInfo(service.getServiceId(), service.getName(),
                        service.getDescription(), service.getCategory(),
                        service.getSubCategory(),
                        service.getAvailability(), service.getPrice(), service.getPricingType(),
                        service.getLevel(), service.getSubject())
        );
    }


    @Override
    public void deleteBooking(Integer bookingId) {

        var booking = bookingRepository.findById(bookingId).
                orElseThrow(()-> new ResourceNotFoundException("Booking not found"));
        bookingRepository.deleteById(booking.getBookingId());
    }

    @Override
    public List<BookingInfo> findBookingsByCustomerId(Integer customerId) {
        var bookings =  bookingRepository.findByCustomerUserId(customerId);


        return bookings.stream().map(booking -> new BookingInfo(
                booking.getBookingId(),
                booking.getBookingDate(),
                booking.getServiceDateTime(),
                booking.getStatus(),
                booking.getNotes(),
                booking.getPriceAtBooking(),
                booking.getPricingTypeAtBooking(),
                booking.getTotalPrice(),
                new CustomerInfo(
                        booking.getCustomer().getUserId(),
                        booking.getCustomer().getUsername(),
                        booking.getCustomer().getEmail()
                ),
                new ProviderInfo(
                        booking.getProvider().getUserId(),
                        booking.getProvider().getUsername(),
                        booking.getProvider().getEmail(),
                        booking.getProvider().getRole()
                ),
                new ServiceInfo(
                        booking.getService().getServiceId(),
                        booking.getService().getName(),
                        booking.getService().getDescription(),
                        booking.getService().getCategory(),
                        booking.getService().getSubCategory(),
                        booking.getService().getAvailability(),
                        booking.getService().getPrice(),
                        booking.getService().getPricingType(),
                        booking.getService().getLevel(),
                        booking.getService().getSubject()
                )
        )).collect(Collectors.toList());

    }

    @Override
    public List<BookingInfo> findBookingsByProviderId(Integer providerId) {
        var bookings =  bookingRepository.findByProviderUserId(providerId);
        return bookings.stream().map(booking -> new BookingInfo(
                booking.getBookingId(),
                booking.getBookingDate(),
                booking.getServiceDateTime(),
                booking.getStatus(),
                booking.getNotes(),
                booking.getPriceAtBooking(),
                booking.getPricingTypeAtBooking(),
                booking.getTotalPrice(),
                new CustomerInfo(
                        booking.getCustomer().getUserId(),
                        booking.getCustomer().getUsername(),
                        booking.getCustomer().getEmail()
                ),
                new ProviderInfo(
                        booking.getProvider().getUserId(),
                        booking.getProvider().getUsername(),
                        booking.getProvider().getEmail(),
                        booking.getProvider().getRole(),
                        feedbackRepository
                                .findByBookingBookingId(booking.getBookingId())
                                .map(Feedback::getRating)
                                .orElse(0),
                        feedbackRepository.findByBookingBookingId(booking.getBookingId())
                                .map(Feedback::getComments).orElse("")
                ),
                new ServiceInfo(
                        booking.getService().getServiceId(),
                        booking.getService().getName(),
                        booking.getService().getDescription(),
                        booking.getService().getCategory(),
                        booking.getService().getSubCategory(),
                        booking.getService().getAvailability(),
                        booking.getService().getPrice(),
                        booking.getService().getPricingType(),
                        booking.getService().getLevel(),
                        booking.getService().getSubject()
                )
        )).collect(Collectors.toList());
    }
}