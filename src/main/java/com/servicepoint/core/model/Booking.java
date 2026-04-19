package com.servicepoint.core.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.sql.Timestamp;
import java.time.LocalDate;


@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer bookingId;

    public Integer getBookingId() {
		return bookingId;
	}

	public void setBookingId(Integer bookingId) {
		this.bookingId = bookingId;
	}

	public User getCustomer() {
		return customer;
	}

	public void setCustomer(User customer) {
		this.customer = customer;
	}

	public User getProvider() {
		return provider;
	}

	public void setProvider(User provider) {
		this.provider = provider;
	}

	public ServiceCatalog getService() {
		return service;
	}

	public void setService(ServiceCatalog service) {
		this.service = service;
	}

	public Timestamp getBookingDate() {
		return bookingDate;
	}

	public void setBookingDate(Timestamp bookingDate) {
		this.bookingDate = bookingDate;
	}

	public Timestamp getServiceDateTime() {
		return serviceDateTime;
	}

	public void setServiceDateTime(Timestamp serviceDateTime) {
		this.serviceDateTime = serviceDateTime;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Double getPriceAtBooking() {
		return priceAtBooking;
	}

	public void setPriceAtBooking(Double priceAtBooking) {
		this.priceAtBooking = priceAtBooking;
	}

	public String getPricingTypeAtBooking() {
		return pricingTypeAtBooking;
	}

	public void setPricingTypeAtBooking(String pricingTypeAtBooking) {
		this.pricingTypeAtBooking = pricingTypeAtBooking;
	}

	public Double getTotalPrice() {
		return totalPrice;
	}

	public void setTotalPrice(Double totalPrice) {
		this.totalPrice = totalPrice;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public String getPaymentStatus() {
		return paymentStatus;
	}

	public void setPaymentStatus(String paymentStatus) {
		this.paymentStatus = paymentStatus;
	}

	public String getStripeSessionId() {
		return stripeSessionId;
	}

	public void setStripeSessionId(String stripeSessionId) {
		this.stripeSessionId = stripeSessionId;
	}

	public String getStripePaymentIntentId() {
		return stripePaymentIntentId;
	}

	public void setStripePaymentIntentId(String stripePaymentIntentId) {
		this.stripePaymentIntentId = stripePaymentIntentId;
	}

	public Timestamp getPaidAt() {
		return paidAt;
	}

	public void setPaidAt(Timestamp paidAt) {
		this.paidAt = paidAt;
	}

	public Timestamp getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Timestamp createdAt) {
		this.createdAt = createdAt;
	}

	public Timestamp getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Timestamp updatedAt) {
		this.updatedAt = updatedAt;
	}

	@ManyToOne
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne
    @JoinColumn(name = "provider_id", nullable = false)
    private User provider;

    @ManyToOne
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceCatalog service;

    @Column(nullable = false)
    private Timestamp bookingDate;

    @Column(nullable = false)
    private Timestamp serviceDateTime;

    @Column(nullable = false)
    private String status; // pending, confirmed, paid, in_progress, completed, cancelled

    // Snapshot of pricing at time of booking (important for historical records)
    @Column(nullable = false)
    private Double priceAtBooking;

    @Column(nullable = false)
    private String pricingTypeAtBooking; // hourly, per_work

    // Total price calculation (can include hours, additional fees, etc.)
    @Column(nullable = false, columnDefinition = "DOUBLE PRECISION DEFAULT 0.0")
    private Double totalPrice = 0.0;

    private String notes;

    // Payment-related fields
    @Column(nullable = false)
    private String paymentStatus = "pending"; // pending, completed, failed, cancelled, refunded

    private String stripeSessionId;
    private String stripePaymentIntentId;
    private Timestamp paidAt;

    @CreationTimestamp
    @Column(updatable = false)
    private Timestamp createdAt;

    private Timestamp updatedAt;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = new Timestamp(System.currentTimeMillis());
    }

    @PrePersist
    protected void onCreate() {
        if (totalPrice == null) {
            totalPrice = 0.0;
        }
        if (paymentStatus == null) {
            paymentStatus = "pending";
        }

    }
}