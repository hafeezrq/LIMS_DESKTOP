package com.qdc.lims.ui.controller;

import com.qdc.lims.entity.ReferenceRange;
import com.qdc.lims.entity.TestDefinition;
import com.qdc.lims.repository.ReferenceRangeRepository;
import com.qdc.lims.repository.TestDefinitionRepository;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.StringConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Optional;

/**
 * Controller for managing gender and age-specific {@link ReferenceRange}
 * entries for a selected {@link TestDefinition}.
 */
@Component
public class ReferenceRangeController {

    @Autowired
    private ReferenceRangeRepository referenceRangeRepository;

    @Autowired
    private TestDefinitionRepository testDefinitionRepository;

    @FXML
    private Label testNameLabel;

    @FXML
    private ComboBox<TestDefinition> testCombo;

    @FXML
    private TableView<ReferenceRange> rangesTable;

    @FXML
    private TableColumn<ReferenceRange, String> genderColumn;

    @FXML
    private TableColumn<ReferenceRange, String> ageRangeColumn;

    @FXML
    private TableColumn<ReferenceRange, String> normalRangeColumn;

    @FXML
    private ComboBox<String> genderCombo;

    @FXML
    private TextField minAgeField;

    @FXML
    private TextField maxAgeField;

    @FXML
    private TextField minValField;

    @FXML
    private TextField maxValField;

    @FXML
    private Button addButton;

    @FXML
    private Button deleteButton;

    private TestDefinition currentTest;
    private final ObservableList<ReferenceRange> rangesList = FXCollections.observableArrayList();

    /**
     * Initializes table bindings and form restrictions.
     */
    @FXML
    public void initialize() {
        setupTable();
        setupForm();
        setupTestSelector();
    }

    /**
     * Sets the current test and loads its reference ranges.
     *
     * @param test selected test definition
     */
    public void setTestDefinition(TestDefinition test) {
        this.currentTest = test;
        if (test != null) {
            testNameLabel.setText("Reference Ranges for: " + test.getTestName());
            loadRanges();
        } else {
            testNameLabel.setText("For Test: ...");
            rangesList.clear();
        }
    }

    private void setupTable() {
        genderColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getGender()));

        ageRangeColumn.setCellValueFactory(cellData -> {
            ReferenceRange r = cellData.getValue();
            return new SimpleStringProperty(r.getMinAge() + " - " + r.getMaxAge() + " yrs");
        });

        normalRangeColumn.setCellValueFactory(cellData -> {
            ReferenceRange r = cellData.getValue();
            String unit = currentTest != null && currentTest.getUnit() != null ? currentTest.getUnit() : "";
            return new SimpleStringProperty(r.getMinVal() + " - " + r.getMaxVal() + " " + unit);
        });

        rangesTable.setItems(rangesList);

        rangesTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            deleteButton.setDisable(newVal == null);
        });
    }

    private void setupForm() {
        genderCombo.getItems().addAll("Both", "Male", "Female");
        genderCombo.setValue("Both");

        setupNumericField(minAgeField);
        setupNumericField(maxAgeField);
        setupDecimalField(minValField);
        setupDecimalField(maxValField);
    }

    private void setupTestSelector() {
        testCombo.setItems(FXCollections.observableArrayList(
                testDefinitionRepository.findAll().stream()
                        .sorted(Comparator.comparing(TestDefinition::getTestName, String.CASE_INSENSITIVE_ORDER))
                        .toList()));
        testCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(TestDefinition test) {
                return test != null ? test.getTestName() : "";
            }

            @Override
            public TestDefinition fromString(String string) {
                return testCombo.getItems().stream()
                        .filter(t -> t.getTestName().equalsIgnoreCase(string))
                        .findFirst()
                        .orElse(null);
            }
        });
        testCombo.valueProperty().addListener((obs, oldVal, newVal) -> setTestDefinition(newVal));
        addButton.disableProperty().bind(Bindings.isNull(testCombo.valueProperty()));
    }

    private void setupNumericField(TextField field) {
        field.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*")) {
                field.setText(newValue.replaceAll("[^\\d]", ""));
            }
        });
    }

    private void setupDecimalField(TextField field) {
        field.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*(\\.\\d*)?")) {
                field.setText(oldValue);
            }
        });
    }

    private void loadRanges() {
        refreshData();
    }

    /**
     * Reloads reference ranges from the repository for the current test.
     */
    public void refreshData() {
        rangesList.clear();
        if (currentTest != null && currentTest.getId() != null) {
            rangesList.addAll(referenceRangeRepository.findByTestId(currentTest.getId()));
        }
    }

    /**
     * Validates and saves a new reference range entry.
     */
    @FXML
    private void handleAdd() {
        if (currentTest == null) {
            return;
        }

        if (minAgeField.getText().isEmpty() || maxAgeField.getText().isEmpty() ||
                minValField.getText().isEmpty() || maxValField.getText().isEmpty()) {
            showAlert("Validation Error", "All fields are required.");
            return;
        }

        try {
            int minAge = Integer.parseInt(minAgeField.getText());
            int maxAge = Integer.parseInt(maxAgeField.getText());
            java.math.BigDecimal minVal = new java.math.BigDecimal(minValField.getText());
            java.math.BigDecimal maxVal = new java.math.BigDecimal(maxValField.getText());

            if (minAge > maxAge) {
                showAlert("Validation Error", "Min Age cannot be greater than Max Age.");
                return;
            }
            if (minVal.compareTo(maxVal) > 0) {
                showAlert("Validation Error", "Min Value cannot be greater than Max Value.");
                return;
            }

            ReferenceRange range = new ReferenceRange();
            range.setTest(currentTest);
            range.setGender(genderCombo.getValue());
            range.setMinAge(minAge);
            range.setMaxAge(maxAge);
            range.setMinVal(minVal);
            range.setMaxVal(maxVal);

            referenceRangeRepository.save(range);
            refreshData();
            clearForm();

        } catch (NumberFormatException e) {
            showAlert("Error", "Invalid number format.");
        }
    }

    /**
     * Deletes the selected range after confirmation.
     */
    @FXML
    private void handleDelete() {
        ReferenceRange selected = rangesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Range");
        alert.setHeaderText("Delete this reference range?");
        alert.setContentText("This action cannot be undone.");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            referenceRangeRepository.delete(selected);
            refreshData();
        }
    }

    /**
     * Closes the reference range window.
     */
    @FXML
    private void handleClose() {
        com.qdc.lims.ui.util.ViewCloseUtil.closeCurrentTabOrWindow(rangesTable);
    }

    private void clearForm() {
        minAgeField.clear();
        maxAgeField.clear();
        minValField.clear();
        maxValField.clear();
        genderCombo.setValue("Both");
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
