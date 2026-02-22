package com.qdc.lims.ui.controller;

import com.qdc.lims.entity.Patient;
import com.qdc.lims.service.PatientService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * JavaFX controller for patient registration.
 */
@Component("patientRegistrationController")
public class PatientRegistrationController {

    @FXML
    private TextField fullNameField;

    @FXML
    private TextField cnicField;

    @FXML
    private Spinner<Integer> ageSpinner;

    @FXML
    private RadioButton maleRadio;

    @FXML
    private RadioButton femaleRadio;

    @FXML
    private RadioButton otherRadio;

    @FXML
    private ToggleGroup genderGroup;

    @FXML
    private TextField mobileField;

    @FXML
    private TextField cityField;

    @FXML
    private Label messageLabel;

    private final PatientService patientService;
    private final ApplicationContext springContext;

    public PatientRegistrationController(PatientService patientService, ApplicationContext springContext) {
        this.patientService = patientService;
        this.springContext = springContext;
    }

    @FXML
    private void initialize() {
        messageLabel.setText("");
        ageSpinner.getValueFactory().setValue(1);
        ageSpinner.getEditor().clear();
        ageSpinner.getEditor().setPromptText("Enter age");

        // Add Enter key support for all text fields
        fullNameField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                handleRegister();
            }
        });

        cnicField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                handleRegister();
            }
        });

        mobileField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                handleRegister();
            }
        });

        cityField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                handleRegister();
            }
        });

        // Add Enter key support for age spinner (when user types)
        ageSpinner.getEditor().setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                // First commit the typed value to the spinner
                try {
                    int value = Integer.parseInt(ageSpinner.getEditor().getText());
                    ageSpinner.getValueFactory().setValue(value);
                } catch (NumberFormatException ignored) {
                    // Invalid number, will be caught by validation
                }
                handleRegister();
            }
        });

        // Add keyboard shortcuts for radio buttons (Alt+M, Alt+F, Alt+O)
        maleRadio.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                handleRegister();
            }
        });

        femaleRadio.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                handleRegister();
            }
        });

        otherRadio.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                handleRegister();
            }
        });
    }

    @FXML
    private void handleRegister() {
        // Clear previous messages
        messageLabel.setText("");
        messageLabel.setStyle("");

        // Validation
        String fullName = fullNameField.getText().trim();
        Integer age = parseAge();

        if (fullName.isEmpty()) {
            showError("Full name is required");
            return;
        }

        if (age == null || age <= 0) {
            showError("Please enter a valid age (greater than 0)");
            return;
        }

        // Check if gender is selected
        if (genderGroup.getSelectedToggle() == null) {
            showError("Please select a gender");
            return;
        }

        // Get gender
        String gender = "Male"; // default
        if (maleRadio.isSelected()) {
            gender = "Male";
        } else if (femaleRadio.isSelected()) {
            gender = "Female";
        } else if (otherRadio.isSelected()) {
            gender = "Other";
        }

        // Create patient entity
        Patient patient = new Patient();
        patient.setFullName(fullName);
        patient.setCnic(cnicField.getText().trim());
        patient.setAge(age);
        patient.setGender(gender);
        patient.setMobileNumber(mobileField.getText().trim());
        patient.setCity(cityField.getText().trim());

        // Register patient
        try {
            Patient savedPatient = patientService.registerPatient(patient);
            // Show success with option to create order
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Success");
            alert.setHeaderText("Patient Registered Successfully!");
            alert.setContentText("MRN: " + savedPatient.getMrn() + "\nName: " + savedPatient.getFullName() +
                    "\n\nWould you like to create a lab order for this patient?");

            ButtonType createOrderBtn = new ButtonType("Create Order");
            ButtonType registerAnotherBtn = new ButtonType("Register Another");
            ButtonType closeBtn = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);

            alert.getButtonTypes().setAll(createOrderBtn, registerAnotherBtn, closeBtn);

            alert.showAndWait().ifPresent(response -> {
                if (response == createOrderBtn) {
                    openOrderCreationWithPatient(savedPatient);
                } else if (response == registerAnotherBtn) {
                    handleClear();
                }
            });
        } catch (Exception e) {
            showError("Failed to register patient: " + e.getMessage());
        }
    }

    @FXML
    private void handleClear() {
        fullNameField.clear();
        cnicField.clear();
        ageSpinner.getValueFactory().setValue(1);
        ageSpinner.getEditor().clear();
        genderGroup.selectToggle(null); // Deselect all gender options
        mobileField.clear();
        cityField.clear();
        messageLabel.setText("");
        messageLabel.setStyle("");
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) fullNameField.getScene().getWindow();
        stage.close();
    }

    private void showError(String message) {
        messageLabel.setText("❌ " + message);
        messageLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
    }

    private Integer parseAge() {
        String ageText = ageSpinner.getEditor().getText() != null
                ? ageSpinner.getEditor().getText().trim()
                : "";
        if (ageText.isEmpty()) {
            return null;
        }
        try {
            Integer age = Integer.parseInt(ageText);
            ageSpinner.getValueFactory().setValue(age);
            return age;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @SuppressWarnings("unused")
    private void showSuccess(String message) {
        messageLabel.setText("✓ " + message);
        messageLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
    }

    private void openOrderCreationWithPatient(Patient patient) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/create_order.fxml"));
            loader.setControllerFactory(springContext::getBean);
            Parent root = loader.load();

            // Get the controller and set the patient
            CreateOrderController orderController = loader.getController();
            orderController.setPreselectedPatient(patient);

            Stage stage = new Stage();
            stage.setTitle("Create Lab Order");
            stage.setScene(new Scene(root, 900, 800));
            stage.show();

            // Close this registration window
            handleClose();
        } catch (Exception e) {
            e.printStackTrace();
            showError("Failed to open order creation: " + e.getMessage());
        }
    }
}
