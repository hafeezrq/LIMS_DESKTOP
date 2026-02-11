package com.qdc.lims.ui.controller;

import com.qdc.lims.service.BrandingService;
import com.qdc.lims.ui.DashboardNavigator;
import com.qdc.lims.ui.SessionManager;
import com.qdc.lims.ui.navigation.DashboardSwitchService;
import com.qdc.lims.ui.navigation.DashboardType;
import com.qdc.lims.ui.util.LogoutUtil;
import com.qdc.lims.repository.LabOrderRepository;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * JavaFX controller for the lab technician dashboard window.
 */
@Component("labDashboardController")
public class LabDashboardController {

    @FXML
    private Label userLabel;

    @FXML
    private Label welcomeLabel;

    @FXML
    private Label roleLabel;

    @FXML
    private ComboBox<String> dashboardSwitcher;

    @FXML
    private Label pendingCountLabel;

    @FXML
    private Label completedCountLabel;
    @FXML
    private Label footerBrandLabel;

    @FXML
    private Button switchRoleButton;

    // Auto-refresh timer for real-time count updates (every 10 seconds)
    private Timeline autoRefreshTimeline;

    private final ApplicationContext springContext;
    private final DashboardNavigator navigator;
    private final LabOrderRepository labOrderRepository;
    private final DashboardSwitchService dashboardSwitchService;
    private final BrandingService brandingService;

    public LabDashboardController(ApplicationContext springContext,
            DashboardNavigator navigator,
            LabOrderRepository labOrderRepository,
            DashboardSwitchService dashboardSwitchService,
            BrandingService brandingService) {
        this.springContext = springContext;
        this.navigator = navigator;
        this.labOrderRepository = labOrderRepository;
        this.dashboardSwitchService = dashboardSwitchService;
        this.brandingService = brandingService;
    }

    @FXML
    private void initialize() {
        updateUserLabels();

        // Hide switch button - not needed in tabbed interface
        if (switchRoleButton != null) {
            switchRoleButton.setVisible(false);
        }

        // Load stats even if user isn't available yet
        loadDashboardStats();

        // Start auto-refresh for real-time count updates
        startAutoRefresh();

        if (welcomeLabel != null) {
            welcomeLabel.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    newScene.windowProperty().addListener((obs2, oldWindow, newWindow) -> {
                        if (newWindow instanceof Stage stage) {
                            stage.setOnShown(e -> {
                                brandingService.tagStage(stage, DashboardType.LAB.getWindowTitle());
                                updateUserLabels();
                                loadDashboardStats();
                                startAutoRefresh();
                                applyBranding();
                            });
                            stage.setOnHidden(e -> stopAutoRefresh());
                        }
                    });
                }
            });
        }

        if (dashboardSwitcher != null) {
            dashboardSwitcher.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    newScene.windowProperty().addListener((obs2, oldWindow, newWindow) -> {
                        if (newWindow instanceof Stage stage) {
                            dashboardSwitchService.setupDashboardSwitcher(dashboardSwitcher, DashboardType.LAB, stage);
                        }
                    });
                }
            });
        }

        applyBranding();
    }

    /**
     * Starts automatic refresh of order counts every 10 seconds.
     * This ensures the "Pending" and "Completed" counts update in real-time
     * when Reception creates new orders or Lab Tech completes tests.
     */
    private void startAutoRefresh() {
        if (autoRefreshTimeline != null) {
            autoRefreshTimeline.stop();
        }
        autoRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(10), event -> {
            // Run database query in background thread to avoid UI freeze
            new Thread(() -> {
                try {
                    long newPendingCount = labOrderRepository.countPendingWithResults();
                    long newCompletedCount = countCompletedTodayWithResults();

                    // Update UI on JavaFX thread
                    Platform.runLater(() -> {
                        String currentPendingCount = pendingCountLabel.getText();
                        String currentCompletedCount = completedCountLabel.getText();

                        // Only update if counts have changed
                        if (!String.valueOf(newPendingCount).equals(currentPendingCount) ||
                                !String.valueOf(newCompletedCount).equals(currentCompletedCount)) {
                            pendingCountLabel.setText(String.valueOf(newPendingCount));
                            completedCountLabel.setText(String.valueOf(newCompletedCount));
                        }
                    });
                } catch (Exception e) {
                    System.err.println("Auto-refresh error: " + e.getMessage());
                }
            }).start();
        }));
        autoRefreshTimeline.setCycleCount(Animation.INDEFINITE);
        autoRefreshTimeline.play();
    }

    /**
     * Stops the auto-refresh timer. Should be called when navigating away from this
     * dashboard.
     */
    public void stopAutoRefresh() {
        if (autoRefreshTimeline != null) {
            autoRefreshTimeline.stop();
        }
    }

    /**
     * Load dashboard statistics (pending and completed tests).
     */
    private void loadDashboardStats() {
        try {
            // Pending = anything NOT completed and NOT cancelled (same logic as Reception)
            long pendingCount = labOrderRepository.countPendingWithResults();
            long completedCount = countCompletedTodayWithResults();

            pendingCountLabel.setText(String.valueOf(pendingCount));
            completedCountLabel.setText(String.valueOf(completedCount));

        } catch (Exception e) {
            System.err.println("Error loading lab stats: " + e.getMessage());
        }
    }

    private void updateUserLabels() {
        if (SessionManager.getCurrentUser() != null) {
            String fullName = SessionManager.getCurrentUser().getFullName();
            if (welcomeLabel != null) {
                welcomeLabel.setText(fullName);
            }
            if (userLabel != null) {
                userLabel.setText(fullName);
            }
        }

        if (roleLabel != null && SessionManager.getCurrentRole() != null) {
            roleLabel.setText("Active Role: " + SessionManager.getCurrentRole());
        }
    }

    /**
     * Handle Switch User button - allows quick switching to a different user.
     */
    @FXML
    private void handleSwitchUser() {
        navigator.switchUser((Stage) welcomeLabel.getScene().getWindow());
    }

    @FXML
    private void handleDashboardSwitch() {
        String selected = dashboardSwitcher.getValue();
        if (selected == null || selected.isEmpty() || selected.equals(DashboardType.LAB.getDisplayName())) {
            return;
        }

        stopAutoRefresh();
        Stage stage = (Stage) welcomeLabel.getScene().getWindow();
        dashboardSwitchService.switchToDashboard(selected, stage);
    }

    @FXML
    private void handleLogout() {
        LogoutUtil.confirmAndCloseParentTab(welcomeLabel);
    }

    // Menu action handlers
    @FXML
    private void handleRegisterPatient() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/patient_registration.fxml"));
            loader.setControllerFactory(springContext::getBean);
            Parent root = loader.load();

            Stage stage = createBrandedStage("Patient Registration");
            stage.setScene(new Scene(root, 550, 620));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", "Failed to open patient registration: " + e.getMessage());
        }
    }

    @FXML
    private void handleSearchPatient() {
        showAlert("Feature", "Search Patient feature will be implemented in the full version.");
    }

    @FXML
    private void handleCreateOrder() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/create_order.fxml"));
            loader.setControllerFactory(springContext::getBean);
            Parent root = loader.load();

            Stage stage = createBrandedStage("Create Lab Order");
            stage.setScene(new Scene(root, 900, 800));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", "Failed to open order creation: " + e.getMessage());
        }
    }

    @FXML
    private void handleViewOrders() {
        showAlert("Feature", "View Orders feature will be implemented in the full version.");
    }

    @FXML
    private void handleWorklist() {
        openWorklistInCurrentTab(false);
    }

    @FXML
    private void handleEnterResults() {
        openWorklistInCurrentTab(false);
    }

    @FXML
    private void handleCompletedTests() {
        openWorklistInCurrentTab(true);
    }

    private void openWorklistInCurrentTab(boolean showCompleted) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/lab_worklist.fxml"));
            loader.setControllerFactory(springContext::getBean);
            Parent worklistRoot = loader.load();

            LabWorklistController controller = loader.getController();
            Tab currentTab = findCurrentSessionTab();
            if (currentTab != null) {
                Node previousContent = currentTab.getContent();
                String originalTitle = currentTab.getText();
                Tooltip originalTooltip = currentTab.getTooltip();
                currentTab.setText(buildWorklistTabTitle(originalTitle, showCompleted));
                currentTab.setTooltip(new Tooltip(showCompleted ? "Lab Completed Tests" : "Lab Pending Worklist"));
                controller.setCloseAction(() -> {
                    currentTab.setContent(previousContent);
                    currentTab.setText(originalTitle);
                    currentTab.setTooltip(originalTooltip);
                    loadDashboardStats();
                });
                currentTab.setContent(worklistRoot);
            } else {
                Stage stage = createBrandedStage(showCompleted ? "Lab Worklist - Completed Tests" : "Lab Worklist");
                stage.setScene(new Scene(worklistRoot));
                stage.show();
            }

            if (showCompleted) {
                controller.showCompleted();
            } else {
                controller.showPending();
            }
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", "Failed to open lab worklist: " + e.getMessage());
        }
    }

    private long countCompletedTodayWithResults() {
        LocalDate today = LocalDate.now();
        return labOrderRepository.findByStatusAndOrderDateBetween(
                "COMPLETED",
                today.atStartOfDay(),
                today.atTime(23, 59, 59)).stream()
                .filter(order -> order.getResults() != null && !order.getResults().isEmpty())
                .count();
    }

    private Tab findCurrentSessionTab() {
        if (welcomeLabel == null || welcomeLabel.getScene() == null) {
            return null;
        }
        if (!(welcomeLabel.getScene().getRoot() instanceof BorderPane borderPane)) {
            return null;
        }
        if (!(borderPane.getCenter() instanceof TabPane tabPane)) {
            return null;
        }
        for (Tab tab : tabPane.getTabs()) {
            Node tabContent = tab.getContent();
            if (tabContent == welcomeLabel || isDescendantOf(welcomeLabel, tabContent)) {
                return tab;
            }
        }
        return null;
    }

    private boolean isDescendantOf(Node node, Node potentialParent) {
        if (node == null || potentialParent == null) {
            return false;
        }
        javafx.scene.Parent current = node.getParent();
        while (current != null) {
            if (current == potentialParent) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private String buildWorklistTabTitle(String originalTitle, boolean showCompleted) {
        String suffix = showCompleted ? "Completed Tests" : "Worklist";
        if (originalTitle == null || originalTitle.isBlank()) {
            return suffix;
        }
        return originalTitle + " - " + suffix;
    }

    @FXML
    private void handleViewStock() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/inventory_view.fxml"));
            loader.setControllerFactory(springContext::getBean);
            Parent root = loader.load();

            Stage stage = createBrandedStage("Inventory Management");
            stage.setScene(new Scene(root));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", "Failed to open inventory view: " + e.getMessage());
        }
    }

    @FXML
    private void handleLowStock() {
        showAlert("Feature", "Low Stock Alert feature will be implemented in the full version.");
    }

    private void applyBranding() {
        if (footerBrandLabel != null) {
            footerBrandLabel.setText(brandingService.getLabNameOrAppName() + " - Lab Interface");
        }
    }

    private Stage createBrandedStage(String context) {
        Stage stage = new Stage();
        brandingService.tagStage(stage, context);
        return stage;
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
