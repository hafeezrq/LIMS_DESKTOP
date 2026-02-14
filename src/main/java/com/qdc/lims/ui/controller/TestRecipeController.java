package com.qdc.lims.ui.controller;

import com.qdc.lims.entity.InventoryItem;
import com.qdc.lims.entity.TestConsumption;
import com.qdc.lims.entity.TestDefinition;
import com.qdc.lims.repository.InventoryItemRepository;
import com.qdc.lims.repository.TestConsumptionRepository;
import com.qdc.lims.service.TestDefinitionService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.StringConverter;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Controller for defining per-test inventory consumption recipes.
 */
@Component
@Scope("prototype")
public class TestRecipeController {

    @FXML
    private Label testNameLabel;
    @FXML
    private ComboBox<TestDefinition> testDefinitionComboBox;
    @FXML
    private TableView<TestConsumption> recipeTable;
    @FXML
    private TableColumn<TestConsumption, String> itemNameColumn;
    @FXML
    private TableColumn<TestConsumption, BigDecimal> quantityColumn;
    @FXML
    private TableColumn<TestConsumption, String> unitColumn;

    @FXML
    private ComboBox<InventoryItem> inventoryItemComboBox;
    @FXML
    private TextField quantityField;
    @FXML
    private Label statusLabel;

    private final TestConsumptionRepository consumptionRepository;
    private final InventoryItemRepository inventoryRepository;
    private final TestDefinitionService testDefinitionService;

    private TestDefinition currentTest;

    /**
     * Creates the controller.
     */
    public TestRecipeController(TestConsumptionRepository consumptionRepository,
            InventoryItemRepository inventoryRepository,
            TestDefinitionService testDefinitionService) {
        this.consumptionRepository = consumptionRepository;
        this.inventoryRepository = inventoryRepository;
        this.testDefinitionService = testDefinitionService;
    }

    /**
     * Initializes table bindings, converters, and loads reference data.
     */
    @FXML
    public void initialize() {
        itemNameColumn.setCellValueFactory(
                cell -> new SimpleStringProperty(cell.getValue().getItem().getItemName()));
        quantityColumn.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        unitColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getItem().getUnit()));

        testDefinitionComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(TestDefinition test) {
                if (test == null) {
                    return null;
                }
                String dept = test.getDepartment() != null ? test.getDepartment().getName() : "No Dept";
                return test.getTestName() + " (" + dept + ")";
            }

            @Override
            public TestDefinition fromString(String string) {
                return null;
            }
        });
        testDefinitionComboBox.valueProperty().addListener((obs, oldVal, newVal) -> setTestDefinition(newVal));

        inventoryItemComboBox.setConverter(new StringConverter<InventoryItem>() {
            @Override
            public String toString(InventoryItem item) {
                return item == null ? null
                        : item.getItemName() + " ("
                                + (item.getCurrentStock() != null ? item.getCurrentStock().toPlainString() : "0")
                                + " " + item.getUnit() + ")";
            }

            @Override
            public InventoryItem fromString(String string) {
                return null;
            }
        });

        loadTests();
        loadInventoryItems();
        if (statusLabel != null) {
            statusLabel.setText("");
        }
    }

    /**
     * Sets the current test definition and reloads its recipe.
     *
     * @param test selected test
     */
    public void setTestDefinition(TestDefinition test) {
        this.currentTest = test;
        if (test != null) {
            testNameLabel.setText("Test: " + test.getTestName());
            loadRecipes();
            setStatus("Loaded recipe items for " + test.getTestName() + ".");
        } else {
            testNameLabel.setText("Test: (select a test)");
            recipeTable.getItems().clear();
            setStatus("Select a test definition to manage its recipe.");
        }
    }

    private void loadTests() {
        testDefinitionComboBox.setItems(
                FXCollections.observableArrayList(testDefinitionService.findAll().stream()
                        .filter(t -> t.getActive() == null || t.getActive())
                        .sorted(java.util.Comparator.comparing(TestDefinition::getTestName, String.CASE_INSENSITIVE_ORDER))
                        .toList()));
    }

    private void loadInventoryItems() {
        inventoryItemComboBox.setItems(FXCollections.observableArrayList(
                inventoryRepository.findAll().stream()
                        .filter(InventoryItem::isActive)
                        .sorted(java.util.Comparator.comparing(InventoryItem::getItemName, String.CASE_INSENSITIVE_ORDER))
                        .toList()));
    }

    private void loadRecipes() {
        if (currentTest == null) {
            return;
        }
        recipeTable.setItems(FXCollections.observableArrayList(consumptionRepository.findByTestId(currentTest.getId())));
    }

    /**
     * Adds or updates a recipe item for the selected test.
     */
    @FXML
    private void handleAdd() {
        if (currentTest == null) {
            return;
        }

        InventoryItem selectedItem = inventoryItemComboBox.getValue();
        if (selectedItem == null) {
            showAlert("Required", "Please select an inventory item.");
            return;
        }

        String qtyStr = quantityField.getText().trim();
        BigDecimal quantity;
        try {
            quantity = new BigDecimal(qtyStr);
        } catch (NumberFormatException e) {
            showAlert("Invalid Input", "Quantity must be a valid number.");
            return;
        }

        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            showAlert("Invalid Input", "Quantity must be greater than 0.");
            return;
        }

        TestConsumption recipe = consumptionRepository.findByTestAndItem(currentTest, selectedItem)
                .orElseGet(TestConsumption::new);
        recipe.setTest(currentTest);
        recipe.setItem(selectedItem);
        recipe.setQuantity(quantity);
        consumptionRepository.save(recipe);

        quantityField.clear();
        inventoryItemComboBox.getSelectionModel().clearSelection();
        loadRecipes();
        setStatus("Saved recipe item: " + selectedItem.getItemName());
    }

    /**
     * Removes the selected recipe item after confirmation.
     */
    @FXML
    private void handleRemove() {
        TestConsumption selected = recipeTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Selection Error", "Please select a recipe item to remove.");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Remove");
        alert.setHeaderText("Remove " + selected.getItem().getItemName() + " from recipe?");

        if (alert.showAndWait().get() == ButtonType.OK) {
            consumptionRepository.delete(selected);
            loadRecipes();
            setStatus("Removed recipe item.");
        }
    }

    /**
     * Closes the window.
     */
    @FXML
    private void handleClose() {
        com.qdc.lims.ui.util.ViewCloseUtil.closeCurrentTabOrWindow(recipeTable);
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void setStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
        }
    }
}
