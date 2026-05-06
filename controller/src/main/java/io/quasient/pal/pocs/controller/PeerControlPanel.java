package io.quasient.pal.pocs.controller;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/** Per-peer control panel UI. Each intercepted peer gets one of these inside a Tab. */
public class PeerControlPanel extends VBox {

  private static final int MAX_LOG_LINES = 5000;
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

  private final PeerState peerState;
  private final Label headerLabel;
  private final Label statusLabel;
  private final TextArea messageLog;
  private final ToggleButton throttleToggle;
  private final Slider throttleSlider;
  private final Label throttleValueLabel;
  private final ToggleButton pauseToggle;
  private final ToggleButton stepToggle;
  private final Button nextButton;
  private final ToggleButton printToggle;

  private int logLineCount;

  public PeerControlPanel(PeerState peerState) {
    this.peerState = peerState;
    getStyleClass().add("peer-panel");

    // --- Header ---
    headerLabel = new Label(formatHeader());
    headerLabel.getStyleClass().add("peer-header");

    // --- Row 1: Flow controls ---
    pauseToggle = new ToggleButton("Pause");
    pauseToggle.getStyleClass().add("pause-toggle");
    pauseToggle.setSelected(peerState.isPaused());
    pauseToggle.setOnAction(
        e -> {
          peerState.setPaused(pauseToggle.isSelected());
          updateStatus();
        });

    stepToggle = new ToggleButton("Step");
    stepToggle.setSelected(peerState.isStepMode());

    nextButton = new Button("Next");
    nextButton.getStyleClass().add("next-button");
    nextButton.setDisable(!peerState.isStepMode());
    nextButton.setOnAction(e -> peerState.releaseStep());

    stepToggle.setOnAction(
        e -> {
          peerState.setStepMode(stepToggle.isSelected());
          nextButton.setDisable(!stepToggle.isSelected());
          updateStatus();
        });

    printToggle = new ToggleButton("Print");
    printToggle.setSelected(peerState.isPrintMessages());
    printToggle.setOnAction(
        e -> {
          peerState.setPrintMessages(printToggle.isSelected());
          updateStatus();
        });

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    HBox flowRow = new HBox(8, pauseToggle, stepToggle, nextButton, spacer, printToggle);
    flowRow.getStyleClass().add("control-card");

    // --- Row 2: Throttle ---
    throttleToggle = new ToggleButton("Throttle");
    throttleToggle.setSelected(peerState.isThrottleOn());

    throttleSlider = new Slider(0, 3000, peerState.getThrottleDelayMs());
    throttleSlider.setShowTickMarks(true);
    throttleSlider.setShowTickLabels(true);
    throttleSlider.setMajorTickUnit(1000);
    throttleSlider.setMinorTickCount(49);
    throttleSlider.setSnapToTicks(true);
    throttleSlider.setBlockIncrement(20);
    throttleSlider.setLabelFormatter(SECONDS_LABEL_FORMATTER);
    throttleSlider.setDisable(!peerState.isThrottleOn());
    HBox.setHgrow(throttleSlider, Priority.ALWAYS);

    throttleValueLabel = new Label(formatSeconds(peerState.getThrottleDelayMs()));
    throttleValueLabel.getStyleClass().add("throttle-value");
    throttleValueLabel.setVisible(peerState.isThrottleOn());
    throttleValueLabel.setManaged(peerState.isThrottleOn());

    throttleSlider
        .valueProperty()
        .addListener(
            (obs, oldVal, newVal) -> {
              peerState.setThrottleDelayMs(newVal.intValue());
              throttleValueLabel.setText(formatSeconds(newVal.intValue()));
            });

    throttleToggle.setOnAction(
        e -> {
          peerState.setThrottleOn(throttleToggle.isSelected());
          throttleSlider.setDisable(!throttleToggle.isSelected());
          throttleValueLabel.setVisible(throttleToggle.isSelected());
          throttleValueLabel.setManaged(throttleToggle.isSelected());
          updateStatus();
        });

    HBox throttleRow = new HBox(8, throttleToggle, throttleSlider, throttleValueLabel);
    throttleRow.getStyleClass().add("control-card");

    // --- Message log ---
    messageLog = new TextArea();
    messageLog.getStyleClass().add("message-log");
    messageLog.setEditable(false);
    messageLog.setWrapText(true);
    messageLog.setPrefRowCount(15);
    VBox.setVgrow(messageLog, Priority.ALWAYS);

    // --- Status bar ---
    statusLabel = new Label();
    statusLabel.getStyleClass().add("status-bar");
    updateStatus();

    // Wire message listener
    peerState.setMessageListener(this::onMessage);

    getChildren().addAll(headerLabel, flowRow, throttleRow, messageLog, statusLabel);
  }

  private void onMessage(String message) {
    Platform.runLater(
        () -> {
          if (logLineCount >= MAX_LOG_LINES) {
            // Trim oldest lines
            String text = messageLog.getText();
            int cutAt = text.indexOf('\n', text.length() / 3);
            if (cutAt > 0) {
              messageLog.setText(text.substring(cutAt + 1));
              logLineCount = logLineCount * 2 / 3;
            }
          }
          messageLog.appendText(message + "\n");
          logLineCount++;
          updateStatus();
        });
  }

  public void updatePeerName(String name) {
    Platform.runLater(
        () -> {
          peerState.setPeerName(name);
          headerLabel.setText(formatHeader());
        });
  }

  private String formatHeader() {
    String name = peerState.getPeerName();
    String uuid = peerState.getPeerUuid();
    String shortUuid = uuid.length() > 8 ? uuid.substring(0, 8) + "..." : uuid;
    if (name != null && !name.isEmpty()) {
      return "Peer: " + name + " (" + shortUuid + ")";
    }
    return "Peer: " + uuid;
  }

  private void updateStatus() {
    StringBuilder sb = new StringBuilder();
    if (peerState.isPaused()) sb.append("Paused");
    else if (peerState.isStepMode()) sb.append("Stepping");
    else sb.append("Running");

    if (peerState.isThrottleOn()) {
      sb.append(" | Throttle: ").append(formatSeconds(peerState.getThrottleDelayMs()));
    }
    sb.append(" | Callbacks: ").append(peerState.getCallbackCount());
    statusLabel.setText(sb.toString());
  }

  public PeerState getPeerState() {
    return peerState;
  }

  /** Returns a short display name suitable for a tab title. */
  public String getTabTitle() {
    String name = peerState.getPeerName();
    if (name != null && !name.isEmpty()) return name;
    String uuid = peerState.getPeerUuid();
    return uuid.length() > 8 ? uuid.substring(0, 8) + "..." : uuid;
  }

  private static String formatSeconds(int ms) {
    double s = ms / 1000.0;
    return s == (int) s ? (int) s + "s" : String.format("%.2fs", s);
  }
}
