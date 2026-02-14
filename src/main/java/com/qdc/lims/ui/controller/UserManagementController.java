package com.qdc.lims.ui.controller;

import com.qdc.lims.ui.SessionManager;
import com.qdc.lims.entity.Role;
import com.qdc.lims.entity.User;
import com.qdc.lims.repository.RoleRepository;
import com.qdc.lims.repository.UserRepository;
import com.qdc.lims.service.PasswordPolicyService;
import com.qdc.lims.service.UserService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JavaFX controller for user management (Admin only).
 * SECURITY: This controller verifies admin permissions on initialization.
 */
@Component("userManagementController")
public class UserManagementController {

    @FXML
    private TableView<User> userTable;

    @FXML
    private TableColumn<User, String> usernameColumn;

    @FXML
    private TableColumn<User, String> fullNameColumn;

    @FXML
    private TableColumn<User, String> roleColumn;

    @FXML
    private TableColumn<User, String> statusColumn;

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private TextField fullNameField;

    @FXML
    private RadioButton adminRadio;

    @FXML
    private RadioButton receptionRadio;

    @FXML
    private RadioButton labRadio;

    @FXML
    private RadioButton staffRadio;

    @FXML
    private ToggleGroup roleToggleGroup;

    @FXML
    private CheckBox activeCheckBox;

    @FXML
    private Label messageLabel;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserService userService;
    private final PasswordPolicyService passwordPolicyService;

    private Long editingUserId;

    public UserManagementController(UserRepository userRepository,
            RoleRepository roleRepository,
            UserService userService,
            PasswordPolicyService passwordPolicyService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userService = userService;
        this.passwordPolicyService = passwordPolicyService;
    }

    @FXML
    private void initialize() {
        // SECURITY: Verify admin access before loading
        if (!SessionManager.isAdmin()) {
            showAccessDeniedAndClose();
            return;
        }

        // Setup table columns
        usernameColumn.setCellValueFactory(new PropertyValueFactory<>("username"));
        fullNameColumn.setCellValueFactory(new PropertyValueFactory<>("fullName"));

        // Display roles as comma-separated string
        roleColumn.setCellValueFactory(cellData -> {
            String roleNames = cellData.getValue().getRoles().stream()
                    .map(role -> role.getName().replace("ROLE_", ""))
                    .collect(Collectors.joining(", "));
            return new javafx.beans.property.SimpleStringProperty(roleNames);
        });

        statusColumn.setCellValueFactory(cellData -> {
            String status = cellData.getValue().isActive() ? "Active" : "Inactive";
            return new javafx.beans.property.SimpleStringProperty(status);
        });

        statusColumn.setCellFactory(column -> new TableCell<User, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.equals("Active")) {
                        setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold;");
                    }
                }
            }
        });

        loadUsers();
        messageLabel.setText("");
    }

    private void loadUsers() {
        List<User> users = userRepository.findAll();
        ObservableList<User> observableUsers = FXCollections.observableArrayList(users);
        userTable.setItems(observableUsers);
    }

    @FXML
    private void handleCreateUser() {
        messageLabel.setText("");

        // Validation
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String fullName = fullNameField.getText().trim();

        if (username.isEmpty() || password.isEmpty() || fullName.isEmpty()) {
            if (editingUserId != null && !fullName.isEmpty() && !username.isEmpty()) {
                // Allow blank password on edit to keep existing password.
            } else {
                showError("Please fill in all required fields");
                return;
            }
        }

        // Enforce password policy when creating or when explicitly changing a password.
        if (editingUserId == null || !password.isBlank()) {
            passwordPolicyService.validate(password).ifPresent(msg -> {
                throw new IllegalArgumentException(msg);
            });
        }

        // Check if username already exists (only for new users)
        if (editingUserId == null && userRepository.findByUsername(username).isPresent()) {
            showError("Username already exists");
            return;
        }

        // Build roles FIRST - need to ensure we have role entities
        Role selectedRole = null;
        String missingRoleMessage = null;
        if (adminRadio.isSelected()) {
            selectedRole = roleRepository.findByName("ROLE_ADMIN").orElse(null);
            missingRoleMessage = "Administrator role is not available. Please refresh roles or restart the app.";
        } else if (staffRadio.isSelected()) {
            selectedRole = roleRepository.findByName("ROLE_STAFF").orElse(null);
            missingRoleMessage = "Staff role is not available. Please refresh roles or restart the app.";
        } else if (receptionRadio.isSelected()) {
            selectedRole = roleRepository.findByName("ROLE_RECEPTION").orElse(null);
            missingRoleMessage = "Reception role is not available. Please refresh roles or restart the app.";
        } else if (labRadio.isSelected()) {
            selectedRole = roleRepository.findByName("ROLE_LAB").orElse(null);
            missingRoleMessage = "Lab role is not available. Please refresh roles or restart the app.";
        } else {
            showError("Please select a role");
            return;
        }

        if (selectedRole == null) {
            showError(missingRoleMessage);
            return;
        }

        try {
            User user;
            if (editingUserId != null) {
                user = userService.getUserById(editingUserId)
                        .orElseThrow(() -> new RuntimeException("User not found"));
            } else {
                user = new User();
                user.setUsername(username);
            }

            user.setFullName(fullName);
            user.setActive(activeCheckBox.isSelected());
            user.setRoles(new HashSet<>(List.of(selectedRole)));

            if (editingUserId == null || !password.isBlank()) {
                user.setPassword(password);
            }

            if (editingUserId != null) {
                userService.updateUser(user);
                showSuccess("User updated successfully!");
            } else {
                userService.createUser(user);
                showSuccess("User created successfully!");
            }

            // Critical: reload from DB to ensure list is up-to-date with transactions
            loadUsers();
            handleClearForm();
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            showError("Failed to save user: " + e.getMessage());
        }
    }

    @FXML
    private void handleEditUser() {
        User selectedUser = userTable.getSelectionModel().getSelectedItem();
        if (selectedUser == null) {
            showError("Please select a user to edit");
            return;
        }

        // Populate form with selected user data
        usernameField.setText(selectedUser.getUsername());
        usernameField.setDisable(true); // Can't change username
        fullNameField.setText(selectedUser.getFullName());

        // Check which roles the user has
        adminRadio.setSelected(selectedUser.hasRole("ROLE_ADMIN"));
        staffRadio.setSelected(selectedUser.hasRole("ROLE_STAFF"));
        receptionRadio.setSelected(selectedUser.hasRole("ROLE_RECEPTION"));
        labRadio.setSelected(selectedUser.hasRole("ROLE_LAB"));

        activeCheckBox.setSelected(selectedUser.isActive());

        editingUserId = selectedUser.getId();
        showInfo("Editing user: " + selectedUser.getUsername() + ". Update and click Create to save changes.");
    }

    @FXML
    private void handleDeleteUser() {
        User selectedUser = userTable.getSelectionModel().getSelectedItem();
        if (selectedUser == null) {
            showError("Please select a user to delete");
            return;
        }

        // Confirm deletion
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Deletion");
        alert.setHeaderText("Delete user: " + selectedUser.getUsername() + "?");
        alert.setContentText("This action cannot be undone.");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    userRepository.delete(selectedUser);
                    showSuccess("User deleted successfully");
                    loadUsers();
                } catch (Exception e) {
                    showError("Failed to delete user: " + e.getMessage());
                }
            }
        });
    }

    @FXML
    private void handleRefresh() {
        loadUsers();
        handleClearForm();
        showInfo("User list refreshed");
    }

    @FXML
    private void handleClearForm() {
        usernameField.clear();
        usernameField.setDisable(false);
        passwordField.clear();
        fullNameField.clear();
        if (roleToggleGroup != null) {
            roleToggleGroup.selectToggle(null);
        }
        activeCheckBox.setSelected(true);
        editingUserId = null;
        messageLabel.setText("");
    }

    @FXML
    private void handleClose() {
        com.qdc.lims.ui.util.ViewCloseUtil.closeCurrentTabOrWindow(userTable);
    }

    private void showError(String message) {
        messageLabel.setText("❌ " + message);
        messageLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
    }

    private void showSuccess(String message) {
        messageLabel.setText("✓ " + message);
        messageLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
    }

    private void showInfo(String message) {
        messageLabel.setText("ℹ️ " + message);
        messageLabel.setStyle("-fx-text-fill: #3498db; -fx-font-weight: bold;");
    }

    /**
     * SECURITY: Show access denied alert and close the window.
     * Called when a non-admin user attempts to open User Management.
     */
    private void showAccessDeniedAndClose() {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Access Denied");
        alert.setHeaderText("Admin Access Required");
        alert.setContentText(
                "User Management is restricted to administrators only.\n\n" +
                        "Your current role does not have permission to access this feature.\n\n" +
                        "Please contact your system administrator if you need access.");
        alert.showAndWait();

        // Close the window
        if (userTable != null && userTable.getScene() != null && userTable.getScene().getWindow() != null) {
            com.qdc.lims.ui.util.ViewCloseUtil.closeCurrentTabOrWindow(userTable);
        }
    }
}
