package com.qdc.lims.ui.controller;

import com.qdc.lims.entity.CommissionLedger;
import com.qdc.lims.repository.CommissionLedgerRepository;
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
 * Controller that summarizes doctor commissions over a date range and exposes
 * aggregate paid/due totals.
 */
@Component
public class DoctorCommissionLedgerController {

    @Autowired
    private CommissionLedgerRepository commissionRepository;
    @Autowired
    private LocaleFormatService localeFormatService;

    @FXML
    private DatePicker startDatePicker;
    @FXML
    private DatePicker endDatePicker;
    @FXML
    private ComboBox<String> doctorFilter;
    @FXML
    private Label totalDueLabel;
    @FXML
    private Label totalPaidLabel;
    @FXML
    private Label totalCommissionLabel;
    @FXML
    private TableView<DoctorCommissionSummary> summaryTable;
    @FXML
    private TableColumn<DoctorCommissionSummary, String> doctorCol;
    @FXML
    private TableColumn<DoctorCommissionSummary, String> totalBillCol;
    @FXML
    private TableColumn<DoctorCommissionSummary, String> commissionCol;
    @FXML
    private TableColumn<DoctorCommissionSummary, String> paidCol;
    @FXML
    private TableColumn<DoctorCommissionSummary, String> dueCol;
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
        doctorCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().doctorName));
        totalBillCol.setCellValueFactory(data -> new SimpleStringProperty(formatAmount(data.getValue().totalBill)));
        commissionCol
                .setCellValueFactory(data -> new SimpleStringProperty(formatAmount(data.getValue().commissionTotal)));
        paidCol.setCellValueFactory(data -> new SimpleStringProperty(formatAmount(data.getValue().paidTotal)));
        dueCol.setCellValueFactory(data -> new SimpleStringProperty(formatAmount(data.getValue().dueTotal)));
    }

    /**
     * Recomputes the commission summary for the selected date range and filter.
     */
    @FXML
    private void handleGenerate() {
        LocalDate start = startDatePicker.getValue();
        LocalDate end = endDatePicker.getValue();
        if (start == null || end == null) {
            return;
        }

        List<CommissionLedger> commissions = commissionRepository.findByTransactionDateBetween(start, end);

        List<CommissionLedger> eligibleCommissions = commissions.stream()
                .filter(c -> c.getDoctor() != null)
                .filter(c -> {
                    BigDecimal rate = c.getDoctor().getCommissionPercentage();
                    return rate != null && rate.compareTo(BigDecimal.ZERO) > 0;
                })
                .collect(Collectors.toList());

        String previousSelection = doctorFilter.getValue();
        List<String> doctorNames = eligibleCommissions.stream()
                .map(c -> c.getDoctor().getName())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        doctorFilter.setItems(FXCollections.observableArrayList(doctorNames));
        doctorFilter.getItems().add(0, "All Doctors");
        if (previousSelection != null && doctorFilter.getItems().contains(previousSelection)) {
            doctorFilter.setValue(previousSelection);
        } else {
            doctorFilter.setValue("All Doctors");
        }

        String selectedDoctor = doctorFilter.getValue();
        if (selectedDoctor != null && !"All Doctors".equals(selectedDoctor)) {
            eligibleCommissions = eligibleCommissions.stream()
                    .filter(c -> selectedDoctor.equals(c.getDoctor().getName()))
                    .collect(Collectors.toList());
        }

        Map<String, List<CommissionLedger>> byDoctor = eligibleCommissions.stream()
                .collect(Collectors.groupingBy(c -> c.getDoctor().getName()));

        ObservableList<DoctorCommissionSummary> rows = FXCollections.observableArrayList();
        BigDecimal totalDue = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        BigDecimal totalCommission = BigDecimal.ZERO;

        for (Map.Entry<String, List<CommissionLedger>> entry : byDoctor.entrySet()) {
            BigDecimal billTotal = entry.getValue().stream()
                    .map(this::getBillAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal commissionTotal = entry.getValue().stream()
                    .map(this::getCommissionAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal paid = entry.getValue().stream()
                    .map(c -> c.getPaidAmount() != null ? c.getPaidAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal due = commissionTotal.subtract(paid).max(BigDecimal.ZERO);

            if (commissionTotal.compareTo(BigDecimal.ZERO) > 0) {
                rows.add(new DoctorCommissionSummary(entry.getKey(), billTotal, commissionTotal, paid, due));
            }

            totalDue = totalDue.add(due);
            totalPaid = totalPaid.add(paid);
            totalCommission = totalCommission.add(commissionTotal);
        }

        summaryTable.setItems(rows);
        totalDueLabel.setText(formatAmount(totalDue));
        totalPaidLabel.setText(formatAmount(totalPaid));
        totalCommissionLabel.setText(formatAmount(totalCommission));
    }

    /**
     * Closes the window.
     */
    @FXML
    private void handleClose() {
        com.qdc.lims.ui.util.ViewCloseUtil.closeCurrentTabOrWindow(closeButton);
    }

    private String formatAmount(BigDecimal amount) {
        return localeFormatService.formatCurrency(amount);
    }

    private BigDecimal getBillAmount(CommissionLedger commission) {
        if (commission.getLabOrder() == null || commission.getLabOrder().getTotalAmount() == null) {
            return BigDecimal.ZERO;
        }
        return commission.getLabOrder().getTotalAmount();
    }

    private BigDecimal getCommissionAmount(CommissionLedger commission) {
        BigDecimal rate = commission.getDoctor() != null ? commission.getDoctor().getCommissionPercentage() : null;
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return getBillAmount(commission)
                .multiply(rate)
                .divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Immutable table row for doctor commission aggregates.
     */
    public static class DoctorCommissionSummary {
        private final String doctorName;
        private final BigDecimal totalBill;
        private final BigDecimal commissionTotal;
        private final BigDecimal paidTotal;
        private final BigDecimal dueTotal;

        /**
         * Creates a commission summary row.
         */
        public DoctorCommissionSummary(String doctorName, BigDecimal totalBill, BigDecimal commissionTotal,
                BigDecimal paidTotal, BigDecimal dueTotal) {
            this.doctorName = doctorName;
            this.totalBill = totalBill;
            this.commissionTotal = commissionTotal;
            this.paidTotal = paidTotal;
            this.dueTotal = dueTotal;
        }
    }
}
