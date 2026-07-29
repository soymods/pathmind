package com.pathmind.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.function.Predicate;

/**
 * Pure preset tab sizing and positioning calculations.
 */
final class PathmindPresetTabLayout {
    private static final int TAB_GAP = 4;
    private static final int TAB_MIN_WIDTH = 72;
    private static final int TAB_MAX_WIDTH = 140;
    private static final int TAB_HARD_MIN_WIDTH = 24;
    private static final int TAB_TEXT_PADDING = 6;
    private static final int TAB_CLOSE_ICON_SIZE = 6;
    private static final int TAB_CLOSE_HITBOX_PADDING = 2;
    private static final int TAB_CLOSE_GAP = 4;
    private static final int GROUP_TAB_WIDTH = 10;

    private PathmindPresetTabLayout() {
    }

    static int[] computeXs(int[] widths, int startX) {
        int[] xs = new int[widths.length];
        int x = startX;
        for (int i = 0; i < widths.length; i++) {
            xs[i] = x;
            if (widths[i] > 0) {
                x += widths[i] + TAB_GAP;
            }
        }
        return xs;
    }

    static int[] computeWidths(List<String> tabNames,
                               int availableWidth,
                               int createTabWidth,
                               Font font,
                               Predicate<String> groupTab,
                               Predicate<String> deletableTab) {
        int presetCount = tabNames != null ? tabNames.size() : 0;
        if (presetCount <= 0) {
            return new int[0];
        }

        int available = Math.max(0, availableWidth);
        int gapCount = presetCount;
        int gapSpace = TAB_GAP * gapCount;
        int widthForPresets = Math.max(0, available - gapSpace - createTabWidth);
        if (widthForPresets <= 0) {
            return new int[presetCount];
        }

        int[] preferred = new int[presetCount];
        int preferredTotal = 0;
        for (int i = 0; i < presetCount; i++) {
            String label = tabNames.get(i);
            int width;
            if (groupTab.test(label)) {
                width = GROUP_TAB_WIDTH;
            } else {
                int closeSpace = deletableTab.test(label)
                    ? TAB_CLOSE_GAP + TAB_CLOSE_ICON_SIZE + TAB_CLOSE_HITBOX_PADDING * 2
                    : 0;
                width = font.width(label) + TAB_TEXT_PADDING * 2 + closeSpace;
                width = Mth.clamp(width, TAB_MIN_WIDTH, TAB_MAX_WIDTH);
            }
            preferred[i] = width;
            preferredTotal += width;
        }

        if (preferredTotal <= widthForPresets) {
            return preferred;
        }

        int minWidth = Math.min(TAB_MIN_WIDTH, Math.max(TAB_HARD_MIN_WIDTH, widthForPresets / presetCount));
        int minTotal = minWidth * presetCount;
        int[] result = new int[presetCount];
        if (widthForPresets <= minTotal) {
            int base = Math.max(TAB_HARD_MIN_WIDTH, widthForPresets / presetCount);
            int remainder = Math.max(0, widthForPresets - base * presetCount);
            for (int i = 0; i < presetCount; i++) {
                result[i] = base + (i < remainder ? 1 : 0);
            }
            return result;
        }

        int reducibleTotal = 0;
        for (int width : preferred) {
            reducibleTotal += Math.max(0, width - minWidth);
        }
        int reductionNeeded = preferredTotal - widthForPresets;
        int assigned = 0;
        for (int i = 0; i < presetCount; i++) {
            int reducible = Math.max(0, preferred[i] - minWidth);
            int reduction = reducibleTotal > 0 ? (reductionNeeded * reducible) / reducibleTotal : 0;
            result[i] = preferred[i] - reduction;
            if (result[i] < minWidth) {
                result[i] = minWidth;
            }
            assigned += result[i];
        }

        int diff = widthForPresets - assigned;
        for (int i = 0; diff != 0 && i < presetCount; i++) {
            if (diff > 0) {
                result[i]++;
                diff--;
            } else if (result[i] > minWidth) {
                result[i]--;
                diff++;
            }
        }
        return result;
    }

    static boolean fits(List<String> tabNames,
                        int availableWidth,
                        int createTabWidth,
                        Predicate<String> groupTab) {
        if (tabNames == null || tabNames.isEmpty()) {
            return false;
        }
        int total = createTabWidth;
        for (String label : tabNames) {
            total += (groupTab.test(label) ? GROUP_TAB_WIDTH : TAB_HARD_MIN_WIDTH) + TAB_GAP;
        }
        return total <= Math.max(0, availableWidth);
    }
}
