package com.pathmind.ui.overlay;

import com.pathmind.execution.ExecutionManager;
import com.pathmind.nodes.Node;
import com.pathmind.ui.animation.AnimatedValue;
import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.theme.UITheme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;
import com.pathmind.util.DrawContextBridge;
import java.util.ArrayList;
import java.util.List;

/**
 * HUD overlay that displays the currently active node in the top right corner.
 * Shows the node type, execution time, and status information.
 */
public class ActiveNodeOverlay {
    private static final int OVERLAY_WIDTH = 150;
    private static final int OVERLAY_HEIGHT = 60;
    private static final int MARGIN = 10;
    private static final int SLIDE_OFFSET = 14;
    private static final int OPEN_DURATION_MS = 180;
    private static final int CLOSE_DURATION_MS = 140;
    
    private final ExecutionManager executionManager;
    private final AnimatedValue visibility;
    
    public ActiveNodeOverlay() {
        this.executionManager = ExecutionManager.getInstance();
        this.visibility = new AnimatedValue(0f, AnimationHelper::easeOutCubic);
    }
    
    /**
     * Render the overlay if execution is active
     */
    public void render(DrawContext context, TextRenderer textRenderer, int screenWidth, int screenHeight) {
        boolean isExecuting = executionManager.isExecuting();
        boolean showingCompletion = executionManager.isDisplayingCompletion();
        Node primaryNode = executionManager.getActiveNode();
        List<Node> activeNodes = executionManager.getActiveNodeChainSnapshot();
        boolean shouldShow = (isExecuting || showingCompletion) && (showingCompletion || !activeNodes.isEmpty());

        visibility.animateTo(shouldShow ? 1f : 0f, shouldShow ? OPEN_DURATION_MS : CLOSE_DURATION_MS);
        visibility.tick();

        float progress = visibility.getValue();
        if (progress <= 0.001f) {
            return;
        }

        List<Node> nodesToRender = new ArrayList<>();
        if (showingCompletion) {
            nodesToRender.add(primaryNode);
        } else {
            nodesToRender.addAll(activeNodes);
        }

        int cardSpacing = 6;
        int maxCards = Math.max(1, (screenHeight - MARGIN) / (OVERLAY_HEIGHT + cardSpacing));
        int cardCount = Math.min(nodesToRender.size(), maxCards);

        for (int i = 0; i < cardCount; i++) {
            Node node = nodesToRender.get(i);
            int slideOffset = (int) ((1f - progress) * SLIDE_OFFSET);
            int overlayX = screenWidth - OVERLAY_WIDTH - MARGIN + slideOffset;
            int overlayY = MARGIN + (i * (OVERLAY_HEIGHT + cardSpacing));

            context.fill(overlayX, overlayY, overlayX + OVERLAY_WIDTH, overlayY + OVERLAY_HEIGHT,
                applyAlpha(UITheme.OVERLAY_BACKGROUND, progress));
            DrawContextBridge.drawBorder(context, overlayX, overlayY, OVERLAY_WIDTH, OVERLAY_HEIGHT,
                applyAlpha(UITheme.BORDER_HIGHLIGHT, progress));

            int textRightX = overlayX + OVERLAY_WIDTH - 8;

            String titleText = i == 0 ? "Active Node" : "Active Node " + (i + 1);
            int titleWidth = textRenderer.getWidth(titleText);
            context.drawTextWithShadow(
                textRenderer,
                Text.literal(titleText),
                textRightX - titleWidth,
                overlayY + 6,
                applyAlpha(UITheme.ACCENT_SKY, progress)
            );

            String nodeTypeName;
            int nodeColor;
            if (showingCompletion) {
                if (node != null) {
                    nodeTypeName = node.getType().getDisplayName();
                    nodeColor = node.getType().getColor();
                } else {
                    nodeTypeName = "End";
                    nodeColor = UITheme.STATE_ERROR;
                }
            } else {
                nodeTypeName = node != null ? node.getType().getDisplayName() : "";
                nodeColor = node != null ? node.getType().getColor() : UITheme.ACCENT_SKY;
            }

            int nodeTypeWidth = textRenderer.getWidth(nodeTypeName);
            context.drawTextWithShadow(
                textRenderer,
                Text.literal(nodeTypeName),
                textRightX - nodeTypeWidth,
                overlayY + 18,
                applyAlpha(nodeColor, progress)
            );

            String timeText = i == 0
                ? "Node Time: " + formatDuration(executionManager.getActiveNodeDuration())
                : "Node Time: --";
            int timeWidth = textRenderer.getWidth(timeText);
            context.drawTextWithShadow(
                textRenderer,
                Text.literal(timeText),
                textRightX - timeWidth,
                overlayY + 30,
                applyAlpha(UITheme.TEXT_HEADER, progress)
            );

            String statusText = showingCompletion ? "Finished" : "Executing...";
            int statusWidth = textRenderer.getWidth(statusText);
            context.drawTextWithShadow(
                textRenderer,
                Text.literal(statusText),
                textRightX - statusWidth,
                overlayY + 42,
                applyAlpha(showingCompletion ? UITheme.STATE_ERROR : UITheme.ACCENT_SKY, progress)
            );

            int dotX = overlayX + 8;
            int dotY = overlayY + 8;
            context.fill(dotX, dotY, dotX + 8, dotY + 8, applyAlpha(nodeColor, progress));
            DrawContextBridge.drawBorder(context, dotX, dotY, 8, 8, applyAlpha(UITheme.BORDER_HIGHLIGHT, progress));
        }
    }
    
    /**
     * Format duration in milliseconds to a readable format
     */
    private String formatDuration(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        
        if (minutes > 0) {
            return String.format("%d:%02d", minutes, seconds);
        } else {
            return String.format("%d.%ds", seconds, (milliseconds % 1000) / 100);
        }
    }

    private int applyAlpha(int color, float alpha) {
        int baseAlpha = (color >>> 24) & 0xFF;
        int adjustedAlpha = (int) (baseAlpha * AnimationHelper.clamp01(alpha));
        return (adjustedAlpha << 24) | (color & 0x00FFFFFF);
    }
}
