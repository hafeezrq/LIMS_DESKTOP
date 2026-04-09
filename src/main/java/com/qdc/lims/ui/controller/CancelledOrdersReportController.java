package com.qdc.lims.ui.controller;

import com.qdc.lims.entity.LabOrder;
import com.qdc.lims.repository.LabOrderRepository;
import com.qdc.lims.service.LocaleFormatService;
import com.qdc.lims.ui.util.ViewCloseUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Cancelled orders reporting screen for admin users.
 */
@Component
public class CancelledOrdersReportController {

    private static final String CANCELLED_STATUS = "CANCELLED";

    private final LabOrderRepository labOrderRepository;
    private final LocaleFormatService localeFormatService;

    @FXML
    private Button closeButton;
    @FXML
    private DatePicker startDatePicker;
    @FXML
    private DatePicker endDatePicker;
    @FXML
    private Label periodLabel;
    @FXML
    private Label cancelledCountLabel;
    @FXML
    private Label totalRefundLabel;
    @FXML
    private TableView<LabOrder> cancelledOrdersTable;
    @FXML
    private TableColumn<LabOrder, String> orderIdCol;
    @FXML
    private TableColumn<LabOrder, String> patientCol;
    @FXML
    private TableColumn<LabOrder, String> cancelledAtCol;
    @FXML
    private TableColumn<LabOrder, String> cancelledByCol;
    @FXML
    private TableColumn<LabOrder, String> refundCol;
    @FXML
    private TableColumn<LabOrder, String> reasonCol;
    @FXML
    private VBox emptyStateBox;
    @FXML
    private VBox detailsBox;

    public CancelledOrdersReportController(LabOrderRepository labOrderRepository,
            LocaleFormatService localeFormatService) {
        this.labOrderRepository = labOrderRepository;
        this.localeFormatService = localeFormatService;
    }

    @FXML
    public void initialize() {
        setupTableColumns();
        localeFormatService.applyDatePickerLocale(startDatePicker, endDatePicker);
        startDatePicker.setValue(LocalDate.now().minusDays(30));
        endDatePicker.setValue(LocalDate.now());
        resetSummary();
        showDetails(false);
    }

    @FXML
    private void handleGenerateReport() {
        LocalDate start = startDatePicker.getValue();
        LocalDate end = endDatePicker.getValue();

        if (start == null || end == null) {
            periodLabel.setText("Select both start and end dates.");
            showDetails(false);
            return;
        }
        if (end.isBefore(start)) {
            periodLabel.setText("End date must be on or after start date.");
            showDetails(false);
            return;
        }

        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = LocalDateTime.of(end, LocalTime.of(23, 59, 59));

        List<LabOrder> cancelledOrders = labOrderRepository
                .findByStatusAndCancelledAtBetweenOrderByCancelledAtDesc(CANCELLED_STATUS, startDateTime, endDateTime);

        BigDecimal totalRefund = cancelledOrders.stream()
                .map(order -> order.getCancellationRefundAmount() != null
                        ? order.getCancellationRefundAmount()
                        : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        cancelledCountLabel.setText(String.valueOf(cancelledOrders.size()));
        totalRefundLabel.setText(localeFormatService.formatCurrency(totalRefund));
        periodLabel.setText("Period: " + localeFormatService.formatDate(start) + " to "
                + localeFormatService.formatDate(end));

        cancelledOrdersTable.setItems(FXCollections.observableArrayList(cancelledOrders));
        showDetails(true);
    }

    @FXML
    private void handleQuickToday() {
        LocalDate today = LocalDate.now();
        startDatePicker.setValue(today);
        endDatePicker.setValue(today);
        handleGenerateReport();
    }

    @FXML
    private void handleQuickWeek() {
        LocalDate today = LocalDate.now();
        startDatePicker.setValue(today.minusDays(6));
        endDatePicker.setValue(today);
        handleGenerateReport();
    }

    @FXML
    private void handleQuickMonth() {
        LocalDate today = LocalDate.now();
        startDatePicker.setValue(today.withDayOfMonth(1));
        endDatePicker.setValue(today);
        handleGenerateReport();
    }

    @FXML
    private void handleClose() {
        ViewCloseUtil.closeCurrentTabOrWindow(closeButton);
    }

    private void setupTableColumns() {
        orderIdCol.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getId())));
        patientCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getPatient() != null && data.getValue().getPatient().getFullName() != null
                        ? data.getValue().getPatient().getFullName()
                        : "Unknown"));
        cancelledAtCol.setCellValueFactory(data -> new SimpleStringProperty(
                localeFormatService.formatDateTime(data.getValue().getCancelledAt())));
        cancelledByCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getCancelledBy() != null && !data.getValue().getCancelledBy().isBlank()
                        ? data.getValue().getCancelledBy()
                        : "UNKNOWN"));
        refundCol.setCellValueFactory(data -> new SimpleStringProperty(
                localeFormatService.formatCurrency(data.getValue().getCancellationRefundAmount() != null
                        ? data.getValue().getCancellationRefundAmount()
                        : BigDecimal.ZERO)));
        reasonCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getCancellationReason() != null && !data.getValue().getCancellationReason().isBlank()
                        ? data.getValue().getCancellationReason()
                        : "-"));
    }

    private void resetSummary() {
        cancelledCountLabel.setText("0");
        totalRefundLabel.setText(localeFormatService.formatCurrency(BigDecimal.ZERO));
        periodLabel.setText("Select a date range to generate cancelled order report.");
    }

    private void showDetails(boolean show) {
        emptyStateBox.setVisible(!show);
        emptyStateBox.setManaged(!show);
        detailsBox.setVisible(show);
        detailsBox.setManaged(show);
    }
}

