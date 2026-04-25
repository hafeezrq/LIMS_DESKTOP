package com.qdc.lims.ui.controller;

import com.qdc.lims.entity.LabOrder;
import com.qdc.lims.entity.LabResult;
import com.qdc.lims.entity.ReferenceRange;
import com.qdc.lims.entity.TestDefinition;
import com.qdc.lims.repository.LabOrderRepository;
import com.qdc.lims.repository.ReferenceRangeRepository;
import com.qdc.lims.service.LocaleFormatService;
import com.qdc.lims.service.ResultService;
import com.qdc.lims.service.TestResultOptionService;
import com.qdc.lims.util.LabResultDisplayOrder;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private TabPane departmentTabPane;
    @FXML
    private Label messageLabel;
    @FXML
    private Button saveButton;

    private final LabOrderRepository orderRepository;
    private final ResultService resultService;
    private final TestResultOptionService testResultOptionService;
    private final LocaleFormatService localeFormatService;
    private final ReferenceRangeRepository referenceRangeRepository;
    private LabOrder currentOrder;
    private Runnable closeAction;

    private final List<TableView<LabResult>> resultTables = new ArrayList<>();
    private final Map<Tab, TableView<LabResult>> categoryTabTableMap = new LinkedHashMap<>();
    private final Map<Long, List<String>> resultOptionsByTestId = new LinkedHashMap<>();
    private TableView<LabResult> activeResultsTable;

    // Flag to prevent selection listener loops during programmatic navigation.
    private boolean adjustingSelection = false;

    public ResultEntryController(LabOrderRepository orderRepository,
            ResultService resultService,
            TestResultOptionService testResultOptionService,
            LocaleFormatService localeFormatService,
            ReferenceRangeRepository referenceRangeRepository) {
        this.orderRepository = orderRepository;
        this.resultService = resultService;
        this.testResultOptionService = testResultOptionService;
        this.localeFormatService = localeFormatService;
        this.referenceRangeRepository = referenceRangeRepository;
    }

    public void setOrder(LabOrder order) {
        this.currentOrder = order;
        loadOrderData();
    }

    /**
     * Optional close callback used when embedded in a tab instead of a stage.
     */
    public void setCloseAction(Runnable closeAction) {
        this.closeAction = closeAction;
    }

    @FXML
    private void initialize() {
        departmentTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            refreshActiveTableFromSelection();
        });
        messageLabel.setText("");
    }

    private TableView<LabResult> createResultsTable() {
        TableView<LabResult> table = new TableView<>();
        table.setEditable(true);
        table.getSelectionModel().setCellSelectionEnabled(true);
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<LabResult, String> testNameColumn = new TableColumn<>("Test Name");
        testNameColumn.setEditable(false);
        testNameColumn.setPrefWidth(260);
        testNameColumn.setCellValueFactory(cellData -> {
            TestDefinition testDefinition = cellData.getValue().getTestDefinition();
            String testName = testDefinition != null && testDefinition.getTestName() != null
                    ? testDefinition.getTestName()
                    : "";
            return new SimpleStringProperty(testName);
        });

        TableColumn<LabResult, String> resultValueColumn = new TableColumn<>("Result Value");
        resultValueColumn.setPrefWidth(160);
        resultValueColumn.setCellValueFactory(cellData -> {
            LabResult result = cellData.getValue();
            String value = result.getResultValue() != null ? result.getResultValue() : "";
            return new SimpleStringProperty(value);
        });

        TableColumn<LabResult, String> unitColumn = new TableColumn<>("Unit");
        unitColumn.setEditable(false);
        unitColumn.setPrefWidth(90);
        unitColumn.setCellValueFactory(cellData -> {
            TestDefinition testDefinition = cellData.getValue().getTestDefinition();
            String unit = testDefinition != null && testDefinition.getUnit() != null ? testDefinition.getUnit() : "";
            return new SimpleStringProperty(unit);
        });

        TableColumn<LabResult, String> referenceRangeColumn = new TableColumn<>("Reference Range");
        referenceRangeColumn.setEditable(false);
        referenceRangeColumn.setPrefWidth(170);
        referenceRangeColumn.setCellValueFactory(cellData -> {
            TestDefinition test = cellData.getValue().getTestDefinition();
            ReferenceRange range = findMatchingRange(test, currentOrder != null ? currentOrder.getPatient() : null);
            if (range != null && range.getReferenceText() != null && !range.getReferenceText().trim().isEmpty()) {
                return new SimpleStringProperty(range.getReferenceText().trim());
            }
            if (range == null) {
                return new SimpleStringProperty("N/A");
            }

            String min = range.getMinVal() != null ? localeFormatService.formatNumber(range.getMinVal()) : "";
            String max = range.getMaxVal() != null ? localeFormatService.formatNumber(range.getMaxVal()) : "";

            return (min.isEmpty() && max.isEmpty())
                    ? new SimpleStringProperty("N/A")
                    : new SimpleStringProperty(min + " - " + max);
        });

        TableColumn<LabResult, String> statusColumn = new TableColumn<>("Status");
        statusColumn.setEditable(false);
        statusColumn.setPrefWidth(130);
        statusColumn.setCellValueFactory(cellData -> {
            if (cellData.getValue().isAbnormal()) {
                return new SimpleStringProperty(cellData.getValue().getRemarks());
            }
            return new SimpleStringProperty("Normal");
        });
        statusColumn.setCellFactory(column -> new TableCell<>() {
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

        resultValueColumn.setCellFactory(column -> new TableCell<>() {
            private TextField textField;
            private ComboBox<String> comboBox;

            @Override
            public void startEdit() {
                if (!isEmpty()) {
                    super.startEdit();
                    activeResultsTable = getTableView();
                    LabResult rowItem = currentRowItem();
                    if (hasConfiguredOptions(rowItem)) {
                        createComboBox(rowItem);
                        setText(null);
                        setGraphic(comboBox);
                        Platform.runLater(() -> {
                            comboBox.requestFocus();
                            comboBox.show();
                        });
                    } else {
                        createTextField();
                        setText(null);
                        setGraphic(textField);
                        textField.selectAll();
                        Platform.runLater(textField::requestFocus);
                    }
                }
            }

            @Override
            public void cancelEdit() {
                super.cancelEdit();
                setText(currentValue());
                setGraphic(null);
            }

            @Override
            public void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setText(null);
                    setGraphic(null);
                } else if (isEditing()) {
                    LabResult rowItem = currentRowItem();
                    if (hasConfiguredOptions(rowItem)) {
                        if (comboBox != null) {
                            comboBox.setValue(currentValue());
                        }
                        setText(null);
                        setGraphic(comboBox);
                    } else {
                        if (textField != null) {
                            textField.setText(currentValue());
                        }
                        setText(null);
                        setGraphic(textField);
                    }
                } else {
                    setText(currentValue());
                    setGraphic(null);
                }
            }

            private LabResult currentRowItem() {
                return getTableRow() != null ? getTableRow().getItem() : null;
            }

            private void createTextField() {
                comboBox = null;
                textField = new TextField(currentValue());
                textField.setMinWidth(this.getWidth() - this.getGraphicTextGap() * 2);

                // Keep row model updated even before explicit commit.
                textField.textProperty().addListener((obs, oldVal, newVal) -> {
                    LabResult rowItem = getTableRow() != null ? getTableRow().getItem() : null;
                    if (rowItem != null) {
                        rowItem.setResultValue(newVal);
                    }
                });

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
                            delta = event.isShiftDown() ? -1 : 1;
                        }

                        navigateToRow(delta);
                        event.consume();
                    } else if (event.getCode() == KeyCode.ESCAPE) {
                        cancelEdit();
                        event.consume();
                    }
                });

                // Focus lost backup save.
                textField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                    if (!isNowFocused && isEditing()) {
                        commitEdit(textField.getText());
                    }
                });
            }

            private void createComboBox(LabResult rowItem) {
                textField = null;
                List<String> options = new ArrayList<>(configuredOptionsFor(rowItem));
                String current = currentValue();
                if (current != null && !current.isBlank() && !options.contains(current)) {
                    options.add(0, current);
                }

                comboBox = new ComboBox<>(FXCollections.observableArrayList(options));
                comboBox.setEditable(false);
                comboBox.setMaxWidth(Double.MAX_VALUE);
                comboBox.setValue(current);

                comboBox.setOnAction(event -> {
                    String selected = comboBox.getValue();
                    if (selected != null) {
                        commitEdit(selected);
                    }
                });

                comboBox.setOnKeyPressed(event -> {
                    if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.TAB
                            || event.getCode() == KeyCode.DOWN || event.getCode() == KeyCode.UP) {
                        String selected = comboBox.getValue() != null ? comboBox.getValue() : currentValue();
                        commitEdit(selected != null ? selected : "");

                        int delta;
                        if (event.getCode() == KeyCode.UP) {
                            delta = -1;
                        } else if (event.getCode() == KeyCode.DOWN) {
                            delta = 1;
                        } else {
                            delta = event.isShiftDown() ? -1 : 1;
                        }
                        navigateToRow(delta);
                        event.consume();
                    } else if (event.getCode() == KeyCode.ESCAPE) {
                        cancelEdit();
                        event.consume();
                    }
                });

                comboBox.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                    if (!isNowFocused && isEditing()) {
                        String selected = comboBox.getValue() != null ? comboBox.getValue() : currentValue();
                        commitEdit(selected != null ? selected : "");
                    }
                });
            }

            private String currentValue() {
                LabResult rowItem = getTableRow() != null ? getTableRow().getItem() : null;
                if (rowItem == null || rowItem.getResultValue() == null) {
                    return "";
                }
                return rowItem.getResultValue();
            }

            private void navigateToRow(int delta) {
                int currentRow = getTableRow().getIndex();
                int targetRow = currentRow + delta;
                TableView<LabResult> currentTable = getTableView();

                if (targetRow < 0 || targetRow >= currentTable.getItems().size()) {
                    return;
                }

                adjustingSelection = true;
                Platform.runLater(() -> {
                    try {
                        activeResultsTable = currentTable;
                        currentTable.requestFocus();
                        currentTable.getSelectionModel().clearAndSelect(targetRow, resultValueColumn);
                        currentTable.getFocusModel().focus(targetRow, resultValueColumn);
                        currentTable.scrollTo(targetRow);

                        Platform.runLater(() -> {
                            currentTable.edit(targetRow, resultValueColumn);
                            adjustingSelection = false;
                        });
                    } catch (Exception e) {
                        adjustingSelection = false;
                    }
                });
            }
        });

        resultValueColumn.setOnEditCommit(event -> {
            LabResult result = event.getRowValue();
            result.setResultValue(event.getNewValue());
            autoCalculateStatus(result);
            Platform.runLater(table::refresh);
        });

        table.getSelectionModel().selectedIndexProperty().addListener((obs, oldVal, newVal) -> {
            if (adjustingSelection || newVal == null || newVal.intValue() < 0) {
                return;
            }

            int row = newVal.intValue();
            activeResultsTable = table;
            Platform.runLater(() -> {
                if (table.getEditingCell() == null) {
                    table.requestFocus();
                    table.getSelectionModel().select(row, resultValueColumn);
                    table.edit(row, resultValueColumn);
                }
            });
        });

        table.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (isNowFocused) {
                activeResultsTable = table;
            }
        });
        table.setOnMousePressed(event -> activeResultsTable = table);

        table.getColumns().addAll(List.of(
                testNameColumn,
                resultValueColumn,
                unitColumn,
                referenceRangeColumn,
                statusColumn));
        return table;
    }

    private void loadOrderData() {
        if (currentOrder == null) {
            return;
        }

        currentOrder = orderRepository.findById(currentOrder.getId())
                .orElseThrow(() -> new RuntimeException("Order not found"));

        boolean isEditMode = "COMPLETED".equals(currentOrder.getStatus());

        orderInfoLabel.setText("Order #" + currentOrder.getId()
                + (isEditMode ? " - EDITING COMPLETED" : " - Status: " + currentOrder.getStatus()));
        if (saveButton != null) {
            saveButton.setText(isEditMode ? "Save Corrections" : "Save Results");
        }

        mrnLabel.setText(currentOrder.getPatient().getMrn());
        nameLabel.setText(currentOrder.getPatient().getFullName());
        ageGenderLabel.setText(localeFormatService.formatAge(currentOrder.getPatient().getAge(),
                currentOrder.getPatient().getAgeUnit()) + " / " + currentOrder.getPatient().getGender());
        orderDateLabel.setText(localeFormatService.formatDateTime(currentOrder.getOrderDate()));

        List<LabResult> sortedResults = currentOrder.getResults() == null
                ? List.of()
                : currentOrder.getResults().stream()
                        .sorted(LabResultDisplayOrder.comparator())
                        .toList();

        loadResultOptions(sortedResults);
        buildDepartmentTabs(sortedResults);
        Platform.runLater(this::focusFirstResultCell);
    }

    private void loadResultOptions(List<LabResult> results) {
        resultOptionsByTestId.clear();
        if (results == null || results.isEmpty()) {
            return;
        }

        Set<Long> testIds = new HashSet<>();
        for (LabResult result : results) {
            if (result == null || result.getTestDefinition() == null || result.getTestDefinition().getId() == null) {
                continue;
            }
            testIds.add(result.getTestDefinition().getId());
        }
        if (testIds.isEmpty()) {
            return;
        }

        resultOptionsByTestId.putAll(testResultOptionService.findActiveOptionLabelsByTestIds(testIds));
    }

    private List<String> configuredOptionsFor(LabResult result) {
        if (result == null || result.getTestDefinition() == null || result.getTestDefinition().getId() == null) {
            return List.of();
        }
        return resultOptionsByTestId.getOrDefault(result.getTestDefinition().getId(), List.of());
    }

    private boolean hasConfiguredOptions(LabResult result) {
        return !configuredOptionsFor(result).isEmpty();
    }

    private void buildDepartmentTabs(List<LabResult> sortedResults) {
        resultTables.clear();
        categoryTabTableMap.clear();
        activeResultsTable = null;
        departmentTabPane.getTabs().clear();

        Map<String, Map<String, List<LabResult>>> grouped = new LinkedHashMap<>();
        for (LabResult result : sortedResults) {
            String departmentName = getDepartmentName(result);
            String categoryName = getCategoryName(result);
            grouped.computeIfAbsent(departmentName, key -> new LinkedHashMap<>())
                    .computeIfAbsent(categoryName, key -> new ArrayList<>())
                    .add(result);
        }

        for (Map.Entry<String, Map<String, List<LabResult>>> departmentEntry : grouped.entrySet()) {
            TabPane categoryTabPane = new TabPane();
            categoryTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

            for (Map.Entry<String, List<LabResult>> categoryEntry : departmentEntry.getValue().entrySet()) {
                TableView<LabResult> table = createResultsTable();
                table.setItems(FXCollections.observableArrayList(categoryEntry.getValue()));
                resultTables.add(table);

                Tab categoryTab = new Tab(categoryEntry.getKey(), table);
                categoryTab.setClosable(false);
                categoryTabTableMap.put(categoryTab, table);
                categoryTabPane.getTabs().add(categoryTab);
            }

            categoryTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
                refreshActiveTableFromSelection();
            });

            Tab departmentTab = new Tab(departmentEntry.getKey(), categoryTabPane);
            departmentTab.setClosable(false);
            departmentTabPane.getTabs().add(departmentTab);
        }
    }

    private void focusFirstResultCell() {
        if (resultTables.isEmpty()) {
            return;
        }

        if (departmentTabPane.getTabs().isEmpty()) {
            return;
        }

        departmentTabPane.getSelectionModel().select(0);
        Tab selectedDepartmentTab = departmentTabPane.getSelectionModel().getSelectedItem();
        if (selectedDepartmentTab != null && selectedDepartmentTab.getContent() instanceof TabPane categoryTabPane) {
            if (!categoryTabPane.getTabs().isEmpty()) {
                categoryTabPane.getSelectionModel().select(0);
            }
        }

        TableView<LabResult> firstTable = resultTables.get(0);
        activeResultsTable = firstTable;
        if (!firstTable.getItems().isEmpty()) {
            firstTable.requestFocus();
            TableColumn<LabResult, ?> valueColumn = firstTable.getColumns().size() > 1 ? firstTable.getColumns().get(1)
                    : null;
            if (valueColumn != null) {
                firstTable.getSelectionModel().select(0, valueColumn);
                firstTable.edit(0, valueColumn);
            }
        }
    }

    private void refreshActiveTableFromSelection() {
        Tab selectedDepartmentTab = departmentTabPane.getSelectionModel().getSelectedItem();
        if (selectedDepartmentTab == null || !(selectedDepartmentTab.getContent() instanceof TabPane categoryTabPane)) {
            return;
        }

        Tab selectedCategoryTab = categoryTabPane.getSelectionModel().getSelectedItem();
        if (selectedCategoryTab != null) {
            TableView<LabResult> table = categoryTabTableMap.get(selectedCategoryTab);
            if (table != null) {
                activeResultsTable = table;
            }
        }
    }

    private List<LabResult> collectAllResults() {
        List<LabResult> allResults = new ArrayList<>();
        for (TableView<LabResult> table : resultTables) {
            allResults.addAll(table.getItems());
        }
        return allResults;
    }

    private String getDepartmentName(LabResult result) {
        if (result == null || result.getTestDefinition() == null
                || result.getTestDefinition().getDepartment() == null
                || result.getTestDefinition().getDepartment().getName() == null
                || result.getTestDefinition().getDepartment().getName().isBlank()) {
            return "Other";
        }
        return result.getTestDefinition().getDepartment().getName();
    }

    private String getCategoryName(LabResult result) {
        if (result == null || result.getTestDefinition() == null
                || result.getTestDefinition().getCategory() == null
                || result.getTestDefinition().getCategory().getName() == null
                || result.getTestDefinition().getCategory().getName().isBlank()) {
            return "Other Tests";
        }
        return result.getTestDefinition().getCategory().getName();
    }

    private void autoCalculateStatus(LabResult result) {
        String value = result.getResultValue();
        if (value == null || value.trim().isEmpty()) {
            return;
        }

        try {
            java.math.BigDecimal numValue = new java.math.BigDecimal(value.trim());
            ReferenceRange range = findMatchingRange(result.getTestDefinition(),
                    currentOrder != null ? currentOrder.getPatient() : null);

            if (range != null
                    && (range.getReferenceText() == null || range.getReferenceText().trim().isEmpty())
                    && range.getMinVal() != null
                    && range.getMaxVal() != null) {
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
            } else {
                result.setAbnormal(false);
                result.setRemarks("");
            }
        } catch (NumberFormatException e) {
            result.setAbnormal(false);
            result.setRemarks("");
        }
    }

    private ReferenceRange findMatchingRange(TestDefinition test, com.qdc.lims.entity.Patient patient) {
        if (test == null || test.getId() == null) {
            return null;
        }
        var ranges = referenceRangeRepository.findByTestId(test.getId());
        if (ranges == null || ranges.isEmpty()) {
            return null;
        }

        Integer age = toAgeInYears(patient);
        String gender = patient != null ? patient.getGender() : null;

        return ranges.stream()
                .filter(range -> matchesRange(range, age, gender))
                .sorted((a, b) -> Integer.compare(genderScore(b, gender), genderScore(a, gender)))
                .findFirst()
                .orElse(null);
    }

    private boolean matchesRange(ReferenceRange range, Integer age, String gender) {
        if (range == null) {
            return false;
        }
        String rangeGender = range.getGender();
        if (gender != null && rangeGender != null && !"Both".equalsIgnoreCase(rangeGender)
                && !rangeGender.equalsIgnoreCase(gender)) {
            return false;
        }
        if (age != null) {
            if (range.getMinAge() != null && age < range.getMinAge()) {
                return false;
            }
            if (range.getMaxAge() != null && age > range.getMaxAge()) {
                return false;
            }
        }
        return true;
    }

    private int genderScore(ReferenceRange range, String gender) {
        if (range == null) {
            return 0;
        }
        String rg = range.getGender();
        if (gender != null && rg != null && rg.equalsIgnoreCase(gender)) {
            return 2;
        }
        if (rg != null && "Both".equalsIgnoreCase(rg)) {
            return 1;
        }
        return 0;
    }

    private Integer toAgeInYears(com.qdc.lims.entity.Patient patient) {
        if (patient == null || patient.getAge() == null) {
            return null;
        }
        int age = patient.getAge();
        String ageUnit = patient.getAgeUnit();
        if (ageUnit != null && ageUnit.equalsIgnoreCase("Months")) {
            return Math.max(0, age / 12);
        }
        if (ageUnit != null && ageUnit.equalsIgnoreCase("Days")) {
            return 0;
        }
        return age;
    }

    @FXML
    private void handleSaveResults() {
        try {
            commitActiveEdit();
            resultTables.forEach(TableView::refresh);

            List<LabResult> allResults = collectAllResults();
            int enteredCount = (int) allResults.stream()
                    .filter(r -> r.getResultValue() != null && !r.getResultValue().trim().isEmpty())
                    .count();

            if (enteredCount == 0) {
                showError("No results to save.");
                return;
            }

            if ("COMPLETED".equals(currentOrder.getStatus())) {
                TextInputDialog dialog = new TextInputDialog();
                dialog.setTitle("Audit Reason Required");
                if (currentOrder.isReportDelivered()) {
                    dialog.setHeaderText("Report already delivered. Enter correction reason:");
                } else {
                    dialog.setHeaderText("Enter reason for editing completed results:");
                }
                dialog.setContentText("Reason:");
                String editReason = dialog.showAndWait().orElse("").trim();
                if (editReason.isEmpty()) {
                    showError("Edit reason is required.");
                    return;
                }
                currentOrder.setResults(new ArrayList<>(allResults));
                resultService.saveEditedResults(currentOrder, editReason);
                showSuccess("Results corrected!");
            } else {
                currentOrder.setResults(new ArrayList<>(allResults));
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
        if (closeAction != null) {
            closeAction.run();
            return;
        }
        Node anchor = saveButton != null ? saveButton : departmentTabPane;
        if (anchor == null || anchor.getScene() == null) {
            return;
        }
        Stage stage = (Stage) anchor.getScene().getWindow();
        if (stage != null) {
            stage.close();
        }
    }

    private void showError(String message) {
        messageLabel.setText("❌ " + message);
        messageLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
    }

    private void showSuccess(String message) {
        messageLabel.setText("✓ " + message);
        messageLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
    }

    private void commitActiveEdit() {
        TableView<LabResult> table = resolveTableForCommit();
        if (table == null) {
            return;
        }

        TablePosition<LabResult, ?> editingCell = table.getEditingCell();
        if (editingCell == null) {
            return;
        }

        int row = editingCell.getRow();
        if (row < 0 || row >= table.getItems().size()) {
            return;
        }

        LabResult editingResult = table.getItems().get(row);
        String latestValue = editingResult.getResultValue();

        if (table.getScene() != null) {
            Node focusOwner = table.getScene().getFocusOwner();
            if (focusOwner instanceof TextField textField) {
                latestValue = textField.getText();
            } else if (focusOwner instanceof ComboBox<?> comboBox) {
                Object selectedValue = comboBox.getValue();
                latestValue = selectedValue != null ? Objects.toString(selectedValue) : latestValue;
            }
        }

        editingResult.setResultValue(latestValue);
        autoCalculateStatus(editingResult);
        table.edit(-1, null);
        table.refresh();
    }

    private TableView<LabResult> resolveTableForCommit() {
        if (activeResultsTable != null) {
            return activeResultsTable;
        }
        for (TableView<LabResult> table : resultTables) {
            if (table.getEditingCell() != null) {
                return table;
            }
        }
        if (!resultTables.isEmpty()) {
            return resultTables.get(0);
        }
        return null;
    }
}
