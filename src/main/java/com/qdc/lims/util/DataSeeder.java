package com.qdc.lims.util;

import com.qdc.lims.entity.*;
import com.qdc.lims.repository.*;
import com.qdc.lims.service.PatientService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.util.List;

/**
 * Seeds baseline master data into a fresh database on application startup.
 * <p>
 * The seeder is intentionally conservative: it exits early if any tests already
 * exist.
 */
@Component
@Profile({ "dev", "test" })
public class DataSeeder implements CommandLineRunner {

    @Value("${qdc.seed.dev.enabled:false}")
    private boolean seedEnabled;

    private final InventoryItemRepository inventoryRepo;
    private final DoctorRepository doctorRepo;
    private final TestDefinitionRepository testRepo;
    private final TestCategoryRepository testCategoryRepo;
    private final TestConsumptionRepository recipeRepo;
    private final PatientService patientService;
    private final DepartmentRepository departmentRepo;
    private final PanelRepository panelRepo;

    /**
     * Creates the data seeder.
     *
     * @param inventoryRepo inventory repository
     * @param doctorRepo doctor repository
     * @param testRepo test definition repository
     * @param recipeRepo test consumption repository
     * @param patientRepo patient repository (currently unused but kept for wiring)
     * @param patientService patient service
     * @param departmentRepo department repository
     */
    public DataSeeder(InventoryItemRepository inventoryRepo,
            DoctorRepository doctorRepo,
            TestDefinitionRepository testRepo,
            TestCategoryRepository testCategoryRepo,
            TestConsumptionRepository recipeRepo,
            PatientRepository patientRepo,
            PatientService patientService,
            DepartmentRepository departmentRepo,
            PanelRepository panelRepo) {
        this.inventoryRepo = inventoryRepo;
        this.doctorRepo = doctorRepo;
        this.testRepo = testRepo;
        this.testCategoryRepo = testCategoryRepo;
        this.recipeRepo = recipeRepo;
        this.patientService = patientService;
        this.departmentRepo = departmentRepo;
        this.panelRepo = panelRepo;
    }

    /**
     * Runs the seed process once at startup if the database is empty.
     *
     * @param args command-line arguments
     */
    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (!seedEnabled) {
            System.out.println("ℹ️ Dev/Test master data seeding is disabled.");
            return;
        }

        // Stop early if the database already contains tests.
        if (testRepo.count() > 0) {
            System.out.println("✅ Database already has data. Skipping Seeder.");
            return;
        }

        System.out.println("⚡ Seeding Default Data...");

        // --- A. INVENTORY ---
        saveInventory("Purple Top Tube (EDTA)", BigDecimal.valueOf(500.0), BigDecimal.valueOf(50.0), "pcs");
        saveInventory("Glucose Strip", BigDecimal.valueOf(200.0), BigDecimal.valueOf(20.0), "pcs");
        saveInventory("Alcohol Swab", BigDecimal.valueOf(1000.0), BigDecimal.valueOf(100.0), "pcs");

        // --- B. DOCTORS ---
        saveDoctor("Dr. Bilal Ahmed", "City Hospital", "0300-1234567", BigDecimal.valueOf(10.0));
        saveDoctor("Dr. Sara Khan", "Khan Clinic", "0321-7654321", BigDecimal.valueOf(15.0));

        // --- C. TEST DEFINITIONS ---
        TestDefinition cbc = createTest("Complete Blood Count (CBC)", "CBC", "Hematology",
                BigDecimal.valueOf(650.0), "cells/mcL", BigDecimal.valueOf(4500.0), BigDecimal.valueOf(11000.0));
        createTest("ESR (Erythrocyte Sedimentation Rate)", "ESR", "Hematology",
                BigDecimal.valueOf(200.0), "mm/hr", BigDecimal.valueOf(0.0), BigDecimal.valueOf(20.0));
        createTest("Blood Group & Rh Factor", "BG-RH", "Hematology", BigDecimal.valueOf(250.0), null, null, null);
        createTest("Hemoglobin (Hb)", "HB", "Hematology", BigDecimal.valueOf(150.0), "g/dL",
                BigDecimal.valueOf(12.0), BigDecimal.valueOf(17.0));
        createTest("Platelet Count", "PLT", "Hematology", BigDecimal.valueOf(200.0), "cells/mcL",
                BigDecimal.valueOf(150000.0), BigDecimal.valueOf(400000.0));

        TestDefinition bsf = createTest("Blood Sugar Fasting", "BSF", "Biochemistry", BigDecimal.valueOf(150.0),
                "mg/dL", BigDecimal.valueOf(70.0), BigDecimal.valueOf(100.0));
        createTest("Blood Sugar Random", "BSR", "Biochemistry", BigDecimal.valueOf(150.0), "mg/dL",
                BigDecimal.valueOf(70.0), BigDecimal.valueOf(140.0));
        createTest("HbA1c", "HBA1C", "Biochemistry", BigDecimal.valueOf(800.0), "%", BigDecimal.valueOf(4.0),
                BigDecimal.valueOf(5.6));
        createTest("Lipid Profile", "LIPID", "Biochemistry", BigDecimal.valueOf(900.0), "mg/dL", null, null);
        createTest("Cholesterol Total", "CHOL", "Biochemistry", BigDecimal.valueOf(250.0), "mg/dL",
                BigDecimal.valueOf(0.0), BigDecimal.valueOf(200.0));
        createTest("Creatinine", "CREAT", "Biochemistry", BigDecimal.valueOf(250.0), "mg/dL",
                BigDecimal.valueOf(0.7), BigDecimal.valueOf(1.3));
        createTest("Liver Function Test (LFT)", "LFT", "Biochemistry", BigDecimal.valueOf(1200.0), null, null, null);
        createTest("ALT (SGPT)", "SGPT", "Biochemistry", BigDecimal.valueOf(250.0), "U/L",
                BigDecimal.valueOf(7.0), BigDecimal.valueOf(56.0));

        createTest("HIV I & II", "HIV", "Serology", BigDecimal.valueOf(500.0), null, null, null);
        createTest("HBsAg", "HBSAG", "Serology", BigDecimal.valueOf(400.0), null, null, null);
        createTest("HCV", "HCV", "Serology", BigDecimal.valueOf(500.0), null, null, null);
        createTest("Typhidot", "TYPHI", "Serology", BigDecimal.valueOf(600.0), null, null, null);
        createTest("Dengue NS1", "DEN-NS1", "Serology", BigDecimal.valueOf(800.0), null, null, null);

        createTest("TSH", "TSH", "Thyroid", BigDecimal.valueOf(500.0), "mIU/L",
                BigDecimal.valueOf(0.4), BigDecimal.valueOf(4.0));
        createTest("T3", "T3", "Thyroid", BigDecimal.valueOf(400.0), "ng/dL",
                BigDecimal.valueOf(80.0), BigDecimal.valueOf(200.0));
        createTest("T4", "T4", "Thyroid", BigDecimal.valueOf(400.0), "mcg/dL",
                BigDecimal.valueOf(5.0), BigDecimal.valueOf(12.0));

        createUrineRoutinePanel();
        createTest("Urine Culture", "URINE-CS", "Urine", BigDecimal.valueOf(800.0), null, null, null);
        createTest("Urine Pregnancy (UPT)", "UPT", "Urine", BigDecimal.valueOf(200.0), null, null, null);

        // --- D. RECIPES ---
        InventoryItem tube = inventoryRepo.findByItemName("Purple Top Tube (EDTA)").orElse(null);
        InventoryItem strip = inventoryRepo.findByItemName("Glucose Strip").orElse(null);
        InventoryItem swab = inventoryRepo.findByItemName("Alcohol Swab").orElse(null);

        if (tube != null && swab != null) {
            createRecipe(cbc, tube, java.math.BigDecimal.ONE);
            createRecipe(cbc, swab, java.math.BigDecimal.ONE);
        }
        if (strip != null && bsf != null && swab != null) {
            createRecipe(bsf, strip, java.math.BigDecimal.ONE);
            createRecipe(bsf, swab, java.math.BigDecimal.ONE);
        }

        // --- E. PATIENTS ---
        registerPatient("Ali Khan", 35, "Male", "0300-5555555", "Lahore");
        registerPatient("Fatima Bibi", 28, "Female", "0321-9999999", "Karachi");

        System.out.println("✅ Seeding Complete! System ready.");
    }

    /**
     * Creates and stores a default inventory item.
     */
    private void saveInventory(String name, java.math.BigDecimal stock, java.math.BigDecimal threshold, String unit) {
        InventoryItem item = new InventoryItem();
        item.setItemName(name);
        item.setCurrentStock(stock);
        item.setMinThreshold(threshold);
        item.setUnit(unit);
        inventoryRepo.save(item);
    }

    /**
     * Creates and stores a default doctor record.
     */
    private void saveDoctor(String name, String clinic, String mobile, java.math.BigDecimal comm) {
        Doctor doc = new Doctor();
        doc.setName(name);
        doc.setClinicName(clinic);
        doc.setMobile(mobile);
        doc.setCommissionPercentage(comm);
        doctorRepo.save(doc);
    }

    /**
     * Registers a demo patient using the patient service (to reuse MRN logic).
     */
    private void registerPatient(String name, int age, String gender, String mobile, String city) {
        Patient p = new Patient();
        p.setFullName(name);
        p.setAge(age);
        p.setGender(gender);
        p.setMobileNumber(mobile);
        p.setCity(city);
        patientService.registerPatient(p);
    }

    /**
     * Creates a recipe item linking a test to an inventory item.
     */
    private void createRecipe(TestDefinition test, InventoryItem item, java.math.BigDecimal qty) {
        if (test == null || item == null) {
            return;
        }
        TestConsumption tc = new TestConsumption();
        tc.setTest(test);
        tc.setItem(item);
        tc.setQuantity(qty);
        recipeRepo.save(tc);
    }

    private void createUrineRoutinePanel() {
        Department urineDept = getOrCreateDepartment("Urine");
        TestCategory physical = getOrCreateCategory("Physical", urineDept);
        TestCategory chemical = getOrCreateCategory("Chemical", urineDept);
        TestCategory microscopic = getOrCreateCategory("Microscopic", urineDept);

        BigDecimal perTestPrice = BigDecimal.valueOf(10.0);

        TestDefinition color = createTestWithCategory("Color", "UR-COLOR", urineDept, physical,
                perTestPrice, null, null, null);
        TestDefinition appearance = createTestWithCategory("Appearance / Clarity", "UR-APPR", urineDept, physical,
                perTestPrice, null, null, null);
        TestDefinition odor = createTestWithCategory("Odor", "UR-ODOR", urineDept, physical,
                perTestPrice, null, null, null);
        TestDefinition volume = createTestWithCategory("Volume (24-hr)", "UR-VOL", urineDept, physical,
                perTestPrice, "mL/day", BigDecimal.valueOf(800.0), BigDecimal.valueOf(2000.0));
        TestDefinition specificGravity = createTestWithCategory("Specific Gravity", "UR-SG", urineDept, physical,
                perTestPrice, null, BigDecimal.valueOf(1.005), BigDecimal.valueOf(1.030));
        TestDefinition ph = createTestWithCategory("pH (Reaction)", "UR-PH", urineDept, physical,
                perTestPrice, null, BigDecimal.valueOf(4.5), BigDecimal.valueOf(8.0));

        TestDefinition protein = createTestWithCategory("Protein (Albumin)", "UR-PROT", urineDept, chemical,
                perTestPrice, null, null, null);
        TestDefinition glucose = createTestWithCategory("Glucose (Sugar)", "UR-GLU", urineDept, chemical,
                perTestPrice, null, null, null);
        TestDefinition ketone = createTestWithCategory("Ketone Bodies", "UR-KET", urineDept, chemical,
                perTestPrice, null, null, null);
        TestDefinition bilirubin = createTestWithCategory("Bilirubin (Bile pigments)", "UR-BIL", urineDept, chemical,
                perTestPrice, null, null, null);
        TestDefinition urobilinogen = createTestWithCategory("Urobilinogen", "UR-URO", urineDept, chemical,
                perTestPrice, "mg/dL", BigDecimal.valueOf(0.2), BigDecimal.valueOf(1.0));
        TestDefinition blood = createTestWithCategory("Blood / Hemoglobin", "UR-BLD", urineDept, chemical,
                perTestPrice, null, null, null);
        TestDefinition nitrite = createTestWithCategory("Nitrite", "UR-NIT", urineDept, chemical,
                perTestPrice, null, null, null);
        TestDefinition leukocyte = createTestWithCategory("Leukocyte Esterase", "UR-LEU", urineDept, chemical,
                perTestPrice, null, null, null);

        TestDefinition rbc = createTestWithCategory("Red Blood Cells (RBCs)", "UR-RBC", urineDept, microscopic,
                perTestPrice, "/HPF", BigDecimal.valueOf(0.0), BigDecimal.valueOf(2.0));
        TestDefinition wbc = createTestWithCategory("White Blood Cells (Pus cells)", "UR-WBC", urineDept, microscopic,
                perTestPrice, "/HPF", BigDecimal.valueOf(0.0), BigDecimal.valueOf(5.0));
        TestDefinition epithelial = createTestWithCategory("Epithelial Cells", "UR-EP", urineDept, microscopic,
                perTestPrice, "/HPF", BigDecimal.valueOf(0.0), BigDecimal.valueOf(5.0));
        TestDefinition casts = createTestWithCategory("Casts", "UR-CAST", urineDept, microscopic,
                perTestPrice, null, null, null);
        TestDefinition crystals = createTestWithCategory("Crystals", "UR-CRYS", urineDept, microscopic,
                perTestPrice, null, null, null);
        TestDefinition bacteria = createTestWithCategory("Bacteria", "UR-BACT", urineDept, microscopic,
                perTestPrice, null, null, null);
        TestDefinition yeast = createTestWithCategory("Yeast / Fungi", "UR-YEAST", urineDept, microscopic,
                perTestPrice, null, null, null);
        TestDefinition parasites = createTestWithCategory("Parasites", "UR-PARA", urineDept, microscopic,
                perTestPrice, null, null, null);
        TestDefinition mucus = createTestWithCategory("Mucus threads", "UR-MUC", urineDept, microscopic,
                perTestPrice, null, null, null);

        Panel panel = new Panel();
        panel.setPanelName("Urine Routine");
        panel.setDepartment(urineDept);
        panel.setPrice(BigDecimal.valueOf(200.0));
        panel.setActive(true);
        panel.setTests(List.of(color, appearance, odor, volume, specificGravity, ph,
                protein, glucose, ketone, bilirubin, urobilinogen, blood, nitrite, leukocyte,
                rbc, wbc, epithelial, casts, crystals, bacteria, yeast, parasites, mucus));
        panelRepo.save(panel);
    }

    private Department getOrCreateDepartment(String name) {
        return departmentRepo.findByName(name).orElseGet(() -> {
            Department d = new Department();
            d.setName(name);
            String code = name.length() > 3 ? name.substring(0, 3).toUpperCase() : name.toUpperCase();
            d.setCode(code);
            d.setActive(true);
            return departmentRepo.save(d);
        });
    }

    private TestCategory getOrCreateCategory(String name, Department department) {
        return testCategoryRepo.findByNameAndDepartment(name, department)
                .orElseGet(() -> {
                    TestCategory category = new TestCategory();
                    category.setName(name);
                    category.setDepartment(department);
                    category.setActive(true);
                    return testCategoryRepo.save(category);
                });
    }

    private TestDefinition createTestWithCategory(String name, String shortCode, Department department,
            TestCategory category, java.math.BigDecimal price, String unit, java.math.BigDecimal minRange,
            java.math.BigDecimal maxRange) {
        TestDefinition test = new TestDefinition();
        test.setTestName(name);
        test.setShortCode(shortCode);
        test.setDepartment(department);
        test.setCategory(category);
        if (price != null) {
            test.setPrice(price);
        }
        test.setUnit(unit);
        if (minRange != null && minRange.compareTo(java.math.BigDecimal.ZERO) > 0) {
            test.setMinRange(minRange);
        }
        if (maxRange != null && maxRange.compareTo(java.math.BigDecimal.ZERO) > 0) {
            test.setMaxRange(maxRange);
        }
        test.setActive(true);
        return testRepo.save(test);
    }

    /**
     * Creates a test definition and ensures its department exists.
     */
    private TestDefinition createTest(String name, String shortCode, String categoryName,
            java.math.BigDecimal price, String unit, java.math.BigDecimal minRange, java.math.BigDecimal maxRange) {

        TestDefinition test = new TestDefinition();
        test.setTestName(name);
        test.setShortCode(shortCode);

        Department dept = getOrCreateDepartment(categoryName);

        test.setDepartment(dept);

        if (price != null) {
            test.setPrice(price);
        }
        test.setUnit(unit);
        if (minRange != null && minRange.compareTo(java.math.BigDecimal.ZERO) > 0) {
            test.setMinRange(minRange);
        }
        if (maxRange != null && maxRange.compareTo(java.math.BigDecimal.ZERO) > 0) {
            test.setMaxRange(maxRange);
        }

        test.setActive(true);
        return testRepo.save(test);
    }
}
