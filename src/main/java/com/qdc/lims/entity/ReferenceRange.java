package com.qdc.lims.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Entity representing age and gender-specific reference ranges for lab test
 * results.
 * Used for automatic flagging of abnormal values based on patient demographics.
 */
@Entity
@Data
public class ReferenceRange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "test_id", nullable = false)
    private TestDefinition test;

    private String gender; // "Male", "Female", "Both"

    private Integer minAge; // e.g. 0
    private Integer maxAge; // e.g. 100 (in Years)

    private java.math.BigDecimal minVal; // The Low Limit
    private java.math.BigDecimal maxVal; // The High Limit
    private String referenceText; // Optional textual reference (e.g. "Pale white - Creamy white")
}
