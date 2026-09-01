package com.pathmind.ui.graph;

import com.pathmind.data.SettingsManager;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeParameter;
import com.pathmind.nodes.NodeType;
import com.pathmind.nodes.ParameterType;
import com.pathmind.nodes.RuntimeValueScope;
import com.pathmind.nodes.StartLaunchMode;
import com.pathmind.ui.animation.AnimatedValue;
import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.control.PathmindIconRenderer;
import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.ui.tooltip.TooltipRenderer;
import com.pathmind.util.DrawContextBridge;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Owns rendering, animation state, hit testing, and input for embedded node controls. */
final class NodeControlController {
    private static final int NODE_HEADER_BUTTON_SIZE = 12;
    private static final int PARAMETER_INPUT_HEIGHT = 16;
    private static final int PARAMETER_INPUT_GAP = 4;
    private static final int DIRECTION_MODE_TAB_HEIGHT = 18;

    interface Host {
        int cameraX();
        int cameraY();
        int screenToWorldX(int screenX);
        int screenToWorldY(int screenY);
        float zoomScale();
        boolean compactViewportMode();
        Node sensorDropTarget();
        Node actionDropTarget();
        Node parameterDropTarget();
        Integer parameterDropSlotIndex();
        Node nodeAt(int screenX, int screenY);
        int getStartModeButtonWorldX(Node node);
        int getStartModeButtonWorldY(Node node);
        boolean isPointInsideStartModeButton(Node node, int screenX, int screenY);
        void pushUndoState();
        void notifyNodeParametersChanged(Node node);
        void stopMessageEditing(boolean commit);
        void startMessageEditing(Node node, int index);
        Node messageEditingNode();
        int messageEditingIndex();
        String translate(String key);
        String trimTextToWidth(String text, Font renderer, int maxWidth);
        void drawNodeText(GuiGraphics context, Font renderer, Component text,
                          int x, int y, int color);
        void drawNodeText(GuiGraphics context, Font renderer, String text,
                          int x, int y, int color);
    }

    private final Host host;
    private final Map<Node, AnimatedValue> messageScopeAnimations = new WeakHashMap<>();
    private final Map<Node, AnimatedValue> booleanToggleAnimations = new WeakHashMap<>();
    private final Map<Node, Map<String, ParameterLayoutCacheEntry>> parameterLayoutCache = new WeakHashMap<>();

    NodeControlController(Host host) {
        this.host = host;
    }

    int getRuntimeScopeButtonWorldX(Node node) {
        return node.getX() + node.getWidth() - NODE_HEADER_BUTTON_SIZE - 2;
    }

    int getRuntimeScopeButtonWorldY(Node node) {
        return node.getY() + 2;
    }

    boolean isPointInsideRuntimeScopeButton(Node node, int screenX, int screenY) {
        if (node == null || !node.supportsRuntimeValueScope()) {
            return false;
        }
        return isPointInsideNodeHeaderButton(getRuntimeScopeButtonWorldX(node),
            getRuntimeScopeButtonWorldY(node), NODE_HEADER_BUTTON_SIZE, screenX, screenY);
    }

    void renderRuntimeScopeButton(GuiGraphics context, Node node, boolean dimmed, int mouseX, int mouseY) {
        NodeHeaderButtonVisual visual = renderNodeHeaderButtonFrame(context, getRuntimeScopeButtonWorldX(node),
            getRuntimeScopeButtonWorldY(node), NODE_HEADER_BUTTON_SIZE, dimmed, true,
            getSelectedNodeAccentColor(), mouseX, mouseY);
        int iconX = visual.left() + 3;
        int iconY = visual.top() + 3;
        if (node.getRuntimeValueScope() == RuntimeValueScope.GLOBAL) {
            PathmindIconRenderer.drawGlobalScope(context, iconX, iconY, 7, visual.iconColor());
        } else {
            PathmindIconRenderer.drawLocalScope(context, iconX, iconY, 7, visual.iconColor());
        }
    }

    NodeHeaderButtonVisual renderNodeHeaderButtonFrame(GuiGraphics context, int worldLeft, int worldTop,
                                                                int size, boolean dimmed, boolean enabled,
                                                                int hoverBorder, int mouseX, int mouseY) {
        // Node bodies are batched on modern versions, so header controls render in a later root layer.
        DrawContextBridge.startNewRootLayer(context);
        int left = worldLeft - host.cameraX();
        int top = worldTop - host.cameraY();
        boolean hovered = enabled && !dimmed
            && isPointInsideNodeHeaderButton(worldLeft, worldTop, size, mouseX, mouseY);
        int baseFill = dimmed ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_PRIMARY;
        int fill = hovered ? adjustColorBrightness(baseFill, 1.15f) : baseFill;
        int border = hovered ? hoverBorder : dimmed ? UITheme.BORDER_SUBTLE : UITheme.BORDER_DEFAULT;
        int iconColor = !enabled ? UITheme.NODE_LABEL_DIMMED
            : dimmed ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
        context.fill(left, top, left + size, top + size, fill);
        DrawContextBridge.drawBorderInLayer(context, left, top, size, size, border);
        return new NodeHeaderButtonVisual(left, top, iconColor);
    }

    void renderNodeHeaderTextButton(GuiGraphics context, Font textRenderer,
                                            int worldLeft, int worldTop, int size, String label,
                                            boolean dimmed, boolean enabled, int hoverBorder,
                                            int mouseX, int mouseY) {
        NodeHeaderButtonVisual visual = renderNodeHeaderButtonFrame(context, worldLeft, worldTop, size,
            dimmed, enabled, hoverBorder, mouseX, mouseY);
        int textX = visual.left() + (size - textRenderer.width(label)) / 2;
        int textY = visual.top() + (size - textRenderer.lineHeight) / 2 + 1;
        host.drawNodeText(context, textRenderer, Component.literal(label), textX, textY, visual.iconColor());
    }

    boolean isPointInsideNodeHeaderButton(int worldLeft, int worldTop, int size,
                                                   int screenX, int screenY) {
        return isPointInsideNodeHeaderButtonWorld(worldLeft, worldTop, size,
            host.screenToWorldX(screenX), host.screenToWorldY(screenY));
    }

    static boolean isPointInsideNodeHeaderButtonWorld(int worldLeft, int worldTop, int size,
                                                              int worldX, int worldY) {
        return worldX >= worldLeft && worldX < worldLeft + size
            && worldY >= worldTop && worldY < worldTop + size;
    }

    record NodeHeaderButtonVisual(int left, int top, int iconColor) {}

    void renderRuntimeScopeTooltip(GuiGraphics context, Font textRenderer, int mouseX, int mouseY) {
        Node node = host.nodeAt(mouseX, mouseY);
        if (!isPointInsideRuntimeScopeButton(node, mouseX, mouseY)) {
            return;
        }
        String key = switch (node.getRuntimeValueScope()) {
            case GLOBAL -> "pathmind.runtimeScope.global.short";
            case CHAIN -> "pathmind.runtimeScope.local.short";
        };
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }
        float scale = Math.max(0.01f, host.zoomScale());
        int scaledMouseX = Math.round(mouseX / scale);
        int scaledMouseY = Math.round(mouseY / scale);
        int scaledWidth = Math.round(client.getWindow().getGuiScaledWidth() / scale);
        int scaledHeight = Math.round(client.getWindow().getGuiScaledHeight() / scale);
        TooltipRenderer.render(context, textRenderer, host.translate(key), scaledMouseX, scaledMouseY,
            scaledWidth, scaledHeight);
    }

    boolean handleRuntimeScopeButtonClick(Node node, int mouseX, int mouseY) {
        if (!isPointInsideRuntimeScopeButton(node, mouseX, mouseY)) {
            return false;
        }
        host.pushUndoState();
        node.toggleRuntimeValueScope();
        host.notifyNodeParametersChanged(node);
        return true;
    }

    void renderStartNodeNumber(GuiGraphics context, Font textRenderer, Node node, int x, int y, boolean isOverSidebar) {
        int number = node.getStartNodeNumber();
        if (number <= 0) {
            return;
        }

        String label = String.valueOf(number);
        int textColor = isOverSidebar ? UITheme.TEXT_LABEL : UITheme.TEXT_PRIMARY;
        host.drawNodeText(context, textRenderer, label, x + 4, y + 4, textColor);
    }

    void renderStartLaunchIcon(GuiGraphics context, StartLaunchMode mode, int centerX, int centerY,
                                       int color, int nodeY, int nodeHeight) {
        StartLaunchMode effectiveMode = mode == null ? StartLaunchMode.MANUAL : mode;
        if (effectiveMode == StartLaunchMode.CLIENT_LAUNCH) {
            context.fill(centerX - 2, centerY - 8, centerX + 3, centerY + 5, color);
            context.fill(centerX - 6, centerY + 2, centerX + 7, centerY + 6, color);
            context.fill(centerX - 4, centerY - 5, centerX + 5, centerY - 1, color);
            return;
        }
        if (effectiveMode == StartLaunchMode.WORLD_JOIN) {
            context.fill(centerX - 7, centerY - 5, centerX + 8, centerY + 6, color);
            context.fill(centerX - 4, centerY - 8, centerX + 5, centerY + 9, color);
            context.fill(centerX - 9, centerY - 2, centerX + 10, centerY + 3, color);
            return;
        }
        if (effectiveMode == StartLaunchMode.MAIN_MENU_OPEN) {
            context.fill(centerX - 8, centerY - 7, centerX + 9, centerY - 3, color);
            context.fill(centerX - 8, centerY - 1, centerX + 9, centerY + 3, color);
            context.fill(centerX - 8, centerY + 5, centerX + 9, centerY + 9, color);
            return;
        }
        if (effectiveMode == StartLaunchMode.SCREEN_OPENED) {
            context.fill(centerX - 9, centerY - 7, centerX + 10, centerY + 8, color);
            context.fill(centerX - 5, centerY - 3, centerX + 6, centerY + 4, UITheme.NODE_START_BG);
            context.fill(centerX - 4, centerY + 10, centerX + 5, centerY + 13, color);
            return;
        }

        int triangleSize = 13;
        for (int i = 0; i < triangleSize; i++) {
            int lineWidth = Math.max(3, i + 2);
            int startX = centerX - 5;
            int lineY = centerY - triangleSize / 2 + i;
            if (lineY >= nodeY + 2 && lineY <= nodeY + nodeHeight - 3) {
                context.fill(startX, lineY, startX + lineWidth, lineY + 2, color);
            }
        }
    }

    void renderStartModeButton(GuiGraphics context, Node node, int x, int y, boolean isOverSidebar,
                                       int mouseX, int mouseY) {
        int buttonX = host.getStartModeButtonWorldX(node) - host.cameraX();
        int buttonY = host.getStartModeButtonWorldY(node) - host.cameraY();
        int color = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
        boolean hovered = host.isPointInsideStartModeButton(node, mouseX, mouseY);
        if (hovered && !isOverSidebar) {
            context.fill(buttonX - 1, buttonY - 1, buttonX + 10, buttonY + 7, 0x33000000);
        }
        for (int i = 0; i < 3; i++) {
            int dotX = buttonX + 2 + (i * 3);
            context.fill(dotX, buttonY + 2, dotX + 1, buttonY + 3, color);
        }
    }

    void renderBooleanToggleButton(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar, int mouseX, int mouseY) {
        if (node == null || node.getType() == NodeType.PARAM_BOOLEAN) {
            return;
        }
        int buttonLeft = node.getBooleanToggleLeft() - host.cameraX();
        int buttonTop = node.getBooleanToggleTop() - host.cameraY();
        int buttonWidth = node.getBooleanToggleWidth();
        int buttonHeight = node.getBooleanToggleHeight();

        boolean hovered = isPointInsideBooleanToggle(node, mouseX, mouseY);
        int borderColor;
        int fillColor;
        int textColor;
        if (isOverSidebar) {
            borderColor = UITheme.BORDER_HIGHLIGHT;
            fillColor = UITheme.BACKGROUND_SECONDARY;
            textColor = UITheme.TEXT_TERTIARY;
        } else {
            float progress = getNodeToggleProgress(booleanToggleAnimations, node, node.getBooleanToggleValue());
            int accentColor = getSelectedNodeAccentColor();
            int onBorder = adjustColorBrightness(accentColor, 1.12f);
            int onFill = adjustColorBrightness(accentColor, 0.22f);
            borderColor = AnimationHelper.lerpColor(UITheme.TOGGLE_OFF_BORDER, onBorder, progress);
            fillColor = AnimationHelper.lerpColor(UITheme.BOOL_TOGGLE_OFF_FILL, onFill, progress);
            if (hovered) {
                fillColor = adjustColorBrightness(fillColor, 1.12f);
                borderColor = adjustColorBrightness(borderColor, 1.05f);
            }
            textColor = UITheme.TEXT_PRIMARY;
        }

        context.fill(buttonLeft, buttonTop, buttonLeft + buttonWidth, buttonTop + buttonHeight, fillColor);
        DrawContextBridge.drawBorderInLayer(context, buttonLeft, buttonTop, buttonWidth, buttonHeight, borderColor);

        String label = node.getBooleanToggleValue() ? "TRUE" : "FALSE";
        int textWidth = textRenderer.width(label);
        int textX = buttonLeft + Math.max(2, (buttonWidth - textWidth) / 2);
        int textY = buttonTop + (buttonHeight - textRenderer.lineHeight) / 2 + 1;
        host.drawNodeText(context, textRenderer, Component.literal(label), textX, textY, textColor);
    }

    void renderSensorSlot(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar) {
        int slotX = node.getSensorSlotLeft() - host.cameraX();
        int slotY = node.getSensorSlotTop() - host.cameraY();
        int slotWidth = node.getSensorSlotWidth();
        int slotHeight = node.getSensorSlotHeight();
        boolean useLogicSlotTitle = usesLogicSensorSlotTitle(node);

        int backgroundColor = node.hasAttachedSensor() ? UITheme.BACKGROUND_TERTIARY : UITheme.BACKGROUND_PRIMARY;
        if (isOverSidebar) {
            backgroundColor = UITheme.NODE_INPUT_BG_DIMMED;
        }

        int borderColor = node.hasAttachedSensor() ? UITheme.BORDER_HIGHLIGHT : UITheme.BORDER_DEFAULT;
        if (host.sensorDropTarget() == node) {
            backgroundColor = UITheme.DROP_HIGHLIGHT_BLUE;
            borderColor = getSelectedNodeAccentColor();
        }

        context.fill(slotX, slotY, slotX + slotWidth, slotY + slotHeight, backgroundColor);
        DrawContextBridge.drawBorderInLayer(context, slotX, slotY, slotWidth, slotHeight, borderColor);

        if (useLogicSlotTitle) {
            String titleDisplay = host.trimTextToWidth(getLogicSensorSlotTitle(node), textRenderer, slotWidth - 4);
            int titleY = slotY - textRenderer.lineHeight - 2;
            int titleColor = host.sensorDropTarget() == node ? getSelectedNodeAccentColor() : (isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_SECONDARY);
            host.drawNodeText(context, textRenderer, Component.literal(titleDisplay), slotX + 2, titleY, titleColor);
        }

        if (!node.hasAttachedSensor()) {
            if (useLogicSlotTitle) {
                return;
            }
            String placeholder = host.translate("pathmind.node.slot.dragSensorHere");
            String display = host.trimTextToWidth(placeholder, textRenderer, slotWidth - 8);
            int textWidth = textRenderer.width(display);
            int textX = slotX + Math.max(4, (slotWidth - textWidth) / 2);
            int textY = slotY + (slotHeight - textRenderer.lineHeight) / 2;
            int textColor = host.sensorDropTarget() == node ? getSelectedNodeAccentColor() : UITheme.TEXT_TERTIARY;
            host.drawNodeText(context, textRenderer, Component.literal(display), textX, textY, textColor);
        }
    }

    void renderActionSlot(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar) {
        int slotX = node.getActionSlotLeft() - host.cameraX();
        int slotY = node.getActionSlotTop() - host.cameraY();
        int slotWidth = node.getActionSlotWidth();
        int slotHeight = node.getActionSlotHeight();
        boolean useLogicSlotTitle = usesLogicActionSlotTitle(node);

        int backgroundColor = node.hasAttachedActionNode() ? UITheme.BACKGROUND_TERTIARY : UITheme.BACKGROUND_PRIMARY;
        if (isOverSidebar) {
            backgroundColor = UITheme.NODE_INPUT_BG_DIMMED;
        }

        int borderColor = node.hasAttachedActionNode() ? UITheme.BORDER_HIGHLIGHT : UITheme.BORDER_DEFAULT;
        if (host.actionDropTarget() == node) {
            backgroundColor = UITheme.DROP_HIGHLIGHT_GREEN;
            borderColor = UITheme.DROP_ACCENT_GREEN;
        }

        context.fill(slotX, slotY, slotX + slotWidth, slotY + slotHeight, backgroundColor);
        DrawContextBridge.drawBorderInLayer(context, slotX, slotY, slotWidth, slotHeight, borderColor);

        if (useLogicSlotTitle) {
            String title = getLogicActionSlotTitle(node);
            int titleColor = host.actionDropTarget() == node ? UITheme.DROP_ACCENT_GREEN : (isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_SECONDARY);
            int titleY = slotY - textRenderer.lineHeight - 2;
            String titleDisplay = host.trimTextToWidth(title, textRenderer, slotWidth - 4);
            host.drawNodeText(context, textRenderer, Component.literal(titleDisplay), slotX + 2, titleY, titleColor);
        }

        if (!node.hasAttachedActionNode()) {
            if (useLogicSlotTitle) {
                return;
            }
            String placeholder = host.translate("pathmind.node.slot.dragNodeHere");
            String display = host.trimTextToWidth(placeholder, textRenderer, slotWidth - 8);
            int textWidth = textRenderer.width(display);
            int textX = slotX + Math.max(4, (slotWidth - textWidth) / 2);
            int textY = slotY + (slotHeight - textRenderer.lineHeight) / 2;
            int textColor = host.actionDropTarget() == node ? UITheme.DROP_ACCENT_GREEN : UITheme.TEXT_TERTIARY;
            host.drawNodeText(context, textRenderer, Component.literal(display), textX, textY, textColor);
        }
    }

    boolean usesLogicSensorSlotTitle(Node node) {
        if (node == null) {
            return false;
        }
        NodeType type = node.getType();
        return type == NodeType.CONTROL_IF
            || type == NodeType.CONTROL_IF_DO
            || type == NodeType.CONTROL_IF_ELSE
            || type == NodeType.CONTROL_REPEAT_UNTIL
            || type == NodeType.CONTROL_WAIT_UNTIL;
    }

    boolean usesLogicActionSlotTitle(Node node) {
        if (node == null) {
            return false;
        }
        NodeType type = node.getType();
        return type == NodeType.CONTROL_IF_DO
            || type == NodeType.CONTROL_REPEAT
            || type == NodeType.CONTROL_REPEAT_UNTIL
            || type == NodeType.CONTROL_FOREVER;
    }

    String getLogicSensorSlotTitle(Node node) {
        return host.translate("pathmind.node.slot.condition");
    }

    String getLogicActionSlotTitle(Node node) {
        if (node != null && node.getType() == NodeType.CONTROL_IF_DO) {
            return host.translate("pathmind.node.slot.action");
        }
        if (node != null && node.getType() == NodeType.CONTROL_REPEAT) {
            return host.translate("pathmind.node.slot.repeatBody");
        }
        return host.translate("pathmind.node.slot.loopBody");
    }

    boolean isPointInsideBooleanToggle(Node node, int mouseX, int mouseY) {
        if (node == null || !node.hasBooleanToggle() || node.getType() == NodeType.PARAM_BOOLEAN) {
            return false;
        }
        int worldMouseX = host.screenToWorldX(mouseX);
        int worldMouseY = host.screenToWorldY(mouseY);
        int buttonLeft = node.getBooleanToggleLeft();
        int buttonTop = node.getBooleanToggleTop();
        int buttonWidth = node.getBooleanToggleWidth();
        int buttonHeight = node.getBooleanToggleHeight();
        return worldMouseX >= buttonLeft && worldMouseX <= buttonLeft + buttonWidth &&
               worldMouseY >= buttonTop && worldMouseY <= buttonTop + buttonHeight;
    }

    boolean handleBooleanToggleClick(Node node, int mouseX, int mouseY) {
        if (!isPointInsideBooleanToggle(node, mouseX, mouseY)) {
            return false;
        }
        node.toggleBooleanToggleValue();
        getNodeToggleAnimation(booleanToggleAnimations, node, node.getBooleanToggleValue())
            .animateTo(node.getBooleanToggleValue() ? 1f : 0f, UITheme.TRANSITION_ANIM_MS, AnimationHelper::easeInOutCubic);
        host.notifyNodeParametersChanged(node);
        return true;
    }

    boolean handleMessageButtonClick(Node node, int mouseX, int mouseY) {
        if (node == null || !node.hasMessageInputFields()) {
            return false;
        }
        int size = node.getMessageButtonSize();
        int top = node.getMessageButtonTop();
        int addLeft = node.getMessageAddButtonLeft();
        int removeLeft = node.getMessageRemoveButtonLeft();
        boolean handled = false;

        boolean overAdd = isPointInsideNodeHeaderButton(addLeft, top, size, mouseX, mouseY);
        boolean overRemove = isPointInsideNodeHeaderButton(removeLeft, top, size, mouseX, mouseY);

        if (overAdd) {
            host.stopMessageEditing(true);
            node.addMessageLine("");
            node.recalculateDimensions();
            host.notifyNodeParametersChanged(node);
            host.startMessageEditing(node, node.getMessageFieldCount() - 1);
            handled = true;
        } else if (overRemove && node.getMessageFieldCount() > 1) {
            int targetIndex = (host.messageEditingNode() == node && host.messageEditingIndex() >= 0)
                ? host.messageEditingIndex()
                : node.getMessageFieldCount() - 1;
            host.stopMessageEditing(true);
            int removeIndex = Math.min(node.getMessageFieldCount() - 1, targetIndex);
            node.removeMessageLine(removeIndex);
            node.recalculateDimensions();
            host.notifyNodeParametersChanged(node);
            int nextIndex = Math.max(0, Math.min(removeIndex, node.getMessageFieldCount() - 1));
            host.startMessageEditing(node, nextIndex);
            handled = true;
        }

        return handled;
    }

    boolean handleBooleanOperatorButtonClick(Node node, int mouseX, int mouseY) {
        if (node == null || !node.isExpandableBooleanOperator()) {
            return false;
        }
        int size = node.getBooleanOperatorButtonSize();
        int top = node.getBooleanOperatorButtonTop();
        int addLeft = node.getBooleanOperatorAddButtonLeft();
        int removeLeft = node.getBooleanOperatorRemoveButtonLeft();

        boolean overAdd = isPointInsideNodeHeaderButton(addLeft, top, size, mouseX, mouseY);
        boolean overRemove = isPointInsideNodeHeaderButton(removeLeft, top, size, mouseX, mouseY);

        if (overAdd) {
            if (node.addBooleanOperatorSlot()) {
                host.notifyNodeParametersChanged(node);
            }
            return true;
        }
        if (overRemove && node.getParameterSlotCount() > 2) {
            if (node.removeBooleanOperatorSlot()) {
                host.notifyNodeParametersChanged(node);
            }
            return true;
        }
        return false;
    }

    boolean isPointInsideMessageScopeToggle(Node node, int mouseX, int mouseY) {
        if (node == null || !node.hasMessageScopeToggle()) {
            return false;
        }
        int worldMouseX = host.screenToWorldX(mouseX);
        int worldMouseY = host.screenToWorldY(mouseY);
        int left = node.getMessageScopeToggleLeft();
        int top = node.getMessageScopeToggleTop();
        int width = node.getMessageScopeToggleWidth();
        int height = node.getMessageScopeToggleHeight();
        return worldMouseX >= left && worldMouseX <= left + width
            && worldMouseY >= top && worldMouseY <= top + height;
    }

    boolean handleMessageScopeToggleClick(Node node, int mouseX, int mouseY) {
        if (node == null || !node.hasMessageScopeToggle()) {
            return false;
        }
        if (!isPointInsideMessageScopeToggle(node, mouseX, mouseY)) {
            return false;
        }
        host.stopMessageEditing(true);
        node.toggleMessageClientSide();
        AnimatedValue animation = getMessageScopeAnimation(node);
        animation.animateTo(node.isMessageClientSide() ? 1f : 0f, UITheme.TRANSITION_ANIM_MS, AnimationHelper::easeInOutCubic);
        host.notifyNodeParametersChanged(node);
        return true;
    }

    AnimatedValue getMessageScopeAnimation(Node node) {
        boolean clientSide = node != null && node.isMessageClientSide();
        return messageScopeAnimations.computeIfAbsent(node, key -> AnimatedValue.forToggle(clientSide));
    }

    AnimatedValue getNodeToggleAnimation(Map<Node, AnimatedValue> animations, Node node, boolean enabled) {
        return animations.computeIfAbsent(node, key -> AnimatedValue.forToggle(enabled));
    }

    float getNodeToggleProgress(Map<Node, AnimatedValue> animations, Node node, boolean enabled) {
        AnimatedValue animation = getNodeToggleAnimation(animations, node, enabled);
        animation.animateTo(enabled ? 1f : 0f, UITheme.TRANSITION_ANIM_MS, AnimationHelper::easeInOutCubic);
        animation.tick();
        return AnimationHelper.easeInOutCubic(animation.getValue());
    }

    void renderNodeSliderToggle(GuiGraphics context, int toggleLeft, int toggleTop, int toggleWidth, int toggleHeight,
                                        float progress, boolean hovered, boolean isOverSidebar) {
        int accentColor = isOverSidebar ? toGrayscale(getSelectedNodeAccentColor(), 0.8f) : getSelectedNodeAccentColor();
        UIStyleHelper.TogglePalette palette = UIStyleHelper.getTogglePalette(accentColor, progress, hovered, false);
        if (isOverSidebar) {
            palette = new UIStyleHelper.TogglePalette(UITheme.BACKGROUND_SECONDARY, UITheme.BORDER_HIGHLIGHT, UITheme.TOGGLE_KNOB);
        }
        UIStyleHelper.drawToggleSwitch(context, toggleLeft, toggleTop, toggleWidth, toggleHeight, progress, palette);
    }

    int adjustColorBrightness(int color, float factor) {
        int a = (color >>> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        r = Mth.clamp((int) (r * factor), 0, 255);
        g = Mth.clamp((int) (g * factor), 0, 255);
        b = Mth.clamp((int) (b * factor), 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    int getSelectedNodeAccentColor() {
        SettingsManager.Settings settings = SettingsManager.getCurrent();
        if (settings == null || settings.accentColor == null) {
            return UITheme.ACCENT_DEFAULT;
        }
        switch (settings.accentColor.toLowerCase(Locale.ROOT)) {
            case "sky":
                return UITheme.ACCENT_SKY;
            case "mint":
                return UITheme.ACCENT_MINT;
            case "amber":
                return UITheme.ACCENT_AMBER;
            default:
                return UITheme.ACCENT_DEFAULT;
        }
    }

    int toGrayscale(int color, float brightnessFactor) {
        int a = (color >>> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int gray = Mth.clamp((int) ((0.299f * r + 0.587f * g + 0.114f * b) * brightnessFactor), 0, 255);
        return (a << 24) | (gray << 16) | (gray << 8) | gray;
    }

    void renderParameterSlot(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar, int slotIndex) {
        int slotX = node.getParameterSlotLeft(slotIndex) - host.cameraX();
        int slotY = node.getParameterSlotTop(slotIndex) - host.cameraY();
        int slotWidth = node.getParameterSlotWidth(slotIndex);
        int slotHeight = node.getParameterSlotHeight(slotIndex);

        Node parameterNode = node.getAttachedParameter(slotIndex);
        boolean occupied = parameterNode != null;
        boolean isDropTarget = host.parameterDropTarget() == node && host.parameterDropSlotIndex() != null && host.parameterDropSlotIndex() == slotIndex;

        int backgroundColor = occupied ? UITheme.BACKGROUND_TERTIARY : UITheme.BACKGROUND_PRIMARY;
        if (isOverSidebar) {
            backgroundColor = occupied ? UITheme.NODE_INPUT_BG_DIMMED : UITheme.BACKGROUND_PRIMARY;
        }

        int borderColor = occupied ? UITheme.BORDER_HIGHLIGHT : UITheme.BORDER_DEFAULT;
        if (isDropTarget) {
            backgroundColor = UITheme.DROP_HIGHLIGHT_BLUE;
            borderColor = getSelectedNodeAccentColor();
        }

        context.fill(slotX, slotY, slotX + slotWidth, slotY + slotHeight, backgroundColor);
        DrawContextBridge.drawBorderInLayer(context, slotX, slotY, slotWidth, slotHeight, borderColor);

        String headerText = node.getParameterSlotLabel(slotIndex);
        int headerColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_SECONDARY;
        int headerY = slotY - textRenderer.lineHeight - 2;
        if (headerY > node.getY() - host.cameraY() + 14) {
            if (node.getType() == NodeType.WALK && slotIndex == 1) {
                int buttonWidth = textRenderer.width(headerText) + 8;
                int buttonHeight = textRenderer.lineHeight + 4;
                int buttonTop = headerY - 2;
                context.fill(slotX, buttonTop, slotX + buttonWidth, buttonTop + buttonHeight,
                    isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_TERTIARY);
                DrawContextBridge.drawBorderInLayer(context, slotX, buttonTop, buttonWidth, buttonHeight,
                    isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BORDER_DEFAULT);
            }
            host.drawNodeText(context, textRenderer, Component.literal(headerText), slotX + 2, headerY, headerColor);
        }

        if (!occupied && isDropTarget) {
            // Provide a minimal visual cue when dragging to an empty slot without adding text.
            DrawContextBridge.drawBorderInLayer(context, slotX + 2, slotY + 2, slotWidth - 4, slotHeight - 4, getSelectedNodeAccentColor());
        }

        if (node.usesMinimalNodePresentation()
            && isComparisonOperator(node)
            && !node.isExpandableBooleanOperator()
            && slotIndex == 0) {
            int leftSlotX = node.getParameterSlotLeft(0) - host.cameraX();
            int rightSlotX = node.getParameterSlotLeft(1) - host.cameraX();
            int leftSlotWidth = node.getParameterSlotWidth(0);
            int leftSlotHeight = node.getParameterSlotHeight(0);
            int rightSlotHeight = node.getParameterSlotHeight(1);
            int gapCenterX = leftSlotX + leftSlotWidth + (rightSlotX - (leftSlotX + leftSlotWidth)) / 2;
            String operatorText = getOperatorSymbol(node, true);
            int operatorWidth = textRenderer.width(operatorText);
            int operatorX = gapCenterX - operatorWidth / 2;
            int leftSlotTop = node.getParameterSlotTop(0) - host.cameraY();
            int rightSlotTop = node.getParameterSlotTop(1) - host.cameraY();
            int leftCenterY = leftSlotTop + leftSlotHeight / 2;
            int rightCenterY = rightSlotTop + rightSlotHeight / 2;
            int operatorCenterY = (leftCenterY + rightCenterY) / 2;
            int operatorY = operatorCenterY - textRenderer.lineHeight / 2;
            int operatorColor = isOverSidebar ? toGrayscale(UITheme.NODE_OPERATOR_SYMBOL, 0.85f) : UITheme.NODE_OPERATOR_SYMBOL;
            if (node.getType() == NodeType.OPERATOR_GREATER || node.getType() == NodeType.OPERATOR_LESS) {
                int buttonPaddingX = 3;
                int buttonPaddingY = 4;
                int maxSymbolWidth = textRenderer.width(">=");
                int buttonWidth = maxSymbolWidth + buttonPaddingX * 2;
                int buttonHeight = textRenderer.lineHeight + buttonPaddingY * 2;
                int buttonLeft = gapCenterX - buttonWidth / 2;
                int buttonTop = operatorY - buttonPaddingY;
                int buttonFill = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_TERTIARY;
                int buttonBorder = isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BORDER_DEFAULT;
                context.fill(buttonLeft, buttonTop, buttonLeft + buttonWidth, buttonTop + buttonHeight, buttonFill);
                DrawContextBridge.drawBorderInLayer(context, buttonLeft, buttonTop, buttonWidth, buttonHeight, buttonBorder);
                operatorX = buttonLeft + (buttonWidth - operatorWidth) / 2;
            }
            host.drawNodeText(
                context,
                textRenderer,
                Component.literal(operatorText),
                operatorX,
                operatorY,
                operatorColor
            );
        }
    }

    boolean isComparisonOperator(Node node) {
        if (node == null || node.getType() == null) {
            return false;
        }
        switch (node.getType()) {
            case OPERATOR_EQUALS:
            case OPERATOR_NOT:
            case OPERATOR_BOOLEAN_OR:
            case OPERATOR_BOOLEAN_AND:
            case OPERATOR_BOOLEAN_XOR:
            case OPERATOR_GREATER:
            case OPERATOR_LESS:
                return true;
            default:
                return false;
        }
    }

    boolean isOperatorInclusive(Node node) {
        if (node == null) {
            return false;
        }
        NodeParameter param = node.getParameter("Inclusive");
        if (param == null) {
            return false;
        }
        if (param.getType() == ParameterType.BOOLEAN) {
            return param.getBoolValue();
        }
        String value = param.getStringValue();
        return value != null && Boolean.parseBoolean(value.trim());
    }

    String getOperatorSymbol(Node node, boolean minimalStyle) {
        if (node == null || node.getType() == null) {
            return "";
        }
        switch (node.getType()) {
            case OPERATOR_EQUALS:
                return "=";
            case OPERATOR_NOT:
                return minimalStyle ? "=/" : "!=";
            case OPERATOR_BOOLEAN_OR:
                return "OR";
            case OPERATOR_BOOLEAN_AND:
                return "AND";
            case OPERATOR_BOOLEAN_XOR:
                return "XOR";
            case OPERATOR_GREATER:
                return isOperatorInclusive(node) ? ">=" : ">";
            case OPERATOR_LESS:
                return isOperatorInclusive(node) ? "<=" : "<";
            default:
                return "";
        }
    }

    boolean isOperatorToggleHit(Node node, Font textRenderer, int mouseX, int mouseY) {
        if (!isComparisonOperator(node)) {
            return false;
        }
        int worldMouseX = host.screenToWorldX(mouseX);
        int worldMouseY = host.screenToWorldY(mouseY);
        int leftSlotX = node.getParameterSlotLeft(0);
        int rightSlotX = node.getParameterSlotLeft(1);
        int leftSlotWidth = node.getParameterSlotWidth(0);
        int leftSlotHeight = node.getParameterSlotHeight(0);
        int rightSlotHeight = node.getParameterSlotHeight(1);
        int gapCenterX = leftSlotX + leftSlotWidth + (rightSlotX - (leftSlotX + leftSlotWidth)) / 2;

        int leftSlotTop = node.getParameterSlotTop(0);
        int rightSlotTop = node.getParameterSlotTop(1);
        int leftCenterY = leftSlotTop + leftSlotHeight / 2;
        int rightCenterY = rightSlotTop + rightSlotHeight / 2;
        int operatorCenterY = (leftCenterY + rightCenterY) / 2;

        String operatorText = getOperatorSymbol(node, node.usesMinimalNodePresentation());
        int textWidth = textRenderer.width(operatorText);
        int textHeight = textRenderer.lineHeight;
        int padding = 4;
        int hitLeft = gapCenterX - textWidth / 2 - padding;
        int hitRight = gapCenterX + textWidth / 2 + padding;
        int hitTop = operatorCenterY - textHeight / 2 - padding;
        int hitBottom = operatorCenterY + textHeight / 2 + padding;

        return worldMouseX >= hitLeft && worldMouseX <= hitRight && worldMouseY >= hitTop && worldMouseY <= hitBottom;
    }

    boolean handleOperatorToggleClick(Font textRenderer, int mouseX, int mouseY) {
        if (textRenderer == null) {
            return false;
        }
        Node node = host.nodeAt(mouseX, mouseY);
        if (node == null || node.getType() == null) {
            return false;
        }
        if (node.getType() != NodeType.OPERATOR_GREATER && node.getType() != NodeType.OPERATOR_LESS) {
            return false;
        }
        if (!isOperatorToggleHit(node, textRenderer, mouseX, mouseY)) {
            return false;
        }
        NodeParameter param = node.getParameter("Inclusive");
        if (param == null) {
            return false;
        }
        boolean next = !isOperatorInclusive(node);
        param.setStringValueFromUser(Boolean.toString(next));
        node.recalculateDimensions();
        return true;
    }

    void renderMessageButtons(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar, int mouseX, int mouseY) {
        int size = node.getMessageButtonSize();
        int worldTop = node.getMessageButtonTop();
        int worldAddLeft = node.getMessageAddButtonLeft();
        int worldRemoveLeft = node.getMessageRemoveButtonLeft();

        boolean canRemove = node.getMessageFieldCount() > 1;
        renderNodeHeaderTextButton(context, textRenderer, worldAddLeft, worldTop, size, "+",
            isOverSidebar, true, getSelectedNodeAccentColor(), mouseX, mouseY);
        renderNodeHeaderTextButton(context, textRenderer, worldRemoveLeft, worldTop, size, "-",
            isOverSidebar, canRemove, UITheme.BORDER_DANGER, mouseX, mouseY);
    }

    void renderBooleanOperatorButtons(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar, int mouseX, int mouseY) {
        int size = node.getBooleanOperatorButtonSize();
        int worldTop = node.getBooleanOperatorButtonTop();
        int worldAddLeft = node.getBooleanOperatorAddButtonLeft();
        int worldRemoveLeft = node.getBooleanOperatorRemoveButtonLeft();

        boolean canRemove = node.getParameterSlotCount() > 2;
        renderNodeHeaderTextButton(context, textRenderer, worldAddLeft, worldTop, size, "+",
            isOverSidebar, true, getSelectedNodeAccentColor(), mouseX, mouseY);
        renderNodeHeaderTextButton(context, textRenderer, worldRemoveLeft, worldTop, size, "-",
            isOverSidebar, canRemove, UITheme.BORDER_DANGER, mouseX, mouseY);
    }

    void renderMessageScopeToggle(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar, int mouseX, int mouseY) {
        if (!node.hasMessageScopeToggle()) {
            return;
        }
        int labelColor = isOverSidebar ? UITheme.NODE_LABEL_DIMMED : UITheme.NODE_LABEL_COLOR;
        int fieldBackground = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_SIDEBAR;
        int borderColor = isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BORDER_DEFAULT;
        int accentColor = getSelectedNodeAccentColor();
        int activeBackground = isOverSidebar ? adjustColorBrightness(accentColor, 0.72f) : adjustColorBrightness(accentColor, 0.84f);
        int activeBorderColor = accentColor;
        int activeTextColor = UITheme.TEXT_EDITING;
        int inactiveTextColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
        int optionGap = 4;

        int labelLeft = node.getMessageScopeToggleLeft() - host.cameraX();
        int labelTop = node.getMessageScopeLabelTop() - host.cameraY();
        int labelY = labelTop + Math.max(0, (node.getMessageScopeLabelHeight() - textRenderer.lineHeight) / 2);
        host.drawNodeText(context, textRenderer, Component.translatable("pathmind.field.visibility"), labelLeft + 2, labelY, labelColor);

        int left = node.getMessageScopeToggleLeft() - host.cameraX();
        int top = node.getMessageScopeToggleTop() - host.cameraY();
        int width = node.getMessageScopeToggleWidth();
        int height = node.getMessageScopeToggleHeight();
        int segmentWidth = Math.max(1, (width - optionGap) / 2);
        boolean hovered = !isOverSidebar && isPointInsideMessageScopeToggle(node, mouseX, mouseY);
        int worldMouseX = host.screenToWorldX(mouseX);
        int worldMouseY = host.screenToWorldY(mouseY);
        int worldLeft = node.getMessageScopeToggleLeft();
        int worldTop = node.getMessageScopeToggleTop();
        AnimatedValue animation = getMessageScopeAnimation(node);
        float target = node.isMessageClientSide() ? 1f : 0f;
        if (Math.abs(animation.getTargetValue() - target) > 0.001f) {
            animation.setValue(target);
        }
        animation.tick();
        float progress = animation.getValue();

        int globalLeft = left;
        int clientLeft = left + segmentWidth + optionGap;
        int globalWorldLeft = worldLeft;
        int clientWorldLeft = worldLeft + segmentWidth + optionGap;
        boolean globalHovered = hovered
            && worldMouseX >= globalWorldLeft && worldMouseX <= globalWorldLeft + segmentWidth
            && worldMouseY >= worldTop && worldMouseY <= worldTop + height;
        boolean clientHovered = hovered
            && worldMouseX >= clientWorldLeft && worldMouseX <= clientWorldLeft + segmentWidth
            && worldMouseY >= worldTop && worldMouseY <= worldTop + height;

        int globalFill = AnimationHelper.lerpColor(activeBackground, fieldBackground, progress);
        int globalBorder = AnimationHelper.lerpColor(activeBorderColor, borderColor, progress);
        int clientFill = AnimationHelper.lerpColor(fieldBackground, activeBackground, progress);
        int clientBorder = AnimationHelper.lerpColor(borderColor, activeBorderColor, progress);
        if (globalHovered) {
            globalFill = adjustColorBrightness(globalFill, 1.08f);
            globalBorder = activeBorderColor;
        }
        if (clientHovered) {
            clientFill = adjustColorBrightness(clientFill, 1.08f);
            clientBorder = activeBorderColor;
        }

        context.fill(globalLeft, top, globalLeft + segmentWidth, top + height, globalFill);
        DrawContextBridge.drawBorderInLayer(context, globalLeft, top, segmentWidth, height, globalBorder);
        context.fill(clientLeft, top, clientLeft + segmentWidth, top + height, clientFill);
        DrawContextBridge.drawBorderInLayer(context, clientLeft, top, segmentWidth, height, clientBorder);

        String globalLabel = host.translate("pathmind.option.messageScope.global");
        String clientLabel = host.translate("pathmind.option.messageScope.clientSide");
        int globalX = globalLeft + Math.max(0, (segmentWidth - textRenderer.width(globalLabel)) / 2);
        int clientX = clientLeft + Math.max(0, (segmentWidth - textRenderer.width(clientLabel)) / 2);
        int textY = top + (height - textRenderer.lineHeight) / 2 + 1;
        int globalTextColor = AnimationHelper.lerpColor(activeTextColor, inactiveTextColor, progress);
        int clientTextColor = AnimationHelper.lerpColor(inactiveTextColor, activeTextColor, progress);

        host.drawNodeText(context, textRenderer, Component.literal(globalLabel), globalX, textY, globalTextColor);
        host.drawNodeText(context, textRenderer, Component.literal(clientLabel), clientX, textY, clientTextColor);
    }

    void renderBookTextInput(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar, int mouseX, int mouseY) {
        // Render "Edit Text" button
        int buttonLeft = node.getBookTextButtonLeft() - host.cameraX();
        int buttonTop = node.getBookTextButtonTop() - host.cameraY();
        int buttonWidth = node.getBookTextButtonWidth();
        int buttonHeight = node.getBookTextButtonHeight();

        int worldMouseX = host.screenToWorldX(mouseX);
        int worldMouseY = host.screenToWorldY(mouseY);
        boolean buttonHovered = !isOverSidebar &&
            worldMouseX >= node.getBookTextButtonLeft() && worldMouseX <= node.getBookTextButtonLeft() + buttonWidth &&
            worldMouseY >= node.getBookTextButtonTop() && worldMouseY <= node.getBookTextButtonTop() + buttonHeight;

        int buttonFill = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BUTTON_DEFAULT_BG;
        int buttonBorder = isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BUTTON_DEFAULT_BORDER;
        if (buttonHovered) {
            buttonFill = UITheme.BUTTON_DEFAULT_HOVER;
            buttonBorder = getSelectedNodeAccentColor();
        }

        context.fill(buttonLeft, buttonTop, buttonLeft + buttonWidth, buttonTop + buttonHeight, buttonFill);
        DrawContextBridge.drawBorderInLayer(context, buttonLeft, buttonTop, buttonWidth, buttonHeight, buttonBorder);

        String buttonLabel = host.translate("pathmind.button.editText");
        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
        int textX = buttonLeft + (buttonWidth - textRenderer.width(buttonLabel)) / 2;
        int textY = buttonTop + (buttonHeight - textRenderer.lineHeight) / 2;
        host.drawNodeText(context, textRenderer, Component.literal(buttonLabel), textX, textY, textColor);

        if (node.hasBookTextPageInput()) {
            int labelColor = isOverSidebar ? UITheme.NODE_LABEL_DIMMED : UITheme.TEXT_SECONDARY;
            int labelTop = node.getBookTextPageLabelTop() - host.cameraY();
            host.drawNodeText(context, textRenderer, Component.translatable("pathmind.field.pageNumber"), buttonLeft, labelTop, labelColor);

            int fieldTop = node.getBookTextPageFieldTop() - host.cameraY();
            int fieldHeight = node.getBookTextPageFieldHeight();

            NodeParameter pageParam = node.getParameter("Page");
            String pageValue = pageParam != null ? pageParam.getDisplayValue() : "1";
            if (pageValue == null) {
                pageValue = "";
            }
            int pageTextColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
            host.drawNodeText(context, textRenderer, Component.literal(pageValue), buttonLeft + 4, fieldTop + (fieldHeight - textRenderer.lineHeight) / 2, pageTextColor);
        }
    }

    void renderPopupEditButton(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar, int mouseX, int mouseY) {
        if (node == null || !node.hasPopupEditButton()) {
            return;
        }
        int buttonLeft = node.getPopupEditButtonLeft() - host.cameraX();
        int buttonTop = node.getPopupEditButtonTop() - host.cameraY();
        int buttonWidth = node.getPopupEditButtonWidth();
        int buttonHeight = node.getPopupEditButtonHeight();

        int worldMouseX = host.screenToWorldX(mouseX);
        int worldMouseY = host.screenToWorldY(mouseY);
        boolean buttonHovered = !isOverSidebar &&
            worldMouseX >= node.getPopupEditButtonLeft() && worldMouseX <= node.getPopupEditButtonLeft() + buttonWidth &&
            worldMouseY >= node.getPopupEditButtonTop() && worldMouseY <= node.getPopupEditButtonTop() + buttonHeight;

        int buttonFill = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BUTTON_DEFAULT_BG;
        int buttonBorder = isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BUTTON_DEFAULT_BORDER;
        if (buttonHovered) {
            buttonFill = UITheme.BUTTON_DEFAULT_HOVER;
            buttonBorder = getSelectedNodeAccentColor();
        }

        context.fill(buttonLeft, buttonTop, buttonLeft + buttonWidth, buttonTop + buttonHeight, buttonFill);
        DrawContextBridge.drawBorderInLayer(context, buttonLeft, buttonTop, buttonWidth, buttonHeight, buttonBorder);

        String buttonLabel = Component.translatable("pathmind.button.edit").getString();
        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
        int textX = buttonLeft + (buttonWidth - textRenderer.width(buttonLabel)) / 2;
        int textY = buttonTop + (buttonHeight - textRenderer.lineHeight) / 2;
        host.drawNodeText(context, textRenderer, Component.literal(buttonLabel), textX, textY, textColor);
    }

    boolean isPointInsideBookTextButton(Node node, int mouseX, int mouseY) {
        if (node == null || !node.hasBookTextInput()) {
            return false;
        }
        int worldX = host.screenToWorldX(mouseX);
        int worldY = host.screenToWorldY(mouseY);
        int buttonLeft = node.getBookTextButtonLeft();
        int buttonTop = node.getBookTextButtonTop();
        int buttonWidth = node.getBookTextButtonWidth();
        int buttonHeight = node.getBookTextButtonHeight();

        return worldX >= buttonLeft && worldX <= buttonLeft + buttonWidth &&
               worldY >= buttonTop && worldY <= buttonTop + buttonHeight;
    }

    boolean isPointInsidePopupEditButton(Node node, int mouseX, int mouseY) {
        if (node == null || !node.hasPopupEditButton()) {
            return false;
        }
        int worldX = host.screenToWorldX(mouseX);
        int worldY = host.screenToWorldY(mouseY);
        int buttonLeft = node.getPopupEditButtonLeft();
        int buttonTop = node.getPopupEditButtonTop();
        int buttonWidth = node.getPopupEditButtonWidth();
        int buttonHeight = node.getPopupEditButtonHeight();

        return worldX >= buttonLeft && worldX <= buttonLeft + buttonWidth &&
               worldY >= buttonTop && worldY <= buttonTop + buttonHeight;
    }

    void clearParameterLayoutCache() {
        parameterLayoutCache.clear();
    }

    int getParameterFieldLeft(Node node) {
        return node.getX() + 5;
    }

    boolean isCombinedDirectionNode(Node node) {
        return node != null && node.getType() == NodeType.PARAM_DIRECTION;
    }

    boolean isCombinedBooleanNode(Node node) {
        return node != null && node.getType() == NodeType.PARAM_BOOLEAN;
    }

    int getDirectionModeTabTop(Node node) {
        int top = node.getY() + 18;
        if (node != null && node.supportsModeSelection()) {
            top += PARAMETER_INPUT_HEIGHT + PARAMETER_INPUT_GAP;
        }
        return top;
    }

    int getBooleanModeTabTop(Node node) {
        int top = node.getY() + 18;
        if (node != null && node.supportsModeSelection()) {
            top += PARAMETER_INPUT_HEIGHT + PARAMETER_INPUT_GAP;
        }
        return top;
    }

    int getInlineParameterFieldsTop(Node node) {
        int top = node.getY() + 18;
        if (node != null && node.supportsModeSelection()) {
            top += PARAMETER_INPUT_HEIGHT + PARAMETER_INPUT_GAP;
        }
        if (isCombinedDirectionNode(node)) {
            top += DIRECTION_MODE_TAB_HEIGHT + PARAMETER_INPUT_GAP;
        }
        if (isCombinedBooleanNode(node)) {
            top += DIRECTION_MODE_TAB_HEIGHT + PARAMETER_INPUT_GAP;
        }
        return top;
    }

    int getParameterFieldWidth(Node node) {
        return Math.max(20, node.getWidth() - 10);
    }

    int getParameterFieldHeight() {
        return PARAMETER_INPUT_HEIGHT;
    }

    void renderDirectionModeTabs(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                                         int fieldTop, int mouseX, int mouseY) {
        if (!isCombinedDirectionNode(node)) {
            return;
        }
        int fieldLeft = getParameterFieldLeft(node) - host.cameraX();
        int fieldWidth = getParameterFieldWidth(node);
        int fieldHeight = DIRECTION_MODE_TAB_HEIGHT;
        int splitX = fieldLeft + fieldWidth / 2;
        int accentColor = getSelectedNodeAccentColor();
        int inactiveBackground = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_SIDEBAR;
        int inactiveBorder = isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BORDER_DEFAULT;
        int activeBackground = isOverSidebar ? adjustColorBrightness(accentColor, 0.72f) : adjustColorBrightness(accentColor, 0.84f);
        int activeText = UITheme.TEXT_EDITING;
        int inactiveText = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
        boolean exactMode = node.isDirectionModeExact();

        int worldMouseX = host.screenToWorldX(mouseX);
        int worldMouseY = host.screenToWorldY(mouseY);
        int worldLeft = getParameterFieldLeft(node);
        int worldTop = fieldTop + host.cameraY();
        int halfWidth = Math.max(1, fieldWidth / 2);
        boolean hoverExact = !isOverSidebar
            && worldMouseX >= worldLeft
            && worldMouseX < worldLeft + halfWidth
            && worldMouseY >= worldTop
            && worldMouseY <= worldTop + fieldHeight;
        boolean hoverCardinal = !isOverSidebar
            && worldMouseX >= worldLeft + halfWidth
            && worldMouseX <= worldLeft + fieldWidth
            && worldMouseY >= worldTop
            && worldMouseY <= worldTop + fieldHeight;

        int exactLeft = fieldLeft;
        int exactWidth = Math.max(1, halfWidth);
        int cardinalLeft = splitX;
        int cardinalWidth = Math.max(1, fieldLeft + fieldWidth - splitX);

        int exactBackground = exactMode ? activeBackground : inactiveBackground;
        int cardinalBackground = exactMode ? inactiveBackground : activeBackground;
        int exactBorder = exactMode ? accentColor : inactiveBorder;
        int cardinalBorder = exactMode ? inactiveBorder : accentColor;
        if (hoverExact && !exactMode) {
            exactBackground = UITheme.BACKGROUND_TERTIARY;
        }
        if (hoverCardinal && exactMode) {
            cardinalBackground = UITheme.BACKGROUND_TERTIARY;
        }

        context.fill(exactLeft, fieldTop, exactLeft + exactWidth, fieldTop + fieldHeight, exactBackground);
        DrawContextBridge.drawBorderInLayer(context, exactLeft, fieldTop, exactWidth, fieldHeight, exactBorder);
        context.fill(cardinalLeft, fieldTop, cardinalLeft + cardinalWidth, fieldTop + fieldHeight, cardinalBackground);
        DrawContextBridge.drawBorderInLayer(context, cardinalLeft, fieldTop, cardinalWidth, fieldHeight, cardinalBorder);

        String exactLabel = host.translate("pathmind.option.directionMode.exact");
        String cardinalLabel = host.translate("pathmind.option.directionMode.cardinal");
        int exactLabelX = exactLeft + Math.max(0, (exactWidth - textRenderer.width(exactLabel)) / 2);
        int cardinalLabelX = cardinalLeft + Math.max(0, (cardinalWidth - textRenderer.width(cardinalLabel)) / 2);
        int labelY = fieldTop + (fieldHeight - textRenderer.lineHeight) / 2 + 1;
        host.drawNodeText(context, textRenderer, Component.literal(exactLabel), exactLabelX, labelY, exactMode ? activeText : inactiveText);
        host.drawNodeText(context, textRenderer, Component.literal(cardinalLabel), cardinalLabelX, labelY, exactMode ? inactiveText : activeText);
    }

    void renderBooleanModeTabs(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                                       int fieldTop, int mouseX, int mouseY) {
        if (!isCombinedBooleanNode(node)) {
            return;
        }
        int fieldLeft = getParameterFieldLeft(node) - host.cameraX();
        int fieldWidth = getParameterFieldWidth(node);
        int fieldHeight = DIRECTION_MODE_TAB_HEIGHT;
        int splitX = fieldLeft + fieldWidth / 2;
        int accentColor = getSelectedNodeAccentColor();
        int inactiveBackground = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_SIDEBAR;
        int inactiveBorder = isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BORDER_DEFAULT;
        int activeBackground = isOverSidebar ? adjustColorBrightness(accentColor, 0.72f) : adjustColorBrightness(accentColor, 0.84f);
        int activeText = UITheme.TEXT_EDITING;
        int inactiveText = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
        boolean literalMode = node.isBooleanModeLiteral();

        int worldMouseX = host.screenToWorldX(mouseX);
        int worldMouseY = host.screenToWorldY(mouseY);
        int worldLeft = getParameterFieldLeft(node);
        int worldTop = fieldTop + host.cameraY();
        int halfWidth = Math.max(1, fieldWidth / 2);
        boolean hoverLiteral = !isOverSidebar
            && worldMouseX >= worldLeft
            && worldMouseX < worldLeft + halfWidth
            && worldMouseY >= worldTop
            && worldMouseY <= worldTop + fieldHeight;
        boolean hoverVariable = !isOverSidebar
            && worldMouseX >= worldLeft + halfWidth
            && worldMouseX <= worldLeft + fieldWidth
            && worldMouseY >= worldTop
            && worldMouseY <= worldTop + fieldHeight;

        int literalLeft = fieldLeft;
        int literalWidth = Math.max(1, halfWidth);
        int variableLeft = splitX;
        int variableWidth = Math.max(1, fieldLeft + fieldWidth - splitX);

        int literalBackground = literalMode ? activeBackground : inactiveBackground;
        int variableBackground = literalMode ? inactiveBackground : activeBackground;
        int literalBorder = literalMode ? accentColor : inactiveBorder;
        int variableBorder = literalMode ? inactiveBorder : accentColor;
        if (hoverLiteral && !literalMode) {
            literalBackground = UITheme.BACKGROUND_TERTIARY;
        }
        if (hoverVariable && literalMode) {
            variableBackground = UITheme.BACKGROUND_TERTIARY;
        }

        context.fill(literalLeft, fieldTop, literalLeft + literalWidth, fieldTop + fieldHeight, literalBackground);
        DrawContextBridge.drawBorderInLayer(context, literalLeft, fieldTop, literalWidth, fieldHeight, literalBorder);
        context.fill(variableLeft, fieldTop, variableLeft + variableWidth, fieldTop + fieldHeight, variableBackground);
        DrawContextBridge.drawBorderInLayer(context, variableLeft, fieldTop, variableWidth, fieldHeight, variableBorder);

        String literalLabel = host.translate("pathmind.option.booleanMode.literal");
        String variableLabel = host.translate("pathmind.option.booleanMode.variable");
        int literalLabelX = literalLeft + Math.max(0, (literalWidth - textRenderer.width(literalLabel)) / 2);
        int variableLabelX = variableLeft + Math.max(0, (variableWidth - textRenderer.width(variableLabel)) / 2);
        int labelY = fieldTop + (fieldHeight - textRenderer.lineHeight) / 2 + 1;
        host.drawNodeText(context, textRenderer, Component.literal(literalLabel), literalLabelX, labelY, literalMode ? activeText : inactiveText);
        host.drawNodeText(context, textRenderer, Component.literal(variableLabel), variableLabelX, labelY, literalMode ? inactiveText : activeText);
    }

    String getParameterLabelText(Node node, NodeParameter parameter, Font textRenderer, int maxWidth) {
        ParameterLayoutCacheEntry layout = getParameterLayoutCacheEntry(node, parameter, textRenderer);
        if (layout == null || layout.displayName().isEmpty()) {
            return "";
        }
        return layout.labelText();
    }

    boolean isStandaloneParameterNode(Node node) {
        if (node == null || node.getType() == null) {
            return false;
        }
        return node.getType().name().startsWith("PARAM_") && node.getParameters().size() <= 1;
    }

    boolean shouldLeftAlignParameterValue(Node node) {
        return false;
    }

    int getParameterValueStartX(Node node, NodeParameter parameter, Font textRenderer) {
        int fieldLeft = getParameterFieldLeft(node);
        if (shouldLeftAlignParameterValue(node)) {
            return fieldLeft + 4;
        }
        ParameterLayoutCacheEntry layout = getParameterLayoutCacheEntry(node, parameter, textRenderer);
        if (layout == null) {
            return fieldLeft + 8;
        }
        return layout.valueStartX();
    }

    ParameterLayoutCacheEntry getParameterLayoutCacheEntry(Node node, NodeParameter parameter, Font textRenderer) {
        if (node == null || parameter == null) {
            return null;
        }
        String parameterKey = parameter.getName();
        Map<String, ParameterLayoutCacheEntry> nodeCache = parameterLayoutCache.computeIfAbsent(node, ignored -> new HashMap<>());
        String displayName = node.getParameterDisplayName(parameter);
        if (displayName == null) {
            displayName = "";
        }
        int fieldLeft = getParameterFieldLeft(node);
        int fieldWidth = getParameterFieldWidth(node);
        int maxLabelWidth = Math.max(0, fieldWidth - 40);
        boolean leftAligned = shouldLeftAlignParameterValue(node);
        ParameterLayoutCacheEntry cached = nodeCache.get(parameterKey);
        if (cached != null
            && cached.fieldLeft() == fieldLeft
            && cached.fieldWidth() == fieldWidth
            && cached.maxLabelWidth() == maxLabelWidth
            && cached.leftAligned() == leftAligned
            && cached.displayName().equals(displayName)) {
            return cached;
        }

        String label = displayName.isEmpty() ? "" : displayName + ":";
        String labelText = label;
        int labelWidth = 0;
        if (textRenderer != null && !label.isEmpty()) {
            labelText = maxLabelWidth > 0 ? host.trimTextToWidth(label, textRenderer, maxLabelWidth) : label;
            labelWidth = textRenderer.width(labelText);
        }
        int valueStartX = leftAligned ? fieldLeft + 4 : fieldLeft + 4 + labelWidth + 4;
        ParameterLayoutCacheEntry entry = new ParameterLayoutCacheEntry(displayName, fieldLeft, fieldWidth, maxLabelWidth, leftAligned, labelText, valueStartX);
        nodeCache.put(parameterKey, entry);
        return entry;
    }

    String formatVillagerTradeValue(String rawValue) {
        if (rawValue == null || rawValue.isEmpty()) {
            return "";
        }
        if (!rawValue.contains("|") || !rawValue.contains("@")) {
            return rawValue;
        }
        String[] parts = rawValue.split("\\|");
        if (parts.length < 1) {
            return rawValue;
        }
        TradeKeyPart first = parseTradeKeyPart(parts[0]);
        TradeKeyPart second = parts.length > 1 ? parseTradeKeyPart(parts[1]) : null;
        TradeKeyPart sell = parts.length > 2 ? parseTradeKeyPart(parts[2]) : null;
        if (sell == null || sell.name == null || sell.name.isEmpty()) {
            return rawValue;
        }
        StringBuilder builder = new StringBuilder();
        if (first != null && first.isValid()) {
            builder.append(first.format());
        }
        if (second != null && second.isValid()) {
            if (builder.length() > 0) {
                builder.append(" + ");
            }
            builder.append(second.format());
        }
        if (builder.length() > 0) {
            builder.append(" -> ");
        }
        builder.append(sell.format());
        return builder.toString();
    }

    TradeKeyPart parseTradeKeyPart(String part) {
        if (part == null || part.isEmpty() || "none@0".equals(part)) {
            return TradeKeyPart.empty();
        }
        int atIndex = part.indexOf('@');
        if (atIndex <= 0) {
            return TradeKeyPart.empty();
        }
        String itemId = part.substring(0, atIndex);
        String countRaw = part.substring(atIndex + 1);
        int count = 1;
        try {
            count = Math.max(1, Integer.parseInt(countRaw));
        } catch (NumberFormatException ignored) {
            count = 1;
        }
        Identifier identifier = Identifier.tryParse(itemId);
        if (identifier == null || !BuiltInRegistries.ITEM.containsKey(identifier)) {
            return TradeKeyPart.empty();
        }
        Item item = BuiltInRegistries.ITEM.getOptional(identifier).orElse(null);
        return new TradeKeyPart(new ItemStack(item).getHoverName().getString(), count);
    }

    static final class TradeKeyPart {
        private static final TradeKeyPart EMPTY = new TradeKeyPart("", 0);
        private final String name;
        private final int count;

        private TradeKeyPart(String name, int count) {
            this.name = name;
            this.count = count;
        }

        private static TradeKeyPart empty() {
            return EMPTY;
        }

        private boolean isValid() {
            return name != null && !name.isEmpty() && count > 0;
        }

        private String format() {
            if (count > 1) {
                return count + "x " + name;
            }
            return name;
        }
    }

    record ParameterLayoutCacheEntry(
        String displayName,
        int fieldLeft,
        int fieldWidth,
        int maxLabelWidth,
        boolean leftAligned,
        String labelText,
        int valueStartX
    ) {
    }
}
