package com.qdc.lims.ui.controller;

import com.qdc.lims.entity.Department;
import com.qdc.lims.entity.TestCategory;
import com.qdc.lims.entity.TestDefinition;
import com.qdc.lims.service.TestDefinitionService;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Controller for browsing, searching, and maintaining {@link TestDefinition}
 * master data.
 */
@Component
public class TestDefinitionsController {

    @FXML
    private TextField searchField;
    @FXML
    private TableView<TestDefinition> testTable;
    @FXML
    private TableColumn<TestDefinition, String> colTestName;
    @FXML
    private TableColumn<TestDefinition, String> colShortCode;
    @FXML
    private TableColumn<TestDefinition, String> colDepartment;
    @FXML
    private TableColumn<TestDefinition, String> colCategory;
    @FXML
    private TableColumn<TestDefinition, String> colUnit;
    @FXML
    private TableColumn<TestDefinition, BigDecimal> colPrice;
    @FXML
    private TableColumn<TestDefinition, Void> colActions;
    @FXML
    private Label statusLabel;

    @Autowired
    private TestDefinitionService testDefinitionService;
    @Autowired
    private ApplicationContext applicationContext;

    /**
     * Initializes table bindings and loads initial data.
     */
    @FXML
    public void initialize() {
        if (testDefinitionService == null) {
            statusLabel.setText("Error: Service not injected");
            return;
        }
        colDepartment.setCellValueFactory(cellData -> {
            Department dept = cellData.getValue().getDepartment();
            return new SimpleObjectProperty<>(dept != null ? dept.getName() : "");
        });
        colCategory.setCellValueFactory(cellData -> {
            TestCategory category = cellData.getValue().getCategory();
            return new SimpleObjectProperty<>(category != null ? category.getName() : "");
        });
        colUnit.setCellValueFactory(cellData -> new SimpleObjectProperty<>(
                cellData.getValue().getUnit() != null ? cellData.getValue().getUnit() : ""));
        setupActionsColumn();
        loadTests();
    }

    /**
     * Loads all tests into the table.
     */
    private void loadTests() {
        try {
            ObservableList<TestDefinition> tests = FXCollections.observableArrayList(testDefinitionService.findAll());
            testTable.setItems(tests);
            statusLabel.setText("Loaded " + tests.size() + " tests.");
        } catch (Exception e) {
            statusLabel.setText("Error loading tests: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Configures the actions column with edit and delete buttons.
     */
    private void setupActionsColumn() {
        colActions.setCellFactory(param -> new TableCell<>() {
            private final Button editBtn = new Button("Edit");
            private final Button deleteBtn = new Button("Delete");
            private final Button rangesBtn = new Button("Ranges");
            private final HBox pane = new HBox(5, editBtn, rangesBtn, deleteBtn);

            {
                editBtn.setOnAction(event -> handleEdit(getTableView().getItems().get(getIndex())));
                deleteBtn.setOnAction(event -> handleDelete(getTableView().getItems().get(getIndex())));
                deleteBtn.setStyle("-fx-text-fill: red;");
                rangesBtn.setOnAction(event -> handleManageRanges(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(pane);
                }
            }
        });
    }

    private void handleManageRanges(TestDefinition test) {
        try {
            var loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/reference_ranges.fxml"));
            loader.setControllerFactory(applicationContext::getBean);
            Parent root = loader.load();
            ReferenceRangeController controller = loader.getController();
            controller.setTestDefinition(test);

            Stage stage = new Stage();
            stage.setTitle("Reference Ranges - " + test.getTestName());
            stage.setScene(new Scene(root));
            stage.show();
        } catch (Exception e) {
            Alert error = new Alert(Alert.AlertType.ERROR);
            error.setContentText("Could not open Reference Ranges: " + e.getMessage());
            error.show();
        }
    }

    /**
     * Performs a search using the current query text.
     */
    @FXML
    private void handleSearch() {
        String query = searchField.getText();
        if (query == null || query.isBlank()) {
            loadTests();
            return;
        }
        try {
            ObservableList<TestDefinition> tests = FXCollections
                    .observableArrayList(testDefinitionService.searchTests(query));
            testTable.setItems(tests);
            statusLabel.setText("Found " + tests.size() + " matches.");
        } catch (Exception e) {
            statusLabel.setText("Error searching: " + e.getMessage());
        }
    }

    /**
     * Clears filters and reloads all tests.
     */
    @FXML
    private void handleRefresh() {
        searchField.clear();
        loadTests();
    }

    /**
     * Opens the new-test dialog.
     */
    @FXML
    private void handleNewTest() {
        showTestDialog(new TestDefinition());
    }

    /**
     * Closes the window hosting the controller.
     */
    @FXML
    private void handleClose() {
        com.qdc.lims.ui.util.ViewCloseUtil.closeCurrentTabOrWindow(testTable);
    }

    /**
     * Opens the edit dialog for an existing test.
     */
    private void handleEdit(TestDefinition test) {
        showTestDialog(test);
    }

    /**
     * Deletes a test after user confirmation.
     */
    private void handleDelete(TestDefinition test) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Test");
        alert.setHeaderText("Delete " + test.getTestName() + "?");
        alert.setContentText("Are you sure? This cannot be undone.");

        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                testDefinitionService.deleteById(test.getId());
                loadTests();
                statusLabel.setText("Deleted test: " + test.getTestName());
            } catch (Exception e) {
                Alert error = new Alert(Alert.AlertType.ERROR);
                error.setContentText("Could not delete test: " + e.getMessage());
                error.show();
            }
        }
    }

    /**
     * Shows the create/edit dialog and persists the result.
     */
    private void showTestDialog(TestDefinition test) {
        Dialog<TestDefinition> dialog = new Dialog<>();
        dialog.setTitle(test.getId() == null ? "New Test" : "Edit Test");
        dialog.setHeaderText(null);

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField name = new TextField(test.getTestName());
        TextField code = new TextField(test.getShortCode());
        ComboBox<Department> deptCombo = new ComboBox<>();
        deptCombo.setItems(FXCollections.observableArrayList(testDefinitionService.findAllDepartments()));
        deptCombo.setValue(test.getDepartment());
        deptCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Department department) {
                return department != null ? department.getName() : "";
            }

            @Override
            public Department fromString(String string) {
                return deptCombo.getItems().stream()
                        .filter(dept -> dept.getName().equalsIgnoreCase(string))
                        .findFirst()
                        .orElse(null);
            }
        });
        ComboBox<TestCategory> categoryCombo = new ComboBox<>();
        categoryCombo.setEditable(true);
        categoryCombo.setItems(FXCollections.observableArrayList(
                testDefinitionService.findCategoriesByDepartment(test.getDepartment())));
        categoryCombo.setValue(test.getCategory());
        categoryCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(TestCategory category) {
                return category != null ? category.getName() : "";
            }

            @Override
            public TestCategory fromString(String string) {
                return categoryCombo.getItems().stream()
                        .filter(cat -> cat.getName().equalsIgnoreCase(string))
                        .findFirst()
                        .orElse(null);
            }
        });
        deptCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            categoryCombo.getItems().setAll(testDefinitionService.findCategoriesByDepartment(newVal));
            categoryCombo.setValue(null);
        });
        TextField price = new TextField(test.getPrice() != null ? test.getPrice().toPlainString() : "");
        TextField unit = new TextField(test.getUnit());

        grid.add(new Label("Test Name:"), 0, 0);
        grid.add(name, 1, 0);
        grid.add(new Label("Short Code:"), 0, 1);
        grid.add(code, 1, 1);
        grid.add(new Label("Department:"), 0, 2);
        grid.add(deptCombo, 1, 2);
        grid.add(new Label("Category:"), 0, 3);
        grid.add(categoryCombo, 1, 3);
        grid.add(new Label("Unit:"), 0, 4);
        grid.add(unit, 1, 4);
        grid.add(new Label("Price:"), 0, 5);
        grid.add(price, 1, 5);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                test.setTestName(name.getText());
                test.setShortCode(code.getText());
                Department selectedDept = deptCombo.getValue();
                test.setDepartment(selectedDept);
                String categoryName = categoryCombo.getValue() != null
                        ? categoryCombo.getValue().getName()
                        : categoryCombo.getEditor().getText();
                TestCategory resolvedCategory = testDefinitionService.findOrCreateCategory(categoryName, selectedDept);
                test.setCategory(resolvedCategory);
                test.setUnit(unit.getText() != null ? unit.getText().trim() : null);
                try {
                    if (!price.getText().isEmpty()) {
                        test.setPrice(new java.math.BigDecimal(price.getText()));
                    }
                } catch (NumberFormatException e) {
                    // Ignore invalid price input and leave the prior value.
                }
                return test;
            }
            return null;
        });

        Optional<TestDefinition> result = dialog.showAndWait();

        result.ifPresent(t -> {
            try {
                testDefinitionService.save(t);
                loadTests();
                statusLabel.setText("Saved test: " + t.getTestName());
            } catch (Exception e) {
                Alert error = new Alert(Alert.AlertType.ERROR);
                error.setContentText("Could not save test: " + e.getMessage());
                error.show();
            }
        });
    }
}
