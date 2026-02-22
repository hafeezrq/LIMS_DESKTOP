package com.qdc.lims.service;

import com.qdc.lims.entity.Department;
import com.qdc.lims.entity.TestCategory;
import com.qdc.lims.entity.TestDefinition;
import com.qdc.lims.repository.DepartmentRepository;
import com.qdc.lims.repository.TestCategoryRepository;
import com.qdc.lims.repository.TestDefinitionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Application service for managing {@link TestDefinition} master data used by
 * the desktop UI.
 */
@Service
public class TestDefinitionService {

    @Autowired
    private TestDefinitionRepository testDefinitionRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private TestCategoryRepository testCategoryRepository;

    /**
     * @return all test definitions
     */
    public List<TestDefinition> findAll() {
        return testDefinitionRepository.findAll();
    }

    /**
     * Finds a test by id.
     *
     * @param id test id
     * @return matching test, if present
     */
    public Optional<TestDefinition> findById(Long id) {
        return testDefinitionRepository.findById(id);
    }

    /**
     * Saves a test definition.
     *
     * @param test test to save
     * @return saved entity
     */
    public TestDefinition save(TestDefinition test) {
        if (test.getActive() == null) {
            test.setActive(true);
        }
        return testDefinitionRepository.save(test);
    }

    /**
     * Deletes a test definition by id.
     *
     * @param id test id
     */
    public void deleteById(Long id) {
        testDefinitionRepository.deleteById(id);
    }

    /**
     * Performs a case-insensitive contains search across test names and short
     * codes.
     *
     * @param query search text
     * @return matching tests
     */
    public List<TestDefinition> searchTests(String query) {
        return testDefinitionRepository.findAll().stream()
                .filter(t -> t.getTestName().toLowerCase().contains(query.toLowerCase()) ||
                        (t.getShortCode() != null && t.getShortCode().toLowerCase().contains(query.toLowerCase())))
                .toList();
    }

    /**
     * @return all departments/categories
     */
    public List<Department> findAllDepartments() {
        return departmentRepository.findAll();
    }

    /**
     * @param department owning department
     * @return categories under the department
     */
    public List<TestCategory> findCategoriesByDepartment(Department department) {
        if (department == null) {
            return List.of();
        }
        return testCategoryRepository.findByDepartment(department);
    }

    /**
     * Finds or creates a category for the given department.
     *
     * @param name category name
     * @param department owning department
     * @return persisted category, or null if name/department missing
     */
    public TestCategory findOrCreateCategory(String name, Department department) {
        if (name == null || name.trim().isEmpty() || department == null) {
            return null;
        }
        String trimmed = name.trim();
        return testCategoryRepository.findByNameAndDepartment(trimmed, department)
                .orElseGet(() -> {
                    TestCategory created = new TestCategory();
                    created.setName(trimmed);
                    created.setDepartment(department);
                    created.setActive(true);
                    return testCategoryRepository.save(created);
                });
    }
}
