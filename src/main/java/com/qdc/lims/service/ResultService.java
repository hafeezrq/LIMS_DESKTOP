package com.qdc.lims.service;

import com.qdc.lims.dto.ResultEntryRequest;
import com.qdc.lims.entity.LabOrder;
import com.qdc.lims.entity.LabResult;
import com.qdc.lims.entity.LabResultEditAudit;
import com.qdc.lims.entity.ReferenceRange;
import com.qdc.lims.entity.TestDefinition;
import com.qdc.lims.repository.LabOrderRepository;
import com.qdc.lims.repository.LabResultEditAuditRepository;
import com.qdc.lims.repository.LabResultRepository;
import com.qdc.lims.repository.ReferenceRangeRepository;

import com.qdc.lims.ui.CurrentUserProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Service for handling lab result entry, validation, and saving logic.
 */
@Service
public class ResultService {

    private final LabResultRepository repository;
    private final LabResultEditAuditRepository labResultEditAuditRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ReferenceRangeRepository referenceRangeRepository;

    @Autowired
    private LabOrderRepository orderRepo;

    /**
     * Constructs a ResultService with the specified LabResultRepository.
     *
     * @param repository repository for lab results
     */
    public ResultService(LabResultRepository repository,
            LabResultEditAuditRepository labResultEditAuditRepository,
            CurrentUserProvider currentUserProvider,
            ReferenceRangeRepository referenceRangeRepository) {
        this.repository = repository;
        this.labResultEditAuditRepository = labResultEditAuditRepository;
        this.currentUserProvider = currentUserProvider;
        this.referenceRangeRepository = referenceRangeRepository;
    }

    /**
     * Enters a single lab result, applies auto-validation logic, and saves it.
     *
     * @param request the result entry request data
     * @return the saved LabResult entity
     */
    @Transactional
    public LabResult enterResult(ResultEntryRequest request) {
        // 1. Fetch the specific result row
        LabResult result = repository.findById(request.resultId())
                .orElseThrow(() -> new RuntimeException("Result ID not found"));

        TestDefinition test = result.getTestDefinition();

        // 2. Save the value
        result.setResultValue(request.value());

        // 3. Auto-Validation Logic
        try {
            java.math.BigDecimal val = new java.math.BigDecimal(request.value());

            ReferenceRange matchingRule = findMatchingRange(test, result.getLabOrder().getPatient());
            if (matchingRule != null && matchingRule.getMinVal() != null && matchingRule.getMaxVal() != null) {
                if (val.compareTo(matchingRule.getMinVal()) < 0) {
                    result.setAbnormal(true);
                    result.setRemarks("LOW");
                } else if (val.compareTo(matchingRule.getMaxVal()) > 0) {
                    result.setAbnormal(true);
                    result.setRemarks("HIGH");
                } else {
                    result.setAbnormal(false);
                    result.setRemarks("Normal");
                }
            }
        } catch (NumberFormatException e) {
            // If the result is text (e.g., "Positive"), we can't check ranges
            result.setAbnormal(false);
        }

        return repository.save(result);
    }

    /**
     * Saves all lab results from a form, applies validation and audit logic, and
     * updates order status.
     *
     * @param orderForm the LabOrder containing results to save
     */
    @Transactional
    public void saveResultsFromForm(LabOrder orderForm) {

        // 1. Security Check
        LabOrder labOrder = orderRepo.findById(orderForm.getId())
                .orElseThrow(() -> new RuntimeException("The Order not found"));
        if (labOrder.isReportDelivered()) {
            throw new RuntimeException("⛔ ILLEGAL ACTION: Cannot modify results after report delivery.");
        }

        // 1. Get Current User
        String currentUser = currentUserProvider.getUsername();

        // Loop through the results submitted from the screen
        for (LabResult resultFromForm : orderForm.getResults()) {

            // Fetch the real result from DB
            LabResult dbResult = repository.findById(resultFromForm.getId()).orElseThrow();
            String val = resultFromForm.getResultValue();

            // =========================================================
            // FIX START: Only update if the new value is NOT Empty/Null
            // =========================================================
            if (val != null && !val.trim().isEmpty()) {

                dbResult.setResultValue(val);

                // Audit Stamp
                dbResult.setPerformedBy(currentUser);
                dbResult.setPerformedAt(LocalDateTime.now());

                // --- Apply High/Low Logic (Moved inside the check) ---
                TestDefinition test = dbResult.getTestDefinition();

                try {
                    // 2. Parse Number
                    java.math.BigDecimal numVal = new java.math.BigDecimal(val);

                    // 3. Get Patient
                    com.qdc.lims.entity.Patient patient = dbResult.getLabOrder().getPatient();

                    ReferenceRange matchingRule = findMatchingRange(test, patient);

                    // 5. Apply High/Low Logic
                    if (matchingRule != null) {
                        if (numVal.compareTo(matchingRule.getMinVal()) < 0) {
                            dbResult.setAbnormal(true);
                            dbResult.setRemarks("LOW");
                        } else if (numVal.compareTo(matchingRule.getMaxVal()) > 0) {
                            dbResult.setAbnormal(true);
                            dbResult.setRemarks("HIGH");
                        } else {
                            dbResult.setAbnormal(false);
                            dbResult.setRemarks("Normal");
                        }
                    } else {
                        dbResult.setAbnormal(false);
                        dbResult.setRemarks("");
                    }
                } catch (NumberFormatException e) {
                    // Handle Non-Numeric Results (Text like "Positive")
                    dbResult.setAbnormal(false);
                    dbResult.setRemarks("");
                }

                // Save only the modified result
                repository.save(dbResult);
            }
            // =========================================================
            // FIX END
            // =========================================================
        }

        // --- LOGIC UPDATE: Only Mark "COMPLETED" if ALL tests are done ---
        // (Optional improvement: Prevent partial orders being marked complete)
        LabOrder dbOrder = orderRepo.findById(orderForm.getId()).orElseThrow();

        boolean allTestsDone = dbOrder.getResults().stream()
                .allMatch(this::hasEnteredResult);
        boolean anyTestStarted = dbOrder.getResults().stream()
                .anyMatch(this::hasStartedActivity);

        if (anyTestStarted && dbOrder.getLabStartedAt() == null) {
            dbOrder.setLabStartedAt(LocalDateTime.now());
        }

        if (allTestsDone) {
            dbOrder.setStatus("COMPLETED");
        } else if (anyTestStarted || dbOrder.getLabStartedAt() != null) {
            dbOrder.setStatus("IN_PROGRESS");
        } else {
            dbOrder.setStatus("PENDING");
        }
        orderRepo.save(dbOrder);
    }

    /**
     * Saves edits to results for a completed order and records audit metadata.
     *
     * @param orderForm   the LabOrder containing edited results
     * @param editReason  reason for editing (required for completed-result edits)
     */
    @Transactional
    public void saveEditedResults(LabOrder orderForm, String editReason) {
        LabOrder labOrder = orderRepo.findById(orderForm.getId())
                .orElseThrow(() -> new RuntimeException("The Order not found"));

        if (!"COMPLETED".equals(labOrder.getStatus())) {
            throw new RuntimeException("Only completed orders can be edited here.");
        }

        String normalizedReason = editReason != null ? editReason.trim() : "";
        if (normalizedReason.isEmpty()) {
            throw new RuntimeException("Edit reason is required for completed-result corrections.");
        }

        String currentUser = currentUserProvider.getUsername();
        LocalDateTime editedAt = LocalDateTime.now();
        int changeCount = 0;

        for (LabResult resultFromForm : orderForm.getResults()) {
            LabResult dbResult = repository.findById(resultFromForm.getId()).orElseThrow();
            String val = resultFromForm.getResultValue() != null ? resultFromForm.getResultValue().trim() : "";

            if (!val.isEmpty()) {
                String oldValue = dbResult.getResultValue();
                boolean oldAbnormal = dbResult.isAbnormal();
                String oldRemarks = dbResult.getRemarks();

                dbResult.setResultValue(val);
                dbResult.setPerformedBy(currentUser);
                dbResult.setPerformedAt(editedAt);

                TestDefinition test = dbResult.getTestDefinition();
                try {
                    java.math.BigDecimal numVal = new java.math.BigDecimal(val);
                    com.qdc.lims.entity.Patient patient = dbResult.getLabOrder().getPatient();

                    ReferenceRange matchingRule = findMatchingRange(test, patient);

                    if (matchingRule != null) {
                        if (numVal.compareTo(matchingRule.getMinVal()) < 0) {
                            dbResult.setAbnormal(true);
                            dbResult.setRemarks("LOW");
                        } else if (numVal.compareTo(matchingRule.getMaxVal()) > 0) {
                            dbResult.setAbnormal(true);
                            dbResult.setRemarks("HIGH");
                        } else {
                            dbResult.setAbnormal(false);
                            dbResult.setRemarks("Normal");
                        }
                    } else {
                        dbResult.setAbnormal(false);
                        dbResult.setRemarks("");
                    }
                } catch (NumberFormatException e) {
                    dbResult.setAbnormal(false);
                    dbResult.setRemarks("");
                }

                boolean valueChanged = !Objects.equals(normalize(oldValue), normalize(dbResult.getResultValue()));
                boolean abnormalChanged = oldAbnormal != dbResult.isAbnormal();
                boolean remarksChanged = !Objects.equals(normalize(oldRemarks), normalize(dbResult.getRemarks()));
                if (!(valueChanged || abnormalChanged || remarksChanged)) {
                    continue;
                }

                repository.save(dbResult);

                LabResultEditAudit audit = new LabResultEditAudit();
                audit.setLabOrder(labOrder);
                audit.setLabResult(dbResult);
                audit.setTestName(test != null ? test.getTestName() : null);
                audit.setPreviousValue(oldValue);
                audit.setNewValue(dbResult.getResultValue());
                audit.setPreviousRemarks(oldRemarks);
                audit.setNewRemarks(dbResult.getRemarks());
                audit.setPreviousAbnormal(oldAbnormal);
                audit.setNewAbnormal(dbResult.isAbnormal());
                audit.setEditedBy(currentUser);
                audit.setEditedAt(editedAt);
                audit.setReason(normalizedReason);
                audit.setReportDeliveredAtEdit(labOrder.isReportDelivered());
                labResultEditAuditRepository.save(audit);
                changeCount++;
            }
        }

        if (changeCount == 0) {
            throw new RuntimeException("No result changes detected to save.");
        }

        labOrder.setResultsEdited(true);
        labOrder.setResultsEditedAt(editedAt);
        labOrder.setResultsEditedBy(currentUser);
        labOrder.setResultsEditReason(normalizedReason);

        if (labOrder.isReportDelivered()) {
            labOrder.setReprintRequired(true);
        }

        orderRepo.save(labOrder);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasEnteredResult(LabResult result) {
        return result != null && result.getResultValue() != null && !result.getResultValue().trim().isEmpty();
    }

    private boolean hasStartedActivity(LabResult result) {
        return hasEnteredResult(result)
                || (result != null && result.getPerformedAt() != null)
                || (result != null && result.getPerformedBy() != null && !result.getPerformedBy().trim().isEmpty());
    }

    private ReferenceRange findMatchingRange(TestDefinition test, com.qdc.lims.entity.Patient patient) {
        if (test == null || test.getId() == null) {
            return null;
        }
        var ranges = referenceRangeRepository.findByTestId(test.getId());
        if (ranges == null || ranges.isEmpty()) {
            return null;
        }
        Integer age = patient != null ? patient.getAge() : null;
        String gender = patient != null ? patient.getGender() : null;

        return ranges.stream()
                .filter(range -> matchesRange(range, age, gender))
                .sorted((a, b) -> {
                    int genderScoreA = genderScore(a, gender);
                    int genderScoreB = genderScore(b, gender);
                    if (genderScoreA != genderScoreB) {
                        return Integer.compare(genderScoreB, genderScoreA);
                    }
                    Integer minA = a.getMinAge();
                    Integer minB = b.getMinAge();
                    if (minA == null && minB == null) {
                        return 0;
                    }
                    if (minA == null) {
                        return 1;
                    }
                    if (minB == null) {
                        return -1;
                    }
                    return Integer.compare(minA, minB);
                })
                .findFirst()
                .orElse(null);
    }

    private boolean matchesRange(ReferenceRange range, Integer age, String gender) {
        if (range == null) {
            return false;
        }
        String rangeGender = range.getGender();
        if (gender != null && rangeGender != null && !"Both".equalsIgnoreCase(rangeGender)
                && !rangeGender.equalsIgnoreCase(gender)) {
            return false;
        }
        if (age != null) {
            if (range.getMinAge() != null && age < range.getMinAge()) {
                return false;
            }
            if (range.getMaxAge() != null && age > range.getMaxAge()) {
                return false;
            }
        }
        return true;
    }

    private int genderScore(ReferenceRange range, String gender) {
        if (range == null) {
            return 0;
        }
        String rangeGender = range.getGender();
        if (gender != null && rangeGender != null && rangeGender.equalsIgnoreCase(gender)) {
            return 2;
        }
        if (rangeGender != null && "Both".equalsIgnoreCase(rangeGender)) {
            return 1;
        }
        return 0;
    }

}
