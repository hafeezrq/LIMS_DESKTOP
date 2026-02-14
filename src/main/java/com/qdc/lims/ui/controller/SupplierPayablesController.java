package com.qdc.lims.ui.controller;

import com.qdc.lims.entity.SupplierLedger;
import com.qdc.lims.repository.SupplierLedgerRepository;
import com.qdc.lims.service.LocaleFormatService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller that summarizes supplier bills, payments, and outstanding dues over
 * a date range.
 */
@Component
public class SupplierPayablesController {

    @Autowired
    private SupplierLedgerRepository supplierLedgerRepository;
    @Autowired
    private LocaleFormatService localeFormatService;

    @FXML
    private DatePicker startDatePicker;
    @FXML
    private DatePicker endDatePicker;
    @FXML
    private ComboBox<String> supplierFilter;
    @FXML
    private Label totalBillLabel;
    @FXML
    private Label totalPaidLabel;
    @FXML
    private Label totalDueLabel;
    @FXML
    private TableView<SupplierPayableSummary> summaryTable;
    @FXML
    private TableColumn<SupplierPayableSummary, String> supplierCol;
    @FXML
    private TableColumn<SupplierPayableSummary, String> billCol;
    @FXML
    private TableColumn<SupplierPayableSummary, String> paidCol;
    @FXML
    private TableColumn<SupplierPayableSummary, String> dueCol;
    @FXML
    private TableColumn<SupplierPayableSummary, String> latestInvoiceCol;
    @FXML
    private TableColumn<SupplierPayableSummary, String> latestDueCol;
    @FXML
    private Button closeButton;

    /**
     * Initializes date defaults and loads the initial summary.
     */
    @FXML
    public void initialize() {
        localeFormatService.applyDatePickerLocale(startDatePicker, endDatePicker);
        startDatePicker.setValue(LocalDate.now().withDayOfMonth(1));
        endDatePicker.setValue(LocalDate.now());
        setupTable();
        handleGenerate();
    }

    private void setupTable() {
        supplierCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().supplierName));
        billCol.setCellValueFactory(data -> new SimpleStringProperty(formatAmount(data.getValue().totalBill)));
        paidCol.setCellValueFactory(data -> new SimpleStringProperty(formatAmount(data.getValue().totalPaid)));
        dueCol.setCellValueFactory(data -> new SimpleStringProperty(formatAmount(data.getValue().totalDue)));
        latestInvoiceCol.setCellValueFactory(
                data -> new SimpleStringProperty(data.getValue().latestInvoiceNumber));
        latestDueCol.setCellValueFactory(
                data -> new SimpleStringProperty(data.getValue().latestDueDate));
    }

    /**
     * Recomputes supplier payable summaries for the selected range and filter.
     */
    @FXML
    private void handleGenerate() {
        LocalDate start = startDatePicker.getValue();
        LocalDate end = endDatePicker.getValue();
        if (start == null || end == null) {
            return;
        }

        List<SupplierLedger> ledgers = supplierLedgerRepository.findByTransactionDateBetween(start, end);

        if (supplierFilter.getItems().isEmpty()) {
            supplierFilter.setItems(FXCollections.observableArrayList(
                    ledgers.stream()
                            .map(l -> l.getSupplier() != null ? l.getSupplier().getCompanyName() : "Unknown")
                            .distinct()
                            .sorted()
                            .collect(Collectors.toList())));
            supplierFilter.getItems().add(0, "All Suppliers");
            supplierFilter.setValue("All Suppliers");
        }

        String selectedSupplier = supplierFilter.getValue();
        if (selectedSupplier != null && !"All Suppliers".equals(selectedSupplier)) {
            ledgers = ledgers.stream()
                    .filter(l -> l.getSupplier() != null && selectedSupplier.equals(l.getSupplier().getCompanyName()))
                    .collect(Collectors.toList());
        }

        Map<String, List<SupplierLedger>> bySupplier = ledgers.stream()
                .collect(Collectors.groupingBy(l -> l.getSupplier() != null ? l.getSupplier().getCompanyName() : "Unknown"));

        ObservableList<SupplierPayableSummary> rows = FXCollections.observableArrayList();
        BigDecimal totalBill = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        BigDecimal totalDue = BigDecimal.ZERO;

        for (Map.Entry<String, List<SupplierLedger>> entry : bySupplier.entrySet()) {
            BigDecimal bill = entry.getValue().stream()
                    .map(l -> l.getBillAmount() != null ? l.getBillAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal paid = entry.getValue().stream()
                    .map(l -> l.getPaidAmount() != null ? l.getPaidAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal due = bill.subtract(paid).max(BigDecimal.ZERO);

            SupplierLedger latest = entry.getValue().stream()
                    .sorted((a, b) -> {
                        LocalDate aDate = a.getInvoiceDate() != null ? a.getInvoiceDate() : a.getTransactionDate();
                        LocalDate bDate = b.getInvoiceDate() != null ? b.getInvoiceDate() : b.getTransactionDate();
                        if (aDate == null && bDate == null) {
                            return 0;
                        }
                        if (aDate == null) {
                            return 1;
                        }
                        if (bDate == null) {
                            return -1;
                        }
                        return bDate.compareTo(aDate);
                    })
                    .findFirst()
                    .orElse(null);

            String latestInvoice = latest != null && latest.getInvoiceNumber() != null
                    ? latest.getInvoiceNumber()
                    : "-";
            String latestDue = latest != null && latest.getDueDate() != null
                    ? localeFormatService.formatDate(latest.getDueDate())
                    : "-";

            rows.add(new SupplierPayableSummary(entry.getKey(), bill, paid, due, latestInvoice, latestDue));

            totalBill = totalBill.add(bill);
            totalPaid = totalPaid.add(paid);
            totalDue = totalDue.add(due);
        }

        summaryTable.setItems(rows);
        totalBillLabel.setText(formatAmount(totalBill));
        totalPaidLabel.setText(formatAmount(totalPaid));
        totalDueLabel.setText(formatAmount(totalDue));
    }

    /**
     * Closes the payables window.
     */
    @FXML
    private void handleClose() {
        com.qdc.lims.ui.util.ViewCloseUtil.closeCurrentTabOrWindow(closeButton);
    }

    private String formatAmount(BigDecimal amount) {
        return localeFormatService.formatCurrency(amount);
    }

    /**
     * Immutable table row for supplier payable aggregates.
     */
    public static class SupplierPayableSummary {
        private final String supplierName;
        private final BigDecimal totalBill;
        private final BigDecimal totalPaid;
        private final BigDecimal totalDue;
        private final String latestInvoiceNumber;
        private final String latestDueDate;

        /**
         * Creates a supplier payable summary row.
         */
        public SupplierPayableSummary(String supplierName, BigDecimal totalBill, BigDecimal totalPaid,
                BigDecimal totalDue,
                String latestInvoiceNumber, String latestDueDate) {
            this.supplierName = supplierName;
            this.totalBill = totalBill;
            this.totalPaid = totalPaid;
            this.totalDue = totalDue;
            this.latestInvoiceNumber = latestInvoiceNumber;
            this.latestDueDate = latestDueDate;
        }
    }
}
