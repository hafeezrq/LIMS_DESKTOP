package com.qdc.lims.ui.controller;

import com.qdc.lims.ui.SessionManager;
import com.qdc.lims.ui.navigation.DashboardType;
import com.qdc.lims.entity.Role;
import com.qdc.lims.entity.User;
import com.qdc.lims.service.AuthService;
import com.qdc.lims.service.BrandingService;
import com.qdc.lims.service.ConfigService;
import com.qdc.lims.service.PasswordPolicyService;
import com.qdc.lims.service.UserService;
import com.qdc.lims.repository.RoleRepository;
import com.qdc.lims.repository.UserRepository;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.Modality;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Controller for the main application window with tabbed interface.
 * This is the primary window that hosts all user sessions as tabs.
 * 
 * Design Pattern: Multi-Document Interface (MDI) with tabs
 * - Single main window stays open
 * - Each logged-in session opens as a new tab
 * - Users can have multiple simultaneous sessions
 * - Closing a tab logs out that session
 */
@Controller
public class MainWindowController {

    private final ApplicationContext applicationContext;
    private final AuthService authService;
    private final BrandingService brandingService;
    private final ConfigService configService;
    private final PasswordPolicyService passwordPolicyService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @FXML
    private BorderPane mainContainer;

    @FXML
    private TabPane sessionTabs;

    @FXML
    private VBox welcomePane;

    @FXML
    private Button receptionLoginBtn;

    @FXML
    private Button labLoginBtn;

    @FXML
    private Button adminLoginBtn;

    @FXML
    private Button exitBtn;

    @FXML
    private Label statusLabel;

    @FXML
    private Label sessionCountLabel;
    @FXML
    private Label activeRoleLabel;
    @FXML
    private Label appHeaderLabel;
    @FXML
    private Label welcomeTitleLabel;
    @FXML
    private Label welcomeSubtitleLabel;
    @FXML
    private Label footerBrandLabel;
    @FXML
    private ImageView welcomeLogoImage;
    @FXML
    private Label welcomeLogoEmoji;

    // Track which tab belongs to which session
    private final Map<Tab, SessionInfo> tabSessions = new HashMap<>();
    private static final String SESSION_TIMEOUT_ENABLED_KEY = "SESSION_TIMEOUT_ENABLED";

    @Value("${qdc.session.timeout:30}")
    private long sessionTimeoutMinutes;

    private Timeline sessionExpiryTimer;
    private boolean firstRunPromptShown;

    // Session info holder
    private static class SessionInfo {
        User user;
        DashboardType dashboardType;
        Instant lastAccess;

        SessionInfo(User user, DashboardType dashboardType) {
            this.user = user;
            this.dashboardType = dashboardType;
            touch();
        }

        void touch() {
            lastAccess = Instant.now();
        }
    }

    public MainWindowController(ApplicationContext applicationContext,
            AuthService authService,
            BrandingService brandingService,
            ConfigService configService,
            PasswordPolicyService passwordPolicyService,
            UserService userService,
            UserRepository userRepository,
            RoleRepository roleRepository) {
        this.applicationContext = applicationContext;
        this.authService = authService;
        this.brandingService = brandingService;
        this.configService = configService;
        this.passwordPolicyService = passwordPolicyService;
        this.userService = userService;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @FXML
    public void initialize() {
        // Clear any stale session data from previous uses (since this controller is a
        // singleton)
        tabSessions.clear();
        sessionTabs.getTabs().clear();

        // Setup hover effects for header buttons
        setupButtonHoverEffect(receptionLoginBtn, "#27ae60", "#219a52");
        setupButtonHoverEffect(labLoginBtn, "#9b59b6", "#8e44ad");
        setupButtonHoverEffect(adminLoginBtn, "#e74c3c", "#c0392b");
        setupButtonHoverEffect(exitBtn, "rgba(255,255,255,0.15)", "rgba(255,255,255,0.25)");

        // Handle tab selection changes - update SessionManager with the selected
        // session's user
        sessionTabs.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null) {
                SessionInfo session = tabSessions.get(newTab);
                if (session != null && session.user != null) {
                    if (isExpired(session)) {
                        expireSessionTab(newTab, session, true);
                        updateButtonStates();
                        return;
                    }
                    session.touch();
                    // Update SessionManager so the selected tab's user is the "current" user
                    Platform.runLater(() -> {
                        Stage stage = (Stage) mainContainer.getScene().getWindow();
                        if (stage != null) {
                            SessionManager.login(stage, session.user);
                            SessionManager.setActiveStage(stage);
                        }
                    });
                }
            }
            updateButtonStates();
        });

        // Handle tab close - logout that session
        sessionTabs.getTabs().addListener((javafx.collections.ListChangeListener<Tab>) change -> {
            while (change.next()) {
                if (change.wasRemoved()) {
                    for (Tab tab : change.getRemoved()) {
                        handleTabClosed(tab);
                    }
                }
            }
            updateUI();
            updateButtonStates(); // Update button availability
        });

        // Initially hide tabs and show welcome
        updateUI();
        updateButtonStates();
        setupWelcomeLogo();
        applyBrandingToLabels();
        setupStageBrandingAndFirstRun();
        setupSessionTimeoutHandlers();
    }

    private void setupWelcomeLogo() {
        if (welcomeLogoImage == null) {
            return;
        }
        try {
            var logoUrl = MainWindowController.class.getResource("/icons/microscope.png");
            if (logoUrl == null) {
                showWelcomeLogoFallback();
                return;
            }
            Image logo = new Image(logoUrl.toExternalForm(), 512, 512, true, true, false);
            if (logo.isError()) {
                showWelcomeLogoFallback();
                return;
            }
            welcomeLogoImage.setImage(removeBackgroundForWelcomeLogo(logo));
            if (welcomeLogoEmoji != null) {
                welcomeLogoEmoji.setVisible(false);
                welcomeLogoEmoji.setManaged(false);
            }
        } catch (Exception ignored) {
            showWelcomeLogoFallback();
        }
    }

    private Image removeBackgroundForWelcomeLogo(Image logo) {
        PixelReader reader = logo.getPixelReader();
        if (reader == null) {
            return logo;
        }

        int width = Math.max(1, (int) Math.round(logo.getWidth()));
        int height = Math.max(1, (int) Math.round(logo.getHeight()));
        if (width < 4 || height < 4) {
            return logo;
        }

        Color backgroundColor = averageCornerColor(reader, width, height);
        boolean[] backgroundMask = new boolean[width * height];
        ArrayDeque<Integer> queue = new ArrayDeque<>();

        seedBackgroundQueue(reader, width, height, backgroundColor, backgroundMask, queue);

        while (!queue.isEmpty()) {
            int index = queue.removeFirst();
            int x = index % width;
            int y = index / width;

            enqueueIfBackground(reader, width, height, x - 1, y, backgroundColor, backgroundMask, queue);
            enqueueIfBackground(reader, width, height, x + 1, y, backgroundColor, backgroundMask, queue);
            enqueueIfBackground(reader, width, height, x, y - 1, backgroundColor, backgroundMask, queue);
            enqueueIfBackground(reader, width, height, x, y + 1, backgroundColor, backgroundMask, queue);
        }

        WritableImage output = new WritableImage(width, height);
        PixelWriter writer = output.getPixelWriter();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                Color pixel = reader.getColor(x, y);
                if (backgroundMask[index]) {
                    writer.setColor(x, y, Color.color(pixel.getRed(), pixel.getGreen(), pixel.getBlue(), 0.0));
                } else {
                    writer.setColor(x, y, pixel);
                }
            }
        }

        return output;
    }

    private void seedBackgroundQueue(
            PixelReader reader,
            int width,
            int height,
            Color backgroundColor,
            boolean[] backgroundMask,
            ArrayDeque<Integer> queue
    ) {
        for (int x = 0; x < width; x++) {
            enqueueIfBackground(reader, width, height, x, 0, backgroundColor, backgroundMask, queue);
            enqueueIfBackground(reader, width, height, x, height - 1, backgroundColor, backgroundMask, queue);
        }
        for (int y = 0; y < height; y++) {
            enqueueIfBackground(reader, width, height, 0, y, backgroundColor, backgroundMask, queue);
            enqueueIfBackground(reader, width, height, width - 1, y, backgroundColor, backgroundMask, queue);
        }
    }

    private void enqueueIfBackground(
            PixelReader reader,
            int width,
            int height,
            int x,
            int y,
            Color backgroundColor,
            boolean[] backgroundMask,
            ArrayDeque<Integer> queue
    ) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return;
        }
        int index = y * width + x;
        if (backgroundMask[index]) {
            return;
        }
        Color pixel = reader.getColor(x, y);
        if (!isBackgroundColor(pixel, backgroundColor)) {
            return;
        }
        backgroundMask[index] = true;
        queue.addLast(index);
    }

    private boolean isBackgroundColor(Color pixel, Color backgroundColor) {
        if (pixel.getOpacity() < 0.02) {
            return true;
        }
        double dr = pixel.getRed() - backgroundColor.getRed();
        double dg = pixel.getGreen() - backgroundColor.getGreen();
        double db = pixel.getBlue() - backgroundColor.getBlue();
        double distance = Math.sqrt((dr * dr) + (dg * dg) + (db * db));
        return distance <= 0.18;
    }

    private Color averageCornerColor(PixelReader reader, int width, int height) {
        int maxX = width - 1;
        int maxY = height - 1;
        Color c1 = reader.getColor(0, 0);
        Color c2 = reader.getColor(maxX, 0);
        Color c3 = reader.getColor(0, maxY);
        Color c4 = reader.getColor(maxX, maxY);
        return Color.color(
                (c1.getRed() + c2.getRed() + c3.getRed() + c4.getRed()) / 4.0,
                (c1.getGreen() + c2.getGreen() + c3.getGreen() + c4.getGreen()) / 4.0,
                (c1.getBlue() + c2.getBlue() + c3.getBlue() + c4.getBlue()) / 4.0
        );
    }

    private void showWelcomeLogoFallback() {
        if (welcomeLogoImage != null) {
            welcomeLogoImage.setImage(null);
        }
        if (welcomeLogoEmoji != null) {
            welcomeLogoEmoji.setVisible(true);
            welcomeLogoEmoji.setManaged(true);
        }
    }

    private void applyBrandingToLabels() {
        String appName = brandingService.getApplicationName();

        if (appHeaderLabel != null) {
            appHeaderLabel.setText("🧪 " + appName);
        }
        if (welcomeTitleLabel != null) {
            welcomeTitleLabel.setText(appName);
        }
        if (welcomeSubtitleLabel != null) {
            welcomeSubtitleLabel.setText("Laboratory Information Management System");
        }
        if (footerBrandLabel != null) {
            footerBrandLabel.setText(brandingService.getCopyrightLine());
        }
    }

    private void setupStageBrandingAndFirstRun() {
        if (mainContainer == null) {
            return;
        }
        mainContainer.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                return;
            }
            newScene.windowProperty().addListener((obs2, oldWindow, newWindow) -> {
                if (newWindow instanceof Stage stage) {
                    brandingService.applyMainStageBranding(stage);
                    if (!firstRunPromptShown) {
                        firstRunPromptShown = true;
                        Platform.runLater(() -> {
                            boolean adminReady = ensureAdminAccount(stage);
                            if (!adminReady) {
                                return;
                            }
                            handleFirstRun(stage);
                        });
                    }
                }
            });
        });
    }

    private void handleFirstRun(Stage owner) {
        configService.refreshCache();
        if (brandingService.isLabProfileComplete()) {
            return;
        }
        boolean introShown = false;
        while (!brandingService.isLabProfileComplete()) {
            if (!introShown) {
                Alert intro = new Alert(Alert.AlertType.INFORMATION);
                intro.setTitle("Welcome");
                intro.setHeaderText("Complete Lab Setup");
                intro.setContentText("Please enter your lab details. These will be used across the system and reports.");
                intro.showAndWait();
                introShown = true;
            }

            openFirstRunSetupDialog(owner);

            configService.refreshCache();
            applyBrandingToLabels();
            brandingService.refreshAllTaggedStageTitles();

            if (brandingService.isLabProfileComplete()) {
                return;
            }

            Alert reminder = new Alert(Alert.AlertType.WARNING);
            reminder.setTitle("Setup Incomplete");
            reminder.setHeaderText("Lab details are still incomplete");
            reminder.setContentText("You must complete lab information before using the system.");
            reminder.showAndWait();

            Alert confirmExit = new Alert(Alert.AlertType.CONFIRMATION);
            confirmExit.setTitle("Exit Application");
            confirmExit.setHeaderText("Exit without completing lab setup?");
            confirmExit.setContentText("Lab information is required to use the system.\n\nExit now?");
            java.util.Optional<javafx.scene.control.ButtonType> result = confirmExit.showAndWait();
            if (result.isPresent() && result.get() == javafx.scene.control.ButtonType.OK) {
                Platform.exit();
                return;
            }
            // If not exiting, loop and re-open lab setup dialog.
        }
    }

    private boolean ensureAdminAccount(Stage owner) {
        if (userRepository.count() > 0) {
            return true;
        }

        boolean introAccepted = false;
        while (userRepository.count() == 0) {
            if (!introAccepted) {
                if (!showAdminIntroOrExit()) {
                    return false;
                }
                introAccepted = true;
            }

            boolean created = showAdminSetupDialog(owner);
            if (created) {
                Alert success = new Alert(Alert.AlertType.INFORMATION);
                success.setTitle("Administrator Created");
                success.setHeaderText("Admin account created");
                success.setContentText("Please log in with the new admin account to continue setup.");
                success.showAndWait();
                return true;
            }

            Alert warning = new Alert(Alert.AlertType.WARNING);
            warning.setTitle("Administrator Required");
            warning.setHeaderText("Setup incomplete");
            warning.setContentText("You must create an administrator account before using the system.");
            warning.showAndWait();

            Alert confirmExit = new Alert(Alert.AlertType.CONFIRMATION);
            confirmExit.setTitle("Exit Application");
            confirmExit.setHeaderText("Exit without administrator setup?");
            confirmExit.setContentText("The application cannot be used until an administrator is created.\n\nExit now?");
            java.util.Optional<javafx.scene.control.ButtonType> result = confirmExit.showAndWait();
            if (result.isPresent() && result.get() == javafx.scene.control.ButtonType.OK) {
                Platform.exit();
                return false;
            }
            // If not exiting, loop and re-open admin setup dialog.
        }

        return true;
    }

    private boolean showAdminIntroOrExit() {
        while (true) {
            Alert intro = new Alert(Alert.AlertType.INFORMATION);
            intro.setTitle("First-Time Setup");
            intro.setHeaderText("Create Administrator Account");
            intro.setContentText("No users exist yet. Please create the administrator account to continue.");
            java.util.Optional<javafx.scene.control.ButtonType> result = intro.showAndWait();
            if (result.isPresent() && result.get() == javafx.scene.control.ButtonType.OK) {
                return true;
            }

            Alert confirmExit = new Alert(Alert.AlertType.CONFIRMATION);
            confirmExit.setTitle("Exit Application");
            confirmExit.setHeaderText("Exit without administrator setup?");
            confirmExit.setContentText("The application cannot be used until an administrator is created.\n\nExit now?");
            java.util.Optional<javafx.scene.control.ButtonType> exitResult = confirmExit.showAndWait();
            if (exitResult.isPresent() && exitResult.get() == javafx.scene.control.ButtonType.OK) {
                Platform.exit();
                return false;
            }
            // If not exiting, re-show the intro until OK is pressed.
        }
    }

    private boolean showAdminSetupDialog(Stage owner) {
        Dialog<User> dialog = new Dialog<>();
        dialog.setTitle("Create Administrator");
        dialog.setHeaderText("Set up the administrator account");
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);

        TextField usernameField = new TextField();
        usernameField.setPromptText("admin");
        TextField fullNameField = new TextField();
        fullNameField.setPromptText("Full name");
        TextField emailField = new TextField();
        emailField.setPromptText("Email (optional)");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        PasswordField confirmField = new PasswordField();
        confirmField.setPromptText("Confirm password");

        Label policyHint = new Label(passwordPolicyService.getPolicyHint());
        policyHint.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 11;");

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #e74c3c;");

        TextArea rolesInfo = new TextArea(buildRolesSummary());
        rolesInfo.setEditable(false);
        rolesInfo.setWrapText(true);
        rolesInfo.setPrefRowCount(6);

        VBox content = new VBox(10,
                new VBox(5, new Label("Username:"), usernameField),
                new VBox(5, new Label("Full Name:"), fullNameField),
                new VBox(5, new Label("Email:"), emailField),
                new VBox(5, new Label("Password:"), passwordField),
                new VBox(5, new Label("Confirm Password:"), confirmField),
                policyHint,
                new Label("Roles and privileges:"),
                rolesInfo,
                errorLabel);
        content.setPadding(new Insets(20));
        dialog.getDialogPane().setContent(content);

        ButtonType createButtonType = new ButtonType("Create Admin", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);

        Button createButton = (Button) dialog.getDialogPane().lookupButton(createButtonType);
        createButton.setDisable(true);

        Runnable validateReady = () -> {
            boolean ready = !usernameField.getText().trim().isEmpty()
                    && !fullNameField.getText().trim().isEmpty()
                    && !passwordField.getText().isEmpty()
                    && !confirmField.getText().isEmpty();
            createButton.setDisable(!ready);
        };

        usernameField.textProperty().addListener((obs, old, val) -> validateReady.run());
        fullNameField.textProperty().addListener((obs, old, val) -> validateReady.run());
        passwordField.textProperty().addListener((obs, old, val) -> validateReady.run());
        confirmField.textProperty().addListener((obs, old, val) -> validateReady.run());

        createButton.addEventFilter(ActionEvent.ACTION, event -> {
            errorLabel.setText("");
            String username = usernameField.getText().trim();
            String fullName = fullNameField.getText().trim();
            String email = emailField.getText().trim();
            String password = passwordField.getText();
            String confirm = confirmField.getText();

            if (!password.equals(confirm)) {
                errorLabel.setText("Passwords do not match.");
                event.consume();
                return;
            }

            if (userRepository.findByUsername(username).isPresent()) {
                errorLabel.setText("Username already exists.");
                event.consume();
                return;
            }

            Role adminRole = roleRepository.findByName("ROLE_ADMIN").orElse(null);
            if (adminRole == null) {
                errorLabel.setText("Admin role is missing. Please restart the app.");
                event.consume();
                return;
            }

            try {
                User admin = new User();
                admin.setUsername(username);
                admin.setFullName(fullName);
                if (!email.isEmpty()) {
                    admin.setEmail(email);
                }
                admin.setPassword(password);
                admin.getRoles().add(adminRole);
                userService.createUser(admin);
                dialog.setResult(admin);
                dialog.close();
            } catch (RuntimeException ex) {
                errorLabel.setText(ex.getMessage());
                event.consume();
            }
        });

        dialog.setResultConverter(button -> button == createButtonType ? dialog.getResult() : null);
        return dialog.showAndWait().isPresent();
    }

    private String buildRolesSummary() {
        List<Role> roles = roleRepository.findAll();
        if (roles.isEmpty()) {
            return "Roles have not been initialized yet.";
        }

        return roles.stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .map(role -> {
                    String name = role.getName().replace("ROLE_", "");
                    String desc = role.getDescription() != null ? role.getDescription() : "No description";
                    return name + ": " + desc;
                })
                .collect(Collectors.joining("\n"));
    }

    private void openFirstRunSetupDialog(Stage owner) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/system_settings.fxml"));
            loader.setControllerFactory(applicationContext::getBean);
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.initOwner(owner);
            stage.initModality(Modality.APPLICATION_MODAL);
            brandingService.tagStage(stage, "System Configuration");
            stage.setScene(new Scene(root));
            stage.setMinWidth(800);
            stage.setMinHeight(600);
            stage.centerOnScreen();
            stage.setOnCloseRequest(event -> {
                configService.refreshCache();
                if (!brandingService.isLabProfileComplete()) {
                    Alert confirmClose = new Alert(Alert.AlertType.CONFIRMATION);
                    confirmClose.setTitle("Setup Incomplete");
                    confirmClose.setHeaderText("Lab information is incomplete");
                    confirmClose.setContentText("Closing now will require completing setup before using the system.\n\nClose anyway?");
                    java.util.Optional<javafx.scene.control.ButtonType> result = confirmClose.showAndWait();
                    if (result.isEmpty() || result.get() != javafx.scene.control.ButtonType.OK) {
                        event.consume();
                    }
                }
            });
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            showError("Setup Error", "Unable to open System Configuration: " + e.getMessage());
        }
    }

    /**
     * Update UI based on current state.
     */
    private void updateUI() {
        Platform.runLater(() -> {
            int tabCount = sessionTabs.getTabs().size();

            // Show/hide welcome pane based on whether tabs exist
            if (tabCount == 0) {
                welcomePane.setVisible(true);
                welcomePane.setManaged(true);
                sessionTabs.setVisible(false);
                sessionTabs.setManaged(false);
                mainContainer.setCenter(welcomePane);
            } else {
                welcomePane.setVisible(false);
                welcomePane.setManaged(false);
                sessionTabs.setVisible(true);
                sessionTabs.setManaged(true);
                mainContainer.setCenter(sessionTabs);
            }

            // Update session count label
            if (tabCount == 0) {
                sessionCountLabel.setText("No active sessions");
            } else if (tabCount == 1) {
                sessionCountLabel.setText("1 active session");
            } else {
                sessionCountLabel.setText(tabCount + " active sessions");
            }
        });
    }

    /**
     * Handle tab being closed - logout that session.
     */
    private void handleTabClosed(Tab tab) {
        SessionInfo session = tabSessions.remove(tab);
        if (session != null) {
            setStatus("Logged out: " + session.user.getUsername());
        }
    }

    /**
     * Open a login dialog for Reception role.
     */
    @FXML
    private void openReceptionLogin() {
        openLoginDialog(DashboardType.RECEPTION);
    }

    /**
     * Open a login dialog for Lab role.
     */
    @FXML
    private void openLabLogin() {
        openLoginDialog(DashboardType.LAB);
    }

    /**
     * Open a login dialog for Admin role, or switch back to Admin view if already
     * logged in.
     */
    @FXML
    private void openAdminLogin() {
        // If admin is logged in but viewing another dashboard, switch back to Admin
        Tab adminTab = findAdminTab();
        if (adminTab != null) {
            SessionInfo session = tabSessions.get(adminTab);
            if (session != null && session.dashboardType != DashboardType.ADMIN) {
                switchAdminTabView(adminTab, session.user, DashboardType.ADMIN);
                setStatus("Switched back to Admin dashboard");
                updateButtonStates();
                return;
            }
            // Already viewing Admin dashboard, just select the tab
            sessionTabs.getSelectionModel().select(adminTab);
            return;
        }
        // Normal login flow
        openLoginDialog(DashboardType.ADMIN);
    }

    /**
     * Open a login dialog for a specific role.
     * Smart logic:
     * - If admin is logged in and clicks Reception/Lab, switch view in same tab
     * - If that role is already logged in, do nothing (button should be disabled)
     */
    private void openLoginDialog(DashboardType targetRole) {
        System.out.println("DEBUG openLoginDialog: targetRole = " + targetRole);

        // Check if this role is already logged in (don't allow duplicate sessions for
        // non-admin)
        if (targetRole != DashboardType.ADMIN && isRoleAlreadyLoggedIn(targetRole)) {
            System.out.println("DEBUG openLoginDialog: Role " + targetRole + " already logged in by non-admin");
            // If it's an admin-owned view, switch to it
            Tab existingTab = findTabForRole(targetRole);
            if (existingTab != null) {
                sessionTabs.getSelectionModel().select(existingTab);
                return;
            }
            showAlert("Session Exists", "A " + targetRole.getDisplayName() + " session is already active.\n" +
                    "Please use the existing tab or close it first.");
            return;
        }

        // If admin is logged in and clicks Reception/Lab, switch view in the SAME admin
        // tab
        Tab adminTab = findAdminTab();
        System.out.println("DEBUG openLoginDialog: adminTab = " + (adminTab != null ? "found" : "null"));
        if (adminTab != null && targetRole != DashboardType.ADMIN) {
            SessionInfo adminSession = tabSessions.get(adminTab);
            if (adminSession != null) {
                System.out.println("DEBUG openLoginDialog: Switching admin tab to " + targetRole);
                // Switch the admin tab to show the new dashboard
                switchAdminTabView(adminTab, adminSession.user, targetRole);
                setStatus("Switched to " + targetRole.getDisplayName() + " view");
                updateButtonStates();
                return;
            }
        }

        Tab activeTab = sessionTabs.getSelectionModel().getSelectedItem();
        if (activeTab != null && targetRole != DashboardType.ADMIN) {
            SessionInfo activeSession = tabSessions.get(activeTab);
            if (activeSession != null
                    && activeSession.dashboardType != targetRole
                    && !isAdminUser(activeSession.user)
                    && userHasAccessToDashboard(activeSession.user, targetRole)) {
                switchSessionTabView(activeTab, activeSession.user, targetRole);
                setStatus("Switched to " + targetRole.getDisplayName() + " view");
                updateButtonStates();
                return;
            }
        }

        System.out.println("DEBUG openLoginDialog: Showing login dialog for " + targetRole);
        // Normal login flow
        Dialog<User> loginDialog = createLoginDialog(targetRole);
        Optional<User> result = loginDialog.showAndWait();

        result.ifPresent(user -> {
            // Check if user has access to this dashboard
            if (!userHasAccessToDashboard(user, targetRole)) {
                showAccessDenied(targetRole, user);
                return;
            }

            // Create a new tab for this session
            createSessionTab(user, targetRole);
            updateButtonStates();
        });
    }

    /**
     * Find the admin's tab (admin user can only have one tab).
     */
    private Tab findAdminTab() {
        for (Map.Entry<Tab, SessionInfo> entry : tabSessions.entrySet()) {
            if (isAdminUser(entry.getValue().user)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void switchSessionTabView(Tab tab, User user, DashboardType newDashboard) {
        try {
            Stage stage = (Stage) mainContainer.getScene().getWindow();
            SessionManager.login(stage, user);
            SessionManager.setActiveStage(stage);

            FXMLLoader loader = new FXMLLoader(getClass().getResource(newDashboard.getFxmlPath()));
            loader.setControllerFactory(applicationContext::getBean);
            Parent dashboardContent = loader.load();

            tab.setContent(dashboardContent);

            String roleIcon = switch (newDashboard) {
                case ADMIN -> "⚙️";
                case LAB -> "🔬";
                case RECEPTION -> "🏥";
            };

            boolean isAdmin = isAdminUser(user);
            String tabTitle;
            String tooltipText;

            if (isAdmin) {
                tabTitle = roleIcon + " " + user.getUsername() + " ("
                        + newDashboard.getDisplayName().replace(" Dashboard", "") + ")";
                tooltipText = user.getFullName() + "\nAdmin viewing: " + newDashboard.getDisplayName()
                        + "\nClick × to logout";
            } else {
                tabTitle = roleIcon + " " + user.getUsername();
                tooltipText = user.getFullName() + "\n" + newDashboard.getDisplayName() + "\nClick × to logout";
            }

            tab.setText(tabTitle);
            tab.setTooltip(new Tooltip(tooltipText));

            tabSessions.put(tab, new SessionInfo(user, newDashboard));
            sessionTabs.getSelectionModel().select(tab);

        } catch (Exception e) {
            e.printStackTrace();
            showError("Error", "Failed to switch dashboard view: " + e.getMessage());
        }
    }

    /**
     * Find a tab showing a specific role/dashboard.
     */
    private Tab findTabForRole(DashboardType role) {
        for (Map.Entry<Tab, SessionInfo> entry : tabSessions.entrySet()) {
            if (entry.getValue().dashboardType == role) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Check if a user has admin role.
     */
    private boolean isAdminUser(User user) {
        if (user == null || user.getRoles() == null)
            return false;
        return user.getRoles().stream()
                .anyMatch(r -> r.getName().toUpperCase().contains("ADMIN"));
    }

    private boolean isPasswordlessAllowed(DashboardType targetRole) {
        if (targetRole == DashboardType.ADMIN) {
            return false;
        }
        String value = configService.getTrimmed("REQUIRE_PASSWORD_RECEPTION_LAB", "true");
        boolean requirePassword = Boolean.parseBoolean(value);
        return !requirePassword
                && (targetRole == DashboardType.RECEPTION || targetRole == DashboardType.LAB);
    }

    private String getAccountStatusError(User user) {
        if (user == null) {
            return "Username not found.";
        }
        if (!user.isEnabled()) {
            return "Account is disabled. Contact an administrator.";
        }
        if (!user.isAccountNonLocked()) {
            return "Account is locked. Contact an administrator.";
        }
        if (!user.isAccountNonExpired()) {
            return "Account has expired. Contact an administrator.";
        }
        if (!user.isCredentialsNonExpired()) {
            return "Password has expired. Contact an administrator.";
        }
        return null;
    }

    /**
     * Switch the admin tab to show a different dashboard view.
     */
    private void switchAdminTabView(Tab adminTab, User adminUser, DashboardType newDashboard) {
        try {
            // CRITICAL: Update SessionManager BEFORE loading the dashboard content
            // so the dashboard controller's initialize() sees the correct user
            Stage stage = (Stage) mainContainer.getScene().getWindow();
            SessionManager.login(stage, adminUser);
            SessionManager.setActiveStage(stage);

            // Load the new dashboard content
            FXMLLoader loader = new FXMLLoader(getClass().getResource(newDashboard.getFxmlPath()));
            loader.setControllerFactory(applicationContext::getBean);
            Parent dashboardContent = loader.load();

            // Update tab content
            adminTab.setContent(dashboardContent);

            // Update tab title to show current view
            String roleIcon = switch (newDashboard) {
                case ADMIN -> "⚙️";
                case LAB -> "🔬";
                case RECEPTION -> "🏥";
            };
            adminTab.setText(roleIcon + " " + adminUser.getUsername() + " ("
                    + newDashboard.getDisplayName().replace(" Dashboard", "") + ")");

            // Update tooltip
            adminTab.setTooltip(new Tooltip(
                    adminUser.getFullName() + "\n" +
                            "Admin viewing: " + newDashboard.getDisplayName() + "\n" +
                            "Click × to logout"));

            // Update session info to track current view
            tabSessions.put(adminTab, new SessionInfo(adminUser, newDashboard));

            // Select the tab
            sessionTabs.getSelectionModel().select(adminTab);

        } catch (Exception e) {
            e.printStackTrace();
            showError("Error", "Failed to switch dashboard view: " + e.getMessage());
        }
    }

    /**
     * Check if a specific role is already logged in by a NON-ADMIN user.
     * Admin viewing a dashboard doesn't count as that role being "logged in"
     * because admin can switch views freely.
     */
    private boolean isRoleAlreadyLoggedIn(DashboardType role) {
        return tabSessions.values().stream()
                .anyMatch(session -> session.dashboardType == role && !isAdminUser(session.user));
    }

    /**
     * Get the logged-in admin user, if any (regardless of which dashboard they're
     * viewing).
     */
    private User getLoggedInAdmin() {
        return tabSessions.values().stream()
                .filter(session -> isAdminUser(session.user))
                .map(session -> session.user)
                .findFirst()
                .orElse(null);
    }

    /**
     * Update button states based on current sessions.
     * - Disable Reception button if a non-admin Reception user is logged in
     * - Disable Lab button if a non-admin Lab user is logged in
     * - Admin can always switch views (buttons show "Switch to X")
     * - Admin button disabled if admin is already logged in
     */
    private void updateButtonStates() {
        Platform.runLater(() -> {
            boolean receptionLoggedIn = isRoleAlreadyLoggedIn(DashboardType.RECEPTION);
            boolean labLoggedIn = isRoleAlreadyLoggedIn(DashboardType.LAB);
            User adminUser = getLoggedInAdmin();
            boolean adminLoggedIn = adminUser != null;

            // Get current admin view (if admin is logged in)
            DashboardType currentAdminView = null;
            if (adminLoggedIn) {
                Tab adminTab = findAdminTab();
                if (adminTab != null) {
                    SessionInfo session = tabSessions.get(adminTab);
                    currentAdminView = session != null ? session.dashboardType : null;
                }
            }

            // Reception button: disabled if non-admin reception is logged in
            receptionLoginBtn.setDisable(receptionLoggedIn);

            // Lab button: disabled if non-admin lab is logged in
            labLoginBtn.setDisable(labLoggedIn);

            // Admin button: disabled only if admin is viewing Admin dashboard
            // (enabled when admin is viewing Reception/Lab so they can switch back)
            adminLoginBtn.setDisable(adminLoggedIn && currentAdminView == DashboardType.ADMIN);

            // Update button tooltips
            if (receptionLoggedIn) {
                receptionLoginBtn.setTooltip(new Tooltip("Reception session already active"));
            } else if (adminLoggedIn) {
                if (currentAdminView == DashboardType.RECEPTION) {
                    receptionLoginBtn.setTooltip(new Tooltip("Currently viewing Reception"));
                } else {
                    receptionLoginBtn.setTooltip(new Tooltip("Switch to Reception view"));
                }
            } else {
                receptionLoginBtn.setTooltip(new Tooltip("Login as Reception"));
            }

            if (labLoggedIn) {
                labLoginBtn.setTooltip(new Tooltip("Lab session already active"));
            } else if (adminLoggedIn) {
                if (currentAdminView == DashboardType.LAB) {
                    labLoginBtn.setTooltip(new Tooltip("Currently viewing Lab"));
                } else {
                    labLoginBtn.setTooltip(new Tooltip("Switch to Lab view"));
                }
            } else {
                labLoginBtn.setTooltip(new Tooltip("Login as Lab"));
            }

            if (adminLoggedIn) {
                if (currentAdminView == DashboardType.ADMIN) {
                    adminLoginBtn.setTooltip(new Tooltip("Currently viewing Admin dashboard"));
                } else {
                    adminLoginBtn.setTooltip(new Tooltip("Switch back to Admin dashboard"));
                }
            } else {
                adminLoginBtn.setTooltip(new Tooltip("Login as Admin"));
            }

            updateActiveRoleLabel();
        });
    }

    private void updateActiveRoleLabel() {
        if (activeRoleLabel == null) {
            return;
        }
        Tab activeTab = sessionTabs.getSelectionModel().getSelectedItem();
        if (activeTab == null) {
            activeRoleLabel.setText("Active: None");
            return;
        }
        SessionInfo session = tabSessions.get(activeTab);
        if (session == null || session.dashboardType == null) {
            activeRoleLabel.setText("Active: None");
            return;
        }

        String role = switch (session.dashboardType) {
            case RECEPTION -> "Reception";
            case LAB -> "Lab";
            case ADMIN -> "Admin";
        };
        activeRoleLabel.setText("Active: " + role);
    }

    /**
     * Show a simple alert dialog.
     */
    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * Create a login dialog for a specific role.
     */
    private Dialog<User> createLoginDialog(DashboardType targetRole) {
        Dialog<User> dialog = new Dialog<>();
        dialog.setTitle(targetRole.getDisplayName().replace(" Dashboard", "") + " Login");
        dialog.setHeaderText("Enter your credentials");

        // Set the button types
        ButtonType loginButtonType = new ButtonType("Login", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

        // Create the username and password fields
        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");
        usernameField.setPrefWidth(250);

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.setPrefWidth(250);

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #e74c3c;");

        Label passwordHintLabel = new Label(passwordPolicyService.getPolicyHint());
        passwordHintLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 11;");

        boolean passwordlessAllowed = isPasswordlessAllowed(targetRole);

        // Role indicator
        String roleColor = switch (targetRole) {
            case ADMIN -> "#e74c3c";
            case LAB -> "#9b59b6";
            case RECEPTION -> "#27ae60";
        };
        Label roleLabel = new Label("● " + targetRole.getDisplayName().replace(" Dashboard", ""));
        roleLabel.setStyle("-fx-text-fill: " + roleColor + "; -fx-font-weight: bold;");

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.getChildren().addAll(
                roleLabel,
                new VBox(5, new Label("Username:"), usernameField));

        if (passwordlessAllowed) {
            Label passwordlessHint = new Label("Password not required for Reception/Lab.");
            passwordlessHint.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 11;");
            content.getChildren().addAll(passwordlessHint, errorLabel);
        } else {
            content.getChildren().addAll(
                    new VBox(5, new Label("Password:"), passwordField),
                    passwordHintLabel,
                    errorLabel);
        }

        dialog.getDialogPane().setContent(content);

        // Request focus on username field
        Platform.runLater(usernameField::requestFocus);

        // Enable/Disable login button depending on input
        Button loginButton = (Button) dialog.getDialogPane().lookupButton(loginButtonType);
        loginButton.setDisable(true);

        // Style the login button
        loginButton.setStyle("-fx-background-color: " + roleColor + "; -fx-text-fill: white;");

        if (passwordlessAllowed) {
            usernameField.textProperty().addListener((obs, old, newVal) -> loginButton
                    .setDisable(newVal.trim().isEmpty()));
            usernameField.setOnAction(e -> {
                if (!loginButton.isDisabled()) {
                    loginButton.fire();
                }
            });
        } else {
            usernameField.textProperty().addListener((obs, old, newVal) -> loginButton
                    .setDisable(newVal.trim().isEmpty() || passwordField.getText().isEmpty()));
            passwordField.textProperty().addListener((obs, old, newVal) -> loginButton
                    .setDisable(usernameField.getText().trim().isEmpty() || newVal.isEmpty()));

            // Handle Enter key in password field
            passwordField.setOnAction(e -> {
                if (!loginButton.isDisabled()) {
                    loginButton.fire();
                }
            });
        }

        loginButton.addEventFilter(ActionEvent.ACTION, event -> {
            String username = usernameField.getText().trim();
            String password = passwordField.getText();

            if (passwordlessAllowed) {
                User user = authService.getUser(username);
                String statusError = getAccountStatusError(user);
                if (statusError != null) {
                    errorLabel.setText(statusError);
                    event.consume();
                    return;
                }
                if (isAdminUser(user)) {
                    errorLabel.setText("Admin requires a password.");
                    event.consume();
                    return;
                }
                User authenticated = authService.authenticateWithoutPassword(username);
                if (authenticated != null) {
                    dialog.setResult(authenticated);
                    dialog.close();
                    return;
                }
                errorLabel.setText("Login failed. Please try again.");
                event.consume();
                return;
            }

            if (authService.authenticate(username, password)) {
                User user = authService.getUser(username);
                if (user != null) {
                    dialog.setResult(user);
                    dialog.close();
                    return;
                }
                errorLabel.setText("Login failed. Please try again.");
                event.consume();
                return;
            }

            User user = authService.getUser(username);
            if (user == null) {
                errorLabel.setText("Username not found.");
            } else if (!user.isEnabled()) {
                errorLabel.setText("Account is disabled. Contact an administrator.");
            } else if (!user.isAccountNonLocked()) {
                errorLabel.setText("Account is locked. Contact an administrator.");
            } else if (!user.isAccountNonExpired()) {
                errorLabel.setText("Account has expired. Contact an administrator.");
            } else if (!user.isCredentialsNonExpired()) {
                errorLabel.setText("Password has expired. Contact an administrator.");
            } else {
                errorLabel.setText("Incorrect password.");
            }
            event.consume();
        });

        dialog.setResultConverter(dialogButton -> dialogButton == loginButtonType ? dialog.getResult() : null);

        return dialog;
    }

    /**
     * Create a new session tab for the logged-in user.
     */
    private void createSessionTab(User user, DashboardType dashboardType) {
        try {
            // Set session context before loading FXML so controller initialize() reads the
            // correct user.
            Stage stage = (Stage) mainContainer.getScene().getWindow();
            SessionManager.login(stage, user);
            SessionManager.setActiveStage(stage);

            // Load the dashboard content
            FXMLLoader loader = new FXMLLoader(getClass().getResource(dashboardType.getFxmlPath()));
            loader.setControllerFactory(applicationContext::getBean);
            Parent dashboardContent = loader.load();

            // Create the tab
            Tab tab = new Tab();

            // Create custom tab header with user info
            String roleIcon = switch (dashboardType) {
                case ADMIN -> "⚙️";
                case LAB -> "🔬";
                case RECEPTION -> "🏥";
            };

            // For admin users, show username with current dashboard
            boolean isAdmin = isAdminUser(user);
            String tabTitle;
            String tooltipText;

            if (isAdmin) {
                tabTitle = roleIcon + " " + user.getUsername() + " ("
                        + dashboardType.getDisplayName().replace(" Dashboard", "") + ")";
                tooltipText = user.getFullName() + "\nAdmin viewing: " + dashboardType.getDisplayName()
                        + "\nClick × to logout";
            } else {
                tabTitle = roleIcon + " " + user.getUsername();
                tooltipText = user.getFullName() + "\n" + dashboardType.getDisplayName() + "\nClick × to logout";
            }

            tab.setText(tabTitle);
            tab.setContent(dashboardContent);
            tab.setClosable(true);
            tab.setTooltip(new Tooltip(tooltipText));

            // Confirm before closing tab
            tab.setOnCloseRequest(event -> {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("Close Session");
                confirm.setHeaderText("Logout " + user.getUsername() + "?");
                confirm.setContentText("This will end the current session.");

                Optional<ButtonType> result = confirm.showAndWait();
                if (result.isEmpty() || result.get() != ButtonType.OK) {
                    event.consume(); // Cancel close
                }
            });

            // Store session info
            tabSessions.put(tab, new SessionInfo(user, dashboardType));

            // Add tab and select it
            sessionTabs.getTabs().add(tab);
            sessionTabs.getSelectionModel().select(tab);

            setStatus("Logged in: " + user.getUsername() + " ("
                    + dashboardType.getDisplayName().replace(" Dashboard", "") + ")");
            updateUI();

        } catch (Exception e) {
            e.printStackTrace();
            showError("Error", "Failed to load dashboard: " + e.getMessage());
        }
    }

    /**
     * Check if user has access to the specified dashboard type.
     */
    private boolean userHasAccessToDashboard(User user, DashboardType dashboardType) {
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            return false;
        }

        // Admin can access all dashboards
        boolean isAdmin = user.getRoles().stream()
                .anyMatch(r -> r.getName().equals("ROLE_ADMIN"));
        if (isAdmin) {
            return true;
        }

        // Check if user has a role that allows access to this dashboard
        return user.getRoles().stream()
                .anyMatch(role -> dashboardType.isAllowedForRole(role.getName()));
    }

    /**
     * Show access denied message.
     */
    private void showAccessDenied(DashboardType targetRole, User user) {
        String roleName = targetRole.getDisplayName().replace(" Dashboard", "");

        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Access Denied");
        alert.setHeaderText("You don't have " + roleName + " access");
        alert.setContentText(
                "User '" + user.getUsername() + "' is not authorized to access the " + roleName + " dashboard.\n\n" +
                        "Please select a different role or contact an administrator.");
        alert.showAndWait();
    }

    /**
     * Exit the application.
     */
    @FXML
    private void exitApplication() {
        int sessionCount = sessionTabs.getTabs().size();

        if (sessionCount > 0) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Exit Application");
            alert.setHeaderText("Active Sessions");
            alert.setContentText("There are " + sessionCount + " active session(s).\n\n" +
                    "Exiting will close all sessions. Continue?");

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.OK) {
                return;
            }
        }

        Platform.exit();
        System.exit(0);
    }

    /**
     * Set status bar message.
     */
    private void setStatus(String message) {
        Platform.runLater(() -> statusLabel.setText(message));
    }

    /**
     * Setup hover effect for a button.
     */
    private void setupButtonHoverEffect(Button button, String normalColor, String hoverColor) {
        if (button == null)
            return;

        String baseStyle = button.getStyle();
        String normalStyle = baseStyle.replaceFirst("-fx-background-color: [^;]+;",
                "-fx-background-color: " + normalColor + ";");
        String hoverStyle = baseStyle.replaceFirst("-fx-background-color: [^;]+;",
                "-fx-background-color: " + hoverColor + ";");

        button.setOnMouseEntered(e -> button.setStyle(hoverStyle));
        button.setOnMouseExited(e -> button.setStyle(normalStyle));
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void setupSessionTimeoutHandlers() {
        sessionExpiryTimer = new Timeline(
                new KeyFrame(javafx.util.Duration.seconds(30), event -> expireInactiveSessions()));
        sessionExpiryTimer.setCycleCount(Animation.INDEFINITE);
        sessionExpiryTimer.play();

        mainContainer.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                return;
            }
            newScene.addEventFilter(KeyEvent.KEY_PRESSED, event -> touchActiveSession());
            newScene.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> touchActiveSession());
        });
    }

    private void touchActiveSession() {
        Tab active = sessionTabs.getSelectionModel().getSelectedItem();
        if (active == null) {
            return;
        }
        SessionInfo session = tabSessions.get(active);
        if (session != null) {
            session.touch();
        }
    }

    private void expireInactiveSessions() {
        if (!isSessionTimeoutEnabled()) {
            return;
        }

        List<Tab> expiredTabs = new ArrayList<>();
        for (Map.Entry<Tab, SessionInfo> entry : tabSessions.entrySet()) {
            if (isExpired(entry.getValue())) {
                expiredTabs.add(entry.getKey());
            }
        }

        if (expiredTabs.isEmpty()) {
            return;
        }

        for (Tab tab : expiredTabs) {
            SessionInfo session = tabSessions.get(tab);
            expireSessionTab(tab, session, tab == sessionTabs.getSelectionModel().getSelectedItem());
        }
        updateUI();
        updateButtonStates();
    }

    private void expireSessionTab(Tab tab, SessionInfo session, boolean notifyUser) {
        if (tab == null || session == null) {
            return;
        }
        if (notifyUser) {
            showAlert("Session Expired", "Your session has expired due to inactivity.");
        }
        tabSessions.remove(tab);
        sessionTabs.getTabs().remove(tab);
        setStatus("Session expired: " + session.user.getUsername());
    }

    private boolean isExpired(SessionInfo session) {
        Duration timeout = getSessionTimeout();
        if (timeout.isZero()) {
            return false;
        }
        if (session == null || session.lastAccess == null) {
            return false;
        }
        return Duration.between(session.lastAccess, Instant.now()).compareTo(timeout) > 0;
    }

    private Duration getSessionTimeout() {
        if (!isSessionTimeoutEnabled() || sessionTimeoutMinutes <= 0) {
            return Duration.ZERO;
        }
        return Duration.ofMinutes(sessionTimeoutMinutes);
    }

    private boolean isSessionTimeoutEnabled() {
        String value = configService.getTrimmed(SESSION_TIMEOUT_ENABLED_KEY, "false");
        return Boolean.parseBoolean(value);
    }

    /**
     * Get the current number of active sessions.
     */
    public int getActiveSessionCount() {
        return sessionTabs.getTabs().size();
    }
}
