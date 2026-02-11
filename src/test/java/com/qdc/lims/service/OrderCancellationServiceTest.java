package com.qdc.lims.service;

import com.qdc.lims.entity.CommissionLedger;
import com.qdc.lims.entity.InventoryItem;
import com.qdc.lims.entity.LabOrder;
import com.qdc.lims.entity.LabResult;
import com.qdc.lims.entity.Payment;
import com.qdc.lims.entity.TestConsumption;
import com.qdc.lims.entity.TestDefinition;
import com.qdc.lims.repository.CommissionLedgerRepository;
import com.qdc.lims.repository.InventoryItemRepository;
import com.qdc.lims.repository.LabOrderRepository;
import com.qdc.lims.repository.PaymentRepository;
import com.qdc.lims.repository.TestConsumptionRepository;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @InjectMocks
    private OrderCancellationService orderCancellationService;

    @Test
    void cancelOrderShouldRollbackInventoryAndCreateRefund() {
        Long orderId = 10L;
        Long itemId = 99L;

        LabOrder order = buildPendingOrder(orderId, BigDecimal.valueOf(500));
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

        when(labOrderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(testConsumptionRepository.findByTest(testDefinition)).thenReturn(List.of(ingredient));
        when(inventoryItemRepository.findById(itemId)).thenReturn(Optional.of(inventoryItem));
        when(commissionLedgerRepository.findByLabOrderId(orderId)).thenReturn(Optional.of(commissionLedger));

        OrderCancellationService.CancellationResult resultSummary = orderCancellationService.cancelOrder(orderId);

        assertEquals(orderId, resultSummary.orderId());
        assertEquals(0, BigDecimal.valueOf(500).compareTo(resultSummary.refundAmount()));
        assertEquals(0, BigDecimal.valueOf(10).compareTo(inventoryItem.getCurrentStock()));

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment payment = paymentCaptor.getValue();
        assertEquals("EXPENSE", payment.getType());
        assertEquals("REFUND", payment.getCategory());
        assertEquals(0, BigDecimal.valueOf(500).compareTo(payment.getAmount()));

        verify(commissionLedgerRepository).delete(commissionLedger);
        verify(labOrderRepository).delete(order);
        verify(inventoryItemRepository).save(inventoryItem);
    }

    @Test
    void cancelOrderShouldFailWhenLabAlreadyStarted() {
        Long orderId = 11L;
        LabOrder order = buildPendingOrder(orderId, BigDecimal.ZERO);
        order.setLabStartedAt(LocalDateTime.now());

        when(labOrderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThrows(IllegalStateException.class, () -> orderCancellationService.cancelOrder(orderId));

        verify(paymentRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(labOrderRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void releaseLabReviewShouldReturnOrderToPendingWhenNoWorkStarted() {
        Long orderId = 12L;
        LabOrder order = buildPendingOrder(orderId, BigDecimal.ZERO);
        order.setStatus("IN_PROGRESS");

        when(labOrderRepository.findById(orderId)).thenReturn(Optional.of(order));

        orderCancellationService.releaseLabReview(orderId);

        assertEquals("PENDING", order.getStatus());
        verify(labOrderRepository).save(order);
    }

    private LabOrder buildPendingOrder(Long orderId, BigDecimal paidAmount) {
        LabOrder order = new LabOrder();
        order.setId(orderId);
        order.setStatus("PENDING");
        order.setPaidAmount(paidAmount);
        order.setResults(new ArrayList<>());
        return order;
    }
}
