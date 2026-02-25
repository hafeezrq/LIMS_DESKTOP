package com.qdc.lims.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Persistent per-department print progress for a lab order report.
 */
@Entity
@Table(name = "order_report_department_progress", uniqueConstraints = {
        @UniqueConstraint(name = "uk_order_report_department", columnNames = { "order_id", "department_name" })
})
@Getter
@Setter
public class OrderReportDepartmentProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private LabOrder labOrder;

    @Column(name = "department_name", nullable = false, length = 120)
    private String departmentName;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean printed = false;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private Integer printCount = 0;

    private LocalDateTime firstPrintedAt;
    private LocalDateTime lastPrintedAt;
    private String lastPrintedBy;
}

