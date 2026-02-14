package com.qdc.lims.ui.controller;

import com.qdc.lims.dto.FinancialCategorySummary;
import com.qdc.lims.entity.CommissionLedger;
import com.qdc.lims.entity.LabOrder;
import com.qdc.lims.entity.Payment;
import com.qdc.lims.entity.SupplierLedger;
import com.qdc.lims.repository.CommissionLedgerRepository;
import com.qdc.lims.repository.LabOrderRepository;
import com.qdc.lims.repository.PaymentRepository;
import com.qdc.lims.repository.SupplierLedgerRepository;
import com.qdc.lims.service.LocaleFormatService;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for financial reporting queries. It aggregates income and expenses
 * across orders, payments, commissions, and supplier ledgers.
 */
@Component
public class FinancialQueriesController {

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
    private Label totalIncomeLabel;
    @FXML
    private Label totalExpenseLabel;
    @FXML
    private Label netProfitLabel;
    @FXML
    private Label patientReceivableLabel;
    @FXML
    private Label unpaidCommissionLabel;
    @FXML
    private Label supplierPayableLabel;

    @FXML
    private TableView<FinancialCategorySummary> categoryTable;
    @FXML
    private TableColumn<FinancialCategorySummary, String> categoryNameCol;
    @FXML
    private TableColumn<FinancialCategorySummary, String> typeCol;
    @FXML
    private TableColumn<FinancialCategorySummary, Integer> countCol;
    @FXML
    private TableColumn<FinancialCategorySummary, String> amountCol;

    @FXML
    private PieChart expenseChart;

    /**
     * Initializes table styling, sets default dates, and generates the initial
     * report.
     */
    @FXML
    public void initialize() {
        setupTable();
        localeFormatService.applyDatePickerLocale(startDatePicker, endDatePicker);
        startDatePicker.setValue(LocalDate.now().withDayOfMonth(1));
        endDatePicker.setValue(LocalDate.now());
        handleGenerateReport();
    }

    private void setupTable() {
        categoryNameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCategory()));
        typeCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getType()));
        countCol.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getCount()).asObject());
        amountCol.setCellValueFactory(
                data -> new SimpleStringProperty(localeFormatService.formatCurrency(data.getValue().getTotalAmount())));

        amountCol.setCellFactory(col -> new TableCell<FinancialCategorySummary, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    FinancialCategorySummary row = getTableRow().getItem();
                    if (row != null) {
                        if ("INCOME".equals(row.getType())) {
                            setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                        } else {
                            setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                        }
                    }
                }
            }
        });
    }

    /**
     * Aggregates and displays financial summaries for the selected date range.
     */
    @FXML
    private void handleGenerateReport() {
        LocalDate start = startDatePicker.getValue();
        LocalDate end = endDatePicker.getValue();

        if (start == null || end == null) {
            return;
        }

        Map<String, FinancialCategorySummary> summaryMap = new HashMap<>();

        // 1. Patient Income (Lab Orders)
        List<LabOrder> orders = orderRepository.findByOrderDateBetween(start.atStartOfDay(), end.atTime(23, 59, 59));
        BigDecimal patientIncome = BigDecimal.ZERO;
        int patientCount = 0;
        for (LabOrder o : orders) {
            if (o.getPaidAmount() != null && o.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
                patientIncome = patientIncome.add(o.getPaidAmount());
                patientCount++;
            }
        }
        if (patientIncome.compareTo(BigDecimal.ZERO) > 0) {
            summaryMap.put("Patient Services",
                    new FinancialCategorySummary("Patient Services", "INCOME", patientCount, patientIncome));
        }

        // 2. Misc Payments
        List<Payment> payments = paymentRepository.findByTransactionDateBetween(start.atStartOfDay(),
                end.atTime(23, 59, 59));
        for (Payment p : payments) {
            String cat = p.getCategory() != null ? p.getCategory() : "Misc";
            summaryMap.putIfAbsent(cat, new FinancialCategorySummary(cat, p.getType(), 0, BigDecimal.ZERO));
            FinancialCategorySummary s = summaryMap.get(cat);
            s.setCount(s.getCount() + 1);
            BigDecimal amount = p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO;
            s.setTotalAmount(s.getTotalAmount().add(amount));
        }

        // 3. Doctor Commissions (Paid)
        List<CommissionLedger> commissions = commissionRepository.findByTransactionDateBetween(start, end);
        BigDecimal commTotal = BigDecimal.ZERO;
        int commCount = 0;
        for (CommissionLedger c : commissions) {
            if ("PAID".equals(c.getStatus())) {
                commTotal = commTotal.add(getCommissionAmount(c));
                commCount++;
            }
        }
        if (commTotal.compareTo(BigDecimal.ZERO) > 0) {
            summaryMap.put("Doctor Commissions",
                    new FinancialCategorySummary("Doctor Commissions", "EXPENSE", commCount, commTotal));
        }

        // 4. Supplier Payments
        List<SupplierLedger> supplierTxs = supplierRepository.findByTransactionDateBetween(start, end);
        BigDecimal supTotal = BigDecimal.ZERO;
        int supCount = 0;
        for (SupplierLedger s : supplierTxs) {
            if (s.getPaidAmount() != null && s.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
                supTotal = supTotal.add(s.getPaidAmount());
                supCount++;
            }
        }
        if (supTotal.compareTo(BigDecimal.ZERO) > 0) {
            summaryMap.put("Supplier Payments",
                    new FinancialCategorySummary("Supplier Payments", "EXPENSE", supCount, supTotal));
        }

        // Liabilities & Receivables (point-in-time)
        BigDecimal patientReceivable = orders.stream()
                .map(o -> o.getBalanceDue() != null ? o.getBalanceDue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal unpaidCommission = commissions.stream()
                .filter(c -> !"PAID".equals(c.getStatus()))
                .map(c -> {
                    BigDecimal calculated = getCommissionAmount(c);
                    BigDecimal paid = c.getPaidAmount() != null ? c.getPaidAmount() : BigDecimal.ZERO;
                    return calculated.subtract(paid).max(BigDecimal.ZERO);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal supplierPayable = supplierTxs.stream()
                .map(s -> {
                    BigDecimal bill = s.getBillAmount() != null ? s.getBillAmount() : BigDecimal.ZERO;
                    BigDecimal paid = s.getPaidAmount() != null ? s.getPaidAmount() : BigDecimal.ZERO;
                    return bill.subtract(paid).max(BigDecimal.ZERO);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Update UI
        List<FinancialCategorySummary> list = new ArrayList<>(summaryMap.values());
        categoryTable.setItems(FXCollections.observableArrayList(list));

        BigDecimal totalIncome = list.stream().filter(s -> "INCOME".equals(s.getType()))
                .map(FinancialCategorySummary::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalExpense = list.stream().filter(s -> "EXPENSE".equals(s.getType()))
                .map(FinancialCategorySummary::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        totalIncomeLabel.setText(localeFormatService.formatCurrency(totalIncome));
        totalExpenseLabel.setText(localeFormatService.formatCurrency(totalExpense));
        netProfitLabel.setText(localeFormatService.formatCurrency(totalIncome.subtract(totalExpense)));
        if (patientReceivableLabel != null) {
            patientReceivableLabel.setText(localeFormatService.formatCurrency(patientReceivable));
        }
        if (unpaidCommissionLabel != null) {
            unpaidCommissionLabel.setText(localeFormatService.formatCurrency(unpaidCommission));
        }
        if (supplierPayableLabel != null) {
            supplierPayableLabel.setText(localeFormatService.formatCurrency(supplierPayable));
        }

        // Pie Chart (Expenses Only)
        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
        list.stream().filter(s -> "EXPENSE".equals(s.getType()))
                .forEach(s -> pieData.add(new PieChart.Data(s.getCategory(),
                        s.getTotalAmount() != null ? s.getTotalAmount().doubleValue() : 0.0)));
        expenseChart.setData(pieData);
    }

    /**
     * Closes the financial queries window.
     */
    @FXML
    private void handleClose() {
        com.qdc.lims.ui.util.ViewCloseUtil.closeCurrentTabOrWindow(closeButton);
    }

    /**
     * Calculates a commission amount from the underlying doctor rate and lab order
     * total.
     */
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
