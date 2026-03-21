package com.qdc.lims.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Configurable allowed result values for a given test definition.
 */
@Entity
@Table(name = "test_result_option", indexes = {
        @Index(name = "idx_test_result_option_test", columnList = "test_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_test_result_option_test_code", columnNames = { "test_id", "option_code" })
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestResultOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private TestDefinition test;

    @Column(name = "option_code", nullable = false, length = 64)
    private String optionCode;

    @Column(name = "option_label", nullable = false, length = 120)
    private String optionLabel;

    @Builder.Default
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;
}
