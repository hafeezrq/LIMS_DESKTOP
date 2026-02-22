package com.qdc.lims.config;

import com.qdc.lims.entity.*;
import com.qdc.lims.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

/**
 * Initializes financial test data for the LIMS application.
 * Runs after DataSeeder (which seeds Patients, Doctors, Tests).
 */
@Component
@Profile({ "dev", "test" })
@Order(2) // Run after DataSeeder
public class TestDataInitializer implements CommandLineRunner {

    @Value("${qdc.seed.financial.enabled:false}")
    private boolean seedEnabled;

    private final LabOrderRepository orderRepo;
    private final PaymentRepository paymentRepo;
    private final CommissionLedgerRepository commissionRepo;
    private final SupplierLedgerRepository supplierLedgerRepo;
    private final DoctorRepository doctorRepo;
    private final PatientRepository patientRepo;
    private final SupplierRepository supplierRepo;

    public TestDataInitializer(LabOrderRepository orderRepo, PaymentRepository paymentRepo,
            CommissionLedgerRepository commissionRepo, SupplierLedgerRepository supplierLedgerRepo,
            DoctorRepository doctorRepo, PatientRepository patientRepo,
            SupplierRepository supplierRepo) {
        this.orderRepo = orderRepo;
        this.paymentRepo = paymentRepo;
        this.commissionRepo = commissionRepo;
        this.supplierLedgerRepo = supplierLedgerRepo;
        this.doctorRepo = doctorRepo;
        this.patientRepo = patientRepo;
        this.supplierRepo = supplierRepo;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        boolean forceSeed = Boolean.parseBoolean(System.getProperty("qdc.seed.force", "false"))
                || Boolean.parseBoolean(System.getenv().getOrDefault("LIMS_SEED_FORCE", "false"))
                || Boolean.parseBoolean(System.getenv().getOrDefault("QDC_SEED_FORCE", "false"));

        if (!seedEnabled && !forceSeed) {
            System.out.println("ℹ️ Dev/Test financial seeding is disabled.");
            return;
        }

        if (!forceSeed && (paymentRepo.count() > 0 || orderRepo.count() > 0)) {
            System.out.println("✅ Financial data already exists. Skipping Financial Seeder.");
            return;
        }

        System.out.println("⚡ Seeding Financial Test Data..." + (forceSeed ? " (forced)" : ""));

        // Ensure base data exists (from DataSeeder)
        List<Doctor> doctors = doctorRepo.findAll();
        List<Patient> patients = patientRepo.findAll();

        if (doctors.isEmpty() || patients.isEmpty()) {
            System.out.println("⚠️ No doctors or patients found. Skipping financial seeding.");
            return;
        }

        Random random = new Random();

        // 1. Create Suppliers (if not exists)
        if (supplierRepo.count() == 0) {
            createSupplier("MedTech Solutions", "0300-1112222");
            createSupplier("Global Lab Supplies", "0321-3334444");
        }
        List<Supplier> suppliers = supplierRepo.findAll();

        // 2. Generate Past Lab Orders (Income)
        // Orders from last 30 days
        int ordersToCreate = forceSeed ? 25 : 15;
        for (int i = 0; i < ordersToCreate; i++) {
            Patient p = patients.get(random.nextInt(patients.size()));
            Doctor d = doctors.get(random.nextInt(doctors.size()));

            LabOrder order = new LabOrder();
            order.setPatient(p);
            order.setReferringDoctor(d);

            LocalDateTime date = LocalDateTime.now().minusDays(random.nextInt(30));
            order.setOrderDate(date);
            order.setDeliveryDate(date.plusDays(1));
            order.setReportDelivered(random.nextBoolean());

            BigDecimal total = BigDecimal.valueOf(500.0 + random.nextInt(2000));
            order.setTotalAmount(total);

            boolean partial = forceSeed && random.nextBoolean();
            BigDecimal paid = partial
                    ? total.subtract(BigDecimal.valueOf(200 + random.nextInt(600))).max(BigDecimal.ZERO)
                    : total;
            order.setPaidAmount(paid);
            order.setBalanceDue(total.subtract(paid).max(BigDecimal.ZERO));
            order.setStatus("COMPLETED");

            orderRepo.save(order);

            // Generate Commission for this order (Expense)
            if (d.getCommissionPercentage() != null && d.getCommissionPercentage().compareTo(BigDecimal.ZERO) > 0) {
                CommissionLedger com = new CommissionLedger();
                com.setDoctor(d);
                com.setLabOrder(order);
                com.setTransactionDate(date.toLocalDate());
                BigDecimal comAmount = total
                        .multiply(d.getCommissionPercentage())
                        .divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP);

                // Randomly pay some commissions
                if (!forceSeed && random.nextBoolean()) {
                    com.setStatus("PAID");
                    com.setPaidAmount(comAmount);
                    com.setPaymentDate(date.toLocalDate().plusDays(2));
                } else {
                    com.setStatus("UNPAID");
                    com.setPaidAmount(forceSeed
                            ? comAmount.multiply(BigDecimal.valueOf(0.3))
                            : BigDecimal.ZERO);
                }
                commissionRepo.save(com);
            }
        }

        // 3. Generate General Expenses (Utility Custom Payments)
        createExpense("Utility Bill", "Electricity Bill - Jan", BigDecimal.valueOf(15000.0), 5);
        createExpense("Rent", "Lab Premises Rent - Jan", BigDecimal.valueOf(50000.0), 20);
        createExpense("Office Supplies", "Stationery & Printing", BigDecimal.valueOf(2500.0), 10);
        createExpense("Maintenance", "AC Repair", BigDecimal.valueOf(3000.0), 12);

        // 4. Generate Supplier Payments
        if (!suppliers.isEmpty()) {
            int supplierEntries = forceSeed ? 10 : 5;
            for (int i = 0; i < supplierEntries; i++) {
                Supplier s = suppliers.get(random.nextInt(suppliers.size()));
                SupplierLedger sl = new SupplierLedger();
                sl.setSupplier(s);
                LocalDateTime tx = LocalDateTime.now().minusDays(random.nextInt(20));
                sl.setTransactionDate(tx.toLocalDate());
                sl.setInvoiceDate(tx.toLocalDate());
                sl.setDueDate(tx.toLocalDate().plusDays(15));
                sl.setInvoiceNumber("INV-" + (2000 + i));

                BigDecimal bill = BigDecimal.valueOf(5000.0 + random.nextInt(8000));
                sl.setBillAmount(bill);
                BigDecimal paid = forceSeed && random.nextBoolean()
                        ? bill.multiply(BigDecimal.valueOf(0.5))
                        : bill;
                sl.setPaidAmount(paid);
                sl.setBalanceDue(bill.subtract(paid).max(BigDecimal.ZERO));
                sl.setRemarks("Inventory Purchase #" + (1000 + i));

                supplierLedgerRepo.save(sl);
            }
        }

        System.out.println("✅ Financial Seeding Complete!");
    }

    private void createSupplier(String name, String phone) {
        Supplier s = new Supplier();
        s.setName(name);
        s.setContactNumber(phone);
        s.setAddress("City Industrial Area");
        s.setEmail("info@" + name.toLowerCase().replace(" ", "") + ".com");
        supplierRepo.save(s);
    }

    private void createExpense(String category, String desc, BigDecimal amount, int daysAgo) {
        Payment p = new Payment();
        p.setType("EXPENSE");
        p.setCategory(category);
        p.setDescription(desc);
        p.setAmount(amount);
        p.setTransactionDate(LocalDateTime.now().minusDays(daysAgo));
        p.setPaymentMode("CASH");
        paymentRepo.save(p);
    }
}
