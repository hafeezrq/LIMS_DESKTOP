package com.qdc.lims.ui.controller;

import com.qdc.lims.ui.AppPaths;
import com.qdc.lims.ui.backup.BackupService;
import com.qdc.lims.ui.backup.BackupSettingsService;
import com.qdc.lims.ui.backup.SnapshotWindowService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Backup/Restore settings window.
 */
@Component("backupSettingsController")
public class BackupSettingsController {

    @FXML
    private PasswordField backupPasswordField;

    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private Label statusLabel;

    private final BackupSettingsService settings;
    private final BackupService backupService;
    private final SnapshotWindowService snapshotWindowService;

    @FXML
    private ToggleButton autoBackupToggle;

    @FXML
    private ListView<BackupFileInfo> backupListView;

    @FXML
    private Label backupListStatusLabel;

    public BackupSettingsController(BackupSettingsService settings,
            BackupService backupService,
            SnapshotWindowService snapshotWindowService) {
        this.settings = settings;
        this.backupService = backupService;
        this.snapshotWindowService = snapshotWindowService;
    }

    @FXML
    private void initialize() {
        statusLabel.setText("");

        // If a password is already configured, show a hint only.
        if (settings.getBackupPassword().isPresent()) {
            statusLabel.setText("Backup password is configured.");
        }

        syncAutoBackupToggle();
        setupBackupList();
        loadBackups();
    }

    @FXML
    private void handleSavePassword() {
        String p1 = backupPasswordField.getText();
        String p2 = confirmPasswordField.getText();

        if (p1 == null || p1.isBlank()) {
            showError("Please enter a password.");
            return;
        }
        if (!p1.equals(p2)) {
            showError("Passwords do not match.");
            return;
        }
        if (p1.length() < 8) {
            showError("Password must be at least 8 characters.");
            return;
        }

        settings.setBackupPassword(p1.toCharArray());
        backupPasswordField.clear();
        confirmPasswordField.clear();
        showSuccess("Backup password saved.");
    }

    @FXML
    private void handleBackupNow() {
        try {
            Path zip = backupService.backupNow();
            showSuccess("Backup created: " + zip.getFileName());
            loadBackups();
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void handleAutoBackupToggle() {
        if (autoBackupToggle == null) {
            return;
        }
        boolean enabled = autoBackupToggle.isSelected();
        settings.setAutoBackupEnabled(enabled);
        applyAutoBackupToggleStyle(enabled);
        showSuccess(enabled ? "Automatic backup enabled." : "Automatic backup disabled.");
    }

    @FXML
    private void handleRefreshBackups() {
        loadBackups();
    }

    @FXML
    private void handleOpenSnapshot() {
        BackupFileInfo selected = backupListView != null ? backupListView.getSelectionModel().getSelectedItem() : null;
        if (selected == null) {
            showError("Please select a backup from the list.");
            return;
        }

        Optional<char[]> pass = promptForPassword("Snapshot Restore");
        if (pass.isEmpty()) {
            showError("Snapshot cancelled (no password provided).");
            return;
        }

        String suggested = suggestedSnapshotName(selected.path());
        TextInputDialog nameDialog = new TextInputDialog(suggested);
        nameDialog.setTitle("Snapshot Database Name");
        nameDialog.setHeaderText("Enter a name for the snapshot database");
        nameDialog.setContentText("Database name (letters/numbers/_ only):");

        Optional<String> nameOpt = nameDialog.showAndWait();
        if (nameOpt.isEmpty() || nameOpt.get().isBlank()) {
            showError("Snapshot cancelled (no name provided).");
            return;
        }

        try {
            BackupService.SnapshotRestoreResult result = backupService.restoreBackupToNewDatabase(
                    selected.path(), pass.get(), nameOpt.get().trim());
            showSuccess("Snapshot database created: " + result.getDatabaseName());
            snapshotWindowService.openSnapshotWindow(
                    result.getJdbcUrl(),
                    result.getUsername(),
                    result.getPassword(),
                    result.getDatabaseName());
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void handleClose() {
        com.qdc.lims.ui.util.ViewCloseUtil.closeCurrentTabOrWindow(statusLabel);
    }

    private void showError(String msg) {
        statusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
        statusLabel.setText(msg);
    }

    private void showSuccess(String msg) {
        statusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
        statusLabel.setText(msg);
    }

    private void syncAutoBackupToggle() {
        if (autoBackupToggle == null) {
            return;
        }
        boolean enabled = settings.isAutoBackupEnabled();
        autoBackupToggle.setSelected(enabled);
        applyAutoBackupToggleStyle(enabled);
    }

    private void applyAutoBackupToggleStyle(boolean enabled) {
        if (autoBackupToggle == null) {
            return;
        }
        autoBackupToggle.setText(enabled ? "Auto Backup: ON" : "Auto Backup: OFF");
        String color = enabled ? "#27ae60" : "#7f8c8d";
        autoBackupToggle.setStyle("-fx-background-color: " + color
                + "; -fx-text-fill: white; -fx-padding: 6 12; -fx-background-radius: 5;");
    }

    private void setupBackupList() {
        if (backupListView == null) {
            return;
        }
        backupListView.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(BackupFileInfo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.display());
                }
            }
        });
    }

    private void loadBackups() {
        if (backupListView == null) {
            return;
        }
        List<BackupFileInfo> items = new ArrayList<>();
        try {
            Path dir = AppPaths.backupsDir();
            if (java.nio.file.Files.exists(dir)) {
                try (var stream = java.nio.file.Files.list(dir)) {
                    items = stream
                            .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".zip"))
                            .map(this::toBackupInfo)
                            .sorted(Comparator.comparing(BackupFileInfo::lastModified).reversed())
                            .toList();
                }
            }
        } catch (Exception e) {
            showError("Failed to load backups: " + e.getMessage());
        }

        backupListView.getItems().setAll(items);
        if (backupListStatusLabel != null) {
            backupListStatusLabel.setText(items.isEmpty()
                    ? "No backups found."
                    : "Found " + items.size() + " backup(s).");
        }
    }

    private BackupFileInfo toBackupInfo(Path path) {
        try {
            long size = java.nio.file.Files.size(path);
            long lastModified = java.nio.file.Files.getLastModifiedTime(path).toMillis();
            LocalDateTime time = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(lastModified),
                    ZoneId.systemDefault());
            String label = path.getFileName().toString()
                    + "  |  " + formatSize(size)
                    + "  |  " + time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            return new BackupFileInfo(path, label, lastModified);
        } catch (Exception e) {
            return new BackupFileInfo(path, path.getFileName().toString(), 0L);
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format("%.1f MB", mb);
        }
        double gb = mb / 1024.0;
        return String.format("%.2f GB", gb);
    }

    private Optional<char[]> promptForPassword(String title) {
        Dialog<char[]> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText("Enter backup password");
        ButtonType okButton = new ButtonType("Continue", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okButton, ButtonType.CANCEL);

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        dialog.getDialogPane().setContent(passwordField);
        Platform.runLater(passwordField::requestFocus);

        dialog.setResultConverter(button -> button == okButton ? passwordField.getText().toCharArray() : null);
        Optional<char[]> result = dialog.showAndWait();
        if (result.isEmpty() || result.get().length == 0) {
            return Optional.empty();
        }
        return result;
    }

    private String suggestedSnapshotName(Path backupPath) {
        String base = "lims_snapshot_" + DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now());
        if (backupPath == null) {
            return base;
        }
        String name = backupPath.getFileName().toString();
        if (name.startsWith("backup_") && name.endsWith(".zip")) {
            String raw = name.substring("backup_".length(), name.length() - ".zip".length());
            String sanitized = raw.replaceAll("[^A-Za-z0-9_]", "_");
            if (!sanitized.isBlank()) {
                return "lims_snapshot_" + sanitized;
            }
        }
        return base;
    }

    private record BackupFileInfo(Path path, String display, long lastModified) {
    }
}
