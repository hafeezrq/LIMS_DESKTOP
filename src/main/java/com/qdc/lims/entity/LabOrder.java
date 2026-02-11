package com.qdc.lims.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.Hibernate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a laboratory order, including patient, doctor, billing,
 * and result details.
 */
@Entity
@Getter
@Setter
public class LabOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    // Link to the Patient
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "patient_id", nullable = false)
    @NotNull(message = "Patient is required")
    private Patient patient;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "doctor_id")
    private Doctor referringDoctor; // Visible to Receptionist ("Ref By: Dr. Bilal")

    private LocalDateTime orderDate;
    @NotBlank(message = "Status is required")
    private String status; // "PENDING", "COMPLETED"
    @Column(precision = 19, scale = 4)
    private BigDecimal totalAmount; // Calculated automatically

    // --- REPORT DELIVERY STATUS ---
    private boolean isReportDelivered = false; // False = In Lab/Rack, True = With Patient

    private LocalDateTime deliveryDate; // When was it handed over?

    // --- RESULT EDIT / REPRINT AUDIT ---
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean resultsEdited = false;
    private LocalDateTime resultsEditedAt;
    private String resultsEditedBy;
    private String resultsEditReason;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean reprintRequired = false;
    @Column(columnDefinition = "integer default 0")
    private Integer reprintCount = 0;
    private LocalDateTime lastReprintAt;
    private String lastReprintBy;

    // ---------- Update: To incorporate accounting ----------//
    @Column(precision = 19, scale = 4)
    private BigDecimal discountAmount = BigDecimal.ZERO; // e.g. 100
    @Column(precision = 19, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO; // (Optional, usually 0 in labs)
    @Column(precision = 19, scale = 4)
    private BigDecimal paidAmount = BigDecimal.ZERO; // e.g. 500 (Patient paid half)
    @Column(precision = 19, scale = 4)
    private BigDecimal balanceDue = BigDecimal.ZERO; // e.g. 400 (Remaining)
    private LocalDateTime labStartedAt;

    // One Order = Many Tests (Results)
    // "CascadeType.ALL" means if we save the Order, it auto-saves the Result rows
    // too.
    // EAGER fetch to avoid LazyInitializationException in JavaFX controllers
    @OneToMany(mappedBy = "labOrder", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<LabResult> results = new ArrayList<>();

    /**
     * Panels billed for this order (optional).
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "lab_order_panel",
            joinColumns = @JoinColumn(name = "lab_order_id"),
            inverseJoinColumns = @JoinColumn(name = "panel_id"))
    private List<Panel> panels = new ArrayList<>();

    /**
     * Sets the order date and initial status before persisting the entity.
     */
    @PrePersist
    protected void onCreate() {
        this.orderDate = LocalDateTime.now();
        this.status = "PENDING";
    }

    /**
     * Calculates the balance due before updating the entity.
     * Sets balanceDue = totalAmount - discountAmount - paidAmount.
     */
    @PreUpdate
    public void calculateBalance() {
        if (discountAmount == null) {
            discountAmount = BigDecimal.ZERO;
        }
        if (paidAmount == null) {
            paidAmount = BigDecimal.ZERO;
        }
        if (totalAmount == null) {
            totalAmount = BigDecimal.ZERO;
        }

        this.balanceDue = totalAmount.subtract(discountAmount).subtract(paidAmount);
    }

    public long getPendingTestCount() {
        if (results == null)
            return 0;
        return results.stream()
                .filter(r -> "PENDING".equalsIgnoreCase(r.getStatus()))
                .count();
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        if (Hibernate.getClass(this) != Hibernate.getClass(o)) {
            return false;
        }
        LabOrder other = (LabOrder) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public final int hashCode() {
        return getClass().hashCode();
    }

}
