package com.qdc.lims;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the LIMS Spring Boot application.
 */
@SpringBootApplication
public class QdcLimsApplication {

	/**
	 * Starts the LIMS Spring Boot application.
	 * 
	 * Note: When running in a desktop environment, this method delegates to
	 * JavaFX Application.launch() via the DesktopLauncher class if used,
	 * or simply bootstraps Spring if run as a web server.
	 * 
	 * However, since this is a JavaFX app, we should ideally run
	 * DesktopApplication.main()
	 * or rely on the Maven plugin to pick the right class.
	 * 
	 * Currently, `mvn spring-boot:run` targets this class. This class just starts
	 * Spring.
	 * If we want the GUI, we need to launch JavaFX.
	 */
	public static void main(String[] args) {
		// Detect if we are in a headless environment. If not, launch JavaFX.
		// For simplicity in this project structure, we can just launch the
		// DesktopApplication wrapper.
		javafx.application.Application.launch(DesktopApplication.class, args);
	}

}

// Checking for compilation errors
// No actual code change, just using this to trigger a thought process about
// compilation.
