package com.qdc.lims.repository;

import com.qdc.lims.entity.LabResult;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Repository interface for LabResult entities, providing CRUD operations for
 * lab test results.
 */
public interface LabResultRepository extends JpaRepository<LabResult, Long> {
    /**
     * Finds only the tests that ARE NOT marked to skip the worklist.
     * This hides ECG, X-Ray, and Outsourced tests from the Lab Tech.
     */
    @Query("SELECT r FROM LabResult r " +
            "JOIN r.testDefinition td " +
            "WHERE r.status = 'PENDING' " +
            "AND td.skipWorklist = false " +
            "ORDER BY r.labOrder.orderDate ASC")
    List<LabResult> findWorklistItems();

}
