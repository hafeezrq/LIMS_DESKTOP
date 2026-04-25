package com.qdc.lims.ui.controller;

import com.qdc.lims.service.BrandingService;
import com.qdc.lims.service.ConfigService;
import com.qdc.lims.ui.SessionManager;
import com.qdc.lims.ui.navigation.DashboardType;
import com.qdc.lims.ui.util.LogoutUtil;
import com.qdc.lims.entity.LabOrder;
import com.qdc.lims.entity.LabResult;
import com.qdc.lims.entity.Panel;
import com.qdc.lims.entity.Patient;
import com.qdc.lims.entity.ReferenceRange;
import com.qdc.lims.entity.TestDefinition;
import com.qdc.lims.entity.User;
import com.qdc.lims.repository.LabOrderRepository;
import com.qdc.lims.repository.PanelRepository;
import com.qdc.lims.repository.ReferenceRangeRepository;
import com.qdc.lims.service.LocaleFormatService;
import com.qdc.lims.service.OrderCancellationService;
import com.qdc.lims.service.ReportPrintProgressService;
import com.qdc.lims.util.LabResultDisplayOrder;
import com.qdc.lims.util.ReportPrintState;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.springframework.context.ApplicationContext;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.Set;
import javafx.util.Duration;

/**
 * Controller for the Reception Dashboard.
 * Handles patient registration, order creation, and report delivery workflow.
 */
@Component("receptionDashboardController")
public class ReceptionDashboardController {
    private static final String REPORT_PATIENT_INFO_OFFSET_Y_MM = "REPORT_PATIENT_INFO_OFFSET_Y_MM";
    private static final double POINTS_PER_MM = 72.0 / 25.4;
    private enum ReportPaperMode {
        LETTERHEAD("Letterhead"),
        BLANK_A4("Blank A4");

        private final String label;

        ReportPaperMode(String label) {
            this.label = label;
        }

        private String label() {
            return label;
        }
    }

    private final ApplicationContext applicationContext;
    private final LabOrderRepository labOrderRepository;
    private final PanelRepository panelRepository;
    private final ReferenceRangeRepository referenceRangeRepository;
    private final BrandingService brandingService;
    private final ConfigService configService;
    private final LocaleFormatService localeFormatService;
    private final OrderCancellationService orderCancellationService;
    private final ReportPrintProgressService reportPrintProgressService;

    // Auto-refresh timer for real-time count updates
    private Timeline autoRefreshTimeline;
    private long lastReadyCount = -1;
    private long lastPendingCount = -1;
    private long lastInProgressCount = -1;

    // FXML Components
    @FXML
    private BorderPane mainContainer;
    @FXML
    private Label userLabel;
    @FXML
    private Label welcomeLabel;
    @FXML
    private Label dateTimeLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private Label footerBrandLabel;
    @FXML
    private TextField deliveredSearchField;
    @FXML
    private DatePicker deliveredFromDatePicker;
    @FXML
    private DatePicker deliveredToDatePicker;

    // FXML Components - Quick Actions Panel
    @FXML
    private Label readyCountLabel;
    @FXML
    private Label pendingCountLabel;
    @FXML
    private VBox readyPanel;
    @FXML
    private VBox pendingPanel;

    // FXML Components - Orders Table
    @FXML
    private TabPane ordersTabPane;
    @FXML
    private TextField searchField;

    // Ready Orders Table
    @FXML
    private TableView<LabOrder> readyOrdersTable;
    @FXML
    private TableColumn<LabOrder, String> readyOrderIdCol;
    @FXML
    private TableColumn<LabOrder, String> readyMrnCol;
    @FXML
    private TableColumn<LabOrder, String> readyPatientCol;
    @FXML
    private TableColumn<LabOrder, String> readyDateCol;
    @FXML
    private TableColumn<LabOrder, String> readyBalanceCol;
    @FXML
    private TableColumn<LabOrder, Void> readyActionCol;

    // Pending Orders Table
    @FXML
    private TableView<LabOrder> pendingOrdersTable;
    @FXML
    private TableColumn<LabOrder, String> pendingOrderIdCol;
    @FXML
    private TableColumn<LabOrder, String> pendingMrnCol;
    @FXML
    private TableColumn<LabOrder, String> pendingPatientCol;
    @FXML
    private TableColumn<LabOrder, String> pendingDateCol;
    @FXML
    private TableColumn<LabOrder, String> pendingStatusCol;
    @FXML
    private TableColumn<LabOrder, Void> pendingActionCol;
    @FXML
    private TableView<LabOrder> deliveredOrdersTable;
    @FXML
    private TableColumn<LabOrder, String> deliveredOrderIdCol;
    @FXML
    private TableColumn<LabOrder, String> deliveredMrnCol;
    @FXML
    private TableColumn<LabOrder, String> deliveredPatientCol;
    @FXML
    private TableColumn<LabOrder, String> deliveredOrderDateCol;
    @FXML
    private TableColumn<LabOrder, String> deliveredDeliveryDateCol;
    @FXML
    private TableColumn<LabOrder, String> deliveredReportStatusCol;
    @FXML
    private TableColumn<LabOrder, Void> deliveredActionCol;

    // Data
    private ObservableList<LabOrder> readyOrders = FXCollections.observableArrayList();
    private ObservableList<LabOrder> pendingOrders = FXCollections.observableArrayList();
    private ObservableList<LabOrder> deliveredOrders = FXCollections.observableArrayList();
    private record CancellationApproval(String key, String reason) {
    }

    public ReceptionDashboardController(ApplicationContext applicationContext,
            LabOrderRepository labOrderRepository,
            PanelRepository panelRepository,
            ReferenceRangeRepository referenceRangeRepository,
            BrandingService brandingService,
            ConfigService configService,
            LocaleFormatService localeFormatService,
            OrderCancellationService orderCancellationService,
            ReportPrintProgressService reportPrintProgressService) {
        this.applicationContext = applicationContext;
        this.labOrderRepository = labOrderRepository;
        this.panelRepository = panelRepository;
        this.referenceRangeRepository = referenceRangeRepository;
        this.brandingService = brandingService;
        this.configService = configService;
        this.localeFormatService = localeFormatService;
        this.orderCancellationService = orderCancellationService;
        this.reportPrintProgressService = reportPrintProgressService;
    }

    @FXML
    public void initialize() {
        startClock();
        setupReadyOrdersTable();
        setupPendingOrdersTable();
        setupDeliveredOrdersTable();
        ensureOrdersTabsVisible();
        localeFormatService.applyDatePickerLocale(deliveredFromDatePicker, deliveredToDatePicker);
        initializeDeliveredDateRange();
        loadOrders();
        startAutoRefresh();

        // We must wait for the Scene/Window to be ready to get the Stage
        if (mainContainer != null) {
            mainContainer.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    newScene.windowProperty().addListener((obs2, oldWindow, newWindow) -> {
                        if (newWindow instanceof Stage stage) {
                            User user = SessionManager.getUser(stage);
                            brandingService.tagStage(stage, DashboardType.RECEPTION.getWindowTitle());

                            if (user != null) {
                                String username = user.getUsername();
                                if (userLabel != null)
                                    userLabel.setText(username);
                                if (welcomeLabel != null)
                                    welcomeLabel.setText("Welcome, " + username);
                            }
                            applyBranding();
                        }
                    });
                }
            });
        }

        applyBranding();
    }

    /**
     * Starts automatic refresh of order counts every 10 seconds.
     */
    private void startAutoRefresh() {
        autoRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(3), event -> {
            new Thread(() -> {
                try {
                    LocalDateTime startDate = LocalDateTime.now().minusDays(30);
                    LocalDateTime endDate = LocalDateTime.now().plusDays(1);
                    List<LabOrder> allOrders = labOrderRepository.findByOrderDateBetween(startDate, endDate);

                    allOrders = allOrders.stream()
                            .filter(o -> o.getResults() != null && !o.getResults().isEmpty())
                            .collect(Collectors.toList());

                    long newReadyCount = allOrders.stream()
                            .filter(o -> "COMPLETED".equals(o.getStatus())
                                    && reportPrintProgressService.isReadySectionState(o))
                            .count();

                    long newPendingCount = allOrders.stream()
                            .filter(o -> !"COMPLETED".equals(o.getStatus()) && !"CANCELLED".equals(o.getStatus()))
                            .count();

                    long newInProgressCount = allOrders.stream()
                            .filter(o -> "IN_PROGRESS".equals(o.getStatus()))
                            .count();

                    Platform.runLater(() -> {
                        if (newReadyCount != lastReadyCount
                                || newPendingCount != lastPendingCount
                                || newInProgressCount != lastInProgressCount) {
                            loadOrders();
                            lastReadyCount = newReadyCount;
                            lastPendingCount = newPendingCount;
                            lastInProgressCount = newInProgressCount;
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

    public void stopAutoRefresh() {
        if (autoRefreshTimeline != null) {
            autoRefreshTimeline.stop();
        }
    }

    private void startClock() {
        Thread clockThread = new Thread(() -> {
            while (true) {
                try {
                    String time = localeFormatService.formatDateTime(LocalDateTime.now());
                    Platform.runLater(() -> {
                        if (dateTimeLabel != null) {
                            dateTimeLabel.setText(time);
                        }
                    });
                    Thread.sleep(60000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        clockThread.setDaemon(true);
        clockThread.start();
        dateTimeLabel.setText(localeFormatService.formatDateTime(LocalDateTime.now()));
    }

    private void setupReadyOrdersTable() {
        readyOrderIdCol.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getId())));
        readyMrnCol.setCellValueFactory(data -> {
            Patient p = data.getValue().getPatient();
            return new SimpleStringProperty(p != null ? p.getMrn() : "-");
        });
        readyPatientCol.setCellValueFactory(data -> {
            Patient p = data.getValue().getPatient();
            return new SimpleStringProperty(p != null ? p.getFullName() : "-");
        });
        readyDateCol.setCellValueFactory(data -> {
            LocalDateTime dt = data.getValue().getOrderDate();
            return new SimpleStringProperty(
                    dt != null ? localeFormatService.formatDateTime(dt) : "-");
        });
        readyBalanceCol.setCellValueFactory(data -> {
            java.math.BigDecimal balance = data.getValue().getBalanceDue();
            if (balance == null || balance.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                return new SimpleStringProperty("PAID");
            }
            return new SimpleStringProperty(localeFormatService.formatCurrency(balance));
        });

        readyBalanceCol.setCellFactory(col -> new TableCell<LabOrder, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.equals("PAID")) {
                        setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                    }
                }
            }
        });

        readyActionCol.setCellFactory(col -> new TableCell<LabOrder, Void>() {
            private final Button openBtn = new Button("Open");
            {
                openBtn.setStyle(
                        "-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 11; -fx-padding: 3 15;");
                openBtn.setOnAction(e -> handleManageReadyOrder(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : openBtn);
            }
        });

        readyOrdersTable.setItems(readyOrders);
    }

    // When user clicks "Open" on a ready order, show a dialog with order details
    // and actions
    private void handleManageReadyOrder(LabOrder order) {
        // Create a custom dialog
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Manage Order #" + order.getId());
        dialog.setHeaderText("Order Details: " + order.getPatient().getFullName());

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        content.setPrefWidth(450);

        // 1. Show Test List
        content.getChildren().add(new Label("Ordered Items:"));
        for (LabResult res : order.getResults()) {
            String note = Boolean.TRUE.equals(res.getTestDefinition().getSkipWorklist()) ? "[Procedural]"
                    : "[Lab Test]";
            content.getChildren().add(new Label(" • " + res.getTestDefinition().getTestName() + " " + note));
        }

        // 2. Show Payment Status
        content.getChildren().add(new Separator());
        BigDecimal balance = order.getBalanceDue() != null ? order.getBalanceDue() : BigDecimal.ZERO;
        Label billLabel = new Label("Balance Due: " + localeFormatService.formatCurrency(balance));
        if (balance.compareTo(BigDecimal.ZERO) > 0)
            billLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
        content.getChildren().add(billLabel);

        dialog.getDialogPane().setContent(content);

        // 3. Add Actions
        ButtonType deliverBtn = new ButtonType("Deliver / Print", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(deliverBtn, ButtonType.CLOSE);

        Optional<ButtonType> result = dialog.showAndWait();

        if (result.isPresent()) {
            if (result.get() == deliverBtn) {
                deliverReport(order); // Your existing delivery flow
            }
        }
    }

    private void setupPendingOrdersTable() {
        pendingOrderIdCol
                .setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getId())));
        pendingMrnCol.setCellValueFactory(data -> {
            Patient p = data.getValue().getPatient();
            return new SimpleStringProperty(p != null ? p.getMrn() : "-");
        });
        pendingPatientCol.setCellValueFactory(data -> {
            Patient p = data.getValue().getPatient();
            return new SimpleStringProperty(p != null ? p.getFullName() : "-");
        });
        pendingDateCol.setCellValueFactory(data -> {
            LocalDateTime dt = data.getValue().getOrderDate();
            return new SimpleStringProperty(
                    dt != null ? localeFormatService.formatDateTime(dt) : "-");
        });
        pendingStatusCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStatus()));

        if (pendingActionCol != null) {
            pendingActionCol.setCellFactory(col -> new TableCell<LabOrder, Void>() {
                private final Button cancelBtn = new Button("Cancel");

                {
                    cancelBtn.setStyle(
                            "-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-size: 11; -fx-padding: 3 10;");
                    cancelBtn.setOnAction(e -> {
                        LabOrder order = getTableView().getItems().get(getIndex());
                        handleCancelOrder(order);
                    });
                }

                @Override
                protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                        setGraphic(null);
                        return;
                    }

                    LabOrder order = getTableView().getItems().get(getIndex());
                    if (order == null) {
                        setGraphic(null);
                        return;
                    }

                    String blockReason = orderCancellationService.getCancellationBlockReason(order);
                    if (blockReason != null) {
                        cancelBtn.setDisable(true);
                        cancelBtn.setStyle(
                                "-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-size: 11; -fx-padding: 3 10;");
                        cancelBtn.setTooltip(new Tooltip(blockReason));
                        setGraphic(cancelBtn);
                        return;
                    }

                    cancelBtn.setDisable(false);
                    cancelBtn.setStyle(
                            "-fx-background-color: #d35400; -fx-text-fill: white; -fx-font-size: 11; -fx-padding: 3 10;");
                    cancelBtn.setTooltip(new Tooltip("Admin cancellation key is required to cancel this order."));
                    setGraphic(cancelBtn);
                }
            });
        }

        pendingOrdersTable.setItems(pendingOrders);
    }

    private void setupDeliveredOrdersTable() {
        deliveredOrderIdCol
                .setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getId())));
        deliveredMrnCol.setCellValueFactory(data -> {
            Patient p = data.getValue().getPatient();
            return new SimpleStringProperty(p != null ? p.getMrn() : "-");
        });
        deliveredPatientCol.setCellValueFactory(data -> {
            Patient p = data.getValue().getPatient();
            return new SimpleStringProperty(p != null ? p.getFullName() : "-");
        });
        deliveredOrderDateCol.setCellValueFactory(data -> {
            LocalDateTime dt = data.getValue().getOrderDate();
            return new SimpleStringProperty(
                    dt != null ? localeFormatService.formatDateTime(dt) : "-");
        });
        deliveredDeliveryDateCol.setCellValueFactory(data -> {
            LocalDateTime dt = getDeliveredActivityDate(data.getValue());
            return new SimpleStringProperty(
                    dt != null ? localeFormatService.formatDateTime(dt) : "-");
        });
        if (deliveredReportStatusCol != null) {
            deliveredReportStatusCol.setCellValueFactory(
                    data -> new SimpleStringProperty(reportPrintProgressService.buildSubStatusLabel(data.getValue())));
        }

        deliveredActionCol.setCellFactory(col -> new TableCell<LabOrder, Void>() {
            private final Button reprintBtn = new Button("Reprint");
            {
                reprintBtn.setStyle(
                        "-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 11; -fx-padding: 3 10;");
                reprintBtn.setOnAction(e -> {
                    LabOrder order = getTableView().getItems().get(getIndex());
                    handleReprintReport(order);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    return;
                }
                LabOrder order = getTableView().getItems().get(getIndex());
                if (order == null) {
                    setGraphic(null);
                    return;
                }

                String reportState = reportPrintProgressService.normalizeState(order);
                boolean partialOrPending = ReportPrintState.PARTIALLY_PRINTED.equals(reportState)
                        || ReportPrintState.FULLY_PRINTED.equals(reportState);

                if (order.isReprintRequired()) {
                    reprintBtn.setText(partialOrPending ? "Continue Print" : "Reprint");
                    reprintBtn.setTooltip(new Tooltip("Reprint required due to edited delivered report."));
                    reprintBtn.setStyle(
                            "-fx-background-color: #e67e22; -fx-text-fill: white; -fx-font-size: 11; -fx-padding: 3 10;");
                } else if (partialOrPending) {
                    reprintBtn.setText("Continue Print");
                    reprintBtn.setTooltip(new Tooltip("Open print workflow to complete remaining departments."));
                    reprintBtn.setStyle(
                            "-fx-background-color: #8e44ad; -fx-text-fill: white; -fx-font-size: 11; -fx-padding: 3 10;");
                } else {
                    reprintBtn.setText("Reprint");
                    reprintBtn.setTooltip(new Tooltip("Reprint report"));
                    reprintBtn.setStyle(
                            "-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 11; -fx-padding: 3 10;");
                }
                setGraphic(reprintBtn);
            }
        });

        deliveredOrdersTable.setItems(deliveredOrders);
    }

    private void loadOrders() {
        try {
            ensureOrdersTabsVisible();
            LocalDateTime startDate = LocalDateTime.now().minusDays(30);
            LocalDateTime endDate = LocalDateTime.now().plusDays(1);
            List<LabOrder> allOrders = labOrderRepository.findByOrderDateBetween(startDate, endDate);
            LocalDateTime deliveredStart = getDeliveredRangeStart();
            LocalDateTime deliveredEnd = getDeliveredRangeEnd();
            List<LabOrder> delivered = loadDeliveredOrdersWithinRange(deliveredStart, deliveredEnd);

            // Align with lab worklist: ignore orders that have no tests/results attached.
            allOrders = allOrders.stream()
                    .filter(o -> o.getResults() != null && !o.getResults().isEmpty())
                    .collect(Collectors.toList());

            List<LabOrder> ready = allOrders.stream()
                    .filter(o -> "COMPLETED".equals(o.getStatus()))
                    .filter(reportPrintProgressService::isReadySectionState)
                    .collect(Collectors.toList());

            List<LabOrder> pending = allOrders.stream()
                    .filter(o -> !"COMPLETED".equals(o.getStatus()) && !"CANCELLED".equals(o.getStatus()))
                    .collect(Collectors.toList());

            readyOrders.setAll(ready);
            pendingOrders.setAll(pending);
            deliveredOrders.setAll(delivered);

            readyCountLabel.setText(String.valueOf(ready.size()));
            pendingCountLabel.setText(String.valueOf(pending.size()));
            lastReadyCount = ready.size();
            lastPendingCount = pending.size();
            lastInProgressCount = pending.stream().filter(o -> "IN_PROGRESS".equals(o.getStatus())).count();
            statusLabel
                    .setText("Last refreshed: " + localeFormatService.formatTime(LocalDateTime.now().toLocalTime()));

        } catch (Exception e) {
            showError("Failed to load orders: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void ensureOrdersTabsVisible() {
        if (ordersTabPane == null) {
            return;
        }
        ordersTabPane.setVisible(true);
        ordersTabPane.setManaged(true);
        if (ordersTabPane.getSelectionModel().getSelectedItem() == null && !ordersTabPane.getTabs().isEmpty()) {
            ordersTabPane.getSelectionModel().select(0);
        }
    }

    private List<LabOrder> loadDeliveredOrdersWithinRange(LocalDateTime start, LocalDateTime end) {
        Map<Long, LabOrder> merged = new java.util.LinkedHashMap<>();

        List<LabOrder> byProgressDate = labOrderRepository.findByReportProgressUpdatedAtBetween(start, end);
        for (LabOrder order : byProgressDate) {
            if (reportPrintProgressService.isDeliveredSectionState(order)) {
                merged.put(order.getId(), order);
            }
        }

        List<LabOrder> legacyDelivered = labOrderRepository.findByIsReportDeliveredTrueAndDeliveryDateBetween(start,
                end);
        for (LabOrder order : legacyDelivered) {
            merged.put(order.getId(), order);
        }

        List<LabOrder> orders = new ArrayList<>(merged.values());
        orders.removeIf(order -> order.getResults() == null || order.getResults().isEmpty());
        orders.sort((a, b) -> {
            LocalDateTime aTime = getDeliveredActivityDate(a);
            LocalDateTime bTime = getDeliveredActivityDate(b);
            if (aTime == null && bTime == null) {
                return Long.compare(b.getId(), a.getId());
            }
            if (aTime == null) {
                return 1;
            }
            if (bTime == null) {
                return -1;
            }
            return bTime.compareTo(aTime);
        });
        return orders;
    }

    private LocalDateTime getDeliveredActivityDate(LabOrder order) {
        if (order == null) {
            return null;
        }
        if (order.getDeliveryDate() != null) {
            return order.getDeliveryDate();
        }
        if (order.getReportProgressUpdatedAt() != null) {
            return order.getReportProgressUpdatedAt();
        }
        return order.getOrderDate();
    }

    private void handleCancelOrder(LabOrder order) {
        if (order == null) {
            return;
        }

        String blockReason = orderCancellationService.getCancellationBlockReason(order.getId());
        if (blockReason != null) {
            showError(blockReason);
            loadOrders();
            return;
        }

        BigDecimal refundAmount = order.getPaidAmount() != null ? order.getPaidAmount() : BigDecimal.ZERO;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Cancel Order");
        confirm.setHeaderText("Cancel Order #" + order.getId() + "?");
        confirm.setContentText(buildCancellationConfirmationText(order.getId(), refundAmount));

        Optional<ButtonType> response = confirm.showAndWait();
        if (response.isEmpty() || response.get() != ButtonType.OK) {
            return;
        }

        try {
            CancellationApproval approval = promptCancellationApproval();
            if (approval == null) {
                return;
            }

            OrderCancellationService.CancellationResult result = orderCancellationService
                    .cancelOrderAuthorized(order.getId(), approval.key(), approval.reason());
            String message = "Order #" + result.orderId() + " cancelled successfully.";
            if (result.refundAmount() != null && result.refundAmount().compareTo(BigDecimal.ZERO) > 0) {
                message += "\nRefund recorded: " + localeFormatService.formatCurrency(result.refundAmount());
            } else {
                message += "\nNo refund was required.";
            }
            message += "\nApproved using admin cancellation key.";
            showAlert("Order Cancelled", message);
            loadOrders();
        } catch (SecurityException ex) {
            showError(ex.getMessage());
            loadOrders();
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
            loadOrders();
        } catch (IllegalStateException ex) {
            showError(ex.getMessage());
            loadOrders();
        } catch (Exception ex) {
            showError("Failed to cancel order: " + ex.getMessage());
        }
    }

    private CancellationApproval promptCancellationApproval() {
        if (!orderCancellationService.isCancellationKeyConfigured()) {
            showError("Cancellation key is not configured. Ask admin to set it from Admin menu.");
            return null;
        }

        Dialog<CancellationApproval> dialog = new Dialog<>();
        dialog.setTitle("Cancellation Key Required");
        dialog.setHeaderText("Enter admin cancellation key and cancellation reason.");

        ButtonType approveButtonType = new ButtonType("Approve", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(approveButtonType, ButtonType.CANCEL);

        PasswordField keyField = new PasswordField();
        keyField.setPromptText("Cancellation key");
        keyField.setPrefWidth(260);

        TextArea reasonArea = new TextArea();
        reasonArea.setPromptText("Reason for cancellation (required)");
        reasonArea.setPrefRowCount(3);
        reasonArea.setWrapText(true);

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #c0392b;");

        VBox content = new VBox(10,
                new Label("This operation requires admin cancellation key."),
                keyField,
                new Label("Cancellation reason"),
                reasonArea,
                errorLabel);
        content.setPadding(new Insets(15));
        dialog.getDialogPane().setContent(content);

        Button approveButton = (Button) dialog.getDialogPane().lookupButton(approveButtonType);
        approveButton.setDisable(true);
        approveButton.setStyle("-fx-background-color: #d35400; -fx-text-fill: white;");
        Runnable updateApproveState = () -> approveButton
                .setDisable(keyField.getText().trim().isEmpty() || reasonArea.getText().trim().isEmpty());
        keyField.textProperty().addListener((obs, oldVal, newVal) -> updateApproveState.run());
        reasonArea.textProperty().addListener((obs, oldVal, newVal) -> updateApproveState.run());
        updateApproveState.run();

        keyField.setOnAction(e -> {
            if (!approveButton.isDisabled()) {
                approveButton.fire();
            }
        });

        approveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String key = keyField.getText();
            if (!orderCancellationService.isCancellationKeyConfigured()) {
                errorLabel.setText("Cancellation key is not configured.");
                event.consume();
                return;
            }
            if (key == null || key.trim().isEmpty()) {
                errorLabel.setText("Cancellation key is required.");
                event.consume();
                return;
            }
            if (!orderCancellationService.verifyCancellationKey(key.trim())) {
                errorLabel.setText("Invalid cancellation key.");
                event.consume();
                return;
            }
            String reason = reasonArea.getText() != null ? reasonArea.getText().trim() : "";
            if (reason.isEmpty()) {
                errorLabel.setText("Cancellation reason is required.");
                event.consume();
                return;
            }
            dialog.setResult(new CancellationApproval(key.trim(), reason));
        });

        dialog.setResultConverter(button -> button == approveButtonType ? dialog.getResult() : null);
        Platform.runLater(keyField::requestFocus);
        return dialog.showAndWait().orElse(null);
    }

    private String buildCancellationConfirmationText(Long orderId, BigDecimal refundAmount) {
        StringBuilder text = new StringBuilder();
        text.append("This will cancel order #").append(orderId).append(" (no hard delete).\n");
        text.append("Order history and cancellation audit will be preserved.");
        if (refundAmount != null && refundAmount.compareTo(BigDecimal.ZERO) > 0) {
            text.append("\n\nA refund expense will be recorded: ")
                    .append(localeFormatService.formatCurrency(refundAmount));
        }
        return text.toString();
    }

    @FXML
    private void handleRegisterPatient() {
        openWindow("/fxml/patient_registration.fxml", "Patient Registration", 550, 620);
    }

    @FXML
    private void handleCreateOrder() {
        openWindow("/fxml/create_order.fxml", "Create Lab Order", 900, 800);
    }

    @FXML
    private void handleSearchOrder() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Search Order");
        dialog.setHeaderText("Search by MRN");
        dialog.setContentText("Enter MRN:");

        dialog.showAndWait().ifPresent(mrn -> {
            if (!mrn.trim().isEmpty()) {
                searchField.setText(mrn.trim());
                handleSearchInTable();
            }
        });
    }

    @FXML
    private void handleShowReadyOrders() {
        ordersTabPane.setVisible(true);
        ordersTabPane.setManaged(true);
        ordersTabPane.getSelectionModel().select(0);
    }

    @FXML
    private void handleShowPendingOrders() {
        ordersTabPane.setVisible(true);
        ordersTabPane.setManaged(true);
        ordersTabPane.getSelectionModel().select(1);
    }

    @FXML
    private void handleShowDeliveredOrders() {
        ordersTabPane.setVisible(true);
        ordersTabPane.setManaged(true);
        ordersTabPane.getSelectionModel().select(2);
    }

    @FXML
    private void handleSearchInTable() {
        String searchTerm = searchField.getText().trim().toLowerCase();
        if (searchTerm.isEmpty()) {
            loadOrders();
            readyOrdersTable.setItems(readyOrders);
            pendingOrdersTable.setItems(pendingOrders);
            return;
        }

        LocalDateTime startDate = LocalDateTime.now().minusDays(30);
        LocalDateTime endDate = LocalDateTime.now().plusDays(1);
        List<LabOrder> allOrders = labOrderRepository.findByOrderDateBetween(startDate, endDate);

        List<LabOrder> filteredReady = allOrders.stream()
                .filter(o -> "COMPLETED".equals(o.getStatus()))
                .filter(reportPrintProgressService::isReadySectionState)
                .filter(o -> matchesSearch(o, searchTerm))
                .collect(Collectors.toList());

        List<LabOrder> filteredPending = allOrders.stream()
                .filter(o -> !"COMPLETED".equals(o.getStatus()) && !"CANCELLED".equals(o.getStatus()))
                .filter(o -> matchesSearch(o, searchTerm))
                .collect(Collectors.toList());

        readyOrdersTable.setItems(FXCollections.observableArrayList(filteredReady));
        pendingOrdersTable.setItems(FXCollections.observableArrayList(filteredPending));
    }

    @FXML
    private void handleSearchDelivered() {
        if (deliveredSearchField == null) {
            return;
        }
        String searchTerm = deliveredSearchField.getText().trim().toLowerCase();

        LocalDateTime startDate = getDeliveredRangeStart();
        LocalDateTime endDate = getDeliveredRangeEnd();
        List<LabOrder> deliveredOrdersAll = loadDeliveredOrdersWithinRange(startDate, endDate);
        if (searchTerm.isEmpty()) {
            deliveredOrdersTable.setItems(FXCollections.observableArrayList(deliveredOrdersAll));
            return;
        }

        List<LabOrder> filteredDelivered = deliveredOrdersAll.stream()
                .filter(o -> matchesSearch(o, searchTerm))
                .collect(Collectors.toList());
        deliveredOrdersTable.setItems(FXCollections.observableArrayList(filteredDelivered));
    }

    private boolean matchesSearch(LabOrder order, String searchTerm) {
        if (order.getPatient() != null) {
            if (order.getPatient().getMrn() != null && order.getPatient().getMrn().toLowerCase().contains(searchTerm))
                return true;
            if (order.getPatient().getFullName() != null
                    && order.getPatient().getFullName().toLowerCase().contains(searchTerm))
                return true;
        }
        return String.valueOf(order.getId()).contains(searchTerm);
    }

    @FXML
    private void handleRefresh() {
        searchField.clear();
        if (deliveredSearchField != null) {
            deliveredSearchField.clear();
        }
        resetDeliveredDateRange();
        loadOrders();
        readyOrdersTable.setItems(readyOrders);
        pendingOrdersTable.setItems(pendingOrders);
        deliveredOrdersTable.setItems(deliveredOrders);
    }

    private void initializeDeliveredDateRange() {
        LocalDate today = LocalDate.now();
        if (deliveredFromDatePicker != null) {
            deliveredFromDatePicker.setValue(today);
        }
        if (deliveredToDatePicker != null) {
            deliveredToDatePicker.setValue(today);
        }
    }

    private void resetDeliveredDateRange() {
        LocalDate today = LocalDate.now();
        if (deliveredFromDatePicker != null) {
            deliveredFromDatePicker.setValue(today);
        }
        if (deliveredToDatePicker != null) {
            deliveredToDatePicker.setValue(today);
        }
    }

    private LocalDateTime getDeliveredRangeStart() {
        LocalDate fromDate = deliveredFromDatePicker != null && deliveredFromDatePicker.getValue() != null
                ? deliveredFromDatePicker.getValue()
                : LocalDate.now();
        LocalDate toDate = deliveredToDatePicker != null && deliveredToDatePicker.getValue() != null
                ? deliveredToDatePicker.getValue()
                : fromDate;
        if (toDate.isBefore(fromDate)) {
            LocalDate temp = fromDate;
            fromDate = toDate;
            toDate = temp;
        }
        return fromDate.atStartOfDay();
    }

    private LocalDateTime getDeliveredRangeEnd() {
        LocalDate fromDate = deliveredFromDatePicker != null && deliveredFromDatePicker.getValue() != null
                ? deliveredFromDatePicker.getValue()
                : LocalDate.now();
        LocalDate toDate = deliveredToDatePicker != null && deliveredToDatePicker.getValue() != null
                ? deliveredToDatePicker.getValue()
                : fromDate;
        if (toDate.isBefore(fromDate)) {
            LocalDate temp = fromDate;
            fromDate = toDate;
            toDate = temp;
        }
        return toDate.atTime(23, 59, 59, 999_000_000);
    }

    @FXML
    private void handleDeliverReport() {
        LabOrder selectedOrder = readyOrdersTable.getSelectionModel().getSelectedItem();
        if (selectedOrder == null) {
            showAlert("Selection Required", "Please select an order from the Ready for Pickup table.");
            return;
        }
        deliverReport(selectedOrder);
    }

    private void deliverReport(LabOrder order) {
        java.math.BigDecimal balance = order.getBalanceDue();
        if (balance != null && balance.compareTo(java.math.BigDecimal.ZERO) > 0) {
            boolean paid = showPaymentDialog(order);
            if (!paid)
                return;
            order = labOrderRepository.findById(order.getId()).orElse(order);
        }
        showReportDeliveryDialog(order, false);
    }

    private boolean showPaymentDialog(LabOrder order) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Payment Required");
        dialog.setHeaderText("Outstanding Balance: " + localeFormatService.formatCurrency(order.getBalanceDue()));

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.getChildren().add(new Label("Patient: " + order.getPatient().getFullName()));
        content.getChildren().add(new Label("Order #: " + order.getId()));
        content.getChildren().add(new Separator());

        Label amountLabel = new Label(
                "Amount to Collect: " + localeFormatService.formatCurrency(order.getBalanceDue()));
        amountLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #e74c3c;");
        content.getChildren().add(amountLabel);

        TextField paymentField = new TextField(localeFormatService.formatNumber(order.getBalanceDue()));
        HBox paymentRow = new HBox(10);
        paymentRow.setAlignment(Pos.CENTER_LEFT);
        paymentRow.getChildren().addAll(new Label("Payment Received:"), paymentField);
        content.getChildren().add(paymentRow);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                java.math.BigDecimal payment = localeFormatService.parseNumber(paymentField.getText());
                if (payment.compareTo(java.math.BigDecimal.ZERO) > 0) {
                    java.math.BigDecimal currentPaid = order.getPaidAmount() != null ? order.getPaidAmount()
                            : java.math.BigDecimal.ZERO;
                    order.setPaidAmount(currentPaid.add(payment));
                    order.calculateBalance();
                    try {
                        labOrderRepository.save(order);
                        showAlert("Payment Recorded",
                                "Payment of " + localeFormatService.formatCurrency(payment) + " has been recorded.");
                        return true;
                    } catch (ObjectOptimisticLockingFailureException e) {
                        showError("This order was updated by another user. Please refresh and try again.");
                    }
                }
            } catch (NumberFormatException e) {
                showError("Invalid payment amount");
            }
        }
        return false;
    }

    private void showReportDeliveryDialog(LabOrder order, boolean reprintMode) {
        LabOrder freshOrder = labOrderRepository.findById(order.getId()).orElse(order);
        ReportPrintProgressService.ReportProgressSnapshot snapshot = reportPrintProgressService
                .synchronizeAndGetSnapshot(freshOrder);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(reprintMode ? "Reprint Report" : "Report Delivery");
        dialog.setHeaderText(
                (reprintMode ? "Reprint Report for Order #" : "Deliver Report for Order #") + order.getId());
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setResizable(true);

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setPrefWidth(480);

        Patient patient = freshOrder.getPatient();
        String patientName = patient != null && patient.getFullName() != null ? patient.getFullName() : "-";
        String patientMrn = patient != null && patient.getMrn() != null ? patient.getMrn() : "-";
        content.getChildren().add(new Label("Patient: " + patientName));
        content.getChildren().add(new Label("MRN: " + patientMrn));
        content.getChildren().add(new Separator());

        Label paperModeLabel = new Label("Print Format:");
        paperModeLabel.setStyle("-fx-font-weight: bold;");
        ToggleGroup paperModeGroup = new ToggleGroup();
        RadioButton letterheadModeRadio = new RadioButton("Use pre-printed letterhead");
        letterheadModeRadio.setToggleGroup(paperModeGroup);
        letterheadModeRadio.setUserData(ReportPaperMode.LETTERHEAD);
        letterheadModeRadio.setSelected(true);
        RadioButton blankA4ModeRadio = new RadioButton("Print on blank A4 sheet");
        blankA4ModeRadio.setToggleGroup(paperModeGroup);
        blankA4ModeRadio.setUserData(ReportPaperMode.BLANK_A4);
        content.getChildren().addAll(paperModeLabel, letterheadModeRadio, blankA4ModeRadio, new Separator());

        String paymentStatus = (freshOrder.getBalanceDue() == null
                || freshOrder.getBalanceDue().compareTo(java.math.BigDecimal.ZERO) <= 0)
                        ? "FULLY PAID"
                        : "Balance Due: " + localeFormatService.formatCurrency(freshOrder.getBalanceDue());
        Label paymentLabel = new Label("Payment Status: " + paymentStatus);
        paymentLabel.setStyle((freshOrder.getBalanceDue() == null
                || freshOrder.getBalanceDue().compareTo(java.math.BigDecimal.ZERO) <= 0)
                        ? "-fx-text-fill: #27ae60; -fx-font-weight: bold;"
                        : "-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
        content.getChildren().add(paymentLabel);

        List<String> availableDepartments = snapshot.departments();
        List<String> defaultSelection = reportPrintProgressService.defaultSelectionForPrint(snapshot, freshOrder);
        Map<String, CheckBox> departmentCheckboxes = new java.util.LinkedHashMap<>();
        Label printedStatusLabel = null;

        if (!availableDepartments.isEmpty()) {
            content.getChildren().add(new Separator());
            Label sectionLabel = new Label("Departments to Print:");
            sectionLabel.setStyle("-fx-font-weight: bold;");
            content.getChildren().add(sectionLabel);

            for (String department : availableDepartments) {
                CheckBox checkBox = new CheckBox(department);
                checkBox.setSelected(defaultSelection.contains(department));
                departmentCheckboxes.put(department, checkBox);
                content.getChildren().add(checkBox);
            }

            printedStatusLabel = new Label();
            printedStatusLabel.setWrapText(true);
            updatePrintedStatusLabel(printedStatusLabel, snapshot);
            content.getChildren().add(printedStatusLabel);
        }

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(400);
        scrollPane.setMaxHeight(600);
        scrollPane.setPadding(new Insets(5));
        dialog.getDialogPane().setContent(scrollPane);

        ButtonType previewBtn = new ButtonType("Preview", ButtonBar.ButtonData.OTHER);
        ButtonType printBtn = new ButtonType("Print", ButtonBar.ButtonData.OK_DONE);
        ButtonType markDeliveredBtn = new ButtonType("Mark Delivered Only", ButtonBar.ButtonData.APPLY);
        dialog.getDialogPane().getButtonTypes().addAll(previewBtn, printBtn, markDeliveredBtn, ButtonType.CANCEL);

        Button previewButton = (Button) dialog.getDialogPane().lookupButton(previewBtn);
        Button printButton = (Button) dialog.getDialogPane().lookupButton(printBtn);

        Runnable refreshActionButtons = () -> {
            boolean hasSelection = !getSelectedDepartments(departmentCheckboxes).isEmpty();
            previewButton.setDisable(!hasSelection);
            printButton.setDisable(!hasSelection);
        };

        departmentCheckboxes.values()
                .forEach(checkBox -> checkBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
                    refreshActionButtons.run();
                }));
        refreshActionButtons.run();
        Window dialogOwner = mainContainer != null && mainContainer.getScene() != null
                ? mainContainer.getScene().getWindow()
                : null;

        while (true) {
            Optional<ButtonType> result = dialog.showAndWait();
            if (result.isEmpty() || result.get() == ButtonType.CANCEL) {
                break;
            }

            if (result.get() == previewBtn) {
                List<String> selected = getSelectedDepartments(departmentCheckboxes);
                if (selected.isEmpty()) {
                    showError("Select at least one department to preview.");
                    continue;
                }
                ReportPaperMode paperMode = resolveSelectedPaperMode(paperModeGroup);
                showReportPreview(freshOrder, selected, dialogOwner, paperMode);
                continue;
            }

            if (result.get() == printBtn) {
                List<String> selected = getSelectedDepartments(departmentCheckboxes);
                if (selected.isEmpty()) {
                    showError("Select at least one department to print.");
                    continue;
                }
                ReportPaperMode paperMode = resolveSelectedPaperMode(paperModeGroup);
                if (!printReport(freshOrder, selected, paperMode)) {
                    continue;
                }

                snapshot = reportPrintProgressService.markDepartmentsPrinted(freshOrder, selected,
                        getCurrentUsername());
                freshOrder = labOrderRepository.findById(freshOrder.getId()).orElse(freshOrder);
                if (printedStatusLabel != null) {
                    updatePrintedStatusLabel(printedStatusLabel, snapshot);
                }
                if (reprintMode) {
                    markReprintCompleted(freshOrder);
                }
                boolean allPrinted = snapshot.allPrinted();
                if (!allPrinted) {
                    showAlert("Partial Print",
                            "Only selected departments were printed. Order is now visible in Delivered with partial status.");
                    loadOrders();
                    continue;
                }

                markAsDelivered(freshOrder);
                break;
            }

            if (result.get() == markDeliveredBtn) {
                markAsDelivered(freshOrder);
                break;
            }
        }
    }

    private void markAsDelivered(LabOrder order) {
        try {
            reportPrintProgressService.markDelivered(order, getCurrentUsername());
            showAlert("Report Delivered", "Report for Order #" + order.getId() + " has been marked as delivered.");
            loadOrders();
        } catch (ObjectOptimisticLockingFailureException e) {
            showError("This order was updated by another user. Please refresh and try again.");
        } catch (Exception e) {
            showError("Failed to mark as delivered: " + e.getMessage());
        }
    }

    private void handleReprintReport(LabOrder order) {
        showReportDeliveryDialog(order, true);
    }

    private void markReprintCompleted(LabOrder order) {
        try {
            order.setReprintRequired(false);
            int count = order.getReprintCount() != null ? order.getReprintCount() : 0;
            order.setReprintCount(count + 1);
            order.setLastReprintAt(LocalDateTime.now());
            order.setLastReprintBy(getCurrentUsername());
            labOrderRepository.save(order);
            loadOrders();
        } catch (ObjectOptimisticLockingFailureException e) {
            showError("This order was updated by another user. Please refresh and try again.");
        } catch (Exception e) {
            showError("Failed to record reprint: " + e.getMessage());
        }
    }

    private boolean printReport(LabOrder order, List<String> selectedDepartments, ReportPaperMode paperMode) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job != null && job.showPrintDialog(mainContainer.getScene().getWindow())) {
            PageLayout pageLayout = job.getJobSettings().getPageLayout();
            List<StackPane> pages = buildReportPages(order, pageLayout, selectedDepartments, paperMode);
            if (pages.isEmpty()) {
                showError("No printable results found for selected department(s).");
                return false;
            }
            boolean success = true;
            for (StackPane page : pages) {
                page.applyCss();
                page.layout();
                if (!job.printPage(pageLayout, page)) {
                    success = false;
                    break;
                }
            }
            if (success) {
                job.endJob();
            } else {
                showError("Failed to print report");
            }
            return success;
        }
        return false;
    }

    private List<StackPane> buildReportPages(LabOrder order, PageLayout pageLayout, List<String> selectedDepartments,
            ReportPaperMode paperMode) {
        double printableWidth = pageLayout.getPrintableWidth();
        double printableHeight = pageLayout.getPrintableHeight();
        boolean letterheadMode = paperMode == ReportPaperMode.LETTERHEAD;
        // Letterhead mode reserves space for pre-printed branding.
        double topInset = letterheadMode ? (5.5 / 2.54) * 72.0 : 0.75 * 72.0;
        double safeTopInset = letterheadMode ? Math.min(topInset, printableHeight * 0.35) : topInset;
        double bottomInset = letterheadMode ? 1.5 * 72.0 : 0.75 * 72.0;
        double contentWidth = Math.min(620, printableWidth * 0.9);
        double availableHeight = printableHeight - safeTopInset - bottomInset;
        // double headerInset = 36.0;

        Patient patient = order.getPatient();
        String patientName = patient != null && patient.getFullName() != null ? patient.getFullName() : "-";
        String patientMrn = patient != null && patient.getMrn() != null ? patient.getMrn() : "-";
        String patientAge = patient != null ? localeFormatService.formatAge(patient.getAge(), patient.getAgeUnit()) : "-";
        String patientGender = patient != null && patient.getGender() != null ? patient.getGender() : "-";

        GridPane patientInfo = new GridPane();
        patientInfo.setHgap(12);
        patientInfo.setVgap(1);
        patientInfo.setStyle("-fx-border-color: #444444; -fx-border-width: 0.5; -fx-border-insets: 0;");
        patientInfo.setMinHeight(Region.USE_PREF_SIZE);
        patientInfo.setPrefHeight(Region.USE_COMPUTED_SIZE);
        patientInfo.setMaxHeight(Region.USE_PREF_SIZE);

        int row = 0;
        patientInfo.add(createReportLabel("Patient:"), 0, row);
        patientInfo.add(createReportLabel(patientName), 1, row++);
        patientInfo.add(createReportLabel("MRN:"), 0, row);
        patientInfo.add(createReportLabel(patientMrn), 1, row++);
        patientInfo.add(createReportLabel("Age:"), 0, row);
        patientInfo.add(createReportLabel(patientAge), 1, row++);
        patientInfo.add(createReportLabel("Gender:"), 0, row);
        patientInfo.add(createReportLabel(patientGender), 1, row++);
        patientInfo.add(createReportLabel("Order Date:"), 0, row);
        patientInfo.add(createReportLabel(localeFormatService.formatDateTime(order.getOrderDate())), 1, row++);
        patientInfo.add(createReportLabel("Printed:"), 0, row);
        patientInfo.add(createReportLabel(localeFormatService.formatDateTime(LocalDateTime.now())), 1, row++);
        String referredBy = order.getReferringDoctor() != null && order.getReferringDoctor().getName() != null
                ? order.getReferringDoctor().getName().trim()
                : "";
        if (!referredBy.isBlank()) {
            patientInfo.add(createReportLabel("Referred By:"), 0, row);
            patientInfo.add(createReportLabel(referredBy), 1, row++);
        }

        List<StackPane> pages = new ArrayList<>();
        PageContext pageContext = newPage(pages, printableWidth, printableHeight, safeTopInset, bottomInset,
                contentWidth, availableHeight);
        if (letterheadMode) {
            attachPatientHeader(pages.get(pages.size() - 1), patientInfo, printableWidth, pageLayout);
            addNodeToPage(pageContext, createSpacer(6), contentWidth);
        } else {
            VBox blankHeader = createBlankPaperHeader(order, patientInfo, contentWidth);
            addNodeToPage(pageContext, blankHeader, contentWidth);
            addNodeToPage(pageContext, createSpacer(6), contentWidth);
        }

        Map<String, List<LabResult>> byDepartment = buildResultsByDepartment(order, selectedDepartments);
        for (Map.Entry<String, List<LabResult>> entry : byDepartment.entrySet()) {
            pageContext = addDepartmentToPages(entry.getKey(), entry.getValue(), pageContext, pages,
                    printableWidth, printableHeight, safeTopInset, bottomInset, contentWidth, availableHeight);
        }

        return pages;
    }

    private Map<String, List<LabResult>> buildResultsByDepartment(LabOrder order, List<String> selectedDepartments) {
        Set<String> departmentFilter = selectedDepartments == null
                ? java.util.Collections.emptySet()
                : selectedDepartments.stream().collect(Collectors.toCollection(LinkedHashSet::new));

        List<LabResult> results = order.getResults() != null ? order.getResults() : List.of();

        return results.stream()
                .filter(result -> {
                    // --- NEW: EXCLUDE PROCEDURAL/OUTSOURCED TESTS ---
                    // If the test is marked to skip the worklist, we don't print it on the LIMS
                    // report
                    if (result.getTestDefinition() != null &&
                            Boolean.TRUE.equals(result.getTestDefinition().getSkipWorklist())) {
                        return false;
                    }

                    // --- EXISTING DEPARTMENT FILTERING ---
                    if (departmentFilter.isEmpty()) {
                        return true;
                    }
                    String departmentName = resolveDepartmentName(result);
                    return departmentFilter.contains(departmentName);
                })
                .collect(Collectors.groupingBy(result -> {
                    return resolveDepartmentName(result);
                }, java.util.TreeMap::new, Collectors.toList()));
    }

    private String resolveDepartmentName(LabResult result) {
        if (result != null
                && result.getTestDefinition() != null
                && result.getTestDefinition().getDepartment() != null
                && result.getTestDefinition().getDepartment().getName() != null
                && !result.getTestDefinition().getDepartment().getName().isBlank()) {
            return result.getTestDefinition().getDepartment().getName();
        }
        return "Other";
    }

    private List<String> getSelectedDepartments(Map<String, CheckBox> departmentCheckboxes) {
        if (departmentCheckboxes == null || departmentCheckboxes.isEmpty()) {
            return List.of();
        }
        return departmentCheckboxes.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue().isSelected())
                .map(Map.Entry::getKey)
                .toList();
    }

    private void updatePrintedStatusLabel(Label statusLabel,
            ReportPrintProgressService.ReportProgressSnapshot snapshot) {
        if (statusLabel == null) {
            return;
        }
        if (snapshot == null || snapshot.departments() == null || snapshot.departments().isEmpty()) {
            statusLabel.setText("Printed departments: 0/0 | Remaining: 0 | State: NOT_PRINTED");
            return;
        }
        String printedSummary = snapshot.printedCount() + "/" + snapshot.totalCount();
        String remaining = String.valueOf(Math.max(snapshot.totalCount() - snapshot.printedCount(), 0));
        String state = snapshot.state() != null ? snapshot.state() : ReportPrintState.NOT_PRINTED;
        statusLabel.setText("Printed departments: " + printedSummary + " | Remaining: " + remaining
                + " | State: " + state);
    }

    private void showReportPreview(LabOrder order, List<String> selectedDepartments, Window ownerWindow,
            ReportPaperMode paperMode) {
        PageLayout previewLayout = resolvePreviewPageLayout();
        if (previewLayout == null) {
            showError("Preview unavailable: no printer/page layout found.");
            return;
        }

        List<StackPane> pages = buildReportPages(order, previewLayout, selectedDepartments, paperMode);
        if (pages.isEmpty()) {
            showError("No printable results found for selected department(s).");
            return;
        }

        VBox pagesContainer = new VBox(16);
        pagesContainer.setPadding(new Insets(16));
        pagesContainer.setStyle("-fx-background-color: #f2f2f2;");

        final double previewPageWidth = 760;

        for (int i = 0; i < pages.size(); i++) {
            StackPane page = pages.get(i);
            page.resize(page.getPrefWidth(), page.getPrefHeight());
            page.applyCss();
            page.layout();

            double pageWidth = page.getWidth() > 0 ? page.getWidth() : page.getPrefWidth();
            double pageHeight = page.getHeight() > 0 ? page.getHeight() : page.getPrefHeight();
            if (pageWidth <= 0) {
                pageWidth = 595; // A4 width in points fallback.
            }
            if (pageHeight <= 0) {
                pageHeight = 842; // A4 height in points fallback.
            }

            double scale = previewPageWidth / pageWidth;

            Group scaledPage = new Group(page);
            scaledPage.setScaleX(scale);
            scaledPage.setScaleY(scale);

            StackPane pageFrame = new StackPane(scaledPage);
            pageFrame.setPadding(new Insets(8));
            pageFrame.setPrefWidth(previewPageWidth + 20);
            pageFrame.setMinHeight(pageHeight * scale + 20);
            pageFrame.setStyle("-fx-background-color: white; -fx-border-color: #c9c9c9; -fx-border-width: 1;");

            Label pageLabel = new Label("Page " + (i + 1));
            pageLabel.setStyle("-fx-font-weight: bold;");

            VBox pageBlock = new VBox(6, pageLabel, pageFrame);
            pagesContainer.getChildren().add(pageBlock);
        }

        ScrollPane scrollPane = new ScrollPane(pagesContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setPannable(true);

        Label header = new Label(
                "Preview - Order #" + order.getId() + " (" + String.join(", ", selectedDepartments) + ")"
                        + " | Format: " + paperMode.label());
        header.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");

        BorderPane root = new BorderPane();
        root.setTop(new VBox(header, new Separator()));
        BorderPane.setMargin(root.getTop(), new Insets(10, 10, 0, 10));
        root.setCenter(scrollPane);

        Button closeBtn = new Button("Close");
        closeBtn.setOnAction(e -> {
            Stage stage = (Stage) closeBtn.getScene().getWindow();
            stage.close();
        });
        HBox footer = new HBox(closeBtn);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(10));
        root.setBottom(footer);

        Stage previewStage = new Stage();
        if (ownerWindow != null) {
            previewStage.initOwner(ownerWindow);
            previewStage.initModality(Modality.WINDOW_MODAL);
        }
        brandingService.tagStage(previewStage, "Report Preview");
        previewStage.setScene(new Scene(root, 860, 760));
        previewStage.showAndWait();
    }

    private PageLayout resolvePreviewPageLayout() {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job != null) {
            return job.getJobSettings().getPageLayout();
        }
        Printer printer = Printer.getDefaultPrinter();
        if (printer != null) {
            return printer.createPageLayout(Paper.A4, PageOrientation.PORTRAIT, Printer.MarginType.DEFAULT);
        }
        return null;
    }

    private PageContext addDepartmentToPages(String departmentName, List<LabResult> results,
            PageContext pageContext, List<StackPane> pages, double printableWidth, double printableHeight,
            double topInset, double bottomInset, double contentWidth, double availableHeight) {
        List<LabResult> departmentResults = results.stream()
                .sorted(LabResultDisplayOrder.comparator())
                .collect(Collectors.toList());

        VBox departmentSection = buildDepartmentSection(departmentName, departmentResults, contentWidth);
        double requiredHeight = measureNodeHeight(departmentSection, contentWidth);

        // Core rule: if whole department cannot fit in remaining space, move the whole
        // department to a new page (provided it can fit on one page).
        if (requiredHeight <= pageContext.availableHeight && pageContext.remainingHeight < requiredHeight) {
            pageContext = newPage(pages, printableWidth, printableHeight, topInset, bottomInset, contentWidth,
                    availableHeight);
        }

        // If a single department is too tall to fit one page, retain old row-splitting
        // behavior as fallback to avoid clipping content.
        if (requiredHeight > pageContext.availableHeight) {
            return addLargeDepartmentWithRowSplit(departmentName, departmentResults, pageContext, pages,
                    printableWidth, printableHeight, topInset, bottomInset, contentWidth, availableHeight);
        }

        addNodeToPage(pageContext, departmentSection, contentWidth);
        return pageContext;
    }

    private VBox buildDepartmentSection(String departmentName, List<LabResult> departmentResults, double contentWidth) {
        Label deptLabel = createDepartmentLabel(departmentName);
        GridPane table = createResultsTable(contentWidth);
        addTableHeader(table);

        int rowIndex = 1;
        for (LabResult result : departmentResults) {
            addTableRow(table, rowIndex++, result, departmentName);
        }

        Region spacer = createSpacer(4);
        VBox section = new VBox(2, deptLabel, table, spacer);
        section.setPrefWidth(contentWidth);
        return section;
    }

    private PageContext addLargeDepartmentWithRowSplit(String departmentName, List<LabResult> departmentResults,
            PageContext pageContext, List<StackPane> pages, double printableWidth, double printableHeight,
            double topInset, double bottomInset, double contentWidth, double availableHeight) {
        Label deptLabel = createDepartmentLabel(departmentName);
        GridPane table = createResultsTable(contentWidth);
        addTableHeader(table);

        if (!canFitNode(pageContext, deptLabel, table)) {
            pageContext = newPage(pages, printableWidth, printableHeight, topInset, bottomInset, contentWidth,
                    availableHeight);
            deptLabel = createDepartmentLabel(departmentName);
        }

        addNodeToPage(pageContext, deptLabel, contentWidth);
        addNodeToPage(pageContext, table, contentWidth);

        int rowIndex = 1;
        for (LabResult result : departmentResults) {
            List<Node> rowNodes = addTableRow(table, rowIndex, result, departmentName);
            rowIndex++;
            if (!fitsCurrentPage(pageContext, contentWidth)) {
                removeRowNodes(table, rowNodes);
                rowIndex--;

                pageContext = newPage(pages, printableWidth, printableHeight, topInset, bottomInset, contentWidth,
                        availableHeight);
                deptLabel = createDepartmentLabel(departmentName);
                addNodeToPage(pageContext, deptLabel, contentWidth);
                table = createResultsTable(contentWidth);
                addTableHeader(table);
                addNodeToPage(pageContext, table, contentWidth);

                rowNodes = addTableRow(table, rowIndex, result, departmentName);
                rowIndex++;
            }
        }

        addNodeToPage(pageContext, createSpacer(4), contentWidth);
        return pageContext;
    }

    private GridPane createResultsTable(double contentWidth) {
        GridPane table = new GridPane();
        table.setHgap(6);
        table.setVgap(1);
        table.setPrefWidth(contentWidth);

        ColumnConstraints testCol = new ColumnConstraints();
        testCol.setPercentWidth(40);
        ColumnConstraints resultCol = new ColumnConstraints();
        resultCol.setPercentWidth(15);
        ColumnConstraints unitCol = new ColumnConstraints();
        unitCol.setPercentWidth(15);
        ColumnConstraints refCol = new ColumnConstraints();
        refCol.setPercentWidth(30);
        table.getColumnConstraints().addAll(testCol, resultCol, unitCol, refCol);
        return table;
    }

    private void addTableHeader(GridPane table) {
        Label testHeader = createReportHeaderLabel("Test Name");
        Label resultHeader = createReportHeaderLabel("Result");
        Label unitHeader = createReportHeaderLabel("Unit");
        Label refHeader = createReportHeaderLabel("Reference Range");
        table.add(testHeader, 0, 0);
        table.add(resultHeader, 1, 0);
        table.add(unitHeader, 2, 0);
        table.add(refHeader, 3, 0);
    }

    private List<Node> addTableRow(GridPane table, int rowIndex, LabResult result, String departmentName) {
        TestDefinition test = result.getTestDefinition();
        String testName = test != null && test.getTestName() != null ? test.getTestName() : "-";
        String value = result.getResultValue() != null ? result.getResultValue() : "-";
        String unit = test != null && test.getUnit() != null ? test.getUnit() : "-";
        String reference = buildReferenceRange(test, result.getLabOrder() != null
                ? result.getLabOrder().getPatient()
                : null);

        Label testLabel = createReportLabel(testName);
        Label valueLabel = createReportLabel(value);
        Label unitLabel = createReportLabel(unit);
        Label refLabel = createReportLabel(reference);

        if (result.isAbnormal()) {
            valueLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 9;");
        }

        table.add(testLabel, 0, rowIndex);
        table.add(valueLabel, 1, rowIndex);
        table.add(unitLabel, 2, rowIndex);
        table.add(refLabel, 3, rowIndex);

        return List.of(testLabel, valueLabel, unitLabel, refLabel);
    }

    private void removeRowNodes(GridPane table, List<Node> nodes) {
        table.getChildren().removeAll(nodes);
    }

    private boolean canFitNode(PageContext pageContext, Node... nodes) {
        if (pageContext == null) {
            return false;
        }
        double spacing = pageContext.content.getSpacing();
        double total = 0;
        for (int i = 0; i < nodes.length; i++) {
            total += measureNodeHeight(nodes[i], pageContext.contentWidth);
            if (i < nodes.length - 1) {
                total += spacing;
            }
        }
        return pageContext.remainingHeight >= total;
    }

    private boolean fitsCurrentPage(PageContext pageContext, double contentWidth) {
        double currentHeight = measureNodeHeight(pageContext.content, contentWidth);
        pageContext.remainingHeight = pageContext.availableHeight - currentHeight;
        return pageContext.remainingHeight >= 0;
    }

    private void addNodeToPage(PageContext pageContext, Node node, double contentWidth) {
        pageContext.content.getChildren().add(node);
        fitsCurrentPage(pageContext, contentWidth);
    }

    private void attachPatientHeader(StackPane page, GridPane patientInfo, double printableWidth,
            PageLayout pageLayout) {
        // 1. Calculate a proportional width for the box (about 40% of the page)
        double headerWidth = printableWidth * 0.40;
        patientInfo.setPrefWidth(headerWidth);
        patientInfo.setMaxWidth(headerWidth);

        // 2. Position the box in the Top-Right corner
        StackPane.setAlignment(patientInfo, Pos.TOP_RIGHT);

        // 3. CALCULATION FOR PERFECT 0.5 INCH ALIGNMENT
        // Desired distance from physical edge = 36 points (0.5 inch)
        double targetTopPoints = 36.0 + resolvePatientInfoOffsetYPoints();
        double targetRightPoints = 36.0;

        // Subtract what the printer is already enforcing
        double appliedTopMargin = Math.max(0, targetTopPoints - pageLayout.getTopMargin());
        double appliedRightMargin = Math.max(0, targetRightPoints - pageLayout.getRightMargin());
        StackPane.setMargin(patientInfo, new Insets(appliedTopMargin, appliedRightMargin, 0, 0));

        // 4. Add the box to the page StackPane
        page.getChildren().add(patientInfo);
    }

    private VBox createBlankPaperHeader(LabOrder order, GridPane patientInfo, double contentWidth) {
        Label reportTitle = new Label(brandingService.getReportHeaderText());
        reportTitle.setStyle("-fx-font-size: 12; -fx-font-weight: bold;");
        Label orderLabel = new Label("Order #" + order.getId());
        orderLabel.setStyle("-fx-font-size: 9;");

        double patientBoxWidth = Math.min(contentWidth, 460);
        patientInfo.setPrefWidth(patientBoxWidth);
        patientInfo.setMaxWidth(patientBoxWidth);

        Separator separator = new Separator();
        separator.setPrefWidth(contentWidth);

        VBox header = new VBox(4, reportTitle, orderLabel, patientInfo, separator);
        header.setAlignment(Pos.TOP_CENTER);
        header.setPrefWidth(contentWidth);
        return header;
    }

    private ReportPaperMode resolveSelectedPaperMode(ToggleGroup paperModeGroup) {
        if (paperModeGroup == null || paperModeGroup.getSelectedToggle() == null) {
            return ReportPaperMode.LETTERHEAD;
        }
        Object selected = paperModeGroup.getSelectedToggle().getUserData();
        if (selected instanceof ReportPaperMode mode) {
            return mode;
        }
        return ReportPaperMode.LETTERHEAD;
    }

    private double resolvePatientInfoOffsetYPoints() {
        String rawValue = configService.getTrimmed(REPORT_PATIENT_INFO_OFFSET_Y_MM, "0");
        if (rawValue.isBlank()) {
            return 0;
        }
        try {
            double offsetMm = Double.parseDouble(rawValue);
            if (Double.isNaN(offsetMm) || Double.isInfinite(offsetMm)) {
                return 0;
            }
            return offsetMm * POINTS_PER_MM;
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private PageContext newPage(List<StackPane> pages, double printableWidth, double printableHeight,
            double topInset, double bottomInset, double contentWidth, double availableHeight) {
        VBox content = new VBox(2);
        content.setAlignment(Pos.TOP_LEFT);
        content.setPrefWidth(contentWidth);
        content.setPadding(new Insets(topInset, 24, bottomInset, 24));

        StackPane page = new StackPane(content);
        page.setPrefSize(printableWidth, printableHeight);
        page.setMinSize(printableWidth, printableHeight);
        page.setMaxSize(printableWidth, printableHeight);
        StackPane.setAlignment(content, Pos.TOP_CENTER);
        pages.add(page);

        return new PageContext(content, contentWidth, availableHeight);
    }

    private double measureNodeHeight(Node node, double width) {
        if (node instanceof Region region) {
            region.setPrefWidth(width);
            region.applyCss();
            region.autosize();
            return region.prefHeight(width);
        }
        node.applyCss();
        node.autosize();
        return node.getBoundsInLocal().getHeight();
    }

    private String buildReferenceRange(TestDefinition test, Patient patient) {
        if (test == null) {
            return "-";
        }
        Integer age = toAgeInYears(patient);
        String gender = patient != null ? patient.getGender() : null;

        ReferenceRange matchingRange = findMatchingRange(test, age, gender);
        if (matchingRange != null) {
            if (matchingRange.getReferenceText() != null && !matchingRange.getReferenceText().trim().isEmpty()) {
                return matchingRange.getReferenceText().trim();
            }
            String min = matchingRange.getMinVal() != null
                    ? localeFormatService.formatNumber(matchingRange.getMinVal())
                    : null;
            String max = matchingRange.getMaxVal() != null
                    ? localeFormatService.formatNumber(matchingRange.getMaxVal())
                    : null;
            return formatMinMax(min, max);
        }

        String min = test.getMinRange() != null
                ? localeFormatService.formatNumber(test.getMinRange())
                : null;
        String max = test.getMaxRange() != null
                ? localeFormatService.formatNumber(test.getMaxRange())
                : null;
        return formatMinMax(min, max);
    }

    private ReferenceRange findMatchingRange(TestDefinition test, Integer age, String gender) {
        if (test == null || test.getId() == null) {
            return null;
        }
        var ranges = referenceRangeRepository.findByTestId(test.getId());
        if (ranges == null || ranges.isEmpty()) {
            return null;
        }
        return ranges.stream()
                .filter(range -> matchesRange(range, age, gender))
                .sorted((a, b) -> {
                    int genderScoreA = genderScore(a, gender);
                    int genderScoreB = genderScore(b, gender);
                    if (genderScoreA != genderScoreB) {
                        return Integer.compare(genderScoreB, genderScoreA);
                    }
                    Integer minA = a.getMinAge();
                    Integer minB = b.getMinAge();
                    if (minA == null && minB == null) {
                        return 0;
                    }
                    if (minA == null) {
                        return 1;
                    }
                    if (minB == null) {
                        return -1;
                    }
                    return Integer.compare(minA, minB);
                })
                .findFirst()
                .orElse(null);
    }

    private boolean matchesRange(ReferenceRange range, Integer age, String gender) {
        if (range == null) {
            return false;
        }
        if (gender != null && range.getGender() != null && !"Both".equalsIgnoreCase(range.getGender())
                && !range.getGender().equalsIgnoreCase(gender)) {
            return false;
        }
        if (age != null) {
            if (range.getMinAge() != null && age < range.getMinAge()) {
                return false;
            }
            if (range.getMaxAge() != null && age > range.getMaxAge()) {
                return false;
            }
        }
        return true;
    }

    private int genderScore(ReferenceRange range, String gender) {
        if (range == null) {
            return 0;
        }
        String rangeGender = range.getGender();
        if (gender != null && rangeGender != null && rangeGender.equalsIgnoreCase(gender)) {
            return 2;
        }
        if (rangeGender != null && "Both".equalsIgnoreCase(rangeGender)) {
            return 1;
        }
        return 0;
    }

    private String formatMinMax(String min, String max) {
        if (min != null && max != null) {
            return min + " - " + max;
        }
        if (min != null) {
            return "≥ " + min;
        }
        if (max != null) {
            return "≤ " + max;
        }
        return "-";
    }

    private Integer toAgeInYears(Patient patient) {
        if (patient == null || patient.getAge() == null) {
            return null;
        }
        int age = patient.getAge();
        String ageUnit = patient.getAgeUnit();
        if (ageUnit != null && ageUnit.equalsIgnoreCase("Months")) {
            return Math.max(0, age / 12);
        }
        if (ageUnit != null && ageUnit.equalsIgnoreCase("Days")) {
            return 0;
        }
        return age;
    }

    private Label createReportLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 9;");
        return label;
    }

    private Label createReportHeaderLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 9; -fx-font-weight: bold; -fx-underline: true;");
        return label;
    }

    private Label createDepartmentLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-weight: bold; -fx-underline: true; -fx-font-size: 10;");
        return label;
    }

    private Region createSpacer(double height) {
        Region spacer = new Region();
        spacer.setPrefHeight(height);
        spacer.setMinHeight(height);
        return spacer;
    }

    private static class PageContext {
        private final VBox content;
        private final double contentWidth;
        private final double availableHeight;
        private double remainingHeight;

        private PageContext(VBox content, double contentWidth, double availableHeight) {
            this.content = content;
            this.contentWidth = contentWidth;
            this.availableHeight = availableHeight;
            this.remainingHeight = availableHeight;
        }
    }

    private String getCurrentUsername() {
        try {
            Stage stage = (Stage) mainContainer.getScene().getWindow();
            User user = SessionManager.getUser(stage);
            if (user != null && user.getUsername() != null) {
                return user.getUsername();
            }
        } catch (Exception ignored) {
        }
        User fallback = SessionManager.getCurrentUser();
        return fallback != null && fallback.getUsername() != null ? fallback.getUsername() : "UNKNOWN";
    }

    @FXML
    private void handleReprintReceipt() {
        openReceiptReprintDialog();
    }

    private void printReceipt(LabOrder order) {
        TextFlow receiptContent = createReceiptContent(order);
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job != null && job.showPrintDialog(mainContainer.getScene().getWindow())) {
            boolean success = job.printPage(receiptContent);
            if (success)
                job.endJob();
            else
                showError("Failed to print receipt");
        }
    }

    private TextFlow createReceiptContent(LabOrder order) {
        TextFlow flow = new TextFlow();
        Patient patient = order.getPatient();
        Text header = new Text("LIMS LABORATORY - RECEIPT\n\n");
        header.setStyle("-fx-font-size: 16; -fx-font-weight: bold;");

        Text orderInfo = new Text("Order #: " + order.getId() + "\n" +
                "Date: " + localeFormatService.formatDateTime(order.getOrderDate()) + "\n\n" +
                "Patient: " + patient.getFullName() + "\n" +
                "MRN: " + patient.getMrn() + "\n\n");

        java.util.Set<Long> panelTestIds = getPanelTestIds(order);
        StringBuilder tests = new StringBuilder("ITEMS ORDERED\n" + "-".repeat(30) + "\n");

        if (order.getPanels() != null && !order.getPanels().isEmpty()) {
            tests.append("PANELS\n");
            for (Panel panel : order.getPanels()) {
                tests.append(panel.getPanelName()).append(" - ")
                        .append(localeFormatService.formatCurrency(
                                panel.getPrice() != null ? panel.getPrice() : java.math.BigDecimal.ZERO))
                        .append("\n");
            }
            tests.append("\n");
        }

        tests.append("TESTS\n");
        if (order.getResults() != null) {
            for (LabResult result : order.getResults()) {
                if (result.getTestDefinition() != null && !panelTestIds.contains(result.getTestDefinition().getId())) {
                    tests.append(result.getTestDefinition().getTestName()).append(" - ")
                            .append(localeFormatService.formatCurrency(
                                    result.getTestDefinition().getPrice() != null
                                            ? result.getTestDefinition().getPrice()
                                            : java.math.BigDecimal.ZERO))
                            .append("\n");
                }
            }
        }
        tests.append("-".repeat(30)).append("\n\n");
        Text testsText = new Text(tests.toString());

        java.math.BigDecimal total = order.getTotalAmount() != null ? order.getTotalAmount()
                : java.math.BigDecimal.ZERO;
        java.math.BigDecimal discount = order.getDiscountAmount() != null ? order.getDiscountAmount()
                : java.math.BigDecimal.ZERO;
        java.math.BigDecimal paid = order.getPaidAmount() != null ? order.getPaidAmount()
                : java.math.BigDecimal.ZERO;
        java.math.BigDecimal balance = order.getBalanceDue() != null ? order.getBalanceDue()
                : java.math.BigDecimal.ZERO;

        Text billing = new Text("Total Amount: " + localeFormatService.formatCurrency(total) + "\n" +
                "Discount: " + localeFormatService.formatCurrency(discount) + "\n" +
                "Paid: " + localeFormatService.formatCurrency(paid) + "\n" +
                "Balance Due: " + localeFormatService.formatCurrency(balance) + "\n\n");
        billing.setStyle("-fx-font-weight: bold;");
        Text footer = new Text("Thank you for choosing " + brandingService.getLabNameOrAppName());
        footer.setStyle("-fx-font-size: 10;");

        flow.getChildren().addAll(header, orderInfo, testsText, billing, footer);
        return flow;
    }

    private java.util.Set<Long> getPanelTestIds(LabOrder order) {
        if (order == null || order.getPanels() == null || order.getPanels().isEmpty()) {
            return java.util.Set.of();
        }
        List<Integer> panelIds = order.getPanels().stream()
                .map(Panel::getId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toList());
        if (panelIds.isEmpty()) {
            return java.util.Set.of();
        }
        List<Panel> panels = panelRepository.findAllWithTestsById(panelIds);
        java.util.Set<Long> panelTestIds = new java.util.HashSet<>();
        for (Panel panel : panels) {
            if (panel.getPrice() == null) {
                continue;
            }
            if (panel.getTests() == null) {
                continue;
            }
            for (TestDefinition test : panel.getTests()) {
                if (test.getId() != null) {
                    panelTestIds.add(test.getId());
                }
            }
        }
        return panelTestIds;
    }

    private void openReceiptReprintDialog() {
        Stage stage = new Stage();
        brandingService.tagStage(stage, "Reprint Receipt");
        stage.initOwner(mainContainer.getScene().getWindow());
        stage.initModality(Modality.WINDOW_MODAL);

        Label header = new Label("Find an order to reprint the receipt");
        header.setStyle("-fx-font-size: 16; -fx-font-weight: bold;");

        TextField searchField = new TextField();
        searchField.setPromptText("Search by Order #, Patient name, MRN, or phone");

        DatePicker fromDate = new DatePicker(LocalDate.now().minusDays(7));
        DatePicker toDate = new DatePicker(LocalDate.now());
        localeFormatService.applyDatePickerLocale(fromDate, toDate);

        Button todayBtn = new Button("Today");
        todayBtn.getStyleClass().add("btn-secondary");
        Button searchBtn = new Button("Search");
        searchBtn.getStyleClass().add("btn-primary");

        HBox filters = new HBox(10,
                new Label("From:"), fromDate,
                new Label("To:"), toDate,
                searchField,
                todayBtn,
                searchBtn);
        filters.setAlignment(Pos.CENTER_LEFT);

        Label status = new Label("Enter filters and click Search.");
        status.setStyle("-fx-text-fill: #7f8c8d;");

        TableView<LabOrder> table = new TableView<>();
        TableColumn<LabOrder, String> idCol = new TableColumn<>("Order #");
        idCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue() != null ? String.valueOf(data.getValue().getId()) : ""));

        TableColumn<LabOrder, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue() != null && data.getValue().getOrderDate() != null
                        ? localeFormatService.formatDateTime(data.getValue().getOrderDate())
                        : ""));

        TableColumn<LabOrder, String> patientCol = new TableColumn<>("Patient");
        patientCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue() != null && data.getValue().getPatient() != null
                        ? data.getValue().getPatient().getFullName()
                        : ""));

        TableColumn<LabOrder, String> mrnCol = new TableColumn<>("MRN");
        mrnCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue() != null && data.getValue().getPatient() != null
                        ? data.getValue().getPatient().getMrn()
                        : ""));

        TableColumn<LabOrder, String> totalCol = new TableColumn<>("Total");
        totalCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue() != null && data.getValue().getTotalAmount() != null
                        ? localeFormatService.formatCurrency(data.getValue().getTotalAmount())
                        : localeFormatService.formatCurrency(java.math.BigDecimal.ZERO)));

        TableColumn<LabOrder, String> paidCol = new TableColumn<>("Paid");
        paidCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue() != null && data.getValue().getPaidAmount() != null
                        ? localeFormatService.formatCurrency(data.getValue().getPaidAmount())
                        : localeFormatService.formatCurrency(java.math.BigDecimal.ZERO)));

        TableColumn<LabOrder, String> balanceCol = new TableColumn<>("Balance");
        balanceCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue() != null && data.getValue().getBalanceDue() != null
                        ? localeFormatService.formatCurrency(data.getValue().getBalanceDue())
                        : localeFormatService.formatCurrency(java.math.BigDecimal.ZERO)));

        table.getColumns().setAll(List.of(idCol, dateCol, patientCol, mrnCol, totalCol, paidCol, balanceCol));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        ObservableList<LabOrder> rows = FXCollections.observableArrayList();
        table.setItems(rows);

        Button printBtn = new Button("Print Receipt");
        printBtn.getStyleClass().add("btn-primary");
        printBtn.setDisable(true);

        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("btn-secondary");

        HBox actions = new HBox(10, printBtn, closeBtn);
        actions.setAlignment(Pos.CENTER_RIGHT);

        table.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            printBtn.setDisable(newVal == null);
        });

        Runnable runSearch = () -> {
            LocalDate start = fromDate.getValue();
            LocalDate end = toDate.getValue();
            if (start == null || end == null) {
                showError("Please select a valid date range.");
                return;
            }
            if (end.isBefore(start)) {
                showError("End date cannot be before start date.");
                return;
            }

            String query = searchField.getText() != null ? searchField.getText().trim().toLowerCase() : "";
            LocalDateTime startDt = start.atStartOfDay();
            LocalDateTime endDt = end.atTime(23, 59, 59);

            List<LabOrder> orders = labOrderRepository.findByOrderDateBetween(startDt, endDt);
            if (!query.isEmpty()) {
                orders = orders.stream()
                        .filter(order -> matchesReceiptQuery(order, query))
                        .collect(Collectors.toList());
            }

            rows.setAll(orders);
            status.setText(rows.size() + " order(s) found");
        };

        searchBtn.setOnAction(e -> runSearch.run());
        searchField.setOnAction(e -> runSearch.run());
        todayBtn.setOnAction(e -> {
            LocalDate today = LocalDate.now();
            fromDate.setValue(today);
            toDate.setValue(today);
            runSearch.run();
        });

        printBtn.setOnAction(e -> {
            LabOrder selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                printReceipt(selected);
            }
        });

        table.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                LabOrder selected = table.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    printReceipt(selected);
                }
            }
        });

        closeBtn.setOnAction(e -> stage.close());

        VBox root = new VBox(12, header, filters, status, table, actions);
        root.setPadding(new Insets(15));
        root.setPrefWidth(900);
        root.setPrefHeight(520);

        stage.setScene(new Scene(root));
        stage.show();
    }

    private boolean matchesReceiptQuery(LabOrder order, String query) {
        if (order == null) {
            return false;
        }
        if (String.valueOf(order.getId()).contains(query)) {
            return true;
        }
        Patient patient = order.getPatient();
        if (patient == null) {
            return false;
        }
        if (patient.getFullName() != null && patient.getFullName().toLowerCase().contains(query)) {
            return true;
        }
        if (patient.getMrn() != null && patient.getMrn().toLowerCase().contains(query)) {
            return true;
        }
        if (patient.getMobileNumber() != null && patient.getMobileNumber().toLowerCase().contains(query)) {
            return true;
        }
        return false;
    }

    // ========== Logout ==========

    @FXML
    private void handleLogout() {
        try {
            stopAutoRefresh();
            LogoutUtil.confirmAndCloseParentTab(mainContainer);
        } catch (Exception e) {
            showError("Logout failed: " + e.getMessage());
        }
    }

    // ========== Utility Methods ==========

    private void applyBranding() {
        if (footerBrandLabel != null) {
            footerBrandLabel.setText(brandingService.getLabNameOrAppName() + " - Reception");
        }
    }

    private void openWindow(String fxmlPath, String title, int width, int height) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            loader.setControllerFactory(applicationContext::getBean);
            Parent root = loader.load();

            Stage stage = new Stage();
            brandingService.tagStage(stage, title);
            stage.setScene(new Scene(root, width, height));
            stage.initModality(Modality.NONE);
            stage.setOnHidden(e -> loadOrders());
            stage.show();
        } catch (Exception e) {
            showError("Failed to open " + title + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
