package com.pathmind.ui.overlay;

import com.pathmind.ui.animation.AnimatedValue;
import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DrawContextBridge;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Bottom-right HUD notification stack for node failures.
 */
public final class NodeErrorNotificationOverlay {
    private static final int MAX_NOTIFICATIONS = 4;
    private static final int CARD_WIDTH = 228;
    private static final int PADDING_X = 10;
    private static final int PADDING_Y = 7;
    private static final int LINE_SPACING = 1;
    private static final int CARD_SPACING = 6;
    private static final int MARGIN = 10;
    private static final int SLIDE_OFFSET = 18;
    private static final int ACCENT_STRIP_WIDTH = 3;
    private static final int OPEN_DURATION_MS = 180;
    private static final int CLOSE_DURATION_MS = 180;
    private static final int DISPLAY_DURATION_MS = 3200;
    private static final int PROGRESS_BAR_HEIGHT = 6;
    private static final int PROGRESS_BAR_TOP_MARGIN = 6;

    private static final NodeErrorNotificationOverlay INSTANCE = new NodeErrorNotificationOverlay();

    private final List<NotificationEntry> notifications = new ArrayList<>();

    private NodeErrorNotificationOverlay() {
    }

    public static NodeErrorNotificationOverlay getInstance() {
        return INSTANCE;
    }

    public synchronized void show(String message, int nodeColor) {
        if (message == null || message.isEmpty()) {
            return;
        }

        notifications.add(0, new NotificationEntry(null, message, nodeColor));
        while (notifications.size() > MAX_NOTIFICATIONS) {
            notifications.remove(notifications.size() - 1);
        }
    }

    public synchronized void showProgress(String key, String message, int nodeColor, float progress) {
        if (key == null || key.isEmpty() || message == null || message.isEmpty()) {
            return;
        }
        for (NotificationEntry entry : notifications) {
            if (entry.matchesKey(key)) {
                entry.updateProgress(message, nodeColor, progress);
                return;
            }
        }

        notifications.add(0, NotificationEntry.progress(key, message, nodeColor, progress));
        while (notifications.size() > MAX_NOTIFICATIONS) {
            notifications.remove(notifications.size() - 1);
        }
    }

    public synchronized void dismiss(String key) {
        if (key == null || key.isEmpty()) {
            return;
        }
        for (NotificationEntry entry : notifications) {
            if (entry.matchesKey(key)) {
                entry.dismiss();
            }
        }
    }

    public synchronized void clear() {
        notifications.clear();
    }

    public synchronized void render(DrawContext context, TextRenderer textRenderer, int screenWidth, int screenHeight) {
        if (textRenderer == null || notifications.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        int y = screenHeight - MARGIN;
        Iterator<NotificationEntry> iterator = notifications.iterator();
        while (iterator.hasNext()) {
            NotificationEntry entry = iterator.next();
            entry.tick(now);
            if (entry.isFinished()) {
                iterator.remove();
                continue;
            }

            int textStartX = PADDING_X + ACCENT_STRIP_WIDTH + 7;
            int textWidthLimit = CARD_WIDTH - textStartX - PADDING_X;
            List<OrderedText> wrappedLines = textRenderer.wrapLines(Text.literal(entry.message), textWidthLimit);
            if (wrappedLines.isEmpty()) {
                wrappedLines = List.of(Text.literal(entry.message).asOrderedText());
            }

            int textHeight = wrappedLines.size() * textRenderer.fontHeight
                + Math.max(0, wrappedLines.size() - 1) * LINE_SPACING;
            boolean showProgress = entry.hasProgress();
            int progressHeight = showProgress ? PROGRESS_BAR_TOP_MARGIN + PROGRESS_BAR_HEIGHT : 0;
            int cardHeight = textHeight + PADDING_Y * 2 + progressHeight;
            y -= cardHeight;

            float progress = entry.visibility.getValue();
            int slideOffset = (int) ((1f - progress) * SLIDE_OFFSET);
            int x = screenWidth - CARD_WIDTH - MARGIN + slideOffset;
            int borderColor = applyAlpha(entry.nodeColor, progress);
            int backgroundColor = applyAlpha(UITheme.BACKGROUND_SECONDARY, progress);
            int innerShadeColor = applyAlpha(AnimationHelper.darken(UITheme.BACKGROUND_TERTIARY, 0.72f), progress);
            int accentColor = applyAlpha(AnimationHelper.brighten(entry.nodeColor, 1.08f), progress);

            context.fill(x, y, x + CARD_WIDTH, y + cardHeight, backgroundColor);
            context.fill(x + 1, y + 1, x + CARD_WIDTH - 1, y + cardHeight - 1, innerShadeColor);
            context.fill(x + 1, y + 1, x + 1 + ACCENT_STRIP_WIDTH, y + cardHeight - 1, accentColor);
            DrawContextBridge.drawBorder(context, x, y, CARD_WIDTH, cardHeight, borderColor);

            int textY = y + PADDING_Y;
            for (OrderedText line : wrappedLines) {
                context.drawTextWithShadow(textRenderer, line, x + textStartX, textY, borderColor);
                textY += textRenderer.fontHeight + LINE_SPACING;
            }

            if (showProgress) {
                int barX = x + textStartX;
                int barY = y + PADDING_Y + textHeight + PROGRESS_BAR_TOP_MARGIN;
                int barWidth = CARD_WIDTH - textStartX - PADDING_X;
                int barFillWidth = Math.max(0, Math.min(barWidth, Math.round(barWidth * entry.getProgress())));
                int barBackground = applyAlpha(AnimationHelper.darken(UITheme.BACKGROUND_TERTIARY, 0.88f), progress);
                int barFill = applyAlpha(accentColor, progress);
                context.fill(barX, barY, barX + barWidth, barY + PROGRESS_BAR_HEIGHT, barBackground);
                if (barFillWidth > 0) {
                    context.fill(barX, barY, barX + barFillWidth, barY + PROGRESS_BAR_HEIGHT, barFill);
                }
                DrawContextBridge.drawBorder(context, barX, barY, barWidth, PROGRESS_BAR_HEIGHT, borderColor);
            }

            y -= CARD_SPACING;
        }
    }

    private int applyAlpha(int color, float alpha) {
        int baseAlpha = (color >>> 24) & 0xFF;
        int adjustedAlpha = (int) (baseAlpha * AnimationHelper.clamp01(alpha));
        return (adjustedAlpha << 24) | (color & 0x00FFFFFF);
    }

    private static final class NotificationEntry {
        private final String key;
        private String message;
        private int nodeColor;
        private final AnimatedValue visibility;
        private final long expiresAt;
        private final boolean persistent;
        private float progress;
        private boolean closing;

        private NotificationEntry(String key, String message, int nodeColor) {
            this.message = message;
            this.nodeColor = nodeColor;
            this.key = key;
            this.visibility = new AnimatedValue(0f, AnimationHelper::easeOutCubic);
            this.visibility.animateTo(1f, OPEN_DURATION_MS, AnimationHelper::easeOutCubic);
            this.expiresAt = System.currentTimeMillis() + DISPLAY_DURATION_MS;
            this.persistent = false;
            this.progress = -1f;
        }

        private NotificationEntry(String key, String message, int nodeColor, float progress) {
            this.message = message;
            this.nodeColor = nodeColor;
            this.key = key;
            this.visibility = new AnimatedValue(0f, AnimationHelper::easeOutCubic);
            this.visibility.animateTo(1f, OPEN_DURATION_MS, AnimationHelper::easeOutCubic);
            this.expiresAt = Long.MAX_VALUE;
            this.persistent = true;
            this.progress = AnimationHelper.clamp01(progress);
        }

        private static NotificationEntry progress(String key, String message, int nodeColor, float progress) {
            return new NotificationEntry(key, message, nodeColor, progress);
        }

        private boolean matchesKey(String key) {
            return this.key != null && this.key.equals(key);
        }

        private void updateProgress(String message, int nodeColor, float progress) {
            this.nodeColor = nodeColor;
            this.progress = AnimationHelper.clamp01(progress);
            if (message != null && !message.isEmpty()) {
                this.message = message;
            }
        }

        private boolean hasProgress() {
            return progress >= 0.0f;
        }

        private float getProgress() {
            return AnimationHelper.clamp01(progress);
        }

        private void dismiss() {
            if (!closing) {
                closing = true;
                visibility.animateTo(0f, CLOSE_DURATION_MS, AnimationHelper::easeInCubic);
            }
        }

        private void tick(long now) {
            if (!persistent && !closing && now >= expiresAt) {
                closing = true;
                visibility.animateTo(0f, CLOSE_DURATION_MS, AnimationHelper::easeInCubic);
            }
            visibility.tick(now);
        }

        private boolean isFinished() {
            return closing && visibility.getValue() <= 0.001f && !visibility.isAnimating();
        }
    }
}
