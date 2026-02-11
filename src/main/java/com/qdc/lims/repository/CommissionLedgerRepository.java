package com.qdc.lims.repository;

import com.qdc.lims.entity.CommissionLedger;
import com.qdc.lims.entity.Doctor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository interface for CommissionLedger entities, providing queries for
 * commission tracking.
 */
public interface CommissionLedgerRepository extends JpaRepository<CommissionLedger, Long> {

    /**
     * Finds all commission records for a specific doctor with the given status.
     *
     * @param doctorId the ID of the doctor
     * @param status   the payment status (e.g., "UNPAID", "PAID")
     * @return list of CommissionLedger entries
     */
    List<CommissionLedger> findByDoctorIdAndStatus(Long doctorId, String status);

    /**
     * Finds all commission records with the given status across all doctors.
     *
     * @param status the payment status (e.g., "UNPAID", "PAID")
     * @return list of CommissionLedger entries
     */
    List<CommissionLedger> findByStatus(String status);

    /**
     * Finds all commission records for a specific doctor.
     *
     * @param doctorId the ID of the doctor
     * @return list of CommissionLedger entries
     */
    List<CommissionLedger> findByDoctorId(Long doctorId);

    /**
     * Finds all commission records for a specific doctor.
     *
     * @param doctor the doctor entity
     * @return list of CommissionLedger entries
     */
    List<CommissionLedger> findByDoctor(Doctor doctor);

    /**
     * Finds commission records between two dates.
     *
     * @param startDate the start date
     * @param endDate   the end date
     * @return list of CommissionLedger entries
     */
    List<CommissionLedger> findByTransactionDateBetween(LocalDate startDate, LocalDate endDate);

    /**
     * Finds commission records for a doctor between two dates.
     *
     * @param doctorId  the ID of the doctor
     * @param startDate the start date
     * @param endDate   the end date
     * @return list of CommissionLedger entries
     */
    List<CommissionLedger> findByDoctorIdAndTransactionDateBetween(Long doctorId, LocalDate startDate,
            LocalDate endDate);

    /**
     * Calculates total unpaid commission for a specific doctor.
     *
     * @param doctorId the ID of the doctor
     * @param status   the payment status
     * @return total unpaid amount
     */
    @Query("SELECT COALESCE(SUM(c.calculatedAmount), 0) FROM CommissionLedger c WHERE c.doctor.id = :doctorId AND c.status = :status")
    BigDecimal getTotalCommissionByDoctorAndStatus(@Param("doctorId") Long doctorId, @Param("status") String status);

    /**
     * Calculates total commission for all doctors by status.
     *
     * @param status the payment status
     * @return total amount
     */
    @Query("SELECT COALESCE(SUM(c.calculatedAmount), 0) FROM CommissionLedger c WHERE c.status = :status")
    BigDecimal getTotalCommissionByStatus(@Param("status") String status);

    /**
     * Counts commission records by status.
     *
     * @param status the payment status
     * @return count of records
     */
    long countByStatus(String status);

    /**
     * Counts commission records for a doctor by status.
     *
     * @param doctorId the ID of the doctor
     * @param status   the payment status
     * @return count of records
     */
    long countByDoctorIdAndStatus(Long doctorId, String status);

    /**
     * Finds commission ledger row linked to a specific lab order.
     *
     * @param labOrderId order id
     * @return matching commission ledger if present
     */
    Optional<CommissionLedger> findByLabOrderId(Long labOrderId);
}
