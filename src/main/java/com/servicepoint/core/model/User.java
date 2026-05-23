package com.servicepoint.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.sql.Timestamp;
import java.util.List;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "users")
public class User {

	public String getBio(){return bio;}

	public void setBio(String bio){this.bio=bio;}

    public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public String getProfilePicture() {
		return profilePicture;
	}

	public void setProfilePicture(String profilePicture) {
		this.profilePicture = profilePicture;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public Double getLatitude() {
		return latitude;
	}

	public void setLatitude(Double latitude) {
		this.latitude = latitude;
	}

	public Double getLongitude() {
		return longitude;
	}

	public void setLongitude(Double longitude) {
		this.longitude = longitude;
	}

	public String getPhoneNumber() {
		return phoneNumber;
	}

	public void setPhoneNumber(String phoneNumber) {
		this.phoneNumber = phoneNumber;
	}

	public Double getRating() {
		return rating;
	}

	public void setRating(Double rating) {
		this.rating = rating;
	}

	public Integer getReviewCount() {
		return reviewCount;
	}

	public void setReviewCount(Integer reviewCount) {
		this.reviewCount = reviewCount;
	}

	public Timestamp getLastLogin() {
		return lastLogin;
	}

	public void setLastLogin(Timestamp lastLogin) {
		this.lastLogin = lastLogin;
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

	public List<Session> getSessions() {
		return sessions;
	}

	public void setSessions(List<Session> sessions) {
		this.sessions = sessions;
	}

	public List<Address> getAddresses() {
		return addresses;
	}

	public void setAddresses(List<Address> addresses) {
		this.addresses = addresses;
	}

	public PaymentPreference getPaymentPreferences() {
		return paymentPreferences;
	}

	public void setPaymentPreferences(PaymentPreference paymentPreferences) {
		this.paymentPreferences = paymentPreferences;
	}

	public CommunicationPreferences getCommunicationPreferences() {
		return communicationPreferences;
	}

	public void setCommunicationPreferences(CommunicationPreferences communicationPreferences) {
		this.communicationPreferences = communicationPreferences;
	}

	public List<ServiceCatalog> getServices() {
		return services;
	}

	public void setServices(List<ServiceCatalog> services) {
		this.services = services;
	}

	public List<Booking> getCustomerBookings() {
		return customerBookings;
	}

	public void setCustomerBookings(List<Booking> customerBookings) {
		this.customerBookings = customerBookings;
	}

	public List<Booking> getProviderBookings() {
		return providerBookings;
	}

	public void setProviderBookings(List<Booking> providerBookings) {
		this.providerBookings = providerBookings;
	}

	public List<Feedback> getCustomerFeedbacks() {
		return customerFeedbacks;
	}

	public void setCustomerFeedbacks(List<Feedback> customerFeedbacks) {
		this.customerFeedbacks = customerFeedbacks;
	}

	public List<Feedback> getProviderFeedbacks() {
		return providerFeedbacks;
	}

	public void setProviderFeedbacks(List<Feedback> providerFeedbacks) {
		this.providerFeedbacks = providerFeedbacks;
	}

	public Integer getUserId() {
		return userId;
	}

	public void setRole(String role) {
		this.role = role;
	}

	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer userId;

    @Column(unique = true, nullable = false)
    private String username;

	@Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String role; // customer or provider

    private String profilePicture;

    private String location;

    private Double latitude;

    private Double longitude;

    private String phoneNumber;

    private Double rating;

    private Integer reviewCount;

	private String bio;

    // REMOVED: private Double distanceMiles;

    private Timestamp lastLogin;

    @CreationTimestamp
    @Column(updatable = false)
    private Timestamp createdAt;

    @UpdateTimestamp
    private Timestamp updatedAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    @JsonIgnore
    private List<Session> sessions;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<Address> addresses;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private PaymentPreference paymentPreferences;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private CommunicationPreferences communicationPreferences;

    @OneToMany(mappedBy = "provider", cascade = CascadeType.ALL)
    @JsonIgnore
    private List<ServiceCatalog> services;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL)
    private List<Booking> customerBookings;

    @OneToMany(mappedBy = "provider", cascade = CascadeType.ALL)
    private List<Booking> providerBookings;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL)
    private List<Feedback> customerFeedbacks;

    @OneToMany(mappedBy = "provider", cascade = CascadeType.ALL)
    private List<Feedback> providerFeedbacks;

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getRole() {
        return this.role;
    }
}