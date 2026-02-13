package com.pathmind.util;

import net.minecraft.client.font.TextRenderer;

/**
 * Utility helpers for rendering text within constrained widths.
 */
public final class TextRenderUtil {
    private static final String ELLIPSIS = "...";

    private TextRenderUtil() {
    }

    public static String trimWithEllipsis(TextRenderer renderer, String text, int availableWidth) {
        if (renderer == null || text == null) {
            return "";
        }
        if (availableWidth <= 0) {
            return ELLIPSIS;
        }
        if (renderer.getWidth(text) <= availableWidth) {
            return text;
        }

        int ellipsisWidth = renderer.getWidth(ELLIPSIS);
        if (ellipsisWidth >= availableWidth) {
            return ELLIPSIS;
        }

        int trimmedWidth = Math.max(0, availableWidth - ellipsisWidth);
        return renderer.trimToWidth(text, trimmedWidth) + ELLIPSIS;
    }
}
