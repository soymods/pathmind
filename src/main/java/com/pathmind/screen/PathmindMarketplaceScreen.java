package com.pathmind.screen;

import com.pathmind.PathmindMod;
import com.pathmind.data.NodeGraphData;
import com.pathmind.data.NodeGraphPersistence;
import com.pathmind.data.PresetManager;
import com.pathmind.data.SettingsManager;
import com.pathmind.marketplace.MarketplacePreset;
import com.pathmind.marketplace.MarketplaceService;
import com.pathmind.ui.animation.AnimatedValue;
import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.animation.HoverAnimator;
import com.pathmind.ui.animation.PopupAnimationHandler;
import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.ScrollbarHelper;
import com.pathmind.util.TextRenderUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Read-only marketplace browser with a full-window preset gallery.
 */
public class PathmindMarketplaceScreen extends Screen {
    private static final int OUTER_PADDING = 12;
    private static final int TOP_BAR_HEIGHT = 30;
    private static final int SECTION_TOP_GAP = 4;
    private static final int SECTION_HEADER_HEIGHT = 32;
    private static final int SECTION_BODY_PADDING = 12;
    private static final int FOOTER_HEIGHT = 14;
    private static final int CARD_GAP = 8;
    private static final int CARD_MIN_WIDTH = 120;
    private static final int CARD_MAX_WIDTH = 140;
    private static final int CARD_SIZE = 128;
    private static final int BACK_BUTTON_SIZE = 18;
    private static final int REFRESH_BUTTON_SIZE = 18;
    private static final int PAGE_CONTROL_GAP = 18;
    private static final int PAGE_NUMBER_GAP = 14;
    private static final int SEARCH_FIELD_WIDTH = 154;
    private static final int SEARCH_FIELD_HEIGHT = 18;
    private static final int SORT_BUTTON_WIDTH = 82;
    private static final int SORT_BUTTON_HEIGHT = 18;
    private static final int SORT_OPTION_HEIGHT = 18;

    private final Screen parent;

    private List<MarketplacePreset> allPresets = List.of();
    private List<MarketplacePreset> presets = List.of();
    private int selectedIndex = -1;
    private int pageIndex = 0;
    private boolean loading = false;
    private boolean initialFetchStarted = false;
    private String statusMessage = "Loading published presets...";
    private MarketplacePreset popupPreset = null;
    private boolean importingPreset = false;
    private String popupStatusMessage = "";
    private int popupStatusColor = UITheme.TEXT_SECONDARY;
    private int popupScrollOffset = 0;
    private boolean popupScrollDragging = false;
    private int popupScrollDragOffset = 0;
    private final PopupAnimationHandler presetPopupAnimation = new PopupAnimationHandler();
    private final AnimatedValue sortDropdownAnimation = AnimatedValue.forHover();
    private TextFieldWidget searchField;
    private SortMode sortMode = SortMode.NEWEST;
    private boolean sortDropdownOpen = false;

    public PathmindMarketplaceScreen(Screen parent) {
        super(Text.literal("Marketplace"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        if (searchField == null) {
            searchField = new TextFieldWidget(this.textRenderer, 0, 0, SEARCH_FIELD_WIDTH, SEARCH_FIELD_HEIGHT, Text.literal("Search presets"));
            searchField.setMaxLength(64);
            searchField.setDrawsBackground(false);
            searchField.setEditableColor(UITheme.TEXT_PRIMARY);
            searchField.setUneditableColor(UITheme.TEXT_TERTIARY);
            searchField.setChangedListener(value -> applyFilters());
            this.addSelectableChild(searchField);
        }
        if (!initialFetchStarted) {
            initialFetchStarted = true;
            refreshListings();
        }
    }

    private void refreshListings() {
        loading = true;
        statusMessage = "Loading published presets...";
        MarketplaceService.fetchPublishedPresets().whenComplete((results, throwable) -> {
            if (this.client == null) {
                return;
            }
            this.client.execute(() -> {
                loading = false;
                if (throwable != null) {
                    allPresets = List.of();
                    presets = List.of();
                    selectedIndex = -1;
                    pageIndex = 0;
                    statusMessage = "Failed to load marketplace presets.";
                    return;
                }

                allPresets = results == null ? List.of() : List.copyOf(results);
                applyFilters();
            });
        });
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, UITheme.BACKGROUND_PRIMARY);
        presetPopupAnimation.tick();
        sortDropdownAnimation.animateTo(sortDropdownOpen ? 1f : 0f, UITheme.TRANSITION_ANIM_MS, AnimationHelper::easeOutQuad);
        sortDropdownAnimation.tick();

        Layout layout = getLayout();
        renderTopBar(context, mouseX, mouseY, layout);
        renderGallerySection(context, mouseX, mouseY, layout);
        renderSortDropdown(context, mouseX, mouseY, layout);
        if (popupPreset != null || presetPopupAnimation.isVisible()) {
            renderPresetPopup(context, mouseX, mouseY, layout);
        }
    }

    private void renderTopBar(DrawContext context, int mouseX, int mouseY, Layout layout) {
        boolean backHovered = isPointInRect(mouseX, mouseY, layout.backButtonX, layout.backButtonY, BACK_BUTTON_SIZE, BACK_BUTTON_SIZE);
        drawIconButton(context, layout.backButtonX, layout.backButtonY, BACK_BUTTON_SIZE, BACK_BUTTON_SIZE, backHovered, false);
        drawBackArrow(context, layout.backButtonX, layout.backButtonY, backHovered ? UITheme.TEXT_HEADER : UITheme.TEXT_PRIMARY);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, layout.topBarY + 2, UITheme.TEXT_HEADER);
        String subtitle = TextRenderUtil.trimWithEllipsis(
            this.textRenderer,
            "Browse community presets",
            Math.max(80, this.width - 140)
        );
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(subtitle), this.width / 2, layout.topBarY + 14, UITheme.TEXT_SECONDARY);
        context.drawHorizontalLine(layout.sectionX, layout.sectionX + layout.sectionWidth - 1, layout.sectionY - 1, UITheme.BORDER_SUBTLE);

        renderFilterControls(context, mouseX, mouseY, layout);
    }

    private void renderGallerySection(DrawContext context, int mouseX, int mouseY, Layout layout) {
        boolean refreshHovered = isPointInRect(mouseX, mouseY, layout.refreshButtonX, layout.refreshButtonY, REFRESH_BUTTON_SIZE, REFRESH_BUTTON_SIZE);
        drawIconButton(context, layout.refreshButtonX, layout.refreshButtonY, REFRESH_BUTTON_SIZE, REFRESH_BUTTON_SIZE, refreshHovered, loading);
        drawRefreshIcon(context, layout.refreshButtonX, layout.refreshButtonY, loading ? UITheme.TEXT_TERTIARY : (refreshHovered ? getAccentColor() : UITheme.TEXT_PRIMARY));

        int bodyY = layout.sectionY + SECTION_HEADER_HEIGHT;
        int bodyHeight = layout.sectionHeight - SECTION_HEADER_HEIGHT - FOOTER_HEIGHT;
        drawGalleryBackdrop(context, layout.bodyX, bodyY, layout.bodyWidth, bodyHeight);
        int scissorTop = Math.max(layout.sectionY, bodyY - 6);
        context.enableScissor(layout.bodyX, scissorTop, layout.bodyX + layout.bodyWidth, bodyY + bodyHeight);

        if (loading || presets.isEmpty()) {
            String message = loading ? "Fetching latest listings..." : "Nothing to show yet.";
            int messageColor = loading ? UITheme.TEXT_PRIMARY : UITheme.TEXT_TERTIARY;
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(message),
                layout.bodyX + layout.bodyWidth / 2, bodyY + bodyHeight / 2, messageColor);
            context.disableScissor();
            renderFooter(context, mouseX, mouseY, layout);
            return;
        }

        int cardsPerPage = getCardsPerPage(layout);
        int startIndex = pageIndex * cardsPerPage;
        int endIndex = Math.min(presets.size(), startIndex + cardsPerPage);
        int visibleCount = Math.max(0, endIndex - startIndex);
        for (int index = startIndex; index < endIndex; index++) {
            Rect rect = getCardRect(layout, index - startIndex, visibleCount);
            renderPresetCard(context, mouseX, mouseY, rect, presets.get(index), index == selectedIndex);
        }

        context.disableScissor();
        renderFooter(context, mouseX, mouseY, layout);
    }

    private void renderFilterControls(DrawContext context, int mouseX, int mouseY, Layout layout) {
        int searchX = layout.searchFieldX;
        int searchY = layout.searchFieldY;
        boolean searchHovered = isPointInRect(mouseX, mouseY, searchX, searchY, SEARCH_FIELD_WIDTH, SEARCH_FIELD_HEIGHT);
        UIStyleHelper.drawToolbarButtonFrame(
            context,
            searchX,
            searchY,
            SEARCH_FIELD_WIDTH,
            SEARCH_FIELD_HEIGHT,
            UITheme.BACKGROUND_SECTION,
            searchHovered || (searchField != null && searchField.isFocused()) ? getAccentColor() : UITheme.BORDER_SUBTLE,
            UITheme.PANEL_INNER_BORDER
        );
        drawSearchIcon(context, searchX + 6, searchY + 5, UITheme.TEXT_SECONDARY);
        if (searchField != null) {
            searchField.setPosition(searchX + 18, searchY + 5);
            searchField.setWidth(SEARCH_FIELD_WIDTH - 24);
            searchField.render(context, mouseX, mouseY, 0.0f);
        }

        boolean sortHovered = isPointInRect(mouseX, mouseY, layout.sortButtonX, layout.sortButtonY, SORT_BUTTON_WIDTH, SORT_BUTTON_HEIGHT);
        drawActionButton(context, layout.sortButtonX, layout.sortButtonY, SORT_BUTTON_WIDTH, SORT_BUTTON_HEIGHT,
            sortMode.label, sortHovered || sortDropdownOpen, false);
        drawDropdownChevron(context, layout.sortButtonX + SORT_BUTTON_WIDTH - 12, layout.sortButtonY + 6,
            sortDropdownOpen ? getAccentColor() : UITheme.TEXT_SECONDARY, sortDropdownOpen);

        String resultLabel = loading
            ? "Loading..."
            : presets.size() + " result" + (presets.size() == 1 ? "" : "s");
        int resultWidth = this.textRenderer.getWidth(resultLabel);
        context.drawTextWithShadow(this.textRenderer, Text.literal(resultLabel),
            Math.max(layout.searchFieldX + SEARCH_FIELD_WIDTH + SORT_BUTTON_WIDTH + 16, layout.refreshButtonX - resultWidth - 12),
            searchY + 5,
            UITheme.TEXT_SECONDARY);
    }

    private void renderPresetCard(DrawContext context, int mouseX, int mouseY, Rect rect, MarketplacePreset preset, boolean selected) {
        boolean hovered = isPointInRect(mouseX, mouseY, rect.x, rect.y, rect.width, rect.height);
        float hoverProgress = HoverAnimator.getProgress("marketplace-card:" + preset.getId(), hovered);
        int bgColor = hoverProgress > 0.001f
            ? AnimationHelper.lerpColor(UITheme.BACKGROUND_TERTIARY, UITheme.TOOLBAR_BG_HOVER, hoverProgress * 0.45f)
            : UITheme.BACKGROUND_TERTIARY;
        int borderColor = AnimationHelper.lerpColor(UITheme.BORDER_SUBTLE, getAccentColor(), hoverProgress);
        UIStyleHelper.drawBeveledPanel(context, rect.x, rect.y, rect.width, rect.height, bgColor, borderColor, UITheme.PANEL_INNER_BORDER);

        int previewX = rect.x + 8;
        int previewY = rect.y + 8;
        int previewWidth = rect.width - 16;
        int previewHeight = rect.height - 36;
        UIStyleHelper.drawBeveledPanel(
            context,
            previewX,
            previewY,
            previewWidth,
            previewHeight,
            UITheme.BACKGROUND_PRIMARY,
            UITheme.BORDER_SUBTLE,
            UITheme.PANEL_INNER_BORDER
        );
        drawGraphPreview(context, previewX, previewY, previewWidth, previewHeight);

        int textX = rect.x + 8;
        int textWidth = rect.width - 16;
        context.drawTextWithShadow(this.textRenderer,
            Text.literal(TextRenderUtil.trimWithEllipsis(this.textRenderer, preset.getName(), textWidth)),
            textX, rect.y + rect.height - 24, UITheme.TEXT_HEADER);
        context.drawTextWithShadow(this.textRenderer,
            Text.literal(TextRenderUtil.trimWithEllipsis(this.textRenderer, "by " + preset.getAuthorName(), textWidth)),
            textX, rect.y + rect.height - 13, UITheme.TEXT_SECONDARY);
    }

    private void drawGraphPreview(DrawContext context, int x, int y, int width, int height) {
        int gridColor = 0x203D3D3D;
        for (int lineX = x + 12; lineX < x + width - 8; lineX += 18) {
            context.drawVerticalLine(lineX, y + 6, y + height - 7, gridColor);
        }
        for (int lineY = y + 10; lineY < y + height - 8; lineY += 14) {
            context.drawHorizontalLine(x + 6, x + width - 7, lineY, gridColor);
        }

        int nodeColor = UITheme.BACKGROUND_SECTION;
        int nodeBorder = UITheme.BORDER_DEFAULT;
        drawMiniNode(context, x + 14, y + 14, 46, 16, nodeColor, nodeBorder);
        drawMiniNode(context, x + width / 2 - 24, y + height / 2 - 10, 48, 16, nodeColor, nodeBorder);
        drawMiniNode(context, x + width - 62, y + height - 30, 40, 14, nodeColor, nodeBorder);

        context.drawHorizontalLine(x + 60, x + width / 2 - 26, y + 22, getAccentColor());
        context.drawHorizontalLine(x + width / 2 + 24, x + width - 62, y + height / 2, getAccentColor());
    }

    private void drawMiniNode(DrawContext context, int x, int y, int width, int height, int backgroundColor, int borderColor) {
        UIStyleHelper.drawBeveledPanel(context, x, y, width, height, backgroundColor, borderColor, UITheme.PANEL_INNER_BORDER);
    }

    private void drawGalleryBackdrop(DrawContext context, int x, int y, int width, int height) {
        int dotColor = 0x182E2E2E;
        int lineColor = 0x102A2A2A;
        for (int lineX = x + 16; lineX < x + width; lineX += 40) {
            context.drawVerticalLine(lineX, y + 4, y + height - 5, lineColor);
        }
        for (int lineY = y + 12; lineY < y + height; lineY += 40) {
            context.drawHorizontalLine(x + 4, x + width - 5, lineY, lineColor);
        }
        for (int dotX = x + 16; dotX < x + width - 4; dotX += 20) {
            for (int dotY = y + 12; dotY < y + height - 4; dotY += 20) {
                context.fill(dotX, dotY, dotX + 1, dotY + 1, dotColor);
            }
        }
    }

    private void renderFooter(DrawContext context, int mouseX, int mouseY, Layout layout) {
        int footerY = layout.sectionY + layout.sectionHeight - FOOTER_HEIGHT;
        context.drawHorizontalLine(layout.sectionX, layout.sectionX + layout.sectionWidth - 1, footerY, UITheme.BORDER_SUBTLE);

        int footerBottom = this.height - OUTER_PADDING;
        int centerY = footerY + Math.max(6, (footerBottom - footerY - this.textRenderer.fontHeight) / 2 + 8);
        int centerX = layout.sectionX + layout.sectionWidth / 2;
        boolean canGoPrev = pageIndex > 0;
        boolean canGoNext = pageIndex < getMaxPageIndex();

        int leftArrowWidth = this.textRenderer.getWidth("<");
        int rightArrowWidth = this.textRenderer.getWidth(">");
        int prevPageWidth = getPageLabelWidth(pageIndex);
        int currentPageWidth = getPageLabelWidth(pageIndex + 1);
        int nextPageWidth = getPageLabelWidth(pageIndex + 2);
        int totalWidth = leftArrowWidth + PAGE_CONTROL_GAP + prevPageWidth + PAGE_NUMBER_GAP + currentPageWidth
            + PAGE_NUMBER_GAP + nextPageWidth + PAGE_CONTROL_GAP + rightArrowWidth;
        int cursorX = centerX - totalWidth / 2;

        int leftArrowX = cursorX;
        int leftArrowColor = canGoPrev && isPointInRect(mouseX, mouseY, leftArrowX - 2, centerY - 2, leftArrowWidth + 4, 12)
            ? UITheme.TEXT_HEADER : canGoPrev ? UITheme.TEXT_SECONDARY : UITheme.TEXT_TERTIARY;
        context.drawTextWithShadow(this.textRenderer, Text.literal("<"), leftArrowX, centerY, leftArrowColor);
        cursorX += leftArrowWidth + PAGE_CONTROL_GAP;

        if (pageIndex > 0) {
            context.drawTextWithShadow(this.textRenderer, Text.literal(Integer.toString(pageIndex)), cursorX, centerY, UITheme.TEXT_SECONDARY);
        }
        cursorX += prevPageWidth + PAGE_NUMBER_GAP;

        context.drawTextWithShadow(this.textRenderer, Text.literal(Integer.toString(pageIndex + 1)), cursorX, centerY, getAccentColor());
        cursorX += currentPageWidth + PAGE_NUMBER_GAP;

        if (pageIndex < getMaxPageIndex()) {
            context.drawTextWithShadow(this.textRenderer, Text.literal(Integer.toString(pageIndex + 2)), cursorX, centerY, UITheme.TEXT_SECONDARY);
        }
        cursorX += nextPageWidth + PAGE_CONTROL_GAP;

        int rightArrowColor = canGoNext && isPointInRect(mouseX, mouseY, cursorX - 2, centerY - 2, rightArrowWidth + 4, 12)
            ? UITheme.TEXT_HEADER : canGoNext ? UITheme.TEXT_SECONDARY : UITheme.TEXT_TERTIARY;
        context.drawTextWithShadow(this.textRenderer, Text.literal(">"), cursorX, centerY, rightArrowColor);
    }

    private void renderPresetPopup(DrawContext context, int mouseX, int mouseY, Layout layout) {
        PopupLayout popup = getPopupLayout(layout);
        context.fill(0, 0, this.width, this.height, presetPopupAnimation.getAnimatedBackgroundColor(UITheme.OVERLAY_BACKGROUND));
        int[] bounds = presetPopupAnimation.getScaledPopupBounds(this.width, this.height, popup.width, popup.height);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int popupWidth = bounds[2];
        int popupHeight = bounds[3];
        if (popupWidth <= 0 || popupHeight <= 0 || popupPreset == null) {
            return;
        }
        UIStyleHelper.drawBeveledPanel(
            context,
            popupX,
            popupY,
            popupWidth,
            popupHeight,
            presetPopupAnimation.getAnimatedPopupColor(UITheme.BACKGROUND_SECONDARY),
            presetPopupAnimation.getAnimatedPopupColor(UITheme.BORDER_DEFAULT),
            presetPopupAnimation.getAnimatedPopupColor(UITheme.PANEL_INNER_BORDER)
        );

        context.drawTextWithShadow(this.textRenderer, Text.literal("Preset Details"), popupX + 12, popupY + 10,
            presetPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_HEADER));
        context.drawHorizontalLine(popupX, popupX + popupWidth - 1, popupY + 28,
            presetPopupAnimation.getAnimatedPopupColor(UITheme.BORDER_SUBTLE));

        int contentTop = popupY + 40;
        int contentBottom = popupY + popupHeight - 48;
        int contentHeight = Math.max(24, contentBottom - contentTop);
        int previewX = popupX + 12;
        int previewY = contentTop - popupScrollOffset;
        int previewWidth = popupWidth - 24;
        int previewHeight = 120;
        int textX = popupX + 12;
        int textWidth = popupWidth - 24;
        int contentHeightTotal = measurePopupContentHeight(textWidth);
        int maxPopupScroll = Math.max(0, contentHeightTotal - contentHeight);
        popupScrollOffset = Math.max(0, Math.min(popupScrollOffset, maxPopupScroll));

        context.enableScissor(popupX + 8, contentTop, popupX + popupWidth - 8, contentBottom);
        UIStyleHelper.drawBeveledPanel(
            context,
            previewX,
            previewY,
            previewWidth,
            previewHeight,
            presetPopupAnimation.getAnimatedPopupColor(UITheme.BACKGROUND_PRIMARY),
            presetPopupAnimation.getAnimatedPopupColor(UITheme.BORDER_SUBTLE),
            presetPopupAnimation.getAnimatedPopupColor(UITheme.PANEL_INNER_BORDER)
        );
        drawGraphPreview(context, previewX, previewY, previewWidth, previewHeight);

        int cursorY = previewY + previewHeight + 12;
        context.drawTextWithShadow(this.textRenderer,
            Text.literal(TextRenderUtil.trimWithEllipsis(this.textRenderer, popupPreset.getName(), textWidth)),
            textX, cursorY, presetPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_HEADER));
        cursorY += 14;
        cursorY = drawWrappedValue(context, textX, cursorY, textWidth, "Author: " + fallback(popupPreset.getAuthorName(), "Unknown"),
            presetPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_SECONDARY), 2);
        cursorY = drawWrappedValue(context, textX, cursorY, textWidth, "Tags: " + formatTags(popupPreset.getTags()),
            presetPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_SECONDARY), 2);
        cursorY = drawWrappedValue(context, textX, cursorY, textWidth, "Game: " + fallback(popupPreset.getGameVersion(), "Any"),
            presetPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_SECONDARY), 2);
        cursorY = drawWrappedValue(context, textX, cursorY, textWidth, "Pathmind: " + fallback(popupPreset.getPathmindVersion(), "Unknown"),
            presetPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_SECONDARY), 2);
        CompatibilityStatus compatibility = getCompatibilityStatus(popupPreset);
        cursorY += 4;
        context.drawTextWithShadow(this.textRenderer, Text.literal("Compatibility"), textX, cursorY,
            presetPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_LABEL));
        cursorY += 12;
        cursorY = drawWrappedValue(context, textX, cursorY, textWidth, compatibility.minecraftLine(),
            presetPopupAnimation.getAnimatedPopupColor(compatibility.minecraftColor()), 2);
        cursorY = drawWrappedValue(context, textX, cursorY, textWidth, compatibility.pathmindLine(),
            presetPopupAnimation.getAnimatedPopupColor(compatibility.pathmindColor()), 2);
        cursorY += 4;
        context.drawTextWithShadow(this.textRenderer, Text.literal("Description"), textX, cursorY,
            presetPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_LABEL));
        cursorY += 12;
        cursorY = drawWrappedValue(context, textX, cursorY, textWidth, fallback(popupPreset.getDescription(), "No description provided."),
            presetPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_PRIMARY), 4);
        context.disableScissor();

        ScrollbarHelper.renderCutoffDividers(
            context,
            popupX + 8,
            popupX + popupWidth - 9,
            contentTop,
            contentBottom,
            popupScrollOffset,
            maxPopupScroll,
            presetPopupAnimation.getAnimatedPopupColor(UITheme.BORDER_SUBTLE)
        );

        if (!popupStatusMessage.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer,
                Text.literal(TextRenderUtil.trimWithEllipsis(this.textRenderer, popupStatusMessage, textWidth)),
                textX, popupY + popupHeight - 40, presetPopupAnimation.getAnimatedPopupColor(popupStatusColor));
        }

        if (maxPopupScroll > 0) {
            ScrollbarHelper.renderSettingsStyle(
                context,
                getPopupScrollMetrics(popupX, popupY, popupWidth, popupHeight),
                presetPopupAnimation.getAnimatedPopupColor(UITheme.BACKGROUND_SIDEBAR),
                presetPopupAnimation.getAnimatedPopupColor(UITheme.BORDER_DEFAULT),
                presetPopupAnimation.getAnimatedPopupColor(UITheme.BORDER_DEFAULT)
            );
        }

        int closeButtonX = popupX + (popup.closeButtonX - popup.x);
        int downloadButtonX = popupX + (popup.downloadButtonX - popup.x);
        int buttonY = popupY + (popup.buttonY - popup.y);
        boolean closeHovered = isPointInRect(mouseX, mouseY, closeButtonX, buttonY, popup.buttonWidth, popup.buttonHeight);
        boolean downloadHovered = isPointInRect(mouseX, mouseY, downloadButtonX, buttonY, popup.buttonWidth, popup.buttonHeight);
        drawActionButton(context, closeButtonX, buttonY, popup.buttonWidth, popup.buttonHeight,
            "Close", closeHovered, false);
        drawActionButton(context, downloadButtonX, buttonY, popup.buttonWidth, popup.buttonHeight,
            importingPreset ? "Downloading..." : "Download", downloadHovered, importingPreset);
    }

    @Override
    public boolean mouseClicked(Click click, boolean inBounds) {
        int mouseX = (int) click.x();
        int mouseY = (int) click.y();
        int button = click.button();
        if (button != 0) {
            return super.mouseClicked(click, inBounds);
        }

        Layout layout = getLayout();
        if (popupPreset != null) {
            PopupLayout popup = getPopupLayout(layout);
            int[] bounds = presetPopupAnimation.getScaledPopupBounds(this.width, this.height, popup.width, popup.height);
            int popupX = bounds[0];
            int popupY = bounds[1];
            int popupWidth = bounds[2];
            int popupHeight = bounds[3];
            ScrollbarHelper.Metrics scrollMetrics = getPopupScrollMetrics(popupX, popupY, popupWidth, popupHeight);
            int closeButtonX = popupX + (popup.closeButtonX - popup.x);
            int downloadButtonX = popupX + (popup.downloadButtonX - popup.x);
            int buttonY = popupY + (popup.buttonY - popup.y);
            if (scrollMetrics.maxScroll() > 0
                && isPointInRect(mouseX, mouseY, scrollMetrics.trackLeft() - 3, scrollMetrics.trackTop(), scrollMetrics.trackWidth() + 6, scrollMetrics.viewportHeight())) {
                popupScrollDragging = true;
                popupScrollDragOffset = mouseY - scrollMetrics.thumbTop();
                return true;
            }
            if (isPointInRect(mouseX, mouseY, closeButtonX, buttonY, popup.buttonWidth, popup.buttonHeight)) {
                closePopup();
                return true;
            }
            if (!importingPreset && isPointInRect(mouseX, mouseY, downloadButtonX, buttonY, popup.buttonWidth, popup.buttonHeight)) {
                startPresetImport();
                return true;
            }
            if (!isPointInRect(mouseX, mouseY, popupX, popupY, popupWidth, popupHeight)) {
                closePopup();
                return true;
            }
            return true;
        }
        if (sortDropdownOpen) {
            if (isPointInRect(mouseX, mouseY, layout.sortButtonX, layout.sortButtonY, SORT_BUTTON_WIDTH, SORT_BUTTON_HEIGHT)) {
                sortDropdownOpen = false;
                return true;
            }
            Rect dropdownBounds = getSortDropdownBounds(layout);
            if (isPointInRect(mouseX, mouseY, dropdownBounds.x, dropdownBounds.y, dropdownBounds.width, dropdownBounds.height)) {
                int optionIndex = Math.max(0, Math.min(SortMode.values().length - 1, (mouseY - dropdownBounds.y) / SORT_OPTION_HEIGHT));
                sortMode = SortMode.values()[optionIndex];
                sortDropdownOpen = false;
                applyFilters();
                return true;
            }
            sortDropdownOpen = false;
        }
        if (isPointInRect(mouseX, mouseY, layout.backButtonX, layout.backButtonY, BACK_BUTTON_SIZE, BACK_BUTTON_SIZE)) {
            close();
            return true;
        }
        if (searchField != null && isPointInRect(mouseX, mouseY, layout.searchFieldX, layout.searchFieldY, SEARCH_FIELD_WIDTH, SEARCH_FIELD_HEIGHT)) {
            searchField.setFocused(true);
            searchField.mouseClicked(click, inBounds);
            return true;
        }
        if (searchField != null) {
            searchField.setFocused(false);
        }
        if (isPointInRect(mouseX, mouseY, layout.sortButtonX, layout.sortButtonY, SORT_BUTTON_WIDTH, SORT_BUTTON_HEIGHT)) {
            sortDropdownOpen = !sortDropdownOpen;
            return true;
        }
        if (!loading && isPointInRect(mouseX, mouseY, layout.refreshButtonX, layout.refreshButtonY, REFRESH_BUTTON_SIZE, REFRESH_BUTTON_SIZE)) {
            refreshListings();
            return true;
        }

        PageHitAreas pageHits = getPageHitAreas(layout);
        if (pageHits.leftArrow() != null && isPointInRect(mouseX, mouseY, pageHits.leftArrow().x, pageHits.leftArrow().y, pageHits.leftArrow().width, pageHits.leftArrow().height)) {
            pageIndex = Math.max(0, pageIndex - 1);
            return true;
        }
        if (pageHits.rightArrow() != null && isPointInRect(mouseX, mouseY, pageHits.rightArrow().x, pageHits.rightArrow().y, pageHits.rightArrow().width, pageHits.rightArrow().height)) {
            pageIndex = Math.min(getMaxPageIndex(), pageIndex + 1);
            return true;
        }

        int cardsPerPage = getCardsPerPage(layout);
        int startIndex = pageIndex * cardsPerPage;
        int endIndex = Math.min(presets.size(), startIndex + cardsPerPage);
        int visibleCount = Math.max(0, endIndex - startIndex);
        for (int index = startIndex; index < endIndex; index++) {
            Rect rect = getCardRect(layout, index - startIndex, visibleCount);
            if (isPointInRect(mouseX, mouseY, rect.x, rect.y, rect.width, rect.height)) {
                selectedIndex = index;
                popupPreset = presets.get(index);
                popupScrollOffset = 0;
                popupStatusMessage = "";
                popupStatusColor = UITheme.TEXT_SECONDARY;
                presetPopupAnimation.show();
                return true;
            }
        }

        return super.mouseClicked(click, inBounds);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (popupScrollDragging && popupPreset != null) {
            Layout layout = getLayout();
            PopupLayout popup = getPopupLayout(layout);
            int[] bounds = presetPopupAnimation.getScaledPopupBounds(this.width, this.height, popup.width, popup.height);
            ScrollbarHelper.Metrics scrollMetrics = getPopupScrollMetrics(bounds[0], bounds[1], bounds[2], bounds[3]);
            if (scrollMetrics.maxScroll() <= 0) {
                return true;
            }
            int desiredThumbY = (int) click.y() - popupScrollDragOffset;
            int minThumbY = scrollMetrics.trackTop();
            int maxThumbY = scrollMetrics.trackTop() + Math.max(0, scrollMetrics.viewportHeight() - scrollMetrics.thumbHeight());
            int clampedThumbY = Math.max(minThumbY, Math.min(maxThumbY, desiredThumbY));
            popupScrollOffset = ScrollbarHelper.scrollFromThumb(scrollMetrics, clampedThumbY);
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (popupScrollDragging) {
            popupScrollDragging = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        Layout layout = getLayout();
        if (popupPreset != null) {
            PopupLayout popup = getPopupLayout(layout);
            int[] bounds = presetPopupAnimation.getScaledPopupBounds(this.width, this.height, popup.width, popup.height);
            ScrollbarHelper.Metrics scrollMetrics = getPopupScrollMetrics(bounds[0], bounds[1], bounds[2], bounds[3]);
            if (scrollMetrics.maxScroll() > 0 && isPointInRect((int) mouseX, (int) mouseY, bounds[0] + 8, scrollMetrics.trackTop(), bounds[2] - 16, scrollMetrics.viewportHeight())) {
                int nextOffset = ScrollbarHelper.applyWheel(popupScrollOffset, verticalAmount, 18, scrollMetrics.maxScroll());
                if (nextOffset != popupScrollOffset) {
                    popupScrollOffset = nextOffset;
                    return true;
                }
            }
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        if (sortDropdownOpen) {
            Rect dropdownBounds = getSortDropdownBounds(layout);
            if (isPointInRect((int) mouseX, (int) mouseY, dropdownBounds.x, dropdownBounds.y, dropdownBounds.width, dropdownBounds.height)) {
                return true;
            }
        }
        if (!isPointInRect((int) mouseX, (int) mouseY, layout.sectionX, layout.sectionY, layout.sectionWidth, layout.sectionHeight)) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        int maxPage = getMaxPageIndex();
        if (verticalAmount < 0 && pageIndex < maxPage) {
            pageIndex++;
            return true;
        }
        if (verticalAmount > 0 && pageIndex > 0) {
            pageIndex--;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void close() {
        if (popupPreset != null) {
            closePopup();
            return;
        }
        sortDropdownOpen = false;
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (sortDropdownOpen) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE || input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
                sortDropdownOpen = false;
                return true;
            }
        }
        if (searchField != null && searchField.isFocused()) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                searchField.setFocused(false);
                return true;
            }
            if (searchField.keyPressed(input)) {
                return true;
            }
        }
        if (input.key() == 256) { // ESC
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (searchField != null && searchField.isFocused() && searchField.charTyped(input)) {
            return true;
        }
        return super.charTyped(input);
    }

    private void drawIconButton(DrawContext context, int x, int y, int width, int height, boolean hovered, boolean disabled) {
        int bgColor = disabled ? UITheme.TOOLBAR_BG_DISABLED : hovered ? UITheme.TOOLBAR_BG_HOVER : UITheme.BACKGROUND_SECTION;
        int borderColor = disabled ? UITheme.BORDER_SUBTLE : hovered ? getAccentColor() : UITheme.BORDER_SUBTLE;
        UIStyleHelper.drawToolbarButtonFrame(context, x, y, width, height, bgColor, borderColor, UITheme.PANEL_INNER_BORDER);
    }

    private void drawActionButton(DrawContext context, int x, int y, int width, int height, String label, boolean hovered, boolean disabled) {
        int bgColor = disabled ? UITheme.TOOLBAR_BG_DISABLED : hovered ? UITheme.TOOLBAR_BG_HOVER : UITheme.TOOLBAR_BG;
        int borderColor = disabled ? UITheme.BORDER_SUBTLE : hovered ? getAccentColor() : UITheme.BORDER_DEFAULT;
        UIStyleHelper.drawToolbarButtonFrame(context, x, y, width, height, bgColor, borderColor, UITheme.PANEL_INNER_BORDER);
        int textColor = disabled ? UITheme.TEXT_TERTIARY : hovered ? UITheme.TEXT_HEADER : UITheme.TEXT_PRIMARY;
        int textX = x + (width - this.textRenderer.getWidth(label)) / 2;
        int textY = y + (height - this.textRenderer.fontHeight) / 2;
        context.drawTextWithShadow(this.textRenderer, Text.literal(label), textX, textY, textColor);
    }

    private void renderSortDropdown(DrawContext context, int mouseX, int mouseY, Layout layout) {
        float animProgress = AnimationHelper.easeOutQuad(sortDropdownAnimation.getValue());
        if (animProgress <= 0.001f) {
            return;
        }

        Rect dropdownBounds = getSortDropdownBounds(layout);
        int animatedHeight = Math.max(1, (int) (dropdownBounds.height * animProgress));
        context.enableScissor(dropdownBounds.x, dropdownBounds.y, dropdownBounds.x + dropdownBounds.width, dropdownBounds.y + animatedHeight);
        UIStyleHelper.drawBeveledPanel(
            context,
            dropdownBounds.x,
            dropdownBounds.y,
            dropdownBounds.width,
            dropdownBounds.height,
            UITheme.BACKGROUND_SECONDARY,
            UITheme.BORDER_DEFAULT,
            UITheme.PANEL_INNER_BORDER
        );

        SortMode[] modes = SortMode.values();
        for (int i = 0; i < modes.length; i++) {
            int optionY = dropdownBounds.y + i * SORT_OPTION_HEIGHT;
            boolean hovered = animProgress >= 1f
                && isPointInRect(mouseX, mouseY, dropdownBounds.x + 1, optionY + 1, dropdownBounds.width - 2, SORT_OPTION_HEIGHT - 1);
            int optionColor = hovered ? UITheme.DROPDOWN_OPTION_HOVER : UITheme.DROPDOWN_OPTION_BG;
            context.fill(dropdownBounds.x + 1, optionY + 1, dropdownBounds.x + dropdownBounds.width - 1, optionY + SORT_OPTION_HEIGHT, optionColor);
            if (i > 0) {
                context.drawHorizontalLine(dropdownBounds.x + 1, dropdownBounds.x + dropdownBounds.width - 2, optionY, UITheme.BORDER_SUBTLE);
            }
            int textColor = modes[i] == sortMode ? getAccentColor() : UITheme.TEXT_PRIMARY;
            context.drawTextWithShadow(this.textRenderer, Text.literal(modes[i].label), dropdownBounds.x + 8, optionY + 5, textColor);
        }
        context.disableScissor();
    }

    private int drawWrappedValue(DrawContext context, int x, int y, int width, String value, int color, int maxLines) {
        for (String line : wrapText(value, width, maxLines)) {
            context.drawTextWithShadow(this.textRenderer, Text.literal(line), x, y, color);
            y += 10;
        }
        return y + 2;
    }

    private int measureWrappedValueHeight(int width, String value, int maxLines) {
        return wrapText(value, width, maxLines).size() * 10 + 2;
    }

    private int measurePopupContentHeight(int textWidth) {
        if (popupPreset == null) {
            return 0;
        }
        int height = 120 + 12;
        height += 14;
        height += measureWrappedValueHeight(textWidth, "Author: " + fallback(popupPreset.getAuthorName(), "Unknown"), 2);
        height += measureWrappedValueHeight(textWidth, "Tags: " + formatTags(popupPreset.getTags()), 2);
        height += measureWrappedValueHeight(textWidth, "Game: " + fallback(popupPreset.getGameVersion(), "Any"), 2);
        height += measureWrappedValueHeight(textWidth, "Pathmind: " + fallback(popupPreset.getPathmindVersion(), "Unknown"), 2);
        CompatibilityStatus compatibility = getCompatibilityStatus(popupPreset);
        height += 4 + 12;
        height += measureWrappedValueHeight(textWidth, compatibility.minecraftLine(), 2);
        height += measureWrappedValueHeight(textWidth, compatibility.pathmindLine(), 2);
        height += 4 + 12;
        height += measureWrappedValueHeight(textWidth, fallback(popupPreset.getDescription(), "No description provided."), 4);
        return height;
    }

    private ScrollbarHelper.Metrics getPopupScrollMetrics(int popupX, int popupY, int popupWidth, int popupHeight) {
        int contentTop = popupY + 40;
        int contentBottom = popupY + popupHeight - 48;
        int contentHeight = Math.max(24, contentBottom - contentTop);
        int contentHeightTotal = measurePopupContentHeight(Math.max(32, popupWidth - 24));
        int maxScroll = Math.max(0, contentHeightTotal - contentHeight);
        return ScrollbarHelper.metrics(popupX + popupWidth - 12, contentTop, 4, contentHeight, maxScroll, popupScrollOffset, 20);
    }

    private List<String> wrapText(String text, int maxWidth, int maxLines) {
        String value = text == null || text.isBlank() ? "" : text.trim();
        if (value.isEmpty()) {
            return List.of("");
        }

        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        String[] words = value.split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (this.textRenderer.getWidth(candidate) <= maxWidth) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
                if (lines.size() >= maxLines) {
                    break;
                }
                line.setLength(0);
            }
            line.append(TextRenderUtil.trimWithEllipsis(this.textRenderer, word, maxWidth));
        }
        if (!line.isEmpty() && lines.size() < maxLines) {
            lines.add(line.toString());
        }
        if (lines.size() == maxLines) {
            int last = lines.size() - 1;
            lines.set(last, TextRenderUtil.trimWithEllipsis(this.textRenderer, lines.get(last), maxWidth));
        }
        return lines;
    }

    private void closePopup() {
        if (!importingPreset) {
            presetPopupAnimation.hide();
            popupPreset = null;
            popupScrollOffset = 0;
            popupScrollDragging = false;
            popupScrollDragOffset = 0;
            popupStatusMessage = "";
            popupStatusColor = UITheme.TEXT_SECONDARY;
        }
    }

    private void startPresetImport() {
        if (popupPreset == null || importingPreset) {
            return;
        }
        importingPreset = true;
        popupStatusMessage = "Downloading preset...";
        popupStatusColor = UITheme.TEXT_SECONDARY;

        MarketplaceService.downloadPresetToTempFile(popupPreset).whenComplete((path, throwable) -> {
            if (this.client == null) {
                return;
            }
            this.client.execute(() -> finishPresetImport(path, throwable));
        });
    }

    private void finishPresetImport(Path path, Throwable throwable) {
        if (throwable != null || path == null) {
            importingPreset = false;
            popupStatusMessage = "Download failed.";
            popupStatusColor = UITheme.STATE_ERROR;
            cleanupTempFile(path);
            return;
        }

        try {
            NodeGraphData data = NodeGraphPersistence.loadNodeGraphFromPath(path);
            if (data == null) {
                importingPreset = false;
                popupStatusMessage = "Downloaded file is not a valid preset.";
                popupStatusColor = UITheme.STATE_ERROR;
                cleanupTempFile(path);
                return;
            }

            String requestedName = fallback(popupPreset.getSlug(), popupPreset.getName());
            java.util.Optional<String> importedPreset = PresetManager.importPresetFromFile(path, requestedName);
            if (importedPreset.isEmpty()) {
                importingPreset = false;
                popupStatusMessage = "Failed to import preset.";
                popupStatusColor = UITheme.STATE_ERROR;
                cleanupTempFile(path);
                return;
            }

            PresetManager.setActivePreset(importedPreset.get());
            cleanupTempFile(path);
            importingPreset = false;
            popupStatusMessage = "Imported \"" + importedPreset.get() + "\".";
            popupStatusColor = getAccentColor();
            presetPopupAnimation.hide();
            popupPreset = null;
            if (this.client != null) {
                PathmindScreens.openVisualEditorOrWarn(this.client, parent);
            }
        } catch (Exception e) {
            importingPreset = false;
            popupStatusMessage = "Import failed.";
            popupStatusColor = UITheme.STATE_ERROR;
            cleanupTempFile(path);
        }
    }

    private void cleanupTempFile(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
        }
    }

    private int getAccentColor() {
        SettingsManager.Settings settings = SettingsManager.load();
        if (settings == null || settings.accentColor == null) {
            return UITheme.ACCENT_SKY;
        }
        switch (settings.accentColor.toLowerCase()) {
            case "mint":
                return UITheme.ACCENT_MINT;
            case "amber":
                return UITheme.ACCENT_AMBER;
            case "sky":
            default:
                return UITheme.ACCENT_SKY;
        }
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void drawBackArrow(DrawContext context, int x, int y, int color) {
        int centerY = y + BACK_BUTTON_SIZE / 2;
        context.drawHorizontalLine(x + 5, x + 11, centerY, color);
        context.drawHorizontalLine(x + 8, x + 11, centerY - 1, color);
        context.drawHorizontalLine(x + 10, x + 12, centerY - 2, color);
        context.drawHorizontalLine(x + 8, x + 11, centerY + 1, color);
        context.drawHorizontalLine(x + 10, x + 12, centerY + 2, color);
    }

    private void drawRefreshIcon(DrawContext context, int x, int y, int color) {
        int centerX = x + REFRESH_BUTTON_SIZE / 2;
        int centerY = y + REFRESH_BUTTON_SIZE / 2;
        context.drawHorizontalLine(centerX - 3, centerX + 1, centerY - 3, color);
        context.drawVerticalLine(centerX - 4, centerY - 2, centerY, color);
        context.drawHorizontalLine(centerX - 2, centerX + 2, centerY + 3, color);
        context.drawVerticalLine(centerX + 3, centerY - 1, centerY + 2, color);
        context.drawHorizontalLine(centerX + 1, centerX + 3, centerY - 4, color);
        context.drawHorizontalLine(centerX - 4, centerX - 2, centerY + 4, color);
    }

    private void drawSearchIcon(DrawContext context, int x, int y, int color) {
        context.drawHorizontalLine(x + 1, x + 5, y + 5, color);
        context.drawVerticalLine(x, y + 2, y + 4, color);
        context.drawVerticalLine(x + 6, y + 2, y + 4, color);
        context.drawHorizontalLine(x + 2, x + 4, y + 1, color);
        context.drawHorizontalLine(x + 2, x + 4, y + 9, color);
        context.drawVerticalLine(x + 7, y + 7, y + 8, color);
        context.drawHorizontalLine(x + 8, x + 9, y + 9, color);
    }

    private void drawDropdownChevron(DrawContext context, int x, int y, int color, boolean open) {
        if (open) {
            context.drawHorizontalLine(x, x + 4, y + 2, color);
            context.drawHorizontalLine(x + 1, x + 3, y + 1, color);
            context.drawHorizontalLine(x + 2, x + 2, y, color);
            return;
        }
        context.drawHorizontalLine(x, x + 4, y, color);
        context.drawHorizontalLine(x + 1, x + 3, y + 1, color);
        context.drawHorizontalLine(x + 2, x + 2, y + 2, color);
    }

    private Layout getLayout() {
        int topBarY = OUTER_PADDING;
        int backButtonX = OUTER_PADDING;
        int backButtonY = topBarY + 2;

        int sectionX = OUTER_PADDING;
        int sectionY = topBarY + TOP_BAR_HEIGHT + SECTION_TOP_GAP;
        int sectionWidth = this.width - OUTER_PADDING * 2;
        int sectionHeight = this.height - sectionY - OUTER_PADDING;
        int bodyX = sectionX + SECTION_BODY_PADDING;
        int bodyWidth = sectionWidth - SECTION_BODY_PADDING * 2;

        int refreshButtonX = this.width - OUTER_PADDING - REFRESH_BUTTON_SIZE;
        int refreshButtonY = topBarY + 2;
        int searchFieldX = bodyX;
        int searchFieldY = sectionY + 5;
        int sortButtonX = searchFieldX + SEARCH_FIELD_WIDTH + 8;
        int sortButtonY = searchFieldY;

        return new Layout(topBarY, backButtonX, backButtonY, sectionX, sectionY, sectionWidth, sectionHeight,
            bodyX, bodyWidth, refreshButtonX, refreshButtonY, searchFieldX, searchFieldY, sortButtonX, sortButtonY);
    }

    private PopupLayout getPopupLayout(Layout layout) {
        int width = Math.min(360, this.width - 40);
        int height = Math.min(330, this.height - 40);
        int x = (this.width - width) / 2;
        int y = (this.height - height) / 2;
        int buttonWidth = 86;
        int buttonHeight = 18;
        int buttonY = y + height - 28;
        int closeButtonX = x + width - buttonWidth * 2 - 16;
        int downloadButtonX = x + width - buttonWidth - 10;
        return new PopupLayout(x, y, width, height, closeButtonX, downloadButtonX, buttonY, buttonWidth, buttonHeight);
    }

    private Rect getCardRect(Layout layout, int pageOffset, int visibleCount) {
        int columns = getGridColumns(layout);
        int bodyY = layout.sectionY + SECTION_HEADER_HEIGHT - 4;
        int availableWidth = layout.bodyWidth;
        int cardWidth = Math.min(CARD_MAX_WIDTH, (availableWidth - (columns - 1) * CARD_GAP) / columns);
        int column = pageOffset % columns;
        int row = pageOffset / columns;
        int itemsBeforeRow = row * columns;
        int itemsInRow = Math.max(1, Math.min(columns, visibleCount - itemsBeforeRow));
        int startX = layout.bodyX;
        return new Rect(
            startX + column * (cardWidth + CARD_GAP),
            bodyY + row * (CARD_SIZE + CARD_GAP),
            cardWidth,
            CARD_SIZE
        );
    }

    private int getGridColumns(Layout layout) {
        int columns = Math.max(1, (layout.bodyWidth + CARD_GAP) / (CARD_MIN_WIDTH + CARD_GAP));
        return Math.max(1, columns);
    }

    private int getGridRows(Layout layout) {
        int availableHeight = layout.sectionHeight - SECTION_HEADER_HEIGHT - FOOTER_HEIGHT - 8;
        return Math.max(1, (availableHeight + CARD_GAP) / (CARD_SIZE + CARD_GAP));
    }

    private int getCardsPerPage(Layout layout) {
        return Math.max(1, getGridColumns(layout) * getGridRows(layout));
    }

    private int getMaxPageIndex() {
        Layout layout = getLayout();
        int cardsPerPage = getCardsPerPage(layout);
        return Math.max(0, (presets.size() - 1) / cardsPerPage);
    }

    private int getPageLabelWidth(int pageNumber) {
        if (pageNumber <= 0 || pageNumber > getMaxPageIndex() + 1) {
            return 0;
        }
        return this.textRenderer.getWidth(Integer.toString(pageNumber));
    }

    private Rect getSortDropdownBounds(Layout layout) {
        return new Rect(layout.sortButtonX, layout.sortButtonY + SORT_BUTTON_HEIGHT, SORT_BUTTON_WIDTH, SortMode.values().length * SORT_OPTION_HEIGHT);
    }

    private PageHitAreas getPageHitAreas(Layout layout) {
        int footerY = layout.sectionY + layout.sectionHeight - FOOTER_HEIGHT;
        int centerY = footerY + 10;
        int centerX = layout.sectionX + layout.sectionWidth / 2;
        int leftArrowWidth = this.textRenderer.getWidth("<");
        int rightArrowWidth = this.textRenderer.getWidth(">");
        int prevPageWidth = getPageLabelWidth(pageIndex);
        int currentPageWidth = getPageLabelWidth(pageIndex + 1);
        int nextPageWidth = getPageLabelWidth(pageIndex + 2);
        int totalWidth = leftArrowWidth + PAGE_CONTROL_GAP + prevPageWidth + PAGE_NUMBER_GAP + currentPageWidth
            + PAGE_NUMBER_GAP + nextPageWidth + PAGE_CONTROL_GAP + rightArrowWidth;
        int cursorX = centerX - totalWidth / 2;

        Rect leftArrow = pageIndex > 0 ? new Rect(cursorX - 2, centerY - 2, leftArrowWidth + 4, 12) : null;
        cursorX += leftArrowWidth + PAGE_CONTROL_GAP + prevPageWidth + PAGE_NUMBER_GAP + currentPageWidth + PAGE_NUMBER_GAP + nextPageWidth + PAGE_CONTROL_GAP;
        Rect rightArrow = pageIndex < getMaxPageIndex() ? new Rect(cursorX - 2, centerY - 2, rightArrowWidth + 4, 12) : null;
        return new PageHitAreas(leftArrow, rightArrow);
    }

    private static String formatTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "No tags";
        }
        return String.join(", ", tags);
    }

    private void applyFilters() {
        String query = searchField == null ? "" : normalizeSearch(searchField.getText());
        List<MarketplacePreset> filtered = new ArrayList<>();
        for (MarketplacePreset preset : allPresets) {
            if (query.isEmpty() || matchesQuery(preset, query)) {
                filtered.add(preset);
            }
        }
        filtered.sort(sortMode.comparator);
        presets = List.copyOf(filtered);
        pageIndex = Math.max(0, Math.min(pageIndex, getMaxPageIndex()));
        selectedIndex = presets.isEmpty() ? -1 : Math.max(0, Math.min(selectedIndex, presets.size() - 1));
        if (allPresets.isEmpty()) {
            statusMessage = "No published presets found.";
        } else if (presets.isEmpty()) {
            statusMessage = "No presets match your search.";
        } else {
            statusMessage = "Loaded " + presets.size() + " preset" + (presets.size() == 1 ? "" : "s") + ".";
        }
    }

    private boolean matchesQuery(MarketplacePreset preset, String query) {
        if (containsNormalized(preset.getName(), query)
            || containsNormalized(preset.getSlug(), query)
            || containsNormalized(preset.getAuthorName(), query)
            || containsNormalized(preset.getDescription(), query)) {
            return true;
        }
        for (String tag : preset.getTags()) {
            if (containsNormalized(tag, query)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsNormalized(String value, String query) {
        return value != null && normalizeSearch(value).contains(query);
    }

    private String normalizeSearch(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private CompatibilityStatus getCompatibilityStatus(MarketplacePreset preset) {
        String currentMinecraftVersion = this.client != null ? this.client.getGameVersion() : "Unknown";
        String currentPathmindVersion = getInstalledPathmindVersion();

        String presetMinecraftVersion = fallback(preset.getGameVersion(), "Any");
        boolean minecraftCompatible = isVersionLooseMatch(presetMinecraftVersion, currentMinecraftVersion)
            || isAnyVersion(presetMinecraftVersion);
        String minecraftLine = minecraftCompatible
            ? "Minecraft: compatible with " + currentMinecraftVersion
            : "Minecraft: built for " + presetMinecraftVersion + ", you are on " + currentMinecraftVersion;

        String presetPathmindVersion = fallback(preset.getPathmindVersion(), "Unknown");
        boolean pathmindCompatible = isVersionLooseMatch(presetPathmindVersion, currentPathmindVersion)
            || isAnyVersion(presetPathmindVersion)
            || "current".equalsIgnoreCase(presetPathmindVersion);
        String pathmindLine = pathmindCompatible
            ? "Pathmind: compatible with " + currentPathmindVersion
            : "Pathmind: built for " + presetPathmindVersion + ", you have " + currentPathmindVersion;

        return new CompatibilityStatus(
            minecraftLine,
            minecraftCompatible ? getAccentColor() : UITheme.STATE_WARNING,
            pathmindLine,
            pathmindCompatible ? getAccentColor() : UITheme.STATE_WARNING
        );
    }

    private boolean isAnyVersion(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() || normalized.equals("any") || normalized.equals("unknown");
    }

    private boolean isVersionLooseMatch(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        String normalizedExpected = expected.trim().toLowerCase(Locale.ROOT);
        String normalizedActual = actual.trim().toLowerCase(Locale.ROOT);
        if (normalizedExpected.isEmpty() || normalizedActual.isEmpty()) {
            return false;
        }
        return normalizedActual.equals(normalizedExpected)
            || normalizedActual.startsWith(normalizedExpected + "+")
            || normalizedExpected.startsWith(normalizedActual + "+")
            || normalizedActual.contains(normalizedExpected)
            || normalizedExpected.contains(normalizedActual);
    }

    private String getInstalledPathmindVersion() {
        Optional<String> version = FabricLoader.getInstance()
            .getModContainer(PathmindMod.MOD_ID)
            .map(container -> container.getMetadata().getVersion().getFriendlyString());
        return version.orElse("Unknown");
    }

    private static boolean isPointInRect(int x, int y, int rectX, int rectY, int width, int height) {
        return x >= rectX && y >= rectY && x < rectX + width && y < rectY + height;
    }

    private record Rect(int x, int y, int width, int height) {
    }

    private record PageHitAreas(Rect leftArrow, Rect rightArrow) {
    }

    private record Layout(
        int topBarY,
        int backButtonX,
        int backButtonY,
        int sectionX,
        int sectionY,
        int sectionWidth,
        int sectionHeight,
        int bodyX,
        int bodyWidth,
        int refreshButtonX,
        int refreshButtonY,
        int searchFieldX,
        int searchFieldY,
        int sortButtonX,
        int sortButtonY
    ) {
    }

    private record PopupLayout(
        int x,
        int y,
        int width,
        int height,
        int closeButtonX,
        int downloadButtonX,
        int buttonY,
        int buttonWidth,
        int buttonHeight
    ) {
    }

    private record CompatibilityStatus(
        String minecraftLine,
        int minecraftColor,
        String pathmindLine,
        int pathmindColor
    ) {
    }

    private enum SortMode {
        NEWEST("Newest", Comparator.comparing((MarketplacePreset preset) -> fallbackStatic(preset.getCreatedAt(), "")).reversed()),
        NAME("Name", Comparator.comparing((MarketplacePreset preset) -> fallbackStatic(preset.getName(), "").toLowerCase(Locale.ROOT))),
        AUTHOR("Author", Comparator.comparing((MarketplacePreset preset) -> fallbackStatic(preset.getAuthorName(), "").toLowerCase(Locale.ROOT)));

        private final String label;
        private final Comparator<MarketplacePreset> comparator;

        SortMode(String label, Comparator<MarketplacePreset> comparator) {
            this.label = label;
            this.comparator = comparator;
        }

        private static String fallbackStatic(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value;
        }
    }
}
