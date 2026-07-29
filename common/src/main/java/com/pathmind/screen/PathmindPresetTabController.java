package com.pathmind.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.pathmind.data.PresetManager;
import com.pathmind.data.SettingsManager;
import com.pathmind.data.SettingsManager.Settings;
import com.pathmind.ui.animation.AnimatedValue;
import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.control.PathmindIconRenderer;
import com.pathmind.ui.control.PathmindTextField;
import com.pathmind.ui.control.UiHitTest;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DrawContextBridge;
import com.pathmind.util.TextRenderUtil;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Owns visual-editor preset tab layout, groups, dragging, renaming, and animations. */
final class PathmindPresetTabController {
    private static final int TAB_BAR_TOP = 4;
    private static final int TAB_HEIGHT = 16;
    private static final int TAB_GAP = 4;
    private static final int TAB_MIN_WIDTH = 72;
    private static final int TAB_MAX_WIDTH = 140;
    private static final int PRESET_TAB_TEXT_PADDING = 6;
    private static final int PRESET_TAB_CLOSE_ICON_SIZE = 6;
    private static final int PRESET_TAB_CLOSE_HITBOX_PADDING = 2;
    private static final int PRESET_TAB_CLOSE_GAP = 4;
    private static final int PRESET_TAB_ADD_WIDTH = 20;
    private static final int PRESET_TAB_TITLE_GAP = 0;
    private static final int PRESET_TAB_DRAG_THRESHOLD = 4;
    private static final int PRESET_GROUP_TAB_WIDTH = 10;
    private static final long PRESET_TAB_RENAME_DOUBLE_CLICK_MS = 300L;
    private static final String PRESET_GROUP_TAB_PREFIX = "__pathmind_preset_group__:";
    private static final String[] PRESET_GROUP_COLOR_KEYS = {"sky", "mint", "amber", "rose", "violet"};
    private static final int[] PRESET_GROUP_COLORS = {
        0xFF38BDF8, 0xFF34D399, 0xFFF59E0B, 0xFFFB7185, 0xFFA78BFA
    };

    interface Host {
        Font font();
        int titleTextX();
        int presetOverflowTabRight();
        int accentColor();
        boolean isPopupObscuringWorkspace();
        List<String> availablePresets();
        String activePresetName();
        Settings settings();
        EditBox inlinePresetRenameField();
        boolean isPresetDeleteDisabled(String presetName);
        void openPresetDeletePopup(String presetName);
        void openCreatePresetPopup();
        void closeCreatePresetPopup();
        void closeRenamePresetPopup();
        void closePresetDropdown();
        void switchPreset(String presetName);
        boolean renamePresetInternal(String currentName, String desiredName);
        void attemptDeletePresetImmediate(String presetName);
    }

    private final Host host;
    private final List<String> presetTabOrder = new ArrayList<>();
    private final Map<String, AnimatedValue> presetTabXAnimations = new HashMap<>();
    private final Map<String, AnimatedValue> presetTabAppearAnimations = new HashMap<>();
    private final AnimatedValue presetTabAddButtonFadeAnimation =
        new AnimatedValue(1f, AnimationHelper::easeOutCubic);
    private boolean presetTabsInitialized = false;
    private String pendingPresetTabInteractionName = null;
    private int pendingPresetTabPressMouseX = 0;
    private int pendingPresetTabPressMouseY = 0;
    private int pendingPresetTabPressTabLeft = 0;
    private String draggingPresetTabName = null;
    private int draggingPresetTabPointerOffsetX = 0;
    private int draggingPresetTabCurrentX = 0;
    private String pendingPresetDropdownDragName = null;
    private int pendingPresetDropdownPressMouseX = 0;
    private int pendingPresetDropdownPressMouseY = 0;
    private String draggingPresetDropdownName = null;
    private int draggingPresetDropdownCurrentX = 0;
    private int draggingPresetDropdownCurrentY = 0;
    private String animatingPresetDeletionName = null;
    private long animatingPresetDeletionExecuteAtMs = 0L;
    private String inlinePresetRenameName = "";
    private long lastPresetTitleClickTime = 0L;
    private String lastPresetTitleClickName = "";

    PathmindPresetTabController(Host host) {
        this.host = host;
    }

    void render(GuiGraphics context, int mouseX, int mouseY) {
        tickQueuedPresetDeletionAnimation();
        if (!host.isPopupObscuringWorkspace()
            && pendingPresetTabInteractionName != null && draggingPresetTabName == null) {
            updatePendingPresetTabInteraction(mouseX, mouseY);
        }
        if (!host.isPopupObscuringWorkspace() && draggingPresetTabName != null) {
            updatePresetTabDrag(mouseX);
        }
        int x = getPresetTabStartX();
        int y = TAB_BAR_TOP;
        int rightLimit = getPresetTabRightLimit();
        List<String> tabs = getRenderedPresetTabsForWidth(rightLimit - x);
        if (tabs.isEmpty()) {
            return;
        }

        int[] tabWidths = computePresetTabWidths(tabs, rightLimit - x, PRESET_TAB_ADD_WIDTH);
        int[] tabXs = computePresetTabXs(tabWidths, x);
        int dragIndex = draggingPresetTabName == null ? -1 : tabs.indexOf(draggingPresetTabName);

        for (int i = 0; i < tabs.size() && i < tabWidths.length; i++) {
            if (i == dragIndex) {
                continue;
            }
            String label = tabs.get(i);
            int tabWidth = tabWidths[i];
            if (tabWidth <= 0) {
                continue;
            }
            int drawX = getAnimatedPresetTabX(label, tabXs[i]);
            drawPresetTab(context, mouseX, mouseY, label, drawX, y, tabWidth, false);
        }

        if (dragIndex >= 0 && dragIndex < tabs.size() && dragIndex < tabWidths.length) {
            String label = tabs.get(dragIndex);
            int tabWidth = tabWidths[dragIndex];
            if (tabWidth > 0) {
                int drawX = draggingPresetTabCurrentX == 0 ? tabXs[dragIndex] : draggingPresetTabCurrentX;
                drawPresetTab(context, mouseX, mouseY, label, drawX, y, tabWidth, true);
            }
        }

        x = getPresetTabStartX();
        for (int width : tabWidths) {
            if (width > 0) {
                x += width + TAB_GAP;
            }
        }
        int addTabX = Math.max(getPresetTabStartX(), x - TAB_GAP);
        presetTabAddButtonFadeAnimation.animateTo(
            draggingPresetTabName != null ? 0f : 1f, 120, AnimationHelper::easeOutCubic);
        presetTabAddButtonFadeAnimation.tick();
        float plusAlpha = Mth.clamp(presetTabAddButtonFadeAnimation.getValue(), 0f, 1f);
        if (addTabX + PRESET_TAB_ADD_WIDTH <= rightLimit) {
            boolean hovered = contains(mouseX, mouseY, addTabX, y, PRESET_TAB_ADD_WIDTH, TAB_HEIGHT);
            context.drawCenteredString(
                host.font(),
                Component.literal("+"),
                addTabX + PRESET_TAB_ADD_WIDTH / 2,
                y + (TAB_HEIGHT - host.font().lineHeight) / 2 + 1,
                AnimationHelper.multiplyAlpha(
                    hovered ? host.accentColor() : UITheme.ICON_MUTED_BRIGHT, plusAlpha)
            );
        }
        renderInlinePresetRenameField(context, mouseX, mouseY, tabs, tabWidths, tabXs, y, dragIndex);
    }

    boolean handleClick(int mouseX, int mouseY) {
        int x = getPresetTabStartX();
        int y = TAB_BAR_TOP;
        int rightLimit = getPresetTabRightLimit();
        List<String> tabs = getRenderedPresetTabsForWidth(rightLimit - x);
        if (tabs.isEmpty()) {
            return false;
        }
        int[] tabWidths = computePresetTabWidths(tabs, rightLimit - x, PRESET_TAB_ADD_WIDTH);
        int[] tabXs = computePresetTabXs(tabWidths, x);
        for (int i = 0; i < tabs.size() && i < tabWidths.length; i++) {
            String label = tabs.get(i);
            int tabWidth = tabWidths[i];
            if (tabWidth <= 0) {
                continue;
            }
            if (label.equals(animatingPresetDeletionName)) {
                continue;
            }
            x = tabXs[i];
            if (contains(mouseX, mouseY, x, y, tabWidth, TAB_HEIGHT)) {
                if (isPresetGroupTab(label)) {
                    beginPendingTabInteraction(label, mouseX, mouseY, x);
                    return true;
                }
                if (!host.isPresetDeleteDisabled(label)) {
                    int closeLeft = x + tabWidth - PRESET_TAB_TEXT_PADDING - PRESET_TAB_CLOSE_ICON_SIZE;
                    int closeTop = y + (TAB_HEIGHT - PRESET_TAB_CLOSE_ICON_SIZE) / 2;
                    int closeHitboxSize =
                        PRESET_TAB_CLOSE_ICON_SIZE + PRESET_TAB_CLOSE_HITBOX_PADDING * 2;
                    if (contains(
                        mouseX, mouseY,
                        closeLeft - PRESET_TAB_CLOSE_HITBOX_PADDING,
                        closeTop - PRESET_TAB_CLOSE_HITBOX_PADDING,
                        closeHitboxSize, closeHitboxSize
                    )) {
                        host.openPresetDeletePopup(label);
                        return true;
                    }
                }
                if (!host.isPresetDeleteDisabled(label) && shouldStartInlinePresetRename(label)) {
                    clearPendingPresetTabInteraction();
                    startInlinePresetRename(label);
                    return true;
                }
                if (!label.equals(host.activePresetName())) {
                    beginPendingTabInteraction(label, mouseX, mouseY, x);
                } else if (!host.isPresetDeleteDisabled(label)) {
                    beginPendingTabInteraction(label, mouseX, mouseY, x);
                }
                return true;
            }
        }
        x = getPresetTabStartX();
        for (int width : tabWidths) {
            if (width > 0) {
                x += width + TAB_GAP;
            }
        }
        int addTabX = Math.max(getPresetTabStartX(), x - TAB_GAP);
        if (addTabX + PRESET_TAB_ADD_WIDTH <= rightLimit
            && contains(mouseX, mouseY, addTabX, y, PRESET_TAB_ADD_WIDTH, TAB_HEIGHT)) {
            host.openCreatePresetPopup();
            return true;
        }
        return false;
    }

    private void beginPendingTabInteraction(String label, int mouseX, int mouseY, int tabLeft) {
        pendingPresetTabInteractionName = label;
        pendingPresetTabPressMouseX = mouseX;
        pendingPresetTabPressMouseY = mouseY;
        pendingPresetTabPressTabLeft = tabLeft;
    }

    int getPresetTabRightLimit() {
        return Math.max(getPresetTabStartX(), host.titleTextX() - PRESET_TAB_TITLE_GAP);
    }

    boolean hasPendingPresetTabInteraction() {
        return pendingPresetTabInteractionName != null;
    }

    boolean isDraggingPresetTab() {
        return draggingPresetTabName != null;
    }

    boolean hasPendingPresetDropdownDrag() {
        return pendingPresetDropdownDragName != null;
    }

    boolean isDraggingPresetDropdown() {
        return draggingPresetDropdownName != null;
    }

    void clearPendingPresetTabInteraction() {
        pendingPresetTabInteractionName = null;
        pendingPresetTabPressMouseX = 0;
        pendingPresetTabPressMouseY = 0;
        pendingPresetTabPressTabLeft = 0;
    }

    void updatePendingPresetTabInteraction(int mouseX, int mouseY) {
        if (pendingPresetTabInteractionName == null || draggingPresetTabName != null) {
            return;
        }
        int dx = Math.abs(mouseX - pendingPresetTabPressMouseX);
        int dy = Math.abs(mouseY - pendingPresetTabPressMouseY);
        if (dx < PRESET_TAB_DRAG_THRESHOLD && dy < PRESET_TAB_DRAG_THRESHOLD) {
            return;
        }
        String presetName = pendingPresetTabInteractionName;
        int tabLeft = pendingPresetTabPressTabLeft;
        clearPendingPresetTabInteraction();
        if (host.isPresetDeleteDisabled(presetName)) {
            return;
        }
        beginPresetTabDrag(presetName, mouseX, tabLeft);
    }

    private void beginPresetTabDrag(String presetName, int mouseX, int tabLeft) {
        draggingPresetTabName = presetName;
        draggingPresetTabPointerOffsetX = mouseX - tabLeft;
        draggingPresetTabCurrentX = tabLeft;
    }

    private void normalizePresetTabOrder() {
        String defaultPresetName = PresetManager.getDefaultPresetName();
        if (defaultPresetName == null || defaultPresetName.isEmpty()) {
            return;
        }
        if (presetTabOrder.remove(defaultPresetName)) {
            presetTabOrder.add(0, defaultPresetName);
        }
    }

    void updatePresetTabDrag(int mouseX) {
        if (draggingPresetTabName == null) {
            return;
        }
        List<String> tabs =
            getRenderedPresetTabsForWidth(getPresetTabRightLimit() - getPresetTabStartX());
        int currentIndex = tabs.indexOf(draggingPresetTabName);
        if (currentIndex < 0) {
            endPresetTabDrag();
            return;
        }
        int startX = getPresetTabStartX();
        int rightLimit = getPresetTabRightLimit();
        int[] widths = computePresetTabWidths(tabs, rightLimit - startX, PRESET_TAB_ADD_WIDTH);
        int[] xs = computePresetTabXs(widths, startX);
        if (currentIndex >= widths.length) {
            return;
        }
        int draggedWidth = widths[currentIndex];
        draggingPresetTabCurrentX = mouseX - draggingPresetTabPointerOffsetX;
        int dragCenter = draggingPresetTabCurrentX + draggedWidth / 2;
        if (isPresetGroupTab(draggingPresetTabName)) {
            updatePresetGroupDragOrder(tabs, widths, xs, currentIndex, dragCenter);
            return;
        }
        int targetIndex = 0;
        for (int i = 0; i < tabs.size() && i < widths.length; i++) {
            if (i == currentIndex) {
                continue;
            }
            int center = xs[i] + widths[i] / 2;
            if (dragCenter > center) {
                targetIndex++;
            }
        }
        int orderIndex = presetTabOrder.indexOf(draggingPresetTabName);
        if (orderIndex < 0) {
            return;
        }
        int clampedTarget = Mth.clamp(targetIndex, 1, presetTabOrder.size() - 1);
        if (clampedTarget != orderIndex) {
            presetTabOrder.remove(orderIndex);
            presetTabOrder.add(clampedTarget, draggingPresetTabName);
            normalizePresetTabOrder();
        }
    }

    private void updatePresetGroupDragOrder(
        List<String> tabs, int[] widths, int[] xs, int currentIndex, int dragCenter
    ) {
        Settings settings = host.settings();
        if (settings == null || settings.presetGroupOrder == null) {
            return;
        }
        String groupKey = getPresetGroupKeyFromTab(draggingPresetTabName);
        int orderIndex = settings.presetGroupOrder.indexOf(groupKey);
        if (orderIndex < 0) {
            return;
        }
        int targetIndex = 0;
        for (int i = 0; i < tabs.size() && i < widths.length; i++) {
            if (i == currentIndex || !isPresetGroupTab(tabs.get(i))) {
                continue;
            }
            int center = xs[i] + widths[i] / 2;
            if (dragCenter > center) {
                targetIndex++;
            }
        }
        int clampedTarget = Mth.clamp(targetIndex, 0, settings.presetGroupOrder.size() - 1);
        if (clampedTarget != orderIndex) {
            settings.presetGroupOrder.remove(orderIndex);
            settings.presetGroupOrder.add(clampedTarget, groupKey);
            SettingsManager.save(settings);
        }
    }

    void endPresetTabDrag() {
        if (draggingPresetTabName != null && draggingPresetTabCurrentX > 0) {
            int dropX = draggingPresetTabCurrentX + Math.max(1, PRESET_GROUP_TAB_WIDTH / 2);
            String groupKey = getPresetGroupAt(dropX, TAB_BAR_TOP + TAB_HEIGHT / 2);
            if (!groupKey.isEmpty() && !isPresetGroupTab(draggingPresetTabName)) {
                setPresetGroupColor(draggingPresetTabName, groupKey);
            } else if (!isPresetGroupTab(draggingPresetTabName)
                && !getPresetGroupKey(draggingPresetTabName).isEmpty()
                && !isPointInPresetGroupSpan(
                    dropX, TAB_BAR_TOP + TAB_HEIGHT / 2, getPresetGroupKey(draggingPresetTabName))) {
                setPresetGroupColor(draggingPresetTabName, null);
            }
        }
        if (draggingPresetTabName != null && draggingPresetTabCurrentX > 0) {
            presetTabXAnimations
                .computeIfAbsent(draggingPresetTabName, key -> new AnimatedValue(draggingPresetTabCurrentX))
                .setValue(draggingPresetTabCurrentX);
        }
        draggingPresetTabName = null;
        draggingPresetTabPointerOffsetX = 0;
        draggingPresetTabCurrentX = 0;
        clearPendingPresetTabInteraction();
    }

    boolean releasePendingPresetTabInteraction() {
        if (pendingPresetTabInteractionName == null) {
            return false;
        }
        String presetName = pendingPresetTabInteractionName;
        clearPendingPresetTabInteraction();
        if (isPresetGroupTab(presetName)) {
            togglePresetGroupExpanded(getPresetGroupKeyFromTab(presetName));
            return true;
        }
        if (!presetName.equals(host.activePresetName())) {
            host.switchPreset(presetName);
        }
        return true;
    }

    boolean isPointInPresetTabBarContextZone(int mouseX, int mouseY) {
        int startX = getPresetTabStartX();
        int rightLimit = host.presetOverflowTabRight();
        return contains(
            mouseX, mouseY, startX, TAB_BAR_TOP - 4,
            Math.max(0, rightLimit - startX), TAB_HEIGHT + 8);
    }

    String getPresetTabAt(int mouseX, int mouseY) {
        int startX = getPresetTabStartX();
        int y = TAB_BAR_TOP;
        int rightLimit = getPresetTabRightLimit();
        List<String> tabs = getRenderedPresetTabsForWidth(rightLimit - startX);
        int[] widths = computePresetTabWidths(tabs, rightLimit - startX, PRESET_TAB_ADD_WIDTH);
        int[] xs = computePresetTabXs(widths, startX);
        for (int i = 0; i < tabs.size() && i < widths.length; i++) {
            if (contains(mouseX, mouseY, xs[i], y, widths[i], TAB_HEIGHT)) {
                String tabName = tabs.get(i);
                return isPresetGroupTab(tabName) ? null : tabName;
            }
        }
        return null;
    }

    String getPresetGroupAt(int mouseX, int mouseY) {
        int startX = getPresetTabStartX();
        int y = TAB_BAR_TOP;
        int rightLimit = getPresetTabRightLimit();
        List<String> tabs = getRenderedPresetTabsForWidth(rightLimit - startX);
        int[] widths = computePresetTabWidths(tabs, rightLimit - startX, PRESET_TAB_ADD_WIDTH);
        int[] xs = computePresetTabXs(widths, startX);
        for (int i = 0; i < tabs.size() && i < widths.length; i++) {
            if (contains(mouseX, mouseY, xs[i], y, widths[i], TAB_HEIGHT)
                && isPresetGroupTab(tabs.get(i))) {
                return getPresetGroupKeyFromTab(tabs.get(i));
            }
        }
        return "";
    }

    private boolean isPointInPresetGroupSpan(int mouseX, int mouseY, String groupKey) {
        if (!isValidPresetGroupColorKey(groupKey)) {
            return false;
        }
        int startX = getPresetTabStartX();
        int y = TAB_BAR_TOP;
        int rightLimit = getPresetTabRightLimit();
        List<String> tabs = getRenderedPresetTabsForWidth(rightLimit - startX);
        int[] widths = computePresetTabWidths(tabs, rightLimit - startX, PRESET_TAB_ADD_WIDTH);
        int[] xs = computePresetTabXs(widths, startX);
        int left = -1;
        int right = -1;
        for (int i = 0; i < tabs.size() && i < widths.length; i++) {
            String tab = tabs.get(i);
            boolean inGroup = isPresetGroupTab(tab)
                ? groupKey.equals(getPresetGroupKeyFromTab(tab))
                : groupKey.equals(getPresetGroupKey(tab));
            if (inGroup) {
                if (left < 0) {
                    left = xs[i];
                }
                right = xs[i] + widths[i];
            } else if (left >= 0) {
                break;
            }
        }
        return left >= 0 && contains(
            mouseX, mouseY, left, y - 4, Math.max(0, right - left), TAB_HEIGHT + 8);
    }

    String getPresetGroupColorLabel(String key) {
        if ("sky".equals(key)) return "Sky";
        if ("mint".equals(key)) return "Mint";
        if ("amber".equals(key)) return "Amber";
        if ("rose".equals(key)) return "Rose";
        if ("violet".equals(key)) return "Violet";
        return key;
    }

    String getNextPresetGroupColorKey() {
        Settings settings = host.settings();
        if (settings == null) {
            return "";
        }
        if (settings.presetGroupOrder == null) {
            settings.presetGroupOrder = new ArrayList<>();
        }
        for (String key : PRESET_GROUP_COLOR_KEYS) {
            if (!settings.presetGroupOrder.contains(key)) {
                return key;
            }
        }
        return "";
    }

    void createPresetGroup() {
        String key = getNextPresetGroupColorKey();
        Settings settings = host.settings();
        if (key.isEmpty() || settings == null) {
            return;
        }
        if (settings.presetGroupOrder == null) {
            settings.presetGroupOrder = new ArrayList<>();
        }
        if (settings.presetGroupsExpanded == null) {
            settings.presetGroupsExpanded = new LinkedHashMap<>();
        }
        settings.presetGroupOrder.add(key);
        settings.presetGroupsExpanded.put(key, true);
        SettingsManager.save(settings);
    }

    void deletePresetGroup(String groupKey) {
        Settings settings = host.settings();
        if (!isValidPresetGroupColorKey(groupKey) || settings == null) {
            return;
        }
        if (settings.presetGroupOrder != null) {
            settings.presetGroupOrder.remove(groupKey);
        }
        if (settings.presetGroupsExpanded != null) {
            settings.presetGroupsExpanded.remove(groupKey);
        }
        if (settings.presetGroupColors != null) {
            settings.presetGroupColors.entrySet().removeIf(entry -> groupKey.equals(entry.getValue()));
        }
        SettingsManager.save(settings);
    }

    void recolorPresetGroup(String oldKey, String newKey) {
        Settings settings = host.settings();
        if (!isValidPresetGroupColorKey(oldKey) || !isValidPresetGroupColorKey(newKey)
            || settings == null || oldKey.equals(newKey)) {
            return;
        }
        if (settings.presetGroupOrder == null) {
            settings.presetGroupOrder = new ArrayList<>();
        }
        if (settings.presetGroupOrder.contains(newKey)) {
            return;
        }
        int index = settings.presetGroupOrder.indexOf(oldKey);
        if (index >= 0) {
            settings.presetGroupOrder.set(index, newKey);
        }
        if (settings.presetGroupsExpanded != null) {
            Boolean expanded = settings.presetGroupsExpanded.remove(oldKey);
            settings.presetGroupsExpanded.put(newKey, expanded == null || expanded);
        }
        if (settings.presetGroupColors != null) {
            for (Map.Entry<String, String> entry : settings.presetGroupColors.entrySet()) {
                if (oldKey.equals(entry.getValue())) {
                    entry.setValue(newKey);
                }
            }
        }
        SettingsManager.save(settings);
    }

    void setPresetGroupColor(String presetName, String colorKey) {
        Settings settings = host.settings();
        if (presetName == null || presetName.isEmpty() || settings == null) {
            return;
        }
        if (settings.presetGroupColors == null) {
            settings.presetGroupColors = new LinkedHashMap<>();
        }
        if (settings.presetGroupsExpanded == null) {
            settings.presetGroupsExpanded = new LinkedHashMap<>();
        }
        if (colorKey == null || colorKey.isEmpty()) {
            settings.presetGroupColors.remove(presetName);
        } else {
            if (settings.presetGroupOrder == null) {
                settings.presetGroupOrder = new ArrayList<>();
            }
            if (!settings.presetGroupOrder.contains(colorKey)) {
                settings.presetGroupOrder.add(colorKey);
            }
            settings.presetGroupColors.put(presetName, colorKey);
            settings.presetGroupsExpanded.putIfAbsent(colorKey, true);
        }
        SettingsManager.save(settings);
    }

    boolean isPresetGroupTab(String tabName) {
        return tabName != null && tabName.startsWith(PRESET_GROUP_TAB_PREFIX);
    }

    private String getPresetGroupTabName(String colorKey) {
        return PRESET_GROUP_TAB_PREFIX + colorKey;
    }

    String getPresetGroupKeyFromTab(String tabName) {
        if (!isPresetGroupTab(tabName)) {
            return "";
        }
        return tabName.substring(PRESET_GROUP_TAB_PREFIX.length());
    }

    String getPresetGroupKey(String presetName) {
        Settings settings = host.settings();
        if (presetName == null || settings == null || settings.presetGroupColors == null) {
            return "";
        }
        String key = settings.presetGroupColors.get(presetName);
        return isValidPresetGroupColorKey(key) ? key : "";
    }

    private boolean isValidPresetGroupColorKey(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        for (String candidate : PRESET_GROUP_COLOR_KEYS) {
            if (candidate.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPresetGroupExpanded(String colorKey) {
        Settings settings = host.settings();
        if (!isValidPresetGroupColorKey(colorKey)) {
            return false;
        }
        if (settings == null || settings.presetGroupsExpanded == null) {
            return true;
        }
        Boolean expanded = settings.presetGroupsExpanded.get(colorKey);
        return expanded == null || expanded;
    }

    private void togglePresetGroupExpanded(String colorKey) {
        Settings settings = host.settings();
        if (!isValidPresetGroupColorKey(colorKey) || settings == null) {
            return;
        }
        setPresetGroupExpanded(colorKey, !isPresetGroupExpanded(colorKey));
    }

    private void setPresetGroupExpanded(String colorKey, boolean expanded) {
        Settings settings = host.settings();
        if (!isValidPresetGroupColorKey(colorKey) || settings == null) {
            return;
        }
        if (settings.presetGroupsExpanded == null) {
            settings.presetGroupsExpanded = new LinkedHashMap<>();
        }
        settings.presetGroupsExpanded.put(colorKey, expanded);
        if (expanded) {
            for (String presetName : host.availablePresets()) {
                if (colorKey.equals(getPresetGroupKey(presetName))) {
                    AnimatedValue appear = presetTabAppearAnimations.computeIfAbsent(
                        presetName, key -> new AnimatedValue(1f));
                    appear.setValue(0f);
                    appear.animateTo(1f, 180, AnimationHelper::easeOutCubic);
                }
            }
        }
        SettingsManager.save(settings);
    }

    private int getPresetGroupColorByKey(String key) {
        for (int i = 0; i < PRESET_GROUP_COLOR_KEYS.length; i++) {
            if (PRESET_GROUP_COLOR_KEYS[i].equals(key)) {
                return PRESET_GROUP_COLORS[i];
            }
        }
        return 0;
    }

    int getPresetGroupColor(String presetName) {
        if (isPresetGroupTab(presetName)) {
            return getPresetGroupColorByKey(getPresetGroupKeyFromTab(presetName));
        }
        return getPresetGroupColorByKey(getPresetGroupKey(presetName));
    }

    void beginPresetDropdownDrag(String presetName, int mouseX, int mouseY) {
        pendingPresetDropdownDragName = presetName;
        pendingPresetDropdownPressMouseX = mouseX;
        pendingPresetDropdownPressMouseY = mouseY;
    }

    void clearPresetDropdownDragState() {
        pendingPresetDropdownDragName = null;
        pendingPresetDropdownPressMouseX = 0;
        pendingPresetDropdownPressMouseY = 0;
        draggingPresetDropdownName = null;
        draggingPresetDropdownCurrentX = 0;
        draggingPresetDropdownCurrentY = 0;
    }

    void updatePendingPresetDropdownDrag(int mouseX, int mouseY) {
        if (pendingPresetDropdownDragName == null || draggingPresetDropdownName != null) {
            return;
        }
        int dx = Math.abs(mouseX - pendingPresetDropdownPressMouseX);
        int dy = Math.abs(mouseY - pendingPresetDropdownPressMouseY);
        if (dx < PRESET_TAB_DRAG_THRESHOLD && dy < PRESET_TAB_DRAG_THRESHOLD) {
            return;
        }
        draggingPresetDropdownName = pendingPresetDropdownDragName;
        pendingPresetDropdownDragName = null;
        draggingPresetDropdownCurrentX = mouseX;
        draggingPresetDropdownCurrentY = mouseY;
    }

    void updatePresetDropdownDrag(int mouseX, int mouseY) {
        draggingPresetDropdownCurrentX = mouseX;
        draggingPresetDropdownCurrentY = mouseY;
    }

    void finishPresetDropdownDrag(int mouseX, int mouseY) {
        String presetName = draggingPresetDropdownName;
        if (presetName == null || presetName.isEmpty()) {
            clearPresetDropdownDragState();
            return;
        }
        if (isPointInPresetTabBarDropZone(mouseX, mouseY)) {
            String groupKey = getPresetGroupAt(mouseX, mouseY);
            if (!groupKey.isEmpty()) {
                setPresetGroupColor(presetName, groupKey);
            } else {
                insertPresetTabAtDropPosition(presetName, mouseX);
            }
            host.closePresetDropdown();
        }
        clearPresetDropdownDragState();
    }

    boolean releasePendingPresetDropdownDrag() {
        if (pendingPresetDropdownDragName == null) {
            return false;
        }
        String presetName = pendingPresetDropdownDragName;
        clearPresetDropdownDragState();
        host.closePresetDropdown();
        if (!presetName.equals(host.activePresetName())) {
            host.switchPreset(presetName);
        }
        return true;
    }

    private boolean isPointInPresetTabBarDropZone(int mouseX, int mouseY) {
        int startX = getPresetTabStartX();
        int rightLimit = getPresetTabRightLimit();
        return contains(
            mouseX, mouseY, startX, TAB_BAR_TOP - 4,
            Math.max(0, rightLimit - startX), TAB_HEIGHT + 8);
    }

    private void insertPresetTabAtDropPosition(String presetName, int mouseX) {
        if (presetName == null || presetName.isEmpty() || !host.availablePresets().contains(presetName)) {
            return;
        }
        if (host.isPresetDeleteDisabled(presetName)) {
            return;
        }
        int targetIndex = getPresetTabDropIndex(mouseX);
        presetTabOrder.remove(presetName);
        int clampedTarget = Mth.clamp(targetIndex, 1, presetTabOrder.size());
        presetTabOrder.add(clampedTarget, presetName);
        normalizePresetTabOrder();
        presetTabAppearAnimations
            .computeIfAbsent(presetName, key -> new AnimatedValue(1f)).setValue(1f);
    }

    private int getPresetTabDropIndex(int mouseX) {
        int startX = getPresetTabStartX();
        int rightLimit = getPresetTabRightLimit();
        List<String> tabs = getRenderedPresetTabsForWidth(rightLimit - startX);
        int[] widths = computePresetTabWidths(tabs, rightLimit - startX, PRESET_TAB_ADD_WIDTH);
        int[] xs = computePresetTabXs(widths, startX);
        int targetIndex = 0;
        for (int i = 0; i < tabs.size() && i < widths.length; i++) {
            String tabName = tabs.get(i);
            if (tabName != null
                && (tabName.equals(draggingPresetDropdownName) || isPresetGroupTab(tabName))) {
                continue;
            }
            if (mouseX > xs[i] + widths[i] / 2) {
                targetIndex++;
            }
        }
        return targetIndex;
    }

    void renderDraggedPresetDropdownTab(GuiGraphics context, int mouseX, int mouseY) {
        if (draggingPresetDropdownName == null) {
            return;
        }
        int width = Mth.clamp(
            host.font().width(draggingPresetDropdownName) + PRESET_TAB_TEXT_PADDING * 2,
            TAB_MIN_WIDTH,
            TAB_MAX_WIDTH
        );
        int x = draggingPresetDropdownCurrentX - width / 2;
        int y = draggingPresetDropdownCurrentY - TAB_HEIGHT / 2;
        drawPresetTab(context, mouseX, mouseY, draggingPresetDropdownName, x, y, width, true);
    }

    private int[] computePresetTabXs(int[] widths, int startX) {
        return PathmindPresetTabLayout.computeXs(widths, startX);
    }

    private void drawPresetTab(
        GuiGraphics context, int mouseX, int mouseY, String label,
        int x, int y, int tabWidth, boolean dragging
    ) {
        if (isPresetGroupTab(label)) {
            drawPresetGroupTab(context, mouseX, mouseY, label, x, y, tabWidth, dragging);
            return;
        }
        String displayLabel = getPresetTabDisplayLabel(label);
        boolean active = label.equals(host.activePresetName());
        boolean hovered = contains(mouseX, mouseY, x, y, tabWidth, TAB_HEIGHT);
        int groupColor = getPresetGroupColor(label);
        int fill = active ? UITheme.BUTTON_ACTIVE_BG : UITheme.BUTTON_DEFAULT_BG;
        int border = groupColor != 0 ? groupColor
            : (active ? host.accentColor() : UITheme.BORDER_DEFAULT);
        if (!active && hovered) {
            fill = UITheme.BUTTON_DEFAULT_HOVER;
            border = groupColor != 0 ? groupColor : UITheme.BORDER_HIGHLIGHT;
        }
        if (dragging) {
            fill = UITheme.TOOLBAR_BG_ACTIVE;
        }

        float appear = dragging ? 1f : getPresetTabAppearProgress(label);
        int fillColor = AnimationHelper.multiplyAlpha(fill, appear);
        int borderColor = AnimationHelper.multiplyAlpha(border, appear);
        int textColor = AnimationHelper.multiplyAlpha(
            active ? UITheme.TEXT_PRIMARY : UITheme.TEXT_SECONDARY, appear);
        if (hovered && !active) {
            textColor = AnimationHelper.multiplyAlpha(UITheme.TEXT_PRIMARY, appear);
        }

        context.fill(x, y, x + tabWidth, y + TAB_HEIGHT, fillColor);
        if (groupColor != 0) {
            context.fill(
                x + 1, y + 1, x + tabWidth - 1, y + 3,
                AnimationHelper.multiplyAlpha(groupColor, appear));
        }
        DrawContextBridge.drawBorderInLayer(context, x, y, tabWidth, TAB_HEIGHT, borderColor);
        boolean deletable = !host.isPresetDeleteDisabled(label);
        int closeSpace = deletable
            ? PRESET_TAB_CLOSE_GAP + PRESET_TAB_CLOSE_ICON_SIZE + PRESET_TAB_CLOSE_HITBOX_PADDING * 2
            : 0;
        int textMaxWidth = Math.max(4, tabWidth - PRESET_TAB_TEXT_PADDING * 2 - closeSpace);
        if (!label.equals(inlinePresetRenameName)) {
            String drawLabel = TextRenderUtil.trimWithEllipsis(host.font(), displayLabel, textMaxWidth);
            context.drawString(
                host.font(), Component.literal(drawLabel), x + PRESET_TAB_TEXT_PADDING,
                y + (TAB_HEIGHT - host.font().lineHeight) / 2 + 1, textColor, false);
        }

        if (deletable) {
            int closeLeft = x + tabWidth - PRESET_TAB_TEXT_PADDING - PRESET_TAB_CLOSE_ICON_SIZE;
            int closeTop = y + (TAB_HEIGHT - PRESET_TAB_CLOSE_ICON_SIZE) / 2;
            int closeHitboxSize =
                PRESET_TAB_CLOSE_ICON_SIZE + PRESET_TAB_CLOSE_HITBOX_PADDING * 2;
            boolean closeHovered = contains(
                mouseX, mouseY,
                closeLeft - PRESET_TAB_CLOSE_HITBOX_PADDING,
                closeTop - PRESET_TAB_CLOSE_HITBOX_PADDING,
                closeHitboxSize, closeHitboxSize
            );
            int closeColor = closeHovered ? UITheme.STATE_ERROR : UITheme.ICON_MUTED;
            PathmindIconRenderer.drawCloseX(
                context, closeLeft, closeTop, PRESET_TAB_CLOSE_ICON_SIZE,
                AnimationHelper.multiplyAlpha(closeColor, appear));
        }
    }

    private void drawPresetGroupTab(
        GuiGraphics context, int mouseX, int mouseY, String label,
        int x, int y, int tabWidth, boolean dragging
    ) {
        String groupKey = getPresetGroupKeyFromTab(label);
        int groupColor = getPresetGroupColorByKey(groupKey);
        float appear = dragging ? 1f : getPresetTabAppearProgress(label);
        boolean expanded = isPresetGroupExpanded(groupKey);
        int squareSize = 8;
        int squareX = x + (tabWidth - squareSize) / 2;
        int squareY = y + (TAB_HEIGHT - squareSize) / 2;
        context.fill(
            squareX + 1, squareY + 1, squareX + squareSize + 1, squareY + squareSize + 1,
            AnimationHelper.multiplyAlpha(UITheme.BACKGROUND_SECONDARY, appear * 0.75f));
        if (expanded) {
            context.fill(
                squareX + 1, squareY + 1, squareX + squareSize - 1, squareY + squareSize - 1,
                AnimationHelper.multiplyAlpha(UITheme.BACKGROUND_SECONDARY, appear));
            context.fill(
                squareX + 3, squareY + 3, squareX + squareSize - 3, squareY + squareSize - 3,
                AnimationHelper.multiplyAlpha(groupColor, appear));
        } else {
            context.fill(
                squareX + 1, squareY + 1, squareX + squareSize - 1, squareY + squareSize - 1,
                AnimationHelper.multiplyAlpha(groupColor, appear));
        }
        DrawContextBridge.drawBorderInLayer(
            context, squareX, squareY, squareSize, squareSize,
            AnimationHelper.multiplyAlpha(groupColor, appear));
    }

    private String getPresetTabDisplayLabel(String label) {
        return isPresetGroupTab(label) ? "" : label;
    }

    private List<String> getRenderedPresetTabs() {
        List<String> tabs = new ArrayList<>();
        HashSet<String> groupedPresets = new HashSet<>();
        Settings settings = host.settings();
        if (settings != null && settings.presetGroupOrder != null) {
            for (String groupKey : settings.presetGroupOrder) {
                if (!isValidPresetGroupColorKey(groupKey)) {
                    continue;
                }
                tabs.add(getPresetGroupTabName(groupKey));
                if (isPresetGroupExpanded(groupKey)) {
                    for (String name : presetTabOrder) {
                        if (host.availablePresets().contains(name)
                            && groupKey.equals(getPresetGroupKey(name))) {
                            tabs.add(name);
                            groupedPresets.add(name);
                        }
                    }
                }
            }
        }
        for (String name : presetTabOrder) {
            if (host.availablePresets().contains(name)
                && !groupedPresets.contains(name) && getPresetGroupKey(name).isEmpty()) {
                tabs.add(name);
            }
        }
        for (String name : host.availablePresets()) {
            if (!tabs.contains(name) && getPresetGroupKey(name).isEmpty()) {
                tabs.add(name);
            }
        }
        String defaultPresetName = PresetManager.getDefaultPresetName();
        if (defaultPresetName != null && tabs.remove(defaultPresetName)) {
            tabs.add(0, defaultPresetName);
        }
        return tabs;
    }

    private List<String> getRenderedPresetTabsForWidth(int availableWidth) {
        List<String> allTabs = getRenderedPresetTabs();
        if (allTabs.isEmpty() || doPresetTabsFit(allTabs, availableWidth, PRESET_TAB_ADD_WIDTH)) {
            return allTabs;
        }

        List<String> visibleTabs = new ArrayList<>();
        for (String name : allTabs) {
            List<String> candidate = new ArrayList<>(visibleTabs);
            candidate.add(name);
            if (!doPresetTabsFit(candidate, availableWidth, PRESET_TAB_ADD_WIDTH)) {
                break;
            }
            visibleTabs.add(name);
        }

        String activePresetName = host.activePresetName();
        if (activePresetName != null && allTabs.contains(activePresetName)
            && !visibleTabs.contains(activePresetName)) {
            while (!visibleTabs.isEmpty()) {
                List<String> candidate = new ArrayList<>(visibleTabs);
                candidate.add(activePresetName);
                if (doPresetTabsFit(candidate, availableWidth, PRESET_TAB_ADD_WIDTH)) {
                    visibleTabs.add(activePresetName);
                    break;
                }
                visibleTabs.remove(visibleTabs.size() - 1);
            }
            if (visibleTabs.isEmpty()) {
                List<String> candidate = new ArrayList<>();
                candidate.add(activePresetName);
                if (doPresetTabsFit(candidate, availableWidth, PRESET_TAB_ADD_WIDTH)) {
                    visibleTabs.add(activePresetName);
                }
            }
        }
        return visibleTabs;
    }

    boolean isInlinePresetRenameActive() {
        EditBox field = host.inlinePresetRenameField();
        return field != null && field.isVisible()
            && inlinePresetRenameName != null && !inlinePresetRenameName.isEmpty();
    }

    boolean canStartInlinePresetRename(String presetName) {
        return presetName != null && !presetName.isEmpty()
            && !host.isPresetDeleteDisabled(presetName)
            && host.inlinePresetRenameField() != null;
    }

    private boolean shouldStartInlinePresetRename(String presetName) {
        long now = System.currentTimeMillis();
        boolean doubleClick = presetName != null && presetName.equals(lastPresetTitleClickName)
            && now - lastPresetTitleClickTime <= PRESET_TAB_RENAME_DOUBLE_CLICK_MS;
        lastPresetTitleClickName = presetName;
        lastPresetTitleClickTime = now;
        return doubleClick;
    }

    private int getPresetTabTextMaxWidth(String label, int tabWidth) {
        boolean deletable = !host.isPresetDeleteDisabled(label);
        int closeSpace = deletable
            ? PRESET_TAB_CLOSE_GAP + PRESET_TAB_CLOSE_ICON_SIZE + PRESET_TAB_CLOSE_HITBOX_PADDING * 2
            : 0;
        return Math.max(4, tabWidth - PRESET_TAB_TEXT_PADDING * 2 - closeSpace);
    }

    private int[] getPresetTabTitleBounds(String label, int x, int y, int tabWidth) {
        int textMaxWidth = getPresetTabTextMaxWidth(label, tabWidth);
        String drawLabel = TextRenderUtil.trimWithEllipsis(
            host.font(), getPresetTabDisplayLabel(label), textMaxWidth);
        int textX = x + PRESET_TAB_TEXT_PADDING;
        int textY = y + (TAB_HEIGHT - host.font().lineHeight) / 2 + 1;
        int textWidth = Math.max(4, host.font().width(drawLabel));
        return new int[]{
            textX, textY - 1, Math.min(textWidth, textMaxWidth), host.font().lineHeight + 2
        };
    }

    void startInlinePresetRename(String presetName) {
        if (!canStartInlinePresetRename(presetName)) {
            return;
        }
        host.closeCreatePresetPopup();
        host.closeRenamePresetPopup();
        clearPendingPresetTabInteraction();
        endPresetTabDrag();
        inlinePresetRenameName = presetName;
        EditBox field = host.inlinePresetRenameField();
        field.setValue(presetName);
        field.setVisible(true);
        field.setEditable(true);
        field.setFocused(true);
        field.moveCursorToStart(false);
        field.setHighlightPos(presetName.length());
    }

    void stopInlinePresetRename(boolean commit) {
        if (!isInlinePresetRenameActive()) {
            return;
        }
        EditBox field = host.inlinePresetRenameField();
        boolean renamed = false;
        if (commit && field != null) {
            renamed = host.renamePresetInternal(inlinePresetRenameName, field.getValue());
        }
        if (commit && !renamed) {
            field.setFocused(true);
            return;
        }
        inlinePresetRenameName = "";
        if (field != null) {
            PathmindTextField.deactivate(field);
        }
    }

    private void renderInlinePresetRenameField(
        GuiGraphics context, int mouseX, int mouseY, List<String> tabs,
        int[] tabWidths, int[] tabXs, int y, int dragIndex
    ) {
        EditBox field = host.inlinePresetRenameField();
        if (!isInlinePresetRenameActive() || field == null) {
            return;
        }
        for (int i = 0; i < tabs.size() && i < tabWidths.length; i++) {
            if (i == dragIndex) {
                continue;
            }
            String label = tabs.get(i);
            if (!label.equals(inlinePresetRenameName)) {
                continue;
            }
            int tabWidth = tabWidths[i];
            if (tabWidth <= 0) {
                break;
            }
            int drawX = getAnimatedPresetTabX(label, tabXs[i]);
            int[] titleBounds = getPresetTabTitleBounds(label, drawX, y, tabWidth);
            int fieldX = titleBounds[0];
            int fieldWidth = getPresetTabTextMaxWidth(label, tabWidth);
            int fieldHeight = Math.max(host.font().lineHeight + 2, titleBounds[3]);
            int fieldY = titleBounds[1];
            int frameX = Math.max(drawX + 2, fieldX - 3);
            int frameY = y + 2;
            int frameWidth = Math.min(fieldWidth + 6, drawX + tabWidth - 2 - frameX);
            int frameHeight = TAB_HEIGHT - 4;
            context.fill(
                frameX, frameY, frameX + frameWidth, frameY + frameHeight,
                UITheme.BACKGROUND_SECONDARY);
            DrawContextBridge.drawBorderInLayer(
                context, frameX, frameY, frameWidth, frameHeight, host.accentColor());
            field.setVisible(true);
            field.setEditable(true);
            field.setPosition(fieldX, fieldY);
            field.setWidth(fieldWidth);
            field.setHeight(fieldHeight);
            field.render(context, mouseX, mouseY, 0f);
            return;
        }
        stopInlinePresetRename(false);
    }

    void queueAnimatedPresetDeletion(String presetName) {
        if (presetName == null || presetName.isEmpty()) {
            return;
        }
        if (presetName.equals(animatingPresetDeletionName)) {
            return;
        }
        if (draggingPresetTabName != null && draggingPresetTabName.equals(presetName)) {
            endPresetTabDrag();
        }
        AnimatedValue appear =
            presetTabAppearAnimations.computeIfAbsent(presetName, key -> new AnimatedValue(1f));
        appear.animateTo(0f, 140, AnimationHelper::easeOutCubic);
        animatingPresetDeletionName = presetName;
        animatingPresetDeletionExecuteAtMs = System.currentTimeMillis() + 140L;
    }

    private void tickQueuedPresetDeletionAnimation() {
        if (animatingPresetDeletionName == null) {
            return;
        }
        if (System.currentTimeMillis() < animatingPresetDeletionExecuteAtMs) {
            return;
        }
        String presetName = animatingPresetDeletionName;
        animatingPresetDeletionName = null;
        animatingPresetDeletionExecuteAtMs = 0L;
        host.attemptDeletePresetImmediate(presetName);
    }

    private int getAnimatedPresetTabX(String presetName, int targetX) {
        AnimatedValue animation =
            presetTabXAnimations.computeIfAbsent(presetName, key -> new AnimatedValue(targetX));
        if (!animation.isAnimating() && Math.abs(animation.getValue() - targetX) < 0.5f) {
            animation.setValue(targetX);
            return targetX;
        }
        animation.animateTo(targetX, 120, AnimationHelper::easeOutCubic);
        animation.tick();
        return Math.round(animation.getValue());
    }

    private float getPresetTabAppearProgress(String presetName) {
        AnimatedValue animation =
            presetTabAppearAnimations.computeIfAbsent(presetName, key -> new AnimatedValue(1f));
        animation.tick();
        return Mth.clamp(animation.getValue(), 0f, 1f);
    }

    private int getPresetTabStartX() {
        return 6;
    }

    int[] computePresetTabWidths(int availableWidth, int createTabWidth) {
        return computePresetTabWidths(host.availablePresets(), availableWidth, createTabWidth);
    }

    private int[] computePresetTabWidths(
        List<String> tabNames, int availableWidth, int createTabWidth
    ) {
        return PathmindPresetTabLayout.computeWidths(
            tabNames, availableWidth, createTabWidth, host.font(),
            this::isPresetGroupTab, label -> !host.isPresetDeleteDisabled(label)
        );
    }

    private boolean doPresetTabsFit(
        List<String> tabNames, int availableWidth, int createTabWidth
    ) {
        return PathmindPresetTabLayout.fits(
            tabNames, availableWidth, createTabWidth, this::isPresetGroupTab);
    }

    void movePresetTabToEnd(String presetName) {
        if (presetName == null || presetName.isEmpty()) {
            return;
        }
        if (presetTabOrder.remove(presetName)) {
            presetTabOrder.add(presetName);
        }
    }

    void refreshAvailablePresets() {
        List<String> availablePresets = host.availablePresets();
        HashSet<String> previousSet = new HashSet<>(presetTabOrder);
        HashSet<String> availableSet = new HashSet<>(availablePresets);
        presetTabOrder.removeIf(name -> !availableSet.contains(name));
        for (String preset : availablePresets) {
            if (!presetTabOrder.contains(preset)) {
                presetTabOrder.add(preset);
                AnimatedValue appear =
                    presetTabAppearAnimations.computeIfAbsent(preset, key -> new AnimatedValue(1f));
                if (presetTabsInitialized && !previousSet.contains(preset)) {
                    appear.setValue(0f);
                    appear.animateTo(1f, 180, AnimationHelper::easeOutCubic);
                } else {
                    appear.setValue(1f);
                }
            }
        }
        normalizePresetTabOrder();
        presetTabXAnimations.entrySet().removeIf(entry -> !availableSet.contains(entry.getKey()));
        presetTabAppearAnimations.entrySet().removeIf(entry -> !availableSet.contains(entry.getKey()));
        presetTabsInitialized = true;
    }

    private boolean contains(int mouseX, int mouseY, int x, int y, int width, int height) {
        return UiHitTest.contains(mouseX, mouseY, x, y, width, height);
    }
}
