package com.pathmind.ui.overlay;

import com.pathmind.schematic.SchematicBuildExecutor;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DrawContextBridge;
import com.pathmind.util.TextRenderUtil;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Compact live progress view for a native schematic build. */
public final class SchematicBuildOverlay {
    private static final int WIDTH = 300;
    private static final int MARGIN = 10;
    private static final int PADDING = 7;
    private static final int LINE_HEIGHT = 11;
    private static final int BAR_HEIGHT = 6;
    private static final long PAUSED_DISMISS_DELAY_MS = 4_500L;

    private String lastState = "";
    private long pausedAtMs;

    public void render(GuiGraphics context, Font font, int screenWidth, int screenHeight) {
        SchematicBuildExecutor.Snapshot snapshot = SchematicBuildExecutor.getInstance().snapshot();
        if (snapshot == null) {
            lastState = "";
            pausedAtMs = 0L;
            return;
        }
        long now = System.currentTimeMillis();
        if (!snapshot.state().equals(lastState)) {
            lastState = snapshot.state();
            pausedAtMs = "PAUSED".equals(snapshot.state()) ? now : 0L;
        }
        // A paused build remains resumable via !build resume, but its progress
        // card should not become a permanent HUD fixture after the reason has
        // been reported through Pathmind's notification stack.
        if ("PAUSED".equals(snapshot.state()) && now - pausedAtMs >= PAUSED_DISMISS_DELAY_MS) {
            return;
        }
        List<String> lines = List.of(
            "Schematic Build " + snapshot.state() + (snapshot.creativeFlight() ? "  Creative Flight" : "  Survival"),
            snapshot.completedBlocks() + "/" + snapshot.totalBlocks() + " blocks  ·  " + snapshot.remainingBlocks() + " remaining"
                + (snapshot.blockedBlocks() > 0 ? "  ·  " + snapshot.blockedBlocks() + " blocked" : ""),
            snapshot.activeTarget() == null ? "Target: --" : "Target: " + format(snapshot.activeTarget()),
            snapshot.status() == null || snapshot.status().isBlank() ? "--" : snapshot.status()
        );
        int height = PADDING * 2 + lines.size() * LINE_HEIGHT + BAR_HEIGHT + 4;
        int x = screenWidth - WIDTH - MARGIN;
        int y = MARGIN;
        context.fill(x, y, x + WIDTH, y + height, UITheme.OVERLAY_BACKGROUND);
        DrawContextBridge.drawBorder(context, x, y, WIDTH, height, UITheme.BORDER_HIGHLIGHT);

        int textY = y + PADDING;
        for (int index = 0; index < lines.size(); index++) {
            int color = index == 0 ? UITheme.ACCENT_SKY : UITheme.TEXT_HEADER;
            context.drawString(font, Component.literal(TextRenderUtil.trimWithEllipsis(font, lines.get(index), WIDTH - PADDING * 2)),
                x + PADDING, textY + index * LINE_HEIGHT, color);
        }
        int barX = x + PADDING;
        int barY = y + height - PADDING - BAR_HEIGHT;
        int barWidth = WIDTH - PADDING * 2;
        context.fill(barX, barY, barX + barWidth, barY + BAR_HEIGHT, 0xFF2A3440);
        context.fill(barX, barY, barX + (int) Math.round(barWidth * snapshot.progress()), barY + BAR_HEIGHT, UITheme.ACCENT_SKY);
    }

    private String format(net.minecraft.core.BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
