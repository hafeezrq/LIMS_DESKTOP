package com.qdc.lims.service;

import com.qdc.lims.entity.CommissionLedger;
import com.qdc.lims.entity.InventoryItem;
import com.qdc.lims.entity.LabOrder;
import com.qdc.lims.entity.LabResult;
import com.qdc.lims.entity.Payment;
import com.qdc.lims.entity.TestConsumption;
import com.qdc.lims.entity.TestDefinition;
import com.qdc.lims.entity.User;
import com.qdc.lims.repository.CommissionLedgerRepository;
import com.qdc.lims.repository.InventoryItemRepository;
import com.qdc.lims.repository.LabOrderRepository;
import com.qdc.lims.repository.PaymentRepository;
import com.qdc.lims.repository.TestConsumptionRepository;
import com.qdc.lims.repository.UserRepository;
import com.qdc.lims.ui.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCancellationServiceTest {

    @Mock
    private LabOrderRepository labOrderRepository;
    @Mock
    private TestConsumptionRepository testConsumptionRepository;
    @Mock
    private InventoryItemRepository inventoryItemRepository;
    @Mock
    private CommissionLedgerRepository commissionLedgerRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private CancellationApprovalKeyService cancellationApprovalKeyService;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OrderCancellationService orderCancellationService;

    @Test
    void cancelOrderShouldSoftCancelRollbackInventoryAndCreateRefund() {
        Long orderId = 10L;
        Long itemId = 99L;
        String approvalKey = "admin-key";

        LabOrder order = buildOrder(orderId, "PENDING", BigDecimal.valueOf(500));
        TestDefinition testDefinition = new TestDefinition();
        testDefinition.setId(50L);

        LabResult result = new LabResult();
        result.setLabOrder(order);
        result.setTestDefinition(testDefinition);
        result.setResultValue("");
        order.setResults(new ArrayList<>(List.of(result)));

        InventoryItem inventoryItem = new InventoryItem();
        inventoryItem.setId(itemId);
        inventoryItem.setCurrentStock(BigDecimal.valueOf(8));

        TestConsumption ingredient = new TestConsumption();
        ingredient.setTest(testDefinition);
        ingredient.setItem(inventoryItem);
        ingredient.setQuantity(BigDecimal.valueOf(2));

        CommissionLedger commissionLedger = new CommissionLedger();
        commissionLedger.setStatus("UNPAID");

        User actor = new User();
        actor.setId(7L);
        actor.setUsername("reception1");

        when(labOrderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(testConsumptionRepository.findByTest(testDefinition)).thenReturn(List.of(ingredient));
        when(inventoryItemRepository.findById(itemId)).thenReturn(Optional.of(inventoryItem));
        when(commissionLedgerRepository.findByLabOrderId(orderId)).thenReturn(Optional.of(commissionLedger));
        when(cancellationApprovalKeyService.verifyKey(approvalKey)).thenReturn(true);
        when(currentUserProvider.getUsername()).thenReturn("reception1");
        when(userRepository.findByUsername("reception1")).thenReturn(Optional.of(actor));

        OrderCancellationService.CancellationResult resultSummary = orderCancellationService
                .cancelOrderAuthorized(orderId, approvalKey, "Patient requested cancellation.");

        assertEquals(orderId, resultSummary.orderId());
        assertEquals(0, BigDecimal.valueOf(500).compareTo(resultSummary.refundAmount()));
        assertEquals(0, BigDecimal.valueOf(10).compareTo(inventoryItem.getCurrentStock()));

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment payment = paymentCaptor.getValue();
        assertEquals("EXPENSE", payment.getType());
        assertEquals("REFUND", payment.getCategory());
        assertEquals(0, BigDecimal.valueOf(500).compareTo(payment.getAmount()));
        assertEquals(actor, payment.getRecordedBy());

        ArgumentCaptor<LabOrder> orderCaptor = ArgumentCaptor.forClass(LabOrder.class);
        verify(labOrderRepository).save(orderCaptor.capture());
        LabOrder savedOrder = orderCaptor.getValue();
        assertEquals("CANCELLED", savedOrder.getStatus());
        assertEquals("reception1", savedOrder.getCancelledBy());
        assertEquals("Patient requested cancellation.", savedOrder.getCancellationReason());
        assertEquals("ADMIN_KEY", savedOrder.getCancellationApprovalMethod());
        assertEquals(0, BigDecimal.valueOf(500).compareTo(savedOrder.getCancellationRefundAmount()));

        verify(labOrderRepository, never()).delete(any());

        ArgumentCaptor<CommissionLedger> commissionCaptor = ArgumentCaptor.forClass(CommissionLedger.class);
        verify(commissionLedgerRepository).save(commissionCaptor.capture());
        assertEquals("CANCELLED", commissionCaptor.getValue().getStatus());

        verify(inventoryItemRepository).save(inventoryItem);
    }

    @Test
    void cancelOrderShouldFailWhenLabAlreadyStarted() {
        Long orderId = 11L;
        String approvalKey = "admin-key";
        LabOrder order = buildOrder(orderId, "PENDING", BigDecimal.ZERO);
        order.setLabStartedAt(LocalDateTime.now());

        when(labOrderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(cancellationApprovalKeyService.verifyKey(approvalKey)).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> orderCancellationService.cancelOrderAuthorized(orderId, approvalKey, "Cancel request"));

        verify(paymentRepository, never()).save(any());
        verify(labOrderRepository, never()).save(any());
    }

    @Test
    void cancelOrderShouldFailForCompletedOrder() {
        Long orderId = 21L;
        String approvalKey = "admin-key";
        LabOrder order = buildOrder(orderId, "COMPLETED", BigDecimal.ZERO);

        when(labOrderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(cancellationApprovalKeyService.verifyKey(approvalKey)).thenReturn(true);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> orderCancellationService.cancelOrderAuthorized(orderId, approvalKey, "Cancel request"));

        assertTrue(ex.getMessage().contains("completed"));
    }

    @Test
    void cancelOrderShouldRequireAuthorizedApprover() {
        Long orderId = 13L;
        String approvalKey = "wrong-key";
        when(cancellationApprovalKeyService.verifyKey(approvalKey)).thenReturn(false);

        assertThrows(SecurityException.class,
                () -> orderCancellationService.cancelOrderAuthorized(orderId, approvalKey, "Cancel request"));

        verify(labOrderRepository, never()).findById(any());
        verify(labOrderRepository, never()).save(any());
    }

    @Test
    void cancelOrderShouldRequireReason() {
        Long orderId = 14L;
        String approvalKey = "admin-key";
        when(cancellationApprovalKeyService.verifyKey(approvalKey)).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> orderCancellationService.cancelOrderAuthorized(orderId, approvalKey, "   "));

        verify(labOrderRepository, never()).findById(any());
    }

    @Test
    void inProgressWithoutActivityShouldStillBeCancellable() {
        LabOrder order = buildOrder(15L, "IN_PROGRESS", BigDecimal.ZERO);
        assertTrue(orderCancellationService.canCancel(order));
        assertEquals(null, orderCancellationService.getCancellationBlockReason(order));
    }

    @Test
    void releaseLabReviewShouldReturnOrderToPendingWhenNoWorkStarted() {
        Long orderId = 12L;
        LabOrder order = buildOrder(orderId, "IN_PROGRESS", BigDecimal.ZERO);

        when(labOrderRepository.findById(orderId)).thenReturn(Optional.of(order));

        orderCancellationService.releaseLabReview(orderId);

        assertEquals("PENDING", order.getStatus());
        verify(labOrderRepository).save(order);
    }

    @Test
    void markUnderLabReviewShouldOnlyTransitionPending() {
        Long orderId = 16L;
        LabOrder pending = buildOrder(orderId, "PENDING", BigDecimal.ZERO);
        when(labOrderRepository.findById(orderId)).thenReturn(Optional.of(pending));

        boolean acquired = orderCancellationService.markUnderLabReview(orderId);

        assertTrue(acquired);
        assertEquals("IN_PROGRESS", pending.getStatus());
        verify(labOrderRepository).save(pending);
    }

    @Test
    void markUnderLabReviewShouldNotReacquireInProgress() {
        Long orderId = 17L;
        LabOrder inProgress = buildOrder(orderId, "IN_PROGRESS", BigDecimal.ZERO);
        when(labOrderRepository.findById(orderId)).thenReturn(Optional.of(inProgress));

        boolean acquired = orderCancellationService.markUnderLabReview(orderId);

        assertFalse(acquired);
        verify(labOrderRepository, never()).save(any());
    }

    private LabOrder buildOrder(Long orderId, String status, BigDecimal paidAmount) {
        LabOrder order = new LabOrder();
        order.setId(orderId);
        order.setStatus(status);
        order.setPaidAmount(paidAmount);
        order.setResults(new ArrayList<>());
        return order;
    }
}
