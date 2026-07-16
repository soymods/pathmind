package com.pathmind.ui.overlay;

import com.pathmind.execution.ExecutionManager;
import com.pathmind.execution.PathmindNavigator;
import com.pathmind.nodes.Node;
import com.pathmind.ui.animation.AnimatedValue;
import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DrawContextBridge;
import com.pathmind.util.PathmindI18n;
import com.pathmind.util.TextRenderUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * HUD overlay that displays the currently active node in the top right corner.
 * Shows the node type, execution time, and status information.
 */
public class ActiveNodeOverlay {
    private static final int OVERLAY_WIDTH = 150;
    private static final int OVERLAY_HEIGHT = 60;
    private static final int COMPACT_MIN_WIDTH = 112;
    private static final int COMPACT_MAX_WIDTH = 210;
    private static final int COMPACT_HEIGHT = 18;
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
    public void render(GuiGraphicsExtractor context, Font textRenderer, int screenWidth, int screenHeight) {
        boolean isExecuting = executionManager.isExecuting();
        boolean showingCompletion = executionManager.isDisplayingCompletion();
        Node primaryNode = executionManager.getActiveNode();
        List<Node> activeNodes = executionManager.getActiveNodeChainSnapshot();
        PathmindNavigator.Snapshot navigatorSnapshot = PathmindNavigator.getInstance().getSnapshot();
        boolean shouldShow = ((isExecuting || showingCompletion) && (showingCompletion || !activeNodes.isEmpty()))
            || navigatorSnapshot != null;

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
                AnimationHelper.multiplyAlpha(UITheme.OVERLAY_BACKGROUND, progress));
            DrawContextBridge.drawBorder(context, overlayX, overlayY, OVERLAY_WIDTH, OVERLAY_HEIGHT,
                AnimationHelper.multiplyAlpha(UITheme.BORDER_HIGHLIGHT, progress));

            int textRightX = overlayX + OVERLAY_WIDTH - 8;

            String titleText = i == 0 ? tr("pathmind.overlay.activeNode") : tr("pathmind.overlay.activeNode.numbered", i + 1);
            int titleWidth = textRenderer.width(titleText);
            context.text(
                textRenderer,
                Component.literal(titleText),
                textRightX - titleWidth,
                overlayY + 6,
                AnimationHelper.multiplyAlpha(UITheme.ACCENT_SKY, progress)
            );

            String nodeTypeName;
            int nodeColor;
            if (showingCompletion) {
                if (node != null) {
                    nodeTypeName = node.getType().getDisplayName();
                    nodeColor = node.getColor();
                } else {
                    nodeTypeName = tr("pathmind.overlay.end");
                    nodeColor = UITheme.STATE_ERROR;
                }
            } else {
                nodeTypeName = node != null ? node.getType().getDisplayName() : "";
                nodeColor = node != null ? node.getColor() : UITheme.ACCENT_SKY;
            }

            int nodeTypeWidth = textRenderer.width(nodeTypeName);
            context.text(
                textRenderer,
                Component.literal(nodeTypeName),
                textRightX - nodeTypeWidth,
                overlayY + 18,
                AnimationHelper.multiplyAlpha(nodeColor, progress)
            );

            String timeText = i == 0
                ? tr("pathmind.overlay.nodeTime", formatDuration(executionManager.getActiveNodeDuration()))
                : tr("pathmind.overlay.nodeTimeUnknown");
            int timeWidth = textRenderer.width(timeText);
            context.text(
                textRenderer,
                Component.literal(timeText),
                textRightX - timeWidth,
                overlayY + 30,
                AnimationHelper.multiplyAlpha(UITheme.TEXT_HEADER, progress)
            );

            String statusText = showingCompletion ? tr("pathmind.overlay.finished") : tr("pathmind.overlay.executing");
            int statusWidth = textRenderer.width(statusText);
            context.text(
                textRenderer,
                Component.literal(statusText),
                textRightX - statusWidth,
                overlayY + 42,
                AnimationHelper.multiplyAlpha(showingCompletion ? UITheme.STATE_ERROR : UITheme.ACCENT_SKY, progress)
            );

            int dotX = overlayX + 8;
            int dotY = overlayY + 8;
            context.fill(dotX, dotY, dotX + 8, dotY + 8, AnimationHelper.multiplyAlpha(nodeColor, progress));
            DrawContextBridge.drawBorder(context, dotX, dotY, 8, 8, AnimationHelper.multiplyAlpha(UITheme.BORDER_HIGHLIGHT, progress));
        }

        if (navigatorSnapshot != null && cardCount < maxCards) {
            renderNavigatorCard(context, textRenderer, screenWidth, progress, cardCount, cardSpacing, navigatorSnapshot);
        }
    }

    /**
     * Render a one-line active-node indicator for menus and other non-world screens.
     */
    public void renderCompact(GuiGraphicsExtractor context, Font textRenderer, int screenWidth, int screenHeight) {
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

        Node node = showingCompletion ? primaryNode : (!activeNodes.isEmpty() ? activeNodes.get(0) : primaryNode);
        String nodeTypeName;
        int nodeColor;
        if (showingCompletion) {
            nodeTypeName = node != null ? node.getType().getDisplayName() : tr("pathmind.overlay.end");
            nodeColor = node != null ? node.getColor() : UITheme.STATE_ERROR;
        } else {
            nodeTypeName = node != null ? node.getType().getDisplayName() : tr("pathmind.overlay.running");
            nodeColor = node != null ? node.getColor() : UITheme.ACCENT_SKY;
        }

        int availableTextWidth = COMPACT_MAX_WIDTH - 18;
        String pattern = showingCompletion ? tr("pathmind.overlay.finishedNode", "") : tr("pathmind.overlay.runningNode", "");
        int valueWidth = availableTextWidth - textRenderer.width(pattern);
        String text = showingCompletion
            ? tr("pathmind.overlay.finishedNode", trimToWidth(textRenderer, nodeTypeName, valueWidth))
            : tr("pathmind.overlay.runningNode", trimToWidth(textRenderer, nodeTypeName, valueWidth));
        int textWidth = textRenderer.width(text);
        int overlayWidth = Math.max(COMPACT_MIN_WIDTH, Math.min(COMPACT_MAX_WIDTH, textWidth + 18));
        int slideOffset = (int) ((1f - progress) * SLIDE_OFFSET);
        int overlayX = screenWidth - overlayWidth - MARGIN + slideOffset;
        int overlayY = MARGIN;

        context.fill(overlayX, overlayY, overlayX + overlayWidth, overlayY + COMPACT_HEIGHT,
            AnimationHelper.multiplyAlpha(UITheme.OVERLAY_BACKGROUND, progress));
        DrawContextBridge.drawBorder(context, overlayX, overlayY, overlayWidth, COMPACT_HEIGHT,
            AnimationHelper.multiplyAlpha(UITheme.BORDER_HIGHLIGHT, progress));
        context.fill(overlayX + 5, overlayY + 5, overlayX + 10, overlayY + 10, AnimationHelper.multiplyAlpha(nodeColor, progress));
        context.text(
            textRenderer,
            Component.literal(text),
            overlayX + 14,
            overlayY + 5,
            AnimationHelper.multiplyAlpha(UITheme.TEXT_HEADER, progress)
        );
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

    private void renderNavigatorCard(GuiGraphicsExtractor context, Font textRenderer, int screenWidth, float progress,
                                     int cardIndex, int cardSpacing, PathmindNavigator.Snapshot snapshot) {
        int slideOffset = (int) ((1f - progress) * SLIDE_OFFSET);
        int overlayX = screenWidth - OVERLAY_WIDTH - MARGIN + slideOffset;
        int overlayY = MARGIN + (cardIndex * (OVERLAY_HEIGHT + cardSpacing));

        context.fill(overlayX, overlayY, overlayX + OVERLAY_WIDTH, overlayY + OVERLAY_HEIGHT,
            AnimationHelper.multiplyAlpha(UITheme.OVERLAY_BACKGROUND, progress));
        DrawContextBridge.drawBorder(context, overlayX, overlayY, OVERLAY_WIDTH, OVERLAY_HEIGHT,
            AnimationHelper.multiplyAlpha(UITheme.BORDER_HIGHLIGHT, progress));

        int textRightX = overlayX + OVERLAY_WIDTH - 8;
        String titleText = tr("pathmind.navigator.title");
        context.text(
            textRenderer,
            Component.literal(titleText),
            textRightX - textRenderer.width(titleText),
            overlayY + 6,
            AnimationHelper.multiplyAlpha(UITheme.ACCENT_SKY, progress)
        );

        String targetText = formatTarget(snapshot.targetPos());
        context.text(
            textRenderer,
            Component.literal(targetText),
            textRightX - textRenderer.width(targetText),
            overlayY + 18,
            AnimationHelper.multiplyAlpha(UITheme.TEXT_HEADER, progress)
        );

        String distanceText = snapshot.distance() >= 0.0D
            ? tr("pathmind.navigator.distance", snapshot.distance())
            : tr("pathmind.navigator.distanceUnknown");
        context.text(
            textRenderer,
            Component.literal(distanceText),
            textRightX - textRenderer.width(distanceText),
            overlayY + 30,
            AnimationHelper.multiplyAlpha(UITheme.TEXT_PRIMARY, progress)
        );

        String statusText = switch (snapshot.state()) {
            case PREVIEW -> tr("pathmind.navigator.status.preview");
            case PATHING -> tr("pathmind.navigator.status.moving");
            case ARRIVED -> tr("pathmind.navigator.status.arrived");
            case STOPPED -> tr("pathmind.navigator.status.stopped");
            case FAILED -> tr("pathmind.navigator.status.failed");
            case IDLE -> tr("pathmind.navigator.status.idle");
        };
        int statusColor = snapshot.state() == PathmindNavigator.State.FAILED
            ? UITheme.STATE_ERROR
            : UITheme.ACCENT_SKY;
        context.text(
            textRenderer,
            Component.literal(statusText),
            textRightX - textRenderer.width(statusText),
            overlayY + 42,
            AnimationHelper.multiplyAlpha(statusColor, progress)
        );

        int dotX = overlayX + 8;
        int dotY = overlayY + 8;
        context.fill(dotX, dotY, dotX + 8, dotY + 8, AnimationHelper.multiplyAlpha(UITheme.ACCENT_SKY, progress));
        DrawContextBridge.drawBorder(context, dotX, dotY, 8, 8, AnimationHelper.multiplyAlpha(UITheme.BORDER_HIGHLIGHT, progress));
    }

    private String formatTarget(net.minecraft.core.BlockPos targetPos) {
        if (targetPos == null) {
            return tr("pathmind.navigator.targetUnknown");
        }
        return tr("pathmind.navigator.target", targetPos.getX(), targetPos.getY(), targetPos.getZ());
    }

    private String trimToWidth(Font textRenderer, String value, int maxWidth) {
        return TextRenderUtil.trimWithEllipsis(textRenderer, value, maxWidth);
    }

    private static String tr(String key, Object... args) {
        return PathmindI18n.tr(key, args);
    }
}
