package com.pathmind.screen;

import com.pathmind.ai.AiPresetService;
import com.pathmind.ai.AiProviderRegistry;
import com.pathmind.ai.AiProviderType;
import com.pathmind.ai.AiChatHistoryStore;
import com.pathmind.ai.AiChatHistoryStore.Role;
import com.pathmind.ai.AiChatLayout;
import com.pathmind.ai.AiChatScrollState;
import com.pathmind.ai.AiRequestControl;
import com.pathmind.ai.AiRequestProgress;
import com.pathmind.ui.control.PathmindWorkspaceChrome;
import com.pathmind.ui.control.PathmindDropdownRenderer;
import com.pathmind.ui.control.PathmindIconRenderer;
import com.pathmind.ui.control.PathmindPopupRenderer;
import com.pathmind.ui.animation.AnimatedValue;
import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.animation.HoverAnimator;
import com.pathmind.ui.tooltip.TooltipRenderer;
import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DropdownLayoutHelper;
import com.pathmind.util.ScrollbarHelper;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** A resizable, non-modal AI workspace that stays alongside the graph editor. */
final class PathmindAiPopupController {
    interface Host {
        void requestAiProposal(AiProviderType provider, String prompt, String conversation, AiRequestControl control, Consumer<AiPresetService.Proposal> success, Consumer<String> failure);
        String applyAiProposal(AiPresetService.Proposal proposal);
        String activePresetName();
        com.pathmind.data.NodeGraphData activeGraph();
        void showAiError(String message);
    }
    private enum View { CHAT, SETTINGS }
    private enum Field { NONE, KEY, ENDPOINT, MODEL, PROMPT }
    private enum ResizeCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
    // The settings form stacks four labelled rows for a gateway provider, so the popup cannot be
    // shorter than that form; DEFAULT_WIDTH keeps every provider tab on one row at the default size.
    private static final int MIN_WIDTH = 185, MIN_HEIGHT = 265, DEFAULT_WIDTH = 215, DEFAULT_HEIGHT = 330, HEADER = 22, COMPOSER_LINES = 3;
    private static final int ACTION_BUTTON_SIZE = 16, ACTION_BUTTON_INSET = 4;
    private static final int SETTINGS_ROW = 34, SETTINGS_INPUT_OFFSET = 12, SETTINGS_INPUT_HEIGHT = 20;
    private static final int KEY_LIMIT = 512, ENDPOINT_LIMIT = 256, MODEL_LIMIT = 128;
    private final Host host;
    private final AnimatedValue modelDropdownAnimation = AnimatedValue.forHover();
    private final AnimatedValue routingDropdownAnimation = AnimatedValue.forHover();
    private boolean visible, dragging, resizing, requesting, replaceOnType, modelDropdownOpen, routingDropdownOpen;
    private int x = -1, y = -1, width = DEFAULT_WIDTH, height = DEFAULT_HEIGHT, dragOffsetX, dragOffsetY, resizeStartX, resizeStartY, resizeStartWidth, resizeStartHeight;
    private ResizeCorner resizeCorner;
    private View view = View.CHAT;
    private Field activeField = Field.NONE;
    private AiProviderType provider = AiProviderType.OPENAI;
    private String apiKey = "", model = provider.defaultModel(), endpoint = provider.defaultEndpoint(), prompt = "", status = "";
    private String routingSort = "";
    private boolean allowFallbacks = true;
    private AiPresetService.Proposal pendingProposal;
    private Font currentFont;
    private int promptCursor, promptScrollLine, promptAnchor, promptDragAnchor;
    private boolean promptSelecting;
    private long requestStartedAt;
    private final AiChatHistoryStore conversationHistory;
    private int conversationGeneration;
    private boolean resetArmed;
    private long pendingHistoryGeneration;
    private AiProviderType cachedHistoryProvider;
    private long cachedHistoryRevision = -1;
    private java.util.List<ChatLine> cachedHistory = java.util.List.of();
    private Font cachedThinkingFont;
    private AiProviderType cachedThinkingProvider;
    private int cachedThinkingWidth;
    private long cachedThinkingRevision = -1;
    private boolean cachedThinkingRequesting;
    private String cachedThinkingStatus = "";
    private java.util.List<AiChatLayout.Row> cachedThinkingLines = java.util.List.of();
    private final AiChatScrollState chatScroll = new AiChatScrollState();
    private final java.util.Set<Integer> expandedDetails = new java.util.HashSet<>();
    private boolean scrollbarDragging;
    private int scrollbarGrab;
    private AiRequestControl requestControl;
    private String progressLabel = "";
    private final java.util.Map<String, Object> buttonHoverKeys = new java.util.HashMap<>();
    private final java.util.Set<String> renderedButtonKeys = new java.util.HashSet<>();
    private int renderMouseX, renderMouseY;
    private String hoveredTooltip;

    PathmindAiPopupController(Host host) {
        this.host = host;
        conversationHistory = AiChatHistoryStore.open(Minecraft.getInstance().gameDirectory.toPath().resolve("pathmind"));
    }
    boolean isVisible() { return visible; }
    void open(int screenWidth, int screenHeight) {
        if (x < 0 || y < 0) { x = Math.max(10, screenWidth - width - 55); y = 28; }
        clampToScreen(screenWidth, screenHeight);
        view = AiProviderRegistry.hasConfiguredProvider() ? View.CHAT : View.SETTINGS;
        loadProviderConfiguration(); visible = true;
        if (!conversationHistory.warning().isBlank()) status = conversationHistory.warning();
    }
    void close() { visible = false; dragging = false; resizing = false; scrollbarDragging = false; closeDropdowns(); activeField = Field.NONE; resetArmed = false; }
    private void closeDropdowns() { modelDropdownOpen = false; routingDropdownOpen = false; }

    /**
     * One provider tab with its resolved bounds. Render and hit-testing both read this list, so a
     * wrapped row can never drift between the two.
     */
    private record TabSlot(AiProviderType provider, int x, int y, int width) { }

    private java.util.List<TabSlot> chatTabSlots(Font f) { return tabSlots(f, x + 9, y + 3, 14, x + width - 40); }
    private java.util.List<TabSlot> settingsTabSlots(Font f) { return tabSlots(f, x + 12, y + HEADER + 8, 12, x + width - 12); }

    private java.util.List<TabSlot> tabSlots(Font f, int originX, int originY, int padding, int rightLimit) {
        java.util.List<TabSlot> slots = new java.util.ArrayList<>();
        int tabX = originX, tabY = originY;
        for (AiProviderType candidate : supportedProviders()) {
            int tabWidth = textWidth(f, tabLabel(candidate)) + padding;
            if (tabX > originX && tabX + tabWidth > rightLimit) { tabX = originX; tabY += tabRowHeight(); }
            slots.add(new TabSlot(candidate, tabX, tabY, tabWidth));
            tabX += tabWidth + 2;
        }
        return slots;
    }
    private static int tabRowHeight() { return HEADER - 2; }
    private static int textWidth(Font f, String value) { return f == null ? value.length() * 6 : f.width(value); }
    /** The chat header grows when provider tabs wrap; the settings header keeps its tabs in the body. */
    private int headerHeight() {
        if (view == View.SETTINGS) return HEADER;
        java.util.List<TabSlot> slots = chatTabSlots(currentFont);
        return HEADER + slots.get(slots.size() - 1).y() - slots.get(0).y();
    }

    void render(GuiGraphics c, Font font, int mouseX, int mouseY, int accent) {
        if (!visible) return;
        currentFont = font;
        renderMouseX = mouseX; renderMouseY = mouseY; hoveredTooltip = null;
        renderedButtonKeys.clear();
        UIStyleHelper.drawBeveledPanel(c, x, y, width, height, UITheme.BACKGROUND_SECONDARY, UITheme.BORDER_DEFAULT, UITheme.PANEL_INNER_BORDER);
        c.fill(x + 1, y + 1, x + width - 1, y + headerHeight(), UITheme.BACKGROUND_SECTION);
        if (view == View.CHAT) renderChatHeader(c, font, mouseX, mouseY, accent); else renderSettingsHeader(c, font, mouseX, mouseY, accent);
        c.hLine(x + 1, x + width - 2, y + headerHeight(), UITheme.BORDER_SUBTLE);
        if (view != View.SETTINGS) renderChat(c, font, mouseX, mouseY, accent); else renderSettings(c, font, mouseX, mouseY, accent);
        renderCornerHandles(c);
        buttonHoverKeys.forEach((key, identity) -> { if (!renderedButtonKeys.contains(key)) HoverAnimator.getProgress(identity, false); });
        buttonHoverKeys.keySet().removeIf(key -> key.startsWith("details-") && !renderedButtonKeys.contains(key));
        if (hoveredTooltip != null) TooltipRenderer.render(c, font, hoveredTooltip, mouseX, mouseY,
            Minecraft.getInstance().getWindow().getGuiScaledWidth(), Minecraft.getInstance().getWindow().getGuiScaledHeight());
    }

    private void renderChatHeader(GuiGraphics c, Font f, int mouseX, int mouseY, int accent) {
        for (TabSlot slot : chatTabSlots(f)) {
            drawTab(c, f, tabLabel(slot.provider()), slot.x(), slot.y(), slot.width(), slot.provider() == provider, mouseX, mouseY, accent);
        }
        int settingsColor = iconButton(c, "settings", x + width - 38, y + 2, 18, 18, accent, false, "AI settings");
        PathmindWorkspaceChrome.drawSettingsIcon(c, x + width - 38, y + 2, 18, settingsColor);
        renderClose(c, f);
    }

    private void renderSettingsHeader(GuiGraphics c, Font f, int mouseX, int mouseY, int accent) {
        textButton(c, f, "back", "← Back", x + 7, y + 2, 48, 18, UIStyleHelper.TextButtonStyle.DEFAULT, accent, null);
        String heading = "AI settings";
        c.drawString(f, Component.literal(heading), x + width / 2 - f.width(heading) / 2, y + 8, UITheme.TEXT_HEADER);
        renderClose(c, f);
    }

    private void renderChat(GuiGraphics c, Font f, int mouseX, int mouseY, int accent) {
        boolean connected = AiProviderRegistry.configured(provider).isPresent();
        int composerHeight = COMPOSER_LINES * (f.lineHeight + 1) + 12;
        int composerY = y + height - composerHeight - 12;
        String headline = connected ? "Ask a question or describe a preset change" : "Configure " + provider.displayName() + " to begin";
        renderClearContextButton(c, f, mouseX, mouseY, accent);
        if (!requesting && history().isEmpty()) {
            drawCenteredWrapped(c, f, headline, x + width / 2, y + headerHeight() + 40, width - 24, 2, UITheme.TEXT_TERTIARY);
        }
        if (requesting || !history().isEmpty() || status.startsWith("Error")) renderThinking(c, f, chatBottomY());
        else if (!status.isBlank()) renderActivity(c, f, status.startsWith("Error") ? "Error" : "Result", status, composerY - 18, status.startsWith("Error") ? UITheme.STATE_ERROR : UITheme.TEXT_SECONDARY);
        renderProposalActions(c, f, mouseX, mouseY, composerY, accent);
        c.fill(x + 10, composerY, x + width - 10, composerY + composerHeight, UITheme.BACKGROUND_PRIMARY);
        DrawBorder(c, x + 10, composerY, width - 20, composerHeight, activeField == Field.PROMPT ? accent : UITheme.BORDER_DEFAULT);
        String fieldText = prompt.isBlank() && activeField != Field.PROMPT ? "Ask " + provider.displayName() + "…" : prompt;
        int textWidth = promptTextWidth();
        java.util.List<TextLine> promptLines = promptLines(f, fieldText, textWidth);
        if (activeField == Field.PROMPT) ensurePromptCursorVisible(promptLines);
        int lineY = composerY + 6;
        if (activeField == Field.PROMPT && promptAnchor != promptCursor) renderPromptSelection(c, f, promptLines, lineY);
        for (int i = 0; i < COMPOSER_LINES && promptScrollLine + i < promptLines.size(); i++) c.drawString(f, Component.literal(promptLines.get(promptScrollLine + i).text()), x + 17, lineY + i * (f.lineHeight + 1), prompt.isBlank() ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY);
        if (activeField == Field.PROMPT && ((System.currentTimeMillis() / 300L) & 1L) == 0L) {
            int lineIndex = promptLineIndex(promptLines, promptCursor);
            TextLine cursorLine = promptLines.get(lineIndex);
            int caretY = lineY + (lineIndex - promptScrollLine) * (f.lineHeight + 1);
            String beforeCursor = cursorLine.text().substring(0, Math.max(0, Math.min(promptCursor - cursorLine.start(), cursorLine.text().length())));
            c.vLine(Math.min(actionButtonX() - 4, x + 17 + f.width(beforeCursor)), caretY, caretY + f.lineHeight, UITheme.CARET_COLOR);
        }
        int actionX = actionButtonX();
        int actionY = actionButtonY(composerY, composerHeight);
        var actionPalette = buttonFrame(c, "send-stop", actionX, actionY, ACTION_BUTTON_SIZE, ACTION_BUTTON_SIZE,
            requesting ? UIStyleHelper.TextButtonStyle.DANGER : UIStyleHelper.TextButtonStyle.PRIMARY, accent, null);
        if (requesting) {
            c.fill(actionX + 5, actionY + 5, actionX + 11, actionY + 11, actionPalette.textColor());
        } else {
            PathmindIconRenderer.drawSendArrow(c, actionX, actionY, ACTION_BUTTON_SIZE, actionPalette.textColor());
        }
        if (requesting) PathmindIconRenderer.drawLoadingDots(c, x + 10, y + headerHeight() + 5, 16, UITheme.TEXT_HEADER, System.currentTimeMillis());
        String activity = requesting ? progressLabel : pendingProposal != null ? "Awaiting review" : status;
        c.drawString(f, Component.literal(trim(activity, Math.max(1, (width - (requesting ? 62 : 42)) / 6))),
            x + (requesting ? 29 : 12), y + headerHeight() + 8, status.startsWith("Error") ? UITheme.STATE_ERROR : UITheme.TEXT_SECONDARY);
        if (!chatScroll.atEnd()) {
            textButton(c, f, "latest", "↓ Latest", x + width - 68, chatBottomY() + 1, 58, 13, UIStyleHelper.TextButtonStyle.DEFAULT, accent, null);
        }
    }

    private void renderProposalActions(GuiGraphics c, Font f, int mouseX, int mouseY, int composerY, int accent) {
        if (pendingProposal == null) return;
        int buttonY = composerY - 20;
        int applyWidth = 48;
        int discardWidth = 54;
        textButton(c, f, "apply", "Apply", x + 10, buttonY, applyWidth, 16, UIStyleHelper.TextButtonStyle.PRIMARY, accent, "Apply the reviewed proposal");
        textButton(c, f, "discard", "Discard", x + 64, buttonY, discardWidth, 16, UIStyleHelper.TextButtonStyle.DANGER, accent, "Discard without changing the preset");
    }

    private void renderClearContextButton(GuiGraphics c, Font f, int mouseX, int mouseY, int accent) {
        int bx = resetButtonX(), by = y + headerHeight() + 4;
        int iconColor = iconButton(c, "clear-history", bx, by, 16, 16, resetArmed ? UITheme.STATE_ERROR : accent,
            resetArmed, resetArmed ? "Click again to reset chat and context" : "Reset chat and context");
        PathmindWorkspaceChrome.drawClearIcon(c, bx, by, 16, resetArmed ? UITheme.STATE_ERROR : iconColor);
    }
    private int resetButtonX() { return x + width - 26; }

    /**
     * Row origins for the settings form. Gateway providers get two extra controls, so the layout is
     * derived once and shared by rendering and hit-testing instead of being repeated as offsets.
     */
    private record SettingsForm(int keyY, int endpointY, int modelY, int routingY, boolean routing) {
        int inputY(int rowY) { return rowY + SETTINGS_INPUT_OFFSET; }
    }

    private SettingsForm settingsForm(Font f) {
        java.util.List<TabSlot> slots = settingsTabSlots(f);
        int keyY = slots.get(slots.size() - 1).y() + tabRowHeight() + 10;
        int endpointY = keyY + SETTINGS_ROW;
        int modelY = endpointY + SETTINGS_ROW;
        return new SettingsForm(keyY, endpointY, modelY, modelY + SETTINGS_ROW, supportsRouting());
    }
    private boolean supportsRouting() { return provider == AiProviderType.OPENROUTER; }
    /** Gemini addresses the model through the URL, so an edited endpoint must keep the placeholder. */
    private String settingsHint() {
        return provider == AiProviderType.GEMINI
            ? "Keep {model} in the endpoint. Keys are stored encrypted locally."
            : "Stored encrypted in your local Pathmind settings.";
    }
    private int saveButtonY() { return y + height - 30; }

    private void renderSettings(GuiGraphics c, Font f, int mouseX, int mouseY, int accent) {
        modelDropdownAnimation.animateTo(modelDropdownOpen ? 1f : 0f, UITheme.TRANSITION_ANIM_MS, AnimationHelper::easeOutQuad);
        modelDropdownAnimation.tick();
        routingDropdownAnimation.animateTo(routingDropdownOpen ? 1f : 0f, UITheme.TRANSITION_ANIM_MS, AnimationHelper::easeOutQuad);
        routingDropdownAnimation.tick();
        for (TabSlot slot : settingsTabSlots(f)) {
            drawTab(c, f, tabLabel(slot.provider()), slot.x(), slot.y(), slot.width(), slot.provider() == provider, mouseX, mouseY, accent);
        }
        SettingsForm form = settingsForm(f);
        c.drawString(f, Component.literal(provider.displayName() + " API key"), x + 12, form.keyY(), UITheme.TEXT_SECONDARY);
        input(c, f, masked(apiKey), Field.KEY, x + 12, form.inputY(form.keyY()), width - 24);
        c.drawString(f, Component.literal("Endpoint"), x + 12, form.endpointY(), UITheme.TEXT_SECONDARY);
        input(c, f, endpoint, Field.ENDPOINT, x + 12, form.inputY(form.endpointY()), width - 24);
        c.drawString(f, Component.literal("Model"), x + 12, form.modelY(), UITheme.TEXT_SECONDARY);
        renderModelField(c, f, form.inputY(form.modelY()), mouseX, mouseY, accent);
        if (form.routing()) {
            c.drawString(f, Component.literal("Routing"), x + 12, form.routingY(), UITheme.TEXT_SECONDARY);
            renderFallbackToggle(c, f, form.routingY(), accent);
            renderRoutingField(c, f, form.inputY(form.routingY()), mouseX, mouseY, accent);
        }
        drawWrapped(c, f, settingsHint(), x + 12, saveButtonY() - 24, width - 84, 2, UITheme.TEXT_TERTIARY);
        textButton(c, f, "save-settings", "Save", x + width - 72, saveButtonY(), 60, 18, UIStyleHelper.TextButtonStyle.PRIMARY, accent, null);
        // Overlays draw last so an open list covers the rows beneath it.
        renderModelDropdownOptions(c, f, form.inputY(form.modelY()), mouseX, mouseY, accent);
        if (form.routing()) renderRoutingDropdownOptions(c, f, form.inputY(form.routingY()), mouseX, mouseY, accent);
    }

    private void renderFallbackToggle(GuiGraphics c, Font f, int rowY, int accent) {
        Bounds bounds = fallbackToggleBounds(f, rowY);
        int color = framelessColor("fallback-toggle", bounds.x(), bounds.y(), bounds.width(), bounds.height(),
            allowFallbacks ? accent : UITheme.TEXT_HEADER);
        c.drawString(f, Component.literal("Fallbacks"), bounds.x(), rowY, color);
        int boxX = x + width - 21, boxY = rowY - 1;
        DrawBorder(c, boxX, boxY, 10, 10, allowFallbacks ? accent : UITheme.BORDER_DEFAULT);
        if (allowFallbacks) c.fill(boxX + 3, boxY + 3, boxX + 7, boxY + 7, accent);
    }
    private record Bounds(int x, int y, int width, int height) { }
    /** Label and box share one hit target; the box alone is a 10px square and hard to hit. */
    private Bounds fallbackToggleBounds(Font f, int rowY) {
        int left = x + width - 25 - textWidth(f, "Fallbacks");
        return new Bounds(left, rowY - 2, x + width - 11 - left, 13);
    }

    private void renderRoutingField(GuiGraphics c, Font f, int iy, int mouseX, int mouseY, int accent) {
        dropdownField(c, f, routingLabel(routingSort), "routing-dropdown", routingDropdownOpen, iy, mouseX, mouseY, accent);
    }
    private void renderRoutingDropdownOptions(GuiGraphics c, Font f, int iy, int mouseX, int mouseY, int accent) {
        String[] options = ROUTING_LABELS;
        renderDropdownOptions(c, f, iy, mouseX, mouseY, accent, routingDropdownAnimation, options,
            index -> options[index].equals(routingLabel(routingSort)));
    }

    boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!visible || button != 0) return false;
        ResizeCorner corner = resizeCornerAt(mouseX, mouseY);
        if (corner != null) { beginResize(corner); return true; }
        if (!contains(mouseX, mouseY, x, y, width, height)) { activeField = Field.NONE; closeDropdowns(); return false; }
        activeField = Field.NONE;
        if (contains(mouseX, mouseY, x + width - 18, y + 2, 16, 18)) { close(); return true; }
        if (view == View.CHAT) return chatClick(mouseX, mouseY);
        return settingsClick(mouseX, mouseY);
    }
    private boolean chatClick(int mouseX, int mouseY) {
        if (contains(mouseX, mouseY, resetButtonX(), y + headerHeight() + 4, 16, 16)) {
            if (resetArmed) clearConversation(); else resetArmed = true;
            return true;
        }
        resetArmed = false;
        if (contains(mouseX, mouseY, x + width - 38, y + 2, 18, 18)) { view = View.SETTINGS; closeDropdowns(); return true; }
        for (TabSlot slot : chatTabSlots(currentFont)) {
            if (contains(mouseX, mouseY, slot.x(), slot.y(), slot.width(), HEADER - 4)) { selectProvider(slot.provider()); return true; }
        }
        int composerHeight = COMPOSER_LINES * ((currentFont == null ? 9 : currentFont.lineHeight) + 1) + 12;
        int composerY = y + height - composerHeight - 12;
        if (!chatScroll.atEnd() && contains(mouseX, mouseY, x + width - 68, chatBottomY() + 1, 58, 13)) { chatScroll.end(); return true; }
        if (contains(mouseX, mouseY, x + 8, chatTopY(), width - 16, Math.max(0, chatBottomY() - chatTopY()))) {
            var rows = thinkingLines(currentFont);
            updateChatScroll(rows);
            var metrics = chatScrollMetrics();
            if (chatScroll.maxPixels() > 0 && contains(mouseX, mouseY, metrics.trackLeft() - 3, metrics.trackTop(), metrics.trackWidth() + 6, metrics.viewportHeight())) {
                scrollbarDragging = true;
                int thumbTop = metrics.thumbTop(), thumbHeight = metrics.thumbHeight();
                scrollbarGrab = mouseY >= thumbTop && mouseY < thumbTop + thumbHeight ? mouseY - thumbTop : thumbHeight / 2;
                dragScrollbar(mouseY); return true;
            }
            int rowIndex = (chatScroll.offsetPixels() + mouseY - chatTopY()) / chatRowHeight();
            if (rowIndex < rows.size() && rows.get(rowIndex).kind() == AiChatLayout.Kind.DETAILS) {
                int group = rows.get(rowIndex).entry();
                if (!expandedDetails.add(group)) expandedDetails.remove(group);
                cachedThinkingRevision = -1;
            }
            return true;
        }
        if (pendingProposal != null && contains(mouseX, mouseY, x + 10, composerY - 20, 48, 16)) { applyPendingProposal(); return true; }
        if (pendingProposal != null && contains(mouseX, mouseY, x + 64, composerY - 20, 54, 16)) { discardPendingProposal(); return true; }
        if (contains(mouseX, mouseY, x + 10, composerY, width - 20, composerHeight)) {
            if (contains(mouseX, mouseY, actionButtonX(), actionButtonY(composerY, composerHeight), ACTION_BUTTON_SIZE, ACTION_BUTTON_SIZE)) {
                if (requesting) cancelRequest(); else activateAction();
            } else {
                activeField = Field.PROMPT;
                replaceOnType = false;
                promptCursor = promptIndexAt(mouseX, mouseY, composerY);
                promptAnchor = promptCursor;
                promptDragAnchor = promptCursor;
                promptSelecting = true;
            }
            return true;
        }
        dragging = true; dragOffsetX = mouseX - x; dragOffsetY = mouseY - y; return true;
    }
    private boolean settingsClick(int mouseX, int mouseY) {
        if (contains(mouseX, mouseY, x + 7, y + 2, 48, 18)) { view = View.CHAT; closeDropdowns(); return true; }
        for (TabSlot slot : settingsTabSlots(currentFont)) {
            if (contains(mouseX, mouseY, slot.x(), slot.y(), slot.width(), HEADER - 4)) { selectProvider(slot.provider()); return true; }
        }
        SettingsForm form = settingsForm(currentFont);
        // Open option lists overlay the rows below them, so they claim the click first.
        if (modelDropdownOpen) {
            Integer picked = optionIndexAt(mouseX, mouseY, form.inputY(form.modelY()), modelOptions().length);
            if (picked != null) { model = modelOptions()[picked]; modelDropdownOpen = false; return true; }
        }
        if (routingDropdownOpen) {
            Integer picked = optionIndexAt(mouseX, mouseY, form.inputY(form.routingY()), ROUTING_SORTS.length);
            if (picked != null) { routingSort = ROUTING_SORTS[picked]; routingDropdownOpen = false; return true; }
        }
        if (textFieldClick(mouseX, mouseY, Field.KEY, form.inputY(form.keyY()), width - 24)) return true;
        if (textFieldClick(mouseX, mouseY, Field.ENDPOINT, form.inputY(form.endpointY()), width - 24)) return true;
        if (contains(mouseX, mouseY, modelChevronX(), form.inputY(form.modelY()), 18, SETTINGS_INPUT_HEIGHT)) {
            modelDropdownOpen = !modelDropdownOpen; routingDropdownOpen = false; activeField = Field.NONE; return true;
        }
        if (textFieldClick(mouseX, mouseY, Field.MODEL, form.inputY(form.modelY()), width - 42)) return true;
        if (form.routing()) {
            Bounds toggle = fallbackToggleBounds(currentFont, form.routingY());
            if (contains(mouseX, mouseY, toggle.x(), toggle.y(), toggle.width(), toggle.height())) {
                allowFallbacks = !allowFallbacks; closeDropdowns(); activeField = Field.NONE; return true;
            }
            if (contains(mouseX, mouseY, x + 12, form.inputY(form.routingY()), width - 24, SETTINGS_INPUT_HEIGHT)) {
                routingDropdownOpen = !routingDropdownOpen; modelDropdownOpen = false; activeField = Field.NONE; return true;
            }
        }
        closeDropdowns();
        if (contains(mouseX, mouseY, x + width - 72, saveButtonY(), 60, 18)) { saveConfiguration(); return true; }
        dragging = true; dragOffsetX = mouseX - x; dragOffsetY = mouseY - y; return true;
    }
    private boolean textFieldClick(int mouseX, int mouseY, Field field, int iy, int fieldWidth) {
        if (!contains(mouseX, mouseY, x + 12, iy, fieldWidth, SETTINGS_INPUT_HEIGHT)) return false;
        activeField = field; replaceOnType = false; closeDropdowns();
        return true;
    }
    /** Option lists render 22px below their field; returns null when the click missed the list. */
    private Integer optionIndexAt(int mouseX, int mouseY, int fieldY, int optionCount) {
        int top = fieldY + 22;
        if (!contains(mouseX, mouseY, x + 12, top, width - 24, optionCount * 20)) return null;
        return (mouseY - top) / 20;
    }
    boolean mouseDragged(int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (!visible) return false;
        if (scrollbarDragging) { dragScrollbar(mouseY); return true; }
        if (resizing) { updateResize(mouseX, mouseY, screenWidth, screenHeight); return true; }
        if (dragging) { x = mouseX - dragOffsetX; y = mouseY - dragOffsetY; clampToScreen(screenWidth, screenHeight); return true; }
        if (promptSelecting) { promptCursor = promptIndexAt(mouseX, mouseY, y + height - (COMPOSER_LINES * ((currentFont == null ? 9 : currentFont.lineHeight) + 1) + 12) - 12); return true; }
        return false;
    }
    boolean mouseReleased() { boolean handled = dragging || resizing || promptSelecting || scrollbarDragging; dragging = false; resizing = false; scrollbarDragging = false; resizeCorner = null; promptSelecting = false; return handled; }
    boolean keyPressed(int keyCode, int modifiers) {
        if (!visible) return false;
        if (modelDropdownOpen || routingDropdownOpen) { closeDropdowns(); return true; }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        boolean shortcut = (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        if (shortcut && isTextField(activeField) && keyCode == GLFW.GLFW_KEY_A) { replaceOnType = true; return true; }
        if (shortcut && isTextField(activeField) && keyCode == GLFW.GLFW_KEY_V) { pasteField(); return true; }
        if (shortcut && keyCode == GLFW.GLFW_KEY_A && activeField == Field.PROMPT) { promptAnchor = 0; promptCursor = prompt.length(); return true; }
        if (shortcut && activeField == Field.PROMPT && keyCode == GLFW.GLFW_KEY_C) { copyPromptSelection(); return true; }
        if (shortcut && activeField == Field.PROMPT && keyCode == GLFW.GLFW_KEY_X) { copyPromptSelection(); deletePromptSelection(); return true; }
        if (shortcut && activeField == Field.PROMPT && keyCode == GLFW.GLFW_KEY_V) { insertPromptText(clipboardText()); return true; }
        if (shortcut && keyCode == GLFW.GLFW_KEY_A && activeField != Field.NONE) { replaceOnType = true; return true; }
        if (activeField == Field.PROMPT && currentFont != null) {
            boolean extendingSelection = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
            if (keyCode == GLFW.GLFW_KEY_LEFT) { promptCursor = !extendingSelection && promptAnchor != promptCursor ? Math.min(promptAnchor, promptCursor) : Math.max(0, promptCursor - 1); if (!extendingSelection) promptAnchor = promptCursor; return true; }
            if (keyCode == GLFW.GLFW_KEY_RIGHT) { promptCursor = !extendingSelection && promptAnchor != promptCursor ? Math.max(promptAnchor, promptCursor) : Math.min(prompt.length(), promptCursor + 1); if (!extendingSelection) promptAnchor = promptCursor; return true; }
            if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) { movePromptCursorVertically(keyCode == GLFW.GLFW_KEY_UP ? -1 : 1); if (!extendingSelection) promptAnchor = promptCursor; return true; }
            if (keyCode == GLFW.GLFW_KEY_HOME) { promptCursor = 0; if (!extendingSelection) promptAnchor = promptCursor; return true; }
            if (keyCode == GLFW.GLFW_KEY_END) { promptCursor = prompt.length(); if (!extendingSelection) promptAnchor = promptCursor; return true; }
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && activeField != Field.NONE) { backspace(); return true; }
        if (keyCode == GLFW.GLFW_KEY_DELETE && activeField == Field.PROMPT) { if (promptAnchor != promptCursor) deletePromptSelection(); else if (promptCursor < prompt.length()) prompt = prompt.substring(0, promptCursor) + prompt.substring(promptCursor + 1); return true; }
        if (keyCode == GLFW.GLFW_KEY_DELETE && activeField != Field.NONE) { clearField(); return true; }
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && activeField == Field.PROMPT) { if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) insertPromptText("\n"); else activateAction(); return true; }
        return activeField != Field.NONE;
    }
    // Decline characters unless a field actually has focus. The popup is non-modal and the editor
    // routes charTyped to it before the preset and rename controllers, so consuming while unfocused
    // silently kills every other text input on the screen. keyPressed already declines this way.
    boolean charTyped(char character) { if (!visible || activeField == Field.NONE || Character.isISOControl(character)) return false; type(character); return true; }

    private void activateAction() {
        if (requesting) return;
        if (prompt.isBlank()) { reportError("Describe the preset first."); return; }
        if (pendingProposal != null) { reportError("Apply or discard the pending proposal first."); return; }
        if (AiProviderRegistry.configured(provider).isEmpty()) { view = View.SETTINGS; reportError("Configure " + provider.displayName() + " first."); return; }
        requesting = true; status = ""; chatScroll.end(); progressLabel = "Understanding your request"; requestStartedAt = System.currentTimeMillis();
        String submittedPrompt = prompt;
        int requestGeneration = conversationGeneration;
        long requestHistoryGeneration = conversationHistory.generation(provider);
        var requestStamp = com.pathmind.ai.AiRequestStamp.capture(provider, requestGeneration, conversationHistory);
        String priorContext = conversationContext();
        appendHistory(provider, Role.USER, submittedPrompt);
        prompt = ""; promptCursor = 0; promptAnchor = 0; promptScrollLine = 0;
        AiProviderType requestProvider = provider;
        requestControl = new AiRequestControl(progress -> Minecraft.getInstance().execute(() -> {
            if (!requestStamp.isCurrent(provider, conversationGeneration, conversationHistory) || !requesting) return;
            progressLabel = progress.message();
            if (progress.completedTool() != null && !progress.completedTool().tool().equals("finish")) {
                var step = progress.completedTool();
                appendHistory(requestProvider, Role.DETAIL, (step.success() ? "✓ " : "! ") + "turn " + step.turn()
                    + " " + step.tool() + (step.code().isBlank() ? "" : " [" + step.code() + "]")
                    + " (draft " + step.revisionBefore() + "→" + step.revisionAfter() + "): " + step.message());
            }
        }));
        host.requestAiProposal(provider, submittedPrompt, priorContext, requestControl, value -> {
            if (!requestStamp.isCurrent(provider, conversationGeneration, conversationHistory)) return;
            requesting = false;
            requestControl = null;
            if (value.changesGraph()) {
                recordChange(requestProvider, "PROPOSED", value.title(), AiPresetService.graphFingerprint(value.title(), value.graph()));
                pendingProposal = value;
                pendingHistoryGeneration = requestHistoryGeneration;
                status = value.editsCurrentPreset() ? "Review the proposed update." : "Review the proposed preset.";
                appendHistory(requestProvider, Role.EVENT, "Proposal prepared for review; it has NOT been applied. Target: " + value.target());
                if (value.review() != null) for (String line : value.review().displayLines()) {
                    appendHistory(requestProvider, Role.DETAIL, line);
                }
            } else {
                status = switch (value.outcome()) {
                    case CLARIFICATION -> "Waiting for your clarification.";
                    case BLOCKED -> "Unable to prepare the requested change.";
                    default -> "Answered. No preset changes applied.";
                };
                if (value.outcome() == com.pathmind.ai.AiCompletionOutcome.CLARIFICATION || value.outcome() == com.pathmind.ai.AiCompletionOutcome.BLOCKED)
                    appendHistory(requestProvider, Role.EVENT, "Request outcome: " + value.outcome() + ". No preset changes applied.");
                else appendHistory(requestProvider, Role.EVENT, "Request outcome: ANSWER. No preset changes applied.");
            }
            String response = value.response() == null || value.response().isBlank() ? (value.editsCurrentPreset() ? "I prepared an update for review." : value.changesGraph() ? "I prepared a new preset for review." : "I reviewed the current preset.") : value.response();
            appendHistory(requestProvider, Role.ASSISTANT, response);
        }, error -> { if (!requestStamp.isCurrent(provider, conversationGeneration, conversationHistory)) return; requesting = false; requestControl = null; appendHistory(requestProvider, Role.ERROR, error); reportError(error); });
    }
    private void applyPendingProposal() {
        if (pendingProposal == null) return;
        if (pendingHistoryGeneration != conversationHistory.generation(provider)) { pendingProposal = null; reportError("This proposal belongs to a reset conversation. Send a new request."); return; }
        String result = host.applyAiProposal(pendingProposal);
        if (result.startsWith("Error")) { reportError(result); return; }
        appendHistory(provider, Role.EVENT, "Proposal applied: " + result);
        try { conversationHistory.recordChange(provider, "APPLIED", host.activePresetName(), AiPresetService.graphFingerprint(host.activePresetName(), host.activeGraph()),
            pendingProposal.review() == null ? "" : String.join("; ", pendingProposal.review().changes())); }
        catch (IllegalStateException failure) { host.showAiError(failure.getMessage()); }
        pendingProposal = null;
        status = result;
    }
    private void discardPendingProposal() {
        if (pendingProposal == null) return;
        recordChange(provider, "DISCARDED", pendingProposal.title(), AiPresetService.graphFingerprint(pendingProposal.title(), pendingProposal.graph()));
        pendingProposal = null;
        status = "Proposal discarded.";
        appendHistory(provider, Role.EVENT, "Proposal discarded; it was NOT applied.");
    }
    private void clearConversation() {
        try { conversationHistory.reset(provider); }
        catch (IllegalStateException failure) { reportError(failure.getMessage()); resetArmed = false; return; }
        stopWork(false);
        conversationGeneration++; pendingProposal = null; prompt = ""; promptCursor = promptAnchor = promptScrollLine = 0;
        status = ""; requestStartedAt = 0; requesting = false; activeField = Field.NONE; chatScroll.reset(); expandedDetails.clear(); resetArmed = false;
    }
    private java.util.List<ChatLine> history() { return history(provider); }
    private java.util.List<ChatLine> history(AiProviderType type) {
        long revision = conversationHistory.revision(type);
        if (cachedHistoryProvider == type && cachedHistoryRevision == revision) return cachedHistory;
        cachedHistoryProvider = type; cachedHistoryRevision = revision;
        cachedHistory = conversationHistory.history(type).stream().map(entry -> new ChatLine(
            switch (entry.role()) { case USER -> "You: "; case ASSISTANT -> "AI: "; case ERROR -> "Error: "; default -> "· "; } + entry.text(),
            switch (entry.role()) { case USER, EVENT -> UITheme.TEXT_SECONDARY; case ASSISTANT -> UITheme.TEXT_PRIMARY; case ERROR -> UITheme.STATE_ERROR; case DETAIL -> UITheme.TEXT_TERTIARY; }
        )).toList();
        return cachedHistory;
    }
    private void appendHistory(AiProviderType type, Role role, String text) {
        try { conversationHistory.append(type, role, text); }
        catch (IllegalStateException failure) { host.showAiError(failure.getMessage()); }
    }
    private String conversationContext() { return conversationHistory.context(provider); }
    private void recordChange(AiProviderType type, String state, String preset, String fingerprint) {
        try { conversationHistory.recordChange(type, state, preset, fingerprint); }
        catch (IllegalStateException failure) { host.showAiError(failure.getMessage()); }
    }
    private void saveConfiguration() {
        if (apiKey.isBlank() && !com.pathmind.ai.AiSecretStore.hasSecret(provider)) { status = "Error: enter an API key."; return; }
        if (model.isBlank()) { status = "Error: enter a model."; return; }
        // Routing is only passed for providers that expose it, so a direct provider never overwrites it.
        AiProviderRegistry.saveConfiguration(provider, true, model, endpoint, apiKey.isBlank() ? null : apiKey,
            supportsRouting() ? routingSort : null, supportsRouting() ? allowFallbacks : null);
        apiKey = ""; loadProviderConfiguration(); status = "Saved."; view = View.CHAT;
    }
    private void reportError(String message) { status = "Error: " + (message == null || message.isBlank() ? "AI request failed." : message); host.showAiError(status); }
    private void cancelRequest() { stopWork(true); }
    private void stopWork(boolean record) {
        if (!requesting || requestControl == null) return;
        AiRequestControl cancelledControl = requestControl;
        conversationGeneration++; requesting = false; requestControl = null; pendingProposal = null;
        progressLabel = "Cancelled"; status = "Request cancelled. No preset changes applied.";
        cancelledControl.cancel();
        if (record) appendHistory(provider, Role.EVENT, status);
    }
    void dispose() { stopWork(true); try { conversationHistory.flush(); } catch (IllegalStateException failure) { host.showAiError(failure.getMessage()); } close(); }
    private int chatRowHeight() { return (currentFont == null ? 9 : currentFont.lineHeight) + 3; }
    private int chatTopY() { return y + headerHeight() + 25; }
    private int chatBottomY() {
        int composerHeight = COMPOSER_LINES * ((currentFont == null ? 9 : currentFont.lineHeight) + 1) + 12;
        return y + height - composerHeight - 12 - (pendingProposal == null ? 24 : 40);
    }
    private void updateChatScroll(java.util.List<AiChatLayout.Row> rows) {
        chatScroll.updatePixels(rows, Math.max(1, chatBottomY() - chatTopY()), chatRowHeight());
    }
    private ScrollbarHelper.Metrics chatScrollMetrics() {
        return ScrollbarHelper.metrics(x + width - 12, chatTopY(), UITheme.SCROLLBAR_WIDTH,
            Math.max(1, chatBottomY() - chatTopY()), chatScroll.maxPixels(), chatScroll.offsetPixels(), 20);
    }
    private void dragScrollbar(int mouseY) {
        chatScroll.seekPixels(ScrollbarHelper.scrollFromThumb(chatScrollMetrics(), mouseY - scrollbarGrab));
    }
    private void renderThinking(GuiGraphics c, Font f, int bottomY) {
        java.util.List<AiChatLayout.Row> rows = thinkingLines(f);
        int topY = chatTopY();
        int rowHeight = f.lineHeight + 3;
        updateChatScroll(rows);
        c.enableScissor(x + 8, topY, x + width - 18, bottomY);
        int endRow = (chatScroll.offsetPixels() + bottomY - topY + rowHeight - 1) / rowHeight;
        for (int i = chatScroll.top(); i < Math.min(rows.size(), endRow); i++) {
            int lineY = topY + i * rowHeight - chatScroll.offsetPixels();
            var row = rows.get(i);
            int color = row.kind() == AiChatLayout.Kind.DETAILS ? framelessColor("details-" + row.entry(), x + 12, lineY, width - 32, rowHeight, UITheme.TEXT_HEADER)
                : row.role() == Role.ERROR ? UITheme.STATE_ERROR : row.role() == Role.DETAIL ? UITheme.TEXT_TERTIARY
                : row.role() == Role.ASSISTANT ? UITheme.TEXT_PRIMARY : UITheme.TEXT_SECONDARY;
            c.drawString(f, Component.literal(row.text()), x + 12, lineY, color);
        }
        c.disableScissor();
        ScrollbarHelper.renderSettingsStyle(c, chatScrollMetrics(), UITheme.BACKGROUND_SIDEBAR, UITheme.BORDER_DEFAULT, UITheme.BORDER_DEFAULT);
    }
    private void renderActivity(GuiGraphics c, Font f, String label, String message, int bottomY, int color) { int topY = Math.max(y + headerHeight() + 58, bottomY - 3 * (f.lineHeight + 3)); c.drawString(f, Component.literal(label), x + 12, topY, color); drawWrapped(c, f, message, x + 16, topY + f.lineHeight + 3, width - 32, 2, color); }
    private record ChatLine(String text, int color) { }
    private java.util.List<AiChatLayout.Row> thinkingLines(Font font) {
        long revision = conversationHistory.revision(provider);
        if (cachedThinkingFont == font && cachedThinkingProvider == provider && cachedThinkingWidth == width
            && cachedThinkingRevision == revision && cachedThinkingRequesting == requesting && cachedThinkingStatus.equals(status)) return cachedThinkingLines;
        var entries = new java.util.ArrayList<>(conversationHistory.history(provider));
        if (status.startsWith("Error") && (entries.isEmpty() || entries.getLast().role() != Role.ERROR
            || !(entries.getLast().text().equals(status) || status.equals("Error: " + entries.getLast().text()))))
            entries.add(new AiChatHistoryStore.Entry(Role.ERROR, status, System.currentTimeMillis()));
        cachedThinkingFont = font; cachedThinkingProvider = provider; cachedThinkingWidth = width;
        cachedThinkingRevision = revision; cachedThinkingRequesting = requesting; cachedThinkingStatus = status;
        cachedThinkingLines = AiChatLayout.layout(entries, Math.max(24, width - 32), font == null ? text -> text.length() * 6 : font::width, expandedDetails);
        return cachedThinkingLines;
    }
    boolean mouseScrolled(int mouseX, int mouseY, double amount) {
        if (!visible || view == View.SETTINGS || currentFont == null || amount == 0 || !contains(mouseX, mouseY, x + 8, chatTopY(), width - 16, Math.max(0, chatBottomY() - chatTopY()))) return false;
        updateChatScroll(thinkingLines(currentFont));
        chatScroll.wheel(amount); return true;
    }
    private void selectProvider(AiProviderType next) { if (next != provider) { stopWork(true); conversationGeneration++; requesting = false; pendingProposal = null; requestStartedAt = 0; status = ""; prompt = ""; promptCursor = promptAnchor = promptScrollLine = 0; chatScroll.reset(); expandedDetails.clear(); resetArmed = false; } provider = next; apiKey = ""; loadProviderConfiguration(); closeDropdowns(); activeField = Field.NONE; replaceOnType = false; }
    private static boolean isTextField(Field field) { return field == Field.KEY || field == Field.ENDPOINT || field == Field.MODEL; }
    private String fieldValue(Field field) { return switch (field) { case KEY -> apiKey; case ENDPOINT -> endpoint; case MODEL -> model; default -> ""; }; }
    private void setFieldValue(Field field, String value) { switch (field) { case KEY -> apiKey = value; case ENDPOINT -> endpoint = value; case MODEL -> model = value; default -> { } } }
    private static int fieldLimit(Field field) { return switch (field) { case KEY -> KEY_LIMIT; case ENDPOINT -> ENDPOINT_LIMIT; default -> MODEL_LIMIT; }; }
    private void type(char character) { if (replaceOnType) clearField(); replaceOnType = false; if (activeField == Field.PROMPT) { insertPromptText(String.valueOf(character)); return; } if (!isTextField(activeField)) return; String current = fieldValue(activeField); if (current.length() < fieldLimit(activeField)) setFieldValue(activeField, current + character); }
    private void backspace() { if (replaceOnType) { clearField(); return; } if (activeField == Field.PROMPT) { if (promptAnchor != promptCursor) deletePromptSelection(); else if (promptCursor > 0) { prompt = prompt.substring(0, promptCursor - 1) + prompt.substring(promptCursor); promptCursor--; promptAnchor = promptCursor; } return; } if (!isTextField(activeField)) return; String current = fieldValue(activeField); if (!current.isEmpty()) setFieldValue(activeField, current.substring(0, current.length() - 1)); }
    private void clearField() { if (activeField == Field.PROMPT) { prompt = ""; promptCursor = 0; promptScrollLine = 0; } else if (isTextField(activeField)) setFieldValue(activeField, ""); replaceOnType = false; }
    private String configuredModel() { String configured = AiProviderRegistry.config(provider).model; return configured == null || configured.isBlank() ? provider.defaultModel() : configured; }
    /** Mirrors the stored configuration into the editable fields whenever the active provider changes. */
    private void loadProviderConfiguration() {
        var config = AiProviderRegistry.config(provider);
        model = configuredModel();
        endpoint = config.endpoint == null || config.endpoint.isBlank() ? provider.defaultEndpoint() : config.endpoint;
        routingSort = config.routingSort == null ? "" : config.routingSort;
        allowFallbacks = !Boolean.FALSE.equals(config.allowFallbacks);
    }
    /**
     * The model row is a free-text field with a suggestion list attached, not a closed dropdown:
     * provider catalogues move faster than this list, and a gateway exposes hundreds of slugs.
     */
    private void renderModelField(GuiGraphics c, Font f, int iy, int mouseX, int mouseY, int accent) {
        int ix = x + 12, iw = width - 24;
        input(c, f, model, Field.MODEL, ix, iy, iw - 18);
        boolean hovered = contains(mouseX, mouseY, modelChevronX(), iy, 18, SETTINGS_INPUT_HEIGHT);
        var palette = UIStyleHelper.getDropdownFieldPalette(accent, hover("model-dropdown", hovered), modelDropdownOpen, false);
        UIStyleHelper.drawBeveledPanel(c, modelChevronX(), iy, 18, SETTINGS_INPUT_HEIGHT,
            palette.backgroundColor(), palette.borderColor(), palette.innerBorderColor());
        PathmindPopupRenderer.drawDropdownChevron(c, modelChevronX() + 6, iy + 6,
            modelDropdownOpen ? accent : UITheme.TEXT_SECONDARY, modelDropdownOpen);
    }
    private int modelChevronX() { return x + width - 30; }

    private void dropdownField(GuiGraphics c, Font f, String value, String key, boolean open, int iy, int mouseX, int mouseY, int accent) {
        int ix = x + 12, iw = width - 24;
        boolean hovered = contains(mouseX, mouseY, ix, iy, iw, SETTINGS_INPUT_HEIGHT);
        var palette = UIStyleHelper.getDropdownFieldPalette(accent, hover(key, hovered), open, false);
        UIStyleHelper.drawBeveledPanel(c, ix, iy, iw, SETTINGS_INPUT_HEIGHT, palette.backgroundColor(), palette.borderColor(), palette.innerBorderColor());
        c.drawString(f, Component.literal(trim(value, Math.max(16, (iw - 30) / 6))), ix + 8, iy + 6, open ? accent : UITheme.TEXT_PRIMARY);
        PathmindPopupRenderer.drawDropdownChevron(c, ix + iw - 12, iy + 6, open ? accent : UITheme.TEXT_SECONDARY, open);
    }

    private void renderModelDropdownOptions(GuiGraphics c, Font f, int iy, int mouseX, int mouseY, int accent) {
        String[] options = modelOptions();
        renderDropdownOptions(c, f, iy, mouseX, mouseY, accent, modelDropdownAnimation, options,
            index -> options[index].equals(model));
    }

    private void renderDropdownOptions(GuiGraphics c, Font f, int iy, int mouseX, int mouseY, int accent,
                                       AnimatedValue animation, String[] options, java.util.function.IntPredicate selected) {
        float progress = AnimationHelper.easeOutQuad(animation.getValue());
        if (progress <= 0.001f) return;
        int ix = x + 12, iw = width - 24;
        PathmindDropdownRenderer.renderTextList(c, f, PathmindDropdownRenderer.TextListSpec.builder()
            .bounds(ix, iy + 22, iw)
            .rows(20, options.length, options.length)
            .scroll(0, 0, 0)
            .animation(progress)
            .hoverPoint(mouseX, mouseY)
            .colors(accent, UITheme.TEXT_SECONDARY)
            .textLayout(6, 6, false, true)
            .labels("", index -> options[index])
            .textColors(index -> selected.test(index) ? UITheme.TEXT_PRIMARY : UITheme.TEXT_SECONDARY)
            .chrome(UIStyleHelper.getScrollContainerPalette(accent, 1f, true, false), UITheme.BORDER_DEFAULT, UITheme.BORDER_HIGHLIGHT, UITheme.BORDER_DEFAULT)
            .build());
    }

    private String[] modelOptions() { return switch (provider) {
        case OPENAI -> new String[]{provider.defaultModel(), "gpt-5.5"};
        case ANTHROPIC -> new String[]{provider.defaultModel(), "claude-sonnet-5", "claude-haiku-4-5-20251001"};
        case GEMINI -> new String[]{provider.defaultModel(), "gemini-3.7-flash", "gemini-3.1-pro-preview"};
        case OPENROUTER -> new String[]{provider.defaultModel(), "openai/gpt-5.4-mini", "google/gemini-2.5-flash"};
        default -> new String[]{provider.defaultModel()};
    }; }

    // Gateway sort keys, paired with the labels shown in the dropdown.
    private static final String[] ROUTING_SORTS = {"", "throughput", "price", "latency"};
    private static final String[] ROUTING_LABELS = {"Default", "Throughput", "Price", "Latency"};
    private static String routingLabel(String sort) {
        for (int i = 0; i < ROUTING_SORTS.length; i++) if (ROUTING_SORTS[i].equals(sort == null ? "" : sort)) return ROUTING_LABELS[i];
        return ROUTING_LABELS[0];
    }
    private void input(GuiGraphics c, Font f, String value, Field field, int ix, int iy, int iw) { String visible = trim(value, Math.max(16, (iw - 14) / 6)); c.fill(ix, iy, ix + iw, iy + SETTINGS_INPUT_HEIGHT, UITheme.BACKGROUND_PRIMARY); DrawBorder(c, ix, iy, iw, SETTINGS_INPUT_HEIGHT, activeField == field ? UITheme.ACCENT_SKY : UITheme.BORDER_DEFAULT); c.drawString(f, Component.literal(visible), ix + 6, iy + 6, UITheme.TEXT_PRIMARY); if (activeField == field && ((System.currentTimeMillis() / 300L) & 1L) == 0L) c.vLine(Math.min(ix + iw - 6, ix + 6 + f.width(visible)), iy + 5, iy + 15, UITheme.CARET_COLOR); }
    private void clampToScreen(int screenWidth, int screenHeight) { width = Math.min(width, Math.max(MIN_WIDTH, screenWidth - 8)); height = Math.min(height, Math.max(MIN_HEIGHT, screenHeight - 8)); x = clamp(x, 0, Math.max(0, screenWidth - width)); y = clamp(y, 0, Math.max(0, screenHeight - height)); }
    Identifier cursorTexture(int mouseX, int mouseY) { if (!visible) return null; if (resizing) return textureFor(resizeCorner); ResizeCorner corner = resizeCornerAt(mouseX, mouseY); if (corner != null) return textureFor(corner); if (dragging) return PathmindCursor.GRABBING_TEXTURE; if (contains(mouseX, mouseY, x, y, width, HEADER)) return PathmindCursor.GRAB_TEXTURE; return null; }
    private Identifier textureFor(ResizeCorner corner) { return switch (corner) { case TOP_LEFT -> PathmindCursor.SCALE_TOP_LEFT_TEXTURE; case TOP_RIGHT -> PathmindCursor.SCALE_TOP_RIGHT_TEXTURE; case BOTTOM_LEFT -> PathmindCursor.SCALE_BOTTOM_LEFT_TEXTURE; case BOTTOM_RIGHT -> PathmindCursor.SCALE_TEXTURE; }; }
    private ResizeCorner resizeCornerAt(int mouseX, int mouseY) { int size = 12; if (contains(mouseX, mouseY, x - size / 2, y - size / 2, size, size)) return ResizeCorner.TOP_LEFT; if (contains(mouseX, mouseY, x + width - size / 2, y - size / 2, size, size)) return ResizeCorner.TOP_RIGHT; if (contains(mouseX, mouseY, x - size / 2, y + height - size / 2, size, size)) return ResizeCorner.BOTTOM_LEFT; if (contains(mouseX, mouseY, x + width - size / 2, y + height - size / 2, size, size)) return ResizeCorner.BOTTOM_RIGHT; return null; }
    private void beginResize(ResizeCorner corner) { resizing = true; resizeCorner = corner; resizeStartX = x; resizeStartY = y; resizeStartWidth = width; resizeStartHeight = height; }
    private void updateResize(int mouseX, int mouseY, int screenWidth, int screenHeight) { int left = resizeStartX, top = resizeStartY, right = resizeStartX + resizeStartWidth, bottom = resizeStartY + resizeStartHeight; switch (resizeCorner) { case TOP_LEFT -> { left = Math.min(mouseX, right - MIN_WIDTH); top = Math.min(mouseY, bottom - MIN_HEIGHT); } case TOP_RIGHT -> { right = Math.max(mouseX, left + MIN_WIDTH); top = Math.min(mouseY, bottom - MIN_HEIGHT); } case BOTTOM_LEFT -> { left = Math.min(mouseX, right - MIN_WIDTH); bottom = Math.max(mouseY, top + MIN_HEIGHT); } case BOTTOM_RIGHT -> { right = Math.max(mouseX, left + MIN_WIDTH); bottom = Math.max(mouseY, top + MIN_HEIGHT); } } x = clamp(left, 0, screenWidth - MIN_WIDTH); y = clamp(top, 0, screenHeight - MIN_HEIGHT); width = Math.min(right - x, screenWidth - x); height = Math.min(bottom - y, screenHeight - y); }
    private void renderCornerHandles(GuiGraphics c) { int color = UITheme.BORDER_HIGHLIGHT; int size = 3; c.fill(x - 1, y - 1, x + size, y + size, color); c.fill(x + width - size, y - 1, x + width + 1, y + size, color); c.fill(x - 1, y + height - size, x + size, y + height + 1, color); c.fill(x + width - size, y + height - size, x + width + 1, y + height + 1, color); }
    private void drawTab(GuiGraphics c, Font f, String label, int tx, int ty, int tw, boolean selected, int mouseX, int mouseY, int accent) {
        textButton(c, f, view + "-provider-" + label, label, tx, ty, tw, HEADER - 4,
            selected ? UIStyleHelper.TextButtonStyle.ACCENT : UIStyleHelper.TextButtonStyle.DEFAULT, accent, null);
    }
    private float hover(String key, boolean hovered) {
        renderedButtonKeys.add(key);
        return HoverAnimator.getProgress(buttonHoverKeys.computeIfAbsent(key, ignored -> new Object()), hovered);
    }
    private int framelessColor(String key, int bx, int by, int bw, int bh, int hoverColor) {
        float progress = hover(key, contains(renderMouseX, renderMouseY, bx, by, bw, bh));
        return AnimationHelper.lerpColor(UITheme.TEXT_SECONDARY, hoverColor, AnimationHelper.easeOutQuad(progress));
    }
    private void renderClose(GuiGraphics c, Font f) {
        int color = framelessColor("close", x + width - 18, y + 2, 16, 18, UITheme.STATE_ERROR);
        c.drawCenteredString(f, Component.literal("×"), x + width - 10, y + 2 + (18 - f.lineHeight) / 2 + 1, color);
    }
    private UIStyleHelper.TextButtonPalette buttonFrame(GuiGraphics c, String key, int bx, int by, int bw, int bh,
                                                       UIStyleHelper.TextButtonStyle style, int accent, String tooltip) {
        boolean hovered = contains(renderMouseX, renderMouseY, bx, by, bw, bh);
        var palette = UIStyleHelper.getTextButtonPalette(style, accent, hover(key, hovered), false);
        UIStyleHelper.drawTextButtonFrame(c, bx, by, bw, bh, palette);
        if (hovered && tooltip != null) hoveredTooltip = tooltip;
        return palette;
    }
    private void textButton(GuiGraphics c, Font f, String key, String label, int bx, int by, int bw, int bh,
                            UIStyleHelper.TextButtonStyle style, int accent, String tooltip) {
        var palette = buttonFrame(c, key, bx, by, bw, bh, style, accent, tooltip);
        c.drawCenteredString(f, Component.literal(label), bx + bw / 2, by + (bh - f.lineHeight) / 2 + 1, palette.textColor());
    }
    private int iconButton(GuiGraphics c, String key, int bx, int by, int bw, int bh, int accent, boolean active, String tooltip) {
        boolean hovered = contains(renderMouseX, renderMouseY, bx, by, bw, bh);
        float progress = hover(key, hovered);
        UIStyleHelper.drawToolbarButtonFrame(c, bx, by, bw, bh, UIStyleHelper.getToolbarButtonPalette(accent, progress, active, false));
        if (hovered && tooltip != null) hoveredTooltip = tooltip;
        return AnimationHelper.lerpColor(UITheme.TEXT_SECONDARY, UITheme.TEXT_HEADER, AnimationHelper.easeOutQuad(progress));
    }
    private static AiProviderType[] supportedProviders() { return new AiProviderType[]{AiProviderType.OPENAI, AiProviderType.ANTHROPIC, AiProviderType.GEMINI, AiProviderType.OPENROUTER}; }
    private static String tabLabel(AiProviderType provider) { return switch (provider) { case OPENAI -> "GPT"; case ANTHROPIC -> "Claude"; case GEMINI -> "Gemini"; case OPENROUTER -> "ORouter"; default -> provider.displayName(); }; }
    private static void DrawBorder(GuiGraphics c, int bx, int by, int bw, int bh, int color) { c.hLine(bx, bx + bw - 1, by, color); c.hLine(bx, bx + bw - 1, by + bh - 1, color); c.vLine(bx, by, by + bh - 1, color); c.vLine(bx + bw - 1, by, by + bh - 1, color); }
    private static boolean contains(int px, int py, int bx, int by, int bw, int bh) { return px >= bx && px < bx + bw && py >= by && py < by + bh; }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static String masked(String value) { return value == null || value.isBlank() ? "" : "•".repeat(Math.min(32, value.length())); }
    private static String trim(String value, int max) { return value == null ? "" : value.length() <= max ? value : value.substring(0, max - 1) + "…"; }
    private record TextLine(int start, int end, String text) { }
    private java.util.List<TextLine> promptLines(Font font, String value, int maxWidth) { return lineSegments(font, value, maxWidth); }
    private static java.util.List<TextLine> lineSegments(Font font, String value, int maxWidth) {
        java.util.List<TextLine> lines = new java.util.ArrayList<>();
        String source = value == null ? "" : value;
        if (source.isEmpty()) return java.util.List.of(new TextLine(0, 0, ""));
        int start = 0;
        while (start < source.length()) {
            int newline = source.indexOf('\n', start);
            int hardEnd = newline >= 0 ? newline : source.length();
            if (start == hardEnd) { lines.add(new TextLine(start, start, "")); start = hardEnd + 1; continue; }
            int end = start;
            while (end < hardEnd) {
                String candidate = source.substring(start, end + 1);
                if (font.width(candidate) > maxWidth) break;
                end++;
            }
            if (end == start) end = Math.min(start + 1, hardEnd);
            if (end < hardEnd) {
                int wordEnd = end;
                while (wordEnd > start && !Character.isWhitespace(source.charAt(wordEnd - 1))) wordEnd--;
                if (wordEnd > start) end = wordEnd;
            }
            lines.add(new TextLine(start, end, source.substring(start, end)));
            start = end;
            while (start < hardEnd && Character.isWhitespace(source.charAt(start)) && source.charAt(start) != '\n') start++;
            if (start == hardEnd && newline >= 0) start++;
        }
        return lines;
    }
    private static java.util.List<String> wrap(Font font, String value, int maxWidth) { java.util.List<String> lines = new java.util.ArrayList<>(); for (TextLine line : lineSegments(font, value, maxWidth)) { lines.add(line.text()); if (lines.size() == 2) break; } return lines; }
    private int promptLineIndex(java.util.List<TextLine> lines, int cursor) { for (int i = 0; i < lines.size(); i++) if (cursor <= lines.get(i).end()) return i; return lines.size() - 1; }
    private void ensurePromptCursorVisible(java.util.List<TextLine> lines) { int line = promptLineIndex(lines, promptCursor); if (line < promptScrollLine) promptScrollLine = line; if (line >= promptScrollLine + COMPOSER_LINES) promptScrollLine = line - COMPOSER_LINES + 1; }
    private int promptTextWidth() { return Math.max(30, actionButtonX() - (x + 17) - 4); }
    private int actionButtonX() { return x + width - 10 - ACTION_BUTTON_INSET - ACTION_BUTTON_SIZE; }
    private static int actionButtonY(int composerY, int composerHeight) { return composerY + composerHeight - ACTION_BUTTON_INSET - ACTION_BUTTON_SIZE; }
    private void movePromptCursorVertically(int direction) { java.util.List<TextLine> lines = promptLines(currentFont, prompt, promptTextWidth()); int current = promptLineIndex(lines, promptCursor); int target = Math.max(0, Math.min(lines.size() - 1, current + direction)); int offset = Math.max(0, promptCursor - lines.get(current).start()); promptCursor = Math.min(lines.get(target).end(), lines.get(target).start() + offset); ensurePromptCursorVisible(lines); }
    private int promptIndexAt(int mouseX, int mouseY, int composerY) { if (currentFont == null) return prompt.length(); java.util.List<TextLine> lines = promptLines(currentFont, prompt, promptTextWidth()); int line = Math.max(0, Math.min(lines.size() - 1, promptScrollLine + (mouseY - composerY - 6) / (currentFont.lineHeight + 1))); TextLine target = lines.get(line); int relativeX = Math.max(0, mouseX - (x + 17)); int offset = 0; while (offset < target.text().length() && currentFont.width(target.text().substring(0, offset + 1)) <= relativeX) offset++; return target.start() + offset; }
    private void renderPromptSelection(GuiGraphics c, Font f, java.util.List<TextLine> lines, int lineY) { int start = Math.min(promptAnchor, promptCursor), end = Math.max(promptAnchor, promptCursor); for (int i = promptScrollLine; i < lines.size() && i < promptScrollLine + COMPOSER_LINES; i++) { TextLine line = lines.get(i); int selectedStart = Math.max(start, line.start()), selectedEnd = Math.min(end, line.end()); if (selectedEnd <= selectedStart) continue; int left = x + 17 + f.width(line.text().substring(0, selectedStart - line.start())); int right = x + 17 + f.width(line.text().substring(0, selectedEnd - line.start())); int top = lineY + (i - promptScrollLine) * (f.lineHeight + 1); c.fill(left, top - 1, right, top + f.lineHeight + 1, 0x664F86C6); } }
    private void insertPromptText(String value) { if (value == null || value.isEmpty()) return; String cleaned = value.replace("\r", ""); int start = Math.min(promptAnchor, promptCursor), end = Math.max(promptAnchor, promptCursor); int remaining = 2048 - (prompt.length() - (end - start)); if (remaining <= 0) return; String inserted = cleaned.length() > remaining ? cleaned.substring(0, remaining) : cleaned; prompt = prompt.substring(0, start) + inserted + prompt.substring(end); promptCursor = start + inserted.length(); promptAnchor = promptCursor; }
    private void deletePromptSelection() { int start = Math.min(promptAnchor, promptCursor), end = Math.max(promptAnchor, promptCursor); if (start == end) return; prompt = prompt.substring(0, start) + prompt.substring(end); promptCursor = start; promptAnchor = start; }
    private void copyPromptSelection() { int start = Math.min(promptAnchor, promptCursor), end = Math.max(promptAnchor, promptCursor); if (end > start && Minecraft.getInstance() != null) Minecraft.getInstance().keyboardHandler.setClipboard(prompt.substring(start, end)); }
    private String clipboardText() { return Minecraft.getInstance() == null ? "" : Minecraft.getInstance().keyboardHandler.getClipboard(); }
    private void pasteField() { if (!isTextField(activeField)) return; String value = clipboardText().replace("\r", "").replace("\n", "").trim(); if (value.isEmpty()) return; if (replaceOnType) setFieldValue(activeField, ""); replaceOnType = false; String current = fieldValue(activeField); int remaining = fieldLimit(activeField) - current.length(); if (remaining > 0) setFieldValue(activeField, current + value.substring(0, Math.min(value.length(), remaining))); }
    private static void drawCenteredWrapped(GuiGraphics c, Font f, String value, int centerX, int topY, int maxWidth, int maxLines, int color) { java.util.List<String> lines = wrap(f, value, maxWidth); for (int i = 0; i < lines.size() && i < maxLines; i++) c.drawCenteredString(f, Component.literal(lines.get(i)), centerX, topY + i * (f.lineHeight + 2), color); }
    private static void drawWrapped(GuiGraphics c, Font f, String value, int leftX, int topY, int maxWidth, int maxLines, int color) { java.util.List<String> lines = wrap(f, value, maxWidth); for (int i = 0; i < lines.size() && i < maxLines; i++) c.drawString(f, Component.literal(lines.get(i)), leftX, topY + i * (f.lineHeight + 2), color); }
}
