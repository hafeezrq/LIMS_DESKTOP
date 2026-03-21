package com.qdc.lims.service;

import com.qdc.lims.entity.TestDefinition;
import com.qdc.lims.entity.TestResultOption;
import com.qdc.lims.repository.TestDefinitionRepository;
import com.qdc.lims.repository.TestResultOptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Service for managing configurable per-test dropdown result options.
 */
@Service
public class TestResultOptionService {

    private static final Pattern VALID_CODE = Pattern.compile("[A-Z0-9_-]+");
    private static final Map<String, List<OptionTemplate>> COMMON_OPTION_TEMPLATES = createCommonOptionTemplates();

    private final TestResultOptionRepository optionRepository;
    private final TestDefinitionRepository testDefinitionRepository;

    public TestResultOptionService(TestResultOptionRepository optionRepository,
            TestDefinitionRepository testDefinitionRepository) {
        this.optionRepository = optionRepository;
        this.testDefinitionRepository = testDefinitionRepository;
    }

    /**
     * Returns all tests sorted by name for admin selection.
     */
    public List<TestDefinition> findAllTests() {
        return testDefinitionRepository.findAll().stream()
                .sorted(Comparator.comparing(TestDefinition::getTestName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * Returns all configured options for one test.
     */
    public List<TestResultOption> findOptionsForTest(Long testId) {
        if (testId == null) {
            return List.of();
        }
        return optionRepository.findByTestIdOrderByDisplayOrderAscOptionLabelAsc(testId);
    }

    /**
     * Returns active option labels keyed by test id, for fast UI lookup.
     */
    public Map<Long, List<String>> findActiveOptionLabelsByTestIds(Collection<Long> testIds) {
        if (testIds == null || testIds.isEmpty()) {
            return Map.of();
        }

        List<TestResultOption> options = optionRepository
                .findByTestIdInAndActiveTrueOrderByTestIdAscDisplayOrderAscOptionLabelAsc(testIds);

        Map<Long, List<String>> byTest = new LinkedHashMap<>();
        for (TestResultOption option : options) {
            if (option.getTest() == null || option.getTest().getId() == null) {
                continue;
            }
            Long testId = option.getTest().getId();
            byTest.computeIfAbsent(testId, ignored -> new ArrayList<>()).add(option.getOptionLabel());
        }
        return byTest;
    }

    /**
     * Creates or updates one test result option with validation.
     */
    @Transactional
    public TestResultOption saveOption(Long testId,
            Long optionId,
            String optionCode,
            String optionLabel,
            Integer displayOrder,
            boolean active) {
        if (testId == null) {
            throw new IllegalArgumentException("Test is required.");
        }

        TestDefinition test = testDefinitionRepository.findById(testId)
                .orElseThrow(() -> new IllegalArgumentException("Selected test does not exist."));

        String normalizedCode = normalizeCode(optionCode);
        String normalizedLabel = normalizeLabel(optionLabel);
        int normalizedDisplayOrder = displayOrder == null ? 0 : displayOrder;

        TestResultOption option;
        if (optionId == null) {
            option = new TestResultOption();
            option.setTest(test);
        } else {
            option = optionRepository.findByIdAndTestId(optionId, testId)
                    .orElseThrow(() -> new IllegalArgumentException("Option does not exist for selected test."));
        }

        optionRepository.findByTestIdAndOptionCodeIgnoreCase(testId, normalizedCode)
                .filter(existing -> !Objects.equals(existing.getId(), option.getId()))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Option code already exists for this test.");
                });

        option.setTest(test);
        option.setOptionCode(normalizedCode);
        option.setOptionLabel(normalizedLabel);
        option.setDisplayOrder(normalizedDisplayOrder);
        option.setActive(active);

        return optionRepository.save(option);
    }

    /**
     * Imports predefined common options for one selected test by short code.
     */
    @Transactional
    public ImportSummary importDefaultOptionsForTest(Long testId) {
        if (testId == null) {
            throw new IllegalArgumentException("Test is required.");
        }
        TestDefinition test = testDefinitionRepository.findById(testId)
                .orElseThrow(() -> new IllegalArgumentException("Selected test does not exist."));

        List<OptionTemplate> template = templateFor(test);
        if (template.isEmpty()) {
            return new ImportSummary(0, 0, 0);
        }
        return importTemplateForTest(test, template);
    }

    /**
     * Imports predefined common options for all tests with matching short codes.
     */
    @Transactional
    public ImportSummary importDefaultOptionsForCommonTests() {
        int testsMatched = 0;
        int optionsAdded = 0;
        int optionsSkipped = 0;

        for (TestDefinition test : testDefinitionRepository.findAll()) {
            List<OptionTemplate> template = templateFor(test);
            if (template.isEmpty()) {
                continue;
            }
            ImportSummary result = importTemplateForTest(test, template);
            testsMatched += result.testsMatched();
            optionsAdded += result.optionsAdded();
            optionsSkipped += result.optionsSkipped();
        }

        return new ImportSummary(testsMatched, optionsAdded, optionsSkipped);
    }

    /**
     * Deletes one option by id.
     */
    @Transactional
    public void deleteOption(Long optionId) {
        if (optionId == null) {
            return;
        }
        optionRepository.deleteById(optionId);
    }

    /**
     * Deletes all options for a test. Used before deleting the test.
     */
    @Transactional
    public void deleteByTestId(Long testId) {
        if (testId == null) {
            return;
        }
        optionRepository.deleteByTestId(testId);
    }

    private String normalizeCode(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Option code is required.");
        }
        String normalized = value.trim().replace(' ', '_').toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Option code is required.");
        }
        if (!VALID_CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Option code may contain only A-Z, 0-9, '_' or '-'.");
        }
        return normalized;
    }

    private String normalizeLabel(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Option label is required.");
        }
        return value.trim();
    }

    private ImportSummary importTemplateForTest(TestDefinition test, List<OptionTemplate> template) {
        if (test == null || test.getId() == null || template == null || template.isEmpty()) {
            return new ImportSummary(0, 0, 0);
        }

        int added = 0;
        int skipped = 0;
        for (OptionTemplate item : template) {
            String optionCode = normalizeCode(item.code());
            if (optionRepository.findByTestIdAndOptionCodeIgnoreCase(test.getId(), optionCode).isPresent()) {
                skipped++;
                continue;
            }

            TestResultOption option = new TestResultOption();
            option.setTest(test);
            option.setOptionCode(optionCode);
            option.setOptionLabel(item.label());
            option.setDisplayOrder(item.displayOrder());
            option.setActive(true);
            optionRepository.save(option);
            added++;
        }

        return new ImportSummary(1, added, skipped);
    }

    private List<OptionTemplate> templateFor(TestDefinition test) {
        if (test == null || test.getShortCode() == null || test.getShortCode().isBlank()) {
            return List.of();
        }
        String shortCode = test.getShortCode().trim().toUpperCase(Locale.ROOT);
        return COMMON_OPTION_TEMPLATES.getOrDefault(shortCode, List.of());
    }

    private static Map<String, List<OptionTemplate>> createCommonOptionTemplates() {
        Map<String, List<OptionTemplate>> templates = new HashMap<>();

        List<OptionTemplate> reactivePanel = List.of(
                new OptionTemplate("NON_REACTIVE", "Non-reactive", 10),
                new OptionTemplate("REACTIVE", "Reactive", 20),
                new OptionTemplate("EQUIVOCAL", "Equivocal", 30),
                new OptionTemplate("INVALID", "Invalid", 40));

        templates.put("HBSAG", reactivePanel);
        templates.put("HCV", reactivePanel);
        templates.put("HIV", List.of(
                new OptionTemplate("NON_REACTIVE", "Non-reactive", 10),
                new OptionTemplate("REACTIVE", "Reactive", 20),
                new OptionTemplate("INDETERMINATE", "Indeterminate", 30),
                new OptionTemplate("INVALID", "Invalid", 40)));
        templates.put("DEN-NS1", reactivePanel);
        templates.put("DEN-IGM", reactivePanel);
        templates.put("DEN-IGG", reactivePanel);

        templates.put("VDRL", List.of(
                new OptionTemplate("NON_REACTIVE", "Non-reactive", 10),
                new OptionTemplate("WEAK_REACTIVE", "Weak reactive", 20),
                new OptionTemplate("REACTIVE", "Reactive", 30)));

        templates.put("TYPHI", List.of(
                new OptionTemplate("NEGATIVE", "Negative", 10),
                new OptionTemplate("POSITIVE", "Positive", 20),
                new OptionTemplate("BORDERLINE", "Borderline", 30),
                new OptionTemplate("INVALID", "Invalid", 40)));

        templates.put("UPT", List.of(
                new OptionTemplate("NEGATIVE", "Negative", 10),
                new OptionTemplate("POSITIVE", "Positive", 20),
                new OptionTemplate("INVALID", "Invalid", 30)));

        List<OptionTemplate> urineSemiQuant = List.of(
                new OptionTemplate("NEGATIVE", "Negative", 10),
                new OptionTemplate("TRACE", "Trace", 20),
                new OptionTemplate("ONE_PLUS", "1+", 30),
                new OptionTemplate("TWO_PLUS", "2+", 40),
                new OptionTemplate("THREE_PLUS", "3+", 50),
                new OptionTemplate("FOUR_PLUS", "4+", 60));

        templates.put("UR-PROT", urineSemiQuant);
        templates.put("UR-GLU", urineSemiQuant);
        templates.put("UR-KET", urineSemiQuant);
        templates.put("UR-BIL", urineSemiQuant);
        templates.put("UR-BLD", urineSemiQuant);
        templates.put("UR-LEU", urineSemiQuant);

        templates.put("UR-NIT", List.of(
                new OptionTemplate("NEGATIVE", "Negative", 10),
                new OptionTemplate("POSITIVE", "Positive", 20)));

        templates.put("UR-URO", List.of(
                new OptionTemplate("NORMAL", "Normal", 10),
                new OptionTemplate("ONE_PLUS", "1+", 20),
                new OptionTemplate("TWO_PLUS", "2+", 30),
                new OptionTemplate("THREE_PLUS", "3+", 40)));

        templates.put("UR-COLOR", List.of(
                new OptionTemplate("COLORLESS", "Colorless", 10),
                new OptionTemplate("PALE_YELLOW", "Pale yellow", 20),
                new OptionTemplate("YELLOW", "Yellow", 30),
                new OptionTemplate("DARK_YELLOW", "Dark yellow", 40),
                new OptionTemplate("AMBER", "Amber", 50),
                new OptionTemplate("RED", "Red", 60),
                new OptionTemplate("BROWN", "Brown", 70)));

        templates.put("UR-APPR", List.of(
                new OptionTemplate("CLEAR", "Clear", 10),
                new OptionTemplate("SLIGHTLY_CLOUDY", "Slightly cloudy", 20),
                new OptionTemplate("CLOUDY", "Cloudy", 30),
                new OptionTemplate("TURBID", "Turbid", 40)));

        templates.put("UR-RBC", List.of(
                new OptionTemplate("RANGE_0_2", "0-2 /HPF", 10),
                new OptionTemplate("RANGE_3_5", "3-5 /HPF", 20),
                new OptionTemplate("RANGE_6_10", "6-10 /HPF", 30),
                new OptionTemplate("RANGE_GT_10", ">10 /HPF", 40)));
        templates.put("UR-WBC", List.of(
                new OptionTemplate("RANGE_0_2", "0-2 /HPF", 10),
                new OptionTemplate("RANGE_3_5", "3-5 /HPF", 20),
                new OptionTemplate("RANGE_6_10", "6-10 /HPF", 30),
                new OptionTemplate("RANGE_GT_10", ">10 /HPF", 40)));
        templates.put("UR-EP", List.of(
                new OptionTemplate("RANGE_0_2", "0-2 /HPF", 10),
                new OptionTemplate("RANGE_3_5", "3-5 /HPF", 20),
                new OptionTemplate("RANGE_6_10", "6-10 /HPF", 30),
                new OptionTemplate("RANGE_GT_10", ">10 /HPF", 40)));

        List<OptionTemplate> noneFewMany = List.of(
                new OptionTemplate("NONE", "None", 10),
                new OptionTemplate("FEW", "Few", 20),
                new OptionTemplate("MODERATE", "Moderate", 30),
                new OptionTemplate("MANY", "Many", 40));
        templates.put("UR-CAST", noneFewMany);
        templates.put("UR-CRYS", noneFewMany);
        templates.put("UR-BACT", noneFewMany);
        templates.put("UR-YEAST", noneFewMany);
        templates.put("UR-PARA", noneFewMany);
        templates.put("UR-MUC", noneFewMany);

        return templates;
    }

    /**
     * Summary returned after importing predefined option templates.
     *
     * @param testsMatched  number of tests where a template was found
     * @param optionsAdded  number of newly inserted options
     * @param optionsSkipped number of options skipped because they already existed
     */
    public record ImportSummary(int testsMatched, int optionsAdded, int optionsSkipped) {
    }

    private record OptionTemplate(String code, String label, int displayOrder) {
    }
}
