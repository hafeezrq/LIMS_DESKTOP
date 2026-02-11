package com.qdc.lims.repository;

import com.qdc.lims.entity.LabOrder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for LabOrder entities, providing CRUD operations and
 * custom queries
 * for dashboard statistics and patient order history.
 */
@Repository
public interface LabOrderRepository extends JpaRepository<LabOrder, Long> {

    @EntityGraph(attributePaths = { "patient", "results", "results.testDefinition",
            "results.testDefinition.category" })
    List<LabOrder> findAll();

    @Query("""
            SELECT COUNT(DISTINCT o)
            FROM LabOrder o
            JOIN o.results r
            WHERE o.status NOT IN ('COMPLETED', 'CANCELLED')
            """)
    long countPendingWithResults();

    @Query("""
            SELECT COUNT(DISTINCT o)
            FROM LabOrder o
            JOIN o.results r
            WHERE o.status = 'COMPLETED'
            """)
    long countCompletedWithResults();

    /**
     * Finds all orders for a specific patient, sorted by order ID in descending
     * order.
     *
     * @param patientId the ID of the patient
     * @return list of LabOrders for the patient, newest first
     */
    List<LabOrder> findByPatientIdOrderByIdDesc(Long patientId);

    /**
     * Finds orders with a specific status within a date range (for dashboard
     * processing counts).
     *
     * @param status the order status (e.g., "PENDING", "COMPLETED")
     * @param start  the start of the date range
     * @param end    the end of the date range
     * @return list of matching LabOrders
     */
    List<LabOrder> findByStatusAndOrderDateBetween(String status, LocalDateTime start, LocalDateTime end);

    /**
     * Finds completed orders that have not been delivered, within a date range (for
     * pickup queue).
     *
     * @param status the order status (typically "COMPLETED")
     * @param start  the start of the date range
     * @param end    the end of the date range
     * @return list of LabOrders ready for pickup
     */
    List<LabOrder> findByStatusAndIsReportDeliveredFalseAndOrderDateBetween(String status, LocalDateTime start,
            LocalDateTime end);

    /**
     * Finds orders that were delivered (collected) within a specific date range.
     *
     * @param start the start of the delivery date range
     * @param end   the end of the delivery date range
     * @return list of collected LabOrders
     */
    List<LabOrder> findByIsReportDeliveredTrueAndDeliveryDateBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Finds orders marked for report reprint.
     *
     * @return list of LabOrders requiring reprint
     */
    List<LabOrder> findByReprintRequiredTrue();

    /**
     * Finds all orders created within a specific date range.
     *
     * @param start the start of the order date range
     * @param end   the end of the order date range
     * @return list of LabOrders within the date range
     */
    List<LabOrder> findByOrderDateBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Counts the number of orders within a specific date range.
     *
     * @param start the start of the order date range
     * @param end   the end of the order date range
     * @return the count of LabOrders within the date range
     */
    long countByOrderDateBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Counts orders by status.
     *
     * @param status the order status
     * @return count of orders with that status
     */
    long countByStatus(String status);

    /**
     * Counts orders excluding two statuses.
     *
     * @param status1 first status to exclude
     * @param status2 second status to exclude
     * @return count of orders not matching either status
     */
    long countByStatusNotAndStatusNot(String status1, String status2);

    /**
     * Counts orders by status within a date range.
     *
     * @param status the order status
     * @param start  range start
     * @param end    range end
     * @return count of orders with status in range
     */
    long countByStatusAndOrderDateBetween(String status, LocalDateTime start, LocalDateTime end);

    /**
     * Finds orders that have an outstanding balance due.
     *
     * @param minBalance the minimum balance (usually 0)
     * @return list of orders with unpaid balance
     */
    List<LabOrder> findByBalanceDueGreaterThan(BigDecimal minBalance);

    /**
     * Normalizes stale in-progress locks (orders opened for review but not started).
     *
     * @return number of rows updated
     */
    @Modifying
    @Query("UPDATE LabOrder o SET o.status = 'PENDING' WHERE o.status = 'IN_PROGRESS' AND o.labStartedAt IS NULL")
    int normalizeStaleInProgressToPending();
}
