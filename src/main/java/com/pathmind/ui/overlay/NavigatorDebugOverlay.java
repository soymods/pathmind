package com.pathmind.ui.overlay;

import com.pathmind.execution.PathmindNavigator;
import com.pathmind.ui.animation.AnimatedValue;
import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DrawContextBridge;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistent Pathmind-style debug HUD for the local navigator.
 */
public final class NavigatorDebugOverlay {
    private static final int OVERLAY_WIDTH = 420;
    private static final int MARGIN = 10;
    private static final int TOP_OFFSET = 10;
    private static final int PADDING = 6;
    private static final int LINE_SPACING = 2;
    private static final int SLIDE_OFFSET = 12;
    private static final int OPEN_DURATION_MS = 180;
    private static final int CLOSE_DURATION_MS = 140;

    private final AnimatedValue visibility;
    private boolean enabled;

    public NavigatorDebugOverlay() {
        this.visibility = new AnimatedValue(0f, AnimationHelper::easeOutCubic);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean toggle() {
        enabled = !enabled;
        return enabled;
    }

    public void render(DrawContext context, TextRenderer textRenderer, int screenWidth, int screenHeight) {
        PathmindNavigator.DebugInfo info = PathmindNavigator.getInstance().getDebugInfo();
        boolean shouldShow = enabled;

        visibility.animateTo(shouldShow ? 1f : 0f, shouldShow ? OPEN_DURATION_MS : CLOSE_DURATION_MS);
        visibility.tick();

        float progress = visibility.getValue();
        if (progress <= 0.001f) {
            return;
        }

        List<String> lines = buildLines(info);
        int lineHeight = textRenderer.fontHeight + LINE_SPACING;
        int overlayHeight = PADDING * 2 + (lineHeight * lines.size());
        int slideOffset = (int) ((1f - progress) * SLIDE_OFFSET);
        int overlayX = MARGIN - slideOffset;
        int overlayY = TOP_OFFSET;

        context.fill(
            overlayX,
            overlayY,
            overlayX + OVERLAY_WIDTH,
            overlayY + overlayHeight,
            applyAlpha(UITheme.OVERLAY_BACKGROUND, progress)
        );
        DrawContextBridge.drawBorder(
            context,
            overlayX,
            overlayY,
            OVERLAY_WIDTH,
            overlayHeight,
            applyAlpha(UITheme.BORDER_HIGHLIGHT, progress)
        );

        int textX = overlayX + PADDING;
        int textY = overlayY + PADDING;
        for (int i = 0; i < lines.size(); i++) {
            int color = i == 0 ? UITheme.ACCENT_SKY : UITheme.TEXT_HEADER;
            String line = trimTextToWidth(lines.get(i), textRenderer, OVERLAY_WIDTH - PADDING * 2);
            context.drawTextWithShadow(
                textRenderer,
                Text.literal(line),
                textX,
                textY + i * lineHeight,
                applyAlpha(color, progress)
            );
        }
    }

    private List<String> buildLines(PathmindNavigator.DebugInfo info) {
        List<String> lines = new ArrayList<>();
        lines.add("Navigator Debug");
        if (info == null) {
            lines.add("state=IDLE");
            return lines;
        }

        lines.add("state=" + info.state() + " controller=" + safe(info.controllerMode()));
        lines.add("prevController=" + safe(info.previousControllerMode()));
        lines.add("goal=" + safe(info.goalMode()) + " water=" + safe(info.waterMode()));
        lines.add("break=" + onOff(info.allowBlockBreaking()) + " place=" + onOff(info.allowBlockPlacing()) + " logs=" + onOff(info.eventLoggingEnabled()));
        lines.add("target=" + formatPos(info.targetPos()));
        lines.add("resolved=" + formatPos(info.resolvedGoalPos()));
        lines.add("waypoint=" + formatPos(info.activeWaypoint()) + " prev=" + formatPos(info.previousActiveWaypoint()));
        lines.add("controllerTarget=" + formatPos(info.controllerTarget()));
        lines.add("placeTarget=" + formatPos(info.lastPlaceTarget()));
        lines.add("pathIndex=" + info.pathIndex() + " nodes=" + info.nodeCount());
        lines.add("placeResult=" + safe(info.lastPlaceResult()));
        lines.add("replan=" + safe(info.lastReplanReason()) + " prev=" + safe(info.previousReplanReason()));
        lines.add("stuck=" + safe(info.lastStuckReason()) + " prev=" + safe(info.previousStuckReason()));
        if (!info.recentEvents().isEmpty()) {
            lines.add("Events");
            for (String event : info.recentEvents()) {
                lines.add("  " + event);
            }
        }
        return lines;
    }

    private String onOff(boolean enabled) {
        return enabled ? "on" : "off";
    }

    private String formatPos(net.minecraft.util.math.BlockPos pos) {
        if (pos == null) {
            return "--";
        }
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "--" : value;
    }

    private String trimTextToWidth(String text, TextRenderer textRenderer, int maxWidth) {
        if (textRenderer.getWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int ellipsisWidth = textRenderer.getWidth(ellipsis);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            builder.append(c);
            if (textRenderer.getWidth(builder.toString()) + ellipsisWidth > maxWidth) {
                builder.setLength(Math.max(0, builder.length() - 1));
                return builder + ellipsis;
            }
        }
        return text;
    }

    private int applyAlpha(int color, float alpha) {
        int baseAlpha = (color >>> 24) & 0xFF;
        int adjustedAlpha = (int) (baseAlpha * AnimationHelper.clamp01(alpha));
        return (adjustedAlpha << 24) | (color & 0x00FFFFFF);
    }
}
