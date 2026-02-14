package com.qdc.lims.ui.controller;

import com.qdc.lims.entity.CommissionLedger;
import com.qdc.lims.entity.Doctor;
import com.qdc.lims.repository.CommissionLedgerRepository;
import com.qdc.lims.service.LocaleFormatService;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for Commission Management.
 * Manages doctor commissions, payments, and commission history.
 */
@Component
public class CommissionManagementController {

    @Autowired
    private CommissionLedgerRepository commissionRepository;
    @Autowired
    private LocaleFormatService localeFormatService;

    // Statistics Labels
    @FXML
    private Label totalUnpaidLabel;
    @FXML
    private Label totalPaidLabel;
    @FXML
    private Label pendingCountLabel;
    @FXML
    private Label thisMonthLabel;

    // Toolbar Controls
    @FXML
    private Button markPaidButton;
    @FXML
    private Button markUnpaidButton;
    @FXML
    private Button viewDetailsButton;
    @FXML
    private Button bulkPaymentButton;
    @FXML
    private Button closeButton;

    @FXML
    private ComboBox<String> doctorFilter;
    @FXML
    private ComboBox<String> statusFilter;
    @FXML
    private DatePicker startDatePicker;
    @FXML
    private DatePicker endDatePicker;

    // Table and Columns
    @FXML
    private TableView<CommissionLedger> commissionTable;
    @FXML
    private TableColumn<CommissionLedger, Boolean> selectColumn;
    @FXML
    private TableColumn<CommissionLedger, Long> idColumn;
    @FXML
    private TableColumn<CommissionLedger, Long> orderIdColumn;
    @FXML
    private TableColumn<CommissionLedger, String> doctorColumn;
    @FXML
    private TableColumn<CommissionLedger, String> dateColumn;
    @FXML
    private TableColumn<CommissionLedger, BigDecimal> billAmountColumn;
    @FXML
    private TableColumn<CommissionLedger, BigDecimal> commissionRateColumn;
    @FXML
    private TableColumn<CommissionLedger, BigDecimal> commissionAmountColumn;
    @FXML
    private TableColumn<CommissionLedger, String> statusColumn;
    @FXML
    private TableColumn<CommissionLedger, Void> actionsColumn;

    // Footer Labels
    @FXML
    private Label statusLabel;
    @FXML
    private Label selectedCountLabel;
    @FXML
    private Label recordCountLabel;

    private ObservableList<CommissionLedger> allCommissions = FXCollections.observableArrayList();
    private ObservableList<CommissionLedger> filteredCommissions = FXCollections.observableArrayList();
    private Map<Long, Boolean> selectionMap = new HashMap<>();

    private DecimalFormat percentFormat = new DecimalFormat("#0.0#");

    /**
     * Initialize the controller and set up the UI components.
     */
    @FXML
    public void initialize() {
        localeFormatService.applyDatePickerLocale(startDatePicker, endDatePicker);
        setupTable();
        setupListeners();
        loadData();
        updateStatistics();
    }

    /**
     * Set up the table columns and cell factories.
     */
    private void setupTable() {
        // Selection Column with Checkboxes
        selectColumn.setCellFactory(column -> new TableCell<CommissionLedger, Boolean>() {
            private final CheckBox checkBox = new CheckBox();

            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    CommissionLedger commission = getTableRow().getItem();
                    checkBox.setSelected(selectionMap.getOrDefault(commission.getId(), false));
                    checkBox.setOnAction(e -> {
                        selectionMap.put(commission.getId(), checkBox.isSelected());
                        updateSelectionCount();
                        updateButtonStates();
                    });
                    setGraphic(checkBox);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        // ID Column
        idColumn.setCellValueFactory(cellData -> new SimpleLongProperty(cellData.getValue().getId()).asObject());

        // Order ID Column
        orderIdColumn.setCellValueFactory(cellData -> {
            Long orderId = cellData.getValue().getLabOrder() != null ? cellData.getValue().getLabOrder().getId() : null;
            return new SimpleLongProperty(orderId != null ? orderId : 0L).asObject();
        });

        // Doctor Column
        doctorColumn.setCellValueFactory(cellData -> {
            Doctor doctor = cellData.getValue().getDoctor();
            String doctorName = doctor != null ? doctor.getName() : "Unknown";
            return new SimpleStringProperty(doctorName);
        });

        // Date Column
        dateColumn.setCellValueFactory(cellData -> {
            LocalDate date = cellData.getValue().getTransactionDate();
            String dateStr = date != null ? localeFormatService.formatDate(date) : "";
            return new SimpleStringProperty(dateStr);
        });

        // Bill Amount Column
        billAmountColumn.setCellValueFactory(cellData -> new SimpleObjectProperty<>(getBillAmount(cellData.getValue())));
        billAmountColumn.setCellFactory(column -> new TableCell<CommissionLedger, BigDecimal>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(localeFormatService.formatCurrency(item));
                }
            }
        });

        // Commission Rate Column
        commissionRateColumn
                .setCellValueFactory(cellData -> new SimpleObjectProperty<>(getCommissionRate(cellData.getValue())));
        commissionRateColumn.setCellFactory(column -> new TableCell<CommissionLedger, BigDecimal>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(percentFormat.format(item) + "%");
                }
            }
        });

        // Commission Amount Column
        commissionAmountColumn
                .setCellValueFactory(cellData -> new SimpleObjectProperty<>(getCommissionAmount(cellData.getValue())));
        commissionAmountColumn.setCellFactory(column -> new TableCell<CommissionLedger, BigDecimal>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(localeFormatService.formatCurrency(item));
                    // Highlight larger commissions
                    if (item.compareTo(BigDecimal.valueOf(100)) > 0) {
                        setStyle("-fx-font-weight: bold; -fx-text-fill: #27ae60;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });

        // Status Column with color coding
        statusColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getStatus()));
        statusColumn.setCellFactory(column -> new TableCell<CommissionLedger, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if ("UNPAID".equals(item)) {
                        setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                    } else if ("PAID".equals(item)) {
                        setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });

        // Actions Column with Quick Pay button
        actionsColumn.setCellFactory(column -> new TableCell<CommissionLedger, Void>() {
            private final Button payButton = new Button("Pay");

            {
                payButton.setStyle(
                        "-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 3 10;");
                payButton.setOnAction(e -> {
                    CommissionLedger commission = getTableRow().getItem();
                    if (commission != null) {
                        quickPayCommission(commission);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    CommissionLedger commission = getTableRow().getItem();
                    if ("UNPAID".equals(commission.getStatus())) {
                        setGraphic(payButton);
                    } else {
                        setGraphic(null);
                    }
                }
            }
        });

        commissionTable.setItems(filteredCommissions);
    }

    /**
     * Set up event listeners.
     */
    private void setupListeners() {
        // Table selection listener
        commissionTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            boolean hasSelection = newSelection != null;
            viewDetailsButton.setDisable(!hasSelection);
        });

        // Status filter default value
        statusFilter.setValue("All");
    }

    /**
     * Load all commission records from the database.
     */
    // @Transactional(readOnly = true) // Removed to prevent Spring proxy
    // interference with FXML injection
    private void loadData() {
        try {
            List<CommissionLedger> commissions = commissionRepository.findAll();
            allCommissions.clear();
            allCommissions.addAll(commissions);

            // Populate doctor filter
            List<String> doctors = commissions.stream()
                    .map(CommissionLedger::getDoctor)
                    .filter(Objects::nonNull)
                    .filter(doctor -> doctor.getCommissionPercentage() != null
                            && doctor.getCommissionPercentage().compareTo(BigDecimal.ZERO) > 0)
                    .map(Doctor::getName)
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());

            doctorFilter.getItems().clear();
            doctorFilter.getItems().add("All Doctors");
            doctorFilter.getItems().addAll(doctors);
            doctorFilter.setValue("All Doctors");

            applyFilters();
            updateStatistics();
            statusLabel.setText("Commissions loaded successfully");

        } catch (Exception e) {
            showError("Error loading commissions", e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Apply search and filter criteria.
     */
    private void applyFilters() {
        String doctorValue = doctorFilter.getValue();
        String statusValue = statusFilter.getValue();
        LocalDate startDate = startDatePicker.getValue();
        LocalDate endDate = endDatePicker.getValue();

        List<CommissionLedger> filtered = allCommissions.stream()
                .filter(commission -> {
                    BigDecimal rate = getCommissionRate(commission);
                    if (rate.compareTo(BigDecimal.ZERO) <= 0) {
                        return false;
                    }
                    // Doctor filter
                    boolean matchesDoctor = doctorValue == null ||
                            "All Doctors".equals(doctorValue) ||
                            (commission.getDoctor() != null && doctorValue.equals(commission.getDoctor().getName()));

                    // Status filter
                    boolean matchesStatus = statusValue == null ||
                            "All".equals(statusValue) ||
                            statusValue.equals(commission.getStatus());

                    // Date range filter
                    boolean matchesDate = true;
                    if (startDate != null && commission.getTransactionDate() != null) {
                        matchesDate = !commission.getTransactionDate().isBefore(startDate);
                    }
                    if (endDate != null && commission.getTransactionDate() != null && matchesDate) {
                        matchesDate = !commission.getTransactionDate().isAfter(endDate);
                    }

                    return matchesDoctor && matchesStatus && matchesDate;
                })
                .collect(Collectors.toList());

        filteredCommissions.clear();
        filteredCommissions.addAll(filtered);
        recordCountLabel.setText(filtered.size() + " record" + (filtered.size() != 1 ? "s" : ""));
    }

    /**
     * Update statistics labels.
     */
    private void updateStatistics() {
        try {
            // Total unpaid
            BigDecimal totalUnpaid = allCommissions.stream()
                    .filter(c -> "UNPAID".equals(c.getStatus()))
                    .map(this::getCommissionAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            totalUnpaidLabel.setText(localeFormatService.formatCurrency(totalUnpaid));

            // Total paid
            BigDecimal totalPaid = allCommissions.stream()
                    .filter(c -> "PAID".equals(c.getStatus()))
                    .map(this::getCommissionAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            totalPaidLabel.setText(localeFormatService.formatCurrency(totalPaid));

            // Pending count
            long pendingCount = commissionRepository.countByStatus("UNPAID");
            pendingCountLabel.setText(String.valueOf(pendingCount));

            // This month's commissions
            YearMonth currentMonth = YearMonth.now();
            LocalDate startOfMonth = currentMonth.atDay(1);
            LocalDate endOfMonth = currentMonth.atEndOfMonth();

            BigDecimal thisMonth = commissionRepository.findByTransactionDateBetween(startOfMonth, endOfMonth)
                    .stream()
                    .map(this::getCommissionAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            thisMonthLabel.setText(localeFormatService.formatCurrency(thisMonth));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private BigDecimal getBillAmount(CommissionLedger commission) {
        if (commission.getLabOrder() == null || commission.getLabOrder().getTotalAmount() == null) {
            return BigDecimal.ZERO;
        }
        return commission.getLabOrder().getTotalAmount();
    }

    private BigDecimal getCommissionRate(CommissionLedger commission) {
        Doctor doctor = commission.getDoctor();
        BigDecimal rate = doctor != null ? doctor.getCommissionPercentage() : null;
        return rate != null ? rate : BigDecimal.ZERO;
    }

    private BigDecimal getCommissionAmount(CommissionLedger commission) {
        BigDecimal rate = getCommissionRate(commission);
        if (rate.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return getBillAmount(commission)
                .multiply(rate)
                .divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Update selection count label.
     */
    private void updateSelectionCount() {
        long selectedCount = selectionMap.values().stream().filter(selected -> selected).count();
        selectedCountLabel.setText(selectedCount + " selected");
    }

    /**
     * Update button states based on selection.
     */
    private void updateButtonStates() {
        boolean hasSelection = selectionMap.values().stream().anyMatch(selected -> selected);
        markPaidButton.setDisable(!hasSelection);
        markUnpaidButton.setDisable(!hasSelection);
    }

    /**
     * Quick pay a single commission.
     */
    private void quickPayCommission(CommissionLedger commission) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirm Payment");
        confirmation.setHeaderText("Mark commission as paid?");
        confirmation.setContentText(
                "Doctor: " + commission.getDoctor().getName() + "\n" +
                        "Amount: " + localeFormatService.formatCurrency(getCommissionAmount(commission)) + "\n\n" +
                        "Mark this commission as paid?");

        Optional<ButtonType> result = confirmation.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                commission.setStatus("PAID");
                commission.setPaymentDate(LocalDate.now());
                commission.setPaidAmount(getCommissionAmount(commission));
                commissionRepository.save(commission);
                commissionTable.refresh();
                updateStatistics();
                statusLabel.setText("Commission marked as paid");
            } catch (Exception e) {
                showError("Error updating commission", e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Handle mark as paid button click.
     */
    @FXML
    private void handleMarkPaid() {
        List<CommissionLedger> selected = getSelectedCommissions();
        if (selected.isEmpty()) {
            showWarning("No Selection", "Please select commissions to mark as paid.");
            return;
        }

        BigDecimal totalAmount = selected.stream()
                .map(this::getCommissionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirm Payment");
        confirmation.setHeaderText("Mark " + selected.size() + " commission(s) as paid?");
        confirmation.setContentText("Total Amount: " + localeFormatService.formatCurrency(totalAmount));

        Optional<ButtonType> result = confirmation.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                for (CommissionLedger commission : selected) {
                    commission.setStatus("PAID");
                    commission.setPaymentDate(LocalDate.now());
                    commission.setPaidAmount(getCommissionAmount(commission));
                    commissionRepository.save(commission);
                }
                selectionMap.clear();
                commissionTable.refresh();
                updateStatistics();
                updateSelectionCount();
                updateButtonStates();
                statusLabel.setText(selected.size() + " commission(s) marked as paid");
                showInfo("Success", "Commissions marked as paid successfully!");
            } catch (Exception e) {
                showError("Error updating commissions", e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Handle mark as unpaid button click.
     */
    @FXML
    private void handleMarkUnpaid() {
        List<CommissionLedger> selected = getSelectedCommissions();
        if (selected.isEmpty()) {
            showWarning("No Selection", "Please select commissions to mark as unpaid.");
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirm Action");
        confirmation.setHeaderText("Mark " + selected.size() + " commission(s) as unpaid?");
        confirmation.setContentText("This will revert the payment status.");

        Optional<ButtonType> result = confirmation.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                for (CommissionLedger commission : selected) {
                    commission.setStatus("UNPAID");
                    commission.setPaymentDate(null);
                    commission.setPaidAmount(BigDecimal.ZERO);
                    commissionRepository.save(commission);
                }
                selectionMap.clear();
                commissionTable.refresh();
                updateStatistics();
                updateSelectionCount();
                updateButtonStates();
                statusLabel.setText(selected.size() + " commission(s) marked as unpaid");
            } catch (Exception e) {
                showError("Error updating commissions", e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Handle view details button click.
     */
    @FXML
    private void handleViewDetails() {
        CommissionLedger selected = commissionTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("No Selection", "Please select a commission to view details.");
            return;
        }

        showCommissionDetails(selected);
    }

    /**
     * Show commission details dialog.
     */
    private void showCommissionDetails(CommissionLedger commission) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Commission Details");
        alert.setHeaderText("Commission Record #" + commission.getId());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        int row = 0;
        grid.add(new Label("Order ID:"), 0, row);
        grid.add(new Label(commission.getLabOrder() != null ? String.valueOf(commission.getLabOrder().getId()) : "N/A"),
                1, row++);

        grid.add(new Label("Doctor:"), 0, row);
        grid.add(new Label(commission.getDoctor() != null ? commission.getDoctor().getName() : "N/A"), 1, row++);

        grid.add(new Label("Date:"), 0, row);
        grid.add(new Label(
                commission.getTransactionDate() != null
                        ? localeFormatService.formatDate(commission.getTransactionDate())
                        : "N/A"),
                1, row++);

        grid.add(new Label("Bill Amount:"), 0, row);
        grid.add(new Label(localeFormatService.formatCurrency(getBillAmount(commission))), 1, row++);

        grid.add(new Label("Commission Rate:"), 0, row);
        grid.add(new Label(percentFormat.format(getCommissionRate(commission)) + "%"), 1, row++);

        grid.add(new Label("Commission Amount:"), 0, row);
        Label amountLabel = new Label(localeFormatService.formatCurrency(getCommissionAmount(commission)));
        amountLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #27ae60;");
        grid.add(amountLabel, 1, row++);

        grid.add(new Label("Status:"), 0, row);
        Label statusLabel = new Label(commission.getStatus());
        if ("PAID".equals(commission.getStatus())) {
            statusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
        } else {
            statusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
        }
        grid.add(statusLabel, 1, row++);

        alert.getDialogPane().setContent(grid);
        alert.showAndWait();
    }

    /**
     * Handle bulk payment button click.
     */
    @FXML
    private void handleBulkPayment() {
        // Get all unpaid commissions
        List<CommissionLedger> unpaidCommissions = allCommissions.stream()
                .filter(c -> "UNPAID".equals(c.getStatus()))
                .collect(Collectors.toList());

        if (unpaidCommissions.isEmpty()) {
            showInfo("No Unpaid Commissions", "There are no unpaid commissions to process.");
            return;
        }

        // Group by doctor
        Map<Doctor, List<CommissionLedger>> byDoctor = unpaidCommissions.stream()
                .collect(Collectors.groupingBy(CommissionLedger::getDoctor));

        // Show selection dialog
        Dialog<Map<Doctor, Boolean>> dialog = new Dialog<>();
        dialog.setTitle("Bulk Payment");
        dialog.setHeaderText("Select doctors to pay");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        Map<Doctor, CheckBox> checkBoxes = new HashMap<>();
        int row = 0;

        for (Map.Entry<Doctor, List<CommissionLedger>> entry : byDoctor.entrySet()) {
            Doctor doctor = entry.getKey();
            List<CommissionLedger> commissions = entry.getValue();
            BigDecimal total = commissions.stream()
                    .map(this::getCommissionAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            CheckBox checkBox = new CheckBox();
            checkBox.setSelected(true);
            checkBoxes.put(doctor, checkBox);

            grid.add(checkBox, 0, row);
            grid.add(new Label(doctor.getName()), 1, row);
            grid.add(new Label(localeFormatService.formatCurrency(total) + " (" + commissions.size() + " records)"), 2,
                    row);
            row++;
        }

        dialog.getDialogPane().setContent(grid);
        ButtonType payButtonType = new ButtonType("Pay Selected", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(payButtonType, ButtonType.CANCEL);

        Optional<Map<Doctor, Boolean>> result = dialog.showAndWait();
        if (result.isPresent()) {
            try {
                int paidCount = 0;
                for (Map.Entry<Doctor, CheckBox> entry : checkBoxes.entrySet()) {
                    if (entry.getValue().isSelected()) {
                        List<CommissionLedger> doctorCommissions = byDoctor.get(entry.getKey());
                        for (CommissionLedger commission : doctorCommissions) {
                            commission.setStatus("PAID");
                            commission.setPaymentDate(LocalDate.now());
                            commission.setPaidAmount(getCommissionAmount(commission));
                            commissionRepository.save(commission);
                            paidCount++;
                        }
                    }
                }

                loadData();
                statusLabel.setText(paidCount + " commission(s) paid");
                showInfo("Success", "Bulk payment processed successfully!");
            } catch (Exception e) {
                showError("Error processing payment", e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Get selected commissions from the table.
     */
    private List<CommissionLedger> getSelectedCommissions() {
        return filteredCommissions.stream()
                .filter(commission -> selectionMap.getOrDefault(commission.getId(), false))
                .collect(Collectors.toList());
    }

    /**
     * Handle filter change.
     */
    @FXML
    private void handleFilterChange() {
        applyFilters();
    }

    /**
     * Handle refresh button click.
     */
    @FXML
    private void handleRefresh() {
        selectionMap.clear();
        loadData();
        updateSelectionCount();
        updateButtonStates();
    }

    /**
     * Handle close button click.
     */
    @FXML
    private void handleClose() {
        com.qdc.lims.ui.util.ViewCloseUtil.closeCurrentTabOrWindow(closeButton);
    }

    /**
     * Show information alert.
     */
    private void showInfo(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * Show warning alert.
     */
    private void showWarning(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * Show error alert.
     */
    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
