package com.pathmind.ui.control;

import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DrawContextBridge;
import com.pathmind.validation.GraphValidationIssue;
import com.pathmind.validation.GraphValidationResult;
import com.pathmind.validation.GraphValidationSeverity;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Shared renderer and layout helpers for graph validation panels.
 */
public final class PathmindValidationPanelRenderer {
    private PathmindValidationPanelRenderer() {
    }

    public static List<GraphValidationIssue> visibleIssues(GraphValidationResult validationResult, int maxVisibleRows) {
        if (validationResult == null || !validationResult.hasIssues()) {
            return List.of();
        }
        int limit = Math.min(maxVisibleRows, validationResult.getIssues().size());
        return validationResult.getIssues().subList(0, limit);
    }

    public static boolean renderValidationButton(GuiGraphics context,
                                                 Font textRenderer,
                                                 int buttonX, int buttonY, int buttonSize,
                                                 int mouseX, int mouseY,
                                                 boolean active, boolean disabled,
                                                 float hoverProgress, int accentColor,
                                                 GraphValidationResult validationResult) {
        boolean hovered = !disabled && PathmindWorkspaceChrome.contains(mouseX, mouseY, buttonX, buttonY, buttonSize, buttonSize);
        PathmindWorkspaceChrome.drawToolbarButtonFrame(context, buttonX, buttonY, buttonSize, buttonSize, hovered, active, disabled, hoverProgress, accentColor);

        int statusColor = getValidationStatusColor(validationResult, hovered, active, disabled, accentColor);
        if (!disabled && validationResult != null && validationResult.hasIssues() && !active) {
            int severityBorder = validationResult.hasErrors() ? UITheme.STATE_ERROR : UITheme.ACCENT_AMBER;
            DrawContextBridge.drawBorder(context, buttonX, buttonY, buttonSize, buttonSize, severityBorder);
        }

        if (validationResult != null && validationResult.hasIssues()) {
            drawAlertIcon(context, buttonX, buttonY, buttonSize, statusColor);
            drawCountBadge(context, textRenderer, validationResult, buttonX, buttonY, buttonSize, disabled);
        } else {
            drawConsoleIcon(context, buttonX, buttonY, statusColor);
        }

        return hovered;
    }

    public static int[] getPanelBounds(GraphValidationResult validationResult, Font textRenderer,
                                       int progressAnchorX, int panelY, float progress,
                                       int panelWidth, int maxVisibleRows,
                                       int headerHeight, int presetInputHeight,
                                       int footerHeight, int bottomPadding,
                                       int rowMinHeight) {
        List<GraphValidationIssue> visibleIssues = visibleIssues(validationResult, maxVisibleRows);
        int issuesHeight = getIssuesHeight(visibleIssues, textRenderer, panelWidth, rowMinHeight);
        int visibleCount = visibleIssues.size();
        int resolvedFooterHeight = validationResult != null && validationResult.getIssues().size() > visibleCount
            ? footerHeight : 0;
        int fullHeight = headerHeight + issuesHeight + presetInputHeight + resolvedFooterHeight + bottomPadding;
        int width = Math.max(1, Math.round(panelWidth * progress));
        int height = Math.max(1, Math.round(fullHeight * progress));
        int x = progressAnchorX - width;
        return new int[]{x, panelY, width, height};
    }

    public static int renderPanelAndIssues(GuiGraphics context, Font textRenderer,
                                           int mouseX, int mouseY,
                                           GraphValidationResult validationResult,
                                           int panelX, int panelY, int panelWidth, int panelHeight,
                                           int padding, int headerHeight, int maxVisibleRows,
                                           int rowMinHeight,
                                           IssueHoverProgress issueHoverProgress) {
        int outlineColor = validationResult.hasErrors() ? UITheme.STATE_ERROR
            : validationResult.hasWarnings() ? UITheme.ACCENT_AMBER
            : UITheme.BORDER_DEFAULT;
        UIStyleHelper.drawBeveledPanel(
            context,
            panelX,
            panelY,
            panelWidth,
            panelHeight,
            UITheme.BACKGROUND_SECTION,
            outlineColor,
            UITheme.PANEL_INNER_BORDER
        );

        int textColor = validationResult.hasErrors() ? UITheme.STATE_ERROR
            : validationResult.hasWarnings() ? UITheme.ACCENT_AMBER
            : UITheme.TEXT_PRIMARY;
        context.drawString(
            textRenderer,
            Component.translatable("pathmind.validation.checks"),
            panelX + padding,
            panelY + 8,
            textColor
        );
        context.drawString(
            textRenderer,
            Component.translatable(
                "pathmind.validation.summary",
                validationResult.getErrorCount(),
                validationResult.getErrorCount() == 1 ? "" : "s",
                validationResult.getWarningCount(),
                validationResult.getWarningCount() == 1 ? "" : "s"
            ),
            panelX + padding,
            panelY + 19,
            UITheme.TEXT_SECONDARY
        );

        List<GraphValidationIssue> visibleIssues = visibleIssues(validationResult, maxVisibleRows);
        int issueTop = panelY + headerHeight;
        for (int index = 0; index < visibleIssues.size(); index++) {
            GraphValidationIssue issue = visibleIssues.get(index);
            List<FormattedCharSequence> wrappedLines = getIssueLines(issue, textRenderer, panelWidth);
            int rowHeight = getIssueRowHeight(wrappedLines, textRenderer, rowMinHeight);
            int rowY = issueTop;
            boolean clickable = issue != null && issue.hasNodeTarget();
            boolean hovered = clickable && contains(mouseX, mouseY, panelX + 1, rowY, panelWidth - 2, rowHeight);
            float hoverProgress = issueHoverProgress == null ? 0f : issueHoverProgress.get(issue, index, hovered);
            renderIssueRow(context, textRenderer, issue, wrappedLines, panelX, panelWidth, rowY, rowHeight, hoverProgress);
            issueTop += rowHeight;
        }
        return issueTop;
    }

    public static void renderFooter(GuiGraphics context, Font textRenderer,
                                    GraphValidationResult validationResult,
                                    int panelX, int panelY, int panelWidth, int panelHeight,
                                    int padding, int footerHeight, int maxVisibleRows) {
        List<GraphValidationIssue> visibleIssues = visibleIssues(validationResult, maxVisibleRows);
        int hiddenCount = validationResult.getIssues().size() - visibleIssues.size();
        if (hiddenCount <= 0) {
            return;
        }
        int footerY = panelY + panelHeight - footerHeight;
        context.hLine(panelX + 1, panelX + panelWidth - 2, footerY, UITheme.BORDER_SUBTLE);
        context.drawString(
            textRenderer,
            Component.translatable("pathmind.validation.moreIssues", hiddenCount, hiddenCount == 1 ? "" : "s"),
            panelX + padding,
            footerY + 5,
            UITheme.TEXT_SECONDARY
        );
    }

    public static ClickedIssue findClickedIssue(GraphValidationResult validationResult, Font textRenderer,
                                                int mouseX, int mouseY,
                                                int panelX, int panelY, int panelWidth,
                                                int headerHeight, int maxVisibleRows, int rowMinHeight) {
        List<GraphValidationIssue> visibleIssues = visibleIssues(validationResult, maxVisibleRows);
        int issueTop = panelY + headerHeight;
        for (int index = 0; index < visibleIssues.size(); index++) {
            GraphValidationIssue issue = visibleIssues.get(index);
            int rowHeight = getIssueRowHeight(issue, textRenderer, panelWidth, rowMinHeight);
            int rowY = issueTop;
            if (contains(mouseX, mouseY, panelX + 1, rowY, panelWidth - 2, rowHeight)) {
                return new ClickedIssue(issue, issueTop + rowHeight, true);
            }
            issueTop += rowHeight;
        }
        return new ClickedIssue(null, issueTop, false);
    }

    public static int getIssuesHeight(List<GraphValidationIssue> visibleIssues, Font textRenderer,
                                      int panelWidth, int rowMinHeight) {
        int totalHeight = 0;
        for (GraphValidationIssue issue : visibleIssues) {
            totalHeight += getIssueRowHeight(issue, textRenderer, panelWidth, rowMinHeight);
        }
        return totalHeight;
    }

    public static int getIssueRowHeight(GraphValidationIssue issue, Font textRenderer,
                                        int panelWidth, int rowMinHeight) {
        return getIssueRowHeight(getIssueLines(issue, textRenderer, panelWidth), textRenderer, rowMinHeight);
    }

    private static void renderIssueRow(GuiGraphics context, Font textRenderer,
                                       GraphValidationIssue issue, List<FormattedCharSequence> wrappedLines,
                                       int panelX, int panelWidth, int rowY, int rowHeight,
                                       float hoverProgress) {
        int rowBg = AnimationHelper.lerpColor(UITheme.BACKGROUND_SECONDARY, UITheme.TOOLBAR_BG_HOVER, hoverProgress);
        context.fill(panelX + 1, rowY, panelX + panelWidth - 1, rowY + rowHeight, rowBg);
        context.hLine(panelX + 1, panelX + panelWidth - 2, rowY, UITheme.BORDER_SUBTLE);

        int severityColor = issue.getSeverity() == GraphValidationSeverity.ERROR ? UITheme.STATE_ERROR : UITheme.ACCENT_AMBER;
        int dotTop = rowY + Math.max(7, (rowHeight - 4) / 2);
        context.fill(panelX + 8, dotTop, panelX + 12, dotTop + 4, severityColor);

        int rowTextColor = AnimationHelper.lerpColor(UITheme.TEXT_HEADER, UITheme.TEXT_PRIMARY, hoverProgress);
        int textY = rowY + Math.max(6, (rowHeight - getIssueTextHeight(wrappedLines, textRenderer)) / 2);
        for (FormattedCharSequence line : wrappedLines) {
            context.drawString(textRenderer, line, panelX + 18, textY, rowTextColor);
            textY += textRenderer.lineHeight + 2;
        }
    }

    private static List<FormattedCharSequence> getIssueLines(GraphValidationIssue issue, Font textRenderer, int panelWidth) {
        if (issue == null) {
            return List.of(Component.literal("").getVisualOrderText());
        }
        String prefixKey = issue.getSeverity() == GraphValidationSeverity.ERROR
            ? "pathmind.validation.issue.error"
            : "pathmind.validation.issue.warning";
        String fullMessage = Component.translatable(prefixKey, issue.getMessage()).getString();
        int textWidth = Math.max(40, panelWidth - 34);
        List<FormattedCharSequence> wrappedLines = textRenderer.split(Component.literal(fullMessage), textWidth);
        return wrappedLines.isEmpty() ? List.of(Component.literal(fullMessage).getVisualOrderText()) : wrappedLines;
    }

    private static int getIssueRowHeight(List<FormattedCharSequence> wrappedLines, Font textRenderer, int rowMinHeight) {
        return Math.max(rowMinHeight, getIssueTextHeight(wrappedLines, textRenderer) + 12);
    }

    private static int getIssueTextHeight(List<FormattedCharSequence> wrappedLines, Font textRenderer) {
        return wrappedLines.size() * textRenderer.lineHeight + Math.max(0, wrappedLines.size() - 1) * 2;
    }

    private static int getValidationStatusColor(GraphValidationResult validationResult,
                                                boolean hovered, boolean active, boolean disabled,
                                                int accentColor) {
        int statusColor = UITheme.TEXT_PRIMARY;
        if (validationResult != null) {
            if (validationResult.hasErrors()) {
                statusColor = UITheme.STATE_ERROR;
            } else if (validationResult.hasWarnings()) {
                statusColor = UITheme.ACCENT_AMBER;
            }
        }
        if (disabled) {
            return UITheme.DROPDOWN_ACTION_DISABLED;
        }
        if (hovered || active) {
            return validationResult != null && validationResult.hasIssues() ? statusColor : accentColor;
        }
        return statusColor;
    }

    private static void drawConsoleIcon(GuiGraphics context, int buttonX, int buttonY, int color) {
        int left = buttonX + 4;
        int top = buttonY + 4;
        context.fill(left, top, left + 10, top + 1, color);
        context.fill(left, top + 8, left + 10, top + 9, color);
        context.fill(left, top, left + 1, top + 9, color);
        context.fill(left + 9, top, left + 10, top + 9, color);
        context.fill(left + 2, top + 2, left + 5, top + 3, color);
        context.fill(left + 2, top + 4, left + 7, top + 5, color);
        context.fill(left + 2, top + 6, left + 6, top + 7, color);
    }

    private static void drawAlertIcon(GuiGraphics context, int buttonX, int buttonY, int buttonSize, int color) {
        int stemX = buttonX + buttonSize / 2 - 1;
        int top = buttonY + 4;
        context.fill(stemX, top, stemX + 2, top + 6, color);
        context.fill(stemX, top + 8, stemX + 2, top + 10, color);
    }

    private static void drawCountBadge(GuiGraphics context, Font textRenderer,
                                       GraphValidationResult validationResult, int buttonX, int buttonY,
                                       int buttonSize, boolean disabled) {
        int count = validationResult.getErrorCount() > 0 ? validationResult.getErrorCount() : validationResult.getWarningCount();
        int badgeColor = validationResult.getErrorCount() > 0 ? UITheme.STATE_ERROR : UITheme.ACCENT_AMBER;
        if (disabled) {
            badgeColor = UITheme.DROPDOWN_ACTION_DISABLED;
        }
        String text = count > 9 ? "9+" : String.valueOf(count);
        int textWidth = textRenderer.width(text);
        int badgeSize = Math.max(9, textWidth + 4);
        int badgeX = buttonX + buttonSize - badgeSize + 1;
        int badgeY = buttonY - 2;
        context.fill(badgeX, badgeY, badgeX + badgeSize, badgeY + badgeSize, badgeColor);
        DrawContextBridge.drawBorder(context, badgeX, badgeY, badgeSize, badgeSize, UITheme.BORDER_HIGHLIGHT);
        context.drawString(textRenderer, Component.literal(text), badgeX + (badgeSize - textWidth) / 2, badgeY + 1,
            UITheme.TEXT_PRIMARY);
    }

    private static boolean contains(int mouseX, int mouseY, int x, int y, int width, int height) {
        return UiHitTest.contains(mouseX, mouseY, x, y, width, height);
    }

    @FunctionalInterface
    public interface IssueHoverProgress {
        float get(GraphValidationIssue issue, int index, boolean hovered);
    }

    public record ClickedIssue(GraphValidationIssue issue, int nextTop, boolean clicked) {
    }
}
