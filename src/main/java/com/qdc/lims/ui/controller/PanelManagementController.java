package com.qdc.lims.ui.controller;

import com.qdc.lims.entity.Department;
import com.qdc.lims.entity.Panel;
import com.qdc.lims.entity.TestDefinition;
import com.qdc.lims.repository.DepartmentRepository;
import com.qdc.lims.repository.PanelRepository;
import com.qdc.lims.repository.TestDefinitionRepository;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Controller for managing billable test panels and their member tests.
 */
@Component
public class PanelManagementController {

    @FXML
    private TableView<Panel> panelTable;
    @FXML
    private TableColumn<Panel, String> colPanelName;
    @FXML
    private TableColumn<Panel, String> colDepartment;
    @FXML
    private TableColumn<Panel, BigDecimal> colPrice;
    @FXML
    private TableColumn<Panel, String> colActive;
    @FXML
    private TableColumn<Panel, Integer> colTestCount;

    @FXML
    private TextField panelNameField;
    @FXML
    private ComboBox<Department> departmentCombo;
    @FXML
    private TextField panelPriceField;
    @FXML
    private CheckBox activeCheckBox;
    @FXML
    private TextField testSearchField;
    @FXML
    private ListView<TestDefinition> availableTestsListView;
    @FXML
    private Label selectedTestsLabel;
    @FXML
    private Label statusLabel;

    private final PanelRepository panelRepository;
    private final DepartmentRepository departmentRepository;
    private final TestDefinitionRepository testDefinitionRepository;

    private final ObservableList<Panel> panelItems = FXCollections.observableArrayList();
    private final ObservableList<TestDefinition> departmentTests = FXCollections.observableArrayList();
    private final FilteredList<TestDefinition> filteredTests = new FilteredList<>(departmentTests, t -> true);

    private Panel editingPanel;
    private boolean loadingForm = false;

    public PanelManagementController(
            PanelRepository panelRepository,
            DepartmentRepository departmentRepository,
            TestDefinitionRepository testDefinitionRepository) {
        this.panelRepository = panelRepository;
        this.departmentRepository = departmentRepository;
        this.testDefinitionRepository = testDefinitionRepository;
    }

    @FXML
    public void initialize() {
        setupTable();
        setupDepartmentCombo();
        setupTestList();
        wireInteractions();
        loadDepartments();
        loadPanels();
        clearForm();
        if (departmentCombo.getItems().isEmpty()) {
            setStatus("No departments found. Create departments first, then add tests and panels.");
        } else {
            setStatus("Use this screen to create billable panels and map their tests.");
        }
    }

    private void setupTable() {
        colPanelName.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getPanelName()));
        colDepartment.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getDepartment() != null ? cell.getValue().getDepartment().getName() : "Other"));
        colPrice.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue().getPrice()));
        colActive.setCellValueFactory(cell -> new SimpleStringProperty(
                Boolean.TRUE.equals(cell.getValue().getActive()) ? "Yes" : "No"));
        colTestCount.setCellValueFactory(cell -> new SimpleIntegerProperty(
                cell.getValue().getTests() != null ? cell.getValue().getTests().size() : 0).asObject());
        panelTable.setItems(panelItems);
    }

    private void setupDepartmentCombo() {
        departmentCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Department department) {
                return department != null ? department.getName() : "";
            }

            @Override
            public Department fromString(String string) {
                return departmentCombo.getItems().stream()
                        .filter(dept -> dept.getName().equalsIgnoreCase(string))
                        .findFirst()
                        .orElse(null);
            }
        });
    }

    private void setupTestList() {
        availableTestsListView.setItems(filteredTests);
        availableTestsListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        availableTestsListView.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(TestDefinition item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                String code = item.getShortCode() != null && !item.getShortCode().isBlank()
                        ? item.getShortCode().trim() + " - "
                        : "";
                String category = item.getCategory() != null && item.getCategory().getName() != null
                        ? " [" + item.getCategory().getName() + "]"
                        : "";
                setText(code + item.getTestName() + category);
            }
        });
    }

    private void wireInteractions() {
        panelTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadPanelIntoForm(newVal);
            }
        });

        departmentCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            loadTestsForDepartment(newVal);
            if (!loadingForm) {
                availableTestsListView.getSelectionModel().clearSelection();
            }
            updateSelectedTestsLabel();
        });

        testSearchField.textProperty().addListener((obs, oldVal, newVal) -> applyTestFilter(newVal));
        availableTestsListView.getSelectionModel().getSelectedItems().addListener(
                (javafx.collections.ListChangeListener<? super TestDefinition>) change -> updateSelectedTestsLabel());
    }

    private void loadDepartments() {
        List<Department> departments = departmentRepository.findAll().stream()
                .filter(dept -> dept.getActive() == null || dept.getActive())
                .sorted(Comparator.comparing(Department::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        departmentCombo.setItems(FXCollections.observableArrayList(departments));
    }

    private void loadPanels() {
        List<Panel> panels = panelRepository.findAllWithTestsIncludingInactive().stream()
                .sorted(Comparator
                        .comparing((Panel p) -> p.getDepartment() != null ? p.getDepartment().getName() : "Other",
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Panel::getPanelName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        panelItems.setAll(panels);
    }

    private void loadPanelIntoForm(Panel panel) {
        loadingForm = true;
        editingPanel = panel;

        panelNameField.setText(panel.getPanelName());
        departmentCombo.setValue(panel.getDepartment());
        panelPriceField.setText(panel.getPrice() != null ? panel.getPrice().toPlainString() : "");
        activeCheckBox.setSelected(panel.getActive() == null || panel.getActive());

        loadTestsForDepartment(panel.getDepartment());
        applyTestFilter(testSearchField.getText());
        availableTestsListView.getSelectionModel().clearSelection();

        Set<Long> linkedTestIds = new HashSet<>();
        if (panel.getTests() != null) {
            for (TestDefinition test : panel.getTests()) {
                if (test != null && test.getId() != null) {
                    linkedTestIds.add(test.getId());
                }
            }
        }
        for (int i = 0; i < departmentTests.size(); i++) {
            TestDefinition test = departmentTests.get(i);
            if (test.getId() != null && linkedTestIds.contains(test.getId())) {
                availableTestsListView.getSelectionModel().select(i);
            }
        }

        loadingForm = false;
        updateSelectedTestsLabel();
        setStatus("Editing panel: " + panel.getPanelName());
    }

    private void loadTestsForDepartment(Department department) {
        if (department == null) {
            departmentTests.clear();
            return;
        }

        List<TestDefinition> tests = testDefinitionRepository.findByDepartmentAndActiveTrueOrderByTestNameAsc(department)
                .stream()
                .sorted(Comparator
                        .comparing((TestDefinition t) -> t.getCategory() != null && t.getCategory().getName() != null
                                ? t.getCategory().getName()
                                : "Other", String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(TestDefinition::getTestName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        departmentTests.setAll(tests);
    }

    private void applyTestFilter(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase();
        filteredTests.setPredicate(test -> {
            if (normalized.isEmpty()) {
                return true;
            }
            String name = test.getTestName() != null ? test.getTestName().toLowerCase() : "";
            String code = test.getShortCode() != null ? test.getShortCode().toLowerCase() : "";
            String category = test.getCategory() != null && test.getCategory().getName() != null
                    ? test.getCategory().getName().toLowerCase()
                    : "";
            return name.contains(normalized) || code.contains(normalized) || category.contains(normalized);
        });
    }

    @FXML
    private void handleNewPanel() {
        if (departmentCombo.getItems().isEmpty()) {
            showAlert("Missing Department", "Please create at least one Department before creating panels.");
            setStatus("Panel setup blocked: no departments configured.");
            return;
        }
        clearForm();
        setStatus("Enter panel details, select tests, then click Save Panel.");
    }

    @FXML
    private void handleSavePanel() {
        String panelName = panelNameField.getText() != null ? panelNameField.getText().trim() : "";
        Department department = departmentCombo.getValue();
        List<TestDefinition> selectedTests = new ArrayList<>(availableTestsListView.getSelectionModel().getSelectedItems());

        if (panelName.isEmpty()) {
            showAlert("Validation", "Panel name is required.");
            return;
        }
        if (department == null) {
            showAlert("Validation", "Department is required.");
            return;
        }
        BigDecimal panelPrice = parsePrice(panelPriceField.getText());
        if (panelPrice == null || panelPrice.compareTo(BigDecimal.ZERO) <= 0) {
            showAlert("Validation", "Panel price must be a valid number greater than 0.");
            return;
        }
        if (selectedTests.isEmpty()) {
            showAlert("Validation", "Select at least one test for this panel.");
            return;
        }

        Optional<Panel> duplicate = panelRepository.findByPanelNameIgnoreCaseAndDepartment(panelName, department);
        if (duplicate.isPresent() && (editingPanel == null || !duplicate.get().getId().equals(editingPanel.getId()))) {
            showAlert("Validation", "A panel with this name already exists in the selected department.");
            return;
        }

        Panel panelToSave = editingPanel != null
                ? panelRepository.findById(editingPanel.getId()).orElse(new Panel())
                : new Panel();
        panelToSave.setPanelName(panelName);
        panelToSave.setDepartment(department);
        panelToSave.setPrice(panelPrice);
        panelToSave.setActive(activeCheckBox.isSelected());
        panelToSave.setTests(selectedTests);

        Panel saved = panelRepository.save(panelToSave);
        loadPanels();
        selectPanelById(saved.getId());
        setStatus("Panel saved: " + saved.getPanelName());
    }

    @FXML
    private void handleDeletePanel() {
        Panel selected = editingPanel != null ? editingPanel : panelTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Selection", "Please select a panel to delete.");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Panel");
        alert.setHeaderText("Delete panel: " + selected.getPanelName() + "?");
        alert.setContentText("This will remove the panel and its test mappings.");
        if (alert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        panelRepository.deleteById(selected.getId());
        loadPanels();
        clearForm();
        setStatus("Panel deleted.");
    }

    @FXML
    private void handleSelectAllTests() {
        availableTestsListView.getSelectionModel().selectAll();
        updateSelectedTestsLabel();
    }

    @FXML
    private void handleClearTestSelection() {
        availableTestsListView.getSelectionModel().clearSelection();
        updateSelectedTestsLabel();
    }

    @FXML
    private void handleRefresh() {
        Integer selectedId = editingPanel != null ? editingPanel.getId() : null;
        loadDepartments();
        loadPanels();
        if (selectedId != null) {
            selectPanelById(selectedId);
        } else {
            clearForm();
        }
        setStatus("Panel data refreshed.");
    }

    @FXML
    private void handleClose() {
        if (panelTable == null || panelTable.getScene() == null || panelTable.getScene().getRoot() == null) {
            return;
        }

        if (closeContainingTab(panelTable)) {
            return;
        }

        if (panelTable.getScene().getWindow() instanceof javafx.stage.Stage stage) {
            stage.close();
        }
    }

    private boolean closeContainingTab(Node contentNode) {
        List<TabPane> tabPanes = new ArrayList<>();
        collectTabPanes(contentNode.getScene().getRoot(), tabPanes);

        for (TabPane tabPane : tabPanes) {
            for (Tab tab : tabPane.getTabs()) {
                if (!tab.isClosable()) {
                    continue;
                }
                if (isDescendant(contentNode, tab.getContent())) {
                    tabPane.getTabs().remove(tab);
                    return true;
                }
            }
        }
        return false;
    }

    private void collectTabPanes(Node node, List<TabPane> result) {
        if (node == null) {
            return;
        }
        if (node instanceof TabPane pane) {
            result.add(pane);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectTabPanes(child, result);
            }
        }
    }

    private boolean isDescendant(Node node, Node potentialAncestor) {
        if (node == null || potentialAncestor == null) {
            return false;
        }
        if (node == potentialAncestor) {
            return true;
        }
        Parent parent = node.getParent();
        while (parent != null) {
            if (parent == potentialAncestor) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    private void clearForm() {
        loadingForm = true;
        editingPanel = null;
        panelTable.getSelectionModel().clearSelection();
        panelNameField.clear();
        panelPriceField.clear();
        activeCheckBox.setSelected(true);
        testSearchField.clear();
        departmentCombo.getSelectionModel().clearSelection();
        departmentTests.clear();
        availableTestsListView.getSelectionModel().clearSelection();
        loadingForm = false;
        updateSelectedTestsLabel();
    }

    private void selectPanelById(Integer panelId) {
        if (panelId == null) {
            return;
        }
        for (Panel panel : panelItems) {
            if (panelId.equals(panel.getId())) {
                panelTable.getSelectionModel().select(panel);
                panelTable.scrollTo(panel);
                return;
            }
        }
    }

    private BigDecimal parsePrice(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void updateSelectedTestsLabel() {
        int selectedCount = availableTestsListView.getSelectionModel().getSelectedItems().size();
        selectedTestsLabel.setText("Selected tests: " + selectedCount);
    }

    private void setStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
        }
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
