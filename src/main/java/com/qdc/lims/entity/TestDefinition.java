package com.qdc.lims.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Entity
@Table(name = "test_definition")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String testName;

    private String shortCode;

    @ManyToOne
    @JoinColumn(name = "department_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Department department;

    @ManyToOne
    @JoinColumn(name = "category_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private TestCategory category;

    private String unit;
    private BigDecimal minRange;
    private BigDecimal maxRange;
    private BigDecimal price;

    @Builder.Default
    private Boolean active = true;

    // NEW FIELDS
    @Builder.Default
    @Column(name = "manual_price_required", nullable = false)
    private Boolean manualPriceRequired = false;

    @Builder.Default
    @Column(name = "skip_worklist", nullable = false)
    private Boolean skipWorklist = false;

    @ManyToMany(mappedBy = "tests")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Panel> panels;

    @OneToMany(mappedBy = "test", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("minAge ASC, maxAge ASC")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<ReferenceRange> ranges;
}