package io.quasient.pal.pocs.controller;

import io.quasient.pal.cxn.directory.PalDirectory;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Standalone JavaFX launcher for the intercept controller.
 *
 * <p>Run via PAL:
 *
 * <pre>
 * pal run -d localhost:2379 -n controller --json-rpc auto \
 *   -cp target/classes io.quasient.pal.pocs.controller.ControllerApp
 * </pre>
 *
 * <p>For embedding in a larger application, use {@link ControllerPane} directly instead.
 */
public class ControllerApp extends Application {

  private PalDirectory palDirectory;

  @Override
  public void start(Stage stage) {
    ControllerPane controllerPane = new ControllerPane();

    // Connect to PalDirectory for peer name resolution (optional, best-effort)
    String directoryUrl = System.getenv("PAL_DIRECTORY");
    if (directoryUrl == null) {
      directoryUrl = System.getProperty("pal.directory");
    }
    if (directoryUrl != null && !directoryUrl.isEmpty()) {
      try {
        palDirectory = new PalDirectory(directoryUrl);
        controllerPane.setPalDirectory(palDirectory);
      } catch (Exception e) {
        System.err.println("Could not connect to PalDirectory: " + e.getMessage());
      }
    }

    Scene scene = new Scene(controllerPane, 750, 520);
    scene.getStylesheets().add(getClass().getResource("/controller.css").toExternalForm());
    stage.setTitle("PAL Intercept Controller");
    stage.setScene(scene);
    stage.show();
  }

  @Override
  public void stop() {
    ControllerPane pane = ControllerPane.getInstance();
    if (pane != null) {
      pane.shutdown();
    }
    if (palDirectory != null) {
      try {
        palDirectory.close();
      } catch (Exception e) {
        // Shutting down
      }
    }
  }

  public static void main(String[] args) {
    launch(args);
  }
}
