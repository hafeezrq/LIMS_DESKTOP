package com.qdc.lims.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

/**
 * Entity representing a patient, including demographic and registration details.
 */
@Entity
@Data
@Table(name = "patients", indexes = {
        @Index(name = "idx_mrn", columnList = "mrn"),
        @Index(name = "idx_cnic", columnList = "cnic")
})
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(nullable = false, unique = true, updatable = false)
    @NotBlank(message = "MRN is required")
    private String mrn;

    @Column(unique = true)
    private String cnic;

    @Column(nullable = false)
    @NotBlank(message = "Full name is required")
    private String fullName;

    // --- NEW FIELDS ---

    @NotNull(message = "Age is required")
    @Column(nullable = false)
    private Integer age; // Stores years (e.g., 25)
    private String ageUnit; // "Years", "Months", or "Days"

    private String city; // e.g., "Lahore", "Village 45GB"

    // ------------------

    private String mobileNumber;

    // We can keep DOB as optional, or remove it. Leaving it optional for now.
    private LocalDate dateOfBirth;

    private String gender;

    private LocalDate registrationDate;

    /**
     * Sets the registration date before persisting the entity.
     */
    @PrePersist
    protected void onCreate() {
        this.registrationDate = LocalDate.now();
    }
}
