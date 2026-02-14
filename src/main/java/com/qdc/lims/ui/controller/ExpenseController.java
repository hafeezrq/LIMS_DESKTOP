package com.qdc.lims.ui.controller;

import com.qdc.lims.entity.Payment;
import com.qdc.lims.repository.PaymentRepository;
import com.qdc.lims.service.LocaleFormatService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Controller for recording operational expenses and querying them over a date
 * range.
 */
@Component
public class ExpenseController {

    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private LocaleFormatService localeFormatService;

    @FXML
    private Button closeButton;
    @FXML
    private DatePicker expenseDate;
    @FXML
    private ComboBox<String> categoryCombo;
    @FXML
    private TextField descriptionField;
    @FXML
    private TextField amountField;
    @FXML
    private ComboBox<String> paymentMethodCombo;

    @FXML
    private DatePicker filterStartDate;
    @FXML
    private DatePicker filterEndDate;
    @FXML
    private Label totalExpensesLabel;

    @FXML
    private TableView<Payment> expenseTable;
    @FXML
    private TableColumn<Payment, String> idCol;
    @FXML
    private TableColumn<Payment, String> dateCol;
    @FXML
    private TableColumn<Payment, String> categoryCol;
    @FXML
    private TableColumn<Payment, String> descCol;
    @FXML
    private TableColumn<Payment, String> methodCol;
    @FXML
    private TableColumn<Payment, String> amountCol;

    /**
     * Initializes defaults and loads the initial expense view.
     */
    @FXML
    public void initialize() {
        setupTable();
        setupForm();

        localeFormatService.applyDatePickerLocale(expenseDate, filterStartDate, filterEndDate);
        filterStartDate.setValue(LocalDate.now().withDayOfMonth(1));
        filterEndDate.setValue(LocalDate.now());

        handleSearch();
    }

    private void setupForm() {
        expenseDate.setValue(LocalDate.now());
        categoryCombo.setItems(FXCollections.observableArrayList(
                "RENT", "UTILITIES", "SALARY", "MAINTENANCE", "SUPPLIES", "MARKETING", "MISC"));
        paymentMethodCombo.setItems(FXCollections.observableArrayList(
                "CASH", "BANK_TRANSFER", "CHEQUE", "CREDIT_CARD"));
        paymentMethodCombo.setValue("CASH");
    }

    private void setupTable() {
        idCol.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getId())));
        dateCol.setCellValueFactory(data -> {
            LocalDateTime dt = data.getValue().getTransactionDate();
            return new SimpleStringProperty(dt != null ? localeFormatService.formatDate(dt.toLocalDate()) : "");
        });
        categoryCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCategory()));
        descCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDescription()));
        methodCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getPaymentMethod()));
        amountCol.setCellValueFactory(
                data -> new SimpleStringProperty(localeFormatService.formatCurrency(data.getValue().getAmount())));
    }

    /**
     * Validates and saves a new expense entry.
     */
    @FXML
    private void handleAddExpense() {
        try {
            if (descriptionField.getText().isEmpty() || amountField.getText().isEmpty()) {
                showAlert("Error", "Description and amount are required.");
                return;
            }

            java.math.BigDecimal amount = localeFormatService.parseNumber(amountField.getText());

            Payment payment = new Payment();
            payment.setType("EXPENSE");
            payment.setCategory(categoryCombo.getValue() != null ? categoryCombo.getValue() : "MISC");
            payment.setDescription(descriptionField.getText());
            payment.setAmount(amount);
            payment.setPaymentMethod(paymentMethodCombo.getValue());
            payment.setTransactionDate(expenseDate.getValue().atStartOfDay());

            paymentRepository.save(payment);

            descriptionField.clear();
            amountField.clear();

            handleSearch();

        } catch (NumberFormatException e) {
            showAlert("Error", "Invalid amount format.");
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", "Failed to save: " + e.getMessage());
        }
    }

    /**
     * Queries expenses for the selected date range and updates totals.
     */
    @FXML
    private void handleSearch() {
        LocalDate start = filterStartDate.getValue();
        LocalDate end = filterEndDate.getValue();

        if (start != null && end != null) {
            List<Payment> expenses = paymentRepository.findByTypeAndTransactionDateBetween(
                    "EXPENSE", start.atStartOfDay(), end.atTime(23, 59, 59));

            expenseTable.setItems(FXCollections.observableArrayList(expenses));

            java.math.BigDecimal total = expenses.stream()
                    .map(p -> p.getAmount() != null ? p.getAmount() : java.math.BigDecimal.ZERO)
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            totalExpensesLabel.setText(localeFormatService.formatCurrency(total));
        }
    }

    /**
     * Closes the expense window.
     */
    @FXML
    private void handleClose() {
        com.qdc.lims.ui.util.ViewCloseUtil.closeCurrentTabOrWindow(closeButton);
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.show();
    }
}
