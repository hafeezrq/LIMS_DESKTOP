package com.qdc.lims.ui.controller;

import com.qdc.lims.dto.FinanceTransaction;
import com.qdc.lims.entity.CommissionLedger;
import com.qdc.lims.entity.LabOrder;
import com.qdc.lims.entity.Payment;
import com.qdc.lims.entity.SupplierLedger;
import com.qdc.lims.repository.CommissionLedgerRepository;
import com.qdc.lims.repository.LabOrderRepository;
import com.qdc.lims.repository.PaymentRepository;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Controller for Payment History.
 * Aggregates financial data from multiple sources:
 * 1. Lab Orders (Patient Payments) - INCOME
 * 2. General Payments (Misc Income/Expenses) - INCOME/EXPENSE
 * 3. Doctor Commissions (Paid Commissions) - EXPENSE
 * 4. Supplier Ledger (Payments to Suppliers) - EXPENSE
 */
@Component
public class PaymentHistoryController {

    @Autowired
    private LabOrderRepository orderRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private CommissionLedgerRepository commissionRepository;
    @Autowired
    private SupplierLedgerRepository supplierRepository;
    @Autowired
    private LocaleFormatService localeFormatService;

    @FXML
    private Button closeButton;
    @FXML
    private DatePicker startDatePicker;
    @FXML
    private DatePicker endDatePicker;
    @FXML
    private ComboBox<String> typeFilter;

    @FXML
    private Label totalIncomeLabel;
    @FXML
    private Label totalExpenseLabel;
    @FXML
    private Label netCashFlowLabel;
    @FXML
    private Label recordCountLabel;

    @FXML
    private TableView<FinanceTransaction> transactionTable;
    @FXML
    private TableColumn<FinanceTransaction, String> dateCol;
    @FXML
    private TableColumn<FinanceTransaction, String> idCol;
    @FXML
    private TableColumn<FinanceTransaction, String> typeCol;
    @FXML
    private TableColumn<FinanceTransaction, String> categoryCol;
    @FXML
    private TableColumn<FinanceTransaction, String> descCol;
    @FXML
    private TableColumn<FinanceTransaction, String> amountCol;
    @FXML
    private TableColumn<FinanceTransaction, String> statusCol;

    private ObservableList<FinanceTransaction> allTransactions = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTable();
        setupFilters();

        // Default to current month
        localeFormatService.applyDatePickerLocale(startDatePicker, endDatePicker);
        startDatePicker.setValue(LocalDate.now().withDayOfMonth(1));
        endDatePicker.setValue(LocalDate.now());

        handleSearch();
    }

    private void setupFilters() {
        typeFilter.setItems(FXCollections.observableArrayList("All Transactions", "Income Only", "Expense Only"));
        typeFilter.setValue("All Transactions");
    }

    private void setupTable() {
        dateCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getDate() != null ? localeFormatService.formatDate(data.getValue().getDate()) : ""));
        idCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getSourceId()));
        typeCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getType()));
        categoryCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCategory()));
        descCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDescription()));
        statusCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStatus()));

        amountCol.setCellValueFactory(data -> new SimpleStringProperty(
                localeFormatService.formatCurrency(data.getValue().getAmount())));

        // Color coding for amount
        amountCol.setCellFactory(column -> new TableCell<FinanceTransaction, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    FinanceTransaction tx = getTableRow().getItem();
                    if (tx != null) {
                        if ("INCOME".equals(tx.getType())) {
                            setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                        } else {
                            setStyle("-fx-text-fill: #c0392b; -fx-font-weight: bold;");
                        }
                    }
                }
            }
        });
    }

    @FXML
    private void handleSearch() {
        LocalDate start = startDatePicker.getValue();
        LocalDate end = endDatePicker.getValue();
        String typeSelection = typeFilter.getValue();

        if (start == null || end == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Missing Dates");
            alert.setContentText("Please select both start and end dates.");
            alert.show();
            return;
        }

        List<FinanceTransaction> transactions = new ArrayList<>();

        // 1. Load Patient Income (Lab Orders) - consider partially paid or fully paid
        // Note: For strict cash-accounting, we should track exactly WHEN payments were
        // made.
        // Currently LabOrder only has orderDate. We will use orderDate as proxy for
        // transaction date for now.
        // Ideally we would have a separate PaymentReceipt entity linked to order.
        List<LabOrder> orders = orderRepository.findByOrderDateBetween(start.atStartOfDay(), end.atTime(23, 59, 59));
        for (LabOrder order : orders) {
            if (order.getPaidAmount() != null && order.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
                transactions.add(new FinanceTransaction(
                        "ORD-" + order.getId(),
                        order.getOrderDate().toLocalDate(),
                        "INCOME",
                        "Patient Payment",
                        "Lab Order for " + order.getPatient().getFullName(),
                        order.getPaidAmount(),
                        "COMPLETED"));
            }
        }

        // 2. Load General Payments (Expenses & Misc Income)
        List<Payment> generalPayments = paymentRepository.findByTransactionDateBetween(start.atStartOfDay(),
                end.atTime(23, 59, 59));
        for (Payment p : generalPayments) {
            transactions.add(new FinanceTransaction(
                    "GP-" + p.getId(),
                    p.getTransactionDate() != null ? p.getTransactionDate().toLocalDate() : null,
                    p.getType(),
                    p.getCategory(),
                    p.getDescription(),
                    p.getAmount(),
                    "COMPLETED"));
        }

        // 3. Paid Doctor Commissions
        // Only include PAID status
        List<CommissionLedger> paidCommissions = commissionRepository.findByTransactionDateBetween(start, end)
                .stream()
                .filter(c -> "PAID".equals(c.getStatus()))
                .collect(Collectors.toList());

        for (CommissionLedger c : paidCommissions) {
            // Check if paymentDate exists (added recently), else fallback to
            // transactionDate
            LocalDate txDate = c.getPaymentDate() != null ? c.getPaymentDate() : c.getTransactionDate();

            // Re-filter by date if using paymentDate which might differ from
            // transactionDate
            if (!txDate.isBefore(start) && !txDate.isAfter(end)) {
                transactions.add(new FinanceTransaction(
                        "COM-" + c.getId(),
                        txDate,
                        "EXPENSE",
                        "Doctor Commission",
                        "Commission for Dr. " + c.getDoctor().getName(),
                        getCommissionAmount(c),
                        "COMPLETED"));
            }
        }

        // 4. Supplier Payments
        List<SupplierLedger> supplierTxs = supplierRepository.findByTransactionDateBetween(start, end);
        for (SupplierLedger s : supplierTxs) {
            if (s.getPaidAmount() != null && s.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
                transactions.add(new FinanceTransaction(
                        "SUP-" + s.getId(),
                        s.getTransactionDate(),
                        "EXPENSE",
                        "Supplier Payment",
                        "Payment to " + s.getSupplier().getName(),
                        s.getPaidAmount(),
                        "COMPLETED"));
            }
            // Note: If you want to track purchases as expenses immediately (Accrual Basis),
            // use BillAmount.
            // For Cash Basis, we use PaidAmount. Let's stick to Cash Flow.
        }

        // Filter and Sort
        List<FinanceTransaction> filtered = transactions.stream()
                .filter(t -> {
                    if ("Income Only".equals(typeSelection))
                        return "INCOME".equals(t.getType());
                    if ("Expense Only".equals(typeSelection))
                        return "EXPENSE".equals(t.getType());
                    return true;
                })
                .sorted(Comparator.comparing(FinanceTransaction::getDate).reversed())
                .collect(Collectors.toList());

        allTransactions.setAll(filtered);
        transactionTable.setItems(allTransactions);

        // Update Stats
        BigDecimal totalIncome = filtered.stream().filter(t -> "INCOME".equals(t.getType()))
                .map(t -> t.getAmount() != null ? t.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalExpense = filtered.stream().filter(t -> "EXPENSE".equals(t.getType()))
                .map(t -> t.getAmount() != null ? t.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        totalIncomeLabel.setText(localeFormatService.formatCurrency(totalIncome));
        totalExpenseLabel.setText(localeFormatService.formatCurrency(totalExpense));
        netCashFlowLabel.setText(localeFormatService.formatCurrency(totalIncome.subtract(totalExpense)));
        recordCountLabel.setText(filtered.size() + " records found");
    }

    @FXML
    private void handleExport() {
        // Placeholder for export functionality
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Export");
        alert.setContentText("CSV Export feature coming soon!");
        alert.show();
    }

    @FXML
    private void handleClose() {
        com.qdc.lims.ui.util.ViewCloseUtil.closeCurrentTabOrWindow(closeButton);
    }

    private BigDecimal getCommissionAmount(CommissionLedger commission) {
        if (commission.getDoctor() == null || commission.getDoctor().getCommissionPercentage() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal rate = commission.getDoctor().getCommissionPercentage();
        if (rate.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (commission.getLabOrder() == null || commission.getLabOrder().getTotalAmount() == null) {
            return BigDecimal.ZERO;
        }
        return commission.getLabOrder().getTotalAmount()
                .multiply(rate)
                .divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP);
    }
}
