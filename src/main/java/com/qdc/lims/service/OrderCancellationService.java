package com.qdc.lims.service;

import com.qdc.lims.entity.CommissionLedger;
import com.qdc.lims.entity.InventoryItem;
import com.qdc.lims.entity.LabOrder;
import com.qdc.lims.entity.LabResult;
import com.qdc.lims.entity.Payment;
import com.qdc.lims.entity.TestConsumption;
import com.qdc.lims.repository.CommissionLedgerRepository;
import com.qdc.lims.repository.InventoryItemRepository;
import com.qdc.lims.repository.LabOrderRepository;
import com.qdc.lims.repository.PaymentRepository;
import com.qdc.lims.repository.TestConsumptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles order cancellation, inventory rollback, and refund posting.
 */
@Service
public class OrderCancellationService {

    private final LabOrderRepository labOrderRepository;
    private final TestConsumptionRepository testConsumptionRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final CommissionLedgerRepository commissionLedgerRepository;
    private final PaymentRepository paymentRepository;

    public OrderCancellationService(LabOrderRepository labOrderRepository,
            TestConsumptionRepository testConsumptionRepository,
            InventoryItemRepository inventoryItemRepository,
            CommissionLedgerRepository commissionLedgerRepository,
            PaymentRepository paymentRepository) {
        this.labOrderRepository = labOrderRepository;
        this.testConsumptionRepository = testConsumptionRepository;
        this.inventoryItemRepository = inventoryItemRepository;
        this.commissionLedgerRepository = commissionLedgerRepository;
        this.paymentRepository = paymentRepository;
    }

    /**
     * Marks an order as currently being reviewed by lab staff.
     */
    @Transactional
    public void markUnderLabReview(Long orderId) {
        LabOrder order = labOrderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (!"PENDING".equals(order.getStatus())) {
            return;
        }

        order.setStatus("IN_PROGRESS");
        labOrderRepository.save(order);
    }

    /**
     * Releases temporary review lock if no test work has started.
     */
    @Transactional
    public void releaseLabReview(Long orderId) {
        LabOrder order = labOrderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (!"IN_PROGRESS".equals(order.getStatus())) {
            return;
        }

        if (hasLabWorkStarted(order)) {
            return;
        }

        order.setStatus("PENDING");
        labOrderRepository.save(order);
    }

    /**
     * Returns true if the order can still be cancelled by reception.
     */
    public boolean canCancel(Long orderId) {
        if (orderId == null) {
            return false;
        }
        LabOrder order = labOrderRepository.findById(orderId).orElse(null);
        return canCancel(order);
    }

    /**
     * Returns true if the order can still be cancelled by reception.
     */
    public boolean canCancel(LabOrder order) {
        if (order == null || order.getId() == null) {
            return false;
        }
        if (!"PENDING".equals(order.getStatus())) {
            return false;
        }
        return !hasLabWorkStarted(order);
    }

    /**
     * Cancels a pending order before lab work starts and records refund.
     */
    @Transactional
    public CancellationResult cancelOrder(Long orderId) {
        LabOrder order = labOrderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (!canCancel(order)) {
            throw new IllegalStateException(
                    "Order #" + order.getId() + " cannot be cancelled because lab work has already started.");
        }

        rollbackInventory(order);
        deleteCommissionRow(order.getId());

        BigDecimal refundAmount = normalize(order.getPaidAmount());
        if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
            Payment refund = new Payment();
            refund.setType("EXPENSE");
            refund.setCategory("REFUND");
            refund.setDescription("Refund for cancelled order #" + order.getId());
            refund.setAmount(refundAmount);
            refund.setPaymentMethod("CASH");
            refund.setRemarks("Auto-generated refund for pre-test cancellation");
            refund.setTransactionDate(LocalDateTime.now());
            paymentRepository.save(refund);
        }

        labOrderRepository.delete(order);
        return new CancellationResult(orderId, refundAmount);
    }

    private void rollbackInventory(LabOrder order) {
        if (order.getResults() == null || order.getResults().isEmpty()) {
            return;
        }

        Map<Long, BigDecimal> restockByItemId = new HashMap<>();

        for (LabResult result : order.getResults()) {
            if (result == null || result.getTestDefinition() == null) {
                continue;
            }

            List<TestConsumption> recipe = testConsumptionRepository.findByTest(result.getTestDefinition());
            for (TestConsumption ingredient : recipe) {
                if (ingredient == null || ingredient.getItem() == null || ingredient.getItem().getId() == null) {
                    continue;
                }
                BigDecimal qty = normalize(ingredient.getQuantity());
                if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                Long itemId = ingredient.getItem().getId();
                restockByItemId.put(itemId, restockByItemId.getOrDefault(itemId, BigDecimal.ZERO).add(qty));
            }
        }

        for (Map.Entry<Long, BigDecimal> entry : restockByItemId.entrySet()) {
            InventoryItem item = inventoryItemRepository.findById(entry.getKey())
                    .orElseThrow(() -> new IllegalStateException("Inventory item missing while rolling back: "
                            + entry.getKey()));
            item.setCurrentStock(normalize(item.getCurrentStock()).add(entry.getValue()));
            inventoryItemRepository.save(item);
        }
    }

    private void deleteCommissionRow(Long orderId) {
        CommissionLedger ledger = commissionLedgerRepository.findByLabOrderId(orderId).orElse(null);
        if (ledger == null) {
            return;
        }

        BigDecimal paidAmount = normalize(ledger.getPaidAmount());
        boolean alreadyPaid = "PAID".equalsIgnoreCase(ledger.getStatus())
                || paidAmount.compareTo(BigDecimal.ZERO) > 0;
        if (alreadyPaid) {
            throw new IllegalStateException(
                    "Order #" + orderId
                            + " cannot be cancelled because linked doctor commission has already been paid.");
        }

        commissionLedgerRepository.delete(ledger);
    }

    private boolean hasLabWorkStarted(LabOrder order) {
        if (order.getLabStartedAt() != null) {
            return true;
        }
        if (order.getResults() == null) {
            return false;
        }
        return order.getResults().stream().anyMatch(this::hasResultActivity);
    }

    private boolean hasResultActivity(LabResult result) {
        if (result == null) {
            return false;
        }
        return hasText(result.getResultValue())
                || hasText(result.getPerformedBy())
                || result.getPerformedAt() != null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private BigDecimal normalize(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    public record CancellationResult(Long orderId, BigDecimal refundAmount) {
    }
}
