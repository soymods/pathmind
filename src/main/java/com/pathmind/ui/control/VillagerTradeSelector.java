package com.pathmind.ui.control;

import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DrawContextBridge;
import com.pathmind.util.InputCompatibilityBridge;
import com.pathmind.util.DropdownLayoutHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.VillagerData;
import net.minecraft.village.VillagerProfession;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.lang.reflect.Method;

/**
 * Helper widget for selecting villager trades by profession with search filtering.
 */
public class VillagerTradeSelector {
    private static final Method VILLAGERDATA_WITH_PROFESSION = resolveWithProfessionMethod();
    private static final int DROPDOWN_HEIGHT = 20;
    private static final int SEARCH_HEIGHT = 20;
    private static final int LIST_ROW_HEIGHT = 18;
    private static final int LIST_VISIBLE_ROWS = 8;
    private static final int SECTION_SPACING = 8;
    private static final int TEXT_PADDING = 6;
    private static final int LIST_PADDING = 4;
    private static final int DROPDOWN_OPTION_HEIGHT = 18;
    private static final int DROPDOWN_MAX_VISIBLE = 8;
    private static final Map<String, List<TradeEntry>> TRADE_CACHE = new HashMap<>();

    public interface Listener {
        void onProfessionChanged(String professionId);
        void onTradeChanged(String tradeItemId);
        void requestLayoutRefresh();
    }

    private final Listener listener;
    private final List<ProfessionOption> professions = new ArrayList<>();
    private final List<TradeEntry> trades = new ArrayList<>();
    private final List<TradeEntry> filteredTrades = new ArrayList<>();

    private ProfessionOption selectedProfession;
    private String searchQuery = "";
    private String selectedTradeKey = "";
    private boolean searchFocused = false;
    private int searchCaretPosition = 0;
    private int searchSelectionStart = -1;
    private int searchSelectionEnd = -1;
    private int searchSelectionAnchor = -1;
    private long searchCaretBlinkLastToggle = 0L;
    private boolean searchCaretVisible = true;
    private TextRenderer lastTextRenderer;

    private int renderX;
    private int renderY;
    private int renderWidth;
    private int lastRenderHeight;

    private boolean dropdownOpen = false;
    private int dropdownScrollIndex = 0;
    private int dropdownHoverIndex = -1;

    private int listScrollIndex = 0;

    private int dropdownX;
    private int dropdownY;
    private int dropdownWidth;
    private int dropdownHeight;
    private int searchX;
    private int searchY;
    private int searchWidth;
    private int searchHeight;
    private int listX;
    private int listY;
    private int listWidth;
    private int listHeight;

    public VillagerTradeSelector(Listener listener) {
        this.listener = listener;
        loadProfessionOptions();
        if (!professions.isEmpty()) {
            selectedProfession = professions.get(0);
        }
        rebuildTrades();
    }

    public void setProfessionById(String professionId) {
        ProfessionOption match = null;
        if (professionId != null && !professionId.isEmpty()) {
            String normalized = normalizeId(professionId);
            for (ProfessionOption option : professions) {
                if (option.id.equals(normalized)) {
                    match = option;
                    break;
                }
            }
        }
        if (match == null && !professions.isEmpty()) {
            match = professions.get(0);
        }
        if (!Objects.equals(selectedProfession, match)) {
            selectedProfession = match;
            if (listener != null && selectedProfession != null) {
                listener.onProfessionChanged(selectedProfession.id);
            }
            rebuildTrades();
            if (listener != null) {
                listener.requestLayoutRefresh();
            }
        }
    }

    public String getProfessionId() {
        return selectedProfession != null ? selectedProfession.id : "";
    }

    public void setSelectedTradeKey(String tradeKey) {
        selectedTradeKey = tradeKey != null ? tradeKey : "";
        if (!selectedTradeKey.isEmpty() && !matchesAnyTradeKey(selectedTradeKey)) {
            String fallbackKey = findTradeKeyBySellItem(selectedTradeKey);
            selectedTradeKey = fallbackKey != null ? fallbackKey : "";
        }
        if (selectedTradeKey.isEmpty()) {
            selectFirstTrade();
        }
    }

    public String getSelectedTradeKey() {
        return selectedTradeKey;
    }

    public boolean hasFocusedInput() {
        return searchFocused;
    }

    public int render(DrawContext context, TextRenderer textRenderer, int x, int y, int width, int mouseX, int mouseY, float alpha) {
        this.lastTextRenderer = textRenderer;
        this.renderX = x;
        this.renderY = y;
        this.renderWidth = width;

        int sectionY = y;
        context.drawTextWithShadow(
            textRenderer,
            Text.literal("Profession:"),
            x,
            sectionY,
            applyAlpha(UITheme.TEXT_PRIMARY, alpha)
        );
        sectionY += textRenderer.fontHeight + 4;

        dropdownX = x;
        dropdownY = sectionY;
        dropdownWidth = width;
        dropdownHeight = DROPDOWN_HEIGHT;

        boolean hoverButton = mouseX >= dropdownX && mouseX <= dropdownX + dropdownWidth &&
                              mouseY >= dropdownY && mouseY <= dropdownY + dropdownHeight;
        int buttonBg = hoverButton ? UITheme.BUTTON_DEFAULT_BG : UITheme.BACKGROUND_SECONDARY;
        int borderColor = dropdownOpen ? UITheme.ACCENT_DEFAULT : (hoverButton ? UITheme.TEXT_SECONDARY : UITheme.BORDER_SUBTLE);
        context.fill(dropdownX, dropdownY, dropdownX + dropdownWidth, dropdownY + dropdownHeight, applyAlpha(buttonBg, alpha));
        DrawContextBridge.drawBorder(context, dropdownX, dropdownY, dropdownWidth, dropdownHeight, applyAlpha(borderColor, alpha));
        String professionLabel = selectedProfession != null ? selectedProfession.displayName : "None";
        context.drawTextWithShadow(
            textRenderer,
            Text.literal(professionLabel),
            dropdownX + TEXT_PADDING,
            dropdownY + 6,
            applyAlpha(UITheme.TEXT_PRIMARY, alpha)
        );
        context.drawTextWithShadow(
            textRenderer,
            Text.literal("▼"),
            dropdownX + dropdownWidth - 12,
            dropdownY + 6,
            applyAlpha(UITheme.TEXT_PRIMARY, alpha)
        );
        sectionY += DROPDOWN_HEIGHT + SECTION_SPACING;

        searchX = x;
        searchY = sectionY;
        searchWidth = width;
        searchHeight = SEARCH_HEIGHT;

        boolean isSearchFocused = searchFocused;
        int searchBg = isSearchFocused ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_SIDEBAR;
        int searchBorder = isSearchFocused ? UITheme.ACCENT_DEFAULT : UITheme.BORDER_SUBTLE;
        context.fill(searchX, searchY, searchX + searchWidth, searchY + searchHeight, applyAlpha(searchBg, alpha));
        DrawContextBridge.drawBorder(context, searchX, searchY, searchWidth, searchHeight, applyAlpha(searchBorder, alpha));
        String displayText = searchQuery;
        boolean showPlaceholder = displayText == null || displayText.isEmpty();
        int textY = searchY + (searchHeight - textRenderer.fontHeight) / 2 + 1;
        if (showPlaceholder) {
            context.drawTextWithShadow(
                textRenderer,
                Text.literal("Search trades..."),
                searchX + TEXT_PADDING,
                textY,
                applyAlpha(UITheme.TEXT_TERTIARY, alpha)
            );
        } else {
            String trimmed = searchFocused
                ? displayText
                : trimDisplayString(textRenderer, displayText, searchWidth - TEXT_PADDING * 2);
            if (searchFocused && hasSearchSelection()) {
                int start = MathHelper.clamp(searchSelectionStart, 0, displayText.length());
                int end = MathHelper.clamp(searchSelectionEnd, 0, displayText.length());
                if (start != end) {
                    int selStartX = searchX + TEXT_PADDING + textRenderer.getWidth(displayText.substring(0, Math.min(start, end)));
                    int selEndX = searchX + TEXT_PADDING + textRenderer.getWidth(displayText.substring(0, Math.max(start, end)));
                    selStartX = MathHelper.clamp(selStartX, searchX + 2, searchX + searchWidth - 2);
                    selEndX = MathHelper.clamp(selEndX, searchX + 2, searchX + searchWidth - 2);
                    context.fill(selStartX, searchY + 3, selEndX, searchY + searchHeight - 3,
                        applyAlpha(UITheme.TEXT_SELECTION_BG, alpha));
                }
            }
            context.drawTextWithShadow(
                textRenderer,
                Text.literal(trimmed),
                searchX + TEXT_PADDING,
                textY,
                applyAlpha(UITheme.TEXT_PRIMARY, alpha)
            );
        }

        if (searchFocused) {
            updateSearchCaretBlink();
            if (searchCaretVisible) {
                int caretIndex = MathHelper.clamp(searchCaretPosition, 0, displayText.length());
                int caretX = searchX + TEXT_PADDING + textRenderer.getWidth(displayText.substring(0, caretIndex));
                caretX = Math.min(caretX, searchX + searchWidth - 2);
                context.fill(caretX, searchY + 3, caretX + 1, searchY + searchHeight - 3, applyAlpha(UITheme.CARET_COLOR, alpha));
            }
        }
        sectionY += SEARCH_HEIGHT + SECTION_SPACING;

        listX = x;
        listY = sectionY;
        listWidth = width;
        listHeight = LIST_ROW_HEIGHT * LIST_VISIBLE_ROWS + LIST_PADDING * 2;

        context.fill(listX, listY, listX + listWidth, listY + listHeight, applyAlpha(UITheme.BACKGROUND_SIDEBAR, alpha));
        DrawContextBridge.drawBorder(context, listX, listY, listWidth, listHeight, applyAlpha(UITheme.BORDER_DEFAULT, alpha));

        int visibleRows = LIST_VISIBLE_ROWS;
        int maxScroll = Math.max(0, filteredTrades.size() - visibleRows);
        listScrollIndex = MathHelper.clamp(listScrollIndex, 0, maxScroll);

        int rowTop = listY + LIST_PADDING;
        for (int i = 0; i < visibleRows; i++) {
            int tradeIndex = listScrollIndex + i;
            if (tradeIndex >= filteredTrades.size()) {
                break;
            }
            TradeEntry entry = filteredTrades.get(tradeIndex);
            int rowY = rowTop + i * LIST_ROW_HEIGHT;
            boolean hovered = mouseX >= listX && mouseX <= listX + listWidth &&
                              mouseY >= rowY && mouseY <= rowY + LIST_ROW_HEIGHT;
            boolean selected = entry.tradeKey.equals(selectedTradeKey);
            int rowBg = selected ? UITheme.BACKGROUND_TERTIARY : UITheme.BACKGROUND_SIDEBAR;
            if (hovered) {
                rowBg = adjustColor(rowBg, 1.15f);
            }
            context.fill(listX + 1, rowY, listX + listWidth - 1, rowY + LIST_ROW_HEIGHT, applyAlpha(rowBg, alpha));
            String rowText = trimDisplayString(textRenderer, entry.displayText, listWidth - TEXT_PADDING * 2);
            context.drawTextWithShadow(
                textRenderer,
                Text.literal(rowText),
                listX + TEXT_PADDING,
                rowY + 5,
                applyAlpha(UITheme.TEXT_PRIMARY, alpha)
            );
        }

        if (filteredTrades.isEmpty()) {
            String emptyMessage = "No trades found. Open a villager trade screen to load trades.";
            if (selectedProfession != null && "open_gui".equals(selectedProfession.id)) {
                emptyMessage = "Open a villager trade screen to load trades.";
            }
            int maxTextWidth = Math.max(0, listWidth - TEXT_PADDING * 2);
            int emptyTextX = listX + TEXT_PADDING;
            int emptyTextY = listY + LIST_PADDING + 4;
            renderWrappedText(context, textRenderer, emptyMessage, emptyTextX, emptyTextY, maxTextWidth,
                applyAlpha(UITheme.TEXT_TERTIARY, alpha));
        }

        DropdownLayoutHelper.drawScrollBar(
            context,
            listX,
            listY,
            listWidth,
            listHeight,
            filteredTrades.size(),
            visibleRows,
            listScrollIndex,
            maxScroll,
            applyAlpha(UITheme.BORDER_DEFAULT, alpha),
            applyAlpha(UITheme.BORDER_HIGHLIGHT, alpha)
        );

        sectionY += listHeight + SECTION_SPACING;

        String selectedText = buildSelectedTradeText();
        context.drawTextWithShadow(
            textRenderer,
            Text.literal(selectedText),
            x,
            sectionY,
            applyAlpha(UITheme.TEXT_SECONDARY, alpha)
        );
        sectionY += textRenderer.fontHeight;

        if (dropdownOpen) {
            renderDropdown(context, textRenderer, mouseX, mouseY, alpha);
        }

        lastRenderHeight = sectionY - y;
        return lastRenderHeight;
    }

    public int getEstimatedHeight(int textHeight) {
        int header = textHeight + 4 + DROPDOWN_HEIGHT + SECTION_SPACING;
        int search = SEARCH_HEIGHT + SECTION_SPACING;
        int list = LIST_ROW_HEIGHT * LIST_VISIBLE_ROWS + LIST_PADDING * 2 + SECTION_SPACING;
        int footer = textHeight;
        return header + search + list + footer;
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        boolean buttonPressed = mouseX >= dropdownX && mouseX <= dropdownX + dropdownWidth &&
                                mouseY >= dropdownY && mouseY <= dropdownY + dropdownHeight;
        if (buttonPressed) {
            dropdownOpen = !dropdownOpen;
            if (dropdownOpen) {
                dropdownScrollIndex = 0;
            }
            return true;
        }

        if (dropdownOpen) {
            if (handleDropdownClick(mouseX, mouseY)) {
                return true;
            }
            dropdownOpen = false;
        }

        boolean searchPressed = mouseX >= searchX && mouseX <= searchX + searchWidth &&
                                mouseY >= searchY && mouseY <= searchY + searchHeight;
        if (searchPressed) {
            searchFocused = true;
            updateSearchCaretFromClick(mouseX);
            resetSearchCaretBlink();
            return true;
        }
        searchFocused = false;

        if (mouseX >= listX && mouseX <= listX + listWidth &&
            mouseY >= listY && mouseY <= listY + listHeight) {
            int relativeY = (int) (mouseY - (listY + LIST_PADDING));
            int rowIndex = relativeY / LIST_ROW_HEIGHT;
            int tradeIndex = listScrollIndex + rowIndex;
            if (rowIndex >= 0 && rowIndex < LIST_VISIBLE_ROWS && tradeIndex >= 0 && tradeIndex < filteredTrades.size()) {
                TradeEntry entry = filteredTrades.get(tradeIndex);
                selectedTradeKey = entry.tradeKey;
                if (listener != null) {
                    listener.onTradeChanged(selectedTradeKey);
                }
                return true;
            }
        }

        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (dropdownOpen) {
            if (handleDropdownScroll(mouseX, mouseY, amount)) {
                return true;
            }
        }
        boolean insideList = mouseX >= listX && mouseX <= listX + listWidth &&
                             mouseY >= listY && mouseY <= listY + listHeight;
        if (!insideList) {
            return false;
        }
        int visibleRows = LIST_VISIBLE_ROWS;
        int maxScroll = Math.max(0, filteredTrades.size() - visibleRows);
        if (maxScroll == 0) {
            return false;
        }
        listScrollIndex -= (int) Math.signum(amount);
        listScrollIndex = MathHelper.clamp(listScrollIndex, 0, maxScroll);
        return true;
    }

    public boolean keyPressed(int keyCode, int modifiers) {
        if (!searchFocused) {
            return false;
        }
        boolean shiftHeld = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean controlHeld = InputCompatibilityBridge.hasControlDown()
            || (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE:
                if (!deleteSearchSelection()) {
                    deleteCharBeforeCaret();
                }
                return true;
            case GLFW.GLFW_KEY_DELETE:
                if (!deleteSearchSelection()) {
                    deleteCharAfterCaret();
                }
                return true;
            case GLFW.GLFW_KEY_LEFT:
                moveSearchCaret(searchCaretPosition - 1, shiftHeld);
                return true;
            case GLFW.GLFW_KEY_RIGHT:
                moveSearchCaret(searchCaretPosition + 1, shiftHeld);
                return true;
            case GLFW.GLFW_KEY_HOME:
                moveSearchCaret(0, shiftHeld);
                return true;
            case GLFW.GLFW_KEY_END:
                moveSearchCaret(searchQuery.length(), shiftHeld);
                return true;
            case GLFW.GLFW_KEY_A:
                if (controlHeld) {
                    selectAllSearchText();
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_C:
                if (controlHeld) {
                    copySearchSelection();
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_X:
                if (controlHeld) {
                    copySearchSelection();
                    deleteSearchSelection();
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_V:
                if (controlHeld) {
                    String clip = getClipboardText();
                    if (clip != null && !clip.isEmpty()) {
                        insertSearchText(clip);
                    }
                    return true;
                }
                break;
            default:
                break;
        }
        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        if (!searchFocused) {
            return false;
        }
        if (chr >= 32 && chr != 127) {
            insertSearchText(String.valueOf(chr));
            return true;
        }
        return false;
    }

    public void closeDropdown() {
        dropdownOpen = false;
    }

    public int getLastRenderHeight() {
        return lastRenderHeight;
    }

    private void loadProfessionOptions() {
        professions.clear();
        List<ProfessionOption> options = new ArrayList<>();
        options.add(new ProfessionOption("open_gui", "Open Villager GUI", null, null));
        for (Identifier id : Registries.VILLAGER_PROFESSION.getIds()) {
            if (id == null) {
                continue;
            }
            String path = id.getPath();
            if ("none".equals(path)) {
                continue;
            }
            String display = titleCase(path);
            RegistryKey<VillagerProfession> key = RegistryKey.of(RegistryKeys.VILLAGER_PROFESSION, id);
            RegistryEntry<VillagerProfession> entry = Registries.VILLAGER_PROFESSION.getEntry(id).orElse(null);
            if (entry == null) {
                continue;
            }
            RegistryKey<VillagerProfession> resolvedKey = entry.getKey().orElse(key);
            options.add(new ProfessionOption(path, display, resolvedKey, entry));
        }
        options.sort(Comparator.comparing(option -> option.id, String.CASE_INSENSITIVE_ORDER));
        options.sort((a, b) -> {
            if ("open_gui".equals(a.id)) {
                return -1;
            }
            if ("open_gui".equals(b.id)) {
                return 1;
            }
            if ("librarian".equals(a.id)) {
                return -1;
            }
            if ("librarian".equals(b.id)) {
                return 1;
            }
            return 0;
        });
        professions.addAll(options);
        if (!professions.isEmpty()) {
            selectedProfession = professions.get(0);
        }
    }

    private void rebuildTrades() {
        trades.clear();
        filteredTrades.clear();
        listScrollIndex = 0;

        if (selectedProfession == null || selectedProfession.entry == null) {
            if (selectedProfession != null && "open_gui".equals(selectedProfession.id)) {
                if (loadTradesFromOpenMerchantScreen()) {
                    updateFilteredTrades();
                    if (!selectedTradeKey.isEmpty() && !matchesAnyTradeKey(selectedTradeKey)) {
                        String fallbackKey = findTradeKeyBySellItem(selectedTradeKey);
                        selectedTradeKey = fallbackKey != null ? fallbackKey : "";
                    }
                    if (selectedTradeKey.isEmpty()) {
                        selectFirstTrade();
                    }
                }
            }
            return;
        }

        Map<?, it.unimi.dsi.fastutil.ints.Int2ObjectMap<TradeOffers.Factory[]>> primary =
            TradeOffers.PROFESSION_TO_LEVELED_TRADE;
        Map<?, it.unimi.dsi.fastutil.ints.Int2ObjectMap<TradeOffers.Factory[]>> secondary =
            TradeOffers.REBALANCED_PROFESSION_TO_LEVELED_TRADE;
        it.unimi.dsi.fastutil.ints.Int2ObjectMap<TradeOffers.Factory[]> levelMap =
            resolveTradeLevels(primary, selectedProfession);
        if ((levelMap == null || levelMap.isEmpty()) && secondary != null && !secondary.isEmpty()) {
            levelMap = resolveTradeLevels(secondary, selectedProfession);
        }
        if (levelMap == null || levelMap.isEmpty()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        net.minecraft.server.world.ServerWorld serverWorld = client.getServer() != null
            ? client.getServer().getOverworld()
            : null;
        World fallbackWorld = client.world;
        World activeWorld = serverWorld != null ? serverWorld : fallbackWorld;
        if (activeWorld == null) {
            if (loadCachedTrades() || loadTradesFromOpenMerchantScreen()) {
                updateFilteredTrades();
                if (!selectedTradeKey.isEmpty() && !matchesAnyTradeKey(selectedTradeKey)) {
                    String fallbackKey = findTradeKeyBySellItem(selectedTradeKey);
                    selectedTradeKey = fallbackKey != null ? fallbackKey : "";
                }
                if (selectedTradeKey.isEmpty()) {
                    selectFirstTrade();
                }
            }
            return;
        }
        VillagerEntity villager = new VillagerEntity(EntityType.VILLAGER, activeWorld);
        if (villager == null) {
            return;
        }

        Random random = Random.create();
        for (it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry<TradeOffers.Factory[]> entry : levelMap.int2ObjectEntrySet()) {
            int level = entry.getIntKey();
            TradeOffers.Factory[] factories = entry.getValue();
            if (factories == null) {
                continue;
            }
            VillagerData data = applyProfession(villager.getVillagerData(), selectedProfession);
            if (data == null) {
                continue;
            }
            data = data.withLevel(level);
            villager.setVillagerData(data);

            boolean levelHasOffers = false;
            for (TradeOffers.Factory factory : factories) {
                if (factory == null) {
                    continue;
                }
                TradeOffer offer = createOffer(factory, serverWorld, fallbackWorld, villager, random);
                if (offer == null) {
                    continue;
                }
                ItemStack sell = offer.getSellItem();
                if (sell == null || sell.isEmpty()) {
                    continue;
                }
                ItemStack buyFirst = offer.getDisplayedFirstBuyItem();
                ItemStack buySecond = offer.getDisplayedSecondBuyItem();
                trades.add(TradeEntry.fromOffer(level, buyFirst, buySecond, sell));
                levelHasOffers = true;
            }

            if (!levelHasOffers && serverWorld != null) {
                TradeOfferList offers = villager.getOffers();
                if (offers != null) {
                    offers.clear();
                }
                invokeFillRecipes(villager, serverWorld);
                offers = villager.getOffers();
                if (offers != null) {
                    for (TradeOffer offer : offers) {
                        if (offer == null) {
                            continue;
                        }
                        ItemStack sell = offer.getSellItem();
                        if (sell == null || sell.isEmpty()) {
                            continue;
                        }
                        ItemStack buyFirst = offer.getDisplayedFirstBuyItem();
                        ItemStack buySecond = offer.getDisplayedSecondBuyItem();
                        trades.add(TradeEntry.fromOffer(level, buyFirst, buySecond, sell));
                    }
                }
            }
        }

        trades.sort(Comparator
            .comparingInt((TradeEntry entry) -> entry.level)
            .thenComparing(entry -> entry.displayText, String.CASE_INSENSITIVE_ORDER));

        if (trades.isEmpty()) {
            if (loadCachedTrades() || loadTradesFromOpenMerchantScreen()) {
                updateFilteredTrades();
                if (!selectedTradeKey.isEmpty() && !matchesAnyTradeKey(selectedTradeKey)) {
                    String fallbackKey = findTradeKeyBySellItem(selectedTradeKey);
                    selectedTradeKey = fallbackKey != null ? fallbackKey : "";
                }
                if (selectedTradeKey.isEmpty()) {
                    selectFirstTrade();
                }
                return;
            }
        }

        cacheTrades();
        updateFilteredTrades();
        if (!selectedTradeKey.isEmpty() && !matchesAnyTradeKey(selectedTradeKey)) {
            String fallbackKey = findTradeKeyBySellItem(selectedTradeKey);
            selectedTradeKey = fallbackKey != null ? fallbackKey : "";
        }
        if (selectedTradeKey.isEmpty()) {
            selectFirstTrade();
        }
    }

    private it.unimi.dsi.fastutil.ints.Int2ObjectMap<TradeOffers.Factory[]> resolveTradeLevels(
        Map<?, it.unimi.dsi.fastutil.ints.Int2ObjectMap<TradeOffers.Factory[]>> map,
        ProfessionOption option
    ) {
        if (map == null || map.isEmpty() || option == null) {
            return null;
        }
        it.unimi.dsi.fastutil.ints.Int2ObjectMap<TradeOffers.Factory[]> levelMap = map.get(option.key);
        if ((levelMap == null || levelMap.isEmpty()) && option.entry != null) {
            RegistryKey<VillagerProfession> resolvedKey = option.entry.getKey().orElse(null);
            if (resolvedKey != null) {
                levelMap = map.get(resolvedKey);
            }
            if ((levelMap == null || levelMap.isEmpty())) {
                VillagerProfession profession = option.entry.value();
                if (profession != null) {
                    levelMap = map.get(profession);
                }
            }
            if ((levelMap == null || levelMap.isEmpty())) {
                levelMap = map.get(option.entry);
            }
        }
        if (levelMap == null || levelMap.isEmpty()) {
            for (Map.Entry<?, it.unimi.dsi.fastutil.ints.Int2ObjectMap<TradeOffers.Factory[]>> entry : map.entrySet()) {
                Object key = entry.getKey();
                String keyPath = null;
                if (key instanceof RegistryKey<?> registryKey) {
                    Identifier id = registryKey.getValue();
                    keyPath = id != null ? id.getPath() : null;
                } else if (key instanceof RegistryEntry<?> registryEntry) {
                    RegistryKey<?> registryKey = registryEntry.getKey().orElse(null);
                    if (registryKey != null) {
                        Identifier id = registryKey.getValue();
                        keyPath = id != null ? id.getPath() : null;
                    } else {
                        Object value = registryEntry.value();
                        if (value instanceof VillagerProfession profession) {
                            Identifier id = Registries.VILLAGER_PROFESSION.getId(profession);
                            keyPath = id != null ? id.getPath() : null;
                        }
                    }
                } else if (key instanceof VillagerProfession profession) {
                    Identifier id = Registries.VILLAGER_PROFESSION.getId(profession);
                    keyPath = id != null ? id.getPath() : null;
                } else if (key instanceof Identifier id) {
                    keyPath = id.getPath();
                }
                if (keyPath != null && keyPath.equals(option.id)) {
                    return entry.getValue();
                }
            }
        }
        return levelMap;
    }

    private VillagerProfession resolveProfession(ProfessionOption option) {
        if (option == null) {
            return null;
        }
        if (option.entry != null) {
            return option.entry.value();
        }
        if (option.key != null) {
            return Registries.VILLAGER_PROFESSION.get(option.key);
        }
        return null;
    }

    private static Method resolveWithProfessionMethod() {
        try {
            Method method = VillagerData.class.getMethod("withProfession", VillagerProfession.class);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
        }
        try {
            Method method = VillagerData.class.getMethod("withProfession", RegistryEntry.class);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private VillagerData applyProfession(VillagerData data, ProfessionOption option) {
        if (data == null || option == null) {
            return data;
        }
        if (VILLAGERDATA_WITH_PROFESSION == null) {
            return data;
        }
        Object argument = null;
        Class<?> paramType = VILLAGERDATA_WITH_PROFESSION.getParameterTypes()[0];
        if (paramType.isAssignableFrom(VillagerProfession.class)) {
            argument = resolveProfession(option);
        } else if (RegistryEntry.class.isAssignableFrom(paramType)) {
            argument = option.entry;
        }
        if (argument == null) {
            return data;
        }
        try {
            Object result = VILLAGERDATA_WITH_PROFESSION.invoke(data, argument);
            return result instanceof VillagerData villagerData ? villagerData : data;
        } catch (IllegalAccessException | InvocationTargetException ignored) {
            return data;
        }
    }

    private void updateSearchCaretBlink() {
        long now = System.currentTimeMillis();
        if (now - searchCaretBlinkLastToggle >= 500) {
            searchCaretVisible = !searchCaretVisible;
            searchCaretBlinkLastToggle = now;
        }
    }

    private void resetSearchCaretBlink() {
        searchCaretVisible = true;
        searchCaretBlinkLastToggle = System.currentTimeMillis();
    }

    private void updateSearchCaretFromClick(double mouseX) {
        if (lastTextRenderer == null) {
            searchCaretPosition = searchQuery.length();
            return;
        }
        int textX = searchX + TEXT_PADDING;
        int relativeX = (int) mouseX - textX;
        String value = searchQuery != null ? searchQuery : "";
        if (relativeX <= 0) {
            searchCaretPosition = 0;
            clearSearchSelection();
            return;
        }
        int totalWidth = lastTextRenderer.getWidth(value);
        if (relativeX >= totalWidth) {
            searchCaretPosition = value.length();
            clearSearchSelection();
            return;
        }
        int bestIndex = 0;
        int bestDiff = Integer.MAX_VALUE;
        for (int i = 1; i <= value.length(); i++) {
            int width = lastTextRenderer.getWidth(value.substring(0, i));
            int diff = Math.abs(relativeX - width);
            if (diff < bestDiff) {
                bestDiff = diff;
                bestIndex = i;
            }
        }
        searchCaretPosition = bestIndex;
        clearSearchSelection();
    }

    private void moveSearchCaret(int newPosition, boolean shiftHeld) {
        int clamped = MathHelper.clamp(newPosition, 0, searchQuery.length());
        if (shiftHeld) {
            if (!hasSearchSelection()) {
                searchSelectionAnchor = searchCaretPosition;
            }
            searchSelectionStart = Math.min(searchSelectionAnchor, clamped);
            searchSelectionEnd = Math.max(searchSelectionAnchor, clamped);
        } else {
            clearSearchSelection();
        }
        searchCaretPosition = clamped;
        resetSearchCaretBlink();
    }

    private void selectAllSearchText() {
        searchSelectionStart = 0;
        searchSelectionEnd = searchQuery.length();
        searchSelectionAnchor = 0;
        searchCaretPosition = searchQuery.length();
        resetSearchCaretBlink();
    }

    private boolean hasSearchSelection() {
        return searchSelectionStart >= 0 && searchSelectionEnd >= 0 && searchSelectionStart != searchSelectionEnd;
    }

    private void clearSearchSelection() {
        searchSelectionStart = -1;
        searchSelectionEnd = -1;
        searchSelectionAnchor = -1;
    }

    private boolean deleteSearchSelection() {
        if (!hasSearchSelection()) {
            return false;
        }
        int start = Math.min(searchSelectionStart, searchSelectionEnd);
        int end = Math.max(searchSelectionStart, searchSelectionEnd);
        String value = searchQuery != null ? searchQuery : "";
        setSearchQuery(value.substring(0, start) + value.substring(end));
        searchCaretPosition = start;
        clearSearchSelection();
        return true;
    }

    private void copySearchSelection() {
        if (!hasSearchSelection()) {
            return;
        }
        int start = Math.min(searchSelectionStart, searchSelectionEnd);
        int end = Math.max(searchSelectionStart, searchSelectionEnd);
        String value = searchQuery != null ? searchQuery : "";
        if (start >= 0 && end <= value.length()) {
            setClipboardText(value.substring(start, end));
        }
    }

    private void deleteCharBeforeCaret() {
        if (searchCaretPosition <= 0) {
            return;
        }
        String value = searchQuery != null ? searchQuery : "";
        int caret = MathHelper.clamp(searchCaretPosition, 0, value.length());
        setSearchQuery(value.substring(0, caret - 1) + value.substring(caret));
        searchCaretPosition = caret - 1;
    }

    private void deleteCharAfterCaret() {
        String value = searchQuery != null ? searchQuery : "";
        int caret = MathHelper.clamp(searchCaretPosition, 0, value.length());
        if (caret >= value.length()) {
            return;
        }
        setSearchQuery(value.substring(0, caret) + value.substring(caret + 1));
    }

    private void insertSearchText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (deleteSearchSelection()) {
            // selection cleared in deleteSearchSelection
        }
        String value = searchQuery != null ? searchQuery : "";
        int caret = MathHelper.clamp(searchCaretPosition, 0, value.length());
        setSearchQuery(value.substring(0, caret) + text + value.substring(caret));
        searchCaretPosition = caret + text.length();
    }

    private void setSearchQuery(String value) {
        searchQuery = value != null ? value : "";
        searchCaretPosition = MathHelper.clamp(searchCaretPosition, 0, searchQuery.length());
        updateFilteredTrades();
    }

    private String getClipboardText() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.keyboard != null) {
            return client.keyboard.getClipboard();
        }
        return "";
    }

    private void setClipboardText(String text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.keyboard != null) {
            client.keyboard.setClipboard(text == null ? "" : text);
        }
    }

    private void cacheTrades() {
        if (selectedProfession == null || trades.isEmpty()) {
            return;
        }
        TRADE_CACHE.put(selectedProfession.id, new ArrayList<>(trades));
    }

    private boolean loadCachedTrades() {
        if (selectedProfession == null) {
            return false;
        }
        List<TradeEntry> cached = TRADE_CACHE.get(selectedProfession.id);
        if (cached == null || cached.isEmpty()) {
            return false;
        }
        trades.clear();
        trades.addAll(cached);
        return true;
    }

    private boolean loadTradesFromOpenMerchantScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return false;
        }
        if (!(client.currentScreen instanceof net.minecraft.client.gui.screen.ingame.MerchantScreen merchantScreen)) {
            return false;
        }
        net.minecraft.screen.MerchantScreenHandler screenHandler = merchantScreen.getScreenHandler();
        if (screenHandler == null) {
            return false;
        }
        net.minecraft.village.TradeOfferList offers = screenHandler.getRecipes();
        if (offers == null || offers.isEmpty()) {
            return false;
        }
        trades.clear();
        for (net.minecraft.village.TradeOffer offer : offers) {
            if (offer == null) {
                continue;
            }
            ItemStack sell = offer.getSellItem();
            if (sell == null || sell.isEmpty()) {
                continue;
            }
            ItemStack buyFirst = offer.getDisplayedFirstBuyItem();
            ItemStack buySecond = offer.getDisplayedSecondBuyItem();
            trades.add(TradeEntry.fromOffer(1, buyFirst, buySecond, sell));
        }
        if (trades.isEmpty()) {
            return false;
        }
        cacheTrades();
        return true;
    }

    private TradeOffer createOffer(TradeOffers.Factory factory, net.minecraft.server.world.ServerWorld serverWorld,
                                   World fallbackWorld, VillagerEntity villager, Random random) {
        if (factory == null || villager == null) {
            return null;
        }
        try {
            for (Method method : factory.getClass().getMethods()) {
                if (!"create".equals(method.getName())) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 3) {
                    if (serverWorld != null && params[0].isInstance(serverWorld) && params[1].isInstance(villager)) {
                        Object offer = method.invoke(factory, serverWorld, villager, random);
                        if (offer instanceof TradeOffer tradeOffer) {
                            return tradeOffer;
                        }
                    }
                    if (fallbackWorld != null && params[0].isInstance(fallbackWorld) && params[1].isInstance(villager)) {
                        Object offer = method.invoke(factory, fallbackWorld, villager, random);
                        if (offer instanceof TradeOffer tradeOffer) {
                            return tradeOffer;
                        }
                    }
                } else if (params.length == 2 && params[0].isInstance(villager)) {
                    Object offer = method.invoke(factory, villager, random);
                    if (offer instanceof TradeOffer tradeOffer) {
                        return tradeOffer;
                    }
                }
            }
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
        return null;
    }

    private void invokeFillRecipes(VillagerEntity villager, net.minecraft.server.world.ServerWorld serverWorld) {
        if (villager == null || serverWorld == null) {
            return;
        }
        try {
            Method method = VillagerEntity.class.getDeclaredMethod("fillRecipes", net.minecraft.server.world.ServerWorld.class);
            method.setAccessible(true);
            method.invoke(villager, serverWorld);
        } catch (ReflectiveOperationException ignored) {
            // ignore if inaccessible in this version
        }
    }

    private void updateFilteredTrades() {
        filteredTrades.clear();
        String query = searchQuery.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            filteredTrades.addAll(trades);
        } else {
            for (TradeEntry entry : trades) {
                if (entry.searchText.contains(query)) {
                    filteredTrades.add(entry);
                }
            }
        }
        listScrollIndex = 0;
    }

    private boolean matchesAnyTradeKey(String tradeKey) {
        if (tradeKey == null || tradeKey.isEmpty()) {
            return false;
        }
        for (TradeEntry entry : trades) {
            if (entry.tradeKey.equals(tradeKey)) {
                return true;
            }
        }
        return false;
    }

    private void selectFirstTrade() {
        if (!trades.isEmpty()) {
            selectedTradeKey = trades.get(0).tradeKey;
            if (listener != null) {
                listener.onTradeChanged(selectedTradeKey);
            }
        }
    }

    private String findTradeKeyBySellItem(String sellItemId) {
        if (sellItemId == null || sellItemId.isEmpty()) {
            return null;
        }
        for (TradeEntry entry : trades) {
            if (entry.sellItemId.equals(sellItemId)) {
                return entry.tradeKey;
            }
        }
        return null;
    }

    private void renderDropdown(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float alpha) {
        int dropdownX = this.dropdownX;
        int dropdownY = this.dropdownY + dropdownHeight;
        int dropdownWidth = this.dropdownWidth;
        int totalOptions = professions.size();
        int screenHeight = MinecraftClient.getInstance().getWindow().getScaledHeight();
        DropdownLayoutHelper.Layout layout = DropdownLayoutHelper.calculate(
            totalOptions,
            DROPDOWN_OPTION_HEIGHT,
            DROPDOWN_MAX_VISIBLE,
            dropdownY,
            screenHeight
        );
        int visibleCount = layout.visibleCount;
        dropdownScrollIndex = Math.max(0, Math.min(dropdownScrollIndex, layout.maxScrollOffset));
        int dropdownHeight = layout.height;

        context.fill(dropdownX, dropdownY, dropdownX + dropdownWidth, dropdownY + dropdownHeight, applyAlpha(UITheme.BACKGROUND_SIDEBAR, alpha));
        DrawContextBridge.drawBorder(context, dropdownX, dropdownY, dropdownWidth, dropdownHeight, applyAlpha(UITheme.BORDER_DEFAULT, alpha));
        context.drawHorizontalLine(dropdownX, dropdownX + dropdownWidth, dropdownY + dropdownHeight, applyAlpha(UITheme.BORDER_DEFAULT, alpha));

        dropdownHoverIndex = -1;
        for (int i = 0; i < visibleCount; i++) {
            int optionIndex = dropdownScrollIndex + i;
            ProfessionOption option = professions.get(optionIndex);
            int optionTop = dropdownY + i * DROPDOWN_OPTION_HEIGHT;
            boolean hovered = mouseX >= dropdownX && mouseX <= dropdownX + dropdownWidth &&
                              mouseY >= optionTop && mouseY <= optionTop + DROPDOWN_OPTION_HEIGHT;
            if (hovered) {
                dropdownHoverIndex = optionIndex;
            }
            int bg = option == selectedProfession ? UITheme.BACKGROUND_TERTIARY : UITheme.BACKGROUND_SIDEBAR;
            if (hovered) {
                bg = adjustColor(bg, 1.2f);
            }
            context.fill(dropdownX + 1, optionTop, dropdownX + dropdownWidth - 1, optionTop + DROPDOWN_OPTION_HEIGHT, applyAlpha(bg, alpha));
            context.drawTextWithShadow(
                textRenderer,
                Text.literal(option.displayName),
                dropdownX + TEXT_PADDING,
                optionTop + 5,
                applyAlpha(UITheme.TEXT_PRIMARY, alpha)
            );
        }

        DropdownLayoutHelper.drawScrollBar(
            context,
            dropdownX,
            dropdownY,
            dropdownWidth,
            dropdownHeight,
            totalOptions,
            layout.visibleCount,
            dropdownScrollIndex,
            layout.maxScrollOffset,
            applyAlpha(UITheme.BORDER_DEFAULT, alpha),
            applyAlpha(UITheme.BORDER_HIGHLIGHT, alpha)
        );
        DropdownLayoutHelper.drawOutline(
            context,
            dropdownX,
            dropdownY,
            dropdownWidth,
            dropdownHeight,
            applyAlpha(UITheme.BORDER_DEFAULT, alpha)
        );
    }

    private int applyAlpha(int color, float alpha) {
        int baseAlpha = (color >>> 24) & 0xFF;
        int applied = Math.round(baseAlpha * MathHelper.clamp(alpha, 0f, 1f));
        return (applied << 24) | (color & 0x00FFFFFF);
    }

    private boolean handleDropdownClick(double mouseX, double mouseY) {
        int dropdownX = this.dropdownX;
        int dropdownY = this.dropdownY + dropdownHeight;
        int dropdownWidth = this.dropdownWidth;
        int totalOptions = professions.size();
        int screenHeight = MinecraftClient.getInstance().getWindow().getScaledHeight();
        DropdownLayoutHelper.Layout layout = DropdownLayoutHelper.calculate(
            totalOptions,
            DROPDOWN_OPTION_HEIGHT,
            DROPDOWN_MAX_VISIBLE,
            dropdownY,
            screenHeight
        );
        int visibleCount = layout.visibleCount;
        int dropdownHeight = layout.height;

        boolean inside = mouseX >= dropdownX && mouseX <= dropdownX + dropdownWidth &&
                         mouseY >= dropdownY && mouseY <= dropdownY + dropdownHeight;
        if (!inside) {
            dropdownOpen = false;
            return false;
        }

        int optionIndex = (int) ((mouseY - dropdownY) / DROPDOWN_OPTION_HEIGHT);
        int actualIndex = dropdownScrollIndex + optionIndex;
        if (actualIndex >= 0 && actualIndex < professions.size()) {
            ProfessionOption selected = professions.get(actualIndex);
            if (selected != selectedProfession) {
                selectedProfession = selected;
                if (listener != null) {
                    listener.onProfessionChanged(selected.id);
                }
                rebuildTrades();
                if (listener != null) {
                    listener.requestLayoutRefresh();
                }
            }
        }
        dropdownOpen = false;
        return true;
    }

    private boolean handleDropdownScroll(double mouseX, double mouseY, double amount) {
        int dropdownX = this.dropdownX;
        int dropdownY = this.dropdownY + dropdownHeight;
        int dropdownWidth = this.dropdownWidth;
        int totalOptions = professions.size();
        int screenHeight = MinecraftClient.getInstance().getWindow().getScaledHeight();
        DropdownLayoutHelper.Layout layout = DropdownLayoutHelper.calculate(
            totalOptions,
            DROPDOWN_OPTION_HEIGHT,
            DROPDOWN_MAX_VISIBLE,
            dropdownY,
            screenHeight
        );
        int dropdownHeight = layout.height;
        boolean inside = mouseX >= dropdownX && mouseX <= dropdownX + dropdownWidth &&
                         mouseY >= dropdownY && mouseY <= dropdownY + dropdownHeight;
        if (!inside) {
            return false;
        }
        int maxIndex = layout.maxScrollOffset;
        if (maxIndex == 0) {
            return false;
        }
        dropdownScrollIndex -= (int) Math.signum(amount);
        dropdownScrollIndex = Math.max(0, Math.min(maxIndex, dropdownScrollIndex));
        return true;
    }

    private String buildSelectedTradeText() {
        if (selectedTradeKey == null || selectedTradeKey.isEmpty()) {
            return "Selected: none";
        }
        for (TradeEntry entry : trades) {
            if (entry.tradeKey.equals(selectedTradeKey)) {
                return "Selected: " + entry.sellDisplayName;
            }
        }
        return "Selected: " + selectedTradeKey;
    }

    private String trimDisplayString(TextRenderer renderer, String text, int availableWidth) {
        if (text == null) {
            return "";
        }
        if (renderer.getWidth(text) <= availableWidth) {
            return text;
        }
        int ellipsisWidth = renderer.getWidth("...");
        int trimmedWidth = Math.max(0, availableWidth - ellipsisWidth);
        String trimmed = renderer.trimToWidth(text, trimmedWidth);
        return trimmed + "...";
    }

    private void renderWrappedText(DrawContext context, TextRenderer textRenderer, String message,
                                   int x, int y, int maxWidth, int color) {
        if (message == null || message.isEmpty() || maxWidth <= 0) {
            return;
        }
        List<net.minecraft.text.OrderedText> lines = textRenderer.wrapLines(Text.literal(message), maxWidth);
        if (lines == null || lines.isEmpty()) {
            return;
        }
        int lineY = y;
        for (int i = 0; i < lines.size(); i++) {
            context.drawTextWithShadow(textRenderer, lines.get(i), x, lineY, color);
            lineY += textRenderer.fontHeight + 2;
        }
    }

    private String normalizeId(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        if (trimmed.contains(":")) {
            Identifier id = Identifier.tryParse(trimmed);
            if (id != null) {
                return id.getPath();
            }
        }
        return trimmed;
    }

    private String titleCase(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String[] parts = value.split("_");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    private int adjustColor(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        r = Math.min(255, (int) (r * factor));
        g = Math.min(255, (int) (g * factor));
        b = Math.min(255, (int) (b * factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static final class ProfessionOption {
        private final String id;
        private final String displayName;
        private final RegistryKey<VillagerProfession> key;
        private final RegistryEntry<VillagerProfession> entry;

        private ProfessionOption(String id, String displayName, RegistryKey<VillagerProfession> key,
                                 RegistryEntry<VillagerProfession> entry) {
            this.id = id;
            this.displayName = displayName;
            this.key = key;
            this.entry = entry;
        }
    }

    private static final class TradeEntry {
        private final int level;
        private final String displayText;
        private final String searchText;
        private final String tradeKey;
        private final String sellItemId;
        private final String sellDisplayName;

        private TradeEntry(int level, String displayText, String searchText, String tradeKey,
                           String sellItemId, String sellDisplayName) {
            this.level = level;
            this.displayText = displayText;
            this.searchText = searchText;
            this.tradeKey = tradeKey;
            this.sellItemId = sellItemId;
            this.sellDisplayName = sellDisplayName;
        }

        private static TradeEntry fromOffer(int level, ItemStack firstBuy, ItemStack secondBuy, ItemStack sell) {
            String firstText = formatStack(firstBuy);
            String secondText = formatStack(secondBuy);
            String sellText = formatStack(sell);
            String display = "Lvl " + level + ": " + firstText;
            if (!secondText.isEmpty()) {
                display += " + " + secondText;
            }
            display += " -> " + sellText;
            String search = buildSearch(firstBuy) + " " + buildSearch(secondBuy) + " " + buildSearch(sell);
            String sellId = getItemId(sell);
            String sellName = sell != null ? sell.getName().getString() : sellId;
            String tradeKey = buildTradeKey(firstBuy, secondBuy, sell);
            return new TradeEntry(level, display, search.toLowerCase(Locale.ROOT), tradeKey, sellId, sellName);
        }

        private static String formatStack(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return "";
            }
            String name = stack.getName().getString();
            int count = stack.getCount();
            if (count > 1) {
                return count + "x " + name;
            }
            return name;
        }

        private static String buildSearch(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return "";
            }
            StringBuilder builder = new StringBuilder();
            builder.append(stack.getName().getString());
            String id = getItemId(stack);
            if (!id.isEmpty()) {
                builder.append(' ').append(id);
            }
            return builder.toString();
        }

        private static String getItemId(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return "";
            }
            Identifier id = Registries.ITEM.getId(stack.getItem());
            return id != null ? id.toString() : "";
        }

        private static String buildTradeKey(ItemStack firstBuy, ItemStack secondBuy, ItemStack sell) {
            String first = buildKeyPart(firstBuy);
            String second = buildKeyPart(secondBuy);
            String sellPart = buildKeyPart(sell);
            return first + "|" + second + "|" + sellPart;
        }

        private static String buildKeyPart(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return "none@0";
            }
            Identifier id = Registries.ITEM.getId(stack.getItem());
            String itemId = id != null ? id.toString() : "unknown";
            return itemId + "@" + stack.getCount();
        }
    }
}
