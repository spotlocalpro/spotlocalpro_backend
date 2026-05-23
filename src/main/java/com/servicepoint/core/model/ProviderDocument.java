package com.servicepoint.core.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.sql.Timestamp;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "provider_documents")
public class ProviderDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer documentId;

    @ManyToOne
    @JoinColumn(name = "registration_id", nullable = false)
    private ProviderRegistration registration;

    // Remove @Lob — it causes issues with PostgreSQL
    @Column(name = "file_data", columnDefinition = "BYTEA")
    private byte[] fileData;



    @Column(nullable = false)
    private String documentType; // "certificate", "id_proof", "other"

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String fileUrl; // local storage URL

    @Column(nullable = false)
    private Long fileSize;

    @CreationTimestamp
    @Column(updatable = false)
    private Timestamp uploadedAt;
}