package com.qdc.lims.ui.controller;

import com.qdc.lims.entity.LabOrder;
import com.qdc.lims.repository.LabOrderRepository;
import com.qdc.lims.service.LocaleFormatService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Controller for Revenue Reports.
 * Rebuilt from scratch to ensure FXML compatibility and stability.
 */
@Component
public class RevenueReportsController {

    @Autowired
    private LabOrderRepository orderRepository;
    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private LocaleFormatService localeFormatService;

    @FXML
    private Button closeButton;
    @FXML
    private DatePicker startDatePicker;
    @FXML
    private DatePicker endDatePicker;
    @FXML
    private Label totalRevenueLabel;
    @FXML
    private Label totalPaidLabel;
    @FXML
    private Label totalOutstandingLabel;
    @FXML
    private Label averageOrderLabel;
    @FXML
    private Label totalCountLabel;
    @FXML
    private Label periodLabel;
    @FXML
    private TableView<LabOrder> reportTable;
    @FXML
    private TableColumn<LabOrder, String> orderIdCol;
    @FXML
    private TableColumn<LabOrder, String> dateCol;
    @FXML
    private TableColumn<LabOrder, String> patientCol;
    @FXML
    private TableColumn<LabOrder, String> amountCol;
    @FXML
    private TableColumn<LabOrder, String> statusCol;

    @FXML
    private CheckBox outstandingOnlyBox;
    @FXML
    private VBox emptyStateBox;
    @FXML
    private VBox detailsBox;

    @FXML
    public void initialize() {
        System.out.println("RevenueReportsController initialized.");

        // Setup table columns safely
        setupTableColumns();

        // Set default dates
        if (startDatePicker != null && endDatePicker != null) {
            localeFormatService.applyDatePickerLocale(startDatePicker, endDatePicker);
            startDatePicker.setValue(LocalDate.now().minusDays(30)); // Default to last 30 days
            endDatePicker.setValue(LocalDate.now());
        }

        resetSummary();
        showDetails(false);
    }

    private void setupTableColumns() {
        if (orderIdCol != null) {
            orderIdCol.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getId())));
        }
        if (dateCol != null) {
            dateCol.setCellValueFactory(data -> {
                LocalDateTime date = data.getValue().getOrderDate();
                return new SimpleStringProperty(
                        date != null ? localeFormatService.formatDateTime(date) : "");
            });
        }
        if (patientCol != null) {
            patientCol.setCellValueFactory(data -> new SimpleStringProperty(
                    data.getValue().getPatient() != null ? data.getValue().getPatient().getFullName() : "Unknown"));
        }
        if (amountCol != null) {
            amountCol.setCellValueFactory(data -> new SimpleStringProperty(
                    localeFormatService.formatCurrency(
                            data.getValue().getTotalAmount() != null ? data.getValue().getTotalAmount()
                                    : java.math.BigDecimal.ZERO)));
        }
        if (statusCol != null) {
            statusCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStatus()));
        }
    }

    @FXML
    private void handleGenerateReport() {
        if (orderRepository == null) {
            System.err.println("OrderRepository is null! Spring injection failed.");
            return;
        }

        try {
            LocalDate start = startDatePicker.getValue();
            LocalDate end = endDatePicker.getValue();

            if (start != null && end != null) {
                LocalDateTime startDateTime = start.atStartOfDay();
                LocalDateTime endDateTime = end.atTime(23, 59, 59);

                List<LabOrder> orders = orderRepository.findByOrderDateBetween(startDateTime, endDateTime);

                // Filter for outstanding payments if checkbox is selected
                if (outstandingOnlyBox.isSelected()) {
                    orders = orders.stream()
                            .filter(o -> o.getBalanceDue() != null
                                    && o.getBalanceDue().compareTo(java.math.BigDecimal.ZERO) > 0)
                            .toList();
                }

                java.math.BigDecimal total = orders.stream()
                        .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : java.math.BigDecimal.ZERO)
                        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                java.math.BigDecimal totalPaid = orders.stream()
                        .map(o -> o.getPaidAmount() != null ? o.getPaidAmount() : java.math.BigDecimal.ZERO)
                        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                java.math.BigDecimal totalOutstanding = orders.stream()
                        .map(o -> o.getBalanceDue() != null ? o.getBalanceDue() : java.math.BigDecimal.ZERO)
                        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

                if (totalRevenueLabel != null) {
                    totalRevenueLabel.setText(localeFormatService.formatCurrency(total));
                }
                if (totalPaidLabel != null) {
                    totalPaidLabel.setText(localeFormatService.formatCurrency(totalPaid));
                }
                if (totalOutstandingLabel != null) {
                    totalOutstandingLabel.setText(localeFormatService.formatCurrency(totalOutstanding));
                }
                if (totalCountLabel != null) {
                    totalCountLabel.setText(String.valueOf(orders.size()));
                }
                if (averageOrderLabel != null) {
                    java.math.BigDecimal avg = orders.isEmpty()
                            ? java.math.BigDecimal.ZERO
                            : total.divide(java.math.BigDecimal.valueOf(orders.size()), 4,
                                    java.math.RoundingMode.HALF_UP);
                    averageOrderLabel.setText(localeFormatService.formatCurrency(avg));
                }
                if (periodLabel != null) {
                    periodLabel.setText("Period: " + localeFormatService.formatDate(start) + " to "
                            + localeFormatService.formatDate(end) + " | Source: Patient lab orders");
                }
                if (reportTable != null) {
                    reportTable.setItems(FXCollections.observableArrayList(orders));
                }
                showDetails(true);
            }
        } catch (Exception e) {
            System.err.println("Error generating report: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleQuickToday() {
        if (startDatePicker != null && endDatePicker != null) {
            LocalDate today = LocalDate.now();
            startDatePicker.setValue(today);
            endDatePicker.setValue(today);
            handleGenerateReport();
        }
    }

    @FXML
    private void handleQuickWeek() {
        if (startDatePicker != null && endDatePicker != null) {
            LocalDate today = LocalDate.now();
            startDatePicker.setValue(today.minusDays(6));
            endDatePicker.setValue(today);
            handleGenerateReport();
        }
    }

    @FXML
    private void handleQuickMonth() {
        if (startDatePicker != null && endDatePicker != null) {
            LocalDate today = LocalDate.now();
            startDatePicker.setValue(today.withDayOfMonth(1));
            endDatePicker.setValue(today);
            handleGenerateReport();
        }
    }

    private void resetSummary() {
        if (totalRevenueLabel != null) {
            totalRevenueLabel.setText(localeFormatService.formatCurrency(java.math.BigDecimal.ZERO));
        }
        if (totalPaidLabel != null) {
            totalPaidLabel.setText(localeFormatService.formatCurrency(java.math.BigDecimal.ZERO));
        }
        if (totalOutstandingLabel != null) {
            totalOutstandingLabel.setText(localeFormatService.formatCurrency(java.math.BigDecimal.ZERO));
        }
        if (averageOrderLabel != null) {
            averageOrderLabel.setText(localeFormatService.formatCurrency(java.math.BigDecimal.ZERO));
        }
        if (totalCountLabel != null) {
            totalCountLabel.setText("0");
        }
        if (periodLabel != null) {
            periodLabel.setText("Select a date range to generate the report.");
        }
    }

    private void showDetails(boolean show) {
        if (emptyStateBox != null) {
            emptyStateBox.setVisible(!show);
            emptyStateBox.setManaged(!show);
        }
        if (detailsBox != null) {
            detailsBox.setVisible(show);
            detailsBox.setManaged(show);
        }
    }

    @FXML
    private void handleOpenProfitLoss() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/financial_queries.fxml"));
            loader.setControllerFactory(applicationContext::getBean);
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Financial Summary & P&L");
            stage.setScene(new Scene(root));
            stage.show();
        } catch (Exception e) {
            System.err.println("Failed to open P&L: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleClose(ActionEvent event) {
        com.qdc.lims.ui.util.ViewCloseUtil.closeCurrentTabOrWindow(closeButton);
    }
}
