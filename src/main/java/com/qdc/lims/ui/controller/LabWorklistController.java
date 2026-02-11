package com.qdc.lims.ui.controller;

import com.qdc.lims.entity.LabOrder;
import com.qdc.lims.repository.LabOrderRepository;
import com.qdc.lims.service.LocaleFormatService;
import com.qdc.lims.service.OrderCancellationService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JavaFX controller for lab worklist.
 */
@Component("labWorklistController")
public class LabWorklistController {

    @FXML
    private RadioButton pendingRadio;

    @FXML
    private RadioButton completedRadio;

    @FXML
    private RadioButton allRadio;

    @FXML
    private ToggleGroup filterGroup;

    @FXML
    private TextField searchField;

    @FXML
    private Label pendingCountLabel;

    @FXML
    private Label completedTodayLabel;

    @FXML
    private Label totalOrdersLabel;

    @FXML
    private TableView<LabOrder> ordersTable;

    @FXML
    private TableColumn<LabOrder, Long> orderIdColumn;

    @FXML
    private TableColumn<LabOrder, String> mrnColumn;

    @FXML
    private TableColumn<LabOrder, String> patientNameColumn;

    @FXML
    private TableColumn<LabOrder, String> ageGenderColumn;

    @FXML
    private TableColumn<LabOrder, Integer> testCountColumn;

    @FXML
    private TableColumn<LabOrder, String> orderDateColumn;

    @FXML
    private TableColumn<LabOrder, String> statusColumn;

    @FXML
    private TableColumn<LabOrder, Void> actionColumn;

    private final LabOrderRepository orderRepository;
    private final ApplicationContext springContext;
    private final LocaleFormatService localeFormatService;
    private final OrderCancellationService orderCancellationService;
    private List<LabOrder> allOrders;
    private Runnable closeAction;

    // Flag to show completed tests on initialization
    private boolean showCompletedOnInit = false;

    public LabWorklistController(LabOrderRepository orderRepository,
            ApplicationContext springContext,
            LocaleFormatService localeFormatService,
            OrderCancellationService orderCancellationService) {
        this.orderRepository = orderRepository;
        this.springContext = springContext;
        this.localeFormatService = localeFormatService;
        this.orderCancellationService = orderCancellationService;
    }

    /**
     * Set to show completed tests when the window opens.
     * Must be called before initialize() runs (i.e., after load but before show).
     */
    public void setShowCompletedOnInit(boolean showCompleted) {
        this.showCompletedOnInit = showCompleted;
    }

    @FXML
    private void initialize() {
        if (filterGroup == null) {
            filterGroup = new ToggleGroup();
        }
        pendingRadio.setToggleGroup(filterGroup);
        completedRadio.setToggleGroup(filterGroup);
        allRadio.setToggleGroup(filterGroup);
        ordersTable.setRowFactory(table -> {
            TableRow<LabOrder> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !row.isEmpty()) {
                    openResultEntryForm(row.getItem());
                }
            });
            return row;
        });

        setupTableColumns();
        loadOrders();
        updateStats();

        // If flagged to show completed tests, select the completed radio button
        if (showCompletedOnInit) {
            completedRadio.setSelected(true);
            applyFilter();
        } else if (pendingRadio != null) {
            pendingRadio.setSelected(true);
        }
    }

    private void setupTableColumns() {
        orderIdColumn.setCellValueFactory(new PropertyValueFactory<>("id"));

        mrnColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(
                cellData.getValue().getPatient() != null && cellData.getValue().getPatient().getMrn() != null
                        ? cellData.getValue().getPatient().getMrn()
                        : "-"));

        patientNameColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(
                cellData.getValue().getPatient() != null && cellData.getValue().getPatient().getFullName() != null
                        ? cellData.getValue().getPatient().getFullName()
                        : "-"));

        ageGenderColumn.setCellValueFactory(cellData -> {
            var patient = cellData.getValue().getPatient();
            if (patient == null) {
                return new javafx.beans.property.SimpleStringProperty("-");
            }
            return new javafx.beans.property.SimpleStringProperty(
                    patient.getAge() + " / " + patient.getGender());
        });

        testCountColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleIntegerProperty(
                cellData.getValue().getResults() != null ? cellData.getValue().getResults().size() : 0).asObject());

        orderDateColumn.setCellValueFactory(cellData -> {
            if (cellData.getValue().getOrderDate() == null) {
                return new javafx.beans.property.SimpleStringProperty("-");
            }
            return new javafx.beans.property.SimpleStringProperty(
                    localeFormatService.formatDateTime(cellData.getValue().getOrderDate()));
        });

        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        // Color-code status
        statusColumn.setCellFactory(column -> new TableCell<LabOrder, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.equals("PENDING") || item.equals("IN_PROGRESS")) {
                        setStyle("-fx-background-color: #e67e22; -fx-text-fill: white; -fx-font-weight: bold;");
                    } else if (item.equals("COMPLETED")) {
                        setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold;");
                    }
                }
            }
        });

        // Action buttons in table
        actionColumn.setCellFactory(param -> new TableCell<>() {
            private final Button viewTestsBtn = new Button("Open");
            private final Button editResultsBtn = new Button("Edit Results");

            {
                viewTestsBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-padding: 5 10;");
                viewTestsBtn.setOnAction(event -> {
                    LabOrder order = getTableView().getItems().get(getIndex());
                    openResultEntryForm(order);
                });

                editResultsBtn.setStyle("-fx-background-color: #e67e22; -fx-text-fill: white; -fx-padding: 5 10;");
                editResultsBtn.setOnAction(event -> {
                    LabOrder order = getTableView().getItems().get(getIndex());
                    openResultEntryForm(order);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    LabOrder order = getTableView().getItems().get(getIndex());
                    if (isPendingStatus(order)) {
                        setGraphic(viewTestsBtn);
                    } else {
                        // Allow editing completed orders to fix mistakes
                        setGraphic(editResultsBtn);
                    }
                }
            }
        });
    }

    private void loadOrders() {
        allOrders = orderRepository.findAll();
        applyFilter();
    }

    private void applyFilter() {
        List<LabOrder> filteredOrders = allOrders;
        String searchTerm = searchField.getText().trim().toLowerCase();

        // Do not show orders that have no tests/results attached.
        filteredOrders = filteredOrders.stream()
                .filter(order -> order.getResults() != null && !order.getResults().isEmpty())
                .collect(Collectors.toList());

        if (pendingRadio.isSelected()) {
            filteredOrders = filteredOrders.stream()
                    .filter(this::isPendingStatus)
                    .collect(Collectors.toList());
        } else if (completedRadio.isSelected()) {
            filteredOrders = filteredOrders.stream()
                    .filter(order -> "COMPLETED".equals(order.getStatus()))
                    .collect(Collectors.toList());
        }

        if (!searchTerm.isEmpty()) {
            filteredOrders = filteredOrders.stream()
                    .filter(order -> matchesSearch(order, searchTerm))
                    .collect(Collectors.toList());
        }

        ObservableList<LabOrder> observableOrders = FXCollections.observableArrayList(filteredOrders);
        ordersTable.setItems(observableOrders);
    }

    private void updateStats() {
        long pending = orderRepository.countPendingWithResults();
        LocalDate today = LocalDate.now();
        long completedToday = orderRepository.findByStatusAndOrderDateBetween(
                "COMPLETED",
                today.atStartOfDay(),
                today.atTime(23, 59, 59)).stream()
                .filter(order -> order.getResults() != null && !order.getResults().isEmpty())
                .count();
        long total = orderRepository.findAll().stream()
                .filter(order -> order.getResults() != null && !order.getResults().isEmpty())
                .count();

        pendingCountLabel.setText(String.valueOf(pending));
        completedTodayLabel.setText(String.valueOf(completedToday));
        totalOrdersLabel.setText(String.valueOf(total));
    }

    @FXML
    private void handleFilterChange() {
        applyFilter();
    }

    @FXML
    private void handleSearch() {
        applyFilter();
    }

    @FXML
    private void handleRefresh() {
        searchField.clear();
        pendingRadio.setSelected(true);
        loadOrders();
        updateStats();
    }

    public void showPending() {
        if (pendingRadio != null) {
            pendingRadio.setSelected(true);
        }
        applyFilter();
    }

    public void showCompleted() {
        if (completedRadio != null) {
            completedRadio.setSelected(true);
        }
        applyFilter();
    }

    /**
     * Optional close callback used when this screen is embedded inside a tab.
     */
    public void setCloseAction(Runnable closeAction) {
        this.closeAction = closeAction;
    }

    private boolean matchesSearch(LabOrder order, String searchTerm) {
        if (order.getPatient() != null) {
            if (order.getPatient().getMrn() != null
                    && order.getPatient().getMrn().toLowerCase().contains(searchTerm)) {
                return true;
            }
            if (order.getPatient().getFullName() != null
                    && order.getPatient().getFullName().toLowerCase().contains(searchTerm)) {
                return true;
            }
        }
        return String.valueOf(order.getId()).contains(searchTerm);
    }

    @FXML
    private void handleEnterResults() {
        LabOrder selectedOrder = ordersTable.getSelectionModel().getSelectedItem();
        if (selectedOrder == null) {
            showAlert("Please select an order to enter or edit results");
            return;
        }

        openResultEntryForm(selectedOrder);
    }

    @FXML
    private void handleViewDetails() {
        LabOrder selectedOrder = ordersTable.getSelectionModel().getSelectedItem();
        if (selectedOrder == null) {
            showAlert("Please select an order to view");
            return;
        }

        openResultEntryForm(selectedOrder);
    }

    private void openResultEntryForm(LabOrder order) {
        if (order == null || order.getId() == null) {
            return;
        }
        boolean manageLock = shouldManageCancellationLock(order);
        boolean lockAcquired = false;
        try {
            if (manageLock) {
                orderCancellationService.markUnderLabReview(order.getId());
                lockAcquired = true;
            }

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/result_entry.fxml"));
            loader.setControllerFactory(springContext::getBean);
            Parent root = loader.load();

            ResultEntryController controller = loader.getController();
            controller.setOrder(order);

            Tab currentTab = findCurrentSessionTab();
            if (currentTab != null) {
                Node previousContent = currentTab.getContent();
                String originalTitle = currentTab.getText();
                Tooltip originalTooltip = currentTab.getTooltip();

                currentTab.setText(buildResultEntryTabTitle(originalTitle, order.getId()));
                currentTab.setTooltip(new Tooltip("Result Entry - Order #" + order.getId()));

                boolean releaseLockOnClose = manageLock && lockAcquired;
                controller.setCloseAction(() -> {
                    if (releaseLockOnClose) {
                        try {
                            orderCancellationService.releaseLabReview(order.getId());
                        } catch (Exception ignored) {
                            // Ignore lock-release failures during close to avoid blocking UI flow.
                        }
                    }
                    currentTab.setContent(previousContent);
                    currentTab.setText(originalTitle);
                    currentTab.setTooltip(originalTooltip);
                    refreshWorklistData();
                });

                currentTab.setContent(root);
                return;
            }

            Stage stage = new Stage();
            stage.setTitle("Enter Results - Order #" + order.getId());
            stage.setScene(new Scene(root));
            boolean releaseLockOnClose = manageLock && lockAcquired;
            stage.setOnHidden(e -> {
                if (releaseLockOnClose) {
                    try {
                        orderCancellationService.releaseLabReview(order.getId());
                    } catch (Exception ignored) {
                        // Ignore lock-release failures during close to avoid blocking UI flow.
                    }
                }
                refreshWorklistData();
            });
            stage.show();
        } catch (Exception e) {
            if (manageLock && lockAcquired) {
                try {
                    orderCancellationService.releaseLabReview(order.getId());
                } catch (Exception ignored) {
                    // Ignore secondary unlock errors while surfacing primary failure.
                }
            }
            e.printStackTrace();
            showAlert("Failed to open result entry: " + e.getMessage());
        }
    }

    private boolean isPendingStatus(LabOrder order) {
        if (order == null || order.getStatus() == null) {
            return false;
        }
        return "PENDING".equals(order.getStatus()) || "IN_PROGRESS".equals(order.getStatus());
    }

    private boolean shouldManageCancellationLock(LabOrder order) {
        return order != null
                && order.getId() != null
                && ("PENDING".equals(order.getStatus()) || "IN_PROGRESS".equals(order.getStatus()));
    }

    private void refreshWorklistData() {
        loadOrders();
        updateStats();
        applyFilter();
    }

    private Tab findCurrentSessionTab() {
        if (ordersTable == null || ordersTable.getScene() == null) {
            return null;
        }
        if (!(ordersTable.getScene().getRoot() instanceof BorderPane borderPane)) {
            return null;
        }
        if (!(borderPane.getCenter() instanceof TabPane tabPane)) {
            return null;
        }
        for (Tab tab : tabPane.getTabs()) {
            Node tabContent = tab.getContent();
            if (tabContent == ordersTable || isDescendantOf(ordersTable, tabContent)) {
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

    private String buildResultEntryTabTitle(String originalTitle, Long orderId) {
        if (originalTitle == null || originalTitle.isBlank()) {
            return "Order #" + orderId;
        }
        return originalTitle + " - Order #" + orderId;
    }

    @FXML
    private void handleClose() {
        if (closeAction != null) {
            closeAction.run();
            return;
        }
        Stage stage = (Stage) ordersTable.getScene().getWindow();
        stage.close();
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
