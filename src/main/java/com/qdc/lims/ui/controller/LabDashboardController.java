package com.qdc.lims.ui.controller;

import com.qdc.lims.service.BrandingService;
import com.qdc.lims.ui.DashboardNavigator;
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
import java.time.LocalDateTime;

/**
 * JavaFX controller for the lab technician dashboard window.
 */
@Component("labDashboardController")
public class LabDashboardController {

    @FXML
    private BorderPane mainContainer;

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
    private final BrandingService brandingService;

    public LabDashboardController(ApplicationContext springContext,
            DashboardNavigator navigator,
            LabOrderRepository labOrderRepository,
            BrandingService brandingService) {
        this.springContext = springContext;
        this.navigator = navigator;
        this.labOrderRepository = labOrderRepository;
        this.brandingService = brandingService;
    }

    @FXML
    private void initialize() {
        // Hide switch button - not needed in tabbed interface
        if (switchRoleButton != null) {
            switchRoleButton.setVisible(false);
        }

        // Load stats even if user isn't available yet
        loadDashboardStats();

        // Start auto-refresh for real-time count updates
        startAutoRefresh();

        if (mainContainer != null) {
            mainContainer.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    newScene.windowProperty().addListener((obs2, oldWindow, newWindow) -> {
                        if (newWindow instanceof Stage stage) {
                            stage.setOnShown(e -> {
                                brandingService.tagStage(stage, DashboardType.LAB.getWindowTitle());
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
                    LocalDateTime start = LocalDate.now().atStartOfDay();
                    LocalDateTime end = LocalDate.now().atTime(23, 59, 59);
                    long newPendingCount = labOrderRepository.countActualLabPending();
                    long newCompletedCount = labOrderRepository.countActualLabCompletedInRange(start, end);

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
            LocalDateTime start = LocalDate.now().atStartOfDay();
            LocalDateTime end = LocalDate.now().atTime(23, 59, 59);
            // Pending = anything NOT completed and NOT cancelled (same logic as Reception)
            long pendingCount = labOrderRepository.countActualLabPending();
            long completedCount = labOrderRepository.countActualLabCompletedInRange(start, end);

            pendingCountLabel.setText(String.valueOf(pendingCount));
            completedCountLabel.setText(String.valueOf(completedCount));

        } catch (Exception e) {
            System.err.println("Error loading lab stats: " + e.getMessage());
        }
    }

    /**
     * Handle Switch User button - allows quick switching to a different user.
     */
    @FXML
    private void handleSwitchUser() {
        Stage stage = resolveCurrentStage();
        if (stage != null) {
            navigator.switchUser(stage);
        }
    }

    @FXML
    private void handleLogout() {
        LogoutUtil.confirmAndCloseParentTab(mainContainer);
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
                .filter(this::hasActualLabTests)
                .count();
    }

    private Tab findCurrentSessionTab() {
        if (mainContainer == null || mainContainer.getScene() == null) {
            return null;
        }
        if (!(mainContainer.getScene().getRoot() instanceof BorderPane borderPane)) {
            return null;
        }
        if (!(borderPane.getCenter() instanceof TabPane tabPane)) {
            return null;
        }
        for (Tab tab : tabPane.getTabs()) {
            Node tabContent = tab.getContent();
            if (tabContent == mainContainer || isDescendantOf(mainContainer, tabContent)) {
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

    /**
     * Checks if the order contains at least one test that is NOT a skip-worklist
     * test.
     */
    private boolean hasActualLabTests(com.qdc.lims.entity.LabOrder order) {
        if (order == null || order.getResults() == null || order.getResults().isEmpty()) {
            return false;
        }
        return order.getResults().stream()
                .anyMatch(r -> r.getTestDefinition() != null &&
                        !Boolean.TRUE.equals(r.getTestDefinition().getSkipWorklist()));
    }

    /**
     * Checks if there is at least one test that is PENDING and NOT skipped.
     */
    private boolean hasActualWorkPending(com.qdc.lims.entity.LabOrder order) {
        if (order.getResults() == null)
            return false;
        return order.getResults().stream()
                .anyMatch(result -> "PENDING".equals(result.getStatus()) &&
                        (result.getTestDefinition() == null
                                || !Boolean.TRUE.equals(result.getTestDefinition().getSkipWorklist())));
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

    private Stage resolveCurrentStage() {
        if (mainContainer == null || mainContainer.getScene() == null || mainContainer.getScene().getWindow() == null) {
            return null;
        }
        return (Stage) mainContainer.getScene().getWindow();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
