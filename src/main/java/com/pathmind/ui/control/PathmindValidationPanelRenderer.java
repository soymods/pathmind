package com.pathmind.ui.control;

import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.validation.GraphValidationIssue;
import com.pathmind.validation.GraphValidationResult;
import com.pathmind.validation.GraphValidationSeverity;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

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

    public static int[] getPanelBounds(GraphValidationResult validationResult, TextRenderer textRenderer,
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

    public static int renderPanelAndIssues(DrawContext context, TextRenderer textRenderer,
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
        context.drawTextWithShadow(
            textRenderer,
            Text.translatable("pathmind.validation.checks"),
            panelX + padding,
            panelY + 8,
            textColor
        );
        context.drawTextWithShadow(
            textRenderer,
            Text.translatable(
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
            List<OrderedText> wrappedLines = getIssueLines(issue, textRenderer, panelWidth);
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

    public static void renderFooter(DrawContext context, TextRenderer textRenderer,
                                    GraphValidationResult validationResult,
                                    int panelX, int panelY, int panelWidth, int panelHeight,
                                    int padding, int footerHeight, int maxVisibleRows) {
        List<GraphValidationIssue> visibleIssues = visibleIssues(validationResult, maxVisibleRows);
        int hiddenCount = validationResult.getIssues().size() - visibleIssues.size();
        if (hiddenCount <= 0) {
            return;
        }
        int footerY = panelY + panelHeight - footerHeight;
        context.drawHorizontalLine(panelX + 1, panelX + panelWidth - 2, footerY, UITheme.BORDER_SUBTLE);
        context.drawTextWithShadow(
            textRenderer,
            Text.translatable("pathmind.validation.moreIssues", hiddenCount, hiddenCount == 1 ? "" : "s"),
            panelX + padding,
            footerY + 5,
            UITheme.TEXT_SECONDARY
        );
    }

    public static ClickedIssue findClickedIssue(GraphValidationResult validationResult, TextRenderer textRenderer,
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

    public static int getIssuesHeight(List<GraphValidationIssue> visibleIssues, TextRenderer textRenderer,
                                      int panelWidth, int rowMinHeight) {
        int totalHeight = 0;
        for (GraphValidationIssue issue : visibleIssues) {
            totalHeight += getIssueRowHeight(issue, textRenderer, panelWidth, rowMinHeight);
        }
        return totalHeight;
    }

    public static int getIssueRowHeight(GraphValidationIssue issue, TextRenderer textRenderer,
                                        int panelWidth, int rowMinHeight) {
        return getIssueRowHeight(getIssueLines(issue, textRenderer, panelWidth), textRenderer, rowMinHeight);
    }

    private static void renderIssueRow(DrawContext context, TextRenderer textRenderer,
                                       GraphValidationIssue issue, List<OrderedText> wrappedLines,
                                       int panelX, int panelWidth, int rowY, int rowHeight,
                                       float hoverProgress) {
        int rowBg = AnimationHelper.lerpColor(UITheme.BACKGROUND_SECONDARY, UITheme.TOOLBAR_BG_HOVER, hoverProgress);
        context.fill(panelX + 1, rowY, panelX + panelWidth - 1, rowY + rowHeight, rowBg);
        context.drawHorizontalLine(panelX + 1, panelX + panelWidth - 2, rowY, UITheme.BORDER_SUBTLE);

        int severityColor = issue.getSeverity() == GraphValidationSeverity.ERROR ? UITheme.STATE_ERROR : UITheme.ACCENT_AMBER;
        int dotTop = rowY + Math.max(7, (rowHeight - 4) / 2);
        context.fill(panelX + 8, dotTop, panelX + 12, dotTop + 4, severityColor);

        int rowTextColor = AnimationHelper.lerpColor(UITheme.TEXT_HEADER, UITheme.TEXT_PRIMARY, hoverProgress);
        int textY = rowY + Math.max(6, (rowHeight - getIssueTextHeight(wrappedLines, textRenderer)) / 2);
        for (OrderedText line : wrappedLines) {
            context.drawTextWithShadow(textRenderer, line, panelX + 18, textY, rowTextColor);
            textY += textRenderer.fontHeight + 2;
        }
    }

    private static List<OrderedText> getIssueLines(GraphValidationIssue issue, TextRenderer textRenderer, int panelWidth) {
        if (issue == null) {
            return List.of(Text.literal("").asOrderedText());
        }
        String prefixKey = issue.getSeverity() == GraphValidationSeverity.ERROR
            ? "pathmind.validation.issue.error"
            : "pathmind.validation.issue.warning";
        String fullMessage = Text.translatable(prefixKey, issue.getMessage()).getString();
        int textWidth = Math.max(40, panelWidth - 34);
        List<OrderedText> wrappedLines = textRenderer.wrapLines(Text.literal(fullMessage), textWidth);
        return wrappedLines.isEmpty() ? List.of(Text.literal(fullMessage).asOrderedText()) : wrappedLines;
    }

    private static int getIssueRowHeight(List<OrderedText> wrappedLines, TextRenderer textRenderer, int rowMinHeight) {
        return Math.max(rowMinHeight, getIssueTextHeight(wrappedLines, textRenderer) + 12);
    }

    private static int getIssueTextHeight(List<OrderedText> wrappedLines, TextRenderer textRenderer) {
        return wrappedLines.size() * textRenderer.fontHeight + Math.max(0, wrappedLines.size() - 1) * 2;
    }

    private static boolean contains(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    @FunctionalInterface
    public interface IssueHoverProgress {
        float get(GraphValidationIssue issue, int index, boolean hovered);
    }

    public record ClickedIssue(GraphValidationIssue issue, int nextTop, boolean clicked) {
    }
}
