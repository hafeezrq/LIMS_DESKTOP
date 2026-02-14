package com.qdc.lims.ui.controller;

import com.qdc.lims.entity.Supplier;
import com.qdc.lims.repository.SupplierRepository;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.springframework.stereotype.Component;

/**
 * Controller for maintaining supplier master data.
 */
@Component
public class SupplierManagementController {

    @FXML
    private ListView<Supplier> supplierListView;

    @FXML
    private TextField companyNameField;

    @FXML
    private TextField contactPersonField;

    @FXML
    private TextField mobileField;

    @FXML
    private TextArea addressArea;

    private final SupplierRepository supplierRepository;
    private Supplier currentSupplier = null;

    /**
     * Creates the controller.
     */
    public SupplierManagementController(SupplierRepository supplierRepository) {
        this.supplierRepository = supplierRepository;
    }

    /**
     * Configures the list view and loads suppliers.
     */
    @FXML
    public void initialize() {
        supplierListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(Supplier item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getCompanyName() + " ("
                            + (item.getContactPerson() != null ? item.getContactPerson() : "N/A") + ")");
                }
            }
        });

        supplierListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                populateForm(newVal);
            }
        });

        loadSuppliers();
    }

    private void loadSuppliers() {
        ObservableList<Supplier> suppliers = FXCollections.observableArrayList(supplierRepository.findAll());
        supplierListView.setItems(suppliers);
    }

    private void populateForm(Supplier supplier) {
        currentSupplier = supplier;
        companyNameField.setText(supplier.getCompanyName());
        contactPersonField.setText(supplier.getContactPerson());
        mobileField.setText(supplier.getMobile());
        addressArea.setText(supplier.getAddress());
    }

    /**
     * Saves a new or existing supplier.
     */
    @FXML
    private void handleSave() {
        String name = companyNameField.getText().trim();
        if (name.isEmpty()) {
            showAlert("Required", "Company Name is required.");
            return;
        }

        if (currentSupplier == null) {
            currentSupplier = new Supplier();
        }

        currentSupplier.setCompanyName(name);
        currentSupplier.setContactPerson(contactPersonField.getText().trim());
        currentSupplier.setMobile(mobileField.getText().trim());
        currentSupplier.setAddress(addressArea.getText().trim());
        currentSupplier.setActive(true);

        supplierRepository.save(currentSupplier);

        loadSuppliers();
        handleClear();
        showAlert("Success", "Supplier saved successfully.");
    }

    /**
     * Deletes the selected supplier after confirmation.
     */
    @FXML
    private void handleDelete() {
        Supplier selected = supplierListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Selection Error", "Please select a supplier to delete.");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Delete");
        alert.setHeaderText("Delete Supplier '" + selected.getCompanyName() + "'?");
        alert.setContentText("This cannot be undone. Checks regarding linked items are not yet fully enforced.");

        if (alert.showAndWait().get() == ButtonType.OK) {
            supplierRepository.delete(selected);
            loadSuppliers();
            handleClear();
        }
    }

    /**
     * Clears the form and selection.
     */
    @FXML
    private void handleClear() {
        currentSupplier = null;
        companyNameField.clear();
        contactPersonField.clear();
        mobileField.clear();
        addressArea.clear();
        supplierListView.getSelectionModel().clearSelection();
    }

    /**
     * Closes the supplier management window.
     */
    @FXML
    private void handleClose() {
        com.qdc.lims.ui.util.ViewCloseUtil.closeCurrentTabOrWindow(supplierListView);
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
