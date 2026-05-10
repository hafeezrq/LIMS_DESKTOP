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
import javafx.application.Platform;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * JavaFX controller for patient registration.
 */
@Component("patientRegistrationController")
public class PatientRegistrationController {
    private boolean updatingNameField;

    @FXML
    private TextField fullNameField;

    @FXML
    private TextField cnicField;

    @FXML
    private Spinner<Integer> ageSpinner;

    @FXML
    private ComboBox<String> ageUnitCombo;

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

    @FXML
    private Button registerButton;

    @FXML
    private Button clearButton;

    @FXML
    private Button closeButton;

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
        ageUnitCombo.getItems().setAll("Years", "Months", "Days");
        ageUnitCombo.setValue("Years");
        setupNameAutoCapitalization();
        setupKeyboardFlow();
        setupRegisterButtonState();
        setupEscHandler();
        setupButtonFocusIndicators();
        setupButtonTabCycle();
        Platform.runLater(fullNameField::requestFocus);
    }

    private void setupNameAutoCapitalization() {
        fullNameField.textProperty().addListener((obs, oldValue, newValue) -> {
            if (updatingNameField || newValue == null || newValue.isEmpty()) {
                return;
            }

            String capitalized = capitalizeWordInitials(newValue);
            if (capitalized.equals(newValue)) {
                return;
            }

            int caret = fullNameField.getCaretPosition();
            updatingNameField = true;
            fullNameField.setText(capitalized);
            fullNameField.positionCaret(Math.min(caret, capitalized.length()));
            updatingNameField = false;
        });
    }

    @FXML
    private void handleRegister() {
        // Clear previous messages
        messageLabel.setText("");
        messageLabel.setStyle("");

        // Validation
        String fullName = normalizeName(fullNameField.getText());
        Integer age = parseAge();
        List<String> missing = new ArrayList<>();

        if (fullName.isEmpty()) {
            missing.add("Full name");
        }
        if (age == null || age <= 0) {
            missing.add("Valid age");
        }
        if (genderGroup.getSelectedToggle() == null) {
            missing.add("Gender");
        }
        if (!missing.isEmpty()) {
            showError("Missing required: " + String.join(", ", missing));
            if (missing.contains("Full name")) {
                fullNameField.requestFocus();
            } else if (missing.contains("Valid age")) {
                ageSpinner.getEditor().requestFocus();
            } else {
                maleRadio.requestFocus();
            }
            return;
        }

        fullNameField.setText(fullName);

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
        patient.setAgeUnit(ageUnitCombo.getValue() != null ? ageUnitCombo.getValue() : "Years");
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
            Platform.runLater(() -> {
                Button createOrderButton = (Button) alert.getDialogPane().lookupButton(createOrderBtn);
                if (createOrderButton != null) {
                    createOrderButton.setDefaultButton(true);
                    createOrderButton.requestFocus();
                }
            });

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
        if (!confirmDiscardIfDirty("Clear Form", "Clear entered data?")) {
            return;
        }
        clearFormInternal();
        fullNameField.requestFocus();
    }

    @FXML
    private void handleClose() {
        if (!confirmDiscardIfDirty("Close Registration", "Close this form?")) {
            return;
        }
        Stage stage = (Stage) fullNameField.getScene().getWindow();
        stage.close();
    }

    private void clearFormInternal() {
        fullNameField.clear();
        cnicField.clear();
        ageSpinner.getValueFactory().setValue(1);
        ageSpinner.getEditor().clear();
        ageUnitCombo.setValue("Years");
        genderGroup.selectToggle(null); // Deselect all gender options
        mobileField.clear();
        cityField.clear();
        messageLabel.setText("");
        messageLabel.setStyle("");
        updateRegisterButtonState();
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

    private void setupKeyboardFlow() {
        fullNameField.setOnAction(event -> cnicField.requestFocus());
        cnicField.setOnAction(event -> ageSpinner.getEditor().requestFocus());
        ageSpinner.getEditor().setOnAction(event -> {
            parseAge();
            ageUnitCombo.requestFocus();
        });
        ageUnitCombo.setOnAction(event -> maleRadio.requestFocus());
        maleRadio.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                maleRadio.setSelected(true);
                mobileField.requestFocus();
                event.consume();
            }
        });
        femaleRadio.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                femaleRadio.setSelected(true);
                mobileField.requestFocus();
                event.consume();
            }
        });
        otherRadio.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                otherRadio.setSelected(true);
                mobileField.requestFocus();
                event.consume();
            }
        });
        mobileField.setOnAction(event -> cityField.requestFocus());
        cityField.setOnAction(event -> registerButton.requestFocus());
        registerButton.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER && !registerButton.isDisabled()) {
                handleRegister();
                event.consume();
            }
        });
    }

    private void setupRegisterButtonState() {
        registerButton.setDisable(true);
        registerButton.setDefaultButton(false);
        registerButton.setFocusTraversable(true);
        clearButton.setFocusTraversable(true);
        closeButton.setFocusTraversable(true);
        fullNameField.textProperty().addListener((obs, old, val) -> updateRegisterButtonState());
        ageSpinner.getEditor().textProperty().addListener((obs, old, val) -> updateRegisterButtonState());
        genderGroup.selectedToggleProperty().addListener((obs, old, val) -> updateRegisterButtonState());
    }

    private void updateRegisterButtonState() {
        boolean valid = !normalizeName(fullNameField.getText()).isEmpty()
                && parseAge() != null
                && parseAge() > 0
                && genderGroup.getSelectedToggle() != null;
        registerButton.setDisable(!valid);
        registerButton.setDefaultButton(valid);
    }

    private void setupEscHandler() {
        fullNameField.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                return;
            }
            newScene.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.ESCAPE) {
                    handleClose();
                    event.consume();
                }
            });
        });
    }

    private void setupButtonFocusIndicators() {
        registerButton.focusedProperty().addListener((obs, old, focused) ->
                applyFocusStyle(registerButton, focused));
        clearButton.focusedProperty().addListener((obs, old, focused) ->
                applyFocusStyle(clearButton, focused));
        closeButton.focusedProperty().addListener((obs, old, focused) ->
                applyFocusStyle(closeButton, focused));
    }

    private void applyFocusStyle(Button button, boolean focused) {
        if (button == null) {
            return;
        }
        if (focused) {
            button.setStyle("-fx-border-color: #f39c12; -fx-border-width: 2; -fx-border-radius: 4;");
        } else {
            button.setStyle("");
        }
    }

    private void setupButtonTabCycle() {
        cityField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.TAB && !event.isShiftDown()) {
                registerButton.requestFocus();
                event.consume();
            }
        });

        registerButton.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.TAB) {
                if (event.isShiftDown()) {
                    cityField.requestFocus();
                } else {
                    clearButton.requestFocus();
                }
                event.consume();
            }
        });

        clearButton.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.TAB) {
                if (event.isShiftDown()) {
                    registerButton.requestFocus();
                } else {
                    closeButton.requestFocus();
                }
                event.consume();
            }
        });

        closeButton.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.TAB) {
                if (event.isShiftDown()) {
                    clearButton.requestFocus();
                } else {
                    fullNameField.requestFocus();
                }
                event.consume();
            }
        });
    }

    private boolean isFormDirty() {
        if (fullNameField.getText() != null && !fullNameField.getText().trim().isEmpty()) {
            return true;
        }
        if (cnicField.getText() != null && !cnicField.getText().trim().isEmpty()) {
            return true;
        }
        if (ageSpinner.getEditor().getText() != null && !ageSpinner.getEditor().getText().trim().isEmpty()) {
            return true;
        }
        if (genderGroup.getSelectedToggle() != null) {
            return true;
        }
        if (mobileField.getText() != null && !mobileField.getText().trim().isEmpty()) {
            return true;
        }
        return cityField.getText() != null && !cityField.getText().trim().isEmpty();
    }

    private boolean confirmDiscardIfDirty(String title, String header) {
        if (!isFormDirty()) {
            return true;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(title);
        confirm.setHeaderText(header);
        confirm.setContentText("You have entered information that will be lost.");
        Button okButton = (Button) confirm.getDialogPane().lookupButton(ButtonType.OK);
        if (okButton != null) {
            okButton.setDefaultButton(false);
        }
        return confirm.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private String normalizeName(String rawName) {
        if (rawName == null) {
            return "";
        }
        String trimmed = rawName.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return Arrays.stream(trimmed.split("\\s+"))
                .map(this::capitalizeWord)
                .collect(Collectors.joining(" "));
    }

    private String capitalizeWord(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.length() == 1) {
            return value.toUpperCase(Locale.ROOT);
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT)
                + value.substring(1).toLowerCase(Locale.ROOT);
    }

    private String capitalizeWordInitials(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        boolean capitalizeNext = true;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isLetter(ch) && capitalizeNext) {
                sb.append(Character.toUpperCase(ch));
                capitalizeNext = false;
            } else {
                sb.append(ch);
                capitalizeNext = Character.isWhitespace(ch);
            }
        }
        return sb.toString();
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
