package io.quasient.pal.pocs.controller;

/**
 * Holds default control settings applied to every new peer when its panel is created. The user can
 * tweak these on the placeholder screen before any peers connect, providing a way to pre-configure
 * the initial state.
 *
 * <p>All fields are volatile so the FX thread can write them while callback threads read them
 * concurrently during panel creation.
 */
public class DefaultPeerSettings {

  private volatile boolean paused = true;
  private volatile boolean throttleOn;
  private volatile int throttleDelayMs;
  private volatile boolean stepMode;
  private volatile boolean printMessages = true;

  public boolean isPaused() {
    return paused;
  }

  public void setPaused(boolean paused) {
    this.paused = paused;
  }

  public boolean isThrottleOn() {
    return throttleOn;
  }

  public void setThrottleOn(boolean throttleOn) {
    this.throttleOn = throttleOn;
  }

  public int getThrottleDelayMs() {
    return throttleDelayMs;
  }

  public void setThrottleDelayMs(int throttleDelayMs) {
    this.throttleDelayMs = throttleDelayMs;
  }

  public boolean isStepMode() {
    return stepMode;
  }

  public void setStepMode(boolean stepMode) {
    this.stepMode = stepMode;
  }

  public boolean isPrintMessages() {
    return printMessages;
  }

  public void setPrintMessages(boolean printMessages) {
    this.printMessages = printMessages;
  }
}
