package com.qdc.lims.ui.controller;

import com.qdc.lims.ui.DashboardNavigator;
import com.qdc.lims.ui.SessionManager;
import com.qdc.lims.ui.navigation.DashboardType;
import com.qdc.lims.ui.util.LogoutUtil;
import com.qdc.lims.service.BrandingService;
import com.qdc.lims.service.CancellationApprovalKeyService;
import com.qdc.lims.service.ConfigService;
import com.qdc.lims.service.LocaleFormatService;
import com.qdc.lims.service.UpdateService;
import com.qdc.lims.entity.User;

import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Modality;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * JavaFX controller for the admin dashboard window.
 * This dashboard is for system administration, management, and configuration
 * ONLY.
 * For operational work (patient registration, orders, lab work), admins should
 * switch to Lab or Reception dashboards using the dashboard switcher.
 */
@Controller
public class AdminDashboardController {

    private final ApplicationContext applicationContext;
    private final DashboardNavigator navigator;
    private final BrandingService brandingService;
    private final ConfigService configService;
    private final CancellationApprovalKeyService cancellationApprovalKeyService;
    private final LocaleFormatService localeFormatService;
    private final UpdateService updateService;

    @FXML
    private Label statusLabel;
    @FXML
    private Label dateTimeLabel;
    @FXML
    private BorderPane mainContainer;
    @FXML
    private Button switchRoleButton;
    @FXML
    private Label userLabel;
    @FXML
    private Label footerBrandLabel;

    @FXML
    private ToggleButton receptionLabPasswordToggle;
    @FXML
    private ToggleButton sessionTimeoutToggle;
    @FXML
    private TabPane adminTabs;
    @FXML
    private Tab dashboardHomeTab;

    private static final String TAB_FXML_PATH_KEY = "adminTabFxmlPath";
    private boolean updateCheckInProgress = false;

    public AdminDashboardController(ApplicationContext applicationContext,
            DashboardNavigator navigator,
            BrandingService brandingService,
            ConfigService configService,
            CancellationApprovalKeyService cancellationApprovalKeyService,
            LocaleFormatService localeFormatService,
            UpdateService updateService) {
        this.applicationContext = applicationContext;
        this.navigator = navigator;
        this.brandingService = brandingService;
        this.configService = configService;
        this.cancellationApprovalKeyService = cancellationApprovalKeyService;
        this.localeFormatService = localeFormatService;
        this.updateService = updateService;
    }

    @FXML
    public void initialize() {
        startClock();

        if (mainContainer != null) {
            mainContainer.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    newScene.windowProperty().addListener((obs2, oldWindow, newWindow) -> {
                        if (newWindow instanceof Stage stage) {
                            brandingService.tagStage(stage, DashboardType.ADMIN.getWindowTitle());
                            User user = SessionManager.getUser(stage);
                            if (user != null) {
                                String fullName = user.getFullName();
                                if (userLabel != null) {
                                    userLabel.setText(fullName);
                                }
                            }
                        }
                    });
                }
            });
        }

        if (switchRoleButton != null) {
            switchRoleButton.setVisible(false);
        }

        if (statusLabel != null) {
            statusLabel.setText("System Ready");
        }

        applyBranding();
        syncReceptionLabPasswordToggle();
        syncSessionTimeoutToggle();
    }

    private void startClock() {
        Thread clockThread = new Thread(() -> {
            while (true) {
                try {
                    String time = localeFormatService.formatDateTime(LocalDateTime.now());
                    Platform.runLater(() -> {
                        if (dateTimeLabel != null)
                            dateTimeLabel.setText(time);
                    });
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        clockThread.setDaemon(true);
        clockThread.start();
    }

    /**
     * Handle Switch User button - allows quick switching to a different user.
     */
    @FXML
    private void handleSwitchUser() {
        // Delegate to navigator for consistent behavior
        Stage stage = resolveCurrentStage();
        if (stage != null) {
            navigator.switchUser(stage);
        }
    }

    @FXML
    private void handleLogout() {
        LogoutUtil.confirmAndCloseParentTab(mainContainer);
    }

    // ===========================================================================================
    // ADMINISTRATIVE MENU HANDLERS
    // These are management/configuration functions for system administrators
    // For operational work (patients, orders, lab), admins should switch to
    // Lab/Reception dashboards
    // ===========================================================================================

    // USER MANAGEMENT
    @FXML
    private void handleDashboardHome() {
        if (adminTabs == null) {
            return;
        }
        if (dashboardHomeTab != null) {
            adminTabs.getSelectionModel().select(dashboardHomeTab);
            return;
        }
        if (!adminTabs.getTabs().isEmpty()) {
            adminTabs.getSelectionModel().select(0);
        }
    }

    @FXML
    private void handleUserRoles() {
        // Redirect to user management as roles are managed there for now
        handleUserManagement();
    }

    @FXML
    private void handleActivityLogs() {
        showAlert("Feature Coming Soon", "Activity Logs viewer will be implemented soon.");
    }

    // INVENTORY MANAGEMENT
    @FXML
    private void handleInventoryView() {
        openAdminWindow("/fxml/inventory_view.fxml", "Inventory Management");
    }

    @FXML
    private void handleSuppliers() {
        openAdminWindow("/fxml/supplier_management.fxml", "Supplier Management");
    }

    @FXML
    private void handleSupplierPayables() {
        openAdminWindow("/fxml/supplier_payables.fxml", "Supplier Payables");
    }

    @FXML
    private void handlePurchases() {
        showAlert("Feature Coming Soon", "Purchases & Orders management will be implemented soon.");
    }

    @FXML
    private void handleLowStock() {
        showAlert("Feature Coming Soon", "Low Stock Alerts will be implemented soon.");
    }

    @FXML
    private void handleStockAdjustments() {
        // Redirect to inventory view
        handleInventoryView();
    }

    @FXML
    private void handleViewStock() {
        handleInventoryView();
    }

    // TEST CONFIGURATION
    @FXML
    public void handleTestDefinitions() {
        openAdminWindow("/fxml/test_definitions.fxml", "Test Definitions Management");
    }

    @FXML
    private void handlePanels() {
        openAdminWindow("/fxml/panel_management.fxml", "Panel Management");
    }

    @FXML
    private void handleTestRecipes() {
        openAdminWindow("/fxml/test_recipes.fxml", "Test Recipe Management");
    }

    @FXML
    private void handleReferenceRanges() {
        openAdminWindow("/fxml/reference_ranges.fxml", "Reference Ranges Management");
    }

    @FXML
    private void handleTestCategories() {
        openAdminWindow("/fxml/category_management.fxml", "Department Management");
    }

    @FXML
    private void handleTestPricing() {
        showAlert("Feature Coming Soon", "Test Pricing management will be implemented soon.");
    }

    // DOCTOR MANAGEMENT
    @FXML
    private void handleDoctorPanel() {
        openAdminWindow("/fxml/doctor_panel.fxml", "Doctor Management Panel");
    }

    @FXML
    private void handleDoctorCommissions() {
        openAdminWindow("/fxml/commission_management.fxml", "Commission Management");
    }

    @FXML
    private void handleDoctorCommissionLedger() {
        openAdminWindow("/fxml/doctor_commission_ledger.fxml", "Doctor Commission Ledger");
    }

    @FXML
    private void handleCommissionRates() {
        showAlert("Feature Coming Soon", "Commission Rates configuration will be implemented soon.");
    }

    @FXML
    private void handleCommissionPayments() {
        showAlert("Feature Coming Soon", "Commission Payments will be implemented soon.");
    }

    @FXML
    private void handleDoctorStats() {
        showAlert("Feature Coming Soon", "Doctor Statistics will be implemented soon.");
    }

    // FINANCE & REPORTS
    @FXML
    private void handleRevenueReports() {
        openAdminWindow("/fxml/revenue_reports.fxml", "Revenue Reports & Analytics", 1200, 800);
    }

    @FXML
    private void handleDailyRevenue() {
        // Redirect to main revenue reports
        handleRevenueReports();
    }

    @FXML
    private void handleMonthlyRevenue() {
        // Redirect to main revenue reports
        handleRevenueReports();
    }

    @FXML
    private void handleCommissionReport() {
        // Redirect to Doctor Commissions
        handleDoctorCommissions();
    }

    @FXML
    private void handlePaymentHistory() {
        openAdminWindow("/fxml/payment_history.fxml", "Payment History & Cash Flow");
    }

    @FXML
    private void handleOutstandingPayments() {
        handleRevenueReports();
    }

    @FXML
    private void handleFinancialQueries() {
        openAdminWindow("/fxml/financial_queries.fxml", "Financial Summary & P&L");
    }

    @FXML
    private void handleExpenses() {
        openAdminWindow("/fxml/expenses.fxml", "Expense Management");
    }

    // SYSTEM SETTINGS
    @FXML
    private void handleBackupSettings() {
        openAdminWindow("/fxml/backup_settings.fxml", "Backup & Snapshots");
    }

    @FXML
    private void handleDatabaseSettings() {
        showAlert("Feature Coming Soon", "Database Settings will be implemented soon.");
    }

    @FXML
    private void handleSystemConfig() {
        openAdminWindow("/fxml/system_settings.fxml", "System Configuration");
    }

    @FXML
    private void handleMaintenanceToken() {
        if (!ensureAdminAccess("Maintenance Token")) {
            return;
        }

        boolean configured = cancellationApprovalKeyService.isKeyConfigured();
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Maintenance Token");
        dialog.setHeaderText(configured
                ? "Rotate cancellation approval key"
                : "Set cancellation approval key");

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setPadding(new javafx.geometry.Insets(15));

        PasswordField currentKeyField = null;
        if (configured) {
            currentKeyField = new PasswordField();
            currentKeyField.setPromptText("Current key");
            content.getChildren().addAll(new Label("Current key"), currentKeyField);
        }

        PasswordField newKeyField = new PasswordField();
        newKeyField.setPromptText("New key");
        PasswordField confirmKeyField = new PasswordField();
        confirmKeyField.setPromptText("Confirm new key");

        Label hintLabel = new Label("Use at least 6 characters. This key is required for order cancellation.");
        hintLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 11;");
        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #c0392b;");

        content.getChildren().addAll(
                new Label("New key"),
                newKeyField,
                new Label("Confirm key"),
                confirmKeyField,
                hintLabel,
                errorLabel);
        dialog.getDialogPane().setContent(content);

        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveType);
        saveButton.setStyle("-fx-background-color: #2c3e50; -fx-text-fill: white;");

        PasswordField currentFieldRef = currentKeyField;
        Runnable updateSaveState = () -> {
            boolean missingCurrent = configured
                    && (currentFieldRef == null || currentFieldRef.getText() == null || currentFieldRef.getText().trim().isEmpty());
            boolean disabled = missingCurrent
                    || newKeyField.getText() == null
                    || newKeyField.getText().trim().isEmpty()
                    || confirmKeyField.getText() == null
                    || confirmKeyField.getText().trim().isEmpty();
            saveButton.setDisable(disabled);
        };
        updateSaveState.run();
        if (currentKeyField != null) {
            currentKeyField.textProperty().addListener((obs, oldVal, newVal) -> updateSaveState.run());
            currentKeyField.setOnAction(e -> {
                if (!saveButton.isDisabled()) {
                    saveButton.fire();
                }
            });
        }
        newKeyField.textProperty().addListener((obs, oldVal, newVal) -> updateSaveState.run());
        confirmKeyField.textProperty().addListener((obs, oldVal, newVal) -> updateSaveState.run());
        confirmKeyField.setOnAction(e -> {
            if (!saveButton.isDisabled()) {
                saveButton.fire();
            }
        });

        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String current = currentFieldRef != null ? currentFieldRef.getText() : "";
            String newKey = newKeyField.getText() == null ? "" : newKeyField.getText().trim();
            String confirm = confirmKeyField.getText() == null ? "" : confirmKeyField.getText().trim();

            if (configured && !cancellationApprovalKeyService.verifyKey(current)) {
                errorLabel.setText("Current key is incorrect.");
                event.consume();
                return;
            }
            if (!newKey.equals(confirm)) {
                errorLabel.setText("New key and confirm key do not match.");
                event.consume();
                return;
            }
            try {
                cancellationApprovalKeyService.setKey(newKey);
            } catch (IllegalArgumentException ex) {
                errorLabel.setText(ex.getMessage());
                event.consume();
                return;
            }

            if (statusLabel != null) {
                statusLabel.setText("Cancellation approval key updated.");
            }
            showAlert("Saved", "Cancellation approval key saved successfully.");
        });

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == saveType && statusLabel != null) {
            statusLabel.setText("Cancellation key ready for reception cancellation approvals.");
        }
    }

    @FXML
    private void handleResultEditAudit() {
        openAdminWindow("/fxml/result_edit_audit.fxml", "Result Edit Audit", 1220, 720);
    }

    @FXML
    private void handleCheckForUpdates() {
        if (updateCheckInProgress) {
            showAlert("Update Check", "Update check is already in progress.");
            return;
        }
        updateCheckInProgress = true;

        Alert progress = new Alert(Alert.AlertType.INFORMATION);
        progress.setTitle("Checking for Updates");
        progress.setHeaderText(null);
        progress.initModality(Modality.NONE);
        ProgressIndicator indicator = new ProgressIndicator();
        VBox content = new VBox(10, new Label("Checking for updates..."), indicator);
        progress.getDialogPane().setContent(content);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        progress.getDialogPane().getButtonTypes().setAll(cancelType);

        Task<UpdateService.UpdateCheckResult> task = new Task<>() {
            @Override
            protected UpdateService.UpdateCheckResult call() {
                return updateService.checkForUpdates();
            }
        };

        Thread[] workerRef = new Thread[1];
        boolean[] finalized = { false };
        PauseTransition timeoutGuard = new PauseTransition(javafx.util.Duration.seconds(30));
        Runnable finalizeDialog = () -> {
            if (finalized[0]) {
                return;
            }
            finalized[0] = true;
            updateCheckInProgress = false;
            timeoutGuard.stop();
            progress.close();
        };

        progress.setOnCloseRequest(evt -> {
            if (!task.isDone()) {
                task.cancel(true);
                if (workerRef[0] != null) {
                    workerRef[0].interrupt();
                }
            }
            finalizeDialog.run();
        });

        timeoutGuard.setOnFinished(evt -> {
            if (task.isDone() || finalized[0]) {
                return;
            }
            task.cancel(true);
            if (workerRef[0] != null) {
                workerRef[0].interrupt();
            }
            finalizeDialog.run();
            showAlert("Update Check", "Update check timed out. Please try again.");
        });

        progress.show();
        timeoutGuard.playFromStart();

        task.setOnSucceeded(event -> {
            if (finalized[0]) {
                return;
            }
            UpdateService.UpdateCheckResult result = task.getValue();
            finalizeDialog.run();
            showUpdateResult(result);
        });

        task.setOnFailed(event -> {
            if (finalized[0]) {
                return;
            }
            Throwable ex = task.getException();
            finalizeDialog.run();
            showAlert("Update Check Failed", ex != null ? ex.getMessage() : "Unable to check for updates.");
        });

        task.setOnCancelled(event -> {
            if (finalized[0]) {
                return;
            }
            finalizeDialog.run();
            if (statusLabel != null) {
                statusLabel.setText("Update check canceled.");
            }
        });

        Thread thread = new Thread(task, "update-check");
        workerRef[0] = thread;
        thread.setDaemon(true);
        thread.start();
    }

    private void showUpdateResult(UpdateService.UpdateCheckResult result) {
        if (result == null) {
            showAlert("Update Check", "No update information available.");
            return;
        }

        switch (result.getStatus()) {
            case UPDATE_AVAILABLE -> showUpdateAvailableDialog(result);
            case UP_TO_DATE -> showAlert("Update Check",
                    "You are already on the latest version (" + safe(result.getCurrentVersion()) + ").");
            case UNKNOWN -> showAlert("Update Check",
                    "Latest version: " + safe(result.getLatestTag())
                            + "\nCurrent version: " + safe(result.getCurrentVersion())
                            + "\nUnable to compare versions automatically.");
            case ERROR -> showAlert("Update Check",
                    result.getMessage() != null ? result.getMessage() : "Unable to check for updates.");
        }
    }

    private void showUpdateAvailableDialog(UpdateService.UpdateCheckResult result) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Update Available");
        alert.setHeaderText("New version available: " + safe(result.getLatestTag()));

        StringBuilder content = new StringBuilder();
        content.append("Current version: ").append(safe(result.getCurrentVersion())).append("\n");
        content.append("Latest version: ").append(safe(result.getLatestTag())).append("\n\n");
        if (result.getReleaseNotes() != null && !result.getReleaseNotes().isBlank()) {
            content.append(result.getReleaseNotes().trim());
        } else {
            content.append("Release notes not available.");
        }

        alert.setContentText(content.toString());

        ButtonType download = new ButtonType("Download & Install", ButtonBar.ButtonData.OK_DONE);
        ButtonType later = new ButtonType("Later", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(download, later);

        Optional<ButtonType> choice = alert.showAndWait();
        if (choice.isPresent() && choice.get() == download) {
            startInstallerDownload(result);
            return;
        }
        if (statusLabel != null) {
            statusLabel.setText("Update postponed.");
        }
    }

    private void startInstallerDownload(UpdateService.UpdateCheckResult result) {
        if (!updateService.isInstallerSupported()) {
            showAlert("Update Not Supported", "Installer updates are only supported on Windows.");
            return;
        }
        if (result.getAssetUrl() == null || result.getAssetUrl().isBlank()) {
            showAlert("Update Not Available", "Installer download link not found.");
            return;
        }

        Alert progress = new Alert(Alert.AlertType.INFORMATION);
        progress.setTitle("Downloading Update");
        progress.setHeaderText(null);
        progress.initModality(Modality.NONE);
        ProgressIndicator indicator = new ProgressIndicator();
        VBox content = new VBox(10, new Label("Downloading installer..."), indicator);
        progress.getDialogPane().setContent(content);
        progress.getDialogPane().getButtonTypes().clear();
        progress.show();

        Task<java.nio.file.Path> downloadTask = new Task<>() {
            @Override
            protected java.nio.file.Path call() throws Exception {
                return updateService.downloadInstaller(result.getAssetUrl());
            }
        };

        downloadTask.setOnSucceeded(event -> {
            progress.hide();
            java.nio.file.Path installer = downloadTask.getValue();
            if (installer == null) {
                showAlert("Update Failed", "Installer download failed.");
                return;
            }
            try {
                updateService.launchInstaller(installer);
                Alert done = new Alert(Alert.AlertType.CONFIRMATION);
                done.setTitle("Installer Started");
                done.setHeaderText(null);
                done.setContentText("Installer started. Close the app to continue?");
                Optional<ButtonType> choice = done.showAndWait();
                if (choice.isPresent() && choice.get() == ButtonType.OK) {
                    Platform.exit();
                }
            } catch (Exception e) {
                showAlert("Update Failed", e.getMessage());
            }
        });

        downloadTask.setOnFailed(event -> {
            progress.hide();
            Throwable ex = downloadTask.getException();
            showAlert("Update Failed", ex != null ? ex.getMessage() : "Installer download failed.");
        });

        Thread thread = new Thread(downloadTask, "update-download");
        thread.setDaemon(true);
        thread.start();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "Unknown" : value;
    }

    @FXML
    private void handleReportTemplates() {
        showAlert("Feature Coming Soon", "Report Templates editor will be implemented soon.");
    }

    @FXML
    private void handleUserManagement() {
        openAdminWindow("/fxml/user_management.fxml", "User Management");
    }

    @FXML
    private void handleReceptionLabPasswordToggle() {
        if (receptionLabPasswordToggle == null) {
            return;
        }
        boolean requirePassword = receptionLabPasswordToggle.isSelected();
        configService.set("REQUIRE_PASSWORD_RECEPTION_LAB", Boolean.toString(requirePassword));
        applyReceptionLabPasswordToggleStyle(requirePassword);
        if (statusLabel != null) {
            statusLabel.setText(requirePassword
                    ? "Reception/Lab password requirement enabled."
                    : "Reception/Lab password requirement disabled.");
        }
    }

    @FXML
    private void handleSessionTimeoutToggle() {
        if (sessionTimeoutToggle == null) {
            return;
        }
        boolean enabled = sessionTimeoutToggle.isSelected();
        configService.set("SESSION_TIMEOUT_ENABLED", Boolean.toString(enabled));
        SessionManager.setSessionTimeoutEnabled(enabled);
        applySessionTimeoutToggleStyle(enabled);
        if (statusLabel != null) {
            statusLabel.setText(enabled
                    ? "Session timeout enabled."
                    : "Session timeout disabled.");
        }
    }

    /**
     * Check if current user has admin access.
     */
    /**
     * Check if current window's user has admin access.
     * UPDATED to take Stage as a parameter.
     */
    private boolean isAdminAccessAllowed(Stage stage) {
        // --- FIX: Check the user of the passed stage, not the global active stage ---
        User user = SessionManager.getUser(stage);

        if (user == null || user.getRoles() == null) {
            System.out.println("DEBUG: User is null for this stage");
            return false;
        }

        boolean isAdmin = user.getRoles().stream()
                .anyMatch(r -> r.getName().equals("ROLE_ADMIN"));

        System.out.println("DEBUG isAdminAccessAllowed: " + isAdmin);
        return isAdmin;
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showAccessDenied(String feature) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Access Denied");
        alert.setHeaderText("Insufficient Permissions");
        alert.setContentText("You don't have permission to access " + feature + ".");
        alert.showAndWait();
    }

    private void openAdminWindow(String fxmlPath, String title) {
        openAdminWindow(fxmlPath, title, null, null);
    }

    private void openAdminWindow(String fxmlPath, String title, Integer width, Integer height) {
        if (!ensureAdminAccess(title)) {
            return;
        }
        openTab(fxmlPath, title);
    }

    private void openTab(String fxmlPath, String title) {
        try {
            if (adminTabs == null) {
                showAlert("Error", "Admin workspace is not ready.");
                return;
            }

            Tab existingTab = findExistingTab(fxmlPath);
            if (existingTab != null) {
                adminTabs.getSelectionModel().select(existingTab);
                return;
            }

            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            loader.setControllerFactory(applicationContext::getBean);
            Parent root = loader.load();

            Tab tab = new Tab(title);
            tab.setContent(root);
            tab.setClosable(true);
            tab.setTooltip(new Tooltip(title));
            tab.getProperties().put(TAB_FXML_PATH_KEY, fxmlPath);

            adminTabs.getTabs().add(tab);
            adminTabs.getSelectionModel().select(tab);
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", "Could not open tab: " + title + "\n" + e.getMessage());
        }
    }

    private Tab findExistingTab(String fxmlPath) {
        if (adminTabs == null) {
            return null;
        }
        return adminTabs.getTabs().stream()
                .filter(tab -> fxmlPath.equals(tab.getProperties().get(TAB_FXML_PATH_KEY)))
                .findFirst()
                .orElse(null);
    }

    private boolean ensureAdminAccess(String feature) {
        Stage stage = resolveCurrentStage();
        if (stage == null) {
            showAlert("Error", "Admin window is not ready.");
            return false;
        }
        if (!isAdminAccessAllowed(stage)) {
            showAccessDenied(feature);
            return false;
        }
        return true;
    }

    private Stage resolveCurrentStage() {
        if (mainContainer == null || mainContainer.getScene() == null || mainContainer.getScene().getWindow() == null) {
            return null;
        }
        return (Stage) mainContainer.getScene().getWindow();
    }

    private void applyBranding() {
        if (footerBrandLabel != null) {
            footerBrandLabel.setText(brandingService.getCopyrightLine());
        }
    }

    private void syncReceptionLabPasswordToggle() {
        if (receptionLabPasswordToggle == null) {
            return;
        }
        String value = configService.getTrimmed("REQUIRE_PASSWORD_RECEPTION_LAB", "true");
        boolean requirePassword = Boolean.parseBoolean(value);
        receptionLabPasswordToggle.setSelected(requirePassword);
        applyReceptionLabPasswordToggleStyle(requirePassword);
    }

    private void syncSessionTimeoutToggle() {
        if (sessionTimeoutToggle == null) {
            return;
        }
        String value = configService.getTrimmed("SESSION_TIMEOUT_ENABLED", "false");
        boolean enabled = Boolean.parseBoolean(value);
        sessionTimeoutToggle.setSelected(enabled);
        SessionManager.setSessionTimeoutEnabled(enabled);
        applySessionTimeoutToggleStyle(enabled);
    }

    private void applyReceptionLabPasswordToggleStyle(boolean requirePassword) {
        if (receptionLabPasswordToggle == null) {
            return;
        }
        receptionLabPasswordToggle.setText(requirePassword
                ? "Reception/Lab Password: ON"
                : "Reception/Lab Password: OFF");
        String color = requirePassword ? "#2c3e50" : "#e67e22";
        receptionLabPasswordToggle.setStyle("-fx-background-color: " + color
                + "; -fx-text-fill: white; -fx-padding: 6 12; -fx-background-radius: 5;");
    }

    private void applySessionTimeoutToggleStyle(boolean enabled) {
        if (sessionTimeoutToggle == null) {
            return;
        }
        sessionTimeoutToggle.setText(enabled
                ? "Session Timeout: ON"
                : "Session Timeout: OFF");
        String color = enabled ? "#2c3e50" : "#e67e22";
        sessionTimeoutToggle.setStyle("-fx-background-color: " + color
                + "; -fx-text-fill: white; -fx-padding: 6 12; -fx-background-radius: 5;");
    }
}
