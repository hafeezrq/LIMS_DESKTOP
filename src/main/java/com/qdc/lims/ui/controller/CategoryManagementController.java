package com.qdc.lims.ui.controller;

import com.qdc.lims.entity.Department;
import com.qdc.lims.repository.DepartmentRepository;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import java.util.Optional;

/**
 * Controller for managing {@link Department} records (labeled as categories in
 * parts of the UI).
 */
@Controller
public class CategoryManagementController {

    @FXML
    private TableView<Department> categoryTable;
    @FXML
    private TableColumn<Department, String> nameColumn;
    @FXML
    private TableColumn<Department, String> descriptionColumn;

    @FXML
    private TextField nameField;
    @FXML
    private TextArea descriptionArea;

    private final DepartmentRepository departmentRepository;
    private final ObservableList<Department> categoryList = FXCollections.observableArrayList();

    private Runnable onUpdateCallback;

    /**
     * Creates the controller.
     *
     * @param departmentRepository department repository
     */
    @Autowired
    public CategoryManagementController(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    /**
     * Initializes the table and selection behavior.
     */
    @FXML
    public void initialize() {
        setupTable();
        loadCategories();

        categoryTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> showDetails(newVal));
    }

    /**
     * Registers a callback invoked after successful create/update/delete.
     *
     * @param onUpdateCallback callback to run
     */
    public void setOnUpdateCallback(Runnable onUpdateCallback) {
        this.onUpdateCallback = onUpdateCallback;
    }

    private void setupTable() {
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        descriptionColumn.setCellValueFactory(new PropertyValueFactory<>("code"));

        categoryTable.setItems(categoryList);
    }

    private void loadCategories() {
        categoryList.clear();
        categoryList.addAll(departmentRepository.findAll());
    }

    private void showDetails(Department category) {
        if (category != null) {
            nameField.setText(category.getName());
            descriptionArea.setText(category.getCode());
        } else {
            handleClear();
        }
    }

    /**
     * Saves a new or existing department with duplicate-name protection.
     */
    @FXML
    private void handleSave() {
        String name = nameField.getText();
        if (name == null || name.trim().isEmpty()) {
            showAlert("Error", "Department name is required.");
            return;
        }

        try {
            Department category = categoryTable.getSelectionModel().getSelectedItem();
            if (category == null) {
                Optional<Department> existing = departmentRepository.findByName(name.trim());
                if (existing.isPresent()) {
                    showAlert("Error", "Department with this name already exists.");
                    return;
                }
                category = new Department();
            } else {
                if (!category.getName().equals(name.trim())) {
                    Optional<Department> existing = departmentRepository.findByName(name.trim());
                    if (existing.isPresent()) {
                        showAlert("Error", "Department with this name already exists.");
                        return;
                    }
                }
            }

            category.setName(name.trim());
            category.setCode(descriptionArea.getText() != null ? descriptionArea.getText().trim() : null);
            category.setActive(true);

            departmentRepository.save(category);
            loadCategories();
            handleClear();

            if (onUpdateCallback != null) {
                onUpdateCallback.run();
            }

        } catch (Exception e) {
            showAlert("Error", "Failed to save department: " + e.getMessage());
        }
    }

    /**
     * Deletes the selected department after confirmation.
     */
    @FXML
    private void handleDelete() {
        Department selected = categoryTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Warning", "Please select a department to delete.");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Department");
        alert.setHeaderText("Are you sure?");
        alert.setContentText("Delete department: " + selected.getName() + "?");

        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                departmentRepository.delete(selected);
                loadCategories();
                handleClear();

                if (onUpdateCallback != null) {
                    onUpdateCallback.run();
                }
            } catch (Exception e) {
                showAlert("Error", "Cannot delete department (it may be in use).");
            }
        }
    }

    /**
     * Clears the form and table selection.
     */
    @FXML
    private void handleClear() {
        nameField.clear();
        descriptionArea.clear();
        categoryTable.getSelectionModel().clearSelection();
    }

    /**
     * Closes the window.
     */
    @FXML
    private void handleClose() {
        com.qdc.lims.ui.util.ViewCloseUtil.closeCurrentTabOrWindow(nameField);
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
