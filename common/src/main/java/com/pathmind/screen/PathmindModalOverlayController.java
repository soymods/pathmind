package com.pathmind.screen;

import com.pathmind.ui.animation.PopupAnimationHandler;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DrawContextBridge;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Coordinates modal visibility, scrim selection, and popup cutouts for the editor.
 */
final class PathmindModalOverlayController {
    interface Host {
        int screenWidth();
        int screenHeight();
        boolean isParameterOverlayVisible();
        boolean isBookTextEditorVisible();
        int parameterOverlayScrimColor();
        int bookTextEditorScrimColor();
        int[] parameterOverlayBounds();
        int[] bookTextEditorBounds();
    }

    private final Host host;
    private final PopupAnimationHandler[] screenPopups;
    private boolean cutoutActive = false;
    private int cutoutX = 0;
    private int cutoutY = 0;
    private int cutoutWidth = 0;
    private int cutoutHeight = 0;

    PathmindModalOverlayController(Host host, PopupAnimationHandler... screenPopups) {
        this.host = host;
        this.screenPopups = screenPopups;
    }

    boolean isObscuringWorkspace() {
        return host.isParameterOverlayVisible()
            || host.isBookTextEditorVisible()
            || isScreenPopupVisible();
    }

    boolean isScreenPopupVisible() {
        for (PopupAnimationHandler popup : screenPopups) {
            if (popup != null && popup.isVisible()) {
                return true;
            }
        }
        return false;
    }

    void resetCutout() {
        cutoutActive = false;
    }

    void setCutout(int x, int y, int width, int height) {
        cutoutActive = true;
        cutoutX = x;
        cutoutY = y;
        cutoutWidth = width;
        cutoutHeight = height;
    }

    void setCutoutForNodeOverlay() {
        if (host.isParameterOverlayVisible()) {
            setCutoutFromBounds(host.parameterOverlayBounds());
            return;
        }
        if (host.isBookTextEditorVisible()) {
            setCutoutFromBounds(host.bookTextEditorBounds());
        }
    }

    void renderScrim(GuiGraphics context) {
        if (!isObscuringWorkspace()) {
            return;
        }
        int color = activeOverlayColor();
        int screenWidth = host.screenWidth();
        int screenHeight = host.screenHeight();
        if (!cutoutActive || cutoutWidth <= 0 || cutoutHeight <= 0) {
            DrawContextBridge.fillOverlay(context, 0, 0, screenWidth, screenHeight, color);
            return;
        }
        int cutoutRight = cutoutX + cutoutWidth;
        int cutoutBottom = cutoutY + cutoutHeight;
        if (cutoutY > 0) {
            DrawContextBridge.fillOverlay(context, 0, 0, screenWidth, cutoutY, color);
        }
        if (cutoutX > 0) {
            DrawContextBridge.fillOverlay(context, 0, cutoutY, cutoutX, cutoutBottom, color);
        }
        if (cutoutRight < screenWidth) {
            DrawContextBridge.fillOverlay(context, cutoutRight, cutoutY, screenWidth, cutoutBottom, color);
        }
        if (cutoutBottom < screenHeight) {
            DrawContextBridge.fillOverlay(context, 0, cutoutBottom, screenWidth, screenHeight, color);
        }
    }

    private int activeOverlayColor() {
        for (PopupAnimationHandler popup : screenPopups) {
            if (popup != null && popup.isVisible()) {
                return popup.getAnimatedBackgroundColor(UITheme.OVERLAY_BACKGROUND);
            }
        }
        if (host.isParameterOverlayVisible()) {
            return host.parameterOverlayScrimColor();
        }
        if (host.isBookTextEditorVisible()) {
            return host.bookTextEditorScrimColor();
        }
        return UITheme.OVERLAY_BACKGROUND;
    }

    private void setCutoutFromBounds(int[] bounds) {
        if (bounds != null && bounds.length >= 4 && bounds[2] > 0 && bounds[3] > 0) {
            setCutout(bounds[0], bounds[1], bounds[2], bounds[3]);
        }
    }
}
