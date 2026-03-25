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

        notifications.add(0, new NotificationEntry(message, nodeColor));
        while (notifications.size() > MAX_NOTIFICATIONS) {
            notifications.remove(notifications.size() - 1);
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
            int cardHeight = textHeight + PADDING_Y * 2;
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

            y -= CARD_SPACING;
        }
    }

    private int applyAlpha(int color, float alpha) {
        int baseAlpha = (color >>> 24) & 0xFF;
        int adjustedAlpha = (int) (baseAlpha * AnimationHelper.clamp01(alpha));
        return (adjustedAlpha << 24) | (color & 0x00FFFFFF);
    }

    private static final class NotificationEntry {
        private final String message;
        private final int nodeColor;
        private final AnimatedValue visibility;
        private final long expiresAt;
        private boolean closing;

        private NotificationEntry(String message, int nodeColor) {
            this.message = message;
            this.nodeColor = nodeColor;
            this.visibility = new AnimatedValue(0f, AnimationHelper::easeOutCubic);
            this.visibility.animateTo(1f, OPEN_DURATION_MS, AnimationHelper::easeOutCubic);
            this.expiresAt = System.currentTimeMillis() + DISPLAY_DURATION_MS;
        }

        private void tick(long now) {
            if (!closing && now >= expiresAt) {
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
