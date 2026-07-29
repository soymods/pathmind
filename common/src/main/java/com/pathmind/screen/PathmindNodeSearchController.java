package com.pathmind.screen;

import com.pathmind.data.NodeGraphData;
import com.pathmind.nodes.NodeCatalog;
import com.pathmind.nodes.NodeCategory;
import com.pathmind.nodes.NodeType;
import com.pathmind.ui.control.PathmindIconRenderer;
import com.pathmind.ui.control.PathmindTextField;
import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.MatrixStackBridge;
import com.pathmind.util.TextRenderUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Owns the workspace node search field, matching, zoom-aware layout, rendering, and selection.
 */
final class PathmindNodeSearchController {
    private static final int TITLE_BAR_HEIGHT = 20;
    private static final int TEXT_FIELD_VERTICAL_PADDING = 3;
    private static final int NODE_SEARCH_FIELD_WIDTH = 180;
    private static final int NODE_SEARCH_FIELD_HEIGHT = 22;
    private static final int NODE_SEARCH_DROPDOWN_TOP_GAP = 2;
    private static final int NODE_SEARCH_RESULT_HEIGHT = 18;
    private static final int NODE_SEARCH_MAX_RESULTS = 8;
    private static final int NODE_SEARCH_RESULT_TEXT_PADDING = 6;

    interface Host {
        Font font();
        int screenWidth();
        int screenHeight();
        int sidebarWidth();
        int accentColor();
        float zoomScale();
        int screenToWorldX(int screenX);
        int screenToWorldY(int screenY);
        int worldToScreenX(int worldX);
        int worldToScreenY(int worldY);
        boolean isNodeAvailable(NodeType nodeType);
        boolean baritoneAvailable();
        boolean uiUtilsAvailable();
        List<NodeGraphData.RoutineDefinitionData> rootRoutines();
        boolean shouldBlockBaritoneNode(NodeType nodeType);
        boolean shouldBlockUiUtilsNode(NodeType nodeType);
        void addNode(NodeType nodeType);
        void addRoutine(NodeGraphData.RoutineDefinitionData routine);
        EditBox createSearchField(Runnable responder);
    }

    private static final class Result {
        private final NodeType nodeType;
        private final String label;
        private final String categoryLabel;
        private final int score;
        private final NodeGraphData.RoutineDefinitionData routine;

        private Result(NodeType nodeType, String label, String categoryLabel, int score) {
            this(nodeType, label, categoryLabel, score, null);
        }

        private Result(NodeType nodeType, String label, String categoryLabel, int score,
                       NodeGraphData.RoutineDefinitionData routine) {
            this.nodeType = nodeType;
            this.label = label;
            this.categoryLabel = categoryLabel;
            this.score = score;
            this.routine = routine;
        }
    }

    private final Host host;
    private EditBox field;
    private boolean open = false;
    private int fieldX = 0;
    private int fieldY = 0;
    private int worldX = 0;
    private int worldY = 0;
    private float scale = 1.0f;
    private final List<Result> results = new ArrayList<>();
    private int hoverIndex = -1;

    PathmindNodeSearchController(Host host) {
        this.host = host;
    }

    void initialize() {
        if (field == null) {
            field = host.createSearchField(this::updateMatch);
        }
    }

    EditBox field() {
        return field;
    }

    boolean isOpen() {
        return open;
    }

    void open(int anchorX, int anchorY) {
        int minX = host.sidebarWidth() + 8;
        int maxX = host.screenWidth() - NODE_SEARCH_FIELD_WIDTH - 8;
        fieldX = Mth.clamp(anchorX, minX, Math.max(minX, maxX));
        fieldY = Mth.clamp(anchorY, TITLE_BAR_HEIGHT + 8,
            Math.max(TITLE_BAR_HEIGHT + 8, host.screenHeight() - NODE_SEARCH_FIELD_HEIGHT - 8));
        worldX = host.screenToWorldX(fieldX);
        worldY = host.screenToWorldY(fieldY);
        scale = Math.max(0.05f, host.zoomScale());
        open = true;
        if (field != null) {
            field.setX(fieldX);
            field.setY(fieldY);
            field.setValue("");
            field.setVisible(true);
            field.setEditable(true);
            field.setFocused(true);
            field.setSuggestion(null);
        }
        results.clear();
        hoverIndex = -1;
    }

    void close() {
        open = false;
        results.clear();
        hoverIndex = -1;
        if (field != null) {
            PathmindTextField.deactivate(field);
            field.setSuggestion(null);
        }
    }

    void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (!open || field == null) {
            return;
        }

        updateLayout();
        int transformedMouseX = toSearchSpaceX(mouseX);
        int transformedMouseY = toSearchSpaceY(mouseY);
        var matrices = context.pose();
        MatrixStackBridge.push(matrices);
        MatrixStackBridge.translate(matrices, fieldX, fieldY);
        MatrixStackBridge.scale(matrices, scale, scale);
        MatrixStackBridge.translate(matrices, -fieldX, -fieldY);

        boolean focused = field.isFocused();
        UIStyleHelper.FieldPalette searchPalette = UIStyleHelper.getSearchFieldPalette(host.accentColor(), focused ? 1f : 0f, focused, false);
        UIStyleHelper.drawFieldFrame(context, fieldX, fieldY, NODE_SEARCH_FIELD_WIDTH, NODE_SEARCH_FIELD_HEIGHT, searchPalette);
        int iconX = fieldX + 6;
        int iconY = fieldY + (NODE_SEARCH_FIELD_HEIGHT - 9) / 2;
        PathmindIconRenderer.drawSearch(context, iconX, iconY, searchPalette.textColor());
        int textFieldHeight = Math.max(10, NODE_SEARCH_FIELD_HEIGHT - TEXT_FIELD_VERTICAL_PADDING * 2);
        field.setPosition(fieldX + 20, fieldY + TEXT_FIELD_VERTICAL_PADDING);
        field.setWidth(NODE_SEARCH_FIELD_WIDTH - 26);
        field.setHeight(textFieldHeight);
        field.render(context, transformedMouseX, transformedMouseY, delta);

        renderDropdown(context, mouseX, mouseY);
        MatrixStackBridge.pop(matrices);
    }

    boolean handleClick(int mouseX, int mouseY, int button) {
        if (!open) {
            return false;
        }
        if (button == 0 && field != null && isPointInField(mouseX, mouseY)) {
            field.setFocused(true);
            return true;
        }
        if (button == 0) {
            int resultIndex = getResultIndexAt(mouseX, mouseY);
            if (resultIndex >= 0 && resultIndex < results.size()) {
                selectResult(results.get(resultIndex));
                return true;
            }
        }
        if (!isPointInBounds(mouseX, mouseY)) {
            close();
        }
        return true;
    }

    void moveSelection(int direction) {
        if (results.isEmpty()) {
            hoverIndex = -1;
            return;
        }
        if (hoverIndex < 0 || hoverIndex >= results.size()) {
            hoverIndex = 0;
            return;
        }
        hoverIndex = Mth.clamp(hoverIndex + direction, 0, results.size() - 1);
    }

    void selectCurrentOrClose() {
        Result selected = getSelectedResult();
        if (selected != null) {
            selectResult(selected);
        } else {
            close();
        }
    }

    private void updateMatch() {
        if (!open || field == null) {
            return;
        }
        String query = field.getValue();
        field.setSuggestion(null);
        refreshResults(query);
    }

    private void refreshResults(String query) {
        results.clear();
        hoverIndex = -1;
        if (query == null) {
            return;
        }
        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        if (normalizedQuery.isEmpty()) {
            return;
        }

        for (NodeType nodeType : NodeType.values()) {
            if (!host.isNodeAvailable(nodeType)) {
                continue;
            }
            int score = scoreNodeCandidate(nodeType, normalizedQuery);
            if (score <= 0) {
                continue;
            }
            results.add(new Result(
                nodeType,
                nodeType.getDisplayName(),
                getCategoryLabel(nodeType),
                score
            ));
        }
        for (NodeGraphData.RoutineDefinitionData routine : host.rootRoutines()) {
            if (routine == null) continue;
            int score = scoreCandidate(routine.getName(), normalizedQuery);
            if (score > 0) results.add(new Result(NodeType.ROUTINE_CALL, routine.getName(),
                NodeCategory.ROUTINES.getDisplayName(), score + 20, routine));
        }

        results.sort((left, right) -> {
            int scoreCompare = Integer.compare(right.score, left.score);
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            return left.label.compareToIgnoreCase(right.label);
        });
        if (results.size() > NODE_SEARCH_MAX_RESULTS) {
            results.subList(NODE_SEARCH_MAX_RESULTS, results.size()).clear();
        }
        if (!results.isEmpty()) {
            hoverIndex = 0;
        }
    }

    private String getCategoryLabel(NodeType nodeType) {
        NodeCatalog.NodePlacement placement = NodeCatalog.sidebarPlacement(nodeType, host.baritoneAvailable(), host.uiUtilsAvailable());
        NodeCategory category = placement != null ? placement.displayCategory() : NodeCatalog.category(nodeType);
        return category.getDisplayName();
    }

    private int scoreNodeCandidate(NodeType nodeType, String query) {
        int bestScore = 0;
        bestScore = Math.max(bestScore, scoreCandidate(nodeType.getDisplayName(), query));
        bestScore = Math.max(bestScore, scoreCandidate(nodeType.getDescription(), query) - 40);
        bestScore = Math.max(bestScore, scoreCandidate(getCategoryLabel(nodeType), query) - 80);
        bestScore = Math.max(bestScore, scoreCandidate(nodeType.name(), query) - 100);
        return bestScore;
    }

    private int scoreCandidate(String candidate, String query) {
        if (candidate == null || query == null) {
            return 0;
        }
        String normalizedCandidate = candidate.trim().toLowerCase(Locale.ROOT);
        if (normalizedCandidate.isEmpty() || query.isEmpty()) {
            return 0;
        }
        if (normalizedCandidate.equals(query)) {
            return 1000;
        }
        if (normalizedCandidate.startsWith(query)) {
            return 800 - Math.max(0, normalizedCandidate.length() - query.length());
        }
        int containsIndex = normalizedCandidate.indexOf(query);
        if (containsIndex >= 0) {
            return 650 - containsIndex * 6;
        }
        int fuzzyScore = fuzzyScore(normalizedCandidate, query);
        return fuzzyScore > 0 ? 300 + fuzzyScore : 0;
    }

    private int fuzzyScore(String candidate, String query) {
        int score = 0;
        int streak = 0;
        int queryIndex = 0;
        for (int i = 0; i < candidate.length() && queryIndex < query.length(); i++) {
            if (candidate.charAt(i) == query.charAt(queryIndex)) {
                score += 8 + streak * 4;
                streak++;
                queryIndex++;
            } else {
                streak = 0;
            }
        }
        if (queryIndex != query.length()) {
            return 0;
        }
        return Math.max(1, score - Math.max(0, candidate.length() - query.length()));
    }

    private void renderDropdown(GuiGraphics context, int mouseX, int mouseY) {
        if (results.isEmpty()) {
            return;
        }
        int listX = fieldX;
        int listY = fieldY + NODE_SEARCH_FIELD_HEIGHT + NODE_SEARCH_DROPDOWN_TOP_GAP;
        int listWidth = NODE_SEARCH_FIELD_WIDTH;
        int listHeight = results.size() * NODE_SEARCH_RESULT_HEIGHT;
        UIStyleHelper.ScrollContainerPalette containerPalette = UIStyleHelper.getScrollContainerPalette(host.accentColor(), 1f, true, false);
        UIStyleHelper.drawScrollContainer(context, listX, listY, listWidth, listHeight, containerPalette);

        int hoveredIndex = getResultIndexAt(mouseX, mouseY);
        if (hoveredIndex >= 0) {
            hoverIndex = hoveredIndex;
        }

        for (int i = 0; i < results.size(); i++) {
            Result result = results.get(i);
            int rowTop = listY + i * NODE_SEARCH_RESULT_HEIGHT;
            boolean selected = i == hoverIndex;
            UIStyleHelper.DropdownRowPalette rowPalette = UIStyleHelper.getDropdownRowPalette(host.accentColor(), selected ? 1f : 0f, selected, false);
            if (selected) {
                UIStyleHelper.drawDropdownRow(context, listX + 1, rowTop, listWidth - 2, NODE_SEARCH_RESULT_HEIGHT, rowPalette);
            }
            int textY = rowTop + Math.max(0, (NODE_SEARCH_RESULT_HEIGHT - host.font().lineHeight) / 2);
            String label = trimToWidth(result.label, listWidth - (NODE_SEARCH_RESULT_TEXT_PADDING * 2) - 42);
            context.drawString(host.font(), Component.literal(label), listX + NODE_SEARCH_RESULT_TEXT_PADDING, textY, selected ? rowPalette.textColor() : UITheme.TEXT_PRIMARY);
            String category = trimToWidth(result.categoryLabel, 36);
            int categoryWidth = host.font().width(category);
            context.drawString(host.font(), Component.literal(category),
                listX + listWidth - NODE_SEARCH_RESULT_TEXT_PADDING - categoryWidth, textY, UITheme.TEXT_TERTIARY);
        }
    }

    private String trimToWidth(String value, int maxWidth) {
        return TextRenderUtil.trimWithEllipsis(host.font(), value, maxWidth);
    }

    private boolean isPointInField(int mouseX, int mouseY) {
        int transformedMouseX = toSearchSpaceX(mouseX);
        int transformedMouseY = toSearchSpaceY(mouseY);
        return isPointInRect(transformedMouseX, transformedMouseY, fieldX, fieldY, NODE_SEARCH_FIELD_WIDTH, NODE_SEARCH_FIELD_HEIGHT);
    }

    private boolean isPointInBounds(int mouseX, int mouseY) {
        int transformedMouseX = toSearchSpaceX(mouseX);
        int transformedMouseY = toSearchSpaceY(mouseY);
        int totalHeight = NODE_SEARCH_FIELD_HEIGHT + getDropdownHeight() + (results.isEmpty() ? 0 : NODE_SEARCH_DROPDOWN_TOP_GAP);
        return isPointInRect(transformedMouseX, transformedMouseY, fieldX, fieldY, NODE_SEARCH_FIELD_WIDTH, totalHeight);
    }

    private int getDropdownHeight() {
        return results.isEmpty() ? 0 : results.size() * NODE_SEARCH_RESULT_HEIGHT;
    }

    private int getResultIndexAt(int mouseX, int mouseY) {
        if (results.isEmpty()) {
            return -1;
        }
        int transformedMouseX = toSearchSpaceX(mouseX);
        int transformedMouseY = toSearchSpaceY(mouseY);
        int listX = fieldX;
        int listY = fieldY + NODE_SEARCH_FIELD_HEIGHT + NODE_SEARCH_DROPDOWN_TOP_GAP;
        int listHeight = getDropdownHeight();
        if (!isPointInRect(transformedMouseX, transformedMouseY, listX, listY, NODE_SEARCH_FIELD_WIDTH, listHeight)) {
            return -1;
        }
        int index = (transformedMouseY - listY) / NODE_SEARCH_RESULT_HEIGHT;
        return index >= 0 && index < results.size() ? index : -1;
    }

    private void updateLayout() {
        scale = Math.max(0.05f, host.zoomScale());
        int minX = host.sidebarWidth() + 8;
        int minY = TITLE_BAR_HEIGHT + 8;
        int scaledWidth = Math.max(1, Math.round(NODE_SEARCH_FIELD_WIDTH * scale));
        int scaledHeight = Math.max(1, Math.round(NODE_SEARCH_FIELD_HEIGHT * scale));
        int maxX = Math.max(minX, host.screenWidth() - scaledWidth - 8);
        int maxY = Math.max(minY, host.screenHeight() - scaledHeight - 8);
        fieldX = Mth.clamp(host.worldToScreenX(worldX), minX, maxX);
        fieldY = Mth.clamp(host.worldToScreenY(worldY), minY, maxY);
    }

    private int toSearchSpaceX(int mouseX) {
        if (scale == 0.0f) {
            return mouseX;
        }
        return Math.round(fieldX + (mouseX - fieldX) / scale);
    }

    private int toSearchSpaceY(int mouseY) {
        if (scale == 0.0f) {
            return mouseY;
        }
        return Math.round(fieldY + (mouseY - fieldY) / scale);
    }

    private Result getSelectedResult() {
        if (results.isEmpty()) {
            return null;
        }
        if (hoverIndex < 0 || hoverIndex >= results.size()) {
            return results.get(0);
        }
        return results.get(hoverIndex);
    }

    private void selectResult(Result result) {
        if (result == null || result.nodeType == null) {
            return;
        }
        if (host.shouldBlockBaritoneNode(result.nodeType) || host.shouldBlockUiUtilsNode(result.nodeType)) {
            return;
        }
        if (result.routine != null) host.addRoutine(result.routine);
        else host.addNode(result.nodeType);
        close();
    }

    private static boolean isPointInRect(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
