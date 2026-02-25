package com.qdc.lims.repository;

import com.qdc.lims.entity.OrderReportDepartmentProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for per-department report print progress records.
 */
@Repository
public interface OrderReportDepartmentProgressRepository extends JpaRepository<OrderReportDepartmentProgress, Long> {

    List<OrderReportDepartmentProgress> findByLabOrderId(Long orderId);

    Optional<OrderReportDepartmentProgress> findByLabOrderIdAndDepartmentName(Long orderId, String departmentName);
}

