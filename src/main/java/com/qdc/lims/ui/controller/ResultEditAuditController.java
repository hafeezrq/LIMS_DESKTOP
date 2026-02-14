package com.qdc.lims.ui.controller;

import com.qdc.lims.entity.LabResultEditAudit;
import com.qdc.lims.repository.LabResultEditAuditRepository;
import com.qdc.lims.service.LocaleFormatService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Displays completed-result correction audit records.
 */
@Component
public class ResultEditAuditController {

    private final LabResultEditAuditRepository auditRepository;
    private final LocaleFormatService localeFormatService;

    @FXML
    private DatePicker fromDatePicker;
    @FXML
    private DatePicker toDatePicker;
    @FXML
    private TextField orderIdField;
    @FXML
    private TextField editedByField;
    @FXML
    private TextField testNameField;
    @FXML
    private Button closeButton;
    @FXML
    private Label recordCountLabel;

    @FXML
    private TableView<LabResultEditAudit> auditTable;
    @FXML
    private TableColumn<LabResultEditAudit, String> editedAtCol;
    @FXML
    private TableColumn<LabResultEditAudit, String> orderIdCol;
    @FXML
    private TableColumn<LabResultEditAudit, String> resultIdCol;
    @FXML
    private TableColumn<LabResultEditAudit, String> testNameCol;
    @FXML
    private TableColumn<LabResultEditAudit, String> editedByCol;
    @FXML
    private TableColumn<LabResultEditAudit, String> oldValueCol;
    @FXML
    private TableColumn<LabResultEditAudit, String> newValueCol;
    @FXML
    private TableColumn<LabResultEditAudit, String> reasonCol;
    @FXML
    private TableColumn<LabResultEditAudit, String> deliveredCol;

    public ResultEditAuditController(LabResultEditAuditRepository auditRepository,
            LocaleFormatService localeFormatService) {
        this.auditRepository = auditRepository;
        this.localeFormatService = localeFormatService;
    }

    @FXML
    public void initialize() {
        setupTable();
        localeFormatService.applyDatePickerLocale(fromDatePicker, toDatePicker);
        resetFilters();
        handleSearch();
    }

    private void setupTable() {
        editedAtCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getEditedAt() != null ? localeFormatService.formatDateTime(data.getValue().getEditedAt()) : ""));
        orderIdCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getLabOrder() != null && data.getValue().getLabOrder().getId() != null
                        ? String.valueOf(data.getValue().getLabOrder().getId())
                        : "-"));
        resultIdCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getLabResult() != null && data.getValue().getLabResult().getId() != null
                        ? String.valueOf(data.getValue().getLabResult().getId())
                        : "-"));
        testNameCol.setCellValueFactory(data -> new SimpleStringProperty(safe(data.getValue().getTestName())));
        editedByCol.setCellValueFactory(data -> new SimpleStringProperty(safe(data.getValue().getEditedBy())));
        oldValueCol.setCellValueFactory(data -> new SimpleStringProperty(safe(data.getValue().getPreviousValue())));
        newValueCol.setCellValueFactory(data -> new SimpleStringProperty(safe(data.getValue().getNewValue())));
        reasonCol.setCellValueFactory(data -> new SimpleStringProperty(safe(data.getValue().getReason())));
        deliveredCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().isReportDeliveredAtEdit() ? "Yes" : "No"));
    }

    @FXML
    private void handleSearch() {
        LocalDate from = fromDatePicker.getValue();
        LocalDate to = toDatePicker.getValue();
        if (from != null && to != null && from.isAfter(to)) {
            showWarning("Invalid Date Range", "From date cannot be after To date.");
            return;
        }

        Long orderId = parseOrderId();
        if (orderIdField.getText() != null && !orderIdField.getText().trim().isEmpty() && orderId == null) {
            return;
        }

        String editedBy = normalize(editedByField.getText());
        String testName = normalize(testNameField.getText());

        List<LabResultEditAudit> filtered = auditRepository.findAllByOrderByEditedAtDesc().stream()
                .filter(audit -> matchesDateRange(audit, from, to))
                .filter(audit -> orderId == null
                        || (audit.getLabOrder() != null && orderId.equals(audit.getLabOrder().getId())))
                .filter(audit -> containsIgnoreCase(audit.getEditedBy(), editedBy))
                .filter(audit -> containsIgnoreCase(audit.getTestName(), testName))
                .collect(Collectors.toList());

        auditTable.setItems(FXCollections.observableArrayList(filtered));
        recordCountLabel.setText(filtered.size() + " record(s) found");
    }

    @FXML
    private void handleReset() {
        resetFilters();
        handleSearch();
    }

    @FXML
    private void handleClose() {
        com.qdc.lims.ui.util.ViewCloseUtil.closeCurrentTabOrWindow(closeButton);
    }

    private void resetFilters() {
        fromDatePicker.setValue(LocalDate.now().minusDays(30));
        toDatePicker.setValue(LocalDate.now());
        orderIdField.clear();
        editedByField.clear();
        testNameField.clear();
    }

    private Long parseOrderId() {
        String orderIdText = normalize(orderIdField.getText());
        if (orderIdText.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(orderIdText);
        } catch (NumberFormatException ex) {
            showWarning("Invalid Order ID", "Order ID must be a numeric value.");
            return null;
        }
    }

    private boolean matchesDateRange(LabResultEditAudit audit, LocalDate from, LocalDate to) {
        if (audit.getEditedAt() == null) {
            return false;
        }
        LocalDate date = audit.getEditedAt().toLocalDate();
        if (from != null && date.isBefore(from)) {
            return false;
        }
        if (to != null && date.isAfter(to)) {
            return false;
        }
        return true;
    }

    private boolean containsIgnoreCase(String value, String filter) {
        if (filter == null || filter.isEmpty()) {
            return true;
        }
        return normalize(value).contains(filter);
    }

    private String safe(String value) {
        String normalized = normalize(value);
        return normalized.isEmpty() ? "-" : value;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
