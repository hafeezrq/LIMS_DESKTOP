package com.qdc.lims.repository;

import com.qdc.lims.entity.TestResultOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository for configured test result dropdown options.
 */
public interface TestResultOptionRepository extends JpaRepository<TestResultOption, Long> {

    List<TestResultOption> findByTestIdOrderByDisplayOrderAscOptionLabelAsc(Long testId);

    List<TestResultOption> findByTestIdAndActiveTrueOrderByDisplayOrderAscOptionLabelAsc(Long testId);

    List<TestResultOption> findByTestIdInAndActiveTrueOrderByTestIdAscDisplayOrderAscOptionLabelAsc(
            Collection<Long> testIds);

    Optional<TestResultOption> findByIdAndTestId(Long id, Long testId);

    Optional<TestResultOption> findByTestIdAndOptionCodeIgnoreCase(Long testId, String optionCode);

    void deleteByTestId(Long testId);
}
