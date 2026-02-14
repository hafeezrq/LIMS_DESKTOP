package com.qdc.lims.ui.controller;

import com.qdc.lims.entity.InventoryItem;
import com.qdc.lims.repository.InventoryItemRepository;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JavaFX controller for inventory management view.
 */
@Component("inventoryViewController")
public class InventoryViewController {

    @FXML
    private RadioButton allItemsRadio;

    @FXML
    private RadioButton lowStockRadio;

    @FXML
    private RadioButton outOfStockRadio;

    @FXML
    private ToggleGroup filterGroup;

    @FXML
    private TextField searchField;

    @FXML
    private Label totalItemsLabel;

    @FXML
    private Label lowStockCountLabel;

    @FXML
    private Label outOfStockCountLabel;

    @FXML
    private TableView<InventoryItem> inventoryTable;

    @FXML
    private TableColumn<InventoryItem, String> itemNameColumn;

    @FXML
    private TableColumn<InventoryItem, String> categoryColumn;

    @FXML
    private TableColumn<InventoryItem, BigDecimal> currentStockColumn;

    @FXML
    private TableColumn<InventoryItem, String> unitColumn;

    @FXML
    private TableColumn<InventoryItem, BigDecimal> minStockColumn;

    @FXML
    private TableColumn<InventoryItem, String> supplierColumn;

    @FXML
    private TableColumn<InventoryItem, String> statusColumn;

    private final InventoryItemRepository inventoryRepository;
    private final ApplicationContext applicationContext;
    private List<InventoryItem> allItems;

    public InventoryViewController(InventoryItemRepository inventoryRepository, ApplicationContext applicationContext) {
        this.inventoryRepository = inventoryRepository;
        this.applicationContext = applicationContext;
    }

    @FXML
    private void initialize() {
        // Setup table columns
        itemNameColumn.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        categoryColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty("General")); // No
                                                                                                                   // category
                                                                                                                   // field
        currentStockColumn.setCellValueFactory(new PropertyValueFactory<>("currentStock"));
        unitColumn.setCellValueFactory(new PropertyValueFactory<>("unit"));
        minStockColumn.setCellValueFactory(new PropertyValueFactory<>("minThreshold"));

        // Supplier column
        supplierColumn.setCellValueFactory(cellData -> {
            if (cellData.getValue().getPreferredSupplier() != null) {
                return new javafx.beans.property.SimpleStringProperty(
                        cellData.getValue().getPreferredSupplier().getCompanyName());
            }
            return new javafx.beans.property.SimpleStringProperty("N/A");
        });

        // Status column with color coding
        statusColumn.setCellValueFactory(cellData -> {
            InventoryItem item = cellData.getValue();
            BigDecimal current = item.getCurrentStock() != null ? item.getCurrentStock() : BigDecimal.ZERO;
            BigDecimal min = item.getMinThreshold() != null ? item.getMinThreshold() : BigDecimal.ZERO;

            if (current.compareTo(BigDecimal.ZERO) <= 0) {
                return new javafx.beans.property.SimpleStringProperty("OUT OF STOCK");
            } else if (current.compareTo(min) <= 0) {
                return new javafx.beans.property.SimpleStringProperty("LOW STOCK");
            } else {
                return new javafx.beans.property.SimpleStringProperty("IN STOCK");
            }
        });

        statusColumn.setCellFactory(column -> new TableCell<InventoryItem, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.equals("OUT OF STOCK")) {
                        setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold;");
                    } else if (item.equals("LOW STOCK")) {
                        setStyle("-fx-background-color: #e67e22; -fx-text-fill: white; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold;");
                    }
                }
            }
        });

        // Load data
        loadInventory();
        updateStats();

        // Double click to edit
        inventoryTable.setRowFactory(tv -> {
            TableRow<InventoryItem> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    handleEditItem();
                }
            });
            return row;
        });
    }

    private void loadInventory() {
        allItems = inventoryRepository.findAll();
        applyCurrentFilter();
    }

    private void applyCurrentFilter() {
        List<InventoryItem> filteredItems = allItems;

        if (lowStockRadio.isSelected()) {
            filteredItems = allItems.stream()
                    .filter(item -> {
                        BigDecimal current = item.getCurrentStock() != null ? item.getCurrentStock() : BigDecimal.ZERO;
                        BigDecimal min = item.getMinThreshold() != null ? item.getMinThreshold() : BigDecimal.ZERO;
                        return current.compareTo(BigDecimal.ZERO) > 0 && current.compareTo(min) <= 0;
                    })
                    .collect(Collectors.toList());
        } else if (outOfStockRadio.isSelected()) {
            filteredItems = allItems.stream()
                    .filter(item -> {
                        BigDecimal current = item.getCurrentStock() != null ? item.getCurrentStock() : BigDecimal.ZERO;
                        return current.compareTo(BigDecimal.ZERO) <= 0;
                    })
                    .collect(Collectors.toList());
        }

        ObservableList<InventoryItem> observableItems = FXCollections.observableArrayList(filteredItems);
        inventoryTable.setItems(observableItems);
    }

    private void updateStats() {
        int total = allItems.size();
        long lowStock = allItems.stream()
                .filter(item -> {
                    BigDecimal current = item.getCurrentStock() != null ? item.getCurrentStock() : BigDecimal.ZERO;
                    BigDecimal min = item.getMinThreshold() != null ? item.getMinThreshold() : BigDecimal.ZERO;
                    return current.compareTo(BigDecimal.ZERO) > 0 && current.compareTo(min) <= 0;
                })
                .count();
        long outOfStock = allItems.stream()
                .filter(item -> {
                    BigDecimal current = item.getCurrentStock() != null ? item.getCurrentStock() : BigDecimal.ZERO;
                    return current.compareTo(BigDecimal.ZERO) <= 0;
                })
                .count();

        totalItemsLabel.setText(String.valueOf(total));
        lowStockCountLabel.setText(String.valueOf(lowStock));
        outOfStockCountLabel.setText(String.valueOf(outOfStock));
    }

    @FXML
    private void handleFilterChange() {
        applyCurrentFilter();
    }

    @FXML
    private void handleSearch() {
        String searchTerm = searchField.getText().trim().toLowerCase();

        if (searchTerm.isEmpty()) {
            applyCurrentFilter();
            return;
        }

        List<InventoryItem> searchResults = allItems.stream()
                .filter(item -> item.getItemName().toLowerCase().contains(searchTerm))
                .collect(Collectors.toList());

        ObservableList<InventoryItem> observableItems = FXCollections.observableArrayList(searchResults);
        inventoryTable.setItems(observableItems);
    }

    @FXML
    private void handleRefresh() {
        searchField.clear();
        allItemsRadio.setSelected(true);
        loadInventory();
        updateStats();
    }

    @FXML
    private void handleAddItem() {
        showItemForm(null);
    }

    @FXML
    private void handleEditItem() {
        InventoryItem selected = inventoryTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            showItemForm(selected);
        } else {
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Please select an item to edit.");
            alert.showAndWait();
        }
    }

    private void showItemForm(InventoryItem item) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/fxml/inventory_item_form.fxml"));
            loader.setControllerFactory(applicationContext::getBean);
            Parent root = loader.load();

            InventoryItemFormController controller = loader.getController();
            controller.setInventoryItem(item);
            controller.setOnSaveCallback(this::handleRefresh);

            Stage stage = new Stage();
            stage.setTitle(item == null ? "New Item" : "Edit Item");
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(inventoryTable.getScene().getWindow());
            stage.setScene(new Scene(root));
            stage.showAndWait();

        } catch (IOException e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR, "Error opening form: " + e.getMessage());
            alert.showAndWait();
        }
    }

    @FXML
    private void handleQuickAdjustment() {
        InventoryItem selected = inventoryTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Please select an item.");
            alert.showAndWait();
            return;
        }

        TextInputDialog dialog = new TextInputDialog(
                selected.getCurrentStock() != null ? selected.getCurrentStock().toPlainString() : "0");
        dialog.setTitle("Quick Stock Adjustment");
        dialog.setHeaderText("Adjust stock for: " + selected.getItemName());
        dialog.setContentText("New Stock Level:");

        dialog.showAndWait().ifPresent(result -> {
            try {
                BigDecimal newStock = new BigDecimal(result);
                selected.setCurrentStock(newStock);
                inventoryRepository.save(selected);
                handleRefresh();
            } catch (NumberFormatException e) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Invalid number format.");
                alert.showAndWait();
            }
        });
    }

    @FXML
    private void handleDeleteItem() {
        InventoryItem selected = inventoryTable.getSelectionModel().getSelectedItem();
        if (selected == null)
            return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Are you sure you want to delete '" + selected.getItemName() + "'?");
        if (alert.showAndWait().get() == ButtonType.OK) {
            inventoryRepository.delete(selected);
            handleRefresh();
        }
    }

    @FXML
    private void handleManageSuppliers() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/fxml/supplier_management.fxml"));
            loader.setControllerFactory(applicationContext::getBean);
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Supplier Management");
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(inventoryTable.getScene().getWindow());
            stage.setScene(new Scene(root));
            stage.showAndWait();

            // Suppliers might have changed, so refresh just in case (though mainly affects
            // dropdowns)
            handleRefresh();

        } catch (IOException e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR, "Error opening suppliers: " + e.getMessage());
            alert.showAndWait();
        }
    }

    @FXML
    private void handleClose() {
        com.qdc.lims.ui.util.ViewCloseUtil.closeCurrentTabOrWindow(inventoryTable);
    }
}
