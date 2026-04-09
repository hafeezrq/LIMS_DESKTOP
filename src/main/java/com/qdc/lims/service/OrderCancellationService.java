package com.qdc.lims.service;

import com.qdc.lims.entity.CommissionLedger;
import com.qdc.lims.entity.InventoryItem;
import com.qdc.lims.entity.LabOrder;
import com.qdc.lims.entity.LabResult;
import com.qdc.lims.entity.Payment;
import com.qdc.lims.entity.TestConsumption;
import com.qdc.lims.entity.User;
import com.qdc.lims.repository.CommissionLedgerRepository;
import com.qdc.lims.repository.InventoryItemRepository;
import com.qdc.lims.repository.LabOrderRepository;
import com.qdc.lims.repository.PaymentRepository;
import com.qdc.lims.repository.TestConsumptionRepository;
import com.qdc.lims.repository.UserRepository;
import com.qdc.lims.ui.CurrentUserProvider;
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
    private final CancellationApprovalKeyService cancellationApprovalKeyService;
    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;

    public OrderCancellationService(LabOrderRepository labOrderRepository,
            TestConsumptionRepository testConsumptionRepository,
            InventoryItemRepository inventoryItemRepository,
            CommissionLedgerRepository commissionLedgerRepository,
            PaymentRepository paymentRepository,
            CancellationApprovalKeyService cancellationApprovalKeyService,
            CurrentUserProvider currentUserProvider,
            UserRepository userRepository) {
        this.labOrderRepository = labOrderRepository;
        this.testConsumptionRepository = testConsumptionRepository;
        this.inventoryItemRepository = inventoryItemRepository;
        this.commissionLedgerRepository = commissionLedgerRepository;
        this.paymentRepository = paymentRepository;
        this.cancellationApprovalKeyService = cancellationApprovalKeyService;
        this.currentUserProvider = currentUserProvider;
        this.userRepository = userRepository;
    }

    /**
     * Marks an order as currently being reviewed by lab staff.
     */
    @Transactional
    public boolean markUnderLabReview(Long orderId) {
        LabOrder order = labOrderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (!"PENDING".equals(order.getStatus())) {
            return false;
        }

        order.setStatus("IN_PROGRESS");
        labOrderRepository.save(order);
        return true;
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
        return getCancellationBlockReason(orderId) == null;
    }

    /**
     * Returns true if the order can still be cancelled by reception.
     */
    public boolean canCancel(LabOrder order) {
        return getCancellationBlockReason(order) == null;
    }

    /**
     * Returns null when cancellation is allowed; otherwise returns a user-facing
     * reason why cancellation is blocked.
     */
    public String getCancellationBlockReason(Long orderId) {
        if (orderId == null) {
            return "Order ID is required.";
        }
        LabOrder order = labOrderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return "Order not found: " + orderId;
        }
        return getCancellationBlockReason(order);
    }

    /**
     * Returns null when cancellation is allowed; otherwise returns a user-facing
     * reason why cancellation is blocked.
     */
    public String getCancellationBlockReason(LabOrder order) {
        if (order == null || order.getId() == null) {
            return "Order not found.";
        }

        if (order.getDeliveryDate() != null || order.isReportDelivered()) {
            return "Order #" + order.getId() + " cannot be cancelled because report is already delivered.";
        }

        String status = order.getStatus();
        if ("CANCELLED".equals(status)) {
            return "Order #" + order.getId() + " is already cancelled.";
        }
        if ("COMPLETED".equals(status)) {
            return "Order #" + order.getId() + " is completed and cannot be cancelled.";
        }
        if (!"PENDING".equals(status) && !"IN_PROGRESS".equals(status)) {
            return "Order #" + order.getId() + " cannot be cancelled from status: " + status + ".";
        }
        if (hasLabWorkStarted(order)) {
            return "Order #" + order.getId() + " cannot be cancelled because lab work has already started.";
        }
        return null;
    }

    public boolean isCancellationKeyConfigured() {
        return cancellationApprovalKeyService.isKeyConfigured();
    }

    public boolean verifyCancellationKey(String approvalKey) {
        return cancellationApprovalKeyService.verifyKey(approvalKey);
    }

    /**
     * Cancels an eligible order (pending/in-progress with no lab activity), records
     * refund if needed, and soft-marks the order as CANCELLED.
     */
    @Transactional
    public CancellationResult cancelOrderAuthorized(Long orderId, String approvalKey, String cancellationReason) {
        if (!verifyCancellationKey(approvalKey)) {
            throw new SecurityException("Cancellation approver authorization is required.");
        }
        String normalizedReason = normalizeReason(cancellationReason);

        LabOrder order = labOrderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        String blockReason = getCancellationBlockReason(order);
        if (blockReason != null) {
            throw new IllegalStateException(blockReason);
        }

        rollbackInventory(order);
        voidCommissionForCancellation(order.getId());

        BigDecimal refundAmount = normalize(order.getPaidAmount());
        String currentUsername = normalizeUsername(currentUserProvider.getUsername());
        User actor = userRepository.findByUsername(currentUsername).orElse(null);
        if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
            Payment refund = new Payment();
            refund.setType("EXPENSE");
            refund.setCategory("REFUND");
            refund.setDescription("Refund for cancelled order #" + order.getId());
            refund.setAmount(refundAmount);
            refund.setPaymentMethod("CASH");
            refund.setRemarks("Auto-generated refund for cancelled order.");
            refund.setTransactionDate(LocalDateTime.now());
            refund.setRecordedBy(actor);
            paymentRepository.save(refund);
        }

        order.setStatus("CANCELLED");
        order.setCancelledAt(LocalDateTime.now());
        order.setCancelledBy(currentUsername);
        order.setCancellationReason(normalizedReason);
        order.setCancellationApprovalMethod("ADMIN_KEY");
        order.setCancellationRefundAmount(refundAmount);
        labOrderRepository.save(order);
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

    private void voidCommissionForCancellation(Long orderId) {
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

        ledger.setStatus("CANCELLED");
        commissionLedgerRepository.save(ledger);
    }

    private boolean hasLabWorkStarted(LabOrder order) {
        if (order.getLabStartedAt() != null) {
            return true;
        }
        if (order.getResults() == null) {
            return false;
        }
        // Only count activity if it's on a test that is NOT a procedural/skip-worklist
        // test
        return order.getResults().stream()
                .filter(result -> result.getTestDefinition() != null
                        && !Boolean.TRUE.equals(result.getTestDefinition().getSkipWorklist()))
                .anyMatch(this::hasResultActivity);
    }

    private boolean hasResultActivity(LabResult result) {
        if (result == null) {
            return false;
        }
        String val = result.getResultValue();
        // Work has started only if there is text AND that text is NOT our placeholder
        boolean hasRealValue = hasText(val) && !"PROCEDURAL".equalsIgnoreCase(val.trim());

        return hasRealValue
                || hasText(result.getPerformedBy())
                || result.getPerformedAt() != null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private BigDecimal normalize(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private String normalizeReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Cancellation reason is required.");
        }
        return normalized;
    }

    private String normalizeUsername(String username) {
        String normalized = username == null ? "" : username.trim();
        return normalized.isEmpty() ? "UNKNOWN" : normalized;
    }

    public record CancellationResult(Long orderId, BigDecimal refundAmount) {
    }
}
