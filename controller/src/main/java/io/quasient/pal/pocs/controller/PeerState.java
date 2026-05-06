package io.quasient.pal.pocs.controller;

import io.quasient.pal.common.lang.intercept.InterceptContext;
import io.quasient.pal.messages.colfer.ClassMethodCall;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InstanceMethodCall;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Thread-safe per-peer control state. This is the blocking engine that RPC callback threads
 * interact with. The JavaFX UI thread writes toggle states; RPC threads read them and block
 * accordingly in {@link #applyControls(InterceptContext)}.
 */
public class PeerState {

  private static final int PAUSE_POLL_MS = 50;

  private final String peerUuid;
  private volatile String peerName;

  private volatile boolean throttleOn;
  private volatile int throttleDelayMs;
  private volatile boolean paused;
  private volatile boolean stepMode;
  private volatile boolean printMessages;

  private final Semaphore stepSemaphore = new Semaphore(0, true);
  private final AtomicLong callbackCount = new AtomicLong();

  private volatile Consumer<String> messageListener;

  public PeerState(String peerUuid, DefaultPeerSettings defaults) {
    this.peerUuid = peerUuid;
    this.paused = defaults.isPaused();
    this.throttleOn = defaults.isThrottleOn();
    this.throttleDelayMs = defaults.getThrottleDelayMs();
    this.stepMode = defaults.isStepMode();
    this.printMessages = defaults.isPrintMessages();
  }

  /**
   * Called by the callback handler on the RPC thread. Blocks the caller according to current
   * control state (throttle, pause, step-through).
   */
  public void applyControls(InterceptContext ctx) {
    callbackCount.incrementAndGet();

    if (printMessages) {
      String message = formatMessage(ctx);
      Consumer<String> listener = messageListener;
      if (listener != null && message != null) {
        listener.accept(message);
      }
    }

    if (throttleOn) {
      try {
        Thread.sleep(throttleDelayMs);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }

    while (paused) {
      try {
        Thread.sleep(PAUSE_POLL_MS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }

    if (stepMode) {
      try {
        stepSemaphore.acquire();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  /** Release one step — wired to the "Next" button. */
  public void releaseStep() {
    stepSemaphore.release(1);
  }

  /** Unblock all waiting threads so they can drain on shutdown. */
  public void shutdown() {
    paused = false;
    stepMode = false;
    stepSemaphore.release(Integer.MAX_VALUE / 2);
  }

  private String formatMessage(InterceptContext ctx) {
    ExecMessage exec = ctx.getExec();
    if (exec == null) {
      Object[] args = ctx.getArgs();
      return "callback(" + (args != null ? Arrays.toString(args) : "") + ")";
    }

    String className = null;
    String methodName = null;

    ClassMethodCall cmc = exec.getClassMethodCall();
    if (cmc != null && cmc.getClazz() != null) {
      className = cmc.getClazz().getName();
      methodName = cmc.getName();
    }

    if (className == null) {
      InstanceMethodCall imc = exec.getInstanceMethodCall();
      if (imc != null && imc.getClazz() != null) {
        className = imc.getClazz().getName();
        methodName = imc.getName();
      }
    }

    if (className == null) {
      return "callback";
    }

    // Shorten class name to simple name
    int lastDot = className.lastIndexOf('.');
    String simpleName = lastDot >= 0 ? className.substring(lastDot + 1) : className;

    Object[] args = ctx.getArgs();
    String argsStr = args != null && args.length > 0 ? formatArgs(args) : "";
    return simpleName + "." + methodName + "(" + argsStr + ")";
  }

  private String formatArgs(Object[] args) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < args.length; i++) {
      if (i > 0) sb.append(", ");
      if (args[i] instanceof String) {
        sb.append('"').append(args[i]).append('"');
      } else {
        sb.append(args[i]);
      }
    }
    return sb.toString();
  }

  // --- Getters and setters ---

  public String getPeerUuid() {
    return peerUuid;
  }

  public String getPeerName() {
    return peerName;
  }

  public void setPeerName(String peerName) {
    this.peerName = peerName;
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

  public boolean isPaused() {
    return paused;
  }

  public void setPaused(boolean paused) {
    this.paused = paused;
  }

  public boolean isStepMode() {
    return stepMode;
  }

  public void setStepMode(boolean stepMode) {
    this.stepMode = stepMode;
    if (!stepMode) {
      // Drain any threads waiting on step when step mode is turned off
      stepSemaphore.release(stepSemaphore.getQueueLength() + 1);
    }
  }

  public boolean isPrintMessages() {
    return printMessages;
  }

  public void setPrintMessages(boolean printMessages) {
    this.printMessages = printMessages;
  }

  public long getCallbackCount() {
    return callbackCount.get();
  }

  public void setMessageListener(Consumer<String> messageListener) {
    this.messageListener = messageListener;
  }
}
