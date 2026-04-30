package com.qdc.lims.service;

import com.qdc.lims.entity.LabOrder;
import com.qdc.lims.entity.LabResult;
import com.qdc.lims.entity.OrderReportDepartmentProgress;
import com.qdc.lims.repository.LabOrderRepository;
import com.qdc.lims.repository.OrderReportDepartmentProgressRepository;
import com.qdc.lims.util.ReportPrintState;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Persists per-department report print progress and keeps LabOrder summary state
 * in sync.
 */
@Service
public class ReportPrintProgressService {

    private final LabOrderRepository labOrderRepository;
    private final OrderReportDepartmentProgressRepository progressRepository;

    public ReportPrintProgressService(LabOrderRepository labOrderRepository,
            OrderReportDepartmentProgressRepository progressRepository) {
        this.labOrderRepository = labOrderRepository;
        this.progressRepository = progressRepository;
    }

    @Transactional
    public ReportProgressSnapshot synchronizeAndGetSnapshot(LabOrder order) {
        LabOrder managedOrder = loadManagedOrder(order);
        List<String> departments = collectDepartments(managedOrder);

        List<OrderReportDepartmentProgress> existing = progressRepository.findByLabOrderId(managedOrder.getId());
        Map<String, OrderReportDepartmentProgress> byDepartment = new LinkedHashMap<>();
        for (OrderReportDepartmentProgress row : existing) {
            if (row.getDepartmentName() != null) {
                byDepartment.put(row.getDepartmentName(), row);
            }
        }

        List<OrderReportDepartmentProgress> rowsToSave = new ArrayList<>();
        for (String department : departments) {
            if (!byDepartment.containsKey(department)) {
                OrderReportDepartmentProgress row = new OrderReportDepartmentProgress();
                row.setLabOrder(managedOrder);
                row.setDepartmentName(department);
                row.setPrinted(false);
                row.setPrintCount(0);
                rowsToSave.add(row);
                byDepartment.put(department, row);
            }
        }
        if (!rowsToSave.isEmpty()) {
            progressRepository.saveAll(rowsToSave);
        }

        List<OrderReportDepartmentProgress> stale = existing.stream()
                .filter(row -> row.getDepartmentName() == null || !departments.contains(row.getDepartmentName()))
                .toList();
        if (!stale.isEmpty()) {
            progressRepository.deleteAll(stale);
        }

        return refreshOrderState(managedOrder, byDepartment.values(), null, null);
    }

    @Transactional
    public ReportProgressSnapshot markDepartmentsPrinted(LabOrder order, List<String> selectedDepartments, String username) {
        LabOrder managedOrder = loadManagedOrder(order);
        ReportProgressSnapshot snapshot = synchronizeAndGetSnapshot(managedOrder);
        if (selectedDepartments == null || selectedDepartments.isEmpty()) {
            return snapshot;
        }

        LocalDateTime now = LocalDateTime.now();
        List<OrderReportDepartmentProgress> rows = progressRepository.findByLabOrderId(managedOrder.getId());
        Map<String, OrderReportDepartmentProgress> byDepartment = rows.stream()
                .collect(java.util.stream.Collectors.toMap(
                        OrderReportDepartmentProgress::getDepartmentName,
                        r -> r,
                        (a, b) -> a,
                        LinkedHashMap::new));

        List<OrderReportDepartmentProgress> rowsToSave = new ArrayList<>();
        for (String department : selectedDepartments) {
            OrderReportDepartmentProgress row = byDepartment.get(department);
            if (row == null) {
                continue;
            }
            if (!row.isPrinted()) {
                row.setPrinted(true);
                row.setFirstPrintedAt(now);
            }
            Integer count = row.getPrintCount() != null ? row.getPrintCount() : 0;
            row.setPrintCount(count + 1);
            row.setLastPrintedAt(now);
            row.setLastPrintedBy(username);
            rowsToSave.add(row);
        }

        if (!rowsToSave.isEmpty()) {
            progressRepository.saveAll(rowsToSave);
        }

        return refreshOrderState(managedOrder, byDepartment.values(), now, username);
    }

    @Transactional
    public ReportProgressSnapshot markDelivered(LabOrder order, String username) {
        LabOrder managedOrder = loadManagedOrder(order);
        ReportProgressSnapshot snapshot = synchronizeAndGetSnapshot(managedOrder);
        LocalDateTime now = LocalDateTime.now();

        managedOrder.setReportDelivered(true);
        managedOrder.setDeliveryDate(now);
        managedOrder.setReportProgressUpdatedAt(now);
        managedOrder.setReportProgressUpdatedBy(username);
        managedOrder.setReportPrintState(ReportPrintState.DELIVERED);
        managedOrder.setPrintedDepartmentCount(snapshot.printedCount());
        managedOrder.setTotalDepartmentCount(snapshot.totalCount());
        labOrderRepository.save(managedOrder);

        return new ReportProgressSnapshot(snapshot.departments(), snapshot.printedDepartments(),
                snapshot.printedCount(), snapshot.totalCount(), ReportPrintState.DELIVERED);
    }

    public String buildSubStatusLabel(LabOrder order) {
        int printed = order.getPrintedDepartmentCount() != null ? order.getPrintedDepartmentCount() : 0;
        int total = order.getTotalDepartmentCount() != null ? order.getTotalDepartmentCount() : 0;
        String state = normalizeState(order);

        if (ReportPrintState.DELIVERED.equals(state)) {
            return total > 0 ? "Delivered (" + printed + "/" + total + ")" : "Delivered";
        }
        if (ReportPrintState.PARTIALLY_PRINTED.equals(state)) {
            return "Partially Printed (" + printed + "/" + total + ")";
        }
        if (ReportPrintState.FULLY_PRINTED.equals(state)) {
            return total > 0 ? "Printed Pending Delivery (" + printed + "/" + total + ")" : "Printed Pending Delivery";
        }
        return "Not Printed";
    }

    public boolean isDeliveredSectionState(LabOrder order) {
        String state = normalizeState(order);
        return ReportPrintState.PARTIALLY_PRINTED.equals(state)
                || ReportPrintState.FULLY_PRINTED.equals(state)
                || ReportPrintState.DELIVERED.equals(state)
                || order.isReportDelivered();
    }

    public boolean isReadySectionState(LabOrder order) {
        String state = normalizeState(order);
        return ReportPrintState.NOT_PRINTED.equals(state) && !order.isReportDelivered();
    }

    public String normalizeState(LabOrder order) {
        if (order == null) {
            return ReportPrintState.NOT_PRINTED;
        }
        String state = order.getReportPrintState();
        if (state == null || state.isBlank()) {
            if (order.isReportDelivered()) {
                return ReportPrintState.DELIVERED;
            }
            return ReportPrintState.NOT_PRINTED;
        }
        return state;
    }

    public List<String> defaultSelectionForPrint(ReportProgressSnapshot snapshot, LabOrder order) {
        if (snapshot == null || snapshot.departments().isEmpty()) {
            return List.of();
        }
        String state = normalizeState(order);
        if (ReportPrintState.PARTIALLY_PRINTED.equals(state)) {
            List<String> remaining = snapshot.departments().stream()
                    .filter(dept -> !snapshot.printedDepartments().contains(dept))
                    .toList();
            if (!remaining.isEmpty()) {
                return remaining;
            }
        }
        return snapshot.departments();
    }

    private LabOrder loadManagedOrder(LabOrder order) {
        if (order == null || order.getId() == null) {
            throw new IllegalArgumentException("Order is required");
        }
        return labOrderRepository.findById(order.getId())
                .orElseThrow(() -> new RuntimeException("Order not found: " + order.getId()));
    }

    private List<String> collectDepartments(LabOrder order) {
        if (order.getResults() == null) {
            return List.of();
        }
        Set<String> departments = new LinkedHashSet<>();
        for (LabResult result : order.getResults()) {
            departments.add(resolveDepartmentName(result));
        }
        return departments.stream()
                .sorted(Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private String resolveDepartmentName(LabResult result) {
        if (result != null && result.getTestDefinition() != null) {
            String shortCode = result.getTestDefinition().getShortCode();
            if (shortCode != null && shortCode.trim().toUpperCase(java.util.Locale.ROOT).startsWith("SEM-")) {
                return "Semen Analysis";
            }
        }
        if (result != null
                && result.getTestDefinition() != null
                && result.getTestDefinition().getDepartment() != null
                && result.getTestDefinition().getDepartment().getName() != null
                && !result.getTestDefinition().getDepartment().getName().isBlank()) {
            return result.getTestDefinition().getDepartment().getName();
        }
        return "Other";
    }

    private ReportProgressSnapshot refreshOrderState(LabOrder managedOrder,
            java.util.Collection<OrderReportDepartmentProgress> rows,
            LocalDateTime updateTime,
            String updatedBy) {
        List<String> departments = collectDepartments(managedOrder);
        Set<String> departmentSet = new LinkedHashSet<>(departments);
        Set<String> printedDepartments = new LinkedHashSet<>();
        for (OrderReportDepartmentProgress row : rows) {
            if (row.getDepartmentName() != null
                    && departmentSet.contains(row.getDepartmentName())
                    && row.isPrinted()) {
                printedDepartments.add(row.getDepartmentName());
            }
        }

        int totalCount = departments.size();
        int printedCount = printedDepartments.size();
        String state = determineState(managedOrder, printedCount, totalCount);

        boolean changed = false;
        if (!state.equals(managedOrder.getReportPrintState())) {
            managedOrder.setReportPrintState(state);
            changed = true;
        }
        if (managedOrder.getPrintedDepartmentCount() == null
                || managedOrder.getPrintedDepartmentCount() != printedCount) {
            managedOrder.setPrintedDepartmentCount(printedCount);
            changed = true;
        }
        if (managedOrder.getTotalDepartmentCount() == null
                || managedOrder.getTotalDepartmentCount() != totalCount) {
            managedOrder.setTotalDepartmentCount(totalCount);
            changed = true;
        }
        if (updateTime != null) {
            managedOrder.setReportProgressUpdatedAt(updateTime);
            managedOrder.setReportProgressUpdatedBy(updatedBy);
            changed = true;
        }
        if (changed) {
            labOrderRepository.save(managedOrder);
        }

        return new ReportProgressSnapshot(departments, printedDepartments, printedCount, totalCount, state);
    }

    private String determineState(LabOrder order, int printedCount, int totalCount) {
        if (order.isReportDelivered()) {
            return ReportPrintState.DELIVERED;
        }
        if (totalCount == 0 || printedCount == 0) {
            return ReportPrintState.NOT_PRINTED;
        }
        if (printedCount < totalCount) {
            return ReportPrintState.PARTIALLY_PRINTED;
        }
        return ReportPrintState.FULLY_PRINTED;
    }

    /**
     * Snapshot of department-level progress for one order.
     */
    public record ReportProgressSnapshot(
            List<String> departments,
            Set<String> printedDepartments,
            int printedCount,
            int totalCount,
            String state) {
        public boolean allPrinted() {
            return totalCount > 0 && printedCount >= totalCount;
        }
    }
}
