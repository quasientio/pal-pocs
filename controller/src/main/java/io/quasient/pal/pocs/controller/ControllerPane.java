package io.quasient.pal.pocs.controller;

import io.quasient.pal.common.directory.nodes.PeerInfo;
import io.quasient.pal.cxn.directory.PalDirectory;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * Reusable JavaFX component that hosts per-peer control panels in a TabPane. This is the component
 * to embed in a larger application. For standalone usage, see {@link ControllerApp}.
 *
 * <p>The singleton instance is accessed by {@link ControllerCallback} to route intercept callbacks
 * to the correct peer panel.
 */
public class ControllerPane extends BorderPane {

  private static volatile ControllerPane instance;
  private static final StringConverter<Double> SECONDS_LABEL_FORMATTER =
      new StringConverter<>() {
        @Override
        public String toString(Double ms) {
          return String.valueOf((int) (ms / 1000));
        }

        @Override
        public Double fromString(String s) {
          return Double.parseDouble(s) * 1000;
        }
      };

  private final TabPane tabPane;
  private final ConcurrentHashMap<String, PeerControlPanel> panels = new ConcurrentHashMap<>();
  private final DefaultPeerSettings defaultSettings = new DefaultPeerSettings();
  private volatile PalDirectory palDirectory;

  public ControllerPane() {
    instance = this;

    tabPane = new TabPane();
    tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

    setCenter(buildDefaultsPanel());
  }

  public static ControllerPane getInstance() {
    return instance;
  }

  public void setPalDirectory(PalDirectory palDirectory) {
    this.palDirectory = palDirectory;
  }

  /**
   * Returns the panel for the given peer UUID, creating it on the FX thread if it doesn't exist
   * yet. Called from RPC callback threads — blocks until the panel is ready.
   */
  public PeerControlPanel getOrCreatePanel(String peerUuid) {
    PeerControlPanel existing = panels.get(peerUuid);
    if (existing != null) {
      return existing;
    }

    // Use computeIfAbsent to ensure only one panel is created per peer
    CompletableFuture<PeerControlPanel> future = new CompletableFuture<>();

    PeerControlPanel panel =
        panels.computeIfAbsent(
            peerUuid,
            uuid -> {
              PeerState state = new PeerState(uuid, defaultSettings);
              Platform.runLater(() -> createPanelOnFxThread(state, future));

              try {
                return future.get(10, TimeUnit.SECONDS);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return createFallbackPanel(state);
              } catch (ExecutionException | TimeoutException e) {
                return createFallbackPanel(state);
              }
            });

    // Resolve peer name asynchronously
    if (panel.getPeerState().getPeerName() == null) {
      resolvePeerName(peerUuid, panel);
    }

    return panel;
  }

  private VBox buildDefaultsPanel() {
    VBox box = new VBox();
    box.getStyleClass().add("defaults-panel");

    Label title = new Label("New Peer Defaults");
    title.getStyleClass().add("defaults-title");

    Label subtitle = new Label("These settings will be applied to each new peer when it connects.");
    subtitle.getStyleClass().add("defaults-subtitle");

    // --- Row 1: Flow controls ---
    ToggleButton pauseToggle = new ToggleButton("Pause");
    pauseToggle.getStyleClass().add("pause-toggle");
    pauseToggle.setSelected(defaultSettings.isPaused());
    pauseToggle.setOnAction(e -> defaultSettings.setPaused(pauseToggle.isSelected()));

    ToggleButton stepToggle = new ToggleButton("Step");
    stepToggle.setSelected(defaultSettings.isStepMode());
    stepToggle.setOnAction(e -> defaultSettings.setStepMode(stepToggle.isSelected()));

    ToggleButton printToggle = new ToggleButton("Print");
    printToggle.setSelected(defaultSettings.isPrintMessages());
    printToggle.setOnAction(e -> defaultSettings.setPrintMessages(printToggle.isSelected()));

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    HBox flowRow = new HBox(8, pauseToggle, stepToggle, spacer, printToggle);
    flowRow.getStyleClass().add("control-card");

    // --- Row 2: Throttle ---
    ToggleButton throttleToggle = new ToggleButton("Throttle");
    throttleToggle.setSelected(defaultSettings.isThrottleOn());

    Slider throttleSlider = new Slider(0, 3000, defaultSettings.getThrottleDelayMs());
    throttleSlider.setShowTickMarks(true);
    throttleSlider.setShowTickLabels(true);
    throttleSlider.setMajorTickUnit(1000);
    throttleSlider.setMinorTickCount(49);
    throttleSlider.setSnapToTicks(true);
    throttleSlider.setBlockIncrement(20);
    throttleSlider.setLabelFormatter(SECONDS_LABEL_FORMATTER);
    throttleSlider.setDisable(!defaultSettings.isThrottleOn());
    HBox.setHgrow(throttleSlider, Priority.ALWAYS);

    Label throttleValueLabel = new Label(formatSeconds(defaultSettings.getThrottleDelayMs()));
    throttleValueLabel.getStyleClass().add("throttle-value");
    throttleValueLabel.setVisible(defaultSettings.isThrottleOn());
    throttleValueLabel.setManaged(defaultSettings.isThrottleOn());

    throttleSlider
        .valueProperty()
        .addListener(
            (obs, oldVal, newVal) -> {
              defaultSettings.setThrottleDelayMs(newVal.intValue());
              throttleValueLabel.setText(formatSeconds(newVal.intValue()));
            });

    throttleToggle.setOnAction(
        e -> {
          defaultSettings.setThrottleOn(throttleToggle.isSelected());
          throttleSlider.setDisable(!throttleToggle.isSelected());
          throttleValueLabel.setVisible(throttleToggle.isSelected());
          throttleValueLabel.setManaged(throttleToggle.isSelected());
        });

    HBox throttleRow = new HBox(8, throttleToggle, throttleSlider, throttleValueLabel);
    throttleRow.getStyleClass().add("control-card");

    Label waiting = new Label("Waiting for intercept callbacks...");
    waiting.getStyleClass().add("waiting-label");

    box.getChildren().addAll(title, subtitle, flowRow, throttleRow, waiting);
    return box;
  }

  private void createPanelOnFxThread(PeerState state, CompletableFuture<PeerControlPanel> future) {
    PeerControlPanel panel = new PeerControlPanel(state);
    Tab tab = new Tab(panel.getTabTitle(), panel);

    // Replace placeholder with TabPane on first panel
    if (tabPane.getTabs().isEmpty()) {
      setCenter(tabPane);
    }
    tabPane.getTabs().add(tab);
    tabPane.getSelectionModel().select(tab);

    future.complete(panel);
  }

  private PeerControlPanel createFallbackPanel(PeerState state) {
    // Fallback if FX thread creation times out — create panel without adding to UI
    return new PeerControlPanel(state);
  }

  private void resolvePeerName(String peerUuid, PeerControlPanel panel) {
    PalDirectory dir = palDirectory;
    if (dir == null) return;

    Thread thread =
        new Thread(
            () -> {
              try {
                PeerInfo info = dir.getPeer(UUID.fromString(peerUuid));
                if (info != null && info.getName() != null && !info.getName().isEmpty()) {
                  panel.updatePeerName(info.getName());
                  // Update tab title too
                  Platform.runLater(
                      () -> {
                        for (Tab tab : tabPane.getTabs()) {
                          if (tab.getContent() == panel) {
                            tab.setText(panel.getTabTitle());
                            break;
                          }
                        }
                      });
                }
              } catch (Exception e) {
                // Peer name resolution is best-effort
              }
            });
    thread.setDaemon(true);
    thread.start();
  }

  /** Unblock all waiting threads on shutdown. */
  public void shutdown() {
    for (PeerControlPanel panel : panels.values()) {
      panel.getPeerState().shutdown();
    }
  }

  private static String formatSeconds(int ms) {
    double s = ms / 1000.0;
    return s == (int) s ? (int) s + "s" : String.format("%.2fs", s);
  }
}
