package com.qdc.lims.repository;

import com.qdc.lims.entity.Department;
import com.qdc.lims.entity.Panel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link Panel} entities.
 */
public interface PanelRepository extends JpaRepository<Panel, Integer> {

    /**
     * Loads active panels and eagerly fetches their tests to avoid N+1 lookups in
     * the UI.
     *
     * @return active panels with their tests preloaded
     */
    @Query("SELECT DISTINCT p FROM Panel p LEFT JOIN FETCH p.tests WHERE p.active = true")
    List<Panel> findAllWithTests();

    @Query("SELECT DISTINCT p FROM Panel p LEFT JOIN FETCH p.tests")
    List<Panel> findAllWithTestsIncludingInactive();

    @Query("SELECT DISTINCT p FROM Panel p LEFT JOIN FETCH p.tests WHERE p.id IN :ids")
    List<Panel> findAllWithTestsById(@Param("ids") List<Integer> ids);

    Optional<Panel> findByPanelNameIgnoreCaseAndDepartment(String panelName, Department department);
}
