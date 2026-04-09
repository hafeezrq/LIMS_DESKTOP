package com.qdc.lims.service;

import com.qdc.lims.dto.OrderRequest;
import com.qdc.lims.entity.*;
import com.qdc.lims.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service for handling lab order creation and related business logic.
 */
@Service
public class OrderService {

    private final LabOrderRepository orderRepo;
    private final PatientRepository patientRepo;
    private final TestDefinitionRepository testRepo;
    private final DoctorRepository doctorRepo;
    private final CommissionLedgerRepository commissionRepo;
    private final TestConsumptionRepository consumptionRepo;
    private final InventoryItemRepository inventoryRepo;
    private final PanelRepository panelRepo;

    /**
     * Constructs an OrderService with all required repositories.
     *
     * @param orderRepo       LabOrder repository
     * @param patientRepo     Patient repository
     * @param testRepo        TestDefinition repository
     * @param doctorRepo      Doctor repository
     * @param commissionRepo  CommissionLedger repository
     * @param consumptionRepo TestConsumption repository
     * @param inventoryRepo   InventoryItem repository
     * @param panelRepo       Panel repository
     */
    public OrderService(LabOrderRepository orderRepo, PatientRepository patientRepo,
            TestDefinitionRepository testRepo, DoctorRepository doctorRepo,
            CommissionLedgerRepository commissionRepo, TestConsumptionRepository consumptionRepo,
            InventoryItemRepository inventoryRepo, PanelRepository panelRepo) {
        this.orderRepo = orderRepo;
        this.patientRepo = patientRepo;
        this.testRepo = testRepo;
        this.doctorRepo = doctorRepo;
        this.commissionRepo = commissionRepo;
        this.consumptionRepo = consumptionRepo;
        this.inventoryRepo = inventoryRepo;
        this.panelRepo = panelRepo;
    }

    /**
     * Creates a new lab order, handles inventory deduction, finance logic, and
     * commission calculation.
     *
     * @param request the order request data
     * @return the saved LabOrder entity
     */
    @Transactional
    public LabOrder createOrder(OrderRequest request) {
        // 1. Find Patient
        Patient patient = patientRepo.findById(request.patientId())
                .orElseThrow(() -> new RuntimeException("Patient not found"));

        // 2. Find Doctor (Handle Null for Self-Patients)
        Doctor doctor = null;
        if (request.doctorId() != null) {
            doctor = doctorRepo.findById(request.doctorId()).orElse(null);
        }

        // 3. Setup Order
        LabOrder order = new LabOrder();
        order.setPatient(patient);
        order.setReferringDoctor(doctor);

        BigDecimal totalAmount = BigDecimal.ZERO;

        List<Long> testIds = request.testIds() != null ? request.testIds() : List.of();
        List<Integer> panelIds = request.panelIds() != null ? request.panelIds() : List.of();

        // Fetch tests from panels
        List<Panel> panels = List.of();
        List<TestDefinition> panelTests = List.of();
        if (!panelIds.isEmpty()) {
            panels = panelRepo.findAllWithTestsById(panelIds);
            panelTests = panels.stream()
                    .flatMap(panel -> panel.getTests().stream())
                    .filter(test -> Boolean.TRUE.equals(test.getActive()))
                    .toList();
        }

        // Fetch individual tests
        List<TestDefinition> individualTests = testRepo.findAllById(testIds);

        // Merge and deduplicate
        List<TestDefinition> allTests = java.util.stream.Stream.concat(individualTests.stream(), panelTests.stream())
                .distinct()
                .toList();

        if (allTests.isEmpty()) {
            throw new RuntimeException("At least one test must be selected to create an order.");
        }

        if (!panels.isEmpty()) {
            order.setPanels(new java.util.ArrayList<>(panels));
        }

        java.util.Set<Long> panelTestIds = panels.stream()
                .filter(panel -> panel.getPrice() != null)
                .flatMap(panel -> panel.getTests().stream())
                .filter(test -> Boolean.TRUE.equals(test.getActive()))
                .map(TestDefinition::getId)
                .collect(java.util.stream.Collectors.toSet());

        for (Panel panel : panels) {
            if (panel.getPrice() != null) {
                totalAmount = totalAmount.add(panel.getPrice());
            }
        }

        for (TestDefinition test : individualTests) {
            if (!panelTestIds.contains(test.getId())) {
                BigDecimal priceToCharge;
                // Check if a manual price was sent for this test (ECG, X-Ray, etc.)
                if (request.manualPrices() != null && request.manualPrices().containsKey(test.getId())) {
                    priceToCharge = request.manualPrices().get(test.getId());
                } else {
                    priceToCharge = test.getPrice() != null ? test.getPrice() : BigDecimal.ZERO;
                }
                totalAmount = totalAmount.add(priceToCharge);
            }
        }

        for (TestDefinition test : allTests) {
            // A. Create Empty Result Slot
            LabResult result = new LabResult();
            result.setLabOrder(order);
            result.setTestDefinition(test);

            // --- UPDATED WORKLIST LOGIC ---
            if (Boolean.TRUE.equals(test.getSkipWorklist())) {
                result.setStatus("COMPLETED");
                result.setResultValue("PROCEDURAL"); // Placeholder for non-lab tests
            } else {
                result.setResultValue(""); // Waiting for Lab Tech
                result.setStatus("PENDING");
            }

            order.getResults().add(result);

            // C. INVENTORY LOGIC (Automatic Deduction)
            List<TestConsumption> recipe = consumptionRepo.findByTest(test);
            for (TestConsumption ingredient : recipe) {
                InventoryItem item = ingredient.getItem();

                BigDecimal needed = ingredient.getQuantity();
                BigDecimal available = item.getCurrentStock();

                // --- THE GUARD CHECK ---
                if (available == null || needed == null || available.compareTo(needed) < 0) {
                    throw new RuntimeException(
                            "❌ OUT OF STOCK: Test '" + test.getTestName() + "' requires "
                                    + (needed == null ? "0" : needed.toPlainString()) + " " + item.getUnit()
                                    + " of '" + item.getItemName() + "', "
                                    + "but only " + (available == null ? "0" : available.toPlainString())
                                    + " is available.");
                }
                // -----------------------

                // Subtract Stock
                BigDecimal newStock = available.subtract(needed);
                if (newStock.compareTo(BigDecimal.ZERO) < 0) {
                    throw new RuntimeException(
                            "❌ OUT OF STOCK: Not enough " + item.getItemName() + " to book this test.");
                }

                item.setCurrentStock(newStock);

                // Save updated stock
                inventoryRepo.save(item);
            }
        }

        // STEP B: DECIDE THE ORDER STATUS (DO THIS OUTSIDE THE LOOP)
        // Check the entire merged list of tests
        boolean hasLabWork = allTests.stream()
                .anyMatch(t -> !Boolean.TRUE.equals(t.getSkipWorklist()));

        // DEBUG: Add this line to your console so you can see what is happening!
        System.out.println("Order Creation - Has Lab Work: " + hasLabWork);

        if (hasLabWork) {
            order.setStatus("PENDING");
        } else {
            order.setStatus("COMPLETED");
        }

        // --- NEW FINANCE LOGIC ---
        order.setTotalAmount(totalAmount);
        order.setDiscountAmount(request.discount() != null ? request.discount() : BigDecimal.ZERO);
        order.setPaidAmount(request.cashPaid() != null ? request.cashPaid() : BigDecimal.ZERO);

        // Auto-calculate balance (Total - Discount - Paid)
        order.calculateBalance();
        // -------------------------
        LabOrder savedOrder = orderRepo.save(order);

        // 4. COMMISSION LOGIC (Secret Table)
        if (doctor != null
                && doctor.getCommissionPercentage() != null
                && doctor.getCommissionPercentage().compareTo(BigDecimal.ZERO) > 0) {
            CommissionLedger ledger = new CommissionLedger();
            ledger.setLabOrder(savedOrder);
            ledger.setDoctor(doctor);
            ledger.setTotalBillAmount(totalAmount);
            commissionRepo.save(ledger);
        }

        return savedOrder;
    }
}
