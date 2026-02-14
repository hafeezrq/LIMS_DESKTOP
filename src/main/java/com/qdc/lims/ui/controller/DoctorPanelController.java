package com.qdc.lims.ui.controller;

import com.qdc.lims.entity.Doctor;
import com.qdc.lims.repository.DoctorRepository;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Controller for the Doctor Management Panel.
 * Handles CRUD operations for doctors including commission management.
 */
@Component
public class DoctorPanelController {

    @FXML
    private TextField searchField;

    @FXML
    private TableView<Doctor> doctorTable;

    @FXML
    private TableColumn<Doctor, Long> idColumn;

    @FXML
    private TableColumn<Doctor, String> nameColumn;

    @FXML
    private TableColumn<Doctor, String> clinicColumn;

    @FXML
    private TableColumn<Doctor, String> mobileColumn;

    @FXML
    private TableColumn<Doctor, String> commissionColumn;

    @FXML
    private TableColumn<Doctor, String> statusColumn;

    @FXML
    private TableColumn<Doctor, Void> actionsColumn;

    @FXML
    private Label totalDoctorsLabel;

    @FXML
    private Label activeDoctorsLabel;

    @FXML
    private Label inactiveDoctorsLabel;

    @FXML
    private Label statusLabel;

    private final DoctorRepository doctorRepository;
    private ObservableList<Doctor> doctorList;
    private ObservableList<Doctor> filteredList;

    public DoctorPanelController(DoctorRepository doctorRepository) {
        this.doctorRepository = doctorRepository;
    }

    @FXML
    private void initialize() {
        setupTableColumns();
        loadDoctors();
        updateStatistics();
    }

    /**
     * Setup table columns with cell value factories and styling.
     */
    private void setupTableColumns() {
        // ID Column
        idColumn.setCellValueFactory(
                data -> new javafx.beans.property.SimpleLongProperty(data.getValue().getId()).asObject());

        // Name Column
        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));

        // Clinic Column
        clinicColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getClinicName() != null ? data.getValue().getClinicName() : "N/A"));

        // Mobile Column
        mobileColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getMobile() != null ? data.getValue().getMobile() : "N/A"));

        // Commission Column
        commissionColumn.setCellValueFactory(data -> {
            BigDecimal commission = data.getValue().getCommissionPercentage();
            if (commission == null) {
                return new SimpleStringProperty("0.0%");
            }
            BigDecimal rounded = commission.setScale(1, RoundingMode.HALF_UP);
            return new SimpleStringProperty(rounded.toPlainString() + "%");
        });

        // Status Column with color coding
        statusColumn.setCellValueFactory(
                data -> new SimpleStringProperty(data.getValue().isActive() ? "Active" : "Inactive"));

        statusColumn.setCellFactory(column -> new TableCell<Doctor, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.equals("Active")) {
                        setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                    }
                }
            }
        });

        // Actions Column with Edit/Delete/Toggle buttons
        actionsColumn.setCellFactory(column -> new TableCell<Doctor, Void>() {
            private final Button editBtn = new Button("✏️ Edit");
            private final Button toggleBtn = new Button("🔄");
            private final Button deleteBtn = new Button("🗑️");
            private final HBox actionBox = new HBox(5, editBtn, toggleBtn, deleteBtn);

            {
                actionBox.setAlignment(Pos.CENTER);

                editBtn.setStyle(
                        "-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 10; -fx-padding: 4 8;");
                toggleBtn.setStyle(
                        "-fx-background-color: #f39c12; -fx-text-fill: white; -fx-font-size: 10; -fx-padding: 4 8;");
                deleteBtn.setStyle(
                        "-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-size: 10; -fx-padding: 4 8;");

                editBtn.setOnAction(event -> {
                    Doctor doctor = getTableView().getItems().get(getIndex());
                    handleEditDoctor(doctor);
                });

                toggleBtn.setOnAction(event -> {
                    Doctor doctor = getTableView().getItems().get(getIndex());
                    handleToggleStatus(doctor);
                });

                deleteBtn.setOnAction(event -> {
                    Doctor doctor = getTableView().getItems().get(getIndex());
                    handleDeleteDoctor(doctor);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    Doctor doctor = getTableView().getItems().get(getIndex());
                    toggleBtn.setText(doctor.isActive() ? "⏸️ Deactivate" : "▶️ Activate");
                    toggleBtn.setTooltip(new Tooltip(doctor.isActive() ? "Deactivate doctor" : "Activate doctor"));
                    editBtn.setTooltip(new Tooltip("Edit doctor details"));
                    deleteBtn.setTooltip(new Tooltip("Delete doctor"));
                    setGraphic(actionBox);
                }
            }
        });

        // Enable row selection
        doctorTable.setRowFactory(tv -> {
            TableRow<Doctor> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    handleEditDoctor(row.getItem());
                }
            });
            return row;
        });
    }

    /**
     * Load all doctors from database.
     */
    private void loadDoctors() {
        try {
            List<Doctor> doctors = doctorRepository.findAll();
            doctorList = FXCollections.observableArrayList(doctors);
            filteredList = FXCollections.observableArrayList(doctors);
            doctorTable.setItems(filteredList);

            statusLabel.setText("Loaded " + doctors.size() + " doctor(s)");
            statusLabel.setStyle("-fx-text-fill: #27ae60;");
        } catch (Exception e) {
            statusLabel.setText("Error loading doctors: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: #e74c3c;");
            showAlert("Error", "Failed to load doctors", e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    /**
     * Update statistics labels.
     */
    private void updateStatistics() {
        long total = doctorList != null ? doctorList.size() : 0;
        long active = doctorList != null ? doctorList.stream().filter(Doctor::isActive).count() : 0;
        long inactive = total - active;

        totalDoctorsLabel.setText(String.valueOf(total));
        activeDoctorsLabel.setText(String.valueOf(active));
        inactiveDoctorsLabel.setText(String.valueOf(inactive));
    }

    /**
     * Handle search functionality.
     */
    @FXML
    private void handleSearch() {
        String searchText = searchField.getText().toLowerCase().trim();

        if (searchText.isEmpty()) {
            filteredList.setAll(doctorList);
        } else {
            filteredList.setAll(doctorList.stream()
                    .filter(doctor -> doctor.getName().toLowerCase().contains(searchText) ||
                            (doctor.getClinicName() != null
                                    && doctor.getClinicName().toLowerCase().contains(searchText))
                            ||
                            (doctor.getMobile() != null && doctor.getMobile().contains(searchText)))
                    .toList());
        }

        statusLabel.setText("Found " + filteredList.size() + " doctor(s)");
    }

    /**
     * Handle add new doctor.
     */
    @FXML
    private void handleAddDoctor() {
        Dialog<Doctor> dialog = createDoctorDialog(null);
        Optional<Doctor> result = dialog.showAndWait();

        result.ifPresent(doctor -> {
            try {
                Doctor savedDoctor = doctorRepository.save(doctor);
                doctorList.add(savedDoctor);
                filteredList.add(savedDoctor);
                updateStatistics();

                statusLabel.setText("Doctor added successfully: " + savedDoctor.getName());
                statusLabel.setStyle("-fx-text-fill: #27ae60;");

                showAlert("Success", "Doctor Added",
                        "Dr. " + savedDoctor.getName() + " has been added successfully!",
                        Alert.AlertType.INFORMATION);
            } catch (Exception e) {
                statusLabel.setText("Error adding doctor: " + e.getMessage());
                statusLabel.setStyle("-fx-text-fill: #e74c3c;");
                showAlert("Error", "Failed to add doctor", e.getMessage(), Alert.AlertType.ERROR);
            }
        });
    }

    /**
     * Handle edit doctor.
     */
    private void handleEditDoctor(Doctor doctor) {
        Dialog<Doctor> dialog = createDoctorDialog(doctor);
        Optional<Doctor> result = dialog.showAndWait();

        result.ifPresent(updatedDoctor -> {
            try {
                updatedDoctor.setId(doctor.getId());
                Doctor savedDoctor = doctorRepository.save(updatedDoctor);

                // Update in list
                int index = doctorList.indexOf(doctor);
                if (index >= 0) {
                    doctorList.set(index, savedDoctor);
                }

                // Refresh filtered list
                handleSearch();
                updateStatistics();
                doctorTable.refresh();

                statusLabel.setText("Doctor updated successfully: " + savedDoctor.getName());
                statusLabel.setStyle("-fx-text-fill: #27ae60;");
            } catch (Exception e) {
                statusLabel.setText("Error updating doctor: " + e.getMessage());
                statusLabel.setStyle("-fx-text-fill: #e74c3c;");
                showAlert("Error", "Failed to update doctor", e.getMessage(), Alert.AlertType.ERROR);
            }
        });
    }

    /**
     * Handle toggle doctor active status.
     */
    private void handleToggleStatus(Doctor doctor) {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Confirm Status Change");
        confirmAlert.setHeaderText(doctor.isActive() ? "Deactivate Doctor?" : "Activate Doctor?");
        confirmAlert.setContentText("Are you sure you want to " +
                (doctor.isActive() ? "deactivate" : "activate") + " Dr. " + doctor.getName() + "?");

        confirmAlert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    doctor.setActive(!doctor.isActive());
                    doctorRepository.save(doctor);

                    doctorTable.refresh();
                    updateStatistics();

                    statusLabel.setText("Doctor " + (doctor.isActive() ? "activated" : "deactivated") +
                            " successfully: " + doctor.getName());
                    statusLabel.setStyle("-fx-text-fill: #27ae60;");
                } catch (Exception e) {
                    statusLabel.setText("Error changing status: " + e.getMessage());
                    statusLabel.setStyle("-fx-text-fill: #e74c3c;");
                    showAlert("Error", "Failed to change status", e.getMessage(), Alert.AlertType.ERROR);
                }
            }
        });
    }

    /**
     * Handle delete doctor.
     */
    private void handleDeleteDoctor(Doctor doctor) {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Confirm Deletion");
        confirmAlert.setHeaderText("Delete Doctor?");
        confirmAlert.setContentText("Are you sure you want to delete Dr. " + doctor.getName() +
                "?\n\nThis action cannot be undone!");

        confirmAlert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    doctorRepository.delete(doctor);
                    doctorList.remove(doctor);
                    filteredList.remove(doctor);
                    updateStatistics();

                    statusLabel.setText("Doctor deleted successfully: " + doctor.getName());
                    statusLabel.setStyle("-fx-text-fill: #27ae60;");
                } catch (Exception e) {
                    statusLabel.setText("Error deleting doctor: " + e.getMessage());
                    statusLabel.setStyle("-fx-text-fill: #e74c3c;");
                    showAlert("Error", "Failed to delete doctor", e.getMessage(), Alert.AlertType.ERROR);
                }
            }
        });
    }

    /**
     * Handle refresh button.
     */
    @FXML
    private void handleRefresh() {
        searchField.clear();
        loadDoctors();
        updateStatistics();
        statusLabel.setText("Data refreshed");
        statusLabel.setStyle("-fx-text-fill: #3498db;");
    }

    /**
     * Handle close button.
     */
    @FXML
    private void handleClose() {
        com.qdc.lims.ui.util.ViewCloseUtil.closeCurrentTabOrWindow(doctorTable);
    }

    /**
     * Create dialog for adding/editing doctor.
     */
    private Dialog<Doctor> createDoctorDialog(Doctor existingDoctor) {
        Dialog<Doctor> dialog = new Dialog<>();
        dialog.setTitle(existingDoctor == null ? "Add New Doctor" : "Edit Doctor");
        dialog.setHeaderText(existingDoctor == null ? "Enter doctor details" : "Update doctor details");

        // Create form
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20));

        TextField nameField = new TextField();
        nameField.setPromptText("Dr. John Smith");
        TextField clinicField = new TextField();
        clinicField.setPromptText("City Medical Clinic");
        TextField mobileField = new TextField();
        mobileField.setPromptText("03XX-XXXXXXX");
        TextField commissionField = new TextField();
        commissionField.setPromptText("10.0");
        CheckBox activeCheckBox = new CheckBox("Active");
        activeCheckBox.setSelected(true);

        // Pre-fill if editing
        if (existingDoctor != null) {
            nameField.setText(existingDoctor.getName());
            clinicField.setText(existingDoctor.getClinicName());
            mobileField.setText(existingDoctor.getMobile());
            commissionField.setText(existingDoctor.getCommissionPercentage() != null
                    ? existingDoctor.getCommissionPercentage().toPlainString()
                    : "0.0");
            activeCheckBox.setSelected(existingDoctor.isActive());
        }

        grid.add(new Label("Doctor Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Clinic Name:"), 0, 1);
        grid.add(clinicField, 1, 1);
        grid.add(new Label("Mobile:"), 0, 2);
        grid.add(mobileField, 1, 2);
        grid.add(new Label("Commission %:"), 0, 3);
        grid.add(commissionField, 1, 3);
        grid.add(new Label("Status:"), 0, 4);
        grid.add(activeCheckBox, 1, 4);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Convert result
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                Doctor doctor = new Doctor();
                doctor.setName(nameField.getText().trim());
                doctor.setClinicName(clinicField.getText().trim());
                doctor.setMobile(mobileField.getText().trim());
                try {
                    BigDecimal commission = new BigDecimal(commissionField.getText().trim());
                    doctor.setCommissionPercentage(commission);
                } catch (NumberFormatException e) {
                    doctor.setCommissionPercentage(BigDecimal.ZERO);
                }
                doctor.setActive(activeCheckBox.isSelected());
                return doctor;
            }
            return null;
        });

        // Validation
        nameField.textProperty().addListener((obs, old, newVal) -> {
            dialog.getDialogPane().lookupButton(ButtonType.OK).setDisable(newVal.trim().isEmpty());
        });

        return dialog;
    }

    /**
     * Show alert dialog.
     */
    private void showAlert(String title, String header, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
