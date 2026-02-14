package com.qdc.lims.ui.controller;

import com.qdc.lims.service.ConfigService;
import com.qdc.lims.service.BrandingService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import java.io.File;

/**
 * Controller for the system settings screen backed by {@link ConfigService}
 * key-value configuration entries.
 */
@Controller
public class SystemSettingsController {

    @Autowired
    private ConfigService configService;
    @Autowired
    private BrandingService brandingService;

    // General Info
    @FXML
    private TextField clinicNameField;
    @FXML
    private TextArea clinicAddressArea;
    @FXML
    private TextField clinicPhoneField;
    @FXML
    private TextField clinicEmailField;

    // Reports
    @FXML
    private TextField headerTextField;
    @FXML
    private TextArea footerTextArea;
    @FXML
    private TextField logoPathField;

    // Billing
    @FXML
    private TextField currencySymbolField;
    @FXML
    private TextField taxRateField;

    @FXML
    private Label statusLabel;

    /**
     * Loads persisted settings into the form.
     */
    @FXML
    public void initialize() {
        loadSettings();
    }

    /**
     * Refreshes configuration cache and hydrates all UI fields.
     */
    private void loadSettings() {
        configService.refreshCache();

        clinicNameField.setText(configService.get("CLINIC_NAME"));
        clinicAddressArea.setText(configService.get("CLINIC_ADDRESS"));
        clinicPhoneField.setText(configService.get("CLINIC_PHONE"));
        clinicEmailField.setText(configService.get("CLINIC_EMAIL"));

        headerTextField.setText(configService.get("REPORT_HEADER_TEXT"));
        footerTextArea.setText(configService.get("REPORT_FOOTER_TEXT"));
        logoPathField.setText(configService.get("REPORT_LOGO_PATH"));

        currencySymbolField.setText(configService.get("CURRENCY_SYMBOL"));
        taxRateField.setText(configService.get("TAX_RATE_PERCENT"));

        statusLabel.setText("Settings loaded.");
    }

    /**
     * Persists the edited settings back to the configuration store.
     */
    @FXML
    private void handleSave() {
        try {
            configService.set("CLINIC_NAME", clinicNameField.getText());
            configService.set("CLINIC_ADDRESS", clinicAddressArea.getText());
            configService.set("CLINIC_PHONE", clinicPhoneField.getText());
            configService.set("CLINIC_EMAIL", clinicEmailField.getText());

            configService.set("REPORT_HEADER_TEXT", headerTextField.getText());
            configService.set("REPORT_FOOTER_TEXT", footerTextArea.getText());
            configService.set("REPORT_LOGO_PATH", logoPathField.getText());

            configService.set("CURRENCY_SYMBOL", currencySymbolField.getText());
            configService.set("TAX_RATE_PERCENT", taxRateField.getText());

            configService.refreshCache();
            configService.updateLabProfileCompletionFlag();
            brandingService.refreshAllTaggedStageTitles();

            boolean complete = configService.isLabProfileComplete();
            statusLabel.setText(complete
                    ? "Configuration saved successfully!"
                    : "Saved, but lab profile is incomplete (name, address, phone).");

            Stage stage = (Stage) clinicNameField.getScene().getWindow();
            brandingService.tagStage(stage, "System Configuration");

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Success");
            alert.setHeaderText(null);
            alert.setContentText("System configuration updated successfully.");
            alert.showAndWait();

        } catch (Exception e) {
            statusLabel.setText("Error saving settings.");
            e.printStackTrace();

            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Save Failed");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }

    /**
     * Lets the user browse for a report logo file and stores its path.
     */
    @FXML
    private void handleBrowseLogo() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Logo Image");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif"));

        File selectedFile = fileChooser.showOpenDialog(clinicNameField.getScene().getWindow());
        if (selectedFile != null) {
            logoPathField.setText(selectedFile.getAbsolutePath());
        }
    }

    /**
     * Closes the settings window.
     */
    @FXML
    private void handleClose() {
        com.qdc.lims.ui.util.ViewCloseUtil.closeCurrentTabOrWindow(clinicNameField);
    }
}
