package com.pathmind.screen;

import com.pathmind.PathmindMod;
import com.pathmind.data.NodeGraphData;
import com.pathmind.data.NodeGraphPersistence;
import com.pathmind.data.PresetManager;
import com.pathmind.data.SettingsManager;
import com.pathmind.marketplace.MarketplaceAuthManager;
import com.pathmind.marketplace.MarketplacePreset;
import com.pathmind.marketplace.MarketplaceRateLimitManager;
import com.pathmind.marketplace.MarketplaceService;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeParameter;
import com.pathmind.ui.animation.AnimatedValue;
import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.animation.HoverAnimator;
import com.pathmind.ui.animation.PopupAnimationHandler;
import com.pathmind.ui.control.ToggleSwitch;
import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DrawContextBridge;
import com.pathmind.util.MatrixStackBridge;
import com.pathmind.util.ScrollbarHelper;
import com.pathmind.util.TextureCompatibilityBridge;
import com.pathmind.util.TextRenderUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Read-only marketplace browser with a full-window preset gallery.
 */
public class PathmindMarketplaceScreen extends Screen {
    private static final int OUTER_PADDING = 12;
    private static final int TOP_BAR_HEIGHT = 30;
    private static final int SECTION_TOP_GAP = 4;
    private static final int SECTION_HEADER_HEIGHT = 54;
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
    private static final int MY_PRESETS_BUTTON_WIDTH = 86;
    private static final int MY_PRESET_FILTER_BUTTON_HEIGHT = 16;
    private static final int MY_PRESET_FILTER_ALL_WIDTH = 34;
    private static final int MY_PRESET_FILTER_PUBLIC_WIDTH = 50;
    private static final int MY_PRESET_FILTER_PRIVATE_WIDTH = 54;
    private static final int ACCOUNT_BUTTON_WIDTH = SORT_BUTTON_HEIGHT;
    private static final HttpClient AVATAR_HTTP_CLIENT = HttpClient.newHttpClient();

    private final Screen parent;
    private final boolean openPublishOnInit;
    private final String preferredPublishPresetName;
    private final MarketplacePreset initialPopupPreset;
    private final boolean editorPopupMode;

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
    private final PopupAnimationHandler accountPopupAnimation = new PopupAnimationHandler();
    private final PopupAnimationHandler publishPopupAnimation = new PopupAnimationHandler();
    private final PopupAnimationHandler confirmPopupAnimation = new PopupAnimationHandler();
    private final AnimatedValue sortDropdownAnimation = AnimatedValue.forHover();
    private final AnimatedValue popupUpdateHoverAnimation = AnimatedValue.forHover();
    private final AnimatedValue popupDeleteHoverAnimation = AnimatedValue.forHover();
    private MarketplaceAuthManager.AuthSession authSession = null;
    private final Set<String> likedPresetIds = new HashSet<>();
    private final Set<String> savedMarketplacePresetIds = new HashSet<>();
    private boolean authBusy = false;
    private boolean accountPopupOpen = false;
    private boolean publishPopupOpen = false;
    private boolean publishBusy = false;
    private boolean deleteBusy = false;
    private String deletingPresetId = null;
    private String pendingDeleteFallbackPresetName = null;
    private boolean myPresetsOnly = false;
    private MyPresetsFilter myPresetsFilter = MyPresetsFilter.ALL;
    private String avatarTextureUrl = null;
    private Identifier avatarTextureId = null;
    private boolean avatarLoading = false;
    private final Map<String, PreviewGraphModel> previewGraphCache = new HashMap<>();
    private final Set<String> previewGraphLoading = new HashSet<>();
    private final Map<String, Long> likePulseEndTimes = new HashMap<>();
    private final Map<String, Long> savePulseEndTimes = new HashMap<>();
    private final Map<String, Long> deletePulseEndTimes = new HashMap<>();
    private String pendingLikePresetId = null;
    private boolean popupPreviewDragging = false;
    private int popupPreviewDragLastX = 0;
    private int popupPreviewDragLastY = 0;
    private float popupPreviewPanX = 0f;
    private float popupPreviewPanY = 0f;
    private float popupPreviewZoom = 1f;
    private TextFieldWidget searchField;
    private TextFieldWidget publishNameField;
    private TextFieldWidget publishDescriptionField;
    private TextFieldWidget publishTagsField;
    private SortMode sortMode = SortMode.NEWEST;
    private boolean sortDropdownOpen = false;
    private String publishSourcePresetName = "";
    private MarketplacePreset editingPreset = null;
    private boolean popupMetadataEditing = false;
    private String publishStatusMessage = "";
    private int publishStatusColor = UITheme.TEXT_SECONDARY;
    private boolean publishVisibilityPublic = true;
    private final ToggleSwitch publishVisibilityToggle = new ToggleSwitch(true);
    private final ToggleSwitch presetVisibilityToggle = new ToggleSwitch(true);
    private ConfirmAction pendingConfirmAction = null;
    private MarketplacePreset pendingConfirmPreset = null;
    private boolean pendingConfirmDeleteFromPopup = false;
    private ConfirmAction renderConfirmAction = null;
    private boolean confirmPopupClosing = false;
    private boolean presetPopupClosing = false;
    private boolean returnToParentAfterPresetClose = false;
    private boolean skipMarketplaceDeleteConfirm = SettingsManager.getCurrent().skipMarketplaceDeleteConfirm != null
        && SettingsManager.getCurrent().skipMarketplaceDeleteConfirm;
    private boolean skipMarketplaceUpdateConfirm = SettingsManager.getCurrent().skipMarketplaceUpdateConfirm != null
        && SettingsManager.getCurrent().skipMarketplaceUpdateConfirm;

    public PathmindMarketplaceScreen(Screen parent) {
        this(parent, false, null, null);
    }

    public PathmindMarketplaceScreen(Screen parent, boolean openPublishOnInit, String preferredPublishPresetName) {
        this(parent, openPublishOnInit, preferredPublishPresetName, null);
    }

    public PathmindMarketplaceScreen(Screen parent, boolean openPublishOnInit, String preferredPublishPresetName, MarketplacePreset initialPopupPreset) {
        super(Text.literal("Marketplace"));
        this.parent = parent;
        this.openPublishOnInit = openPublishOnInit;
        this.preferredPublishPresetName = preferredPublishPresetName;
        this.initialPopupPreset = initialPopupPreset;
        this.editorPopupMode = parent instanceof PathmindVisualEditorScreen
            && initialPopupPreset != null
            && !openPublishOnInit;
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
        if (publishNameField == null) {
            publishNameField = new TextFieldWidget(this.textRenderer, 0, 0, 240, 18, Text.literal("Preset name"));
            publishNameField.setMaxLength(64);
            publishNameField.setDrawsBackground(false);
            publishNameField.setEditableColor(UITheme.TEXT_PRIMARY);
            publishNameField.setUneditableColor(UITheme.TEXT_TERTIARY);
            this.addSelectableChild(publishNameField);
        }
        if (publishDescriptionField == null) {
            publishDescriptionField = new TextFieldWidget(this.textRenderer, 0, 0, 240, 18, Text.literal("Description"));
            publishDescriptionField.setMaxLength(180);
            publishDescriptionField.setDrawsBackground(false);
            publishDescriptionField.setEditableColor(UITheme.TEXT_PRIMARY);
            publishDescriptionField.setUneditableColor(UITheme.TEXT_TERTIARY);
            this.addSelectableChild(publishDescriptionField);
        }
        if (publishTagsField == null) {
            publishTagsField = new TextFieldWidget(this.textRenderer, 0, 0, 240, 18, Text.literal("Tags"));
            publishTagsField.setMaxLength(96);
            publishTagsField.setDrawsBackground(false);
            publishTagsField.setEditableColor(UITheme.TEXT_PRIMARY);
            publishTagsField.setUneditableColor(UITheme.TEXT_TERTIARY);
            this.addSelectableChild(publishTagsField);
        }
        if (!initialFetchStarted && !editorPopupMode) {
            initialFetchStarted = true;
            refreshListings();
        }
        refreshSavedPresetState();
        refreshAuthState(false);
        if (openPublishOnInit && !publishPopupOpen) {
            openPublishPopup(preferredPublishPresetName);
        } else if (initialPopupPreset != null && popupPreset == null) {
            openPresetPopup(initialPopupPreset, "", UITheme.TEXT_SECONDARY);
        }
    }

    private void refreshListings() {
        loading = true;
        statusMessage = myPresetsOnly ? "Loading your presets..." : "Loading published presets...";
        CompletableFuture<List<MarketplacePreset>> request = myPresetsOnly && authSession != null
            ? MarketplaceService.fetchOwnedPresets(authSession.getAccessToken(), authSession.getUserId())
            : MarketplaceService.fetchPublishedPresets();
        request.whenComplete((results, throwable) -> {
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
                    statusMessage = myPresetsOnly ? "Failed to load your presets." : "Failed to load marketplace presets.";
                    return;
                }

                allPresets = results == null ? List.of() : List.copyOf(results);
                applyFilters();
            });
        });
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int popupMouseX = mouseX;
        int popupMouseY = mouseY;
        if (editorPopupMode && parent != null) {
            parent.render(context, mouseX, mouseY, delta);
        } else {
            context.fill(0, 0, this.width, this.height, UITheme.BACKGROUND_PRIMARY);
        }
        syncVisibilityToggleColors();
        presetPopupAnimation.tick();
        accountPopupAnimation.tick();
        publishPopupAnimation.tick();
        confirmPopupAnimation.tick();
        cleanupClosingPopups();
        sortDropdownAnimation.animateTo(sortDropdownOpen ? 1f : 0f, UITheme.TRANSITION_ANIM_MS, AnimationHelper::easeOutQuad);
        sortDropdownAnimation.tick();

        if (isAnyMarketplacePopupVisible()) {
            mouseX = Integer.MIN_VALUE;
            mouseY = Integer.MIN_VALUE;
        }

        Layout layout = getLayout();
        if (!editorPopupMode) {
            renderTopBar(context, mouseX, mouseY, layout);
            renderGallerySection(context, mouseX, mouseY, layout);
            renderSortDropdown(context, mouseX, mouseY, layout);
        }
        if (popupPreset != null || presetPopupAnimation.isVisible()
            || accountPopupOpen || accountPopupAnimation.isVisible()
            || publishPopupOpen || publishPopupAnimation.isVisible()
            || pendingConfirmAction != null || confirmPopupAnimation.isVisible()) {
            DrawContextBridge.startNewRootLayer(context);
        }
        if (popupPreset != null || presetPopupAnimation.isVisible()) {
            renderPresetPopup(context, popupMouseX, popupMouseY, layout);
        }
        if (accountPopupOpen || accountPopupAnimation.isVisible()) {
            renderAccountPopup(context, popupMouseX, popupMouseY, layout);
        }
        if (publishPopupOpen || publishPopupAnimation.isVisible()) {
            renderPublishPopup(context, popupMouseX, popupMouseY, layout);
        }
        if (pendingConfirmAction != null || confirmPopupAnimation.isVisible()) {
            renderConfirmPopup(context, popupMouseX, popupMouseY, layout);
        }
    }

    private void renderTopBar(DrawContext context, int mouseX, int mouseY, Layout layout) {
        boolean backHovered = isPointInRect(mouseX, mouseY, layout.backButtonX, layout.backButtonY, BACK_BUTTON_SIZE, BACK_BUTTON_SIZE);
        drawIconButton(context, layout.backButtonX, layout.backButtonY, BACK_BUTTON_SIZE, BACK_BUTTON_SIZE, backHovered, false);
        drawBackArrow(context, layout.backButtonX, layout.backButtonY, backHovered ? UITheme.TEXT_HEADER : UITheme.TEXT_PRIMARY);
        boolean accountHovered = isPointInRect(mouseX, mouseY, layout.accountButtonX, layout.accountButtonY, ACCOUNT_BUTTON_WIDTH, SORT_BUTTON_HEIGHT);
        renderAccountToolbarButton(context, layout.accountButtonX, layout.accountButtonY, accountHovered);

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

    private void renderAccountToolbarButton(DrawContext context, int x, int y, boolean hovered) {
        drawIconButton(context, x, y, ACCOUNT_BUTTON_WIDTH, SORT_BUTTON_HEIGHT, hovered, authBusy);
        Identifier avatarTexture = getOrRequestAvatarTexture();
        if (avatarTexture != null && authSession != null) {
            GuiTextureRenderer.drawIcon(
                context,
                avatarTexture,
                x + 3,
                y + 3,
                ACCOUNT_BUTTON_WIDTH - 6,
                0xFFFFFFFF
            );
            return;
        }
        String initials = authBusy ? "…" : fallback(authSession == null ? null : authSession.getDisplayName(), authSession == null ? "D" : "U").trim();
        initials = initials.isEmpty() ? "D" : initials.substring(0, 1).toUpperCase(Locale.ROOT);
        int textColor = authBusy ? UITheme.TEXT_TERTIARY : hovered ? getAccentColor() : UITheme.TEXT_PRIMARY;
        int textX = x + (ACCOUNT_BUTTON_WIDTH - this.textRenderer.getWidth(initials)) / 2;
        int textY = y + (SORT_BUTTON_HEIGHT - this.textRenderer.fontHeight) / 2;
        context.drawTextWithShadow(this.textRenderer, Text.literal(initials), textX, textY, textColor);
    }

    private void renderGallerySection(DrawContext context, int mouseX, int mouseY, Layout layout) {
        int headerHeight = getSectionHeaderHeight();
        int bodyY = layout.sectionY + headerHeight;
        int bodyHeight = layout.sectionHeight - headerHeight - FOOTER_HEIGHT;
        drawGalleryBackdrop(context, layout.bodyX, layout.sectionY, layout.bodyWidth, layout.sectionHeight - FOOTER_HEIGHT);
        int scissorTop = Math.max(layout.sectionY, bodyY - 6);
        context.enableScissor(layout.bodyX, scissorTop, layout.bodyX + layout.bodyWidth, bodyY + bodyHeight);

        if (loading || presets.isEmpty()) {
            String message = loading ? "Fetching latest listings..." : fallback(statusMessage, "Nothing to show yet.");
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

        boolean myPresetsHovered = isPointInRect(mouseX, mouseY, layout.myPresetsButtonX, layout.myPresetsButtonY, MY_PRESETS_BUTTON_WIDTH, SORT_BUTTON_HEIGHT);
        drawActionButton(context, layout.myPresetsButtonX, layout.myPresetsButtonY, MY_PRESETS_BUTTON_WIDTH, SORT_BUTTON_HEIGHT,
            "My Presets", myPresetsHovered, authSession == null && !myPresetsOnly, myPresetsOnly);

        if (myPresetsOnly) {
            int filterY = layout.searchFieldY + SORT_BUTTON_HEIGHT + 6;
            int allX = layout.searchFieldX;
            int publicX = allX + MY_PRESET_FILTER_ALL_WIDTH + 6;
            int privateX = publicX + MY_PRESET_FILTER_PUBLIC_WIDTH + 6;
            drawActionButton(context, allX, filterY, MY_PRESET_FILTER_ALL_WIDTH, MY_PRESET_FILTER_BUTTON_HEIGHT,
                "All", isPointInRect(mouseX, mouseY, allX, filterY, MY_PRESET_FILTER_ALL_WIDTH, MY_PRESET_FILTER_BUTTON_HEIGHT), false,
                myPresetsFilter == MyPresetsFilter.ALL);
            drawActionButton(context, publicX, filterY, MY_PRESET_FILTER_PUBLIC_WIDTH, MY_PRESET_FILTER_BUTTON_HEIGHT,
                "Public", isPointInRect(mouseX, mouseY, publicX, filterY, MY_PRESET_FILTER_PUBLIC_WIDTH, MY_PRESET_FILTER_BUTTON_HEIGHT), false,
                myPresetsFilter == MyPresetsFilter.PUBLIC);
            drawActionButton(context, privateX, filterY, MY_PRESET_FILTER_PRIVATE_WIDTH, MY_PRESET_FILTER_BUTTON_HEIGHT,
                "Private", isPointInRect(mouseX, mouseY, privateX, filterY, MY_PRESET_FILTER_PRIVATE_WIDTH, MY_PRESET_FILTER_BUTTON_HEIGHT), false,
                myPresetsFilter == MyPresetsFilter.PRIVATE);
        }

        int resultRowY = myPresetsOnly ? layout.searchFieldY + SORT_BUTTON_HEIGHT + 6 : layout.resultRowY;

        String resultLabel = loading
            ? "Loading..."
            : presets.size() + " result" + (presets.size() == 1 ? "" : "s");
        int resultWidth = this.textRenderer.getWidth(resultLabel);
        int resultX = Math.max(layout.searchFieldX, layout.refreshButtonX - resultWidth - 6);
        context.drawTextWithShadow(this.textRenderer, Text.literal(resultLabel), resultX, resultRowY + 5, UITheme.TEXT_SECONDARY);

        boolean refreshHovered = isPointInRect(mouseX, mouseY, layout.refreshButtonX, layout.refreshButtonY, REFRESH_BUTTON_SIZE, REFRESH_BUTTON_SIZE);
        drawIconButton(context, layout.refreshButtonX, layout.refreshButtonY, REFRESH_BUTTON_SIZE, REFRESH_BUTTON_SIZE, refreshHovered, loading);
        drawRefreshIcon(context, layout.refreshButtonX, layout.refreshButtonY, loading ? UITheme.TEXT_TERTIARY : (refreshHovered ? getAccentColor() : UITheme.TEXT_PRIMARY));
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
        int previewHeight = rect.height - 46;
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
        renderGraphPreviewSurface(context, previewX, previewY, previewWidth, previewHeight, preset, true, false, 0f, 0f);

        int deleteX = previewX + previewWidth - 14;
        int deleteY = previewY + 2;
        int heartX = previewX + previewWidth - 14;
        int bookmarkX = heartX - 14;
        int actionY = previewY + previewHeight - 14;
        boolean liked = isPresetLiked(preset);
        boolean saved = isPresetSavedLocally(preset);
        boolean ownPreset = isOwnPreset(preset);
        boolean deleteHovered = ownPreset && isPointInRect(mouseX, mouseY, deleteX, deleteY, 12, 12);
        boolean bookmarkHovered = isPointInRect(mouseX, mouseY, bookmarkX, actionY, 12, 12);
        boolean heartHovered = isPointInRect(mouseX, mouseY, heartX, actionY, 12, 12);
        if (ownPreset) {
            drawAnimatedDeleteIcon(context, deleteX, deleteY, preset, false, deleteHovered);
        }
        drawAnimatedBookmarkIcon(context, bookmarkX, actionY, preset, saved, false, bookmarkHovered);
        drawAnimatedHeartIcon(context, heartX, actionY, preset, liked, false, heartHovered);
        if (!preset.isPublished()) {
            drawPrivateEyeIcon(context, previewX + 6, previewY + 6, UITheme.STATE_WARNING);
        }

        int textX = rect.x + 8;
        String downloadsLine = preset.getDownloadsCount() + " dl";
        String likesLine = preset.getLikesCount() + " like";
        int statsRight = rect.x + rect.width - 8;
        int downloadsColor = UITheme.STATE_SUCCESS;
        int likesColor = 0xFFE05454;
        int footerTop = previewY + previewHeight + 8;
        int statsLineY = footerTop + 3;
        int statsSecondLineY = footerTop + 14;
        int statsBlockWidth = Math.max(this.textRenderer.getWidth(downloadsLine), this.textRenderer.getWidth(likesLine));
        int textWidth = Math.max(32, rect.width - 16 - statsBlockWidth - 8);
        context.drawTextWithShadow(this.textRenderer,
            Text.literal(TextRenderUtil.trimWithEllipsis(this.textRenderer, preset.getName(), textWidth)),
            textX, footerTop - 1, UITheme.TEXT_HEADER);
        context.drawTextWithShadow(this.textRenderer,
            Text.literal(TextRenderUtil.trimWithEllipsis(this.textRenderer, "by " + preset.getAuthorName(), textWidth)),
            textX, footerTop + 10, UITheme.TEXT_SECONDARY);
        context.drawTextWithShadow(this.textRenderer,
            Text.literal(downloadsLine),
            statsRight - this.textRenderer.getWidth(downloadsLine), statsLineY, downloadsColor);
        context.drawTextWithShadow(this.textRenderer,
            Text.literal(likesLine),
            statsRight - this.textRenderer.getWidth(likesLine), statsSecondLineY, likesColor);
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

    private void renderGraphPreviewSurface(DrawContext context, int x, int y, int width, int height,
                                           MarketplacePreset preset, boolean interactive, boolean usePopupViewportState, float panX, float panY) {
        PreviewGraphModel previewModel = getCachedPreviewGraph(preset);
        if (previewModel == null || previewModel.nodes().isEmpty()) {
            requestPreviewGraph(preset);
            drawGraphPreview(context, x, y, width, height);
            return;
        }

        GraphBounds bounds = GraphBounds.of(previewModel.nodes());
        boolean popupInteractive = interactive && usePopupViewportState;
        float horizontalPadding = popupInteractive ? 22f : 16f;
        float verticalPadding = popupInteractive ? 20f : 14f;
        float scaleX = bounds.width() <= 0 ? 1f : Math.max(0.01f, (width - horizontalPadding) / bounds.width());
        float scaleY = bounds.height() <= 0 ? 1f : Math.max(0.01f, (height - verticalPadding) / bounds.height());
        float fitScale = Math.min(scaleX, scaleY);
        if (popupInteractive) {
            float viewScale = Math.max(0.18f, Math.min(1f, fitScale)) * popupPreviewZoom;
            float offsetX = x + width / 2f - (bounds.minX() + bounds.width() / 2f) * viewScale + panX;
            float offsetY = y + height / 2f - (bounds.minY() + bounds.height() / 2f) * viewScale + panY;
            context.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);
            Object matrices = context.getMatrices();
            MatrixStackBridge.push(matrices);
            MatrixStackBridge.translate(matrices, offsetX, offsetY);
            MatrixStackBridge.scale(matrices, viewScale, viewScale);
            for (NodeGraphData.ConnectionData connection : previewModel.connections()) {
                Node from = previewModel.nodeLookup().get(connection.getOutputNodeId());
                Node to = previewModel.nodeLookup().get(connection.getInputNodeId());
                if (from == null || to == null) {
                    continue;
                }
                int outputSocket = Math.max(0, connection.getOutputSocket());
                int inputSocket = Math.max(0, connection.getInputSocket());
                int safeOutputSocket = Math.min(outputSocket, Math.max(0, from.getOutputSocketCount() - 1));
                int safeInputSocket = Math.min(inputSocket, Math.max(0, to.getInputSocketCount() - 1));
                drawPreviewConnection(
                    context,
                    from.getSocketX(false),
                    from.getSocketY(safeOutputSocket, false),
                    to.getSocketX(true),
                    to.getSocketY(safeInputSocket, true),
                    from.getOutputSocketColor(safeOutputSocket),
                    true
                );
            }
            for (Node node : previewModel.nodes()) {
                renderPreviewNode(context, node, 0f, 0f, 1f, true, true);
            }
            MatrixStackBridge.pop(matrices);
            context.disableScissor();
            return;
        }
        float viewScale = Math.max(0.08f, Math.min(0.6f, fitScale));
        float offsetX = x + width / 2f - (bounds.minX() + bounds.width() / 2f) * viewScale;
        float offsetY = y + height / 2f - (bounds.minY() + bounds.height() / 2f) * viewScale;

        context.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);
        Object matrices = context.getMatrices();
        MatrixStackBridge.push(matrices);
        MatrixStackBridge.translate(matrices, offsetX, offsetY);
        MatrixStackBridge.scale(matrices, viewScale, viewScale);
        for (NodeGraphData.ConnectionData connection : previewModel.connections()) {
            Node from = previewModel.nodeLookup().get(connection.getOutputNodeId());
            Node to = previewModel.nodeLookup().get(connection.getInputNodeId());
            if (from == null || to == null) {
                continue;
            }
            int outputSocket = Math.max(0, connection.getOutputSocket());
            int inputSocket = Math.max(0, connection.getInputSocket());
            int safeOutputSocket = Math.min(outputSocket, Math.max(0, from.getOutputSocketCount() - 1));
            int safeInputSocket = Math.min(inputSocket, Math.max(0, to.getInputSocketCount() - 1));
            drawPreviewConnection(
                context,
                from.getSocketX(false),
                from.getSocketY(safeOutputSocket, false),
                to.getSocketX(true),
                to.getSocketY(safeInputSocket, true),
                from.getOutputSocketColor(safeOutputSocket),
                false
            );
        }

        for (Node node : previewModel.nodes()) {
            renderPreviewNode(context, node, 0f, 0f, 1f, true, false);
        }
        MatrixStackBridge.pop(matrices);
        context.disableScissor();
    }

    private void drawPreviewConnection(DrawContext context, int startX, int startY, int endX, int endY, int color, boolean popup) {
        int resolvedColor = popup ? presetPopupAnimation.getAnimatedPopupColor(color) : color;
        int steps = Math.max(8, Math.max(Math.abs(endX - startX), Math.abs(endY - startY)) / 8);
        float controlOffset = Math.max(18f, Math.abs(endX - startX) * 0.33f);
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            float invT = 1f - t;
            float p0x = startX;
            float p0y = startY;
            float p1x = startX + controlOffset;
            float p1y = startY;
            float p2x = endX - controlOffset;
            float p2y = endY;
            float p3x = endX;
            float p3y = endY;
            int x = Math.round(invT * invT * invT * p0x
                + 3f * invT * invT * t * p1x
                + 3f * invT * t * t * p2x
                + t * t * t * p3x);
            int y = Math.round(invT * invT * invT * p0y
                + 3f * invT * invT * t * p1y
                + 3f * invT * t * t * p2y
                + t * t * t * p3y);
            context.fill(x, y, x + 2, y + 2, resolvedColor);
        }
    }

    private void renderPreviewNode(DrawContext context, Node node, float offsetX, float offsetY, float scale, boolean interactive, boolean popup) {
        if (node == null) {
            return;
        }
        int nodeX = Math.round(offsetX + node.getX() * scale);
        int nodeY = Math.round(offsetY + node.getY() * scale);
        int nodeWidth = Math.max(18, Math.round(node.getWidth() * scale));
        int nodeHeight = Math.max(14, Math.round(node.getHeight() * scale));
        int color = node.getType() != null ? node.getType().getColor() : UITheme.BORDER_DEFAULT;
        int borderColor = node.isStopControlNode() ? 0xFFE35B5B : color;
        int backgroundColor = interactive ? UITheme.BACKGROUND_SECONDARY : AnimationHelper.darken(UITheme.BACKGROUND_SECONDARY, 0.94f);
        int resolvedBackground = popup ? presetPopupAnimation.getAnimatedPopupColor(backgroundColor) : backgroundColor;
        int resolvedBorder = popup ? presetPopupAnimation.getAnimatedPopupColor(borderColor) : borderColor;
        int resolvedInnerBorder = popup ? presetPopupAnimation.getAnimatedPopupColor(UITheme.PANEL_INNER_BORDER) : UITheme.PANEL_INNER_BORDER;
        UIStyleHelper.drawBeveledPanel(
            context,
            nodeX,
            nodeY,
            nodeWidth,
            nodeHeight,
            resolvedBackground,
            resolvedBorder,
            resolvedInnerBorder
        );
        boolean compactNode = node.usesMinimalNodePresentation() || node.isSensorNode() || node.isParameterNode();
        int headerHeight = compactNode ? Math.max(10, Math.round(14f * scale)) : Math.max(12, Math.round(18f * scale));
        int headerColor = color & UITheme.NODE_HEADER_ALPHA_MASK;
        context.fill(nodeX + 1, nodeY + 1, nodeX + nodeWidth - 1, nodeY + headerHeight,
            popup ? presetPopupAnimation.getAnimatedPopupColor(headerColor) : headerColor);

        if (scale > 0.12f) {
            String label = TextRenderUtil.trimWithEllipsis(this.textRenderer, node.getDisplayName().getString(), Math.max(20, nodeWidth - 8));
            context.drawTextWithShadow(this.textRenderer, Text.literal(label), nodeX + 4, nodeY + 3,
                popup ? presetPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_HEADER) : UITheme.TEXT_HEADER);
        }

        if (interactive && scale > 0.18f && !compactNode) {
            int textY = nodeY + headerHeight + 3;
            for (String line : buildNodeBodyLines(node)) {
                if (textY > nodeY + nodeHeight - 9) {
                    break;
                }
                context.drawTextWithShadow(this.textRenderer,
                    Text.literal(TextRenderUtil.trimWithEllipsis(this.textRenderer, line, Math.max(22, nodeWidth - 8))),
                    nodeX + 4,
                    textY,
                    popup ? presetPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_SECONDARY) : UITheme.TEXT_SECONDARY);
                textY += 10;
            }
        }
        renderNodeSockets(context, node, offsetX, offsetY, scale, popup);
    }

    private void renderNodeSockets(DrawContext context, Node node, float offsetX, float offsetY, float scale, boolean popup) {
        int socketSize = Math.max(2, Math.round(4f * scale));
        int halfSocket = Math.max(1, socketSize / 2);
        for (int socketIndex = 0; socketIndex < node.getInputSocketCount(); socketIndex++) {
            int socketX = Math.round(offsetX + node.getSocketX(true) * scale);
            int socketY = Math.round(offsetY + node.getSocketY(socketIndex, true) * scale);
            int inputColor = popup ? presetPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_TERTIARY) : UITheme.TEXT_TERTIARY;
            context.fill(socketX - halfSocket, socketY - halfSocket, socketX + halfSocket + 1, socketY + halfSocket + 1, inputColor);
        }
        for (int socketIndex = 0; socketIndex < node.getOutputSocketCount(); socketIndex++) {
            int socketX = Math.round(offsetX + node.getSocketX(false) * scale);
            int socketY = Math.round(offsetY + node.getSocketY(socketIndex, false) * scale);
            int socketColor = node.getOutputSocketColor(socketIndex);
            int outputColor = popup ? presetPopupAnimation.getAnimatedPopupColor(socketColor) : socketColor;
            context.fill(socketX - halfSocket, socketY - halfSocket, socketX + halfSocket + 1, socketY + halfSocket + 1, outputColor);
        }
    }

    private List<String> buildNodeBodyLines(Node node) {
        List<String> bodyLines = new ArrayList<>();
        if (node == null) {
            return bodyLines;
        }
        if (node.getMode() != null) {
            bodyLines.add("Mode: " + node.getMode().getDisplayName());
        }
        for (NodeParameter parameter : node.getParameters()) {
            if (parameter == null) {
                continue;
            }
            String value = fallback(parameter.getStringValue(), "").trim();
            if (value.isEmpty()) {
                continue;
            }
            bodyLines.add(parameter.getName() + ": " + value);
            if (bodyLines.size() >= 4) {
                break;
            }
        }
        return bodyLines;
    }

    private PreviewGraphModel getCachedPreviewGraph(MarketplacePreset preset) {
        if (preset == null || preset.getId() == null) {
            return null;
        }
        return previewGraphCache.get(preset.getId());
    }

    private void requestPreviewGraph(MarketplacePreset preset) {
        if (preset == null || preset.getId() == null || previewGraphCache.containsKey(preset.getId()) || previewGraphLoading.contains(preset.getId())) {
            return;
        }
        previewGraphLoading.add(preset.getId());
        MarketplaceService.fetchPresetGraphData(preset, authSession == null ? null : authSession.getAccessToken()).whenComplete((graphData, throwable) -> {
            if (this.client == null) {
                return;
            }
            this.client.execute(() -> {
                previewGraphLoading.remove(preset.getId());
                if (throwable == null && graphData != null) {
                    previewGraphCache.put(preset.getId(), buildPreviewGraphModel(graphData));
                }
            });
        });
    }

    private PreviewGraphModel buildPreviewGraphModel(NodeGraphData graphData) {
        List<Node> rebuiltNodes = NodeGraphPersistence.convertToNodes(graphData);
        Map<String, Node> nodeLookup = new HashMap<>();
        for (Node node : rebuiltNodes) {
            nodeLookup.put(node.getId(), node);
        }
        List<NodeGraphData.ConnectionData> connections = graphData.getConnections() == null
            ? List.of()
            : List.copyOf(graphData.getConnections());
        return new PreviewGraphModel(List.copyOf(rebuiltNodes), connections, nodeLookup);
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
        context.enableScissor(popupX, popupY, popupX + popupWidth, popupY + popupHeight);
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
        int popupCloseX = popupX + popupWidth - 18;
        int popupCloseY = popupY + 10;
        boolean popupCloseHovered = isPointInRect(mouseX, mouseY, popupCloseX - 2, popupCloseY - 2, 12, 12);
        drawPopupCloseIcon(context, popupCloseX, popupCloseY,
            presetPopupAnimation.getAnimatedPopupColor(popupCloseHovered ? UITheme.TEXT_HEADER : UITheme.TEXT_PRIMARY));
        context.drawHorizontalLine(popupX, popupX + popupWidth - 1, popupY + 28,
            presetPopupAnimation.getAnimatedPopupColor(UITheme.BORDER_SUBTLE));

        int contentTop = popupY + 40;
        int contentBottom = popupY + popupHeight - 48;
        int contentHeight = Math.max(24, contentBottom - contentTop);
        Rect previewRect = getPopupPreviewRect(popupX, popupY, popupWidth, popupHeight, popupScrollOffset);
        int previewX = previewRect.x;
        int previewY = previewRect.y;
        int previewWidth = previewRect.width;
        int previewHeight = previewRect.height;
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
        renderGraphPreviewSurface(context, previewX, previewY, previewWidth, previewHeight, popupPreset, true, true, popupPreviewPanX, popupPreviewPanY);
        int zoomButtonSize = 14;
        int zoomOutX = previewX + previewWidth - zoomButtonSize * 2 - 8;
        int zoomInX = previewX + previewWidth - zoomButtonSize - 6;
        int zoomButtonY = previewY + 6;
        boolean zoomOutHovered = isPointInRect(mouseX, mouseY, zoomOutX, zoomButtonY, zoomButtonSize, zoomButtonSize);
        boolean zoomInHovered = isPointInRect(mouseX, mouseY, zoomInX, zoomButtonY, zoomButtonSize, zoomButtonSize);
        drawMinimalPreviewButton(context, zoomOutX, zoomButtonY, zoomButtonSize, zoomButtonSize, zoomOutHovered);
        drawMinimalPreviewButton(context, zoomInX, zoomButtonY, zoomButtonSize, zoomButtonSize, zoomInHovered);
        drawPreviewMinusIcon(context, zoomOutX, zoomButtonY, presetPopupAnimation.getAnimatedPopupColor(zoomOutHovered ? getAccentColor() : UITheme.TEXT_PRIMARY));
        drawPreviewPlusIcon(context, zoomInX, zoomButtonY, presetPopupAnimation.getAnimatedPopupColor(zoomInHovered ? getAccentColor() : UITheme.TEXT_PRIMARY));

        int popupBookmarkX = popupX + popupWidth - 56;
        int popupHeartX = popupX + popupWidth - 38;
        boolean popupBookmarkHovered = isPointInRect(mouseX, mouseY, popupBookmarkX, popupY + 10, 12, 12);
        boolean popupHeartHovered = isPointInRect(mouseX, mouseY, popupHeartX, popupY + 10, 12, 12);
        drawAnimatedBookmarkIcon(context, popupBookmarkX, popupY + 10, popupPreset, isPresetSavedLocally(popupPreset), true, popupBookmarkHovered);
        drawAnimatedHeartIcon(context, popupHeartX, popupY + 10, popupPreset, isPresetLiked(popupPreset), true, popupHeartHovered);

        int cursorY = previewY + previewHeight + 12;
        if (popupMetadataEditing) {
            cursorY = drawPopupEditableField(context, mouseX, mouseY, textX, cursorY, textWidth, "Title", publishNameField) + 3;
        } else {
            context.drawTextWithShadow(this.textRenderer,
                Text.literal(TextRenderUtil.trimWithEllipsis(this.textRenderer, popupPreset.getName(), textWidth)),
                textX, cursorY, presetPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_HEADER));
            cursorY += 14;
        }
        context.drawTextWithShadow(this.textRenderer,
            Text.literal("by " + TextRenderUtil.trimWithEllipsis(this.textRenderer, fallback(popupPreset.getAuthorName(), "Unknown"), textWidth - 20)),
            textX, cursorY, presetPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_SECONDARY));
        cursorY += 14;

        int downloadsWidth = Math.max(54, this.textRenderer.getWidth(popupPreset.getDownloadsCount() + " downloads") + 12);
        int likesWidth = Math.max(42, this.textRenderer.getWidth(popupPreset.getLikesCount() + " likes") + 12);
        drawPopupStatPill(context, textX, cursorY, downloadsWidth, "Downloads", Integer.toString(popupPreset.getDownloadsCount()));
        drawPopupStatPill(context, textX + downloadsWidth + 6, cursorY, likesWidth, "Likes", Integer.toString(popupPreset.getLikesCount()));
        cursorY += 24;

        if (popupMetadataEditing) {
            cursorY = drawPopupEditableField(context, mouseX, mouseY, textX, cursorY, textWidth, "Tags", publishTagsField) + 1;
        } else {
            String tagsLine = formatTags(popupPreset.getTags());
            if (!tagsLine.isBlank() && !"untagged".equalsIgnoreCase(tagsLine)) {
                cursorY = drawWrappedValue(
                    context,
                    textX,
                    cursorY,
                    textWidth,
                    "Tags: " + tagsLine,
                    presetPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_PRIMARY),
                    2
                );
            }
        }

        int visibilityY = cursorY + 10;
        String visibilityLabel = popupMetadataEditing ? "Visibility" : "Visibility: " + (popupPreset.isPublished() ? "Public" : "Private");
        int visibilityColor = popupMetadataEditing
            ? UITheme.TEXT_LABEL
            : (popupPreset.isPublished() ? getAccentColor() : UITheme.STATE_WARNING);
        context.drawTextWithShadow(this.textRenderer, Text.literal(visibilityLabel), textX, visibilityY,
            presetPopupAnimation.getAnimatedPopupColor(visibilityColor));
        if (popupMetadataEditing) {
            presetVisibilityToggle.setValue(publishVisibilityPublic);
            presetVisibilityToggle.setPosition(textX + textWidth - presetVisibilityToggle.getWidth(), visibilityY - 2);
            presetVisibilityToggle.render(context, mouseX, mouseY, presetPopupAnimation.getPopupAlpha());
        }
        cursorY = visibilityY + 14;

        int descriptionTop = cursorY + 8;
        int descriptionHeight = popupMetadataEditing
            ? 46
            : Math.max(42, measureWrappedValueHeight(textWidth - 16, fallback(popupPreset.getDescription(), "No description provided."), 5) + 18);
        UIStyleHelper.drawBeveledPanel(
            context,
            textX,
            descriptionTop,
            textWidth,
            descriptionHeight,
            presetPopupAnimation.getAnimatedPopupColor(UITheme.BACKGROUND_SECTION),
            presetPopupAnimation.getAnimatedPopupColor(UITheme.BORDER_SUBTLE),
            presetPopupAnimation.getAnimatedPopupColor(UITheme.PANEL_INNER_BORDER)
        );
        context.drawTextWithShadow(this.textRenderer, Text.literal("About"), textX + 8, descriptionTop + 6,
            presetPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_LABEL));
        if (popupMetadataEditing) {
            int fieldY = descriptionTop + 18;
            drawPopupFieldFrame(context, mouseX, mouseY, textX + 8, fieldY, textWidth - 16, 18, publishDescriptionField);
            if (publishDescriptionField != null) {
                publishDescriptionField.setPosition(textX + 14, fieldY + 5);
                publishDescriptionField.setWidth(textWidth - 28);
                publishDescriptionField.render(context, mouseX, mouseY, 0f);
            }
            cursorY = descriptionTop + descriptionHeight;
        } else {
            cursorY = drawWrappedValue(context, textX + 8, descriptionTop + 18, textWidth - 16,
                fallback(popupPreset.getDescription(), "No description provided."),
                presetPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_PRIMARY), 5);
        }

        CompatibilityStatus compatibility = getCompatibilityStatus(popupPreset);
        int compatibilityHeight = 30;
        UIStyleHelper.drawBeveledPanel(
            context,
            textX,
            cursorY + 8,
            textWidth,
            compatibilityHeight,
            presetPopupAnimation.getAnimatedPopupColor(UITheme.BACKGROUND_SECTION),
            presetPopupAnimation.getAnimatedPopupColor(UITheme.BORDER_SUBTLE),
            presetPopupAnimation.getAnimatedPopupColor(UITheme.PANEL_INNER_BORDER)
        );
        context.drawTextWithShadow(this.textRenderer, Text.literal("Compatibility"), textX + 8, cursorY + 14,
            presetPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_LABEL));
        context.drawTextWithShadow(this.textRenderer, Text.literal(TextRenderUtil.trimWithEllipsis(this.textRenderer,
                compatibility.minecraftLine() + "  •  " + compatibility.pathmindLine(), textWidth - 16)),
            textX + 8, cursorY + 24,
            presetPopupAnimation.getAnimatedPopupColor(compatibility.minecraftColor()));
        cursorY += compatibilityHeight + 18;

        String sharedLine = "Published " + formatTimestamp(popupPreset.getCreatedAt()) + "  •  Updated " + formatTimestamp(popupPreset.getUpdatedAt());
        cursorY = drawWrappedValue(context, textX, cursorY, textWidth, sharedLine,
            presetPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_TERTIARY), 2);

        if (authSession == null) {
            cursorY = drawWrappedValue(context, textX, cursorY, textWidth,
                "Sign in with Discord to like presets and count imports.",
                presetPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_TERTIARY), 2);
        }
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

        boolean ownPreset = isOwnPreset(popupPreset);
        boolean hasLinkedLocalPreset = findLocalPresetNameForMarketplacePreset(popupPreset).isPresent();
        boolean hasLocalChanges = hasLinkedLocalPresetChanges(popupPreset);
        boolean showUpdateButton = ownPreset && !popupMetadataEditing && hasLinkedLocalPreset;
        int updateButtonX = popupX + 10;
        int authButtonX = popupX + (popup.authButtonX - popup.x);
        int deleteButtonX = popupX + (popup.deleteButtonX - popup.x);
        int downloadButtonX = popupX + ((ownPreset ? popup.downloadButtonX : popup.deleteButtonX) - popup.x);
        int buttonY = popupY + (popup.buttonY - popup.y);
        boolean updateEnabled = hasLocalChanges && !publishBusy && !deleteBusy && !importingPreset;
        boolean updateHovered = showUpdateButton && isPointInRect(mouseX, mouseY, updateButtonX, buttonY, popup.buttonWidth, popup.buttonHeight);
        boolean authHovered = isPointInRect(mouseX, mouseY, authButtonX, buttonY, popup.buttonWidth, popup.buttonHeight);
        boolean deleteHovered = isPointInRect(mouseX, mouseY, deleteButtonX, buttonY, popup.buttonWidth, popup.buttonHeight);
        boolean downloadHovered = isPointInRect(mouseX, mouseY, downloadButtonX, buttonY, popup.buttonWidth, popup.buttonHeight);
        popupUpdateHoverAnimation.animateTo(showUpdateButton && updateHovered ? 1f : 0f, UITheme.TRANSITION_ANIM_MS, AnimationHelper::easeOutQuad);
        popupDeleteHoverAnimation.animateTo(ownPreset && deleteHovered ? 1f : 0f, UITheme.TRANSITION_ANIM_MS, AnimationHelper::easeOutQuad);
        popupUpdateHoverAnimation.tick();
        popupDeleteHoverAnimation.tick();
        if (showUpdateButton) {
            drawAnimatedActionButton(context, updateButtonX, buttonY, popup.buttonWidth, popup.buttonHeight,
                publishBusy ? "Working..." : "Update", updateHovered, !updateEnabled, presetPopupAnimation, popupUpdateHoverAnimation.getValue());
        }
        drawAnimatedActionButton(context, authButtonX, buttonY, popup.buttonWidth, popup.buttonHeight,
            getPopupAuthButtonLabel(), authHovered, authBusy || publishBusy, presetPopupAnimation);
        if (ownPreset) {
            drawAnimatedActionButton(context, deleteButtonX, buttonY, popup.buttonWidth, popup.buttonHeight,
                deleteBusy ? "..." : "Delete", deleteHovered, deleteBusy || publishBusy, presetPopupAnimation, popupDeleteHoverAnimation.getValue());
        }
        drawAnimatedActionButton(context, downloadButtonX, buttonY, popup.buttonWidth, popup.buttonHeight,
            importingPreset ? "Downloading..." : "Download", downloadHovered, importingPreset || deleteBusy, presetPopupAnimation);
        context.disableScissor();
    }

    private void renderAccountPopup(DrawContext context, int mouseX, int mouseY, Layout layout) {
        AccountPopupLayout popup = getAccountPopupLayout(layout);
        context.fill(0, 0, this.width, this.height, accountPopupAnimation.getAnimatedBackgroundColor(UITheme.OVERLAY_BACKGROUND));
        int[] bounds = accountPopupAnimation.getScaledPopupBounds(this.width, this.height, popup.width, popup.height);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int popupWidth = bounds[2];
        int popupHeight = bounds[3];
        if (popupWidth <= 0 || popupHeight <= 0 || authSession == null) {
            return;
        }
        context.enableScissor(popupX, popupY, popupX + popupWidth, popupY + popupHeight);

        UIStyleHelper.drawBeveledPanel(
            context,
            popupX,
            popupY,
            popupWidth,
            popupHeight,
            accountPopupAnimation.getAnimatedPopupColor(UITheme.BACKGROUND_SECONDARY),
            accountPopupAnimation.getAnimatedPopupColor(UITheme.BORDER_DEFAULT),
            accountPopupAnimation.getAnimatedPopupColor(UITheme.PANEL_INNER_BORDER)
        );

        context.drawTextWithShadow(this.textRenderer, Text.literal("Account"), popupX + 12, popupY + 10,
            accountPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_HEADER));
        context.drawHorizontalLine(popupX, popupX + popupWidth - 1, popupY + 28,
            accountPopupAnimation.getAnimatedPopupColor(UITheme.BORDER_SUBTLE));

        int avatarSize = 52;
        int contentX = popupX + 12;
        int contentY = popupY + 42;
        int contentWidth = popupWidth - 24;
        renderAccountAvatar(context, popupX + 14, popupY + 42, avatarSize);
        int textX = contentX + avatarSize + 12;
        int textWidth = popupWidth - (textX - popupX) - 12;
        context.drawTextWithShadow(this.textRenderer,
            Text.literal(TextRenderUtil.trimWithEllipsis(this.textRenderer,
                fallback(authSession.getDisplayName(), fallback(authSession.getEmail(), "Discord user")), textWidth)),
            textX, contentY + 2, accountPopupAnimation.getAnimatedPopupColor(getAccentColor()));
        contentY += 16;
        contentY = drawWrappedValue(context, textX, contentY + 2, textWidth,
            "Provider: " + fallback(authSession.getProvider(), "discord"),
            accountPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_SECONDARY), 2);
        contentY = drawWrappedValue(context, textX, contentY, textWidth,
            "User ID: " + fallback(authSession.getUserId(), "Unknown"),
            accountPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_TERTIARY), 2);
        int closeButtonX = popupX + (popup.closeButtonX - popup.x);
        int signOutButtonX = popupX + (popup.signOutButtonX - popup.x);
        int buttonY = popupY + (popup.buttonY - popup.y);
        boolean closeHovered = isPointInRect(mouseX, mouseY, closeButtonX, buttonY, popup.buttonWidth, popup.buttonHeight);
        boolean signOutHovered = isPointInRect(mouseX, mouseY, signOutButtonX, buttonY, popup.buttonWidth, popup.buttonHeight);
        drawAnimatedActionButton(context, closeButtonX, buttonY, popup.buttonWidth, popup.buttonHeight,
            "Close", closeHovered, false, accountPopupAnimation);
        drawAnimatedActionButton(context, signOutButtonX, buttonY, popup.buttonWidth, popup.buttonHeight,
            "Sign Out", signOutHovered, authBusy, accountPopupAnimation);
        context.disableScissor();
    }

    private void renderPublishPopup(DrawContext context, int mouseX, int mouseY, Layout layout) {
        PublishPopupLayout popup = getPublishPopupLayout(layout);
        context.fill(0, 0, this.width, this.height, publishPopupAnimation.getAnimatedBackgroundColor(UITheme.OVERLAY_BACKGROUND));
        int[] bounds = publishPopupAnimation.getScaledPopupBounds(this.width, this.height, popup.width, popup.height);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int popupWidth = bounds[2];
        int popupHeight = bounds[3];
        if (popupWidth <= 0 || popupHeight <= 0) {
            return;
        }
        context.enableScissor(popupX, popupY, popupX + popupWidth, popupY + popupHeight);
        UIStyleHelper.drawBeveledPanel(
            context,
            popupX,
            popupY,
            popupWidth,
            popupHeight,
            publishPopupAnimation.getAnimatedPopupColor(UITheme.BACKGROUND_SECONDARY),
            publishPopupAnimation.getAnimatedPopupColor(UITheme.BORDER_DEFAULT),
            publishPopupAnimation.getAnimatedPopupColor(UITheme.PANEL_INNER_BORDER)
        );

        String title = editingPreset == null ? "Publish Preset" : "Edit Metadata";
        context.drawTextWithShadow(this.textRenderer, Text.literal(title), popupX + 12, popupY + 10,
            publishPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_HEADER));
        context.drawHorizontalLine(popupX, popupX + popupWidth - 1, popupY + 28,
            publishPopupAnimation.getAnimatedPopupColor(UITheme.BORDER_SUBTLE));

        int contentX = popupX + 12;
        int contentWidth = popupWidth - 24;
        int sourceY = popupY + 40;
        String sourceLine = editingPreset == null
            ? "Source preset: " + fallback(publishSourcePresetName, "Unknown")
            : "Editing listing by " + fallback(editingPreset.getAuthorName(), "Unknown");
        drawWrappedValue(context, contentX, sourceY, contentWidth,
            sourceLine,
            publishPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_SECONDARY), 2);

        int fieldWidth = popupWidth - 24;
        int fieldHeight = 18;
        int labelGap = 11;
        int nameLabelY = popupY + 53;
        int descriptionLabelY = popupY + 92;
        int tagsLabelY = popupY + 131;
        drawPublishField(context, mouseX, mouseY, contentX, nameLabelY, fieldWidth, fieldHeight, "Name", publishNameField, labelGap);
        drawPublishField(context, mouseX, mouseY, contentX, descriptionLabelY, fieldWidth, fieldHeight, "Description", publishDescriptionField, labelGap);
        drawPublishField(context, mouseX, mouseY, contentX, tagsLabelY, fieldWidth, fieldHeight, "Tags", publishTagsField, labelGap);

        String tagsHint = "Comma-separated tags. Slug updates automatically from the name.";
        drawWrappedValue(context, contentX, popupY + 166, contentWidth, tagsHint,
            publishPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_TERTIARY), 2);

        int visibilityLabelY = popupY + 189;
        context.drawTextWithShadow(this.textRenderer, Text.literal("Visibility"), contentX, visibilityLabelY,
            publishPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_LABEL));
        Rect publishToggle = getPublishPopupVisibilityToggleRect(popupX, popupY, popupWidth);
        publishVisibilityToggle.setValue(publishVisibilityPublic);
        publishVisibilityToggle.setPosition(publishToggle.x, publishToggle.y);
        publishVisibilityToggle.render(context, mouseX, mouseY, publishPopupAnimation.getPopupAlpha());
        String visibilityHint = publishVisibilityPublic
            ? "Visible in the public marketplace."
            : "Private cloud preset. Only visible in My Presets.";
        drawWrappedValue(context, contentX + 78, visibilityLabelY + 2, contentWidth - 78, visibilityHint,
            publishPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_SECONDARY), 2);

        if (!publishStatusMessage.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer,
                Text.literal(TextRenderUtil.trimWithEllipsis(this.textRenderer, publishStatusMessage, contentWidth)),
                contentX,
                popupY + popupHeight - 40,
                publishPopupAnimation.getAnimatedPopupColor(publishStatusColor));
        }

        int cancelButtonX = popupX + (popup.cancelButtonX - popup.x);
        int authButtonX = popupX + (popup.authButtonX - popup.x);
        int submitButtonX = popupX + (popup.submitButtonX - popup.x);
        int buttonY = popupY + (popup.buttonY - popup.y);
        boolean cancelHovered = isPointInRect(mouseX, mouseY, cancelButtonX, buttonY, popup.buttonWidth, popup.buttonHeight);
        boolean authHovered = isPointInRect(mouseX, mouseY, authButtonX, buttonY, popup.buttonWidth, popup.buttonHeight);
        boolean submitHovered = isPointInRect(mouseX, mouseY, submitButtonX, buttonY, popup.buttonWidth, popup.buttonHeight);
        drawAnimatedActionButton(context, cancelButtonX, buttonY, popup.buttonWidth, popup.buttonHeight,
            "Cancel", cancelHovered, false, publishPopupAnimation);
        drawAnimatedActionButton(context, authButtonX, buttonY, popup.buttonWidth, popup.buttonHeight,
            getPublishAuthButtonLabel(), authHovered, publishBusy || authSession != null, publishPopupAnimation);
        drawAnimatedActionButton(context, submitButtonX, buttonY, popup.buttonWidth, popup.buttonHeight,
            publishBusy ? "Working..." : (editingPreset == null ? "Publish" : "Save"),
            submitHovered, publishBusy, publishPopupAnimation);
        context.disableScissor();
    }

    private void renderConfirmPopup(DrawContext context, int mouseX, int mouseY, Layout layout) {
        ConfirmAction confirmAction = pendingConfirmAction != null ? pendingConfirmAction : renderConfirmAction;
        if (confirmAction == null) {
            return;
        }
        ConfirmPopupLayout popup = getConfirmPopupLayout(layout);
        context.fill(0, 0, this.width, this.height, confirmPopupAnimation.getAnimatedBackgroundColor(UITheme.OVERLAY_BACKGROUND));
        int[] bounds = confirmPopupAnimation.getScaledPopupBounds(this.width, this.height, popup.width, popup.height);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int popupWidth = bounds[2];
        int popupHeight = bounds[3];
        if (popupWidth <= 0 || popupHeight <= 0) {
            return;
        }
        context.enableScissor(popupX, popupY, popupX + popupWidth, popupY + popupHeight);
        UIStyleHelper.drawBeveledPanel(
            context,
            popupX,
            popupY,
            popupWidth,
            popupHeight,
            confirmPopupAnimation.getAnimatedPopupColor(UITheme.BACKGROUND_SECONDARY),
            confirmPopupAnimation.getAnimatedPopupColor(UITheme.BORDER_DEFAULT),
            confirmPopupAnimation.getAnimatedPopupColor(UITheme.PANEL_INNER_BORDER)
        );

        String title = confirmAction == ConfirmAction.DELETE ? "Delete Uploaded Preset" : "Update Uploaded Preset";
        String lineOne = confirmAction == ConfirmAction.DELETE
            ? "Delete this uploaded preset from Pathmind Marketplace?"
            : "Overwrite the uploaded preset with your current local graph?";
        String lineTwo = confirmAction == ConfirmAction.DELETE
            ? "This removes the cloud copy and cannot be undone."
            : "This replaces the current uploaded version for all future downloads.";

        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(title),
            popupX + popupWidth / 2, popupY + 14, confirmPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_PRIMARY));
        drawWrappedValue(context, popupX + 20, popupY + 44, popupWidth - 40, lineOne,
            confirmPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_SECONDARY), 2);
        drawWrappedValue(context, popupX + 20, popupY + 72, popupWidth - 40, lineTwo,
            confirmPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_TERTIARY), 2);

        boolean skipConfirm = confirmAction == ConfirmAction.DELETE ? skipMarketplaceDeleteConfirm : skipMarketplaceUpdateConfirm;
        int checkboxX = popupX + 20;
        int checkboxY = popupY + 102;
        boolean checkboxHovered = isPointInRect(mouseX, mouseY, checkboxX - 2, checkboxY - 2, 14, 14);
        context.fill(checkboxX, checkboxY, checkboxX + 10, checkboxY + 10,
            confirmPopupAnimation.getAnimatedPopupColor(UITheme.RENAME_INPUT_BG));
        DrawContextBridge.drawBorder(context, checkboxX, checkboxY, 10, 10,
            confirmPopupAnimation.getAnimatedPopupColor(checkboxHovered ? UITheme.BORDER_HIGHLIGHT : UITheme.BORDER_DEFAULT));
        if (skipConfirm) {
            int checkColor = confirmPopupAnimation.getAnimatedPopupColor(getAccentColor());
            context.fill(checkboxX + 2, checkboxY + 5, checkboxX + 3, checkboxY + 7, checkColor);
            context.fill(checkboxX + 3, checkboxY + 6, checkboxX + 4, checkboxY + 8, checkColor);
            context.fill(checkboxX + 4, checkboxY + 6, checkboxX + 5, checkboxY + 7, checkColor);
            context.fill(checkboxX + 5, checkboxY + 5, checkboxX + 6, checkboxY + 6, checkColor);
            context.fill(checkboxX + 6, checkboxY + 4, checkboxX + 7, checkboxY + 5, checkColor);
            context.fill(checkboxX + 7, checkboxY + 3, checkboxX + 8, checkboxY + 4, checkColor);
        }
        context.drawTextWithShadow(this.textRenderer, Text.literal("Don't show again"),
            checkboxX + 18, checkboxY + 1, confirmPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_SECONDARY));

        int cancelButtonX = popupX + (popup.cancelButtonX - popup.x);
        int confirmButtonX = popupX + (popup.confirmButtonX - popup.x);
        int buttonY = popupY + (popup.buttonY - popup.y);
        boolean cancelHovered = isPointInRect(mouseX, mouseY, cancelButtonX, buttonY, popup.buttonWidth, popup.buttonHeight);
        boolean confirmHovered = isPointInRect(mouseX, mouseY, confirmButtonX, buttonY, popup.buttonWidth, popup.buttonHeight);
        drawAnimatedActionButton(context, cancelButtonX, buttonY, popup.buttonWidth, popup.buttonHeight,
            "Cancel", cancelHovered, false, confirmPopupAnimation);
        drawAnimatedActionButton(context, confirmButtonX, buttonY, popup.buttonWidth, popup.buttonHeight,
            confirmAction == ConfirmAction.DELETE ? "Delete" : "Update",
            confirmHovered, false, confirmPopupAnimation);
        context.disableScissor();
    }

    private int drawPublishField(DrawContext context, int mouseX, int mouseY, int x, int y, int width, int height,
                                 String label, TextFieldWidget field, int labelGap) {
        context.drawTextWithShadow(this.textRenderer, Text.literal(label), x, y,
            publishPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_LABEL));
        int fieldY = y + labelGap;
        boolean hovered = isPointInRect(mouseX, mouseY, x, fieldY, width, height);
        boolean focused = field != null && field.isFocused();
        UIStyleHelper.drawToolbarButtonFrame(
            context,
            x,
            fieldY,
            width,
            height,
            publishPopupAnimation.getAnimatedPopupColor(UITheme.BACKGROUND_SECTION),
            publishPopupAnimation.getAnimatedPopupColor(focused || hovered ? getAccentColor() : UITheme.BORDER_SUBTLE),
            publishPopupAnimation.getAnimatedPopupColor(UITheme.PANEL_INNER_BORDER)
        );
        if (field != null) {
            field.setPosition(x + 6, fieldY + 5);
            field.setWidth(width - 12);
            field.render(context, mouseX, mouseY, 0f);
        }
        return fieldY + height;
    }

    private Rect getPublishPopupVisibilityToggleRect(int popupX, int popupY, int popupWidth) {
        return new Rect(popupX + popupWidth - publishVisibilityToggle.getWidth() - 24, popupY + 188,
            publishVisibilityToggle.getWidth(), publishVisibilityToggle.getHeight());
    }

    private int drawPopupEditableField(DrawContext context, int mouseX, int mouseY, int x, int y, int width, String label, TextFieldWidget field) {
        context.drawTextWithShadow(this.textRenderer, Text.literal(label), x, y,
            presetPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_LABEL));
        int fieldY = y + 11;
        drawPopupFieldFrame(context, mouseX, mouseY, x, fieldY, width, 18, field);
        if (field != null) {
            field.setPosition(x + 6, fieldY + 5);
            field.setWidth(width - 12);
            field.render(context, mouseX, mouseY, 0f);
        }
        return fieldY + 18;
    }

    private void drawPopupFieldFrame(DrawContext context, int mouseX, int mouseY, int x, int y, int width, int height, TextFieldWidget field) {
        boolean hovered = isPointInRect(mouseX, mouseY, x, y, width, height);
        boolean focused = field != null && field.isFocused();
        UIStyleHelper.drawToolbarButtonFrame(
            context,
            x,
            y,
            width,
            height,
            presetPopupAnimation.getAnimatedPopupColor(UITheme.BACKGROUND_SECTION),
            presetPopupAnimation.getAnimatedPopupColor(focused || hovered ? getAccentColor() : UITheme.BORDER_SUBTLE),
            presetPopupAnimation.getAnimatedPopupColor(UITheme.PANEL_INNER_BORDER)
        );
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
        if (editorPopupMode && popupPreset == null && !presetPopupAnimation.isVisible()) {
            close();
            return true;
        }
        if (pendingConfirmAction != null || confirmPopupAnimation.isVisible()) {
            if (pendingConfirmAction == null) {
                return true;
            }
            ConfirmPopupLayout confirmPopup = getConfirmPopupLayout(layout);
            int[] bounds = confirmPopupAnimation.getScaledPopupBounds(this.width, this.height, confirmPopup.width, confirmPopup.height);
            int popupX = bounds[0];
            int popupY = bounds[1];
            int popupWidth = bounds[2];
            int popupHeight = bounds[3];
            int cancelButtonX = popupX + (confirmPopup.cancelButtonX - confirmPopup.x);
            int confirmButtonX = popupX + (confirmPopup.confirmButtonX - confirmPopup.x);
            int buttonY = popupY + (confirmPopup.buttonY - confirmPopup.y);
            int checkboxX = popupX + 20;
            int checkboxY = popupY + 102;
            if (!isPointInRect(mouseX, mouseY, popupX, popupY, popupWidth, popupHeight)) {
                closeConfirmPopup();
                return true;
            }
            if (isPointInRect(mouseX, mouseY, checkboxX - 2, checkboxY - 2, 14, 14)) {
                if (pendingConfirmAction == ConfirmAction.DELETE) {
                    setSkipMarketplaceDeleteConfirm(!skipMarketplaceDeleteConfirm);
                } else if (pendingConfirmAction == ConfirmAction.UPDATE) {
                    setSkipMarketplaceUpdateConfirm(!skipMarketplaceUpdateConfirm);
                }
                return true;
            }
            if (isPointInRect(mouseX, mouseY, cancelButtonX, buttonY, confirmPopup.buttonWidth, confirmPopup.buttonHeight)) {
                closeConfirmPopup();
                return true;
            }
            if (isPointInRect(mouseX, mouseY, confirmButtonX, buttonY, confirmPopup.buttonWidth, confirmPopup.buttonHeight)) {
                confirmPendingAction();
                return true;
            }
            return true;
        }
        if (publishPopupOpen) {
            PublishPopupLayout publishPopup = getPublishPopupLayout(layout);
            int[] bounds = publishPopupAnimation.getScaledPopupBounds(this.width, this.height, publishPopup.width, publishPopup.height);
            int popupX = bounds[0];
            int popupY = bounds[1];
            int popupWidth = bounds[2];
            int popupHeight = bounds[3];
            int cancelButtonX = popupX + (publishPopup.cancelButtonX - publishPopup.x);
            int authButtonX = popupX + (publishPopup.authButtonX - publishPopup.x);
            int submitButtonX = popupX + (publishPopup.submitButtonX - publishPopup.x);
            int buttonY = popupY + (publishPopup.buttonY - publishPopup.y);

            int fieldX = popupX + 12;
            int fieldWidth = popupWidth - 24;
            int nameFieldY = popupY + 64;
            int descriptionFieldY = nameFieldY + 39;
            int tagsFieldY = descriptionFieldY + 39;
            Rect publishVisibilityToggleRect = getPublishPopupVisibilityToggleRect(popupX, popupY, popupWidth);

            boolean clickedField = false;
            if (publishNameField != null && isPointInRect(mouseX, mouseY, fieldX, nameFieldY, fieldWidth, 18)) {
                focusPublishField(publishNameField);
                publishNameField.mouseClicked(click, inBounds);
                clickedField = true;
            } else if (publishDescriptionField != null && isPointInRect(mouseX, mouseY, fieldX, descriptionFieldY, fieldWidth, 18)) {
                focusPublishField(publishDescriptionField);
                publishDescriptionField.mouseClicked(click, inBounds);
                clickedField = true;
            } else if (publishTagsField != null && isPointInRect(mouseX, mouseY, fieldX, tagsFieldY, fieldWidth, 18)) {
                focusPublishField(publishTagsField);
                publishTagsField.mouseClicked(click, inBounds);
                clickedField = true;
            } else if (isPointInRect(mouseX, mouseY, publishVisibilityToggleRect.x, publishVisibilityToggleRect.y, publishVisibilityToggleRect.width, publishVisibilityToggleRect.height)) {
                publishVisibilityToggle.mouseClicked(mouseX, mouseY);
                publishVisibilityPublic = publishVisibilityToggle.getValue();
                clickedField = true;
            } else {
                focusPublishField(null);
            }
            if (clickedField) {
                return true;
            }
            if (isPointInRect(mouseX, mouseY, cancelButtonX, buttonY, publishPopup.buttonWidth, publishPopup.buttonHeight)) {
                closePublishPopup();
                return true;
            }
            if (!publishBusy && authSession == null
                && isPointInRect(mouseX, mouseY, authButtonX, buttonY, publishPopup.buttonWidth, publishPopup.buttonHeight)) {
                handleAuthButton();
                return true;
            }
            if (!publishBusy && isPointInRect(mouseX, mouseY, submitButtonX, buttonY, publishPopup.buttonWidth, publishPopup.buttonHeight)) {
                startPublishSubmission();
                return true;
            }
            if (!isPointInRect(mouseX, mouseY, popupX, popupY, popupWidth, popupHeight)) {
                closePublishPopup();
                return true;
            }
            return true;
        }
        if (accountPopupOpen) {
            AccountPopupLayout accountPopup = getAccountPopupLayout(layout);
            int[] bounds = accountPopupAnimation.getScaledPopupBounds(this.width, this.height, accountPopup.width, accountPopup.height);
            int popupX = bounds[0];
            int popupY = bounds[1];
            int popupWidth = bounds[2];
            int popupHeight = bounds[3];
            int closeButtonX = popupX + (accountPopup.closeButtonX - accountPopup.x);
            int signOutButtonX = popupX + (accountPopup.signOutButtonX - accountPopup.x);
            int buttonY = popupY + (accountPopup.buttonY - accountPopup.y);
            if (isPointInRect(mouseX, mouseY, closeButtonX, buttonY, accountPopup.buttonWidth, accountPopup.buttonHeight)) {
                closeAccountPopup();
                return true;
            }
            if (!authBusy && isPointInRect(mouseX, mouseY, signOutButtonX, buttonY, accountPopup.buttonWidth, accountPopup.buttonHeight)) {
                startSignOut();
                return true;
            }
            if (!isPointInRect(mouseX, mouseY, popupX, popupY, popupWidth, popupHeight)) {
                closeAccountPopup();
                return true;
            }
            return true;
        }
        if (popupPreset != null || presetPopupAnimation.isVisible()) {
            if (popupPreset == null || presetPopupClosing) {
                return true;
            }
            PopupLayout popup = getPopupLayout(layout);
            int[] bounds = presetPopupAnimation.getScaledPopupBounds(this.width, this.height, popup.width, popup.height);
            int popupX = bounds[0];
            int popupY = bounds[1];
            int popupWidth = bounds[2];
            int popupHeight = bounds[3];
            ScrollbarHelper.Metrics scrollMetrics = getPopupScrollMetrics(popupX, popupY, popupWidth, popupHeight);
            Rect previewRect = getPopupPreviewRect(popupX, popupY, popupWidth, popupHeight, popupScrollOffset);
            int previewX = previewRect.x;
            int previewY = previewRect.y;
            int previewWidth = previewRect.width;
            int previewHeight = previewRect.height;
            int authButtonX = popupX + (popup.authButtonX - popup.x);
            int deleteButtonX = popupX + (popup.deleteButtonX - popup.x);
            boolean ownPreset = isOwnPreset(popupPreset);
            boolean hasLinkedLocalPreset = findLocalPresetNameForMarketplacePreset(popupPreset).isPresent();
            boolean hasLocalChanges = hasLinkedLocalPresetChanges(popupPreset);
            boolean showUpdateButton = ownPreset && !popupMetadataEditing && hasLinkedLocalPreset;
            int updateButtonX = popupX + 10;
            int downloadButtonX = popupX + ((ownPreset ? popup.downloadButtonX : popup.deleteButtonX) - popup.x);
            int buttonY = popupY + (popup.buttonY - popup.y);
            Rect popupBookmarkHit = new Rect(popupX + popupWidth - 42, popupY + 10, 12, 12);
            Rect popupHeartHit = new Rect(popupX + popupWidth - 24, popupY + 10, 12, 12);
            Rect popupCloseHit = new Rect(popupX + popupWidth - 18, popupY + 8, 14, 14);
            int zoomButtonSize = 14;
            Rect zoomOutHit = new Rect(previewX + previewWidth - zoomButtonSize * 2 - 8, previewY + 6, zoomButtonSize, zoomButtonSize);
            Rect zoomInHit = new Rect(previewX + previewWidth - zoomButtonSize - 6, previewY + 6, zoomButtonSize, zoomButtonSize);
            if (popupMetadataEditing) {
                int fieldX = popupX + 12;
                int fieldWidth = popupWidth - 24;
                int baseContentY = previewY + previewHeight;
                int nameFieldY = baseContentY + 23;
                int tagsFieldY = baseContentY + 93;
                int visibilityPanelTop = baseContentY + 120;
                int descriptionFieldY = baseContentY + 176;
                presetVisibilityToggle.setValue(publishVisibilityPublic);
                presetVisibilityToggle.setPosition(fieldX + fieldWidth - presetVisibilityToggle.getWidth(), visibilityPanelTop + 8);
                boolean clickedField = false;
                if (publishNameField != null && isPointInRect(mouseX, mouseY, fieldX, nameFieldY, fieldWidth, 18)) {
                    focusPublishField(publishNameField);
                    publishNameField.mouseClicked(click, inBounds);
                    clickedField = true;
                } else if (publishTagsField != null && isPointInRect(mouseX, mouseY, fieldX, tagsFieldY, fieldWidth, 18)) {
                    focusPublishField(publishTagsField);
                    publishTagsField.mouseClicked(click, inBounds);
                    clickedField = true;
                } else if (publishDescriptionField != null && isPointInRect(mouseX, mouseY, fieldX + 8, descriptionFieldY, fieldWidth - 16, 18)) {
                    focusPublishField(publishDescriptionField);
                    publishDescriptionField.mouseClicked(click, inBounds);
                    clickedField = true;
                } else if (presetVisibilityToggle.contains(mouseX, mouseY)) {
                    presetVisibilityToggle.mouseClicked(mouseX, mouseY);
                    publishVisibilityPublic = presetVisibilityToggle.getValue();
                    clickedField = true;
                } else {
                    focusPublishField(null);
                }
                if (clickedField) {
                    return true;
                }
            }
            if (isPointInRect(mouseX, mouseY, popupCloseHit.x, popupCloseHit.y, popupCloseHit.width, popupCloseHit.height)) {
                closePopup();
                return true;
            }
            if (isPointInRect(mouseX, mouseY, popupBookmarkHit.x, popupBookmarkHit.y, popupBookmarkHit.width, popupBookmarkHit.height)) {
                savePresetLocally(popupPreset, false);
                return true;
            }
            if (isPointInRect(mouseX, mouseY, popupHeartHit.x, popupHeartHit.y, popupHeartHit.width, popupHeartHit.height)) {
                if (authSession == null) {
                    handleAuthButton();
                } else {
                    startToggleLike();
                }
                return true;
            }
            if (isPointInRect(mouseX, mouseY, zoomOutHit.x, zoomOutHit.y, zoomOutHit.width, zoomOutHit.height)) {
                adjustPopupPreviewZoom(-1);
                return true;
            }
            if (isPointInRect(mouseX, mouseY, zoomInHit.x, zoomInHit.y, zoomInHit.width, zoomInHit.height)) {
                adjustPopupPreviewZoom(1);
                return true;
            }
            if (isPointInRect(mouseX, mouseY, previewX, previewY, previewWidth, previewHeight)) {
                popupPreviewDragging = true;
                popupPreviewDragLastX = mouseX;
                popupPreviewDragLastY = mouseY;
                return true;
            }
            if (scrollMetrics.maxScroll() > 0
                && isPointInRect(mouseX, mouseY, scrollMetrics.trackLeft() - 3, scrollMetrics.trackTop(), scrollMetrics.trackWidth() + 6, scrollMetrics.viewportHeight())) {
                popupScrollDragging = true;
                popupScrollDragOffset = mouseY - scrollMetrics.thumbTop();
                return true;
            }
            if (showUpdateButton
                && isPointInRect(mouseX, mouseY, updateButtonX, buttonY, popup.buttonWidth, popup.buttonHeight)) {
                if (!hasLocalChanges) {
                    popupStatusMessage = "No local changes to upload.";
                    popupStatusColor = UITheme.TEXT_SECONDARY;
                } else if (!publishBusy && !deleteBusy && !importingPreset) {
                    openConfirmPopup(ConfirmAction.UPDATE, popupPreset, true);
                }
                return true;
            }
            if (!authBusy && isPointInRect(mouseX, mouseY, authButtonX, buttonY, popup.buttonWidth, popup.buttonHeight)) {
                if (popupPreset != null && isOwnPreset(popupPreset)) {
                    if (popupMetadataEditing) {
                        startPublishSubmission();
                    } else {
                        beginPopupMetadataEdit(popupPreset);
                    }
                } else if (authSession == null) {
                    handleAuthButton();
                } else {
                    startToggleLike();
                }
                return true;
            }
            if (ownPreset
                && !deleteBusy
                && !publishBusy
                && isPointInRect(mouseX, mouseY, deleteButtonX, buttonY, popup.buttonWidth, popup.buttonHeight)) {
                openConfirmPopup(ConfirmAction.DELETE, popupPreset, true);
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
        if (!authBusy && isPointInRect(mouseX, mouseY, layout.accountButtonX, layout.accountButtonY, ACCOUNT_BUTTON_WIDTH, SORT_BUTTON_HEIGHT)) {
            handleAuthButton();
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
        if (isPointInRect(mouseX, mouseY, layout.myPresetsButtonX, layout.myPresetsButtonY, MY_PRESETS_BUTTON_WIDTH, SORT_BUTTON_HEIGHT)) {
            if (authSession == null) {
                myPresetsOnly = true;
                handleAuthButton();
            } else {
                myPresetsOnly = !myPresetsOnly;
                if (!myPresetsOnly) {
                    myPresetsFilter = MyPresetsFilter.ALL;
                }
                refreshListings();
            }
            if (!myPresetsOnly || authSession == null) {
                applyFilters();
            }
            return true;
        }
        if (myPresetsOnly) {
            int filterY = layout.searchFieldY + SORT_BUTTON_HEIGHT + 6;
            int allX = layout.searchFieldX;
            int publicX = allX + MY_PRESET_FILTER_ALL_WIDTH + 6;
            int privateX = publicX + MY_PRESET_FILTER_PUBLIC_WIDTH + 6;
            if (isPointInRect(mouseX, mouseY, allX, filterY, MY_PRESET_FILTER_ALL_WIDTH, MY_PRESET_FILTER_BUTTON_HEIGHT)) {
                myPresetsFilter = MyPresetsFilter.ALL;
                applyFilters();
                return true;
            }
            if (isPointInRect(mouseX, mouseY, publicX, filterY, MY_PRESET_FILTER_PUBLIC_WIDTH, MY_PRESET_FILTER_BUTTON_HEIGHT)) {
                myPresetsFilter = MyPresetsFilter.PUBLIC;
                applyFilters();
                return true;
            }
            if (isPointInRect(mouseX, mouseY, privateX, filterY, MY_PRESET_FILTER_PRIVATE_WIDTH, MY_PRESET_FILTER_BUTTON_HEIGHT)) {
                myPresetsFilter = MyPresetsFilter.PRIVATE;
                applyFilters();
                return true;
            }
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
            MarketplacePreset preset = presets.get(index);
            int previewX = rect.x + 8;
            int previewY = rect.y + 8;
            int previewWidth = rect.width - 16;
            int previewHeight = rect.height - 46;
            Rect deleteHit = new Rect(previewX + previewWidth - 14, previewY + 2, 12, 12);
            Rect heartHit = new Rect(previewX + previewWidth - 14, previewY + previewHeight - 14, 12, 12);
            Rect bookmarkHit = new Rect(previewX + previewWidth - 28, previewY + previewHeight - 14, 12, 12);
            if (isOwnPreset(preset) && isPointInRect(mouseX, mouseY, deleteHit.x, deleteHit.y, deleteHit.width, deleteHit.height)) {
                openConfirmPopup(ConfirmAction.DELETE, preset, false);
                return true;
            }
            if (isPointInRect(mouseX, mouseY, heartHit.x, heartHit.y, heartHit.width, heartHit.height)) {
                startToggleLike(preset, false);
                return true;
            }
            if (isPointInRect(mouseX, mouseY, bookmarkHit.x, bookmarkHit.y, bookmarkHit.width, bookmarkHit.height)) {
                savePresetLocally(preset, false);
                return true;
            }
            if (isPointInRect(mouseX, mouseY, rect.x, rect.y, rect.width, rect.height)) {
                selectedIndex = index;
                popupPreset = preset;
                popupScrollOffset = 0;
                popupPreviewPanX = 0f;
                popupPreviewPanY = 0f;
                popupPreviewZoom = 1f;
                popupStatusMessage = "";
                popupStatusColor = UITheme.TEXT_SECONDARY;
                requestPreviewGraph(popupPreset);
                presetPopupAnimation.show();
                return true;
            }
        }

        return super.mouseClicked(click, inBounds);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (presetPopupClosing) {
            return true;
        }
        if (popupPreviewDragging && popupPreset != null) {
            int currentX = (int) click.x();
            int currentY = (int) click.y();
            popupPreviewPanX += currentX - popupPreviewDragLastX;
            popupPreviewPanY += currentY - popupPreviewDragLastY;
            popupPreviewDragLastX = currentX;
            popupPreviewDragLastY = currentY;
            return true;
        }
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
        if (popupPreviewDragging) {
            popupPreviewDragging = false;
            return true;
        }
        if (popupScrollDragging) {
            popupScrollDragging = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        Layout layout = getLayout();
        if (popupPreset != null || presetPopupAnimation.isVisible()) {
            if (popupPreset == null || presetPopupClosing) {
                return true;
            }
            PopupLayout popup = getPopupLayout(layout);
            int[] bounds = presetPopupAnimation.getScaledPopupBounds(this.width, this.height, popup.width, popup.height);
            Rect previewRect = getPopupPreviewRect(bounds[0], bounds[1], bounds[2], bounds[3], popupScrollOffset);
            if (isPointInRect((int) mouseX, (int) mouseY, previewRect.x, previewRect.y, previewRect.width, previewRect.height)
                && Math.abs(verticalAmount) > 0.0001) {
                adjustPopupPreviewZoom(verticalAmount > 0 ? 1 : -1);
                return true;
            }
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
        if (pendingConfirmAction != null || confirmPopupAnimation.isVisible()) {
            closeConfirmPopup();
            return;
        }
        if (publishPopupOpen) {
            closePublishPopup();
            return;
        }
        if (accountPopupOpen) {
            closeAccountPopup();
            return;
        }
        if (popupPreset != null || presetPopupAnimation.isVisible()) {
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
        if (pendingConfirmAction != null || confirmPopupAnimation.isVisible()) {
            if (pendingConfirmAction == null) {
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                closeConfirmPopup();
                return true;
            }
            return true;
        }
        if (presetPopupClosing) {
            return true;
        }
        if (publishPopupOpen || popupMetadataEditing) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                if (publishPopupOpen) {
                    closePublishPopup();
                } else {
                    endPopupMetadataEdit(false);
                }
                return true;
            }
            if (publishNameField != null && publishNameField.isFocused() && publishNameField.keyPressed(input)) {
                return true;
            }
            if (publishDescriptionField != null && publishDescriptionField.isFocused() && publishDescriptionField.keyPressed(input)) {
                return true;
            }
            if (publishTagsField != null && publishTagsField.isFocused() && publishTagsField.keyPressed(input)) {
                return true;
            }
            if ((input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) && !publishBusy) {
                startPublishSubmission();
                return true;
            }
            return true;
        }
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
        if (publishPopupOpen || popupMetadataEditing) {
            if (publishNameField != null && publishNameField.isFocused() && publishNameField.charTyped(input)) {
                return true;
            }
            if (publishDescriptionField != null && publishDescriptionField.isFocused() && publishDescriptionField.charTyped(input)) {
                return true;
            }
            if (publishTagsField != null && publishTagsField.isFocused() && publishTagsField.charTyped(input)) {
                return true;
            }
            return true;
        }
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
        drawActionButton(context, x, y, width, height, label, hovered, disabled, false);
    }

    private void drawActionButton(DrawContext context, int x, int y, int width, int height, String label,
                                  boolean hovered, boolean disabled, boolean active) {
        UIStyleHelper.TextButtonPalette palette = UIStyleHelper.getTextButtonPalette(
            active ? UIStyleHelper.TextButtonStyle.PRIMARY : UIStyleHelper.TextButtonStyle.DEFAULT,
            getAccentColor(),
            hovered || active,
            disabled
        );
        UIStyleHelper.drawToolbarButtonFrame(context, x, y, width, height,
            palette.backgroundColor(), palette.borderColor(), palette.innerBorderColor());
        int textColor = palette.textColor();
        int textX = x + (width - this.textRenderer.getWidth(label)) / 2;
        int textY = y + (height - this.textRenderer.fontHeight) / 2;
        context.drawTextWithShadow(this.textRenderer, Text.literal(label), textX, textY, textColor);
    }

    private void drawAnimatedActionButton(DrawContext context, int x, int y, int width, int height, String label,
                                          boolean hovered, boolean disabled, PopupAnimationHandler animation) {
        drawAnimatedActionButton(context, x, y, width, height, label, hovered, disabled, animation, hovered ? 1f : 0f);
    }

    private void drawAnimatedActionButton(DrawContext context, int x, int y, int width, int height, String label,
                                          boolean hovered, boolean disabled, PopupAnimationHandler animation, float hoverProgress) {
        float easedHover = AnimationHelper.easeOutQuad(Math.max(0f, Math.min(1f, hoverProgress)));
        UIStyleHelper.TextButtonPalette palette = UIStyleHelper.getTextButtonPalette(
            UIStyleHelper.TextButtonStyle.DEFAULT,
            getAccentColor(),
            easedHover,
            disabled
        );
        if (!disabled && easedHover > 0.001f) {
            int glowColor = animation.getAnimatedPopupColor(AnimationHelper.lerpColor(getAccentColor(), 0xFFFFFFFF, 0.22f));
            int alpha = Math.min(84, Math.round(72f * easedHover));
            context.fill(x - 1, y - 1, x + width + 1, y + height + 1, (alpha << 24) | (glowColor & 0x00FFFFFF));
        }
        UIStyleHelper.drawToolbarButtonFrame(
            context,
            x,
            y,
            width,
            height,
            animation.getAnimatedPopupColor(palette.backgroundColor()),
            animation.getAnimatedPopupColor(palette.borderColor()),
            animation.getAnimatedPopupColor(palette.innerBorderColor())
        );
        int textX = x + (width - this.textRenderer.getWidth(label)) / 2;
        int textY = y + (height - this.textRenderer.fontHeight) / 2;
        context.drawTextWithShadow(this.textRenderer, Text.literal(label), textX, textY,
            animation.getAnimatedPopupColor(palette.textColor()));
    }

    private void drawPopupCloseIcon(DrawContext context, int x, int y, int color) {
        context.fill(x, y, x + 1, y + 1, color);
        context.fill(x + 7, y, x + 8, y + 1, color);
        context.fill(x + 1, y + 1, x + 2, y + 2, color);
        context.fill(x + 6, y + 1, x + 7, y + 2, color);
        context.fill(x + 2, y + 2, x + 3, y + 3, color);
        context.fill(x + 5, y + 2, x + 6, y + 3, color);
        context.fill(x + 3, y + 3, x + 4, y + 4, color);
        context.fill(x + 4, y + 3, x + 5, y + 4, color);
        context.fill(x + 2, y + 4, x + 3, y + 5, color);
        context.fill(x + 5, y + 4, x + 6, y + 5, color);
        context.fill(x + 1, y + 5, x + 2, y + 6, color);
        context.fill(x + 6, y + 5, x + 7, y + 6, color);
        context.fill(x, y + 6, x + 1, y + 7, color);
        context.fill(x + 7, y + 6, x + 8, y + 7, color);
    }

    private void drawAnimatedActionButton(DrawContext context, int x, int y, int width, int height, String label,
                                          boolean hovered, boolean disabled) {
        drawAnimatedActionButton(context, x, y, width, height, label, hovered, disabled, presetPopupAnimation);
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

    private void drawPopupStatPill(DrawContext context, int x, int y, int width, String label, String value) {
        UIStyleHelper.drawBeveledPanel(
            context,
            x,
            y,
            width,
            16,
            presetPopupAnimation.getAnimatedPopupColor(UITheme.BACKGROUND_SECTION),
            presetPopupAnimation.getAnimatedPopupColor(UITheme.BORDER_SUBTLE),
            presetPopupAnimation.getAnimatedPopupColor(UITheme.PANEL_INNER_BORDER)
        );
        context.drawTextWithShadow(this.textRenderer, Text.literal(label), x + 6, y + 4,
            presetPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_TERTIARY));
        String shownValue = TextRenderUtil.trimWithEllipsis(this.textRenderer, value, Math.max(10, width - 12));
        context.drawTextWithShadow(this.textRenderer, Text.literal(shownValue),
            x + width - 6 - this.textRenderer.getWidth(shownValue), y + 4,
            presetPopupAnimation.getAnimatedPopupColor(getAccentColor()));
    }

    private void drawPrivateEyeIcon(DrawContext context, int x, int y, int color) {
        context.drawHorizontalLine(x + 2, x + 8, y + 4, color);
        context.drawHorizontalLine(x + 1, x + 9, y + 5, color);
        context.drawHorizontalLine(x + 2, x + 8, y + 6, color);
        context.fill(x + 5, y + 4, x + 6, y + 7, color);
        context.fill(x + 4, y + 5, x + 7, y + 6, color);
    }

    private int measureWrappedValueHeight(int width, String value, int maxLines) {
        return wrapText(value, width, maxLines).size() * 10 + 2;
    }

    private int measurePopupContentHeight(int textWidth) {
        if (popupPreset == null) {
            return 0;
        }
        int height = 120 + 12;
        height += popupMetadataEditing ? 32 : 14;
        height += 14;
        height += 20;
        if (popupMetadataEditing) {
            height += 31;
        } else {
            String tagsLine = formatTags(popupPreset.getTags());
            if (!tagsLine.isBlank() && !"untagged".equalsIgnoreCase(tagsLine)) {
                height += measureWrappedValueHeight(textWidth, "Tags: " + tagsLine, 2);
            }
        }
        height += 30 + 18;
        height += popupMetadataEditing ? 8 : 0;
        height += 30 + 8;
        height += measureWrappedValueHeight(textWidth,
            "Published " + formatTimestamp(popupPreset.getCreatedAt()) + "  •  Updated " + formatTimestamp(popupPreset.getUpdatedAt()),
            2);
        if (authSession == null) {
            height += measureWrappedValueHeight(textWidth,
                "Sign in with Discord to like presets and count imports.",
                2);
        }
        height += 2;
        height += popupMetadataEditing
            ? 46
            : Math.max(42, measureWrappedValueHeight(textWidth - 16, fallback(popupPreset.getDescription(), "No description provided."), 5) + 18);
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
        if (importingPreset || popupPreset == null || presetPopupClosing) {
            return;
        }
        endPopupMetadataEdit(false);
        popupScrollDragging = false;
        popupScrollDragOffset = 0;
        popupPreviewDragging = false;
        presetPopupClosing = true;
        returnToParentAfterPresetClose = editorPopupMode;
        presetPopupAnimation.hide();
    }

    private void openConfirmPopup(ConfirmAction action, MarketplacePreset preset, boolean deleteFromPopup) {
        if (action == null) {
            return;
        }
        if (action == ConfirmAction.DELETE && skipMarketplaceDeleteConfirm && preset != null) {
            startDeletePreset(preset, deleteFromPopup);
            return;
        }
        if (action == ConfirmAction.UPDATE && skipMarketplaceUpdateConfirm) {
            startUpdateFromLinkedLocalPreset();
            return;
        }
        pendingConfirmAction = action;
        pendingConfirmPreset = preset;
        pendingConfirmDeleteFromPopup = deleteFromPopup;
        renderConfirmAction = action;
        confirmPopupClosing = false;
        confirmPopupAnimation.show();
    }

    private void closeConfirmPopup() {
        pendingConfirmAction = null;
        pendingConfirmPreset = null;
        pendingConfirmDeleteFromPopup = false;
        confirmPopupClosing = true;
        confirmPopupAnimation.hide();
    }

    private void confirmPendingAction() {
        ConfirmAction action = pendingConfirmAction;
        MarketplacePreset confirmPreset = pendingConfirmPreset;
        boolean deleteFromPopup = pendingConfirmDeleteFromPopup;
        closeConfirmPopup();
        if (action == null) {
            return;
        }
        if (action == ConfirmAction.UPDATE) {
            startUpdateFromLinkedLocalPreset();
        } else if (action == ConfirmAction.DELETE && confirmPreset != null) {
            startDeletePreset(confirmPreset, deleteFromPopup);
        }
    }

    private void setSkipMarketplaceDeleteConfirm(boolean skip) {
        skipMarketplaceDeleteConfirm = skip;
        SettingsManager.Settings settings = SettingsManager.getCurrent();
        settings.skipMarketplaceDeleteConfirm = skip;
        SettingsManager.save(settings);
    }

    private void setSkipMarketplaceUpdateConfirm(boolean skip) {
        skipMarketplaceUpdateConfirm = skip;
        SettingsManager.Settings settings = SettingsManager.getCurrent();
        settings.skipMarketplaceUpdateConfirm = skip;
        SettingsManager.save(settings);
    }

    private void startPresetImport() {
        if (popupPreset == null || importingPreset) {
            return;
        }
        importingPreset = true;
        popupStatusMessage = "Downloading preset...";
        popupStatusColor = UITheme.TEXT_SECONDARY;

        MarketplaceService.downloadPresetToTempFile(popupPreset, authSession == null ? null : authSession.getAccessToken()).whenComplete((path, throwable) -> {
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
            refreshSavedPresetState();
            if (authSession != null && popupPreset != null && popupPreset.getId() != null && !popupPreset.getId().isBlank()) {
                MarketplacePreset importedMarketplacePreset = popupPreset;
                withFreshAuthSession(session -> {
                    MarketplaceRateLimitManager.LimitCheck limitCheck = MarketplaceRateLimitManager.tryConsumeDownloadCount(
                        session.getUserId(),
                        importedMarketplacePreset.getId()
                    );
                    if (!limitCheck.permitted()) {
                        return;
                    }
                    MarketplaceService.incrementDownload(session.getAccessToken(), importedMarketplacePreset.getId())
                        .whenComplete((unused, incrementThrowable) -> {
                            if (incrementThrowable == null && this.client != null) {
                                this.client.execute(() -> applyPresetCountUpdate(importedMarketplacePreset.getId(), 0, 1));
                            }
                        });
                }, null);
            }
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

    private void refreshSavedPresetState() {
        PresetManager.getAvailablePresets();
        savedMarketplacePresetIds.clear();
        savedMarketplacePresetIds.addAll(PresetManager.getSavedMarketplacePresetIds());
    }

    private boolean isPresetSavedLocally(MarketplacePreset preset) {
        return preset != null && preset.getId() != null && savedMarketplacePresetIds.contains(preset.getId());
    }

    private String normalizeSavedPresetKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '-');
    }

    private void savePresetLocally(MarketplacePreset preset, boolean activateAfterImport) {
        if (preset == null || importingPreset) {
            return;
        }
        if (!activateAfterImport) {
            if (isPresetSavedLocally(preset)) {
                Optional<String> existingPresetName = findLocalPresetNameForMarketplacePreset(preset);
                boolean deleted = existingPresetName.isPresent() && PresetManager.deletePreset(existingPresetName.get());
                if (deleted && preset.getId() != null) {
                    PresetManager.setMarketplacePresetSaved(preset.getId(), false);
                    savedMarketplacePresetIds.remove(preset.getId());
                }
                refreshSavedPresetState();
                applyFilters();
                if (deleted) {
                    triggerSavePulse(preset);
                }
                if (popupPreset != null && popupPreset.getId() != null && popupPreset.getId().equals(preset.getId())) {
                    popupStatusMessage = deleted ? "Removed local save." : "Failed to remove local save.";
                    popupStatusColor = deleted ? UITheme.TEXT_SECONDARY : UITheme.STATE_ERROR;
                }
                return;
            }
        }
        importingPreset = true;
        if (popupPreset != null) {
            popupStatusMessage = isPresetSavedLocally(preset) ? "Already saved locally." : "Saving preset locally...";
            popupStatusColor = UITheme.TEXT_SECONDARY;
        }
        MarketplaceService.downloadPresetToTempFile(preset, authSession == null ? null : authSession.getAccessToken()).whenComplete((path, throwable) -> {
            if (this.client == null) {
                return;
            }
            this.client.execute(() -> finishSavePresetLocally(preset, path, throwable, activateAfterImport));
        });
    }

    private void finishSavePresetLocally(MarketplacePreset preset, Path path, Throwable throwable, boolean activateAfterImport) {
        if (throwable != null || path == null) {
            importingPreset = false;
            if (popupPreset != null) {
                popupStatusMessage = "Failed to save preset locally.";
                popupStatusColor = UITheme.STATE_ERROR;
            }
            cleanupTempFile(path);
            return;
        }
        try {
            String requestedName = fallback(preset.getSlug(), preset.getName());
            Optional<String> importedPreset = PresetManager.importPresetFromFile(path, requestedName);
            cleanupTempFile(path);
            importingPreset = false;
            if (importedPreset.isEmpty()) {
                if (popupPreset != null) {
                    popupStatusMessage = "Failed to save preset locally.";
                    popupStatusColor = UITheme.STATE_ERROR;
                }
                return;
            }
            if (preset.getId() != null) {
                PresetManager.setMarketplacePresetSaved(preset.getId(), true);
                savedMarketplacePresetIds.add(preset.getId());
            }
            refreshSavedPresetState();
            applyFilters();
            triggerSavePulse(preset);
            if (popupPreset != null) {
                popupStatusMessage = "Saved locally as \"" + importedPreset.get() + "\".";
                popupStatusColor = 0xFFE2B93B;
            }
            if (activateAfterImport) {
                PresetManager.setActivePreset(importedPreset.get());
                if (this.client != null) {
                    PathmindScreens.openVisualEditorOrWarn(this.client, parent);
                }
            }
        } catch (Exception e) {
            importingPreset = false;
            cleanupTempFile(path);
            if (popupPreset != null) {
                popupStatusMessage = "Failed to save preset locally.";
                popupStatusColor = UITheme.STATE_ERROR;
            }
        }
    }

    private void refreshAuthState(boolean silent) {
        authBusy = true;
        MarketplaceAuthManager.ensureValidSession().whenComplete((session, throwable) -> {
            if (this.client == null) {
                return;
            }
            this.client.execute(() -> {
                authBusy = false;
                if (throwable != null || session == null) {
                    authSession = null;
                    likedPresetIds.clear();
                    if (myPresetsOnly) {
                        refreshListings();
                    } else {
                        applyFilters();
                    }
                    if (!silent && popupPreset != null) {
                        popupStatusMessage = "Sign in to like presets and count downloads.";
                        popupStatusColor = UITheme.TEXT_TERTIARY;
                    }
                    return;
                }
                authSession = session;
                if (myPresetsOnly) {
                    refreshListings();
                } else {
                    applyFilters();
                }
                refreshLikedPresetIds(silent);
            });
        });
    }

    private void refreshLikedPresetIds(boolean silent) {
        if (authSession == null || authSession.getUserId() == null || authSession.getUserId().isBlank()) {
            likedPresetIds.clear();
            return;
        }
        authBusy = true;
        MarketplaceService.fetchLikedPresetIds(authSession.getAccessToken(), authSession.getUserId())
            .whenComplete((likedPresetIds, throwable) -> {
                if (this.client == null) {
                    return;
                }
                this.client.execute(() -> {
                    authBusy = false;
                    if (throwable != null || likedPresetIds == null) {
                        if (!silent && popupPreset != null) {
                            popupStatusMessage = "Failed to load your likes.";
                            popupStatusColor = UITheme.STATE_ERROR;
                        }
                        return;
                    }
                    this.likedPresetIds.clear();
                    this.likedPresetIds.addAll(likedPresetIds);
                    if (!silent && popupPreset != null) {
                        popupStatusMessage = "Signed in as " + fallback(authSession.getDisplayName(), fallback(authSession.getEmail(), "Discord user")) + ".";
                        popupStatusColor = getAccentColor();
                    }
                });
            });
    }

    private void handleAuthButton() {
        if (authBusy) {
            return;
        }
        if (authSession == null) {
            authBusy = true;
            if (popupPreset != null) {
                popupStatusMessage = "Opening Discord sign-in...";
                popupStatusColor = UITheme.TEXT_SECONDARY;
            }
            MarketplaceAuthManager.startDiscordSignIn().whenComplete((session, throwable) -> {
                if (this.client == null) {
                    return;
                }
                this.client.execute(() -> {
                    authBusy = false;
                    if (throwable != null || session == null) {
                        if (popupPreset != null) {
                            popupStatusMessage = "Discord sign-in failed.";
                            popupStatusColor = UITheme.STATE_ERROR;
                        }
                        return;
                    }
                    authSession = session;
                    if (myPresetsOnly) {
                        refreshListings();
                    } else {
                        applyFilters();
                    }
                    refreshLikedPresetIds(false);
                });
            });
            return;
        }
        accountPopupOpen = true;
        accountPopupAnimation.show();
    }

    private void startSignOut() {
        authBusy = true;
        MarketplaceAuthManager.signOut().whenComplete((unused, throwable) -> {
            if (this.client == null) {
                return;
            }
            this.client.execute(() -> {
                authBusy = false;
                authSession = null;
                likedPresetIds.clear();
                closeAccountPopup();
                if (myPresetsOnly) {
                    refreshListings();
                } else {
                    applyFilters();
                }
                if (popupPreset != null) {
                    popupStatusMessage = throwable == null ? "Signed out." : "Failed to sign out cleanly.";
                    popupStatusColor = throwable == null ? UITheme.TEXT_SECONDARY : UITheme.STATE_ERROR;
                }
            });
        });
    }

    private void closeAccountPopup() {
        accountPopupOpen = false;
        accountPopupAnimation.hide();
    }

    private void openPublishPopup(String presetName) {
        editingPreset = null;
        publishSourcePresetName = fallback(presetName, PresetManager.getActivePreset());
        publishStatusMessage = "";
        publishStatusColor = UITheme.TEXT_SECONDARY;
        if (publishNameField != null) {
            publishNameField.setText(publishSourcePresetName);
        }
        if (publishDescriptionField != null) {
            publishDescriptionField.setText("");
        }
        if (publishTagsField != null) {
            publishTagsField.setText("");
        }
        publishVisibilityPublic = true;
        focusPublishField(publishNameField);
        publishPopupOpen = true;
        publishPopupAnimation.show();
    }

    private void openPresetPopup(MarketplacePreset preset, String statusMessage, int statusColor) {
        if (preset == null) {
            return;
        }
        selectedIndex = Math.max(0, presets.indexOf(preset));
        popupPreset = preset;
        popupScrollOffset = 0;
        popupPreviewPanX = 0f;
        popupPreviewPanY = 0f;
        popupPreviewZoom = 1f;
        popupMetadataEditing = false;
        editingPreset = null;
        publishSourcePresetName = "";
        presetPopupClosing = false;
        returnToParentAfterPresetClose = false;
        popupStatusMessage = fallback(statusMessage, "");
        popupStatusColor = statusColor;
        requestPreviewGraph(popupPreset);
        presetPopupAnimation.show();
    }

    private void cleanupClosingPopups() {
        if (confirmPopupClosing && confirmPopupAnimation.isFullyHidden()) {
            renderConfirmAction = null;
            confirmPopupClosing = false;
        }
        if (presetPopupClosing && presetPopupAnimation.isFullyHidden()) {
            popupPreset = null;
            popupScrollOffset = 0;
            popupScrollDragging = false;
            popupScrollDragOffset = 0;
            popupPreviewDragging = false;
            popupPreviewPanX = 0f;
            popupPreviewPanY = 0f;
            popupPreviewZoom = 1f;
            presetPopupClosing = false;
            boolean returnToParent = returnToParentAfterPresetClose;
            returnToParentAfterPresetClose = false;
            if (returnToParent && this.client != null) {
                this.client.setScreen(parent);
            }
        }
    }

    private void openEditMetadataPopup(MarketplacePreset preset) {
        beginPopupMetadataEdit(preset);
    }

    private void beginPopupMetadataEdit(MarketplacePreset preset) {
        if (preset == null) {
            return;
        }
        popupMetadataEditing = true;
        editingPreset = preset;
        publishSourcePresetName = fallback(findLocalPresetNameForMarketplacePreset(preset).orElse(null), preset.getName());
        popupStatusMessage = "";
        popupStatusColor = UITheme.TEXT_SECONDARY;
        if (publishNameField != null) {
            publishNameField.setText(fallback(preset.getName(), ""));
        }
        if (publishDescriptionField != null) {
            publishDescriptionField.setText(fallback(preset.getDescription(), ""));
        }
        if (publishTagsField != null) {
            publishTagsField.setText(String.join(", ", preset.getTags()));
        }
        publishVisibilityPublic = preset.isPublished();
        focusPublishField(publishNameField);
    }

    private void endPopupMetadataEdit(boolean keepStatus) {
        popupMetadataEditing = false;
        editingPreset = null;
        publishBusy = false;
        publishSourcePresetName = "";
        focusPublishField(null);
        if (!keepStatus) {
            popupStatusMessage = "";
            popupStatusColor = UITheme.TEXT_SECONDARY;
        }
    }

    private void closePublishPopup() {
        publishPopupOpen = false;
        publishBusy = false;
        editingPreset = null;
        publishSourcePresetName = "";
        publishStatusMessage = "";
        publishStatusColor = UITheme.TEXT_SECONDARY;
        focusPublishField(null);
        publishPopupAnimation.hide();
    }

    private void focusPublishField(TextFieldWidget target) {
        if (publishNameField != null) {
            publishNameField.setFocused(publishNameField == target);
        }
        if (publishDescriptionField != null) {
            publishDescriptionField.setFocused(publishDescriptionField == target);
        }
        if (publishTagsField != null) {
            publishTagsField.setFocused(publishTagsField == target);
        }
    }

    private void setActiveSubmissionStatus(String message, int color) {
        if (popupMetadataEditing) {
            popupStatusMessage = fallback(message, "");
            popupStatusColor = color;
            return;
        }
        publishStatusMessage = fallback(message, "");
        publishStatusColor = color;
    }

    private String extractThrowableMessage(Throwable throwable, String fallbackMessage) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                return message;
            }
            current = current.getCause();
        }
        return fallbackMessage;
    }

    private void renderAccountAvatar(DrawContext context, int x, int y, int size) {
        int background = accountPopupAnimation.getAnimatedPopupColor(UITheme.BACKGROUND_PRIMARY);
        int border = accountPopupAnimation.getAnimatedPopupColor(getAccentColor());
        UIStyleHelper.drawBeveledPanel(context, x, y, size, size, background, border, accountPopupAnimation.getAnimatedPopupColor(UITheme.PANEL_INNER_BORDER));

        Identifier avatarTexture = getOrRequestAvatarTexture();
        if (avatarTexture != null) {
            GuiTextureRenderer.drawIcon(
                context,
                avatarTexture,
                x + 3,
                y + 3,
                size - 6,
                accountPopupAnimation.getAnimatedPopupColor(0xFFFFFFFF)
            );
        } else {
            String initials = fallback(authSession == null ? null : authSession.getDisplayName(), "D").trim();
            initials = initials.isEmpty() ? "D" : initials.substring(0, 1).toUpperCase(Locale.ROOT);
            int textX = x + (size - this.textRenderer.getWidth(initials)) / 2;
            int textY = y + (size - this.textRenderer.fontHeight) / 2;
            context.drawTextWithShadow(this.textRenderer, Text.literal(initials), textX, textY, accountPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_HEADER));
        }

        float popupAlpha = accountPopupAnimation.getPopupAlpha();
        if (popupAlpha < 0.999f) {
            int fadeAlpha = Math.max(0, Math.min(255, Math.round((1f - popupAlpha) * 255f)));
            context.fill(x + 2, y + 2, x + size - 2, y + size - 2,
                (fadeAlpha << 24) | (UITheme.BACKGROUND_SECONDARY & 0x00FFFFFF));
        }
    }

    private Identifier getOrRequestAvatarTexture() {
        if (authSession == null || authSession.getAvatarUrl() == null || authSession.getAvatarUrl().isBlank()) {
            return null;
        }
        if (authSession.getAvatarUrl().equals(avatarTextureUrl) && avatarTextureId != null) {
            return avatarTextureId;
        }
        if (avatarLoading) {
            return avatarTextureId;
        }
        avatarLoading = true;
        String avatarUrl = authSession.getAvatarUrl();
        CompletableFuture.supplyAsync(() -> downloadAvatarImage(avatarUrl)).whenComplete((image, throwable) -> {
            if (this.client == null) {
                avatarLoading = false;
                return;
            }
            this.client.execute(() -> {
                avatarLoading = false;
                if (throwable == null && image != null) {
                    avatarTextureUrl = avatarUrl;
                    avatarTextureId = registerAvatarTexture(avatarUrl, image);
                }
            });
        });
        return avatarTextureId;
    }

    private NativeImage downloadAvatarImage(String avatarUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(avatarUrl))
                .GET()
                .build();
            HttpResponse<InputStream> response = AVATAR_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            try (InputStream input = response.body()) {
                return NativeImage.read(input);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private Identifier registerAvatarTexture(String avatarUrl, NativeImage image) {
        if (this.client == null || image == null) {
            return null;
        }
        NativeImageBackedTexture texture = TextureCompatibilityBridge.createNativeImageBackedTexture("pathmind_marketplace_avatar", image);
        Identifier id = Identifier.of("pathmind", "textures/dynamic/marketplace_avatar_" + Integer.toHexString(avatarUrl.hashCode()));
        this.client.getTextureManager().registerTexture(id, texture);
        return id;
    }

    private void startToggleLike() {
        startToggleLike(popupPreset, true);
    }

    private void startPublishSubmission() {
        if (publishBusy) {
            return;
        }
        boolean inlineMetadataEdit = popupMetadataEditing && editingPreset != null;
        String name = publishNameField == null ? "" : publishNameField.getText().trim();
        if (name.isEmpty()) {
            setActiveSubmissionStatus("Enter a preset name.", UITheme.STATE_ERROR);
            focusPublishField(publishNameField);
            return;
        }
        if (authSession == null) {
            setActiveSubmissionStatus("Sign in before publishing.", UITheme.STATE_WARNING);
            handleAuthButton();
            return;
        }

        Path localPresetPath = editingPreset == null ? PresetManager.getPresetPath(publishSourcePresetName) : null;
        if (editingPreset == null && (localPresetPath == null || !Files.exists(localPresetPath))) {
            setActiveSubmissionStatus("The selected local preset could not be found.", UITheme.STATE_ERROR);
            return;
        }

        publishBusy = true;
        authBusy = true;
        setActiveSubmissionStatus(editingPreset == null ? "Publishing preset..." : "Saving metadata...", UITheme.TEXT_SECONDARY);

        String slugSource = editingPreset != null && name.equalsIgnoreCase(fallback(editingPreset.getName(), ""))
            ? fallback(editingPreset.getSlug(), name)
            : name;
        MarketplaceService.PublishRequest request = new MarketplaceService.PublishRequest(
            localPresetPath,
            editingPreset == null ? null : editingPreset.getStorageBucket(),
            editingPreset == null ? null : editingPreset.getFilePath(),
            sanitizeSlug(slugSource),
            name,
            fallback(authSession.getDisplayName(), fallback(authSession.getEmail(), "Discord user")),
            publishDescriptionField == null ? "" : publishDescriptionField.getText().trim(),
            parseTags(publishTagsField == null ? "" : publishTagsField.getText()),
            this.client != null ? this.client.getGameVersion() : "Unknown",
            getInstalledPathmindVersion(),
            publishVisibilityPublic
        );

        MarketplaceAuthManager.ensureValidSession().whenComplete((session, throwable) -> {
            if (this.client == null) {
                return;
            }
            this.client.execute(() -> {
                if (throwable != null || session == null) {
                    publishBusy = false;
                    authBusy = false;
                    authSession = null;
                    setActiveSubmissionStatus("Session expired. Sign in again.", UITheme.STATE_ERROR);
                    return;
                }
                authSession = session;
                if (editingPreset == null) {
                    MarketplaceRateLimitManager.LimitCheck limitCheck = MarketplaceRateLimitManager.tryConsumePublish(session.getUserId());
                    if (!limitCheck.permitted()) {
                        publishBusy = false;
                        authBusy = false;
                        setActiveSubmissionStatus(limitCheck.message(), UITheme.STATE_WARNING);
                        return;
                    }
                }
                CompletableFuture<MarketplacePreset> submitFuture = editingPreset == null
                    ? MarketplaceService.publishPreset(session.getAccessToken(), session.getUserId(), request)
                    : MarketplaceService.updatePresetMetadata(session.getAccessToken(), editingPreset, request);
                submitFuture.whenComplete((preset, submitThrowable) -> {
                    if (this.client == null) {
                        return;
                    }
                    this.client.execute(() -> finishPublishSubmission(preset, submitThrowable));
                });
            });
        });
    }

    private void finishPublishSubmission(MarketplacePreset preset, Throwable throwable) {
        boolean wasEditing = editingPreset != null;
        boolean inlineMetadataEdit = popupMetadataEditing && wasEditing;
        String sourcePresetName = publishSourcePresetName;
        publishBusy = false;
        authBusy = false;
        if (throwable != null) {
            setActiveSubmissionStatus(extractThrowableMessage(throwable, wasEditing ? "Metadata update failed." : "Publish failed."), UITheme.STATE_ERROR);
            return;
        }
        if (preset != null) {
            upsertPreset(preset);
        } else {
            refreshListings();
        }
        statusMessage = wasEditing ? "Metadata updated." : "Preset published.";
        if (inlineMetadataEdit && popupPreset != null && preset != null && preset.getId() != null && preset.getId().equals(popupPreset.getId())) {
            popupStatusMessage = "Metadata updated.";
            popupStatusColor = getAccentColor();
            endPopupMetadataEdit(true);
            applyFilters();
            return;
        }
        if (preset != null && sourcePresetName != null && !sourcePresetName.isBlank()) {
            PresetManager.setMarketplaceLinkedPreset(sourcePresetName, preset.getId());
        }
        closePublishPopup();
        if (preset != null) {
            openPresetPopup(preset, wasEditing ? "Preset updated." : "Preset published.", getAccentColor());
        }
        applyFilters();
    }

    private void startToggleLike(MarketplacePreset target, boolean updatePopupStatus) {
        if (target == null || authBusy) {
            return;
        }
        if (authSession == null) {
            handleAuthButton();
            return;
        }
        MarketplaceRateLimitManager.LimitCheck limitCheck = MarketplaceRateLimitManager.tryConsumeLike(authSession.getUserId(), target.getId());
        if (!limitCheck.permitted()) {
            if (updatePopupStatus && popupPreset != null && popupPreset.getId() != null && popupPreset.getId().equals(target.getId())) {
                popupStatusMessage = limitCheck.message();
                popupStatusColor = UITheme.STATE_WARNING;
            }
            return;
        }
        authBusy = true;
        pendingLikePresetId = target.getId();
        if (updatePopupStatus && popupPreset != null && popupPreset.getId() != null && popupPreset.getId().equals(target.getId())) {
            popupStatusMessage = isPresetLiked(target) ? "Removing like..." : "Saving like...";
            popupStatusColor = UITheme.TEXT_SECONDARY;
        }
        withFreshAuthSession(session -> MarketplaceService.toggleLike(session.getAccessToken(), target.getId(), session.getUserId())
            .whenComplete((liked, throwable) -> {
                if (this.client == null) {
                    return;
                }
                this.client.execute(() -> {
                    authBusy = false;
                    pendingLikePresetId = null;
                    if (throwable != null || liked == null) {
                        if (updatePopupStatus && popupPreset != null && popupPreset.getId() != null && popupPreset.getId().equals(target.getId())) {
                            popupStatusMessage = "Like update failed.";
                            popupStatusColor = UITheme.STATE_ERROR;
                        }
                        return;
                    }
                    setPresetLiked(target.getId(), liked);
                    triggerLikePulse(target);
                    applyPresetCountUpdate(target.getId(), liked ? 1 : -1, 0);
                    if (updatePopupStatus && popupPreset != null && popupPreset.getId() != null && popupPreset.getId().equals(target.getId())) {
                        popupStatusMessage = liked ? "Preset liked." : "Like removed.";
                        popupStatusColor = getAccentColor();
                    }
                });
            }),
            updatePopupStatus ? "Session expired. Sign in again." : null);
    }

    private void withFreshAuthSession(Consumer<MarketplaceAuthManager.AuthSession> action, String failureMessage) {
        MarketplaceAuthManager.ensureValidSession().whenComplete((session, throwable) -> {
            if (this.client == null) {
                return;
            }
            this.client.execute(() -> {
                if (throwable != null || session == null) {
                    authBusy = false;
                    pendingLikePresetId = null;
                    deleteBusy = false;
                    deletingPresetId = null;
                    authSession = null;
                    likedPresetIds.clear();
                    if (failureMessage != null && popupPreset != null) {
                        popupStatusMessage = failureMessage;
                        popupStatusColor = UITheme.STATE_ERROR;
                    }
                    return;
                }
                authSession = session;
                action.accept(session);
            });
        });
    }

    private void startDeletePreset(MarketplacePreset preset, boolean fromPopup) {
        if (preset == null || deleteBusy || publishBusy) {
            return;
        }
        if (authSession == null) {
            if (fromPopup) {
                popupStatusMessage = "Sign in before deleting presets.";
                popupStatusColor = UITheme.STATE_WARNING;
            }
            handleAuthButton();
            return;
        }

        deleteBusy = true;
        deletingPresetId = preset.getId();
        pendingDeleteFallbackPresetName = fromPopup ? findLocalPresetNameForMarketplacePreset(preset).orElse(null) : null;
        triggerDeletePulse(preset);
        if (fromPopup) {
            popupStatusMessage = "Deleting preset...";
            popupStatusColor = UITheme.TEXT_SECONDARY;
        }
        withFreshAuthSession(session -> MarketplaceService.deletePreset(session.getAccessToken(), preset.getId(), preset.getStorageBucket(), preset.getFilePath())
            .whenComplete((unused, throwable) -> {
                if (this.client == null) {
                    return;
                }
                this.client.execute(() -> finishDeletePreset(preset, throwable, fromPopup));
            }),
            fromPopup ? "Session expired. Sign in again." : null);
    }

    private void finishDeletePreset(MarketplacePreset preset, Throwable throwable, boolean fromPopup) {
        deleteBusy = false;
        deletingPresetId = null;
        authBusy = false;
        if (throwable != null) {
            if (fromPopup && popupPreset != null && preset != null && preset.getId() != null && preset.getId().equals(popupPreset.getId())) {
                popupStatusMessage = extractThrowableMessage(throwable, "Delete failed.");
                popupStatusColor = UITheme.STATE_ERROR;
            }
            return;
        }
        boolean deletedCurrentPopup = popupPreset != null && preset != null && preset.getId() != null && preset.getId().equals(popupPreset.getId());
        String fallbackPresetName = pendingDeleteFallbackPresetName;
        pendingDeleteFallbackPresetName = null;
        if (preset != null && preset.getId() != null) {
            PresetManager.clearMarketplaceLinkedPresetById(preset.getId());
        }
        removePreset(preset == null ? null : preset.getId());
        statusMessage = "Preset deleted.";
        if (fromPopup && deletedCurrentPopup) {
            if (fallbackPresetName != null && !fallbackPresetName.isBlank()) {
                closePopup();
                if (this.client != null && parent instanceof PathmindVisualEditorScreen editorScreen) {
                    this.client.setScreen(parent);
                    editorScreen.reopenPublishPresetPopup(fallbackPresetName);
                } else {
                    openPublishPopup(fallbackPresetName);
                }
            } else {
                closePopup();
            }
        } else {
            applyFilters();
        }
    }

    private Optional<String> findLocalPresetNameForMarketplacePreset(MarketplacePreset preset) {
        if (preset == null) {
            return Optional.empty();
        }
        if (preset.getId() != null && !preset.getId().isBlank()) {
            Optional<String> linkedPresetName = PresetManager.getMarketplaceLinkedPresetName(preset.getId());
            if (linkedPresetName.isPresent() && Files.exists(PresetManager.getPresetPath(linkedPresetName.get()))) {
                return linkedPresetName;
            }
        }
        String targetKey = normalizeSavedPresetKey(fallback(preset.getSlug(), preset.getName()));
        if (targetKey.isEmpty()) {
            return Optional.empty();
        }
        List<String> availablePresets = PresetManager.getAvailablePresets();
        for (String presetName : availablePresets) {
            if (!Files.exists(PresetManager.getPresetPath(presetName))) {
                continue;
            }
            if (normalizeSavedPresetKey(presetName).equals(targetKey)) {
                return Optional.of(presetName);
            }
        }
        for (String presetName : availablePresets) {
            if (!Files.exists(PresetManager.getPresetPath(presetName))) {
                continue;
            }
            if (normalizeSavedPresetKey(presetName).startsWith(targetKey + "-")) {
                return Optional.of(presetName);
            }
        }
        return Optional.empty();
    }

    private boolean hasLinkedLocalPresetChanges(MarketplacePreset preset) {
        Optional<String> localPresetName = findLocalPresetNameForMarketplacePreset(preset);
        if (localPresetName.isEmpty()) {
            return false;
        }
        Optional<String> linkedPresetId = PresetManager.getMarketplaceLinkedPresetId(localPresetName.get());
        if (linkedPresetId.isEmpty() || preset == null || preset.getId() == null || !preset.getId().equals(linkedPresetId.get())) {
            return false;
        }
        return PresetManager.hasMarketplaceLinkedPresetChanges(localPresetName.get());
    }

    private void startUpdateFromLinkedLocalPreset() {
        if (popupPreset == null || publishBusy || authSession == null) {
            return;
        }
        Optional<String> localPresetName = findLocalPresetNameForMarketplacePreset(popupPreset);
        if (localPresetName.isEmpty()) {
            popupStatusMessage = "No linked local preset found.";
            popupStatusColor = UITheme.STATE_ERROR;
            return;
        }
        Path localPresetPath = PresetManager.getPresetPath(localPresetName.get());
        if (!Files.exists(localPresetPath)) {
            popupStatusMessage = "The linked local preset could not be found.";
            popupStatusColor = UITheme.STATE_ERROR;
            return;
        }

        publishBusy = true;
        authBusy = true;
        popupStatusMessage = "Updating uploaded preset...";
        popupStatusColor = UITheme.TEXT_SECONDARY;
        MarketplaceService.PublishRequest request = new MarketplaceService.PublishRequest(
            localPresetPath,
            popupPreset.getStorageBucket(),
            popupPreset.getFilePath(),
            fallback(popupPreset.getSlug(), sanitizeSlug(popupPreset.getName())),
            popupPreset.getName(),
            popupPreset.getAuthorName(),
            popupPreset.getDescription(),
            popupPreset.getTags(),
            popupPreset.getGameVersion(),
            popupPreset.getPathmindVersion(),
            popupPreset.isPublished()
        );

        withFreshAuthSession(session -> MarketplaceService.updatePresetMetadata(session.getAccessToken(), popupPreset, request)
            .whenComplete((updatedPreset, throwable) -> {
                if (this.client == null) {
                    return;
                }
                this.client.execute(() -> {
                    publishBusy = false;
                    authBusy = false;
                    if (throwable != null || updatedPreset == null) {
                        popupStatusMessage = extractThrowableMessage(throwable, "Preset update failed.");
                        popupStatusColor = UITheme.STATE_ERROR;
                        return;
                    }
                    PresetManager.setMarketplaceLinkedPreset(localPresetName.get(), updatedPreset.getId());
                    upsertPreset(updatedPreset);
                    popupPreset = updatedPreset;
                    popupStatusMessage = "Preset updated from local changes.";
                    popupStatusColor = getAccentColor();
                    applyFilters();
                });
            }),
            "Session expired. Sign in again.");
    }

    private boolean isOwnPreset(MarketplacePreset preset) {
        if (preset == null || authSession == null) {
            return false;
        }
        String presetAuthorUserId = fallback(preset.getAuthorUserId(), "");
        String currentUserId = fallback(authSession.getUserId(), "");
        return !presetAuthorUserId.isBlank() && presetAuthorUserId.equals(currentUserId);
    }

    private void setPresetLiked(String presetId, boolean liked) {
        if (presetId == null || presetId.isBlank()) {
            return;
        }
        if (liked) {
            likedPresetIds.add(presetId);
        } else {
            likedPresetIds.remove(presetId);
        }
    }

    private boolean isPresetLiked(MarketplacePreset preset) {
        return preset != null && preset.getId() != null && likedPresetIds.contains(preset.getId());
    }

    private String getPopupAuthButtonLabel() {
        if (popupPreset != null && isOwnPreset(popupPreset)) {
            if (publishBusy) {
                return "Working...";
            }
            return popupMetadataEditing ? "Save" : "Edit";
        }
        if (authBusy) {
            return "Working...";
        }
        if (authSession == null) {
            return "Sign In";
        }
        return isPresetLiked(popupPreset) ? "Unlike" : "Like";
    }

    private String getPublishAuthButtonLabel() {
        if (authBusy) {
            return "Working...";
        }
        if (authSession == null) {
            return "Sign In";
        }
        return "Signed In";
    }

    private void applyPresetCountUpdate(String presetId, int likesDelta, int downloadsDelta) {
        if (presetId == null || presetId.isBlank()) {
            return;
        }
        allPresets = updatePresetCountList(allPresets, presetId, likesDelta, downloadsDelta);
        presets = updatePresetCountList(presets, presetId, likesDelta, downloadsDelta);
        if (popupPreset != null && presetId.equals(popupPreset.getId())) {
            popupPreset = updatedPresetCounts(popupPreset, likesDelta, downloadsDelta);
        }
    }

    private void upsertPreset(MarketplacePreset preset) {
        if (preset == null || preset.getId() == null || preset.getId().isBlank()) {
            refreshListings();
            return;
        }
        allPresets = upsertPresetList(allPresets, preset);
        presets = upsertPresetList(presets, preset);
        if (popupPreset != null && preset.getId().equals(popupPreset.getId())) {
            popupPreset = preset;
        }
    }

    private void removePreset(String presetId) {
        if (presetId == null || presetId.isBlank()) {
            refreshListings();
            return;
        }
        allPresets = removePresetFromList(allPresets, presetId);
        presets = removePresetFromList(presets, presetId);
        if (popupPreset != null && presetId.equals(popupPreset.getId())) {
            popupPreset = null;
        }
        selectedIndex = presets.isEmpty() ? -1 : Math.max(0, Math.min(selectedIndex, presets.size() - 1));
    }

    private List<MarketplacePreset> removePresetFromList(List<MarketplacePreset> source, String presetId) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<MarketplacePreset> updated = new ArrayList<>(source.size());
        for (MarketplacePreset preset : source) {
            if (preset == null || presetId.equals(preset.getId())) {
                continue;
            }
            updated.add(preset);
        }
        return List.copyOf(updated);
    }

    private List<MarketplacePreset> upsertPresetList(List<MarketplacePreset> source, MarketplacePreset preset) {
        List<MarketplacePreset> updated = new ArrayList<>(source == null ? List.of() : source);
        boolean replaced = false;
        for (int i = 0; i < updated.size(); i++) {
            MarketplacePreset candidate = updated.get(i);
            if (candidate != null && preset.getId().equals(candidate.getId())) {
                updated.set(i, preset);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            updated.add(0, preset);
        }
        return List.copyOf(updated);
    }

    private List<MarketplacePreset> updatePresetCountList(List<MarketplacePreset> source, String presetId, int likesDelta, int downloadsDelta) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<MarketplacePreset> updated = new ArrayList<>(source.size());
        for (MarketplacePreset preset : source) {
            updated.add(presetId.equals(preset.getId()) ? updatedPresetCounts(preset, likesDelta, downloadsDelta) : preset);
        }
        return List.copyOf(updated);
    }

    private MarketplacePreset updatedPresetCounts(MarketplacePreset preset, int likesDelta, int downloadsDelta) {
        return new MarketplacePreset(
            preset.getId(),
            preset.getSlug(),
            preset.getAuthorUserId(),
            preset.getName(),
            preset.getAuthorName(),
            preset.getDescription(),
            preset.getTags(),
            preset.getGameVersion(),
            preset.getPathmindVersion(),
            Math.max(0, preset.getLikesCount() + likesDelta),
            Math.max(0, preset.getDownloadsCount() + downloadsDelta),
            preset.getStorageBucket(),
            preset.getFilePath(),
            preset.isPublished(),
            preset.getCreatedAt(),
            preset.getUpdatedAt()
        );
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

    private void syncVisibilityToggleColors() {
        int offColor = 0xFFE0B84A;
        int onColor = getAccentColor();
        publishVisibilityToggle.setIndicatorColors(offColor, onColor);
        presetVisibilityToggle.setIndicatorColors(offColor, onColor);
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

    private void drawHeartIcon(DrawContext context, int x, int y, int color) {
        context.fill(x + 2, y + 1, x + 4, y + 3, color);
        context.fill(x + 6, y + 1, x + 8, y + 3, color);
        context.fill(x + 1, y + 3, x + 9, y + 5, color);
        context.fill(x + 2, y + 5, x + 8, y + 7, color);
        context.fill(x + 3, y + 7, x + 7, y + 9, color);
        context.fill(x + 4, y + 9, x + 6, y + 11, color);
    }

    private void drawBookmarkIcon(DrawContext context, int x, int y, int color) {
        context.fill(x + 2, y + 1, x + 8, y + 10, color);
        context.fill(x + 3, y + 9, x + 5, y + 11, UITheme.BACKGROUND_PRIMARY);
        context.fill(x + 5, y + 9, x + 7, y + 11, UITheme.BACKGROUND_PRIMARY);
        context.fill(x + 4, y + 8, x + 6, y + 10, color);
    }

    private void drawDeleteIcon(DrawContext context, int x, int y, int color) {
        context.fill(x + 2, y + 2, x + 8, y + 3, color);
        context.fill(x + 3, y + 3, x + 7, y + 10, color);
        context.fill(x + 1, y + 4, x + 3, y + 10, color);
        context.fill(x + 7, y + 4, x + 9, y + 10, color);
        context.fill(x + 4, y + 1, x + 6, y + 2, color);
        context.fill(x + 3, y + 10, x + 7, y + 11, color);
        context.fill(x + 4, y + 4, x + 5, y + 9, UITheme.BACKGROUND_PRIMARY);
        context.fill(x + 5, y + 4, x + 6, y + 9, UITheme.BACKGROUND_PRIMARY);
    }

    private void drawAnimatedDeleteIcon(DrawContext context, int x, int y, MarketplacePreset preset, boolean popup, boolean hovered) {
        float pulse = getIconPulse(deletePulseEndTimes, preset);
        float hoverFlash = getIconHoverFlash("delete:" + (popup ? "popup:" : "card:") + preset.getId(), hovered);
        float intensity = Math.max(pulse, hoverFlash);
        boolean pending = isDeletePending(preset);
        int baseColor = pending ? UITheme.TEXT_TERTIARY : UITheme.STATE_ERROR;
        int color = popup ? presetPopupAnimation.getAnimatedPopupColor(baseColor) : baseColor;
        if (intensity > 0.001f) {
            int glowColor = popup
                ? presetPopupAnimation.getAnimatedPopupColor(AnimationHelper.lerpColor(baseColor, 0xFFFFFFFF, intensity * 0.35f))
                : AnimationHelper.lerpColor(baseColor, 0xFFFFFFFF, intensity * 0.35f);
            context.fill(x - 2, y - 2, x + 12, y + 13, (Math.min(120, Math.round(intensity * 110)) << 24) | (glowColor & 0x00FFFFFF));
            color = popup
                ? presetPopupAnimation.getAnimatedPopupColor(AnimationHelper.lerpColor(baseColor, 0xFFFFFFFF, intensity * 0.22f))
                : AnimationHelper.lerpColor(baseColor, 0xFFFFFFFF, intensity * 0.22f);
        }
        drawDeleteIcon(context, x, y, color);
    }

    private void drawAnimatedHeartIcon(DrawContext context, int x, int y, MarketplacePreset preset, boolean liked, boolean popup, boolean hovered) {
        float pulse = getIconPulse(likePulseEndTimes, preset);
        float hoverFlash = getIconHoverFlash("heart:" + (popup ? "popup:" : "card:") + preset.getId(), hovered);
        float intensity = Math.max(pulse, hoverFlash);
        boolean pending = isLikePending(preset);
        int baseColor = pending ? UITheme.TEXT_TERTIARY : liked ? 0xFFE05454 : UITheme.TEXT_TERTIARY;
        int color = popup ? presetPopupAnimation.getAnimatedPopupColor(baseColor) : baseColor;
        if (intensity > 0.001f) {
            int glowColor = popup
                ? presetPopupAnimation.getAnimatedPopupColor(AnimationHelper.lerpColor(baseColor, 0xFFFFFFFF, intensity * 0.35f))
                : AnimationHelper.lerpColor(baseColor, 0xFFFFFFFF, intensity * 0.35f);
            context.fill(x - 2, y - 2, x + 12, y + 13, (Math.min(120, Math.round(intensity * 110)) << 24) | (glowColor & 0x00FFFFFF));
            color = popup
                ? presetPopupAnimation.getAnimatedPopupColor(AnimationHelper.lerpColor(baseColor, 0xFFFFFFFF, intensity * 0.22f))
                : AnimationHelper.lerpColor(baseColor, 0xFFFFFFFF, intensity * 0.22f);
        }
        drawHeartIcon(context, x, y, color);
    }

    private void drawAnimatedBookmarkIcon(DrawContext context, int x, int y, MarketplacePreset preset, boolean saved, boolean popup, boolean hovered) {
        float pulse = getIconPulse(savePulseEndTimes, preset);
        float hoverFlash = getIconHoverFlash("bookmark:" + (popup ? "popup:" : "card:") + preset.getId(), hovered);
        float intensity = Math.max(pulse, hoverFlash);
        int baseColor = saved ? 0xFFE2B93B : UITheme.TEXT_TERTIARY;
        int color = popup ? presetPopupAnimation.getAnimatedPopupColor(baseColor) : baseColor;
        if (intensity > 0.001f) {
            int glowColor = popup
                ? presetPopupAnimation.getAnimatedPopupColor(AnimationHelper.lerpColor(baseColor, 0xFFFFFFFF, intensity * 0.35f))
                : AnimationHelper.lerpColor(baseColor, 0xFFFFFFFF, intensity * 0.35f);
            context.fill(x - 2, y - 2, x + 12, y + 13, (Math.min(120, Math.round(intensity * 110)) << 24) | (glowColor & 0x00FFFFFF));
            color = popup
                ? presetPopupAnimation.getAnimatedPopupColor(AnimationHelper.lerpColor(baseColor, 0xFFFFFFFF, intensity * 0.22f))
                : AnimationHelper.lerpColor(baseColor, 0xFFFFFFFF, intensity * 0.22f);
        }
        drawBookmarkIcon(context, x, y, color);
    }

    private float getIconHoverFlash(String key, boolean hovered) {
        float hoverProgress = HoverAnimator.getProgress("marketplace-icon:" + key, hovered);
        if (hoverProgress <= 0.001f) {
            return 0f;
        }
        float cycle = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 120.0);
        return hoverProgress * (0.18f + cycle * 0.14f);
    }

    private boolean isLikePending(MarketplacePreset preset) {
        return preset != null && preset.getId() != null && preset.getId().equals(pendingLikePresetId);
    }

    private boolean isDeletePending(MarketplacePreset preset) {
        return deleteBusy && preset != null && preset.getId() != null && preset.getId().equals(deletingPresetId);
    }

    private float getIconPulse(Map<String, Long> pulseMap, MarketplacePreset preset) {
        if (preset == null || preset.getId() == null) {
            return 0f;
        }
        Long endTime = pulseMap.get(preset.getId());
        if (endTime == null) {
            return 0f;
        }
        long remaining = endTime - System.currentTimeMillis();
        if (remaining <= 0L) {
            pulseMap.remove(preset.getId());
            return 0f;
        }
        return AnimationHelper.easeOutQuad(Math.min(1f, remaining / 220f));
    }

    private void triggerLikePulse(MarketplacePreset preset) {
        if (preset != null && preset.getId() != null) {
            likePulseEndTimes.put(preset.getId(), System.currentTimeMillis() + 220L);
        }
    }

    private void triggerSavePulse(MarketplacePreset preset) {
        if (preset != null && preset.getId() != null) {
            savePulseEndTimes.put(preset.getId(), System.currentTimeMillis() + 220L);
        }
    }

    private void triggerDeletePulse(MarketplacePreset preset) {
        if (preset != null && preset.getId() != null) {
            deletePulseEndTimes.put(preset.getId(), System.currentTimeMillis() + 220L);
        }
    }

    private void drawMinimalPreviewButton(DrawContext context, int x, int y, int width, int height, boolean hovered) {
        int background = presetPopupAnimation.getAnimatedPopupColor(hovered ? UITheme.BACKGROUND_SECTION : UITheme.BACKGROUND_PRIMARY);
        int border = presetPopupAnimation.getAnimatedPopupColor(hovered ? getAccentColor() : UITheme.BORDER_SUBTLE);
        UIStyleHelper.drawBeveledPanel(
            context,
            x,
            y,
            width,
            height,
            background,
            border,
            presetPopupAnimation.getAnimatedPopupColor(UITheme.PANEL_INNER_BORDER)
        );
    }

    private void drawPreviewMinusIcon(DrawContext context, int x, int y, int color) {
        context.fill(x + 4, y + 6, x + 10, y + 8, color);
    }

    private void drawPreviewPlusIcon(DrawContext context, int x, int y, int color) {
        context.fill(x + 4, y + 6, x + 10, y + 8, color);
        context.fill(x + 6, y + 4, x + 8, y + 10, color);
    }

    private void adjustPopupPreviewZoom(int direction) {
        float step = 0.15f;
        popupPreviewZoom = Math.max(0.45f, Math.min(2.75f, popupPreviewZoom + direction * step));
    }

    private boolean isAnyMarketplacePopupVisible() {
        return popupPreset != null
            || presetPopupAnimation.isVisible()
            || accountPopupOpen
            || accountPopupAnimation.isVisible()
            || publishPopupOpen
            || publishPopupAnimation.isVisible()
            || pendingConfirmAction != null
            || confirmPopupAnimation.isVisible();
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

        int searchFieldX = bodyX;
        int searchFieldY = sectionY + 5;
        int sortButtonX = searchFieldX + SEARCH_FIELD_WIDTH + 8;
        int sortButtonY = searchFieldY;
        int myPresetsButtonX = bodyX + bodyWidth - MY_PRESETS_BUTTON_WIDTH;
        int myPresetsButtonY = searchFieldY;
        int resultRowY = searchFieldY + SORT_BUTTON_HEIGHT + 6;
        int refreshButtonX = bodyX + bodyWidth - REFRESH_BUTTON_SIZE;
        int refreshButtonY = resultRowY;
        int accountButtonX = this.width - OUTER_PADDING - ACCOUNT_BUTTON_WIDTH;
        int accountButtonY = topBarY + 2;

        return new Layout(topBarY, backButtonX, backButtonY, sectionX, sectionY, sectionWidth, sectionHeight,
            bodyX, bodyWidth, refreshButtonX, refreshButtonY, accountButtonX, accountButtonY, searchFieldX, searchFieldY,
            sortButtonX, sortButtonY, myPresetsButtonX, myPresetsButtonY, resultRowY);
    }

    private PopupLayout getPopupLayout(Layout layout) {
        int width = Math.min(360, this.width - 40);
        int height = Math.min(330, this.height - 40);
        int x = (this.width - width) / 2;
        int y = (this.height - height) / 2;
        int buttonWidth = 64;
        int buttonHeight = 18;
        int buttonY = y + height - 28;
        int closeButtonX = x + width - buttonWidth * 4 - 28;
        int authButtonX = x + width - buttonWidth * 3 - 22;
        int deleteButtonX = x + width - buttonWidth * 2 - 16;
        int downloadButtonX = x + width - buttonWidth - 10;
        return new PopupLayout(x, y, width, height, closeButtonX, authButtonX, deleteButtonX, downloadButtonX, buttonY, buttonWidth, buttonHeight);
    }

    private ConfirmPopupLayout getConfirmPopupLayout(Layout layout) {
        int width = Math.min(336, this.width - 40);
        int height = 164;
        int x = (this.width - width) / 2;
        int y = (this.height - height) / 2;
        int buttonWidth = 88;
        int buttonHeight = 18;
        int buttonY = y + height - 28;
        int cancelButtonX = x + 14;
        int confirmButtonX = x + width - buttonWidth - 14;
        return new ConfirmPopupLayout(x, y, width, height, cancelButtonX, confirmButtonX, buttonY, buttonWidth, buttonHeight);
    }

    private Rect getPopupPreviewRect(int popupX, int popupY, int popupWidth, int popupHeight, int scrollOffset) {
        int contentTop = popupY + 40;
        return new Rect(
            popupX + 12,
            contentTop - scrollOffset,
            popupWidth - 24,
            120
        );
    }

    private AccountPopupLayout getAccountPopupLayout(Layout layout) {
        int width = Math.min(320, this.width - 40);
        int height = 180;
        int x = (this.width - width) / 2;
        int y = (this.height - height) / 2;
        int buttonWidth = 96;
        int buttonHeight = 18;
        int buttonY = y + height - 28;
        int closeButtonX = x + width - buttonWidth * 2 - 16;
        int signOutButtonX = x + width - buttonWidth - 10;
        return new AccountPopupLayout(x, y, width, height, closeButtonX, signOutButtonX, buttonY, buttonWidth, buttonHeight);
    }

    private PublishPopupLayout getPublishPopupLayout(Layout layout) {
        int width = Math.min(392, this.width - 40);
        int height = Math.min(274, this.height - 40);
        int x = (this.width - width) / 2;
        int y = (this.height - height) / 2;
        int buttonWidth = 88;
        int buttonHeight = 18;
        int buttonY = y + height - 28;
        int cancelButtonX = x + width - buttonWidth * 3 - 22;
        int authButtonX = x + width - buttonWidth * 2 - 16;
        int submitButtonX = x + width - buttonWidth - 10;
        return new PublishPopupLayout(x, y, width, height, cancelButtonX, authButtonX, submitButtonX, buttonY, buttonWidth, buttonHeight);
    }

    private Rect getCardRect(Layout layout, int pageOffset, int visibleCount) {
        int columns = getGridColumns(layout);
        int bodyY = layout.sectionY + getSectionHeaderHeight() + 2;
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
        int availableHeight = layout.sectionHeight - getSectionHeaderHeight() - FOOTER_HEIGHT - 8;
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

    private static String formatTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown";
        }
        String normalized = value.replace('T', ' ');
        int dotIndex = normalized.indexOf('.');
        if (dotIndex > 0) {
            normalized = normalized.substring(0, dotIndex);
        }
        if (normalized.endsWith("Z")) {
            normalized = normalized.substring(0, normalized.length() - 1) + " UTC";
        }
        return normalized;
    }

    private void applyFilters() {
        String query = searchField == null ? "" : normalizeSearch(searchField.getText());
        List<MarketplacePreset> filtered = new ArrayList<>();
        for (MarketplacePreset preset : allPresets) {
            if (myPresetsOnly && !isOwnPreset(preset)) {
                continue;
            }
            if (myPresetsOnly && !myPresetsFilter.matches(preset)) {
                continue;
            }
            if (!sortMode.matches(this, preset)) {
                continue;
            }
            if (query.isEmpty() || matchesQuery(preset, query)) {
                filtered.add(preset);
            }
        }
        filtered.sort(sortMode.comparator);
        presets = List.copyOf(filtered);
        pageIndex = Math.max(0, Math.min(pageIndex, getMaxPageIndex()));
        selectedIndex = presets.isEmpty() ? -1 : Math.max(0, Math.min(selectedIndex, presets.size() - 1));
        if (myPresetsOnly && authSession == null) {
            statusMessage = "Sign in to view your presets.";
        } else if (allPresets.isEmpty()) {
            statusMessage = myPresetsOnly ? "No presets in your cloud library yet." : "No published presets found.";
        } else if (presets.isEmpty()) {
            if (myPresetsOnly) {
                statusMessage = switch (myPresetsFilter) {
                    case PUBLIC -> "No public presets match your search.";
                    case PRIVATE -> "No private presets match your search.";
                    default -> "No presets match your search.";
                };
            } else {
                statusMessage = sortMode == SortMode.SAVED ? "No saved presets match your search." : "No presets match your search.";
            }
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

    private List<String> parseTags(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> tags = new ArrayList<>();
        for (String token : value.split(",")) {
            String normalized = token == null ? "" : token.trim();
            if (!normalized.isEmpty() && !tags.contains(normalized)) {
                tags.add(normalized);
            }
        }
        return List.copyOf(tags);
    }

    private String sanitizeSlug(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-+|-+$)", "");
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

    private int getSectionHeaderHeight() {
        return myPresetsOnly ? SECTION_HEADER_HEIGHT + MY_PRESET_FILTER_BUTTON_HEIGHT + 8 : SECTION_HEADER_HEIGHT;
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
        int accountButtonX,
        int accountButtonY,
        int searchFieldX,
        int searchFieldY,
        int sortButtonX,
        int sortButtonY,
        int myPresetsButtonX,
        int myPresetsButtonY,
        int resultRowY
    ) {
    }

    private record PopupLayout(
        int x,
        int y,
        int width,
        int height,
        int closeButtonX,
        int authButtonX,
        int deleteButtonX,
        int downloadButtonX,
        int buttonY,
        int buttonWidth,
        int buttonHeight
    ) {
    }

    private record AccountPopupLayout(
        int x,
        int y,
        int width,
        int height,
        int closeButtonX,
        int signOutButtonX,
        int buttonY,
        int buttonWidth,
        int buttonHeight
    ) {
    }

    private record PublishPopupLayout(
        int x,
        int y,
        int width,
        int height,
        int cancelButtonX,
        int authButtonX,
        int submitButtonX,
        int buttonY,
        int buttonWidth,
        int buttonHeight
    ) {
    }

    private record ConfirmPopupLayout(
        int x,
        int y,
        int width,
        int height,
        int cancelButtonX,
        int confirmButtonX,
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

    private record GraphBounds(float minX, float minY, float maxX, float maxY) {
        static GraphBounds of(List<Node> nodes) {
            if (nodes == null || nodes.isEmpty()) {
                return new GraphBounds(0f, 0f, 1f, 1f);
            }
            float minX = Float.MAX_VALUE;
            float minY = Float.MAX_VALUE;
            float maxX = Float.MIN_VALUE;
            float maxY = Float.MIN_VALUE;
            for (Node node : nodes) {
                float width = Math.max(1f, node.getWidth()) + 8f;
                float height = Math.max(1f, node.getHeight()) + 8f;
                minX = Math.min(minX, node.getX());
                minY = Math.min(minY, node.getY());
                maxX = Math.max(maxX, node.getX() + width);
                maxY = Math.max(maxY, node.getY() + height);
            }
            return new GraphBounds(minX, minY, maxX, maxY);
        }

        float width() {
            return Math.max(1f, maxX - minX);
        }

        float height() {
            return Math.max(1f, maxY - minY);
        }
    }

    private record PreviewGraphModel(
        List<Node> nodes,
        List<NodeGraphData.ConnectionData> connections,
        Map<String, Node> nodeLookup
    ) {
    }

    private enum SortMode {
        SAVED("Saved", Comparator.comparing((MarketplacePreset preset) -> fallbackStatic(preset.getName(), "").toLowerCase(Locale.ROOT))),
        NEWEST("Newest", Comparator.comparing((MarketplacePreset preset) -> fallbackStatic(preset.getCreatedAt(), "")).reversed()),
        UPDATED("Updated", Comparator.comparing((MarketplacePreset preset) -> fallbackStatic(preset.getUpdatedAt(), "")).reversed()),
        DOWNLOADS("Downloads", Comparator.comparingInt(MarketplacePreset::getDownloadsCount).reversed()),
        LIKES("Likes", Comparator.comparingInt(MarketplacePreset::getLikesCount).reversed()),
        NAME("Name", Comparator.comparing((MarketplacePreset preset) -> fallbackStatic(preset.getName(), "").toLowerCase(Locale.ROOT))),
        AUTHOR("Author", Comparator.comparing((MarketplacePreset preset) -> fallbackStatic(preset.getAuthorName(), "").toLowerCase(Locale.ROOT)));

        private final String label;
        private final Comparator<MarketplacePreset> comparator;

        SortMode(String label, Comparator<MarketplacePreset> comparator) {
            this.label = label;
            this.comparator = comparator;
        }

        private boolean matches(PathmindMarketplaceScreen screen, MarketplacePreset preset) {
            if (this == SAVED) {
                return screen.isPresetSavedLocally(preset);
            }
            return true;
        }

        private static String fallbackStatic(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value;
        }
    }

    private enum MyPresetsFilter {
        ALL,
        PUBLIC,
        PRIVATE;

        private boolean matches(MarketplacePreset preset) {
            return switch (this) {
                case ALL -> true;
                case PUBLIC -> preset != null && preset.isPublished();
                case PRIVATE -> preset != null && !preset.isPublished();
            };
        }
    }

    private enum ConfirmAction {
        UPDATE,
        DELETE
    }
}
