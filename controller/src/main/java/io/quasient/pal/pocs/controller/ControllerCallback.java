package io.quasient.pal.pocs.controller;

import io.quasient.pal.common.lang.intercept.InterceptCallbackResponse;
import io.quasient.pal.common.lang.intercept.InterceptContext;

/**
 * Intercept callback that routes intercept events to the JavaFX controller UI.
 *
 * <p>This class is referenced by name in YAML intercept bundles and invoked reflectively by PAL's
 * runtime. The {@link #handle} method must be static per PAL's intercept callback contract. It
 * delegates to {@link ControllerPane#getInstance()} to find the UI, and blocks the calling RPC
 * thread via {@link PeerState#applyControls(InterceptContext)} until the user allows the
 * intercepted method to proceed.
 */
public class ControllerCallback {

  public static InterceptCallbackResponse handle(InterceptContext ctx) throws Exception {
    ControllerPane pane = ControllerPane.getInstance();
    if (pane == null) {
      // UI not ready yet — allow the intercepted method to proceed without blocking
      return new InterceptCallbackResponse();
    }

    String peerUuid = ctx.getInterceptedPeerUuid();
    PeerControlPanel panel = pane.getOrCreatePanel(peerUuid);
    panel.getPeerState().applyControls(ctx);

    return new InterceptCallbackResponse();
  }
}
