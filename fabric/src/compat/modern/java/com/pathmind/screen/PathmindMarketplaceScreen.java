package com.pathmind.screen;

import com.pathmind.PathmindCommon;
import com.pathmind.data.NodeGraphData;
import com.pathmind.data.NodeGraphPersistence;
import com.pathmind.data.PresetManager;
import com.pathmind.data.SettingsManager;
import com.pathmind.marketplace.MarketplaceAuthManager;
import com.pathmind.marketplace.MarketplacePreset;
import com.pathmind.marketplace.MarketplaceRateLimitManager;
import com.pathmind.marketplace.MarketplaceService;
import com.pathmind.nodes.Node;
import com.pathmind.ui.animation.AnimatedValue;
import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.animation.HoverAnimator;
import com.pathmind.ui.animation.PopupAnimationHandler;
import com.pathmind.ui.control.PathmindDropdownRenderer;
import com.pathmind.ui.control.PathmindPopupRenderer;
import com.pathmind.ui.control.PathmindTextField;
import com.pathmind.ui.control.ToggleSwitch;
import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DrawContextBridge;
import com.pathmind.util.PathmindI18n;
import com.pathmind.util.ScrollbarHelper;
import com.pathmind.util.TextureCompatibilityBridge;
import com.pathmind.util.TextRenderUtil;
import com.pathmind.util.LoaderMetadata;
import net.minecraft.client.MinecraftClient;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Read-only marketplace browser with a full-window preset gallery.
 */
public class PathmindMarketplaceScreen extends Screen {
    private static String tr(String key, Object... args) {
        return PathmindI18n.tr(key, args);
    }

    static final int OUTER_PADDING = 12;
    static final int TOP_BAR_HEIGHT = 30;
    static final int SECTION_TOP_GAP = 4;
    static final int SECTION_HEADER_HEIGHT = 54;
    static final int SECTION_BODY_PADDING = 12;
    static final int FOOTER_HEIGHT = 14;
    static final int CARD_GAP = 8;
    static final int PRESET_GRID_COLUMNS = 4;
    static final int PRESET_GRID_ROWS = 8;
    static final int CARD_MAX_WIDTH = 140;
    static final int CARD_SIZE = 128;
    private static final int BACK_BUTTON_SIZE = 18;
    static final int REFRESH_BUTTON_SIZE = 18;
    private static final int PAGE_CONTROL_GAP = 18;
    private static final int PAGE_NUMBER_GAP = 14;
    static final int SEARCH_FIELD_WIDTH = 154;
    static final int SEARCH_FIELD_HEIGHT = 18;
    static final int SORT_BUTTON_WIDTH = 82;
    static final int SORT_BUTTON_HEIGHT = 18;
    static final int SORT_OPTION_HEIGHT = 18;
    static final int MY_PRESETS_BUTTON_WIDTH = 86;
    static final int MY_PRESET_FILTER_BUTTON_HEIGHT = 16;
    private static final int MY_PRESET_FILTER_ALL_WIDTH = 34;
    private static final int MY_PRESET_FILTER_PUBLIC_WIDTH = 50;
    private static final int MY_PRESET_FILTER_PRIVATE_WIDTH = 54;
    static final int AUTHOR_ROW_HEIGHT = 42;
    static final int AUTHOR_ROW_GAP = 8;
    private static final int ACCOUNT_BUTTON_MIN_WIDTH = SORT_BUTTON_HEIGHT;
    private final Screen parent;
    private final boolean openPublishOnInit;
    private final String preferredPublishPresetName;
    private final MarketplacePreset initialPopupPreset;
    private final boolean editorPopupMode;

    private List<MarketplacePreset> allPresets = List.of();
    private List<MarketplacePreset> presets = List.of();
    private List<AuthorSummary> authorResults = List.of();
    private int selectedIndex = -1;
    private int pageIndex = 0;
    private boolean loading = false;
    private boolean initialFetchStarted = false;
    private String statusMessage = Text.translatable("pathmind.marketplace.loadingPublishedPresets").getString();
    MarketplacePreset popupPreset = null;
    boolean importingPreset = false;
    String popupStatusMessage = "";
    int popupStatusColor = UITheme.TEXT_SECONDARY;
    int popupScrollOffset = 0;
    private boolean popupScrollDragging = false;
    private int popupScrollDragOffset = 0;
    private int galleryScrollOffset = 0;
    private boolean galleryScrollDragging = false;
    private int galleryScrollDragOffset = 0;
    final PopupAnimationHandler presetPopupAnimation = new PopupAnimationHandler();
    final PopupAnimationHandler accountPopupAnimation = new PopupAnimationHandler();
    final PopupAnimationHandler publishPopupAnimation = new PopupAnimationHandler();
    final PopupAnimationHandler confirmPopupAnimation = new PopupAnimationHandler();
    private final AnimatedValue sortDropdownAnimation = AnimatedValue.forHover();
    final AnimatedValue updateConfirmSourceDropdownAnimation = AnimatedValue.forHover();
    final AnimatedValue popupUpdateHoverAnimation = AnimatedValue.forHover();
    final AnimatedValue popupDeleteHoverAnimation = AnimatedValue.forHover();
    MarketplaceAuthManager.AuthSession authSession = null;
    private final Set<String> likedPresetIds = new HashSet<>();
    private final Set<String> savedMarketplacePresetIds = new HashSet<>();
    boolean authBusy = false;
    private boolean accountPopupOpen = false;
    private boolean publishPopupOpen = false;
    boolean publishBusy = false;
    boolean deleteBusy = false;
    private String deletingPresetId = null;
    private String pendingDeleteFallbackPresetName = null;
    private boolean isMarketplaceModerator = false;
    private boolean myPresetsOnly = false;
    private MyPresetsFilter myPresetsFilter = MyPresetsFilter.ALL;
    private String viewedAuthorAvatarUrl = null;
    private final Map<String, Long> likePulseEndTimes = new HashMap<>();
    private final Map<String, Long> savePulseEndTimes = new HashMap<>();
    private final Map<String, Long> deletePulseEndTimes = new HashMap<>();
    private String pendingLikePresetId = null;
    private boolean popupPreviewDragging = false;
    private int popupPreviewDragLastX = 0;
    private int popupPreviewDragLastY = 0;
    float popupPreviewPanX = 0f;
    float popupPreviewPanY = 0f;
    float popupPreviewZoom = 1f;
    private TextFieldWidget searchField;
    TextFieldWidget publishNameField;
    TextFieldWidget publishDescriptionField;
    TextFieldWidget publishTagsField;
    private SortMode sortMode = SortMode.TRENDING;
    private boolean sortDropdownOpen = false;
    String publishSourcePresetName = "";
    MarketplacePreset editingPreset = null;
    boolean popupMetadataEditing = false;
    String publishStatusMessage = "";
    int publishStatusColor = UITheme.TEXT_SECONDARY;
    boolean publishVisibilityPublic = true;
    final ToggleSwitch publishVisibilityToggle = new ToggleSwitch(true);
    final ToggleSwitch presetVisibilityToggle = new ToggleSwitch(true);
    ConfirmAction pendingConfirmAction = null;
    private MarketplacePreset pendingConfirmPreset = null;
    private boolean pendingConfirmDeleteFromPopup = false;
    ConfirmAction renderConfirmAction = null;
    boolean updateConfirmSourceDropdownOpen = false;
    private boolean confirmPopupClosing = false;
    private boolean presetPopupClosing = false;
    private boolean returnToParentAfterPresetClose = false;
    private String viewedAuthorKey = null;
    private String viewedAuthorName = null;
    Rect popupAuthorHitRect = null;
    boolean skipMarketplaceDeleteConfirm = SettingsManager.getCurrent().skipMarketplaceDeleteConfirm != null
        && SettingsManager.getCurrent().skipMarketplaceDeleteConfirm;
    boolean skipMarketplaceUpdateConfirm = SettingsManager.getCurrent().skipMarketplaceUpdateConfirm != null
        && SettingsManager.getCurrent().skipMarketplaceUpdateConfirm;
    private boolean systemCursorHidden = false;
    final PathmindMarketplaceGraphPreviewRenderer graphPreviewRenderer = new PathmindMarketplaceGraphPreviewRenderer(this);
    private final PathmindMarketplacePopupController popupController = new PathmindMarketplacePopupController(this);
    private final PathmindMarketplacePreviewLoader previewLoader = new PathmindMarketplacePreviewLoader();
    private final PathmindMarketplaceAvatarLoader avatarLoader = new PathmindMarketplaceAvatarLoader();

    public PathmindMarketplaceScreen(Screen parent) {
        this(parent, false, null, null);
    }

    public PathmindMarketplaceScreen(Screen parent, boolean openPublishOnInit, String preferredPublishPresetName) {
        this(parent, openPublishOnInit, preferredPublishPresetName, null);
    }

    public PathmindMarketplaceScreen(Screen parent, boolean openPublishOnInit, String preferredPublishPresetName, MarketplacePreset initialPopupPreset) {
        super(Text.translatable("pathmind.marketplace.title"));
        this.parent = parent;
        this.openPublishOnInit = openPublishOnInit;
        this.preferredPublishPresetName = preferredPublishPresetName;
        this.initialPopupPreset = initialPopupPreset;
        this.editorPopupMode = parent instanceof PathmindVisualEditorScreen
            && (openPublishOnInit || initialPopupPreset != null);
    }

    @Override
    protected void init() {
        super.init();
        ensureCustomCursorHidden();
        if (searchField == null) {
            searchField = new PathmindTextField(this.textRenderer, 0, 0, SEARCH_FIELD_WIDTH, SEARCH_FIELD_HEIGHT, Text.translatable("pathmind.marketplace.searchPresets"));
            searchField.setMaxLength(64);
            searchField.setDrawsBackground(false);
            searchField.setEditableColor(UITheme.TEXT_PRIMARY);
            searchField.setUneditableColor(UITheme.TEXT_TERTIARY);
            searchField.setChangedListener(value -> applyFilters());
            this.addSelectableChild(searchField);
        }
        if (publishNameField == null) {
            publishNameField = new PathmindTextField(this.textRenderer, 0, 0, 240, 18, Text.translatable("pathmind.field.presetName"));
            publishNameField.setMaxLength(64);
            publishNameField.setDrawsBackground(false);
            publishNameField.setEditableColor(UITheme.TEXT_PRIMARY);
            publishNameField.setUneditableColor(UITheme.TEXT_TERTIARY);
            this.addSelectableChild(publishNameField);
        }
        if (publishDescriptionField == null) {
            publishDescriptionField = new PathmindTextField(this.textRenderer, 0, 0, 240, 18, Text.translatable("pathmind.field.description"));
            publishDescriptionField.setMaxLength(180);
            publishDescriptionField.setDrawsBackground(false);
            publishDescriptionField.setEditableColor(UITheme.TEXT_PRIMARY);
            publishDescriptionField.setUneditableColor(UITheme.TEXT_TERTIARY);
            this.addSelectableChild(publishDescriptionField);
        }
        if (publishTagsField == null) {
            publishTagsField = new PathmindTextField(this.textRenderer, 0, 0, 240, 18, Text.translatable("pathmind.field.tags"));
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
        statusMessage = myPresetsOnly ? Text.translatable("pathmind.marketplace.loadingYourPresets").getString() : Text.translatable("pathmind.marketplace.loadingPublishedPresets").getString();
        PathmindMarketplaceAsyncController.fetchListings(
            this.client,
            myPresetsOnly && authSession != null,
            authSession == null ? null : authSession.getAccessToken(),
            sortMode.toListingMode(),
            (results, throwable) -> {
                loading = false;
                if (throwable != null) {
                    allPresets = List.of();
                    presets = List.of();
                    selectedIndex = -1;
                    pageIndex = 0;
                    galleryScrollOffset = 0;
                    statusMessage = myPresetsOnly ? Text.translatable("pathmind.marketplace.failedLoadYourPresets").getString() : Text.translatable("pathmind.marketplace.failedLoadPresets").getString();
                    return;
                }

                allPresets = PathmindMarketplaceActions.dedupePresetsById(results);
                applyFilters();
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
        updateConfirmSourceDropdownAnimation.animateTo(updateConfirmSourceDropdownOpen ? 1f : 0f, UITheme.TRANSITION_ANIM_MS, AnimationHelper::easeOutQuad);
        updateConfirmSourceDropdownAnimation.tick();

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
        boolean basePopupVisible = popupPreset != null || presetPopupAnimation.isVisible()
            || accountPopupOpen || accountPopupAnimation.isVisible()
            || publishPopupOpen || publishPopupAnimation.isVisible();
        boolean confirmPopupVisible = pendingConfirmAction != null || confirmPopupAnimation.isVisible();
        if (basePopupVisible) {
            DrawContextBridge.startNewRootLayer(context);
        }
        if (popupPreset != null || presetPopupAnimation.isVisible()) {
            popupController.renderPresetPopup(context, popupMouseX, popupMouseY, layout);
        }
        if (accountPopupOpen || accountPopupAnimation.isVisible()) {
            popupController.renderAccountPopup(context, popupMouseX, popupMouseY, layout);
        }
        if (publishPopupOpen || publishPopupAnimation.isVisible()) {
            popupController.renderPublishPopup(context, popupMouseX, popupMouseY, layout);
        }
        if (confirmPopupVisible) {
            DrawContextBridge.startNewRootLayer(context);
            popupController.renderConfirmPopup(context, popupMouseX, popupMouseY, layout);
        }
        if (!editorPopupMode) {
            DrawContextBridge.startNewRootLayer(context);
            PathmindCursor.renderDefault(context, popupMouseX, popupMouseY);
        }
    }

    private void ensureCustomCursorHidden() {
        if (systemCursorHidden || editorPopupMode) {
            return;
        }
        PathmindCursor.hideSystemCursor(this.client != null ? this.client : MinecraftClient.getInstance());
        systemCursorHidden = true;
    }

    private void restoreSystemCursor() {
        if (!systemCursorHidden) {
            return;
        }
        PathmindCursor.showSystemCursor(this.client != null ? this.client : MinecraftClient.getInstance());
        systemCursorHidden = false;
    }

    private void renderTopBar(DrawContext context, int mouseX, int mouseY, Layout layout) {
        boolean backHovered = isPointInRect(mouseX, mouseY, layout.backButtonX, layout.backButtonY, BACK_BUTTON_SIZE, BACK_BUTTON_SIZE);
        drawIconButton(context, layout.backButtonX, layout.backButtonY, BACK_BUTTON_SIZE, BACK_BUTTON_SIZE, backHovered, false);
        drawBackArrow(context, layout.backButtonX, layout.backButtonY, backHovered ? UITheme.TEXT_HEADER : UITheme.TEXT_PRIMARY);
        int accountButtonWidth = getAccountButtonWidth();
        boolean accountHovered = isPointInRect(mouseX, mouseY, layout.accountButtonX, layout.accountButtonY, accountButtonWidth, SORT_BUTTON_HEIGHT);
        renderAccountToolbarButton(context, layout.accountButtonX, layout.accountButtonY, accountHovered);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, layout.topBarY + 2, UITheme.TEXT_HEADER);
        String subtitle = TextRenderUtil.trimWithEllipsis(
            this.textRenderer,
            isViewingAuthorProfile()
                ? Text.translatable("pathmind.marketplace.viewingCreatorPresets", fallback(viewedAuthorName, Text.translatable("pathmind.marketplace.unknownCreator").getString())).getString()
                : Text.translatable("pathmind.marketplace.browseCommunityPresets").getString(),
            Math.max(80, this.width - 140)
        );
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(subtitle), this.width / 2, layout.topBarY + 14, UITheme.TEXT_SECONDARY);
        context.drawHorizontalLine(layout.sectionX, layout.sectionX + layout.sectionWidth - 1, layout.sectionY - 1, UITheme.BORDER_SUBTLE);

        renderFilterControls(context, mouseX, mouseY, layout);
    }

    private void renderAccountToolbarButton(DrawContext context, int x, int y, boolean hovered) {
        int buttonWidth = getAccountButtonWidth();
        drawIconButton(context, x, y, buttonWidth, SORT_BUTTON_HEIGHT, hovered, authBusy);
        String accountLabel = getAccountButtonLabel();
        Identifier avatarTexture = getOrRequestAvatarTexture();
        if (avatarTexture != null && authSession != null) {
            int labelColor = authBusy ? UITheme.TEXT_TERTIARY : hovered ? getAccentColor() : UITheme.TEXT_PRIMARY;
            int maxLabelWidth = Math.max(0, buttonWidth - SORT_BUTTON_HEIGHT - 10);
            String displayLabel = TextRenderUtil.trimWithEllipsis(this.textRenderer, accountLabel, maxLabelWidth);
            context.drawTextWithShadow(this.textRenderer, Text.literal(displayLabel), x + 5,
                y + (SORT_BUTTON_HEIGHT - this.textRenderer.fontHeight) / 2, labelColor);
            GuiTextureRenderer.drawIcon(
                context,
                avatarTexture,
                x + buttonWidth - SORT_BUTTON_HEIGHT + 2,
                y + 3,
                SORT_BUTTON_HEIGHT - 6,
                UITheme.TEXT_HEADER
            );
            return;
        }
        if (authSession == null && !authBusy) {
            int iconColor = hovered ? getAccentColor() : UITheme.TEXT_PRIMARY;
            drawAccountProfileIcon(context, x + (buttonWidth - 12) / 2, y + (SORT_BUTTON_HEIGHT - 12) / 2, iconColor);
            return;
        }
        int textColor = authBusy ? UITheme.TEXT_TERTIARY : hovered ? getAccentColor() : UITheme.TEXT_PRIMARY;
        int textX = x + (buttonWidth - this.textRenderer.getWidth(accountLabel)) / 2;
        int textY = y + (SORT_BUTTON_HEIGHT - this.textRenderer.fontHeight) / 2;
        context.drawTextWithShadow(this.textRenderer, Text.literal(accountLabel), textX, textY, textColor);
    }

    private void renderGallerySection(DrawContext context, int mouseX, int mouseY, Layout layout) {
        int headerHeight = getSectionHeaderHeight();
        int bodyY = layout.sectionY + headerHeight;
        int bodyHeight = layout.sectionHeight - headerHeight - FOOTER_HEIGHT;
        drawGalleryBackdrop(context, layout.bodyX, layout.sectionY, layout.bodyWidth, layout.sectionHeight - FOOTER_HEIGHT);
        int scissorTop = bodyY;
        context.enableScissor(layout.bodyX, scissorTop, layout.bodyX + layout.bodyWidth, bodyY + bodyHeight);

        if (loading || getCurrentResultCount() == 0) {
            String message = loading ? Text.translatable("pathmind.marketplace.fetchingListings").getString() : fallback(statusMessage, Text.translatable("pathmind.marketplace.nothingToShow").getString());
            int messageColor = loading ? UITheme.TEXT_PRIMARY : UITheme.TEXT_TERTIARY;
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(message),
                layout.bodyX + layout.bodyWidth / 2, bodyY + bodyHeight / 2, messageColor);
            context.disableScissor();
            renderFooter(context, mouseX, mouseY, layout);
            return;
        }

        ScrollbarHelper.Metrics galleryMetrics = null;
        if (isAuthorDirectoryMode()) {
            renderAuthorDirectory(context, mouseX, mouseY, layout);
        } else {
            galleryMetrics = getGalleryScrollMetrics(layout);
            galleryScrollOffset = ScrollbarHelper.clampScroll(galleryScrollOffset, galleryMetrics.maxScroll());
            int firstVisibleIndex = getFirstVisibleCardIndex(layout, galleryScrollOffset);
            int lastVisibleIndex = getLastVisibleCardIndex(layout, galleryScrollOffset);
            for (int index = firstVisibleIndex; index <= lastVisibleIndex; index++) {
                Rect rect = getCardRect(layout, index, galleryScrollOffset);
                if (rect.y + rect.height < bodyY || rect.y > bodyY + bodyHeight) {
                    continue;
                }
                renderPresetCard(context, mouseX, mouseY, rect, presets.get(index), index == selectedIndex);
            }
        }

        context.disableScissor();
        if (galleryMetrics != null) {
            ScrollbarHelper.renderCutoffDividers(context, layout.bodyX, layout.bodyX + layout.bodyWidth - 1, bodyY, bodyY + bodyHeight, galleryScrollOffset, galleryMetrics.maxScroll(), UITheme.BORDER_SUBTLE);
            ScrollbarHelper.renderSettingsStyle(context, galleryMetrics, UITheme.BACKGROUND_SIDEBAR, UITheme.BORDER_DEFAULT, UITheme.BORDER_DEFAULT);
        }
        renderFooter(context, mouseX, mouseY, layout);
    }

    private void renderFilterControls(DrawContext context, int mouseX, int mouseY, Layout layout) {
        if (isViewingAuthorProfile()) {
            Rect exitProfileRect = getExitProfileRect(layout);
            boolean exitProfileHovered = isPointInRect(mouseX, mouseY, exitProfileRect.x, exitProfileRect.y, exitProfileRect.width, exitProfileRect.height);
            drawActionButton(context, exitProfileRect.x, exitProfileRect.y, exitProfileRect.width, exitProfileRect.height,
                Text.translatable("pathmind.marketplace.backToMarket").getString(), exitProfileHovered, false);
            String countLabel = translatedCount("pathmind.marketplace.publicPresetCount", presets.size());
            int countX = layout.bodyX + layout.bodyWidth - this.textRenderer.getWidth(countLabel);
            context.drawTextWithShadow(this.textRenderer, Text.literal(countLabel), countX, layout.searchFieldY + 5, UITheme.TEXT_SECONDARY);

            int avatarSize = 26;
            int avatarX = layout.bodyX + (layout.bodyWidth - avatarSize) / 2;
            int avatarY = layout.searchFieldY;
            renderViewedAuthorAvatar(context, avatarX, avatarY, avatarSize);
            String profileTitle = TextRenderUtil.trimWithEllipsis(this.textRenderer,
                fallback(viewedAuthorName, Text.translatable("pathmind.marketplace.unknownCreator").getString()), Math.max(80, layout.bodyWidth - 20));
            int titleX = layout.bodyX + (layout.bodyWidth - this.textRenderer.getWidth(profileTitle)) / 2;
            context.drawTextWithShadow(this.textRenderer, Text.literal(profileTitle), titleX, avatarY + avatarSize + 4, UITheme.TEXT_HEADER);
            return;
        }

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
        drawSearchIcon(context, searchX + 6, searchY + 3, UITheme.TEXT_SECONDARY);
        if (searchField != null) {
            searchField.setPosition(searchX + 22, searchY);
            searchField.setWidth(SEARCH_FIELD_WIDTH - 28);
            searchField.render(context, mouseX, mouseY, 0.0f);
        }

        boolean sortHovered = isPointInRect(mouseX, mouseY, layout.sortButtonX, layout.sortButtonY, SORT_BUTTON_WIDTH, SORT_BUTTON_HEIGHT);
        drawActionButton(context, layout.sortButtonX, layout.sortButtonY, SORT_BUTTON_WIDTH, SORT_BUTTON_HEIGHT,
            sortMode.label, sortHovered || sortDropdownOpen, false);
        drawDropdownChevron(context, layout.sortButtonX + SORT_BUTTON_WIDTH - 12, layout.sortButtonY + 6,
            sortDropdownOpen ? getAccentColor() : UITheme.TEXT_SECONDARY, sortDropdownOpen);

        boolean myPresetsHovered = isPointInRect(mouseX, mouseY, layout.myPresetsButtonX, layout.myPresetsButtonY, MY_PRESETS_BUTTON_WIDTH, SORT_BUTTON_HEIGHT);
        drawActionButton(context, layout.myPresetsButtonX, layout.myPresetsButtonY, MY_PRESETS_BUTTON_WIDTH, SORT_BUTTON_HEIGHT,
            Text.translatable("pathmind.marketplace.myPresets").getString(), myPresetsHovered, authSession == null && !myPresetsOnly, myPresetsOnly);

        if (myPresetsOnly) {
            int filterY = layout.searchFieldY + SORT_BUTTON_HEIGHT + 6;
            int allX = layout.searchFieldX;
            int publicX = allX + MY_PRESET_FILTER_ALL_WIDTH + 6;
            int privateX = publicX + MY_PRESET_FILTER_PUBLIC_WIDTH + 6;
            drawActionButton(context, allX, filterY, MY_PRESET_FILTER_ALL_WIDTH, MY_PRESET_FILTER_BUTTON_HEIGHT,
                Text.translatable("pathmind.option.all").getString(), isPointInRect(mouseX, mouseY, allX, filterY, MY_PRESET_FILTER_ALL_WIDTH, MY_PRESET_FILTER_BUTTON_HEIGHT), false,
                myPresetsFilter == MyPresetsFilter.ALL);
            drawActionButton(context, publicX, filterY, MY_PRESET_FILTER_PUBLIC_WIDTH, MY_PRESET_FILTER_BUTTON_HEIGHT,
                Text.translatable("pathmind.option.public").getString(), isPointInRect(mouseX, mouseY, publicX, filterY, MY_PRESET_FILTER_PUBLIC_WIDTH, MY_PRESET_FILTER_BUTTON_HEIGHT), false,
                myPresetsFilter == MyPresetsFilter.PUBLIC);
            drawActionButton(context, privateX, filterY, MY_PRESET_FILTER_PRIVATE_WIDTH, MY_PRESET_FILTER_BUTTON_HEIGHT,
                Text.translatable("pathmind.option.private").getString(), isPointInRect(mouseX, mouseY, privateX, filterY, MY_PRESET_FILTER_PRIVATE_WIDTH, MY_PRESET_FILTER_BUTTON_HEIGHT), false,
                myPresetsFilter == MyPresetsFilter.PRIVATE);
        }

        int resultCount = getCurrentResultCount();
        String resultLabel = loading
            ? Text.translatable("pathmind.marketplace.loading").getString()
            : isAuthorDirectoryMode()
                ? Text.translatable("pathmind.marketplace.authorCount", resultCount, resultCount == 1 ? "" : "s").getString()
                : Text.translatable("pathmind.marketplace.resultCount", resultCount, resultCount == 1 ? "" : "s").getString();
        int resultMinX = layout.sortButtonX + SORT_BUTTON_WIDTH + 8;
        int resultMaxX = layout.refreshButtonX - 6;
        int maxResultWidth = resultMaxX - resultMinX;
        if (maxResultWidth > this.textRenderer.getWidth("...")) {
            String displayResultLabel = TextRenderUtil.trimWithEllipsis(this.textRenderer, resultLabel, maxResultWidth);
            int resultX = resultMaxX - this.textRenderer.getWidth(displayResultLabel);
            context.drawTextWithShadow(this.textRenderer, Text.literal(displayResultLabel), resultX, layout.searchFieldY + 5, UITheme.TEXT_SECONDARY);
        }

        boolean refreshHovered = isPointInRect(mouseX, mouseY, layout.refreshButtonX, layout.refreshButtonY, REFRESH_BUTTON_SIZE, REFRESH_BUTTON_SIZE);
        drawIconButton(context, layout.refreshButtonX, layout.refreshButtonY, REFRESH_BUTTON_SIZE, REFRESH_BUTTON_SIZE, refreshHovered, loading);
        drawRefreshIcon(context, layout.refreshButtonX, layout.refreshButtonY, loading ? UITheme.TEXT_TERTIARY : (refreshHovered ? getAccentColor() : UITheme.TEXT_PRIMARY));
    }

    private void renderPresetCard(DrawContext context, int mouseX, int mouseY, Rect rect, MarketplacePreset preset, boolean selected) {
        boolean hovered = isPointInRect(mouseX, mouseY, rect.x, rect.y, rect.width, rect.height);
        float hoverProgress = hovered ? 1f : selected ? 0.55f : 0f;
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
        graphPreviewRenderer.renderSurface(context, previewX, previewY, previewWidth, previewHeight, preset, false, false, 0f, 0f);

        int deleteX = previewX + previewWidth - 14;
        int deleteY = previewY + 2;
        int heartX = previewX + previewWidth - 14;
        int bookmarkX = heartX - 14;
        int actionY = previewY + previewHeight - 14;
        boolean liked = isPresetLiked(preset);
        boolean saved = isPresetSavedLocally(preset);
        boolean manageablePreset = canManagePreset(preset);
        boolean deleteHovered = manageablePreset && isPointInRect(mouseX, mouseY, deleteX, deleteY, 12, 12);
        boolean bookmarkHovered = isPointInRect(mouseX, mouseY, bookmarkX, actionY, 12, 12);
        boolean heartHovered = isPointInRect(mouseX, mouseY, heartX, actionY, 12, 12);
        if (manageablePreset) {
            drawAnimatedDeleteIcon(context, deleteX, deleteY, preset, false, deleteHovered);
        }
        drawAnimatedBookmarkIcon(context, bookmarkX, actionY, preset, saved, false, bookmarkHovered);
        drawAnimatedHeartIcon(context, heartX, actionY, preset, liked, false, heartHovered);
        if (!preset.isPublished()) {
            drawPrivateEyeIcon(context, previewX + 6, previewY + 6, UITheme.STATE_WARNING);
        }
        int textX = rect.x + 8;
        String downloadsLine = Text.translatable("pathmind.marketplace.downloadsShort", preset.getDownloadsCount()).getString();
        String likesLine = Text.translatable("pathmind.marketplace.likesShort", preset.getLikesCount()).getString();
        int statsRight = rect.x + rect.width - 8;
        int downloadsColor = UITheme.STATE_SUCCESS;
        int likesColor = UITheme.MARKETPLACE_LIKE;
        int footerTop = previewY + previewHeight + 8;
        int statsLineY = footerTop + 3;
        int statsSecondLineY = footerTop + 14;
        int statsBlockWidth = Math.max(this.textRenderer.getWidth(downloadsLine), this.textRenderer.getWidth(likesLine));
        int textWidth = Math.max(32, rect.width - 16 - statsBlockWidth - 8);
        context.drawTextWithShadow(this.textRenderer,
            Text.literal(TextRenderUtil.trimWithEllipsis(this.textRenderer, preset.getName(), textWidth)),
            textX, footerTop - 1, UITheme.TEXT_HEADER);
        Rect authorRect = getCardAuthorRect(rect, preset);
        String authorLabel = TextRenderUtil.trimWithEllipsis(this.textRenderer, Text.translatable("pathmind.marketplace.byAuthor", fallback(preset.getAuthorName(), Text.translatable("pathmind.marketplace.unknown").getString())).getString(), textWidth);
        boolean authorHovered = isPointInRect(mouseX, mouseY, authorRect.x, authorRect.y, authorRect.width, authorRect.height);
        renderAuthorLink(context, "marketplace-author-card:" + preset.getId(), authorLabel, textX, footerTop + 10, authorHovered,
            UITheme.TEXT_SECONDARY, UITheme.TEXT_PRIMARY);
        context.drawTextWithShadow(this.textRenderer,
            Text.literal(downloadsLine),
            statsRight - this.textRenderer.getWidth(downloadsLine), statsLineY, downloadsColor);
        context.drawTextWithShadow(this.textRenderer,
            Text.literal(likesLine),
            statsRight - this.textRenderer.getWidth(likesLine), statsSecondLineY, likesColor);
    }

    private void renderAuthorDirectory(DrawContext context, int mouseX, int mouseY, Layout layout) {
        int entriesPerPage = getAuthorEntriesPerPage(layout);
        int startIndex = pageIndex * entriesPerPage;
        int endIndex = Math.min(authorResults.size(), startIndex + entriesPerPage);
        for (int index = startIndex; index < endIndex; index++) {
            Rect rect = getAuthorRowRect(layout, index - startIndex);
            renderAuthorRow(context, mouseX, mouseY, rect, authorResults.get(index), index == selectedIndex);
        }
    }

    private void renderAuthorRow(DrawContext context, int mouseX, int mouseY, Rect rect, AuthorSummary author, boolean selected) {
        boolean hovered = isPointInRect(mouseX, mouseY, rect.x, rect.y, rect.width, rect.height);
        float hoverProgress = HoverAnimator.getProgress("marketplace-author-row:" + author.key(), hovered);
        int bgColor = hoverProgress > 0.001f
            ? AnimationHelper.lerpColor(UITheme.BACKGROUND_TERTIARY, UITheme.TOOLBAR_BG_HOVER, hoverProgress * 0.45f)
            : UITheme.BACKGROUND_TERTIARY;
        int borderColor = AnimationHelper.lerpColor(UITheme.BORDER_SUBTLE, getAccentColor(), Math.max(hoverProgress, selected ? 0.55f : 0f));
        UIStyleHelper.drawBeveledPanel(context, rect.x, rect.y, rect.width, rect.height, bgColor, borderColor, UITheme.PANEL_INNER_BORDER);

        int avatarSize = 26;
        int avatarX = rect.x + 8;
        int avatarY = rect.y + (rect.height - avatarSize) / 2;
        renderAuthorDirectoryAvatar(context, avatarX, avatarY, avatarSize, author);

        int textX = avatarX + avatarSize + 8;
        int statsWidth = Math.max(
            this.textRenderer.getWidth(translatedCount("pathmind.marketplace.publicPresetCount", author.presetCount())),
            Math.max(this.textRenderer.getWidth(Text.translatable("pathmind.marketplace.downloadsShort", author.totalDownloads()).getString()), this.textRenderer.getWidth(Text.translatable("pathmind.marketplace.likesShort", author.totalLikes()).getString()))
        );
        int textWidth = Math.max(48, rect.width - (textX - rect.x) - statsWidth - 16);
        String authorName = TextRenderUtil.trimWithEllipsis(this.textRenderer, author.displayName(), textWidth);
        context.drawTextWithShadow(this.textRenderer, Text.literal(authorName), textX, rect.y + 8, UITheme.TEXT_HEADER);
        String secondary = translatedCount("pathmind.marketplace.publicPresetCount", author.presetCount());
        context.drawTextWithShadow(this.textRenderer, Text.literal(secondary), textX, rect.y + 20, UITheme.TEXT_SECONDARY);

        int statsX = rect.x + rect.width - statsWidth - 8;
        context.drawTextWithShadow(this.textRenderer, Text.translatable("pathmind.marketplace.downloadsShort", author.totalDownloads()), statsX, rect.y + 8, UITheme.STATE_SUCCESS);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("pathmind.marketplace.likesShort", author.totalLikes()), statsX, rect.y + 20, UITheme.MARKETPLACE_LIKE);
    }

    private void renderAuthorDirectoryAvatar(DrawContext context, int x, int y, int size, AuthorSummary author) {
        UIStyleHelper.drawBeveledPanel(context, x, y, size, size, UITheme.BACKGROUND_PRIMARY, getAccentColor(), UITheme.PANEL_INNER_BORDER);
        Identifier avatarTexture = getOrRequestAuthorDirectoryAvatarTexture(author);
        if (avatarTexture != null) {
            GuiTextureRenderer.drawIcon(context, avatarTexture, x + 2, y + 2, size - 4, UITheme.TEXT_HEADER);
            return;
        }
        String initials = fallback(author.displayName(), "?").trim();
        initials = initials.isEmpty() ? "?" : initials.substring(0, 1).toUpperCase(Locale.ROOT);
        int textX = x + (size - this.textRenderer.getWidth(initials)) / 2;
        int textY = y + (size - this.textRenderer.fontHeight) / 2;
        context.drawTextWithShadow(this.textRenderer, Text.literal(initials), textX, textY, UITheme.TEXT_HEADER);
    }

    PreviewGraphModel getCachedPreviewGraph(MarketplacePreset preset) {
        return previewLoader.getCached(preset);
    }

    void requestPreviewGraph(MarketplacePreset preset) {
        previewLoader.request(this.client, preset, authSession == null ? null : authSession.getAccessToken());
    }

    private void invalidatePreviewGraph(MarketplacePreset preset) {
        previewLoader.invalidate(preset);
    }

    private void drawGalleryBackdrop(DrawContext context, int x, int y, int width, int height) {
        int dotColor = UITheme.MARKETPLACE_PREVIEW_DOT;
        int lineColor = UITheme.MARKETPLACE_PREVIEW_LINE;
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

    Rect getPublishPopupVisibilityToggleRect(int popupX, int popupY, int popupWidth) {
        return new Rect(popupX + popupWidth - publishVisibilityToggle.getWidth() - 24, popupY + 188,
            publishVisibilityToggle.getWidth(), publishVisibilityToggle.getHeight());
    }

    int drawPopupEditableField(DrawContext context, int mouseX, int mouseY, int x, int y, int width, String label, TextFieldWidget field) {
        return PathmindPopupRenderer.drawPopupTextFieldRow(context, this.textRenderer, field, mouseX, mouseY, x, y, width,
            label, getAccentColor(), presetPopupAnimation);
    }

    void drawPopupFieldFrame(DrawContext context, int mouseX, int mouseY, int x, int y, int width, int height, TextFieldWidget field) {
        boolean hovered = isPointInRect(mouseX, mouseY, x, y, width, height);
        boolean focused = field != null && field.isFocused();
        PathmindPopupRenderer.drawPopupFieldFrame(context, x, y, width, height, hovered, focused, getAccentColor(), presetPopupAnimation);
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
        if (editorPopupMode && popupPreset == null && !presetPopupAnimation.isVisible()
            && !publishPopupOpen && !publishPopupAnimation.isVisible()) {
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
            int checkboxY = popupY + (pendingConfirmAction == ConfirmAction.UPDATE ? 148 : 102);
            if (pendingConfirmAction == ConfirmAction.UPDATE) {
                Rect sourceField = getUpdateConfirmSourceFieldRect(popupX, popupY, popupWidth);
                List<String> presetOptions = getUpdateSourcePresetOptions();
                if (updateConfirmSourceDropdownOpen) {
                    Rect dropdownBounds = getUpdateConfirmSourceDropdownBounds(popupX, popupY, popupWidth, presetOptions.size());
                    if (isPointInRect(mouseX, mouseY, dropdownBounds.x, dropdownBounds.y, dropdownBounds.width, dropdownBounds.height)) {
                        int optionIndex = Math.max(0, Math.min(presetOptions.size() - 1, (mouseY - dropdownBounds.y) / SORT_OPTION_HEIGHT));
                        publishSourcePresetName = presetOptions.get(optionIndex);
                        updateConfirmSourceDropdownOpen = false;
                        return true;
                    }
                    if (!isPointInRect(mouseX, mouseY, sourceField.x, sourceField.y, sourceField.width, sourceField.height)) {
                        updateConfirmSourceDropdownOpen = false;
                    }
                }
                if (isPointInRect(mouseX, mouseY, sourceField.x, sourceField.y, sourceField.width, sourceField.height)) {
                    updateConfirmSourceDropdownOpen = !updateConfirmSourceDropdownOpen;
                    return true;
                }
            }
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
                closePublishPopup(editorPopupMode && popupPreset == null && !presetPopupAnimation.isVisible());
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
                closePublishPopup(editorPopupMode && popupPreset == null && !presetPopupAnimation.isVisible());
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
            boolean ownPreset = canManagePreset(popupPreset);
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
            if (popupAuthorHitRect != null
                && isPointInRect(mouseX, mouseY, popupAuthorHitRect.x, popupAuthorHitRect.y, popupAuthorHitRect.width, popupAuthorHitRect.height)) {
                openAuthorProfile(popupPreset, true);
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
                    popupStatusMessage = Text.translatable("pathmind.status.noLocalChangesToUpload").getString();
                    popupStatusColor = UITheme.TEXT_SECONDARY;
                } else if (!publishBusy && !deleteBusy && !importingPreset) {
                    openConfirmPopup(ConfirmAction.UPDATE, popupPreset, true);
                }
                return true;
            }
            if (!authBusy && isPointInRect(mouseX, mouseY, authButtonX, buttonY, popup.buttonWidth, popup.buttonHeight)) {
                if (popupPreset != null && canManagePreset(popupPreset)) {
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
        if (!authBusy && isPointInRect(mouseX, mouseY, layout.accountButtonX, layout.accountButtonY, getAccountButtonWidth(), SORT_BUTTON_HEIGHT)) {
            handleAuthButton();
            return true;
        }
        if (isViewingAuthorProfile()) {
            Rect exitProfileRect = getExitProfileRect(layout);
            if (isPointInRect(mouseX, mouseY, exitProfileRect.x, exitProfileRect.y, exitProfileRect.width, exitProfileRect.height)) {
                exitAuthorProfile();
                return true;
            }
        }
        if (searchField != null && isPointInRect(mouseX, mouseY, layout.searchFieldX, layout.searchFieldY, SEARCH_FIELD_WIDTH, SEARCH_FIELD_HEIGHT)) {
            if (isViewingAuthorProfile()) {
                return true;
            }
            searchField.setFocused(true);
            searchField.mouseClicked(click, inBounds);
            return true;
        }
        if (searchField != null) {
            searchField.setFocused(false);
        }
        if (isPointInRect(mouseX, mouseY, layout.sortButtonX, layout.sortButtonY, SORT_BUTTON_WIDTH, SORT_BUTTON_HEIGHT)) {
            if (isViewingAuthorProfile()) {
                return true;
            }
            sortDropdownOpen = !sortDropdownOpen;
            return true;
        }
        if (isPointInRect(mouseX, mouseY, layout.myPresetsButtonX, layout.myPresetsButtonY, MY_PRESETS_BUTTON_WIDTH, SORT_BUTTON_HEIGHT)) {
            if (isViewingAuthorProfile()) {
                return true;
            }
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
            if (!isAuthorDirectoryMode()) {
                int headerHeight = getSectionHeaderHeight();
                int bodyHeight = layout.sectionHeight - headerHeight - FOOTER_HEIGHT;
                galleryScrollOffset = ScrollbarHelper.clampScroll(galleryScrollOffset - bodyHeight, getGalleryScrollMetrics(layout).maxScroll());
            } else {
                pageIndex = Math.max(0, pageIndex - 1);
            }
            return true;
        }
        if (pageHits.rightArrow() != null && isPointInRect(mouseX, mouseY, pageHits.rightArrow().x, pageHits.rightArrow().y, pageHits.rightArrow().width, pageHits.rightArrow().height)) {
            if (!isAuthorDirectoryMode()) {
                int headerHeight = getSectionHeaderHeight();
                int bodyHeight = layout.sectionHeight - headerHeight - FOOTER_HEIGHT;
                galleryScrollOffset = ScrollbarHelper.clampScroll(galleryScrollOffset + bodyHeight, getGalleryScrollMetrics(layout).maxScroll());
            } else {
                pageIndex = Math.min(getMaxPageIndex(), pageIndex + 1);
            }
            return true;
        }

        if (!isAuthorDirectoryMode() && !loading) {
            ScrollbarHelper.Metrics galleryMetrics = getGalleryScrollMetrics(layout);
            if (galleryMetrics.maxScroll() > 0 && isPointInRect(mouseX, mouseY, galleryMetrics.trackLeft(), galleryMetrics.thumbTop(), galleryMetrics.trackWidth(), galleryMetrics.thumbHeight())) {
                galleryScrollDragging = true;
                galleryScrollDragOffset = mouseY - galleryMetrics.thumbTop();
                return true;
            }
        }

        if (isAuthorDirectoryMode()) {
            int entriesPerPage = getAuthorEntriesPerPage(layout);
            int startIndex = pageIndex * entriesPerPage;
            int endIndex = Math.min(authorResults.size(), startIndex + entriesPerPage);
            for (int index = startIndex; index < endIndex; index++) {
                Rect rect = getAuthorRowRect(layout, index - startIndex);
                AuthorSummary author = authorResults.get(index);
                if (isPointInRect(mouseX, mouseY, rect.x, rect.y, rect.width, rect.height)) {
                    selectedIndex = index;
                    openAuthorProfile(author.representativePreset(), false);
                    return true;
                }
            }
        } else {
            int headerHeight = getSectionHeaderHeight();
            int viewportTop = layout.sectionY + headerHeight;
            int viewportBottom = viewportTop + layout.sectionHeight - headerHeight - FOOTER_HEIGHT;
            int firstVisibleIndex = getFirstVisibleCardIndex(layout, galleryScrollOffset);
            int lastVisibleIndex = getLastVisibleCardIndex(layout, galleryScrollOffset);
            for (int index = firstVisibleIndex; index <= lastVisibleIndex; index++) {
                Rect rect = getCardRect(layout, index, galleryScrollOffset);
                if (rect.y + rect.height < viewportTop || rect.y > viewportBottom) continue;
                MarketplacePreset preset = presets.get(index);
                int previewX = rect.x + 8;
                int previewY = rect.y + 8;
                int previewWidth = rect.width - 16;
                int previewHeight = rect.height - 46;
                Rect deleteHit = new Rect(previewX + previewWidth - 14, previewY + 2, 12, 12);
                Rect heartHit = new Rect(previewX + previewWidth - 14, previewY + previewHeight - 14, 12, 12);
                Rect bookmarkHit = new Rect(previewX + previewWidth - 28, previewY + previewHeight - 14, 12, 12);
                if (canManagePreset(preset) && isPointInRect(mouseX, mouseY, deleteHit.x, deleteHit.y, deleteHit.width, deleteHit.height)) {
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
                Rect authorHit = getCardAuthorRect(rect, preset);
                if (isPointInRect(mouseX, mouseY, authorHit.x, authorHit.y, authorHit.width, authorHit.height)) {
                    openAuthorProfile(preset, false);
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
        if (galleryScrollDragging) {
            Layout layout = getLayout();
            ScrollbarHelper.Metrics galleryMetrics = getGalleryScrollMetrics(layout);
            if (galleryMetrics.maxScroll() <= 0) {
                return true;
            }
            int desiredThumbY = (int) click.y() - galleryScrollDragOffset;
            int minThumbY = galleryMetrics.trackTop();
            int maxThumbY = galleryMetrics.trackTop() + Math.max(0, galleryMetrics.viewportHeight() - galleryMetrics.thumbHeight());
            int clampedThumbY = Math.max(minThumbY, Math.min(maxThumbY, desiredThumbY));
            galleryScrollOffset = ScrollbarHelper.scrollFromThumb(galleryMetrics, clampedThumbY);
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
        if (galleryScrollDragging) {
            galleryScrollDragging = false;
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

        if (!isAuthorDirectoryMode()) {
            ScrollbarHelper.Metrics galleryMetrics = getGalleryScrollMetrics(layout);
            int nextOffset = ScrollbarHelper.applyWheel(galleryScrollOffset, verticalAmount, 18, galleryMetrics.maxScroll());
            if (nextOffset != galleryScrollOffset) {
                galleryScrollOffset = nextOffset;
                return true;
            }
        } else {
            int maxPage = getMaxPageIndex();
            if (verticalAmount < 0 && pageIndex < maxPage) {
                pageIndex++;
                return true;
            }
            if (verticalAmount > 0 && pageIndex > 0) {
                pageIndex--;
                return true;
            }
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
            closePublishPopup(editorPopupMode && popupPreset == null && !presetPopupAnimation.isVisible());
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
        restoreSystemCursor();
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    @Override
    public void removed() {
        restoreSystemCursor();
        super.removed();
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
                    closePublishPopup(editorPopupMode && popupPreset == null && !presetPopupAnimation.isVisible());
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
        UIStyleHelper.drawTextButtonFrame(context, x, y, width, height, palette);
        int textColor = palette.textColor();
        int textX = x + (width - this.textRenderer.getWidth(label)) / 2;
        int textY = y + (height - this.textRenderer.fontHeight) / 2;
        context.drawTextWithShadow(this.textRenderer, Text.literal(label), textX, textY, textColor);
    }

    void drawAnimatedActionButton(DrawContext context, int x, int y, int width, int height, String label,
                                          boolean hovered, boolean disabled, PopupAnimationHandler animation) {
        drawAnimatedActionButton(context, x, y, width, height, label, hovered, disabled, animation, hovered ? 1f : 0f);
    }

    void drawAnimatedActionButton(DrawContext context, int x, int y, int width, int height, String label,
                                          boolean hovered, boolean disabled, PopupAnimationHandler animation, float hoverProgress) {
        PathmindPopupRenderer.drawAnimatedActionButton(context, this.textRenderer, x, y, width, height,
            label, disabled, hoverProgress, getAccentColor(), animation);
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
        SortMode[] modes = SortMode.values();
        PathmindDropdownRenderer.renderTextList(
            context,
            this.textRenderer,
            PathmindDropdownRenderer.TextListSpec.builder()
                .bounds(dropdownBounds.x, dropdownBounds.y, dropdownBounds.width)
                .rows(SORT_OPTION_HEIGHT, modes.length, modes.length)
                .scroll(0, 0, 0)
                .animation(animProgress)
                .hoverPoint(mouseX, mouseY)
                .colors(getAccentColor(), UITheme.TEXT_PRIMARY)
                .textLayout(8, 5, false, true)
                .labels("", index -> modes[index].label)
                .textColors(index -> modes[index] == sortMode ? getAccentColor() : UITheme.TEXT_PRIMARY)
                .chrome(new UIStyleHelper.ScrollContainerPalette(
                    UITheme.BACKGROUND_SECONDARY,
                    UITheme.BORDER_DEFAULT,
                    UITheme.PANEL_INNER_BORDER,
                    UITheme.BORDER_DEFAULT,
                    UITheme.BORDER_DEFAULT
                ), UITheme.BORDER_DEFAULT, UITheme.BORDER_DEFAULT, UITheme.BORDER_DEFAULT)
                .build()
        );
    }
    int drawWrappedValue(DrawContext context, int x, int y, int width, String value, int color, int maxLines) {
        for (String line : wrapText(value, width, maxLines)) {
            context.drawTextWithShadow(this.textRenderer, Text.literal(line), x, y, color);
            y += 10;
        }
        return y + 2;
    }

    void drawPopupStatPill(DrawContext context, int x, int y, int width, String label, String value) {
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

    int measureWrappedValueHeight(int width, String value, int maxLines) {
        return wrapText(value, width, maxLines).size() * 10 + 2;
    }

    int measurePopupContentHeight(int textWidth) {
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
                height += measureWrappedValueHeight(textWidth, Text.translatable("pathmind.marketplace.tagsValue", tagsLine).getString(), 2);
            }
        }
        height += 30 + 18;
        height += popupMetadataEditing ? 8 : 0;
        height += 30 + 8;
        height += measureWrappedValueHeight(textWidth,
            Text.translatable("pathmind.marketplace.publishedUpdated", formatTimestamp(popupPreset.getCreatedAt()), formatTimestamp(popupPreset.getUpdatedAt())).getString(),
            2);
        if (authSession == null) {
            height += measureWrappedValueHeight(textWidth,
                Text.translatable("pathmind.marketplace.signInLikeImport").getString(),
                2);
        }
        height += 2;
        height += popupMetadataEditing
            ? 46
            : Math.max(42, measureWrappedValueHeight(textWidth - 16, fallback(popupPreset.getDescription(), Text.translatable("pathmind.marketplace.noDescription").getString()), 5) + 18);
        return height;
    }

    ScrollbarHelper.Metrics getPopupScrollMetrics(int popupX, int popupY, int popupWidth, int popupHeight) {
        int contentTop = popupY + 40;
        int contentBottom = popupY + popupHeight - 48;
        int contentHeight = Math.max(24, contentBottom - contentTop);
        int contentHeightTotal = measurePopupContentHeight(Math.max(32, popupWidth - 24));
        int maxScroll = Math.max(0, contentHeightTotal - contentHeight);
        return ScrollbarHelper.metrics(popupX + popupWidth - 12, contentTop, 4, contentHeight, maxScroll, popupScrollOffset, 20);
    }

    private ScrollbarHelper.Metrics getGalleryScrollMetrics(Layout layout) {
        int headerHeight = getSectionHeaderHeight();
        int bodyY = layout.sectionY + headerHeight;
        int bodyHeight = layout.sectionHeight - headerHeight - FOOTER_HEIGHT;
        int columns = getGridColumns();
        int totalRows = (int) Math.ceil((double) presets.size() / Math.max(1, columns));
        int totalContentHeight = totalRows * (CARD_SIZE + CARD_GAP);
        int maxScroll = Math.max(0, totalContentHeight - bodyHeight);
        return ScrollbarHelper.metrics(layout.bodyX + layout.bodyWidth - 6, bodyY, 4, bodyHeight, maxScroll, galleryScrollOffset, 20);
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
        if (action == ConfirmAction.UPDATE) {
            publishSourcePresetName = fallback(findLocalPresetNameForMarketplacePreset(preset).orElse(null), PresetManager.getActivePreset());
            updateConfirmSourceDropdownOpen = false;
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
        updateConfirmSourceDropdownOpen = false;
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
        popupStatusMessage = Text.translatable("pathmind.status.downloadingPreset").getString();
        popupStatusColor = UITheme.TEXT_SECONDARY;

        PathmindMarketplaceAsyncController.downloadPresetToTempFile(
            this.client,
            popupPreset,
            authSession == null ? null : authSession.getAccessToken(),
            this::finishPresetImport
        );
    }

    private void finishPresetImport(Path path, Throwable throwable) {
        if (throwable != null || path == null) {
            importingPreset = false;
            popupStatusMessage = Text.translatable("pathmind.status.downloadFailed").getString();
            popupStatusColor = UITheme.STATE_ERROR;
            cleanupTempFile(path);
            return;
        }

        try {
            NodeGraphData data = NodeGraphPersistence.loadNodeGraphFromPath(path);
            if (data == null) {
                importingPreset = false;
                popupStatusMessage = Text.translatable("pathmind.status.invalidDownloadedPreset").getString();
                popupStatusColor = UITheme.STATE_ERROR;
                cleanupTempFile(path);
                return;
            }

            String requestedName = fallback(popupPreset.getSlug(), popupPreset.getName());
            java.util.Optional<String> importedPreset = PresetManager.importPresetFromFile(path, requestedName);
            if (importedPreset.isEmpty()) {
                importingPreset = false;
                popupStatusMessage = Text.translatable("pathmind.status.failedImportPreset").getString();
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
                    PathmindMarketplaceAsyncController.incrementDownload(this.client, session.getAccessToken(), importedMarketplacePreset.getId(), (unused, incrementThrowable) -> {
                        if (incrementThrowable == null) {
                            applyPresetCountUpdate(importedMarketplacePreset.getId(), 0, 1);
                        }
                    });
                }, null);
            }
            cleanupTempFile(path);
            importingPreset = false;
            popupStatusMessage = Text.translatable("pathmind.status.importedPreset", importedPreset.get()).getString();
            popupStatusColor = getAccentColor();
            presetPopupAnimation.hide();
            popupPreset = null;
            if (this.client != null) {
                PathmindScreens.openVisualEditorOrWarn(this.client, parent);
            }
        } catch (Exception e) {
            importingPreset = false;
            popupStatusMessage = Text.translatable("pathmind.status.importFailed").getString();
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

    boolean isPresetSavedLocally(MarketplacePreset preset) {
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
                    popupStatusMessage = deleted ? Text.translatable("pathmind.status.removedLocalSave").getString() : Text.translatable("pathmind.status.failedRemoveLocalSave").getString();
                    popupStatusColor = deleted ? UITheme.TEXT_SECONDARY : UITheme.STATE_ERROR;
                }
                return;
            }
        }
        importingPreset = true;
        if (popupPreset != null) {
            popupStatusMessage = isPresetSavedLocally(preset) ? Text.translatable("pathmind.status.alreadySavedLocally").getString() : Text.translatable("pathmind.status.savingPresetLocally").getString();
            popupStatusColor = UITheme.TEXT_SECONDARY;
        }
        PathmindMarketplaceAsyncController.downloadPresetToTempFile(
            this.client,
            preset,
            authSession == null ? null : authSession.getAccessToken(),
            (path, throwable) -> finishSavePresetLocally(preset, path, throwable, activateAfterImport)
        );
    }

    private void finishSavePresetLocally(MarketplacePreset preset, Path path, Throwable throwable, boolean activateAfterImport) {
        if (throwable != null || path == null) {
            importingPreset = false;
            if (popupPreset != null) {
                popupStatusMessage = Text.translatable("pathmind.status.failedSavePresetLocally").getString();
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
                    popupStatusMessage = Text.translatable("pathmind.status.failedSavePresetLocally").getString();
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
                popupStatusMessage = Text.translatable("pathmind.status.savedLocallyAs", importedPreset.get()).getString();
                popupStatusColor = UITheme.MARKETPLACE_SAVE;
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
                popupStatusMessage = Text.translatable("pathmind.status.failedSavePresetLocally").getString();
                popupStatusColor = UITheme.STATE_ERROR;
            }
        }
    }

    private void refreshAuthState(boolean silent) {
        authBusy = true;
        PathmindMarketplaceAsyncController.ensureValidSession(this.client, (session, throwable) -> {
                authBusy = false;
                if (throwable != null || session == null) {
                    authSession = null;
                    isMarketplaceModerator = false;
                    likedPresetIds.clear();
                    if (myPresetsOnly) {
                        refreshListings();
                    } else {
                        applyFilters();
                    }
                    if (!silent && popupPreset != null) {
                        popupStatusMessage = Text.translatable("pathmind.status.signInLikePresets").getString();
                        popupStatusColor = UITheme.TEXT_TERTIARY;
                    }
                    return;
                }
                authSession = session;
                refreshModeratorStatus(silent);
                if (myPresetsOnly) {
                    refreshListings();
                } else {
                    applyFilters();
                }
                refreshLikedPresetIds(silent);
        });
    }

    private void refreshLikedPresetIds(boolean silent) {
        if (authSession == null || authSession.getUserId() == null || authSession.getUserId().isBlank()) {
            likedPresetIds.clear();
            return;
        }
        authBusy = true;
        PathmindMarketplaceAsyncController.fetchLikedPresetIds(this.client, authSession.getAccessToken(), authSession.getUserId(), (likedPresetIds, throwable) -> {
                    authBusy = false;
                    if (throwable != null || likedPresetIds == null) {
                        if (!silent && popupPreset != null) {
                            popupStatusMessage = Text.translatable("pathmind.status.failedLoadLikes").getString();
                            popupStatusColor = UITheme.STATE_ERROR;
                        }
                        return;
                    }
                    this.likedPresetIds.clear();
                    this.likedPresetIds.addAll(likedPresetIds);
                    if (!silent && popupPreset != null) {
                        popupStatusMessage = Text.translatable("pathmind.status.signedInAs", fallback(authSession.getDisplayName(), fallback(authSession.getEmail(), Text.translatable("pathmind.status.discordUser").getString()))).getString();
                        popupStatusColor = getAccentColor();
                    }
                });
    }

    private void handleAuthButton() {
        if (authBusy) {
            return;
        }
        if (authSession == null) {
            authBusy = true;
            if (popupPreset != null) {
                popupStatusMessage = Text.translatable("pathmind.status.openingDiscordSignIn").getString();
                popupStatusColor = UITheme.TEXT_SECONDARY;
            }
            PathmindMarketplaceAsyncController.startDiscordSignIn(this.client, (session, throwable) -> {
                    authBusy = false;
                    if (throwable != null || session == null) {
                        if (popupPreset != null) {
                            popupStatusMessage = fallback(throwable == null ? null : throwable.getMessage(), Text.translatable("pathmind.status.discordSignInFailed").getString());
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
            return;
        }
        refreshModeratorStatus(false);
        accountPopupOpen = true;
        accountPopupAnimation.show();
    }

    private void startSignOut() {
        authBusy = true;
        PathmindMarketplaceAsyncController.signOut(this.client, (unused, throwable) -> {
                authBusy = false;
                authSession = null;
                isMarketplaceModerator = false;
                likedPresetIds.clear();
                closeAccountPopup();
                if (myPresetsOnly) {
                    refreshListings();
                } else {
                    applyFilters();
                }
                if (popupPreset != null) {
                    popupStatusMessage = throwable == null ? Text.translatable("pathmind.status.signedOut").getString() : Text.translatable("pathmind.status.failedSignOutCleanly").getString();
                    popupStatusColor = throwable == null ? UITheme.TEXT_SECONDARY : UITheme.STATE_ERROR;
                }
        });
    }

    private void closeAccountPopup() {
        accountPopupOpen = false;
        accountPopupAnimation.hide();
    }

    private void refreshModeratorStatus(boolean silent) {
        if (authSession == null || authSession.getUserId() == null || authSession.getUserId().isBlank()) {
            isMarketplaceModerator = false;
            return;
        }
        if (isKnownMarketplaceModerator(authSession.getUserId())) {
            isMarketplaceModerator = true;
            applyFilters();
            return;
        }
        PathmindMarketplaceAsyncController.fetchModeratorStatus(this.client, authSession.getAccessToken(), authSession.getUserId(), (moderator, throwable) -> {
                    if (throwable != null || moderator == null) {
                        if (!silent) {
                            applyFilters();
                        }
                        return;
                    }
                    isMarketplaceModerator = moderator;
                    applyFilters();
                });
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
        closePublishPopup(false);
    }

    private void closePublishPopup(boolean returnToParent) {
        publishPopupOpen = false;
        publishBusy = false;
        editingPreset = null;
        publishSourcePresetName = "";
        publishStatusMessage = "";
        publishStatusColor = UITheme.TEXT_SECONDARY;
        focusPublishField(null);
        publishPopupAnimation.hide();
        if (returnToParent && this.client != null) {
            this.client.setScreen(parent);
        }
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

    void renderAccountAvatar(DrawContext context, int x, int y, int size) {
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
                accountPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_HEADER)
            );
        } else {
            String initials = fallback(authSession == null ? null : authSession.getDisplayName(), "D").trim();
            initials = initials.isEmpty() ? "D" : initials.substring(0, 1).toUpperCase(Locale.ROOT);
            int textX = x + (size - this.textRenderer.getWidth(initials)) / 2;
            int textY = y + (size - this.textRenderer.fontHeight) / 2;
            context.drawTextWithShadow(this.textRenderer, Text.literal(initials), textX, textY, accountPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_HEADER));
        }
        float popupAlpha = Math.max(0f, Math.min(1f, accountPopupAnimation.getPopupAlpha()));
        if (popupAlpha < 0.999f) {
            int fadeAlpha = Math.min(255, Math.round((1f - popupAlpha) * 255f));
            DrawContextBridge.fillOverlay(context, x + 3, y + 3, x + size - 3, y + size - 3,
                (fadeAlpha << 24) | (UITheme.BACKGROUND_PRIMARY & 0x00FFFFFF));
        }

    }

    private void renderViewedAuthorAvatar(DrawContext context, int x, int y, int size) {
        UIStyleHelper.drawBeveledPanel(context, x, y, size, size, UITheme.BACKGROUND_PRIMARY, getAccentColor(), UITheme.PANEL_INNER_BORDER);
        Identifier avatarTexture = getOrRequestViewedAuthorAvatarTexture();
        if (avatarTexture != null) {
            GuiTextureRenderer.drawIcon(context, avatarTexture, x + 2, y + 2, size - 4, UITheme.TEXT_HEADER);
            return;
        }
        String initials = fallback(viewedAuthorName, "?").trim();
        initials = initials.isEmpty() ? "?" : initials.substring(0, 1).toUpperCase(Locale.ROOT);
        int textX = x + (size - this.textRenderer.getWidth(initials)) / 2;
        int textY = y + (size - this.textRenderer.fontHeight) / 2;
        context.drawTextWithShadow(this.textRenderer, Text.literal(initials), textX, textY, UITheme.TEXT_HEADER);
    }

    private Identifier getOrRequestAvatarTexture() {
        return avatarLoader.getAccount(this.client, authSession == null ? null : authSession.getAvatarUrl());
    }

    private Identifier getOrRequestViewedAuthorAvatarTexture() {
        return avatarLoader.getViewedAuthor(this.client, viewedAuthorAvatarUrl);
    }

    private Identifier getOrRequestAuthorDirectoryAvatarTexture(AuthorSummary author) {
        return avatarLoader.getAuthorDirectory(this.client, author == null ? null : author.avatarUrl());
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
            setActiveSubmissionStatus(Text.translatable("pathmind.status.enterPresetName").getString(), UITheme.STATE_ERROR);
            focusPublishField(publishNameField);
            return;
        }
        if (authSession == null) {
            setActiveSubmissionStatus(Text.translatable("pathmind.status.signInBeforePublishing").getString(), UITheme.STATE_WARNING);
            handleAuthButton();
            return;
        }

        Path localPresetPath = editingPreset == null ? PresetManager.getPresetPath(publishSourcePresetName) : null;
        if (editingPreset == null && (localPresetPath == null || !Files.exists(localPresetPath))) {
            setActiveSubmissionStatus(Text.translatable("pathmind.status.selectedPresetNotFound").getString(), UITheme.STATE_ERROR);
            return;
        }

        publishBusy = true;
        authBusy = true;
        setActiveSubmissionStatus(editingPreset == null ? Text.translatable("pathmind.status.publishingPreset").getString() : Text.translatable("pathmind.status.savingMetadata").getString(), UITheme.TEXT_SECONDARY);

        MarketplaceService.PublishRequest request = PathmindMarketplaceActions.publishRequest(
            localPresetPath,
            editingPreset,
            name,
            fallback(authSession.getDisplayName(), fallback(authSession.getEmail(), Text.translatable("pathmind.status.discordUser").getString())),
            publishDescriptionField == null ? "" : publishDescriptionField.getText().trim(),
            publishTagsField == null ? "" : publishTagsField.getText(),
            this.client != null ? this.client.getGameVersion() : Text.translatable("pathmind.marketplace.unknown").getString(),
            getInstalledPathmindVersion(),
            publishVisibilityPublic
        );

        PathmindMarketplaceFlowController.submitPublish(this.client, editingPreset, request, result -> {
            if (result.status() == PathmindMarketplaceFlowController.PublishStatus.SESSION_EXPIRED) {
                publishBusy = false;
                authBusy = false;
                authSession = null;
                setActiveSubmissionStatus(Text.translatable("pathmind.status.sessionExpiredSignInAgain").getString(), UITheme.STATE_ERROR);
                return;
            }
            authSession = result.session();
            if (result.status() == PathmindMarketplaceFlowController.PublishStatus.RATE_LIMITED) {
                publishBusy = false;
                authBusy = false;
                setActiveSubmissionStatus(result.limitMessage(), UITheme.STATE_WARNING);
                return;
            }
            finishPublishSubmission(result.preset(), result.throwable());
        });
    }

    private void finishPublishSubmission(MarketplacePreset preset, Throwable throwable) {
        boolean wasEditing = editingPreset != null;
        boolean inlineMetadataEdit = popupMetadataEditing && wasEditing;
        String sourcePresetName = publishSourcePresetName;
        publishBusy = false;
        authBusy = false;
        if (throwable != null) {
            setActiveSubmissionStatus(PathmindMarketplaceActions.extractThrowableMessage(throwable, wasEditing ? Text.translatable("pathmind.status.metadataUpdateFailed").getString() : Text.translatable("pathmind.status.publishFailed").getString()), UITheme.STATE_ERROR);
            return;
        }
        if (preset != null) {
            invalidatePreviewGraph(preset);
            upsertPreset(preset);
            requestPreviewGraph(preset);
        } else {
            refreshListings();
        }
        statusMessage = wasEditing ? Text.translatable("pathmind.status.metadataUpdated").getString() : Text.translatable("pathmind.status.presetPublished").getString();
        if (inlineMetadataEdit && popupPreset != null && preset != null && preset.getId() != null && preset.getId().equals(popupPreset.getId())) {
            popupStatusMessage = Text.translatable("pathmind.status.metadataUpdated").getString();
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
            openPresetPopup(preset, wasEditing ? Text.translatable("pathmind.status.presetUpdated").getString() : Text.translatable("pathmind.status.presetPublished").getString(), getAccentColor());
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
            popupStatusMessage = isPresetLiked(target) ? Text.translatable("pathmind.status.removingLike").getString() : Text.translatable("pathmind.status.savingLike").getString();
            popupStatusColor = UITheme.TEXT_SECONDARY;
        }
        withFreshAuthSession(session -> PathmindMarketplaceAsyncController.toggleLike(this.client, session.getAccessToken(), target.getId(), session.getUserId(), (liked, throwable) -> {
                    authBusy = false;
                    pendingLikePresetId = null;
                    if (throwable != null || liked == null) {
                        if (updatePopupStatus && popupPreset != null && popupPreset.getId() != null && popupPreset.getId().equals(target.getId())) {
                            popupStatusMessage = Text.translatable("pathmind.status.likeUpdateFailed").getString();
                            popupStatusColor = UITheme.STATE_ERROR;
                        }
                        return;
                    }
                    setPresetLiked(target.getId(), liked);
                    triggerLikePulse(target);
                    applyPresetCountUpdate(target.getId(), liked ? 1 : -1, 0);
                    if (updatePopupStatus && popupPreset != null && popupPreset.getId() != null && popupPreset.getId().equals(target.getId())) {
                        popupStatusMessage = liked ? Text.translatable("pathmind.status.presetLiked").getString() : Text.translatable("pathmind.status.likeRemoved").getString();
                        popupStatusColor = getAccentColor();
                    }
                }),
            updatePopupStatus ? Text.translatable("pathmind.status.sessionExpiredSignInAgain").getString() : null);
    }

    private void withFreshAuthSession(Consumer<MarketplaceAuthManager.AuthSession> action, String failureMessage) {
        PathmindMarketplaceAsyncController.ensureValidSession(this.client, (session, throwable) -> {
                if (throwable != null || session == null) {
                    authBusy = false;
                    pendingLikePresetId = null;
                    deleteBusy = false;
                    deletingPresetId = null;
                    authSession = null;
                    isMarketplaceModerator = false;
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
    }

    private void startDeletePreset(MarketplacePreset preset, boolean fromPopup) {
        if (preset == null || deleteBusy || publishBusy) {
            return;
        }
        if (authSession == null) {
            if (fromPopup) {
                popupStatusMessage = Text.translatable("pathmind.status.signInBeforeDeletingPresets").getString();
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
            popupStatusMessage = Text.translatable("pathmind.status.deletingPreset").getString();
            popupStatusColor = UITheme.TEXT_SECONDARY;
        }
        withFreshAuthSession(session -> PathmindMarketplaceAsyncController.deletePreset(this.client, session.getAccessToken(), preset, (unused, throwable) -> finishDeletePreset(preset, throwable, fromPopup)),
            fromPopup ? Text.translatable("pathmind.status.sessionExpiredSignInAgain").getString() : null);
    }

    private void finishDeletePreset(MarketplacePreset preset, Throwable throwable, boolean fromPopup) {
        deleteBusy = false;
        deletingPresetId = null;
        authBusy = false;
        if (throwable != null) {
            if (fromPopup && popupPreset != null && preset != null && preset.getId() != null && preset.getId().equals(popupPreset.getId())) {
                popupStatusMessage = PathmindMarketplaceActions.extractThrowableMessage(throwable, Text.translatable("pathmind.status.deleteFailed").getString());
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
        statusMessage = Text.translatable("pathmind.status.presetDeleted").getString();
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

    Optional<String> findLocalPresetNameForMarketplacePreset(MarketplacePreset preset) {
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

    boolean hasLinkedLocalPresetChanges(MarketplacePreset preset) {
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
        String selectedPresetName = fallback(publishSourcePresetName, "").trim();
        if (selectedPresetName.isEmpty()) {
            selectedPresetName = findLocalPresetNameForMarketplacePreset(popupPreset).orElse("");
        }
        if (selectedPresetName.isEmpty()) {
            popupStatusMessage = Text.translatable("pathmind.status.selectLocalPresetToUpload").getString();
            popupStatusColor = UITheme.STATE_ERROR;
            return;
        }
        final String updateSourcePresetName = selectedPresetName;
        Path localPresetPath = PresetManager.getPresetPath(updateSourcePresetName);
        if (!Files.exists(localPresetPath)) {
            popupStatusMessage = Text.translatable("pathmind.status.selectedPresetNotFound").getString();
            popupStatusColor = UITheme.STATE_ERROR;
            return;
        }

        publishBusy = true;
        authBusy = true;
        popupStatusMessage = Text.translatable("pathmind.status.updatingUploadedPreset").getString();
        popupStatusColor = UITheme.TEXT_SECONDARY;
        MarketplaceService.PublishRequest request = PathmindMarketplaceActions.updateFromLocalRequest(localPresetPath, popupPreset);

        PathmindMarketplaceFlowController.submitPresetUpdate(this.client, popupPreset, request, result -> {
            if (result.status() == PathmindMarketplaceFlowController.PublishStatus.SESSION_EXPIRED) {
                publishBusy = false;
                authBusy = false;
                authSession = null;
                isMarketplaceModerator = false;
                likedPresetIds.clear();
                popupStatusMessage = Text.translatable("pathmind.status.sessionExpiredSignInAgain").getString();
                popupStatusColor = UITheme.STATE_ERROR;
                return;
            }
            authSession = result.session();
            publishBusy = false;
            authBusy = false;
            MarketplacePreset updatedPreset = result.preset();
            Throwable throwable = result.throwable();
            if (throwable != null || updatedPreset == null) {
                popupStatusMessage = PathmindMarketplaceActions.extractThrowableMessage(throwable, Text.translatable("pathmind.status.presetUpdateFailed").getString());
                popupStatusColor = UITheme.STATE_ERROR;
                return;
            }
            PresetManager.setMarketplaceLinkedPreset(updateSourcePresetName, updatedPreset.getId());
            invalidatePreviewGraph(updatedPreset);
            upsertPreset(updatedPreset);
            popupPreset = updatedPreset;
            requestPreviewGraph(updatedPreset);
            popupStatusMessage = Text.translatable("pathmind.status.presetUpdatedFromLocalChanges").getString();
            popupStatusColor = getAccentColor();
            applyFilters();
        });
    }

    private boolean isOwnPreset(MarketplacePreset preset) {
        return PathmindMarketplaceActions.isOwnPreset(preset, authSession == null ? null : authSession.getUserId());
    }

    boolean canManagePreset(MarketplacePreset preset) {
        return PathmindMarketplaceActions.canManagePreset(preset, authSession == null ? null : authSession.getUserId(), isMarketplaceModerator);
    }

    private boolean isKnownMarketplaceModerator(String userId) {
        return PathmindMarketplaceActions.isKnownMarketplaceModerator(userId);
    }

    List<String> getUpdateSourcePresetOptions() {
        List<String> availablePresets = PresetManager.getAvailablePresets();
        List<String> presets = new ArrayList<>();
        for (String presetName : availablePresets) {
            if (presetName == null || presetName.isBlank()) {
                continue;
            }
            if (Files.exists(PresetManager.getPresetPath(presetName))) {
                presets.add(presetName);
            }
        }
        return presets;
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

    boolean isPresetLiked(MarketplacePreset preset) {
        return preset != null && preset.getId() != null && likedPresetIds.contains(preset.getId());
    }

    String getPopupAuthButtonLabel() {
        if (popupPreset != null && canManagePreset(popupPreset)) {
            if (publishBusy) {
                return Text.translatable("pathmind.status.working").getString();
            }
            return popupMetadataEditing ? Text.translatable("pathmind.button.save").getString() : Text.translatable("pathmind.button.edit").getString();
        }
        if (authBusy) {
            return Text.translatable("pathmind.status.working").getString();
        }
        if (authSession == null) {
            return Text.translatable("pathmind.marketplace.signIn").getString();
        }
        return isPresetLiked(popupPreset) ? Text.translatable("pathmind.button.unlike").getString() : Text.translatable("pathmind.button.like").getString();
    }

    String getPublishAuthButtonLabel() {
        if (authBusy) {
            return Text.translatable("pathmind.status.working").getString();
        }
        if (authSession == null) {
            return Text.translatable("pathmind.marketplace.signIn").getString();
        }
        return Text.translatable("pathmind.marketplace.signedIn").getString();
    }

    private void applyPresetCountUpdate(String presetId, int likesDelta, int downloadsDelta) {
        if (presetId == null || presetId.isBlank()) {
            return;
        }
        allPresets = PathmindMarketplaceActions.updatePresetCountList(allPresets, presetId, likesDelta, downloadsDelta);
        presets = PathmindMarketplaceActions.updatePresetCountList(presets, presetId, likesDelta, downloadsDelta);
        if (popupPreset != null && presetId.equals(popupPreset.getId())) {
            popupPreset = PathmindMarketplaceActions.updatedPresetCounts(popupPreset, likesDelta, downloadsDelta);
        }
    }

    private void upsertPreset(MarketplacePreset preset) {
        if (preset == null || preset.getId() == null || preset.getId().isBlank()) {
            refreshListings();
            return;
        }
        allPresets = PathmindMarketplaceActions.upsertPresetList(allPresets, preset);
        presets = PathmindMarketplaceActions.upsertPresetList(presets, preset);
        if (popupPreset != null && preset.getId().equals(popupPreset.getId())) {
            popupPreset = preset;
        }
    }

    private void removePreset(String presetId) {
        if (presetId == null || presetId.isBlank()) {
            refreshListings();
            return;
        }
        allPresets = PathmindMarketplaceActions.removePresetFromList(allPresets, presetId);
        presets = PathmindMarketplaceActions.removePresetFromList(presets, presetId);
        if (popupPreset != null && presetId.equals(popupPreset.getId())) {
            popupPreset = null;
        }
        selectedIndex = presets.isEmpty() ? -1 : Math.max(0, Math.min(selectedIndex, presets.size() - 1));
    }

    int getAccentColor() {
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
        int offColor = UITheme.MARKETPLACE_PRIVATE_VISIBILITY;
        int onColor = getAccentColor();
        publishVisibilityToggle.setIndicatorColors(offColor, onColor);
        presetVisibilityToggle.setIndicatorColors(offColor, onColor);
    }

    String fallback(String value, String fallback) {
        return PathmindMarketplaceActions.fallback(value, fallback);
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
        context.drawHorizontalLine(x + 3, x + 6, y + 1, color);
        context.drawHorizontalLine(x + 3, x + 6, y + 8, color);
        context.drawVerticalLine(x + 1, y + 3, y + 6, color);
        context.drawVerticalLine(x + 8, y + 3, y + 6, color);
        context.drawHorizontalLine(x + 2, x + 2, y + 2, color);
        context.drawHorizontalLine(x + 7, x + 7, y + 2, color);
        context.drawHorizontalLine(x + 2, x + 2, y + 7, color);
        context.drawHorizontalLine(x + 7, x + 7, y + 7, color);
        context.fill(x + 8, y + 8, x + 10, y + 10, color);
        context.fill(x + 10, y + 10, x + 12, y + 12, color);
    }

    void drawDropdownChevron(DrawContext context, int x, int y, int color, boolean open) {
        PathmindPopupRenderer.drawDropdownChevron(context, x, y, color, open);
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

    private void drawAccountProfileIcon(DrawContext context, int x, int y, int color) {
        context.fill(x + 4, y + 1, x + 8, y + 2, color);
        context.fill(x + 3, y + 2, x + 9, y + 4, color);
        context.fill(x + 2, y + 4, x + 10, y + 6, color);
        context.fill(x + 3, y + 6, x + 9, y + 7, color);
        context.fill(x + 4, y + 7, x + 8, y + 8, color);

        context.fill(x + 4, y + 8, x + 8, y + 9, color);
        context.fill(x + 2, y + 9, x + 10, y + 10, color);
        context.fill(x + 1, y + 10, x + 11, y + 11, color);
    }

    private void drawAnimatedDeleteIcon(DrawContext context, int x, int y, MarketplacePreset preset, boolean popup, boolean hovered) {
        float pulse = getIconPulse(deletePulseEndTimes, preset);
        float hoverFlash = getIconHoverFlash(hovered, popup);
        float intensity = Math.max(pulse, hoverFlash);
        boolean pending = isDeletePending(preset);
        int baseColor = pending ? UITheme.TEXT_TERTIARY : UITheme.STATE_ERROR;
        int color = popup ? presetPopupAnimation.getAnimatedPopupColor(baseColor) : baseColor;
        if (intensity > 0.001f) {
            int glowColor = popup
                ? presetPopupAnimation.getAnimatedPopupColor(AnimationHelper.lerpColor(baseColor, UITheme.TEXT_HEADER, intensity * 0.35f))
                : AnimationHelper.lerpColor(baseColor, UITheme.TEXT_HEADER, intensity * 0.35f);
            context.fill(x - 2, y - 2, x + 12, y + 13, (Math.min(120, Math.round(intensity * 110)) << 24) | (glowColor & 0x00FFFFFF));
            color = popup
                ? presetPopupAnimation.getAnimatedPopupColor(AnimationHelper.lerpColor(baseColor, UITheme.TEXT_HEADER, intensity * 0.22f))
                : AnimationHelper.lerpColor(baseColor, UITheme.TEXT_HEADER, intensity * 0.22f);
        }
        drawDeleteIcon(context, x, y, color);
    }

    void drawAnimatedHeartIcon(DrawContext context, int x, int y, MarketplacePreset preset, boolean liked, boolean popup, boolean hovered) {
        float pulse = getIconPulse(likePulseEndTimes, preset);
        float hoverFlash = getIconHoverFlash(hovered, popup);
        float intensity = Math.max(pulse, hoverFlash);
        boolean pending = isLikePending(preset);
        int baseColor = pending ? UITheme.TEXT_TERTIARY : liked ? UITheme.MARKETPLACE_LIKE : UITheme.TEXT_TERTIARY;
        int color = popup ? presetPopupAnimation.getAnimatedPopupColor(baseColor) : baseColor;
        if (intensity > 0.001f) {
            int glowColor = popup
                ? presetPopupAnimation.getAnimatedPopupColor(AnimationHelper.lerpColor(baseColor, UITheme.TEXT_HEADER, intensity * 0.35f))
                : AnimationHelper.lerpColor(baseColor, UITheme.TEXT_HEADER, intensity * 0.35f);
            context.fill(x - 2, y - 2, x + 12, y + 13, (Math.min(120, Math.round(intensity * 110)) << 24) | (glowColor & 0x00FFFFFF));
            color = popup
                ? presetPopupAnimation.getAnimatedPopupColor(AnimationHelper.lerpColor(baseColor, UITheme.TEXT_HEADER, intensity * 0.22f))
                : AnimationHelper.lerpColor(baseColor, UITheme.TEXT_HEADER, intensity * 0.22f);
        }
        drawHeartIcon(context, x, y, color);
    }

    void drawAnimatedBookmarkIcon(DrawContext context, int x, int y, MarketplacePreset preset, boolean saved, boolean popup, boolean hovered) {
        float pulse = getIconPulse(savePulseEndTimes, preset);
        float hoverFlash = getIconHoverFlash(hovered, popup);
        float intensity = Math.max(pulse, hoverFlash);
        int baseColor = saved ? UITheme.MARKETPLACE_SAVE : UITheme.TEXT_TERTIARY;
        int color = popup ? presetPopupAnimation.getAnimatedPopupColor(baseColor) : baseColor;
        if (intensity > 0.001f) {
            int glowColor = popup
                ? presetPopupAnimation.getAnimatedPopupColor(AnimationHelper.lerpColor(baseColor, UITheme.TEXT_HEADER, intensity * 0.35f))
                : AnimationHelper.lerpColor(baseColor, UITheme.TEXT_HEADER, intensity * 0.35f);
            context.fill(x - 2, y - 2, x + 12, y + 13, (Math.min(120, Math.round(intensity * 110)) << 24) | (glowColor & 0x00FFFFFF));
            color = popup
                ? presetPopupAnimation.getAnimatedPopupColor(AnimationHelper.lerpColor(baseColor, UITheme.TEXT_HEADER, intensity * 0.22f))
                : AnimationHelper.lerpColor(baseColor, UITheme.TEXT_HEADER, intensity * 0.22f);
        }
        drawBookmarkIcon(context, x, y, color);
    }

    private float getIconHoverFlash(boolean hovered, boolean popup) {
        if (!hovered) {
            return 0f;
        }
        float cycle = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 120.0);
        return (popup ? 0.22f : 0.16f) + cycle * (popup ? 0.12f : 0.08f);
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
        return PathmindMarketplaceLayout.screen(this.width, this.height, getAccountButtonWidth());
    }

    PopupLayout getPopupLayout(Layout layout) {
        return PathmindMarketplaceLayout.presetPopup(this.width, this.height);
    }

    ConfirmPopupLayout getConfirmPopupLayout(Layout layout) {
        return PathmindMarketplaceLayout.confirmPopup(this.width, this.height, pendingConfirmAction == ConfirmAction.UPDATE);
    }

    Rect getUpdateConfirmSourceFieldRect(int popupX, int popupY, int popupWidth) {
        return PathmindMarketplaceLayout.updateConfirmSourceField(popupX, popupY, popupWidth);
    }

    Rect getUpdateConfirmSourceDropdownBounds(int popupX, int popupY, int popupWidth, int optionCount) {
        return PathmindMarketplaceLayout.updateConfirmSourceDropdown(popupX, popupY, popupWidth, optionCount);
    }

    Rect getPopupPreviewRect(int popupX, int popupY, int popupWidth, int popupHeight, int scrollOffset) {
        return PathmindMarketplaceLayout.popupPreview(popupX, popupY, popupWidth, scrollOffset);
    }

    AccountPopupLayout getAccountPopupLayout(Layout layout) {
        return PathmindMarketplaceLayout.accountPopup(this.width, this.height);
    }

    PublishPopupLayout getPublishPopupLayout(Layout layout) {
        return PathmindMarketplaceLayout.publishPopup(this.width, this.height);
    }

    private Rect getCardRect(Layout layout, int absoluteIndex, int scrollOffset) {
        return PathmindMarketplaceLayout.card(layout, absoluteIndex, getSectionHeaderHeight(), scrollOffset);
    }

    private int getGridColumns() {
        return PathmindMarketplaceLayout.gridColumns();
    }

    private int getCardsPerPage(Layout layout) {
        return PathmindMarketplaceLayout.cardsPerPage();
    }

    private int getFirstVisibleCardIndex(Layout layout, int scrollOffset) {
        return PathmindMarketplaceLayout.firstVisibleCardIndex(presets.size(), scrollOffset);
    }

    private int getLastVisibleCardIndex(Layout layout, int scrollOffset) {
        return PathmindMarketplaceLayout.lastVisibleCardIndex(layout, presets.size(), getSectionHeaderHeight(), scrollOffset);
    }

    private int getAuthorEntriesPerPage(Layout layout) {
        return PathmindMarketplaceLayout.authorEntriesPerPage(layout, getSectionHeaderHeight());
    }

    private int getEntriesPerPage(Layout layout) {
        return isAuthorDirectoryMode() ? getAuthorEntriesPerPage(layout) : getCardsPerPage(layout);
    }

    private int getCurrentResultCount() {
        return isAuthorDirectoryMode() ? authorResults.size() : presets.size();
    }

    private int getMaxPageIndex() {
        Layout layout = getLayout();
        int entriesPerPage = getEntriesPerPage(layout);
        int resultCount = getCurrentResultCount();
        return Math.max(0, (resultCount - 1) / entriesPerPage);
    }

    private int getPageLabelWidth(int pageNumber) {
        if (pageNumber <= 0 || pageNumber > getMaxPageIndex() + 1) {
            return 0;
        }
        return this.textRenderer.getWidth(Integer.toString(pageNumber));
    }

    private Rect getSortDropdownBounds(Layout layout) {
        return PathmindMarketplaceLayout.sortDropdown(layout, SortMode.values().length);
    }

    private Rect getAuthorRowRect(Layout layout, int pageOffset) {
        return PathmindMarketplaceLayout.authorRow(layout, pageOffset, getSectionHeaderHeight());
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

    static String formatTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return Text.translatable("pathmind.marketplace.noTags").getString();
        }
        return String.join(", ", tags);
    }

    private static String translatedCount(String baseKey, int count) {
        return Text.translatable(count == 1 ? baseKey + ".one" : baseKey + ".other", count).getString();
    }

    static String formatTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return Text.translatable("pathmind.marketplace.unknown").getString();
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
            if (!myPresetsOnly && !preset.isPublished()) {
                continue;
            }
            if (myPresetsOnly && !canManagePreset(preset)) {
                continue;
            }
            if (myPresetsOnly && !myPresetsFilter.matches(preset)) {
                continue;
            }
            if (!sortMode.matches(this, preset)) {
                continue;
            }
            if (isViewingAuthorProfile() && (!preset.isPublished() || !isViewedAuthorPreset(preset))) {
                continue;
            }
            boolean matches = isAuthorDirectoryMode()
                ? query.isEmpty() || containsNormalized(preset.getAuthorName(), query)
                : query.isEmpty() || matchesQuery(preset, query);
            if (matches) {
                filtered.add(preset);
            }
        }
        filtered.sort(sortMode.comparator);
        presets = PathmindMarketplaceActions.dedupePresetsById(filtered);
        authorResults = buildAuthorResults(filtered);
        pageIndex = Math.max(0, Math.min(pageIndex, getMaxPageIndex()));
        int currentCount = getCurrentResultCount();
        selectedIndex = currentCount == 0 ? -1 : Math.max(0, Math.min(selectedIndex, currentCount - 1));
        if (myPresetsOnly && authSession == null) {
            statusMessage = Text.translatable("pathmind.status.signInViewPresets").getString();
        } else if (isAuthorDirectoryMode() && authorResults.isEmpty()) {
            statusMessage = query.isEmpty() ? Text.translatable("pathmind.status.noAuthorsPublic").getString() : Text.translatable("pathmind.status.noAuthorsSearch").getString();
        } else if (isViewingAuthorProfile() && presets.isEmpty()) {
            statusMessage = Text.translatable("pathmind.status.noCreatorPresets").getString();
        } else if (allPresets.isEmpty()) {
            statusMessage = myPresetsOnly ? Text.translatable("pathmind.status.noCloudPresets").getString() : Text.translatable("pathmind.status.noPublishedPresets").getString();
        } else if (presets.isEmpty()) {
            if (myPresetsOnly) {
                statusMessage = switch (myPresetsFilter) {
                    case PUBLIC -> Text.translatable("pathmind.status.noPublicSearch").getString();
                    case PRIVATE -> Text.translatable("pathmind.status.noPrivateSearch").getString();
                    default -> Text.translatable("pathmind.status.noPresetsSearch").getString();
                };
            } else {
                statusMessage = sortMode == SortMode.SAVED ? Text.translatable("pathmind.status.noSavedSearch").getString() : Text.translatable("pathmind.status.noPresetsSearch").getString();
            }
        } else {
            if (isAuthorDirectoryMode()) {
                statusMessage = Text.translatable("pathmind.status.loadedAuthors", authorResults.size(), authorResults.size() == 1 ? "" : "s").getString();
            } else {
                statusMessage = translatedCount("pathmind.status.loadedPresets", presets.size());
            }
        }
    }

    private List<AuthorSummary> buildAuthorResults(List<MarketplacePreset> filtered) {
        if (filtered == null || filtered.isEmpty()) {
            return List.of();
        }
        Map<String, AuthorAccumulator> authors = new LinkedHashMap<>();
        for (MarketplacePreset preset : filtered) {
            if (preset == null || !preset.isPublished()) {
                continue;
            }
            String key = buildAuthorKey(preset);
            if (key == null || key.isBlank()) {
                continue;
            }
            AuthorAccumulator accumulator = authors.computeIfAbsent(key, ignored -> new AuthorAccumulator(
                key,
                fallback(preset.getAuthorName(), Text.translatable("pathmind.marketplace.unknown").getString()),
                fallback(preset.getAuthorAvatarUrl(), ""),
                preset
            ));
            accumulator.presetCount++;
            accumulator.totalLikes += Math.max(0, preset.getLikesCount());
            accumulator.totalDownloads += Math.max(0, preset.getDownloadsCount());
            if ((accumulator.avatarUrl == null || accumulator.avatarUrl.isBlank())
                && preset.getAuthorAvatarUrl() != null && !preset.getAuthorAvatarUrl().isBlank()) {
                accumulator.avatarUrl = preset.getAuthorAvatarUrl();
            }
        }
        List<AuthorSummary> summaries = new ArrayList<>(authors.size());
        for (AuthorAccumulator accumulator : authors.values()) {
            summaries.add(new AuthorSummary(
                accumulator.key,
                accumulator.displayName,
                accumulator.avatarUrl,
                accumulator.presetCount,
                accumulator.totalLikes,
                accumulator.totalDownloads,
                accumulator.representativePreset
            ));
        }
        summaries.sort(Comparator.comparing((AuthorSummary author) -> normalizeSearch(author.displayName())));
        return List.copyOf(summaries);
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

    private Rect getCardAuthorRect(Rect cardRect, MarketplacePreset preset) {
        String downloadsLine = Text.translatable("pathmind.marketplace.downloadsShort", preset.getDownloadsCount()).getString();
        String likesLine = Text.translatable("pathmind.marketplace.likesShort", preset.getLikesCount()).getString();
        int statsBlockWidth = Math.max(this.textRenderer.getWidth(downloadsLine), this.textRenderer.getWidth(likesLine));
        int textWidth = Math.max(32, cardRect.width - 16 - statsBlockWidth - 8);
        int previewY = cardRect.y + 8;
        int previewHeight = cardRect.height - 46;
        int footerTop = previewY + previewHeight + 8;
        int authorY = footerTop + 10;
        int textX = cardRect.x + 8;
        String authorLabel = TextRenderUtil.trimWithEllipsis(this.textRenderer, Text.translatable("pathmind.marketplace.byAuthor", fallback(preset.getAuthorName(), Text.translatable("pathmind.marketplace.unknown").getString())).getString(), textWidth);
        return new Rect(textX, authorY, this.textRenderer.getWidth(authorLabel), this.textRenderer.fontHeight + 1);
    }

    void renderAuthorLink(DrawContext context, String key, String label, int x, int y, boolean hovered, int baseColor, int hoverColor) {
        float hoverProgress = HoverAnimator.getProgress(key, hovered);
        int textColor = AnimationHelper.lerpColor(baseColor, hoverColor, hoverProgress);
        context.drawTextWithShadow(this.textRenderer, Text.literal(label), x, y, textColor);
        if (hoverProgress > 0.001f) {
            int fullWidth = this.textRenderer.getWidth(label);
            int underlineWidth = Math.round(fullWidth * hoverProgress);
            if (underlineWidth > 0) {
                int underlineStartX = x + (fullWidth - underlineWidth) / 2;
                int underlineY = y + this.textRenderer.fontHeight;
                context.fill(underlineStartX, underlineY, underlineStartX + underlineWidth, underlineY + 1, hoverColor);
            }
        }
    }

    private Rect getExitProfileRect(Layout layout) {
        return PathmindMarketplaceLayout.exitProfile(layout);
    }

    private void openAuthorProfile(MarketplacePreset preset, boolean closePopup) {
        if (preset == null) {
            return;
        }
        String authorKey = buildAuthorKey(preset);
        if (authorKey == null) {
            return;
        }
        viewedAuthorKey = authorKey;
        viewedAuthorName = fallback(preset.getAuthorName(), Text.translatable("pathmind.marketplace.unknown").getString());
        viewedAuthorAvatarUrl = resolveViewedAuthorAvatarUrl(preset);
        avatarLoader.clearViewedAuthor();
        myPresetsOnly = false;
        myPresetsFilter = MyPresetsFilter.ALL;
        pageIndex = 0;
        galleryScrollOffset = 0;
        selectedIndex = -1;
        applyFilters();
        if (closePopup) {
            closePopup();
        }
    }

    private void exitAuthorProfile() {
        viewedAuthorKey = null;
        viewedAuthorName = null;
        viewedAuthorAvatarUrl = null;
        avatarLoader.clearViewedAuthor();
        pageIndex = 0;
        galleryScrollOffset = 0;
        applyFilters();
    }

    private boolean isViewingAuthorProfile() {
        return viewedAuthorKey != null && !viewedAuthorKey.isBlank();
    }

    private boolean isViewedAuthorPreset(MarketplacePreset preset) {
        return viewedAuthorKey != null && viewedAuthorKey.equals(buildAuthorKey(preset));
    }

    private String buildAuthorKey(MarketplacePreset preset) {
        if (preset == null) {
            return null;
        }
        String userId = fallback(preset.getAuthorUserId(), "").trim();
        if (!userId.isEmpty()) {
            return "id:" + userId;
        }
        String authorName = normalizeSearch(fallback(preset.getAuthorName(), ""));
        return authorName.isEmpty() ? null : "name:" + authorName;
    }

    private boolean isAuthorDirectoryMode() {
        return !myPresetsOnly && !isViewingAuthorProfile() && sortMode == SortMode.AUTHOR;
    }

    private String resolveViewedAuthorAvatarUrl(MarketplacePreset preset) {
        if (preset == null) {
            return null;
        }
        String presetAvatar = fallback(preset.getAuthorAvatarUrl(), "");
        if (!presetAvatar.isBlank()) {
            return presetAvatar;
        }
        if (authSession != null
            && authSession.getAvatarUrl() != null
            && preset.getAuthorUserId() != null
            && preset.getAuthorUserId().equals(authSession.getUserId())) {
            return authSession.getAvatarUrl();
        }
        return null;
    }

    private String getAccountButtonLabel() {
        if (authBusy) {
            return "…";
        }
        if (authSession == null) {
            return "Discord";
        }
        String displayName = fallback(authSession.getDisplayName(), authSession.getEmail());
        return fallback(displayName, "Discord");
    }

    private int getAccountButtonWidth() {
        if (authSession == null) {
            return ACCOUNT_BUTTON_MIN_WIDTH;
        }
        String label = getAccountButtonLabel();
        int labelWidth = this.textRenderer == null ? 0 : this.textRenderer.getWidth(label);
        return Math.max(ACCOUNT_BUTTON_MIN_WIDTH, labelWidth + SORT_BUTTON_HEIGHT + 10);
    }

    private String normalizeSearch(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    CompatibilityStatus getCompatibilityStatus(MarketplacePreset preset) {
        String currentMinecraftVersion = this.client != null ? this.client.getGameVersion() : Text.translatable("pathmind.marketplace.unknown").getString();
        String currentPathmindVersion = getInstalledPathmindVersion();

        String presetMinecraftVersion = fallback(preset.getGameVersion(), Text.translatable("pathmind.marketplace.versionAny").getString());
        boolean minecraftCompatible = isVersionLooseMatch(presetMinecraftVersion, currentMinecraftVersion)
            || isAnyVersion(presetMinecraftVersion);
        String minecraftLine = minecraftCompatible
            ? Text.translatable("pathmind.marketplace.minecraftCompatible", currentMinecraftVersion).getString()
            : Text.translatable("pathmind.marketplace.minecraftMismatch", presetMinecraftVersion, currentMinecraftVersion).getString();

        String presetPathmindVersion = fallback(preset.getPathmindVersion(), Text.translatable("pathmind.marketplace.unknown").getString());
        boolean pathmindCompatible = isVersionLooseMatch(presetPathmindVersion, currentPathmindVersion)
            || isAnyVersion(presetPathmindVersion)
            || "current".equalsIgnoreCase(presetPathmindVersion);
        String pathmindLine = pathmindCompatible
            ? Text.translatable("pathmind.marketplace.pathmindCompatible", currentPathmindVersion).getString()
            : Text.translatable("pathmind.marketplace.pathmindMismatch", presetPathmindVersion, currentPathmindVersion).getString();

        return new CompatibilityStatus(
            minecraftLine,
            minecraftCompatible ? getAccentColor() : UITheme.STATE_WARNING,
            pathmindLine,
            pathmindCompatible ? getAccentColor() : UITheme.STATE_WARNING,
            minecraftCompatible && pathmindCompatible
        );
    }

    int drawStatusBadge(DrawContext context, int x, int y, String label, int accentColor, boolean popup) {
        return PathmindPopupRenderer.drawStatusBadge(context, this.textRenderer, x, y, fallback(label, ""), accentColor,
            popup ? presetPopupAnimation : null);
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
        return LoaderMetadata.getModVersion(PathmindCommon.MOD_ID);
    }


    int screenWidth() {
        return this.width;
    }

    int screenHeight() {
        return this.height;
    }

    net.minecraft.client.font.TextRenderer textRenderer() {
        return this.textRenderer;
    }

    static boolean isPointInRect(int x, int y, int rectX, int rectY, int width, int height) {
        return x >= rectX && y >= rectY && x < rectX + width && y < rectY + height;
    }

    private int getSectionHeaderHeight() {
        return PathmindMarketplaceLayout.sectionHeaderHeight(isViewingAuthorProfile(), myPresetsOnly);
    }

    record Rect(int x, int y, int width, int height) {
    }

    private record PageHitAreas(Rect leftArrow, Rect rightArrow) {
    }

    record Layout(
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

    record PopupLayout(
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

    record AccountPopupLayout(
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

    record PublishPopupLayout(
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

    record ConfirmPopupLayout(
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

    record CompatibilityStatus(
        String minecraftLine,
        int minecraftColor,
        String pathmindLine,
        int pathmindColor,
        boolean fullyCompatible
    ) {
        int summaryColor() {
            return fullyCompatible ? UITheme.STATE_SUCCESS : UITheme.STATE_WARNING;
        }

        boolean isFullyCompatible() {
            return fullyCompatible;
        }
    }

    record GraphBounds(float minX, float minY, float maxX, float maxY) {
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

    record PreviewGraphModel(
        List<Node> nodes,
        List<NodeGraphData.ConnectionData> connections,
        Map<String, Node> nodeLookup,
        GraphBounds bounds,
        Identifier galleryThumbnailTexture
    ) {
    }

    private record AuthorSummary(
        String key,
        String displayName,
        String avatarUrl,
        int presetCount,
        int totalLikes,
        int totalDownloads,
        MarketplacePreset representativePreset
    ) {
    }

    private static final class AuthorAccumulator {
        private final String key;
        private final String displayName;
        private String avatarUrl;
        private final MarketplacePreset representativePreset;
        private int presetCount;
        private int totalLikes;
        private int totalDownloads;

        private AuthorAccumulator(String key, String displayName, String avatarUrl, MarketplacePreset representativePreset) {
            this.key = key;
            this.displayName = displayName;
            this.avatarUrl = avatarUrl;
            this.representativePreset = representativePreset;
        }
    }

    private enum SortMode {
        TRENDING(Text.translatable("pathmind.marketplace.sort.trending").getString(), Comparator
            .comparingInt(MarketplacePreset::getDownloadsCount).reversed()
            .thenComparing(Comparator.comparingInt(MarketplacePreset::getLikesCount).reversed())
            .thenComparing(Comparator.comparing((MarketplacePreset preset) -> fallbackStatic(preset.getUpdatedAt(), "")).reversed())),
        SAVED(Text.translatable("pathmind.marketplace.saved").getString(), Comparator.comparing((MarketplacePreset preset) -> fallbackStatic(preset.getName(), "").toLowerCase(Locale.ROOT))),
        NEWEST(Text.translatable("pathmind.marketplace.sort.newest").getString(), Comparator.comparing((MarketplacePreset preset) -> fallbackStatic(preset.getCreatedAt(), "")).reversed()),
        UPDATED(Text.translatable("pathmind.marketplace.sort.updated").getString(), Comparator.comparing((MarketplacePreset preset) -> fallbackStatic(preset.getUpdatedAt(), "")).reversed()),
        DOWNLOADS(Text.translatable("pathmind.marketplace.downloads").getString(), Comparator.comparingInt(MarketplacePreset::getDownloadsCount).reversed()),
        LIKES(Text.translatable("pathmind.marketplace.likes").getString(), Comparator.comparingInt(MarketplacePreset::getLikesCount).reversed()),
        NAME(Text.translatable("pathmind.marketplace.sort.name").getString(), Comparator.comparing((MarketplacePreset preset) -> fallbackStatic(preset.getName(), "").toLowerCase(Locale.ROOT))),
        AUTHOR(Text.translatable("pathmind.marketplace.sort.author").getString(), Comparator.comparing((MarketplacePreset preset) -> fallbackStatic(preset.getAuthorName(), "").toLowerCase(Locale.ROOT)));

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

        private MarketplaceService.ListingMode toListingMode() {
            return switch (this) {
                case TRENDING -> MarketplaceService.ListingMode.TRENDING;
                case UPDATED -> MarketplaceService.ListingMode.UPDATED;
                case DOWNLOADS -> MarketplaceService.ListingMode.DOWNLOADS;
                case LIKES -> MarketplaceService.ListingMode.LIKES;
                default -> MarketplaceService.ListingMode.NEWEST;
            };
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

    enum ConfirmAction {
        UPDATE,
        DELETE
    }
}
