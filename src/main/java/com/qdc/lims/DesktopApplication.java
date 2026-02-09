package com.qdc.lims;

import com.qdc.lims.ui.AppPaths;
import com.qdc.lims.service.BrandingService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Files;
import java.util.Locale;

/**
 * JavaFX Desktop Application entry point.
 * Integrates Spring Boot context with JavaFX lifecycle.
 */
public class DesktopApplication extends Application {

    private static final Locale PAKISTAN_LOCALE = Locale.forLanguageTag("en-PK");

    private ConfigurableApplicationContext springContext;

    @Override
    public void init() throws Exception {
        // Keep app-wide formatting consistent for numbers, dates, and currency.
        Locale.setDefault(PAKISTAN_LOCALE);
        Locale.setDefault(Locale.Category.FORMAT, PAKISTAN_LOCALE);

        // Ensure app data folders exist (logs/backups).
        Files.createDirectories(AppPaths.appDataDir());
        Files.createDirectories(AppPaths.backupsDir());

        // Initialize Spring context before JavaFX starts
        springContext = new SpringApplicationBuilder(QdcLimsApplication.class)
                .headless(false) // Important for desktop apps
                .run();
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Load the main application window with tabbed interface
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main_window.fxml"));
        loader.setControllerFactory(springContext::getBean);

        Parent root = loader.load();
        Scene scene = new Scene(root, 1100, 750);

        BrandingService brandingService = springContext.getBean(BrandingService.class);
        brandingService.tagStage(primaryStage, brandingService.getApplicationName());
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);
        primaryStage.setResizable(true);
        primaryStage.centerOnScreen();

        // Handle close request
        primaryStage.setOnCloseRequest(event -> {
            // Get the controller to check active sessions
            Object controller = loader.getController();
            if (controller instanceof com.qdc.lims.ui.controller.MainWindowController mainController) {
                int activeSessions = mainController.getActiveSessionCount();
                if (activeSessions > 0) {
                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                            javafx.scene.control.Alert.AlertType.CONFIRMATION);
                    alert.setTitle("Exit Application");
                    alert.setHeaderText("Active Sessions");
                    alert.setContentText("There are " + activeSessions + " active session(s).\n\n" +
                            "Exiting will close all sessions. Continue?");

                    java.util.Optional<javafx.scene.control.ButtonType> result = alert.showAndWait();
                    if (result.isEmpty() || result.get() != javafx.scene.control.ButtonType.OK) {
                        event.consume(); // Cancel the close
                    }
                }
            }
        });

        primaryStage.show();
    }

    @Override
    public void stop() {
        // Close Spring context when JavaFX app closes
        springContext.close();
        Platform.exit();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
