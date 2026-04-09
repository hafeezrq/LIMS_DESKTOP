package com.qdc.lims.service;

import com.qdc.lims.dto.OrderRequest;
import com.qdc.lims.entity.LabOrder;
import com.qdc.lims.entity.Panel;
import com.qdc.lims.entity.Patient;
import com.qdc.lims.entity.TestDefinition;
import com.qdc.lims.repository.CommissionLedgerRepository;
import com.qdc.lims.repository.DoctorRepository;
import com.qdc.lims.repository.InventoryItemRepository;
import com.qdc.lims.repository.LabOrderRepository;
import com.qdc.lims.repository.PanelRepository;
import com.qdc.lims.repository.PatientRepository;
import com.qdc.lims.repository.TestConsumptionRepository;
import com.qdc.lims.repository.TestDefinitionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private LabOrderRepository orderRepo;
    @Mock
    private PatientRepository patientRepo;
    @Mock
    private TestDefinitionRepository testRepo;
    @Mock
    private DoctorRepository doctorRepo;
    @Mock
    private CommissionLedgerRepository commissionRepo;
    @Mock
    private TestConsumptionRepository consumptionRepo;
    @Mock
    private InventoryItemRepository inventoryRepo;
    @Mock
    private PanelRepository panelRepo;

    @InjectMocks
    private OrderService orderService;

    @Test
    void createOrderShouldIncludeOnlyActiveTestsFromSelectedPanels() {
        Patient patient = new Patient();
        patient.setId(1L);

        TestDefinition activePanelTest = new TestDefinition();
        activePanelTest.setId(101L);
        activePanelTest.setTestName("Active Test");
        activePanelTest.setActive(true);
        activePanelTest.setSkipWorklist(false);

        TestDefinition inactivePanelTest = new TestDefinition();
        inactivePanelTest.setId(102L);
        inactivePanelTest.setTestName("Inactive Test");
        inactivePanelTest.setActive(false);
        inactivePanelTest.setSkipWorklist(false);

        Panel panel = new Panel();
        panel.setId(10);
        panel.setPrice(BigDecimal.valueOf(500));
        panel.setTests(List.of(activePanelTest, inactivePanelTest));

        when(patientRepo.findById(1L)).thenReturn(Optional.of(patient));
        when(panelRepo.findAllWithTestsById(List.of(10))).thenReturn(List.of(panel));
        when(testRepo.findAllById(List.of())).thenReturn(List.of());
        when(consumptionRepo.findByTest(any(TestDefinition.class))).thenReturn(List.of());
        when(orderRepo.save(any(LabOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderRequest request = new OrderRequest(
                1L,
                null,
                List.of(),
                List.of(10),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null);

        orderService.createOrder(request);

        ArgumentCaptor<LabOrder> orderCaptor = ArgumentCaptor.forClass(LabOrder.class);
        verify(orderRepo).save(orderCaptor.capture());
        LabOrder savedOrder = orderCaptor.getValue();

        assertEquals(1, savedOrder.getResults().size());
        assertEquals(101L, savedOrder.getResults().get(0).getTestDefinition().getId());
        assertEquals(0, BigDecimal.valueOf(500).compareTo(savedOrder.getTotalAmount()));
    }
}
