package com.qdc.lims.repository;

import com.qdc.lims.entity.Department;
import com.qdc.lims.entity.TestDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * Repository interface for TestDefinition entities, providing CRUD operations
 * and custom queries.
 */
public interface TestDefinitionRepository extends JpaRepository<TestDefinition, Long> {
    /**
     * Finds a test definition by its short code.
     *
     * @param shortCode the short code of the test (e.g., "CBC", "GLU-R")
     * @return the TestDefinition with the given short code, or null if not found
     */
    TestDefinition findByShortCode(String shortCode);

    /**
     * Counts active test definitions.
     * 
     * @return number of active tests
     */
    long countByActiveTrue();

    /**
     * Finds all active test definitions.
     * 
     * @return list of active tests
     */
    List<TestDefinition> findByActiveTrue();

    /**
     * Finds test definitions by department.
     * 
     * @param department the department name
     * @return list of tests in the department
     */
    List<TestDefinition> findByDepartment(String department);

    List<TestDefinition> findByDepartmentAndActiveTrueOrderByTestNameAsc(Department department);

    /**
     * Searches test definitions by name (case-insensitive).
     * 
     * @param testName the test name to search for
     * @return list of matching tests
     */
    List<TestDefinition> findByTestNameContainingIgnoreCase(String testName);
}
