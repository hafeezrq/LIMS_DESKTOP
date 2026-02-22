package com.qdc.lims.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qdc.lims.entity.Department;
import com.qdc.lims.entity.InventoryItem;
import com.qdc.lims.entity.Panel;
import com.qdc.lims.entity.ReferenceRange;
import com.qdc.lims.entity.TestCategory;
import com.qdc.lims.entity.TestConsumption;
import com.qdc.lims.entity.TestDefinition;
import com.qdc.lims.repository.DepartmentRepository;
import com.qdc.lims.repository.InventoryItemRepository;
import com.qdc.lims.repository.ReferenceRangeRepository;
import com.qdc.lims.repository.PanelRepository;
import com.qdc.lims.repository.TestCategoryRepository;
import com.qdc.lims.repository.TestConsumptionRepository;
import com.qdc.lims.repository.TestDefinitionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Seeds master data on first run for production profile.
 * Reads a bundled JSON file and only inserts when the database is empty.
 */
@Component
@Profile("prod")
@Order(Ordered.LOWEST_PRECEDENCE)
public class MasterDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MasterDataSeeder.class);

    private final DepartmentRepository departmentRepository;
    private final InventoryItemRepository inventoryRepository;
    private final TestCategoryRepository testCategoryRepository;
    private final TestDefinitionRepository testDefinitionRepository;
    private final PanelRepository panelRepository;
    private final TestConsumptionRepository testConsumptionRepository;
    private final ReferenceRangeRepository referenceRangeRepository;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    @Value("${qdc.seed.master.enabled:false}")
    private boolean enabled;

    @Value("${qdc.seed.master.location:classpath:seed/master-data.json}")
    private String seedLocation;

    public MasterDataSeeder(DepartmentRepository departmentRepository,
            InventoryItemRepository inventoryRepository,
            TestCategoryRepository testCategoryRepository,
            TestDefinitionRepository testDefinitionRepository,
            PanelRepository panelRepository,
            TestConsumptionRepository testConsumptionRepository,
            ReferenceRangeRepository referenceRangeRepository,
            ResourceLoader resourceLoader) {
        this.departmentRepository = departmentRepository;
        this.inventoryRepository = inventoryRepository;
        this.testCategoryRepository = testCategoryRepository;
        this.testDefinitionRepository = testDefinitionRepository;
        this.panelRepository = panelRepository;
        this.testConsumptionRepository = testConsumptionRepository;
        this.referenceRangeRepository = referenceRangeRepository;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.resourceLoader = resourceLoader;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (!enabled) {
            log.info("Master data seeding disabled (qdc.seed.master.enabled=false).");
            return;
        }

        if (testDefinitionRepository.count() > 0 || departmentRepository.count() > 0) {
            log.info("Master data already exists. Skipping seed.");
            return;
        }

        Resource resource = resourceLoader.getResource(seedLocation);
        if (!resource.exists()) {
            log.warn("Master data seed file not found at {}. Skipping seed.", seedLocation);
            return;
        }

        MasterDataSeed seed;
        try (InputStream in = resource.getInputStream()) {
            seed = objectMapper.readValue(in, MasterDataSeed.class);
        }

        if (seed == null) {
            log.warn("Master data seed file was empty. Skipping seed.");
            return;
        }

        Map<String, Department> departments = new HashMap<>();
        List<DepartmentSeed> deptSeeds = seed.departments != null ? seed.departments : List.of();
        for (DepartmentSeed deptSeed : deptSeeds) {
            if (deptSeed == null || isBlank(deptSeed.name)) {
                continue;
            }
            Department dept = departmentRepository.findByName(deptSeed.name)
                    .orElseGet(Department::new);
            dept.setName(deptSeed.name);
            dept.setCode(isBlank(deptSeed.code) ? buildCode(deptSeed.name) : deptSeed.code);
            dept.setActive(deptSeed.active == null || deptSeed.active);
            departmentRepository.save(dept);
            departments.put(normalizeKey(dept.getName()), dept);
        }

        Map<String, InventoryItem> inventory = new HashMap<>();
        List<InventorySeed> inventorySeeds = seed.inventory != null ? seed.inventory : List.of();
        for (InventorySeed itemSeed : inventorySeeds) {
            if (itemSeed == null || isBlank(itemSeed.name)) {
                continue;
            }
            InventoryItem item = inventoryRepository.findByItemName(itemSeed.name).orElseGet(InventoryItem::new);
            item.setItemName(itemSeed.name);
            item.setUnit(itemSeed.unit);
            item.setCurrentStock(defaultDecimal(itemSeed.currentStock));
            item.setMinThreshold(defaultDecimal(itemSeed.minThreshold));
            item.setAverageCost(defaultDecimal(itemSeed.averageCost));
            item.setActive(itemSeed.active == null || itemSeed.active);
            inventoryRepository.save(item);
            inventory.put(normalizeKey(item.getItemName()), item);
        }

        Map<String, TestCategory> categories = new HashMap<>();
        List<CategorySeed> categorySeeds = seed.categories != null ? seed.categories : List.of();
        for (CategorySeed categorySeed : categorySeeds) {
            if (categorySeed == null || isBlank(categorySeed.name)) {
                continue;
            }

            Department dept = resolveDepartment(categorySeed.department, departments);
            if (dept == null) {
                continue;
            }

            TestCategory category = testCategoryRepository.findByNameAndDepartment(categorySeed.name, dept)
                    .orElseGet(TestCategory::new);
            category.setName(categorySeed.name);
            category.setDescription(categorySeed.description);
            category.setDepartment(dept);
            category.setActive(categorySeed.active == null || categorySeed.active);
            testCategoryRepository.save(category);
            categories.put(categoryKey(dept.getName(), category.getName()), category);
        }

        Map<String, TestDefinition> tests = new HashMap<>();
        List<TestSeed> testSeeds = seed.tests != null ? seed.tests : List.of();
        for (TestSeed testSeed : testSeeds) {
            if (testSeed == null || isBlank(testSeed.name)) {
                continue;
            }

            TestDefinition test = null;
            if (!isBlank(testSeed.shortCode)) {
                test = testDefinitionRepository.findByShortCode(testSeed.shortCode);
            }
            if (test == null) {
                test = findByName(testSeed.name);
            }
            if (test == null) {
                test = new TestDefinition();
            }

            Department dept = resolveDepartment(testSeed.department, departments);

            TestCategory category = null;
            if (!isBlank(testSeed.category)) {
                if (dept != null) {
                    category = categories.get(categoryKey(dept.getName(), testSeed.category));
                    if (category == null) {
                        category = testCategoryRepository.findByNameAndDepartment(testSeed.category, dept)
                                .orElseGet(TestCategory::new);
                        category.setName(testSeed.category);
                        category.setDepartment(dept);
                        category.setActive(true);
                        testCategoryRepository.save(category);
                        categories.put(categoryKey(dept.getName(), category.getName()), category);
                    }
                }
            }

            if (dept == null && category != null) {
                dept = category.getDepartment();
            }

            test.setTestName(testSeed.name);
            test.setShortCode(testSeed.shortCode);
            test.setDepartment(dept);
            test.setCategory(category);
            test.setUnit(testSeed.unit);
            test.setPrice(testSeed.price);
            test.setMinRange(testSeed.minRange);
            test.setMaxRange(testSeed.maxRange);
            test.setActive(testSeed.active == null || testSeed.active);
            testDefinitionRepository.save(test);
            if (!isBlank(test.getShortCode())) {
                tests.put(normalizeKey(test.getShortCode()), test);
            }
            tests.put(normalizeKey(test.getTestName()), test);
        }

        List<PanelSeed> panelSeeds = seed.panels != null ? seed.panels : List.of();
        for (PanelSeed panelSeed : panelSeeds) {
            if (panelSeed == null || isBlank(panelSeed.name)) {
                continue;
            }
            Department dept = resolveDepartment(panelSeed.department, departments);
            Panel panel = new Panel();
            panel.setPanelName(panelSeed.name);
            panel.setDepartment(dept);
            panel.setPrice(panelSeed.price);
            panel.setActive(panelSeed.active == null || panelSeed.active);

            List<String> panelTests = panelSeed.tests != null ? panelSeed.tests : List.of();
            java.util.List<TestDefinition> resolvedTests = new java.util.ArrayList<>();
            for (String testRef : panelTests) {
                if (isBlank(testRef)) {
                    continue;
                }
                TestDefinition test = tests.get(normalizeKey(testRef));
                if (test != null) {
                    resolvedTests.add(test);
                }
            }
            panel.setTests(resolvedTests);
            panelRepository.save(panel);
        }

        Map<Long, Boolean> rangeSeeded = new HashMap<>();
        List<RangeSeed> rangeSeeds = seed.ranges != null ? seed.ranges : List.of();
        for (RangeSeed rangeSeed : rangeSeeds) {
            if (rangeSeed == null || (isBlank(rangeSeed.testShortCode) && isBlank(rangeSeed.testName))) {
                continue;
            }
            if (rangeSeed.minVal == null || rangeSeed.maxVal == null) {
                continue;
            }

            TestDefinition test = null;
            if (!isBlank(rangeSeed.testShortCode)) {
                test = tests.get(normalizeKey(rangeSeed.testShortCode));
            }
            if (test == null && !isBlank(rangeSeed.testName)) {
                test = tests.get(normalizeKey(rangeSeed.testName));
            }
            if (test == null || test.getId() == null) {
                continue;
            }

            boolean hasExisting = rangeSeeded.computeIfAbsent(test.getId(),
                    id -> !referenceRangeRepository.findByTestId(id).isEmpty());
            if (hasExisting) {
                continue;
            }

            ReferenceRange range = new ReferenceRange();
            range.setTest(test);
            range.setGender(isBlank(rangeSeed.gender) ? "Both" : rangeSeed.gender);
            range.setMinAge(rangeSeed.minAge);
            range.setMaxAge(rangeSeed.maxAge);
            range.setMinVal(rangeSeed.minVal);
            range.setMaxVal(rangeSeed.maxVal);
            referenceRangeRepository.save(range);
        }

        List<RecipeSeed> recipeSeeds = seed.recipes != null ? seed.recipes : List.of();
        for (RecipeSeed recipeSeed : recipeSeeds) {
            if (recipeSeed == null || isBlank(recipeSeed.itemName)) {
                continue;
            }

            TestDefinition test = null;
            if (!isBlank(recipeSeed.testShortCode)) {
                test = tests.get(normalizeKey(recipeSeed.testShortCode));
            }
            if (test == null && !isBlank(recipeSeed.testName)) {
                test = tests.get(normalizeKey(recipeSeed.testName));
            }
            InventoryItem item = inventory.get(normalizeKey(recipeSeed.itemName));
            if (test == null || item == null) {
                continue;
            }

            TestConsumption recipe = testConsumptionRepository.findByTestAndItem(test, item)
                    .orElseGet(TestConsumption::new);
            recipe.setTest(test);
            recipe.setItem(item);
            recipe.setQuantity(defaultDecimal(recipeSeed.quantity));
            testConsumptionRepository.save(recipe);
        }

        log.info("Master data seed complete: {} departments, {} categories, {} inventory items, {} tests.",
                departments.size(), categories.size(), inventory.size(), testSeeds.size());
    }

    private TestDefinition findByName(String name) {
        if (isBlank(name)) {
            return null;
        }
        List<TestDefinition> matches = testDefinitionRepository.findByTestNameContainingIgnoreCase(name);
        for (TestDefinition test : matches) {
            if (test != null && name.equalsIgnoreCase(test.getTestName())) {
                return test;
            }
        }
        return null;
    }

    private static BigDecimal defaultDecimal(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String buildCode(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.length() <= 3) {
            return trimmed.toUpperCase(Locale.ROOT);
        }
        return trimmed.substring(0, 3).toUpperCase(Locale.ROOT);
    }

    private static String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private Department resolveDepartment(String departmentName, Map<String, Department> departments) {
        if (isBlank(departmentName)) {
            return null;
        }
        Department dept = departments.get(normalizeKey(departmentName));
        if (dept != null) {
            return dept;
        }
        dept = departmentRepository.findByName(departmentName).orElseGet(() -> {
            Department created = new Department();
            created.setName(departmentName);
            created.setCode(buildCode(departmentName));
            created.setActive(true);
            return departmentRepository.save(created);
        });
        departments.put(normalizeKey(dept.getName()), dept);
        return dept;
    }

    private static String categoryKey(String departmentName, String categoryName) {
        return normalizeKey(departmentName) + "::" + normalizeKey(categoryName);
    }

    public static class MasterDataSeed {
        public List<DepartmentSeed> departments;
        public List<CategorySeed> categories;
        public List<InventorySeed> inventory;
        public List<TestSeed> tests;
        public List<PanelSeed> panels;
        public List<RangeSeed> ranges;
        public List<RecipeSeed> recipes;
    }

    public static class DepartmentSeed {
        public String name;
        public String code;
        public Boolean active;
    }

    public static class CategorySeed {
        public String name;
        public String department;
        public String description;
        public Boolean active;
    }

    public static class InventorySeed {
        public String name;
        public String unit;
        public BigDecimal currentStock;
        public BigDecimal minThreshold;
        public BigDecimal averageCost;
        public Boolean active;
    }

    public static class TestSeed {
        public String name;
        public String shortCode;
        public String department;
        public String category;
        public String unit;
        public BigDecimal price;
        public BigDecimal minRange;
        public BigDecimal maxRange;
        public Boolean active;
    }

    public static class PanelSeed {
        public String name;
        public String department;
        public BigDecimal price;
        public Boolean active;
        public List<String> tests;
    }

    public static class RangeSeed {
        public String testShortCode;
        public String testName;
        public String gender;
        public Integer minAge;
        public Integer maxAge;
        public BigDecimal minVal;
        public BigDecimal maxVal;
    }

    public static class RecipeSeed {
        public String testShortCode;
        public String testName;
        public String itemName;
        public BigDecimal quantity;
    }
}
