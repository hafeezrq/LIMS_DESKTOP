package com.qdc.lims.ui.controller;

import com.qdc.lims.ui.SessionManager;
import com.qdc.lims.entity.LabOrder;
import com.qdc.lims.entity.LabResult;
import com.qdc.lims.entity.ReferenceRange;
import com.qdc.lims.entity.TestDefinition;
import com.qdc.lims.repository.LabOrderRepository;
import com.qdc.lims.repository.LabResultRepository;
import com.qdc.lims.repository.ReferenceRangeRepository;
import com.qdc.lims.service.LocaleFormatService;
import com.qdc.lims.service.ResultService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * JavaFX controller for entering test results.
 */
@Component("resultEntryController")
public class ResultEntryController {

    @FXML
    private Label orderInfoLabel;
    @FXML
    private Label mrnLabel;
    @FXML
    private Label nameLabel;
    @FXML
    private Label ageGenderLabel;
    @FXML
    private Label orderDateLabel;
    @FXML
    private TableView<LabResult> resultsTable;
    @FXML
    private TableColumn<LabResult, String> testNameColumn;
    @FXML
    private TableColumn<LabResult, String> resultValueColumn;
    @FXML
    private TableColumn<LabResult, String> unitColumn;
    @FXML
    private TableColumn<LabResult, String> referenceRangeColumn;
    @FXML
    private TableColumn<LabResult, String> statusColumn;
    @FXML
    private Label messageLabel;
    @FXML
    private Button saveButton;

    private final LabOrderRepository orderRepository;
    private final LabResultRepository resultRepository;
    private final ResultService resultService;
    private final LocaleFormatService localeFormatService;
    private final ReferenceRangeRepository referenceRangeRepository;
    private LabOrder currentOrder;

    // Flag to prevent selection listener loops during programmatic navigation
    private boolean adjustingSelection = false;

    public ResultEntryController(LabOrderRepository orderRepository,
            LabResultRepository resultRepository,
            ResultService resultService,
            LocaleFormatService localeFormatService,
            ReferenceRangeRepository referenceRangeRepository) {
        this.orderRepository = orderRepository;
        this.resultRepository = resultRepository;
        this.resultService = resultService;
        this.localeFormatService = localeFormatService;
        this.referenceRangeRepository = referenceRangeRepository;
    }

    public void setOrder(LabOrder order) {
        this.currentOrder = order;
        loadOrderData();
    }

    @FXML
    private void initialize() {
        setupResultsTable();
        messageLabel.setText("");
    }

    private void setupResultsTable() {
        resultsTable.setEditable(true);
        resultsTable.getSelectionModel().setCellSelectionEnabled(true);
        resultsTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        // Selection Listener: Handles mouse clicks or manual selection changes
        resultsTable.getSelectionModel().selectedIndexProperty().addListener((obs, oldVal, newVal) -> {
            if (adjustingSelection || newVal == null || newVal.intValue() < 0) {
                return;
            }

            int row = newVal.intValue();
            Platform.runLater(() -> {
                if (resultsTable.getEditingCell() == null) {
                    resultsTable.requestFocus();
                    // Optional: specifically select the value column
                    resultsTable.getSelectionModel().select(row, resultValueColumn);
                    resultsTable.edit(row, resultValueColumn);
                }
            });
        });

        testNameColumn.setCellValueFactory(
                cellData -> new SimpleStringProperty(cellData.getValue().getTestDefinition().getTestName()));

        // --- RESULT VALUE COLUMN ---
        resultValueColumn.setCellValueFactory(cellData -> {
            LabResult result = cellData.getValue();
            String value = result.getResultValue() != null ? result.getResultValue() : "";
            return new SimpleStringProperty(value);
        });

        resultValueColumn.setCellFactory(column -> new TableCell<LabResult, String>() {
            private TextField textField;

            @Override
            public void startEdit() {
                if (!isEmpty()) {
                    super.startEdit();
                    createTextField();
                    setText(null);
                    setGraphic(textField);
                    textField.selectAll();
                    Platform.runLater(() -> textField.requestFocus());
                }
            }

            @Override
            public void cancelEdit() {
                super.cancelEdit();
                setText(getItem());
                setGraphic(null);
            }

            @Override
            public void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setText(null);
                    setGraphic(null);
                } else {
                    if (isEditing()) {
                        if (textField != null) {
                            textField.setText(getString());
                        }
                        setText(null);
                        setGraphic(textField);
                    } else {
                        setText(getString());
                        setGraphic(null);
                    }
                }
            }

            private void createTextField() {
                textField = new TextField(getString());
                textField.setMinWidth(this.getWidth() - this.getGraphicTextGap() * 2);

                textField.setOnKeyPressed(event -> {
                    if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.TAB
                            || event.getCode() == KeyCode.DOWN || event.getCode() == KeyCode.UP) {

                        commitEdit(textField.getText());

                        int delta;
                        if (event.getCode() == KeyCode.UP) {
                            delta = -1;
                        } else if (event.getCode() == KeyCode.DOWN) {
                            delta = 1;
                        } else {
                            delta = event.isShiftDown() ? -1 : 1; // Tab/Enter logic
                        }

                        navigateToRow(delta);
                        event.consume();
                    } else if (event.getCode() == KeyCode.ESCAPE) {
                        cancelEdit();
                        event.consume();
                    }
                });

                // Focus lost: backup save
                textField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                    if (!isNowFocused && isEditing()) {
                        commitEdit(textField.getText());
                    }
                });
            }

            private String getString() {
                return getItem() == null ? "" : getItem();
            }

            private void navigateToRow(int delta) {
                int currentRow = getTableRow().getIndex();
                int targetRow = currentRow + delta;

                if (targetRow < 0 || targetRow >= getTableView().getItems().size()) {
                    return;
                }

                adjustingSelection = true;
                Platform.runLater(() -> {
                    try {
                        getTableView().requestFocus();
                        getTableView().getSelectionModel().clearAndSelect(targetRow, getTableColumn());
                        getTableView().getFocusModel().focus(targetRow, getTableColumn());
                        getTableView().scrollTo(targetRow);

                        // Small nested delay ensures the commit is fully finished before the next edit
                        // starts
                        Platform.runLater(() -> {
                            getTableView().edit(targetRow, getTableColumn());
                            adjustingSelection = false;
                        });
                    } catch (Exception e) {
                        adjustingSelection = false;
                    }
                });
            }
        });

        // Data update handler
        resultValueColumn.setOnEditCommit(event -> {
            LabResult result = event.getRowValue();
            result.setResultValue(event.getNewValue());
            autoCalculateStatus(result);

            // Toggle visibility to force status column to redraw instantly
            Platform.runLater(() -> {
                statusColumn.setVisible(false);
                statusColumn.setVisible(true);
            });
        });

        unitColumn.setCellValueFactory(
                cellData -> new SimpleStringProperty(cellData.getValue().getTestDefinition().getUnit() != null
                        ? cellData.getValue().getTestDefinition().getUnit()
                        : ""));

        referenceRangeColumn.setCellValueFactory(cellData -> {
            TestDefinition test = cellData.getValue().getTestDefinition();
            ReferenceRange range = findMatchingRange(test, currentOrder != null ? currentOrder.getPatient() : null);
            if (range == null)
                return new SimpleStringProperty("N/A");

            String min = range.getMinVal() != null ? localeFormatService.formatNumber(range.getMinVal()) : "";
            String max = range.getMaxVal() != null ? localeFormatService.formatNumber(range.getMaxVal()) : "";

            return (min.isEmpty() && max.isEmpty()) ? new SimpleStringProperty("N/A")
                    : new SimpleStringProperty(min + " - " + max);
        });

        statusColumn.setCellValueFactory(cellData -> {
            if (cellData.getValue().isAbnormal()) {
                return new SimpleStringProperty(cellData.getValue().getRemarks());
            }
            return new SimpleStringProperty("Normal");
        });

        statusColumn.setCellFactory(column -> new TableCell<LabResult, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                setStyle("");
                if (!empty && item != null) {
                    setText(item);
                    if ("HIGH".equals(item) || "LOW".equals(item)) {
                        setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold;");
                    } else if ("Normal".equals(item)) {
                        setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold;");
                    }
                }
            }
        });
    }

    private void loadOrderData() {
        if (currentOrder == null)
            return;

        currentOrder = orderRepository.findById(currentOrder.getId())
                .orElseThrow(() -> new RuntimeException("Order not found"));

        boolean isEditMode = "COMPLETED".equals(currentOrder.getStatus());

        orderInfoLabel.setText("Order #" + currentOrder.getId()
                + (isEditMode ? " - EDITING COMPLETED" : " - Status: " + currentOrder.getStatus()));
        if (saveButton != null)
            saveButton.setText(isEditMode ? "Save Corrections" : "Save Results");

        mrnLabel.setText(currentOrder.getPatient().getMrn());
        nameLabel.setText(currentOrder.getPatient().getFullName());
        ageGenderLabel.setText(currentOrder.getPatient().getAge() + " / " + currentOrder.getPatient().getGender());
        orderDateLabel.setText(localeFormatService.formatDateTime(currentOrder.getOrderDate()));

        resultsTable.setItems(FXCollections.observableArrayList(currentOrder.getResults()));

        // Initial Focus
        Platform.runLater(() -> {
            if (!resultsTable.getItems().isEmpty()) {
                resultsTable.requestFocus();
                resultsTable.getSelectionModel().select(0, resultValueColumn);
                resultsTable.edit(0, resultValueColumn);
            }
        });
    }

    private void autoCalculateStatus(LabResult result) {
        String value = result.getResultValue();
        if (value == null || value.trim().isEmpty())
            return;

        try {
            java.math.BigDecimal numValue = new java.math.BigDecimal(value.trim());
            ReferenceRange range = findMatchingRange(result.getTestDefinition(),
                    currentOrder != null ? currentOrder.getPatient() : null);

            if (range != null && range.getMinVal() != null && range.getMaxVal() != null) {
                if (numValue.compareTo(range.getMinVal()) < 0) {
                    result.setAbnormal(true);
                    result.setRemarks("LOW");
                } else if (numValue.compareTo(range.getMaxVal()) > 0) {
                    result.setAbnormal(true);
                    result.setRemarks("HIGH");
                } else {
                    result.setAbnormal(false);
                    result.setRemarks("Normal");
                }
            }
        } catch (NumberFormatException e) {
            result.setAbnormal(false);
            result.setRemarks("");
        }
    }

    private ReferenceRange findMatchingRange(TestDefinition test, com.qdc.lims.entity.Patient patient) {
        if (test == null || test.getId() == null)
            return null;
        var ranges = referenceRangeRepository.findByTestId(test.getId());
        if (ranges == null || ranges.isEmpty())
            return null;

        Integer age = patient != null ? patient.getAge() : null;
        String gender = patient != null ? patient.getGender() : null;

        return ranges.stream()
                .filter(range -> matchesRange(range, age, gender))
                .sorted((a, b) -> Integer.compare(genderScore(b, gender), genderScore(a, gender)))
                .findFirst()
                .orElse(null);
    }

    private boolean matchesRange(ReferenceRange range, Integer age, String gender) {
        if (range == null)
            return false;
        String rangeGender = range.getGender();
        if (gender != null && rangeGender != null && !"Both".equalsIgnoreCase(rangeGender)
                && !rangeGender.equalsIgnoreCase(gender))
            return false;
        if (age != null) {
            if (range.getMinAge() != null && age < range.getMinAge())
                return false;
            if (range.getMaxAge() != null && age > range.getMaxAge())
                return false;
        }
        return true;
    }

    private int genderScore(ReferenceRange range, String gender) {
        if (range == null)
            return 0;
        String rg = range.getGender();
        if (gender != null && rg != null && rg.equalsIgnoreCase(gender))
            return 2;
        if (rg != null && "Both".equalsIgnoreCase(rg))
            return 1;
        return 0;
    }

    @FXML
    private void handleSaveResults() {
        try {
            resultsTable.refresh();
            String currentUser = SessionManager.getCurrentUser() != null ? SessionManager.getCurrentUser().getUsername()
                    : "UNKNOWN";

            int enteredCount = (int) resultsTable.getItems().stream()
                    .filter(r -> r.getResultValue() != null && !r.getResultValue().trim().isEmpty()).count();

            if (enteredCount == 0) {
                showError("No results to save.");
                return;
            }

            if ("COMPLETED".equals(currentOrder.getStatus())) {
                // ... Existing Edit Logic ...
                String editReason = "";
                if (currentOrder.isReportDelivered()) {
                    TextInputDialog dialog = new TextInputDialog();
                    dialog.setTitle("Edit Delivered Report");
                    dialog.setHeaderText("Reason required:");
                    editReason = dialog.showAndWait().orElse("").trim();
                    if (editReason.isEmpty())
                        return;
                }
                currentOrder.setResults(new ArrayList<>(resultsTable.getItems()));
                resultService.saveEditedResults(currentOrder, editReason);
                showSuccess("Results corrected!");
            } else {
                // Pending Logic
                for (LabResult result : resultsTable.getItems()) {
                    if (result.getResultValue() != null && !result.getResultValue().trim().isEmpty()) {
                        result.setPerformedBy(currentUser);
                        result.setPerformedAt(LocalDateTime.now());
                        resultRepository.save(result);
                    }
                }
                currentOrder.setResults(new ArrayList<>(resultsTable.getItems()));
                resultService.saveResultsFromForm(currentOrder);
                showSuccess("Results saved!");
            }

            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    Platform.runLater(() -> handleClose());
                }
            }, 1500);
        } catch (Exception e) {
            showError("Save failed: " + e.getMessage());
        }
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) resultsTable.getScene().getWindow();
        stage.close();
    }

    private void showError(String message) {
        messageLabel.setText("❌ " + message);
        messageLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
    }

    private void showSuccess(String message) {
        messageLabel.setText("✓ " + message);
        messageLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
    }
}