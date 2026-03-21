package com.qdc.lims.ui.controller;

import com.qdc.lims.entity.TestDefinition;
import com.qdc.lims.entity.TestResultOption;
import com.qdc.lims.service.TestResultOptionService;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Admin controller for maintaining per-test fixed result dropdown options.
 */
@Component("resultOptionsController")
public class ResultOptionsController {

    private final TestResultOptionService testResultOptionService;

    @FXML
    private Label testNameLabel;

    @FXML
    private ComboBox<TestDefinition> testCombo;

    @FXML
    private TableView<TestResultOption> optionsTable;

    @FXML
    private TableColumn<TestResultOption, String> codeColumn;

    @FXML
    private TableColumn<TestResultOption, String> labelColumn;

    @FXML
    private TableColumn<TestResultOption, Integer> orderColumn;

    @FXML
    private TableColumn<TestResultOption, Boolean> activeColumn;

    @FXML
    private TextField codeField;

    @FXML
    private TextField labelField;

    @FXML
    private TextField displayOrderField;

    @FXML
    private CheckBox activeCheck;

    @FXML
    private Button saveButton;

    @FXML
    private Button deleteButton;

    @FXML
    private Button importForTestButton;

    @FXML
    private Label statusLabel;

    private final ObservableList<TestResultOption> optionRows = FXCollections.observableArrayList();

    private TestDefinition currentTest;
    private TestResultOption editingOption;

    public ResultOptionsController(TestResultOptionService testResultOptionService) {
        this.testResultOptionService = testResultOptionService;
    }

    @FXML
    public void initialize() {
        setupTable();
        setupForm();
        setupTestSelector();
    }

    private void setupTable() {
        codeColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getOptionCode()));
        labelColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getOptionLabel()));
        orderColumn.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().getDisplayOrder()));
        activeColumn.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().getActive()));
        activeColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(item ? "ACTIVE" : "INACTIVE");
                setStyle(item
                        ? "-fx-text-fill: white; -fx-background-color: #27ae60; -fx-alignment: center;"
                        : "-fx-text-fill: white; -fx-background-color: #e67e22; -fx-alignment: center;");
            }
        });

        optionsTable.setItems(optionRows);
        optionsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            deleteButton.setDisable(newVal == null);
            if (newVal != null) {
                populateForm(newVal);
            }
        });
    }

    private void setupForm() {
        activeCheck.setSelected(true);
        displayOrderField.setText("0");
        displayOrderField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*")) {
                displayOrderField.setText(newValue.replaceAll("[^\\d]", ""));
            }
        });
        deleteButton.setDisable(true);
        if (importForTestButton != null) {
            importForTestButton.setDisable(true);
        }
    }

    private void setupTestSelector() {
        testCombo.setItems(FXCollections.observableArrayList(testResultOptionService.findAllTests()));
        testCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(TestDefinition test) {
                if (test == null) {
                    return "";
                }
                if (test.getShortCode() == null || test.getShortCode().isBlank()) {
                    return test.getTestName();
                }
                return test.getTestName() + " (" + test.getShortCode() + ")";
            }

            @Override
            public TestDefinition fromString(String string) {
                return testCombo.getItems().stream()
                        .filter(t -> toString(t).equalsIgnoreCase(string))
                        .findFirst()
                        .orElse(null);
            }
        });

        testCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            currentTest = newVal;
            if (newVal == null) {
                testNameLabel.setText("For Test: ...");
                optionRows.clear();
                clearForm();
                statusLabel.setText("Select a test.");
                if (importForTestButton != null) {
                    importForTestButton.setDisable(true);
                }
                return;
            }
            testNameLabel.setText("For Test: " + newVal.getTestName());
            loadOptions();
            clearForm();
            if (importForTestButton != null) {
                importForTestButton.setDisable(false);
            }
        });
    }

    private void loadOptions() {
        optionRows.clear();
        if (currentTest == null || currentTest.getId() == null) {
            return;
        }
        optionRows.addAll(testResultOptionService.findOptionsForTest(currentTest.getId()));
        statusLabel.setText("Loaded " + optionRows.size() + " option(s).");
    }

    @FXML
    private void handleSave() {
        if (currentTest == null || currentTest.getId() == null) {
            showWarning("Validation", "Select a test first.");
            return;
        }

        Integer displayOrder = 0;
        if (!displayOrderField.getText().isBlank()) {
            displayOrder = Integer.parseInt(displayOrderField.getText());
        }

        try {
            TestResultOption saved = testResultOptionService.saveOption(
                    currentTest.getId(),
                    editingOption != null ? editingOption.getId() : null,
                    codeField.getText(),
                    labelField.getText(),
                    displayOrder,
                    activeCheck.isSelected());

            loadOptions();
            clearForm();
            statusLabel.setText("Saved option: " + saved.getOptionLabel());
        } catch (Exception e) {
            showWarning("Save Failed", e.getMessage());
        }
    }

    @FXML
    private void handleNew() {
        clearForm();
    }

    @FXML
    private void handleDelete() {
        TestResultOption selected = optionsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Option");
        confirm.setHeaderText("Delete result option?");
        confirm.setContentText("Code: " + selected.getOptionCode() + "\nLabel: " + selected.getOptionLabel());
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }

        testResultOptionService.deleteOption(selected.getId());
        loadOptions();
        clearForm();
        statusLabel.setText("Deleted option.");
    }

    @FXML
    private void handleImportDefaultsForSelectedTest() {
        if (currentTest == null || currentTest.getId() == null) {
            showWarning("Validation", "Select a test first.");
            return;
        }

        TestResultOptionService.ImportSummary summary = testResultOptionService
                .importDefaultOptionsForTest(currentTest.getId());
        if (summary.testsMatched() == 0) {
            statusLabel.setText("No predefined template exists for this test.");
            return;
        }

        loadOptions();
        clearForm();
        statusLabel.setText("Imported defaults for selected test. Added: "
                + summary.optionsAdded() + ", Existing skipped: " + summary.optionsSkipped() + ".");
    }

    @FXML
    private void handleImportCommonDefaults() {
        TestResultOptionService.ImportSummary summary = testResultOptionService.importDefaultOptionsForCommonTests();
        if (summary.testsMatched() == 0) {
            statusLabel.setText("No matching tests found for predefined templates.");
            return;
        }

        if (currentTest != null && currentTest.getId() != null) {
            loadOptions();
            clearForm();
        }

        statusLabel.setText("Common defaults imported. Tests matched: " + summary.testsMatched()
                + ", Added: " + summary.optionsAdded()
                + ", Existing skipped: " + summary.optionsSkipped() + ".");
    }

    @FXML
    private void handleClose() {
        com.qdc.lims.ui.util.ViewCloseUtil.closeCurrentTabOrWindow(optionsTable);
    }

    private void clearForm() {
        editingOption = null;
        codeField.clear();
        labelField.clear();
        displayOrderField.setText("0");
        activeCheck.setSelected(true);
        saveButton.setText("Add Option");
        optionsTable.getSelectionModel().clearSelection();
        deleteButton.setDisable(true);
    }

    private void populateForm(TestResultOption option) {
        editingOption = option;
        codeField.setText(option.getOptionCode());
        labelField.setText(option.getOptionLabel());
        displayOrderField.setText(option.getDisplayOrder() != null ? option.getDisplayOrder().toString() : "0");
        activeCheck.setSelected(option.getActive() != null ? option.getActive() : true);
        saveButton.setText("Update Option");
    }

    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
