package com.qdc.lims.ui.controller;

import com.qdc.lims.dto.OrderRequest;
import com.qdc.lims.entity.Doctor;
import com.qdc.lims.entity.LabOrder;
import com.qdc.lims.entity.Panel;
import com.qdc.lims.entity.Patient;
import com.qdc.lims.entity.TestDefinition;
import com.qdc.lims.repository.DoctorRepository;
import com.qdc.lims.repository.PatientRepository;
import com.qdc.lims.repository.PanelRepository;
import com.qdc.lims.repository.TestDefinitionRepository;
import com.qdc.lims.service.LocaleFormatService;
import com.qdc.lims.service.OrderService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * JavaFX controller for creating lab orders.
 * Supports category-based test selection with checkboxes.
 */
@Component("createOrderController")
public class CreateOrderController {

    @FXML
    private TextField patientSearchField;

    @FXML
    private VBox patientInfoBox;

    @FXML
    private Label patientNameLabel;

    @FXML
    private Label patientDetailsLabel;

    @FXML
    private ComboBox<Doctor> doctorComboBox;

    @FXML
    private TabPane categoryTabPane;

    @FXML
    private Label selectedTestsCountLabel;

    @FXML
    private Label totalAmountLabel;

    @FXML
    private TextField discountField;

    @FXML
    private TextField cashPaidField;

    @FXML
    private Label balanceLabel;

    @FXML
    private Label messageLabel;

    @FXML
    private ListView<String> selectedTestsListView;

    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final TestDefinitionRepository testRepository;
    private final PanelRepository panelRepository;
    private final OrderService orderService;
    private final LocaleFormatService localeFormatService;

    private Patient selectedPatient;
    // Map to track selected tests across all categories
    private final Map<Long, CheckBox> testCheckboxMap = new HashMap<>();
    private final Set<TestDefinition> selectedTests = new HashSet<>();

    // Map to track selected panels
    private final Map<Integer, CheckBox> panelCheckboxMap = new HashMap<>();
    private final Set<Integer> selectedPanelIds = new HashSet<>();
    private final Map<Integer, Panel> panelsById = new HashMap<>();
    private final Map<Integer, List<TestDefinition>> panelTestsMap = new HashMap<>();
    private final Map<Long, Set<Integer>> testPanelMembership = new HashMap<>();
    private boolean bulkSelection = false;

    private ObservableList<String> selectedTestNames = FXCollections.observableArrayList();

    public CreateOrderController(PatientRepository patientRepository,
            DoctorRepository doctorRepository,
            TestDefinitionRepository testRepository,
            PanelRepository panelRepository,
            OrderService orderService,
            LocaleFormatService localeFormatService) {
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.testRepository = testRepository;
        this.panelRepository = panelRepository;
        this.orderService = orderService;
        this.localeFormatService = localeFormatService;
    }

    @FXML
    private void initialize() {
        // Load doctors
        loadDoctors();

        // Load tests into category tabs
        loadTestsIntoCategoryTabs();

        // Add listeners for billing calculation
        discountField.textProperty().addListener((obs, old, newVal) -> calculateBalance(computeTotalAmount()));
        cashPaidField.textProperty().addListener((obs, old, newVal) -> calculateBalance(computeTotalAmount()));

        selectedTestsListView.setItems(selectedTestNames);

        messageLabel.setText("");
        totalAmountLabel.setText(localeFormatService.formatCurrency(java.math.BigDecimal.ZERO));
        balanceLabel.setText(localeFormatService.formatCurrency(java.math.BigDecimal.ZERO));

        patientSearchField.setOnAction(event -> handleSearchPatient());
    }

    /**
     * Pre-select a patient (used when coming from registration)
     */
    public void setPreselectedPatient(Patient patient) {
        if (patient != null) {
            selectedPatient = patient;
            patientSearchField.setText(patient.getMrn());

            // Display patient info
            patientNameLabel.setText(patient.getFullName() + " (MRN: " + patient.getMrn() + ")");
            patientDetailsLabel.setText(
                    "Age: " + patient.getAge() + " | " +
                            "Gender: " + patient.getGender() + " | " +
                            "Mobile: " + (patient.getMobileNumber() != null ? patient.getMobileNumber() : "N/A") + " | "
                            +
                            "City: " + (patient.getCity() != null ? patient.getCity() : "N/A"));

            patientInfoBox.setVisible(true);
            patientInfoBox.setManaged(true);
        }
    }

    private void loadDoctors() {
        List<Doctor> doctors = doctorRepository.findAll();

        // Add a "None" option
        Doctor noneDoctor = new Doctor();
        noneDoctor.setId(null);
        noneDoctor.setName("-- None / Self --");

        ObservableList<Doctor> doctorList = FXCollections.observableArrayList(noneDoctor);
        doctorList.addAll(doctors);

        doctorComboBox.setItems(doctorList);
        doctorComboBox.setValue(noneDoctor);

        // Custom display - only show doctor name (commission is confidential)
        doctorComboBox.setConverter(new StringConverter<Doctor>() {
            @Override
            public String toString(Doctor doctor) {
                if (doctor == null)
                    return "";
                return doctor.getName();
            }

            @Override
            public Doctor fromString(String string) {
                return null;
            }
        });
    }

    /**
     * Load tests into category tabs with checkboxes.
     * Each category gets its own tab with a scrollable list of test checkboxes.
     */
    private void loadTestsIntoCategoryTabs() {
        List<TestDefinition> allTests = testRepository.findAll().stream()
                .filter(test -> Boolean.TRUE.equals(test.getActive()))
                .sorted(Comparator.comparing(TestDefinition::getTestName))
                .collect(Collectors.toList());

        List<Panel> allPanels = panelRepository.findAllWithTests();

        Map<String, List<TestDefinition>> testsByDept = allTests.stream()
                .collect(Collectors.groupingBy(
                        test -> test.getDepartment() != null ? test.getDepartment().getName() : "Other",
                        LinkedHashMap::new,
                        Collectors.toList()));

        Map<String, List<Panel>> panelsByDept = allPanels.stream()
                .collect(Collectors.groupingBy(
                        panel -> panel.getDepartment() != null ? panel.getDepartment().getName() : "Other",
                        LinkedHashMap::new,
                        Collectors.toList()));

        categoryTabPane.getTabs().clear();
        testCheckboxMap.clear();
        selectedTests.clear();
        panelCheckboxMap.clear();
        selectedPanelIds.clear();
        panelsById.clear();
        panelTestsMap.clear();
        testPanelMembership.clear();

        for (Panel panel : allPanels) {
            panelsById.put(panel.getId(), panel);
            List<TestDefinition> panelTests = panel.getTests() != null
                    ? panel.getTests().stream()
                            .filter(test -> Boolean.TRUE.equals(test.getActive()))
                            .collect(Collectors.toList())
                    : List.of();
            panelTestsMap.put(panel.getId(), panelTests);
            for (TestDefinition test : panelTests) {
                if (test.getId() == null) {
                    continue;
                }
                testPanelMembership.computeIfAbsent(test.getId(), id -> new HashSet<>()).add(panel.getId());
            }
        }

        for (String dept : testsByDept.keySet()) {
            Tab tab = new Tab(dept);
            tab.setClosable(false);

            TabPane panelTabPane = new TabPane();
            panelTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
            panelTabPane.getStyleClass().add("category-tabs");

            List<Panel> deptPanels = panelsByDept.getOrDefault(dept, List.of());
            Set<Long> testsInPanels = new HashSet<>();

            for (Panel panel : deptPanels) {
                VBox panelContent = new VBox(8);
                panelContent.setPadding(new Insets(10));
                panelContent.setStyle("-fx-background-color: white;");

                HBox panelHeader = new HBox(12);
                panelHeader.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                CheckBox panelCheckBox = new CheckBox("Bill as panel");
                panelCheckBox.setStyle("-fx-font-weight: bold; -fx-cursor: hand;");
                panelCheckboxMap.put(panel.getId(), panelCheckBox);

                Label priceLabel = new Label(buildPanelPriceLabel(panel));
                priceLabel.setStyle("-fx-text-fill: #2c3e50; -fx-font-size: 12;");

                panelHeader.getChildren().addAll(panelCheckBox, priceLabel);
                panelContent.getChildren().add(panelHeader);

                List<TestDefinition> panelTests = panelTestsMap.getOrDefault(panel.getId(), List.of()).stream()
                        .sorted(Comparator.comparing(TestDefinition::getTestName))
                        .collect(Collectors.toList());
                panelContent.getChildren().add(buildGroupedTests(panelTests));

                panelCheckBox.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
                    if (isSelected) {
                        selectedPanelIds.add(panel.getId());
                        bulkSelection = true;
                        for (TestDefinition test : panelTests) {
                            CheckBox testCheckBox = testCheckboxMap.get(test.getId());
                            if (testCheckBox != null) {
                                testCheckBox.setSelected(true);
                            }
                        }
                        bulkSelection = false;
                    } else {
                        selectedPanelIds.remove(panel.getId());
                        bulkSelection = true;
                        for (TestDefinition test : panelTests) {
                            if (test.getId() == null) {
                                continue;
                            }
                            Set<Integer> memberships = testPanelMembership.get(test.getId());
                            boolean selectedInAnotherPanel = memberships != null
                                    && memberships.stream()
                                            .filter(memberPanelId -> !memberPanelId.equals(panel.getId()))
                                            .map(panelCheckboxMap::get)
                                            .anyMatch(cb -> cb != null && cb.isSelected());
                            if (selectedInAnotherPanel) {
                                continue;
                            }

                            CheckBox testCheckBox = testCheckboxMap.get(test.getId());
                            if (testCheckBox != null) {
                                testCheckBox.setSelected(false);
                            }
                        }
                        bulkSelection = false;
                    }
                    updateTotalAmount();
                    updateSelectedTestsListView();
                });

                for (TestDefinition test : panelTests) {
                    if (test.getId() != null) {
                        testsInPanels.add(test.getId());
                    }
                }

                ScrollPane panelScrollPane = new ScrollPane(panelContent);
                panelScrollPane.setFitToWidth(true);
                panelScrollPane.setStyle("-fx-background-color: transparent;");
                Tab panelTab = new Tab(panel.getPanelName(), panelScrollPane);
                panelTab.setClosable(false);
                panelTabPane.getTabs().add(panelTab);
            }

            List<TestDefinition> standaloneTests = testsByDept.get(dept).stream()
                    .filter(test -> !testsInPanels.contains(test.getId()))
                    .collect(Collectors.toList());

            if (!standaloneTests.isEmpty() || deptPanels.isEmpty()) {
                VBox standaloneContent = new VBox(8);
                standaloneContent.setPadding(new Insets(10));
                standaloneContent.setStyle("-fx-background-color: white;");
                standaloneContent.getChildren().add(buildGroupedTests(standaloneTests));

                ScrollPane standaloneScrollPane = new ScrollPane(standaloneContent);
                standaloneScrollPane.setFitToWidth(true);
                standaloneScrollPane.setStyle("-fx-background-color: transparent;");
                String standaloneTitle = deptPanels.isEmpty() ? "Tests" : "Other Tests";
                Tab standaloneTab = new Tab(standaloneTitle, standaloneScrollPane);
                standaloneTab.setClosable(false);
                panelTabPane.getTabs().add(standaloneTab);
            }

            tab.setContent(panelTabPane);
            categoryTabPane.getTabs().add(tab);
        }

        if (categoryTabPane.getTabs().isEmpty()) {
            Tab tab = new Tab("Tests");
            tab.setClosable(false);
            VBox content = new VBox(10);
            content.setPadding(new Insets(20));
            Label title = new Label("No tests are configured yet.");
            title.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
            Label guide = new Label("Admin setup required: add departments, tests, then panels from Admin Dashboard.");
            guide.setWrapText(true);
            guide.setStyle("-fx-text-fill: #7f8c8d;");
            content.getChildren().addAll(title, guide);

            ScrollPane scrollPane = new ScrollPane(content);
            scrollPane.setFitToWidth(true);
            scrollPane.setStyle("-fx-background-color: transparent;");
            tab.setContent(scrollPane);
            categoryTabPane.getTabs().add(tab);
        }
    }

    private VBox buildGroupedTests(List<TestDefinition> tests) {
        VBox container = new VBox(6);
        if (tests == null || tests.isEmpty()) {
            return container;
        }

        List<TestDefinition> sorted = tests.stream()
                .filter(test -> Boolean.TRUE.equals(test.getActive()))
                .sorted(Comparator
                        .comparing((TestDefinition test) -> categoryName(test))
                        .thenComparing(TestDefinition::getTestName))
                .collect(Collectors.toList());

        String currentCategory = null;
        FlowPane currentPane = null;
        for (TestDefinition test : sorted) {
            String category = categoryName(test);
            if (!category.equals(currentCategory)) {
                Label categoryLabel = new Label(category);
                categoryLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #34495e;");
                container.getChildren().add(categoryLabel);

                currentPane = new FlowPane();
                currentPane.setHgap(15);
                currentPane.setVgap(8);
                currentPane.setPadding(new Insets(4, 0, 6, 0));
                container.getChildren().add(currentPane);
                currentCategory = category;
            }
            if (currentPane != null) {
                currentPane.getChildren().add(createTestCheckBox(test));
            }
        }
        return container;
    }

    private CheckBox createTestCheckBox(TestDefinition test) {
        CheckBox checkBox = new CheckBox(test.getTestName());
        checkBox.setStyle("-fx-font-size: 12; -fx-cursor: hand;");
        checkBox.setMinWidth(200);
        checkBox.setMaxWidth(280);
        checkBox.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            if (isSelected) {
                selectedTests.add(test);
            } else {
                selectedTests.remove(test);
                Set<Integer> panels = testPanelMembership.get(test.getId());
                if (panels != null) {
                    for (Integer panelId : panels) {
                        CheckBox panelCheckBox = panelCheckboxMap.get(panelId);
                        if (panelCheckBox != null && panelCheckBox.isSelected()) {
                            panelCheckBox.setSelected(false);
                        }
                    }
                }
            }
            if (!bulkSelection) {
                updateTotalAmount();
                updateSelectedTestsListView();
            }
        });
        if (test.getShortCode() != null && !test.getShortCode().isEmpty()) {
            checkBox.setTooltip(new Tooltip("Code: " + test.getShortCode()));
        }
        if (test.getId() != null) {
            testCheckboxMap.put(test.getId(), checkBox);
        }
        return checkBox;
    }

    private String categoryName(TestDefinition test) {
        if (test != null && test.getCategory() != null && test.getCategory().getName() != null) {
            return test.getCategory().getName();
        }
        return "Other";
    }

    private String buildPanelPriceLabel(Panel panel) {
        if (panel != null && panel.getPrice() != null) {
            return "Panel Price: " + localeFormatService.formatCurrency(panel.getPrice());
        }
        return "Panel Price: N/A";
    }

    @FXML
    private void handleSearchPatient() {
        String searchTerm = patientSearchField.getText().trim();

        if (searchTerm.isEmpty()) {
            showError("Please enter patient MRN or name");
            return;
        }

        Patient patient = null;

        // Try to find by MRN first (exact match)
        if (searchTerm.contains("-") || searchTerm.length() == 6) {
            patient = patientRepository.findByMrn(searchTerm).orElse(null);
        }

        // If not found by MRN, search by name
        if (patient == null) {
            List<Patient> matchingPatients = patientRepository.findAll().stream()
                    .filter(p -> p.getFullName() != null &&
                            p.getFullName().toLowerCase().contains(searchTerm.toLowerCase()))
                    .collect(java.util.stream.Collectors.toList());

            if (matchingPatients.isEmpty()) {
                showError("No patient found with MRN or name: " + searchTerm);
                patientInfoBox.setVisible(false);
                patientInfoBox.setManaged(false);
                selectedPatient = null;
                return;
            } else if (matchingPatients.size() == 1) {
                patient = matchingPatients.get(0);
            } else {
                // Multiple matches - show in dialog
                patient = showPatientSelectionDialog(matchingPatients);
                if (patient == null) {
                    showError("Please select a patient from the list");
                    return;
                }
            }
        }

        // Display patient info
        selectedPatient = patient;
        patientNameLabel.setText(patient.getFullName() + " (MRN: " + patient.getMrn() + ")");
        patientDetailsLabel.setText(
                "Age: " + patient.getAge() + " | " +
                        "Gender: " + patient.getGender() + " | " +
                        "Mobile: " + (patient.getMobileNumber() != null ? patient.getMobileNumber() : "N/A") + " | " +
                        "City: " + (patient.getCity() != null ? patient.getCity() : "N/A"));

        patientInfoBox.setVisible(true);
        patientInfoBox.setManaged(true);
        messageLabel.setText("");
    }

    private void updateTotalAmount() {
        java.math.BigDecimal total = computeTotalAmount();
        selectedTestsCountLabel.setText(String.valueOf(selectedTests.size()));
        totalAmountLabel.setText(localeFormatService.formatCurrency(total));

        calculateBalance(total);
    }

    private java.math.BigDecimal computeTotalAmount() {
        java.math.BigDecimal total = java.math.BigDecimal.ZERO;
        java.util.Set<Long> panelTestIds = getSelectedPanelTestIds();

        for (Integer panelId : selectedPanelIds) {
            Panel panel = panelsById.get(panelId);
            if (panel != null && panel.getPrice() != null) {
                total = total.add(panel.getPrice());
            }
        }

        for (TestDefinition test : selectedTests) {
            if (!panelTestIds.contains(test.getId()) && test.getPrice() != null) {
                total = total.add(test.getPrice());
            }
        }
        return total;
    }

    private java.util.Set<Long> getSelectedPanelTestIds() {
        java.util.Set<Long> panelTestIds = new java.util.HashSet<>();
        for (Integer panelId : selectedPanelIds) {
            Panel panel = panelsById.get(panelId);
            if (panel == null || panel.getPrice() == null) {
                continue;
            }
            List<TestDefinition> tests = panelTestsMap.getOrDefault(panelId, List.of());
            for (TestDefinition test : tests) {
                if (test.getId() != null) {
                    panelTestIds.add(test.getId());
                }
            }
        }
        return panelTestIds;
    }

    private void calculateBalance(java.math.BigDecimal total) {
        java.math.BigDecimal discount = parseAmount(discountField.getText());
        java.math.BigDecimal cashPaid = parseAmount(cashPaidField.getText());
        java.math.BigDecimal balance = total.subtract(discount).subtract(cashPaid);
        balanceLabel.setText(localeFormatService.formatCurrency(balance));

        if (balance.compareTo(java.math.BigDecimal.ZERO) < 0) {
            balanceLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #27ae60;");
        } else if (balance.compareTo(java.math.BigDecimal.ZERO) == 0) {
            balanceLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #95a5a6;");
        } else {
            balanceLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #e74c3c;");
        }
    }

    @FXML
    private void handleCreateOrder() {
        messageLabel.setText("");

        // Validation
        if (selectedPatient == null) {
            showError("Please select a patient first");
            return;
        }

        if (selectedTests.isEmpty()) {
            showError("Please select at least one test");
            return;
        }

        // Get doctor (nullable)
        Doctor selectedDoctor = doctorComboBox.getValue();
        Long doctorId = (selectedDoctor != null && selectedDoctor.getId() != null)
                ? selectedDoctor.getId()
                : null;

        // Get test IDs
        List<Long> testIds = selectedTests.stream().map(TestDefinition::getId).collect(Collectors.toList());
        List<Integer> panelIds = new ArrayList<>(selectedPanelIds);

        // Create order request
        java.math.BigDecimal discount = parseAmount(discountField.getText());
        java.math.BigDecimal cashPaid = parseAmount(cashPaidField.getText());

        OrderRequest request = new OrderRequest(
                selectedPatient.getId(),
                doctorId,
                testIds,
                panelIds,
                discount,
                cashPaid);

        // Create order
        try {
            LabOrder order = orderService.createOrder(request);

            // Show print receipt dialog
            showPrintReceiptDialog(order);

        } catch (Exception e) {
            showError("Failed to create order: " + e.getMessage());
        }
    }

    /**
     * Show print receipt dialog after order creation.
     */
    private void showPrintReceiptDialog(LabOrder order) {
        javafx.scene.control.Dialog<String> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Order Created Successfully!");
        dialog.setHeaderText("Order #" + order.getId() + " created for " + order.getPatient().getFullName());

        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(15);
        content.setPadding(new javafx.geometry.Insets(20));
        content.setAlignment(javafx.geometry.Pos.CENTER);

        // Order summary
        javafx.scene.layout.VBox summaryBox = new javafx.scene.layout.VBox(5);
        summaryBox.setStyle(
                "-fx-background-color: #e8f5e9; -fx-padding: 15; -fx-background-radius: 8; -fx-border-color: #27ae60; -fx-border-radius: 8;");
        summaryBox.getChildren().addAll(
                new Label("Order Created Successfully!"),
                new javafx.scene.control.Separator(),
                new Label("MRN: " + order.getPatient().getMrn()),
                new Label("Tests: " + order.getResults().size()),
                new Label("Total: " + localeFormatService.formatCurrency(order.getTotalAmount())),
                new Label("Discount: " + localeFormatService.formatCurrency(
                        order.getDiscountAmount() != null ? order.getDiscountAmount()
                                : java.math.BigDecimal.ZERO)),
                new Label("Paid: " + localeFormatService.formatCurrency(order.getPaidAmount())),
                new Label("Balance Due: " + localeFormatService.formatCurrency(order.getBalanceDue())));
        summaryBox.getChildren().get(0).setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #27ae60;");

        // Print buttons
        Label printLabel = new Label("Print Receipt:");
        printLabel.setStyle("-fx-font-weight: bold;");

        javafx.scene.layout.HBox buttonBox = new javafx.scene.layout.HBox(10);
        buttonBox.setAlignment(javafx.geometry.Pos.CENTER);

        Button printNormalBtn = new Button("🖨 Normal Print");
        printNormalBtn.getStyleClass().add("btn-primary");
        printNormalBtn.setOnAction(e -> {
            printReceipt(order, "NORMAL");
            dialog.close();
            closeCreateOrderWindow();
        });

        Button printThermalBtn = new Button("🧾 Thermal Print");
        printThermalBtn.getStyleClass().add("btn-purple");
        printThermalBtn.setOnAction(e -> {
            printReceipt(order, "THERMAL");
            dialog.close();
            closeCreateOrderWindow();
        });

        Button skipBtn = new Button("Skip Print");
        skipBtn.getStyleClass().add("btn-secondary");
        skipBtn.setOnAction(e -> {
            dialog.close();
            closeCreateOrderWindow();
        });

        buttonBox.getChildren().addAll(printNormalBtn, printThermalBtn, skipBtn);

        content.getChildren().addAll(summaryBox, printLabel, buttonBox);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        // Load stylesheet for button hover effects
        dialog.getDialogPane().getStylesheets().add(getClass().getResource("/static/styles.css").toExternalForm());

        // Hide the default close button since we have our own buttons
        dialog.getDialogPane().lookupButton(ButtonType.CLOSE).setVisible(false);

        dialog.showAndWait();
    }

    /**
     * Close the Create Order window after successful order creation.
     */
    private void closeCreateOrderWindow() {
        Stage stage = (Stage) patientSearchField.getScene().getWindow();
        stage.close();
    }

    /**
     * Print receipt using system print dialog.
     * Supports both NORMAL (A4/Letter) and THERMAL (58mm/80mm) formats.
     */
    private void printReceipt(LabOrder order, String printerType) {
        // Build receipt content based on printer type
        String receiptText;
        String fontStyle;

        if ("THERMAL".equals(printerType)) {
            receiptText = buildThermalReceipt(order);
            fontStyle = "-fx-font-family: 'Courier New'; -fx-font-size: 9;";
        } else {
            receiptText = buildNormalReceipt(order);
            fontStyle = "-fx-font-family: 'Courier New'; -fx-font-size: 11;";
        }

        // Create printable content
        javafx.scene.text.TextFlow textFlow = new javafx.scene.text.TextFlow();
        javafx.scene.text.Text text = new javafx.scene.text.Text(receiptText);
        text.setStyle(fontStyle);
        textFlow.getChildren().add(text);

        // Set preferred width for thermal (narrow) vs normal (wider)
        if ("THERMAL".equals(printerType)) {
            textFlow.setPrefWidth(200); // ~58-80mm thermal paper width
        }

        // Use system print dialog
        javafx.print.PrinterJob job = javafx.print.PrinterJob.createPrinterJob();
        if (job != null && job.showPrintDialog(patientSearchField.getScene().getWindow())) {
            boolean success = job.printPage(textFlow);
            if (success) {
                job.endJob();
                showSuccess("Receipt sent to printer");
            } else {
                showError("Failed to print receipt");
            }
        }
    }

    /**
     * Build receipt content for normal printers (A4/Letter size).
     */
    private String buildNormalReceipt(LabOrder order) {
        StringBuilder sb = new StringBuilder();
        sb.append("========== LIMS LABORATORY ==========\n");
        sb.append("          RECEIPT / INVOICE\n");
        sb.append("=====================================\n\n");
        sb.append("Receipt #: ").append(order.getId()).append("\n");
        sb.append("Date: ")
                .append(localeFormatService.formatDateTime(order.getOrderDate()))
                .append("\n\n");
        sb.append("--- PATIENT DETAILS ---\n");
        sb.append("Name: ").append(order.getPatient().getFullName()).append("\n");
        sb.append("MRN: ").append(order.getPatient().getMrn()).append("\n");
        sb.append("Age/Gender: ").append(order.getPatient().getAge()).append(" / ")
                .append(order.getPatient().getGender()).append("\n\n");
        java.util.Set<Long> panelTestIds = getPanelTestIds(order);

        if (order.getPanels() != null && !order.getPanels().isEmpty()) {
            sb.append("--- PANELS ---\n");
            for (Panel panel : order.getPanels()) {
                sb.append("* ").append(panel.getPanelName());
                sb.append(" - ").append(localeFormatService.formatCurrency(
                        panel.getPrice() != null ? panel.getPrice() : java.math.BigDecimal.ZERO))
                        .append("\n");
            }
            sb.append("\n");
        }

        sb.append("--- TESTS ORDERED ---\n");
        for (var result : order.getResults()) {
            TestDefinition test = result.getTestDefinition();
            if (test == null || panelTestIds.contains(test.getId())) {
                continue;
            }
            sb.append("* ").append(test.getTestName());
            sb.append(" - ").append(localeFormatService.formatCurrency(
                    test.getPrice() != null ? test.getPrice() : java.math.BigDecimal.ZERO))
                    .append("\n");
        }

        sb.append("\n--- BILLING ---\n");
        sb.append("Subtotal:  ").append(localeFormatService.formatCurrency(order.getTotalAmount())).append("\n");
        if (order.getDiscountAmount() != null
                && order.getDiscountAmount().compareTo(java.math.BigDecimal.ZERO) > 0) {
            sb.append("Discount:  ").append(localeFormatService.formatCurrency(order.getDiscountAmount())).append("\n");
        }
        sb.append("Paid:      ").append(localeFormatService.formatCurrency(order.getPaidAmount())).append("\n");
        sb.append("Balance:   ").append(localeFormatService.formatCurrency(order.getBalanceDue())).append("\n\n");
        sb.append("=====================================\n");
        sb.append("    Thank you for choosing LIMS!\n");
        sb.append("    Results typically ready in 24hrs\n");
        sb.append("=====================================\n");

        return sb.toString();
    }

    /**
     * Build receipt content optimized for thermal printers (58mm/80mm paper).
     * Compact format with shorter lines and condensed layout.
     */
    private String buildThermalReceipt(LabOrder order) {
        StringBuilder sb = new StringBuilder();

        // Header - centered, compact
        sb.append("--------------------------------\n");
        sb.append("       LIMS LABORATORY\n");
        sb.append("         CASH RECEIPT\n");
        sb.append("--------------------------------\n");
        sb.append("Rcpt#: ").append(order.getId()).append("\n");
        sb.append("Date: ")
                .append(localeFormatService.formatDateTime(order.getOrderDate()))
                .append("\n");
        sb.append("--------------------------------\n");

        // Patient - abbreviated
        sb.append("Patient: ").append(truncate(order.getPatient().getFullName(), 20)).append("\n");
        sb.append("MRN: ").append(order.getPatient().getMrn()).append("\n");
        sb.append("Age: ").append(order.getPatient().getAge())
                .append(" | ").append(order.getPatient().getGender()).append("\n");
        sb.append("--------------------------------\n");

        java.util.Set<Long> panelTestIds = getPanelTestIds(order);

        if (order.getPanels() != null && !order.getPanels().isEmpty()) {
            sb.append("PANELS:\n");
            for (Panel panel : order.getPanels()) {
                String panelName = truncate(panel.getPanelName(), 18);
                String price = localeFormatService.formatCurrency(
                        panel.getPrice() != null ? panel.getPrice() : java.math.BigDecimal.ZERO);
                sb.append(String.format("%-18s %8s\n", panelName, price));
            }
            sb.append("--------------------------------\n");
        }

        // Tests - compact format (exclude panel tests)
        sb.append("TESTS:\n");
        for (var result : order.getResults()) {
            TestDefinition test = result.getTestDefinition();
            if (test == null || panelTestIds.contains(test.getId())) {
                continue;
            }
            String testName = truncate(test.getTestName(), 18);
            String price = localeFormatService.formatCurrency(
                    test.getPrice() != null ? test.getPrice() : java.math.BigDecimal.ZERO);
            sb.append(String.format("%-18s %8s\n", testName, price));
        }
        sb.append("--------------------------------\n");

        // Billing - right-aligned amounts
        sb.append(String.format("%-12s %10s\n", "Total:",
                localeFormatService.formatCurrency(order.getTotalAmount())));
        if (order.getDiscountAmount() != null
                && order.getDiscountAmount().compareTo(java.math.BigDecimal.ZERO) > 0) {
            sb.append(String.format("%-12s %10s\n", "Discount:",
                    localeFormatService.formatCurrency(order.getDiscountAmount())));
        }
        sb.append(String.format("%-12s %10s\n", "Paid:",
                localeFormatService.formatCurrency(order.getPaidAmount())));
        sb.append(String.format("%-12s %10s\n", "Balance:",
                localeFormatService.formatCurrency(order.getBalanceDue())));
        sb.append("--------------------------------\n");

        // Footer - compact
        sb.append("   Thank you for choosing LIMS!\n");
        sb.append("  Results ready in 24 hours\n");
        sb.append("--------------------------------\n");
        sb.append("\n\n"); // Extra space for tear-off

        return sb.toString();
    }

    private java.util.Set<Long> getPanelTestIds(LabOrder order) {
        if (order == null || order.getPanels() == null || order.getPanels().isEmpty()) {
            return java.util.Set.of();
        }
        List<Integer> panelIds = order.getPanels().stream()
                .map(Panel::getId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
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

    /**
     * Truncate string to specified length for thermal receipt formatting.
     */
    private String truncate(String str, int maxLength) {
        if (str == null)
            return "";
        return str.length() <= maxLength ? str : str.substring(0, maxLength - 2) + "..";
    }

    @FXML
    private void handleClear() {
        patientSearchField.clear();
        patientInfoBox.setVisible(false);
        patientInfoBox.setManaged(false);
        selectedPatient = null;

        doctorComboBox.getSelectionModel().selectFirst();

        // Clear all test checkboxes
        for (CheckBox checkBox : testCheckboxMap.values()) {
            checkBox.setSelected(false);
        }
        selectedTests.clear();

        // Clear all panel checkboxes
        for (CheckBox checkBox : panelCheckboxMap.values()) {
            checkBox.setSelected(false);
        }
        selectedPanelIds.clear();

        discountField.setText("0");
        cashPaidField.setText("0");

        selectedTestsCountLabel.setText("0");
        totalAmountLabel.setText(localeFormatService.formatCurrency(java.math.BigDecimal.ZERO));
        balanceLabel.setText(localeFormatService.formatCurrency(java.math.BigDecimal.ZERO));

        messageLabel.setText("");
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) patientSearchField.getScene().getWindow();
        stage.close();
    }

    private java.math.BigDecimal parseAmount(String text) {
        return localeFormatService.parseNumber(text);
    }

    private void showError(String message) {
        messageLabel.setText("❌ " + message);
        messageLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
    }

    private void showSuccess(String message) {
        messageLabel.setText("✓ " + message);
        messageLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
    }

    private Patient showPatientSelectionDialog(List<Patient> patients) {
        // Create ListView for better display
        ListView<Patient> listView = new ListView<>();
        listView.getItems().addAll(patients);
        listView.setCellFactory(lv -> new ListCell<Patient>() {
            @Override
            protected void updateItem(Patient patient, boolean empty) {
                super.updateItem(patient, empty);
                if (empty || patient == null) {
                    setText(null);
                } else {
                    setText(String.format("%s (MRN: %s) - Age: %d, Gender: %s",
                            patient.getFullName(),
                            patient.getMrn(),
                            patient.getAge(),
                            patient.getGender()));
                }
            }
        });

        Dialog<Patient> dialog = new Dialog<>();
        dialog.setTitle("Multiple Patients Found");
        dialog.setHeaderText(patients.size() + " patients match your search. Please select one:");
        Label selectionHint = new Label("Double-click a patient or click OK to confirm selection.");
        selectionHint.setStyle("-fx-text-fill: #7f8c8d;");
        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #e74c3c;");

        VBox content = new VBox(10, listView, selectionHint, errorLabel);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setDisable(true);

        listView.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            okButton.setDisable(selected == null);
            errorLabel.setText("");
        });

        okButton.addEventFilter(ActionEvent.ACTION, event -> {
            Patient selected = listView.getSelectionModel().getSelectedItem();
            if (selected == null) {
                errorLabel.setText("Please select a patient.");
                event.consume();
                return;
            }
            if (!confirmPatientSelection(selected)) {
                event.consume();
                return;
            }
            dialog.setResult(selected);
            dialog.close();
        });

        listView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                Patient selected = listView.getSelectionModel().getSelectedItem();
                if (selected != null && confirmPatientSelection(selected)) {
                    dialog.setResult(selected);
                    dialog.close();
                }
            }
        });

        listView.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER) {
                Patient selected = listView.getSelectionModel().getSelectedItem();
                if (selected != null && confirmPatientSelection(selected)) {
                    dialog.setResult(selected);
                    dialog.close();
                }
            }
        });

        dialog.setResultConverter(button -> button == ButtonType.OK ? dialog.getResult() : null);

        return dialog.showAndWait().orElse(null);
    }

    private boolean confirmPatientSelection(Patient patient) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Patient");
        confirm.setHeaderText("Use this patient?");
        String mobile = patient.getMobileNumber() != null ? patient.getMobileNumber() : "N/A";
        String city = patient.getCity() != null ? patient.getCity() : "N/A";
        String cnic = patient.getCnic() != null ? patient.getCnic() : "N/A";
        confirm.setContentText(String.format(
                "%s (MRN: %s)\nAge: %d | Gender: %s\nMobile: %s | City: %s\nCNIC: %s",
                patient.getFullName(),
                patient.getMrn(),
                patient.getAge(),
                patient.getGender(),
                mobile,
                city,
                cnic));
        return confirm.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    // Call this method whenever tests are added/removed
    private void updateSelectedTestsListView() {
        List<String> testNames = selectedTests.stream()
                .map(TestDefinition::getTestName)
                .collect(Collectors.toList());
        selectedTestNames.setAll(testNames);
    }
}
