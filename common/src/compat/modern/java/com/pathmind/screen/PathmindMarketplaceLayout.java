package com.pathmind.screen;

final class PathmindMarketplaceLayout {
    private PathmindMarketplaceLayout() {
    }

    static PathmindMarketplaceScreen.Layout screen(int screenWidth, int screenHeight, int accountButtonWidth) {
        int topBarY = PathmindMarketplaceScreen.OUTER_PADDING;
        int backButtonX = PathmindMarketplaceScreen.OUTER_PADDING;
        int backButtonY = topBarY + 2;

        int sectionX = PathmindMarketplaceScreen.OUTER_PADDING;
        int sectionY = topBarY + PathmindMarketplaceScreen.TOP_BAR_HEIGHT + PathmindMarketplaceScreen.SECTION_TOP_GAP;
        int sectionWidth = screenWidth - PathmindMarketplaceScreen.OUTER_PADDING * 2;
        int sectionHeight = screenHeight - sectionY - PathmindMarketplaceScreen.OUTER_PADDING;
        int bodyX = sectionX + PathmindMarketplaceScreen.SECTION_BODY_PADDING;
        int bodyWidth = sectionWidth - PathmindMarketplaceScreen.SECTION_BODY_PADDING * 2;

        int searchFieldX = bodyX;
        int searchFieldY = sectionY + 5;
        int sortButtonX = searchFieldX + PathmindMarketplaceScreen.SEARCH_FIELD_WIDTH + 8;
        int sortButtonY = searchFieldY;
        int myPresetsButtonX = bodyX + bodyWidth - PathmindMarketplaceScreen.MY_PRESETS_BUTTON_WIDTH;
        int myPresetsButtonY = searchFieldY;
        int resultRowY = searchFieldY + PathmindMarketplaceScreen.SORT_BUTTON_HEIGHT + 6;
        int refreshButtonX = myPresetsButtonX - PathmindMarketplaceScreen.REFRESH_BUTTON_SIZE - 8;
        int refreshButtonY = searchFieldY;
        int accountButtonX = screenWidth - PathmindMarketplaceScreen.OUTER_PADDING - accountButtonWidth;
        int accountButtonY = topBarY + 2;

        return new PathmindMarketplaceScreen.Layout(topBarY, backButtonX, backButtonY, sectionX, sectionY, sectionWidth, sectionHeight,
            bodyX, bodyWidth, refreshButtonX, refreshButtonY, accountButtonX, accountButtonY, searchFieldX, searchFieldY,
            sortButtonX, sortButtonY, myPresetsButtonX, myPresetsButtonY, resultRowY);
    }

    static PathmindMarketplaceScreen.PopupLayout presetPopup(int screenWidth, int screenHeight) {
        int width = Math.min(360, screenWidth - 40);
        int height = Math.min(330, screenHeight - 40);
        int x = (screenWidth - width) / 2;
        int y = (screenHeight - height) / 2;
        int buttonWidth = 64;
        int buttonHeight = 18;
        int buttonY = y + height - 28;
        int closeButtonX = x + width - buttonWidth * 4 - 28;
        int authButtonX = x + width - buttonWidth * 3 - 22;
        int deleteButtonX = x + width - buttonWidth * 2 - 16;
        int downloadButtonX = x + width - buttonWidth - 10;
        return new PathmindMarketplaceScreen.PopupLayout(x, y, width, height, closeButtonX, authButtonX, deleteButtonX, downloadButtonX, buttonY, buttonWidth, buttonHeight);
    }

    static PathmindMarketplaceScreen.ConfirmPopupLayout confirmPopup(int screenWidth, int screenHeight, boolean updateAction) {
        int width = Math.min(336, screenWidth - 40);
        int height = updateAction ? 236 : 164;
        int x = (screenWidth - width) / 2;
        int y = (screenHeight - height) / 2;
        int buttonWidth = 88;
        int buttonHeight = 18;
        int buttonY = y + height - 28;
        int cancelButtonX = x + 14;
        int confirmButtonX = x + width - buttonWidth - 14;
        return new PathmindMarketplaceScreen.ConfirmPopupLayout(x, y, width, height, cancelButtonX, confirmButtonX, buttonY, buttonWidth, buttonHeight);
    }

    static PathmindMarketplaceScreen.Rect updateConfirmSourceField(int popupX, int popupY, int popupWidth) {
        return new PathmindMarketplaceScreen.Rect(popupX + 20, popupY + 105, popupWidth - 40, 18);
    }

    static PathmindMarketplaceScreen.Rect updateConfirmSourceDropdown(int popupX, int popupY, int popupWidth, int optionCount) {
        PathmindMarketplaceScreen.Rect field = updateConfirmSourceField(popupX, popupY, popupWidth);
        int rows = Math.max(1, Math.min(6, optionCount));
        return new PathmindMarketplaceScreen.Rect(field.x(), field.y() + field.height() + 4, field.width(), rows * PathmindMarketplaceScreen.SORT_OPTION_HEIGHT);
    }

    static PathmindMarketplaceScreen.Rect popupPreview(int popupX, int popupY, int popupWidth, int scrollOffset) {
        int contentTop = popupY + 40;
        return new PathmindMarketplaceScreen.Rect(
            popupX + 12,
            contentTop - scrollOffset,
            popupWidth - 24,
            120
        );
    }

    static PathmindMarketplaceScreen.AccountPopupLayout accountPopup(int screenWidth, int screenHeight) {
        int width = Math.min(320, screenWidth - 40);
        int height = 180;
        int x = (screenWidth - width) / 2;
        int y = (screenHeight - height) / 2;
        int buttonWidth = 96;
        int buttonHeight = 18;
        int buttonY = y + height - 28;
        int closeButtonX = x + width - buttonWidth * 2 - 16;
        int signOutButtonX = x + width - buttonWidth - 10;
        return new PathmindMarketplaceScreen.AccountPopupLayout(x, y, width, height, closeButtonX, signOutButtonX, buttonY, buttonWidth, buttonHeight);
    }

    static PathmindMarketplaceScreen.PublishPopupLayout publishPopup(int screenWidth, int screenHeight) {
        int width = Math.min(392, screenWidth - 40);
        int height = Math.min(274, screenHeight - 40);
        int x = (screenWidth - width) / 2;
        int y = (screenHeight - height) / 2;
        int buttonWidth = 88;
        int buttonHeight = 18;
        int buttonY = y + height - 28;
        int cancelButtonX = x + width - buttonWidth * 3 - 22;
        int authButtonX = x + width - buttonWidth * 2 - 16;
        int submitButtonX = x + width - buttonWidth - 10;
        return new PathmindMarketplaceScreen.PublishPopupLayout(x, y, width, height, cancelButtonX, authButtonX, submitButtonX, buttonY, buttonWidth, buttonHeight);
    }

    static PathmindMarketplaceScreen.Rect card(PathmindMarketplaceScreen.Layout layout, int absoluteIndex, int sectionHeaderHeight, int scrollOffset) {
        int columns = gridColumns();
        int bodyY = layout.sectionY() + sectionHeaderHeight + 2;
        int availableWidth = layout.bodyWidth();
        int cardWidth = Math.min(PathmindMarketplaceScreen.CARD_MAX_WIDTH, (availableWidth - (columns - 1) * PathmindMarketplaceScreen.CARD_GAP) / columns);
        int column = absoluteIndex % columns;
        int row = absoluteIndex / columns;
        int startX = layout.bodyX();
        return new PathmindMarketplaceScreen.Rect(
            startX + column * (cardWidth + PathmindMarketplaceScreen.CARD_GAP),
            bodyY + row * (PathmindMarketplaceScreen.CARD_SIZE + PathmindMarketplaceScreen.CARD_GAP) - scrollOffset,
            cardWidth,
            PathmindMarketplaceScreen.CARD_SIZE
        );
    }

    static int gridColumns() {
        return PathmindMarketplaceScreen.PRESET_GRID_COLUMNS;
    }

    static int cardsPerPage() {
        return PathmindMarketplaceScreen.PRESET_GRID_COLUMNS * PathmindMarketplaceScreen.PRESET_GRID_ROWS;
    }

    static int firstVisibleCardIndex(int resultCount, int scrollOffset) {
        if (resultCount <= 0) {
            return 0;
        }
        int columns = Math.max(1, gridColumns());
        int firstRow = Math.max(0, (scrollOffset - PathmindMarketplaceScreen.CARD_SIZE) / (PathmindMarketplaceScreen.CARD_SIZE + PathmindMarketplaceScreen.CARD_GAP));
        int firstIndex = firstRow * columns;
        return Math.max(0, Math.min(firstIndex, resultCount - 1));
    }

    static int lastVisibleCardIndex(PathmindMarketplaceScreen.Layout layout, int resultCount, int sectionHeaderHeight, int scrollOffset) {
        if (resultCount <= 0) {
            return -1;
        }
        int columns = Math.max(1, gridColumns());
        int bodyHeight = layout.sectionHeight() - sectionHeaderHeight - PathmindMarketplaceScreen.FOOTER_HEIGHT;
        int lastRow = Math.max(0, (scrollOffset + bodyHeight + PathmindMarketplaceScreen.CARD_SIZE) / (PathmindMarketplaceScreen.CARD_SIZE + PathmindMarketplaceScreen.CARD_GAP));
        int lastIndex = (lastRow + 1) * columns - 1;
        return Math.max(0, Math.min(lastIndex, resultCount - 1));
    }

    static int authorEntriesPerPage(PathmindMarketplaceScreen.Layout layout, int sectionHeaderHeight) {
        int availableHeight = layout.sectionHeight() - sectionHeaderHeight - PathmindMarketplaceScreen.FOOTER_HEIGHT - 8;
        return Math.max(1, (availableHeight + PathmindMarketplaceScreen.AUTHOR_ROW_GAP) / (PathmindMarketplaceScreen.AUTHOR_ROW_HEIGHT + PathmindMarketplaceScreen.AUTHOR_ROW_GAP));
    }

    static PathmindMarketplaceScreen.Rect sortDropdown(PathmindMarketplaceScreen.Layout layout, int optionCount) {
        return new PathmindMarketplaceScreen.Rect(layout.sortButtonX(), layout.sortButtonY() + PathmindMarketplaceScreen.SORT_BUTTON_HEIGHT, PathmindMarketplaceScreen.SORT_BUTTON_WIDTH, optionCount * PathmindMarketplaceScreen.SORT_OPTION_HEIGHT);
    }

    static PathmindMarketplaceScreen.Rect authorRow(PathmindMarketplaceScreen.Layout layout, int pageOffset, int sectionHeaderHeight) {
        int bodyY = layout.sectionY() + sectionHeaderHeight + 2;
        return new PathmindMarketplaceScreen.Rect(
            layout.bodyX(),
            bodyY + pageOffset * (PathmindMarketplaceScreen.AUTHOR_ROW_HEIGHT + PathmindMarketplaceScreen.AUTHOR_ROW_GAP),
            layout.bodyWidth(),
            PathmindMarketplaceScreen.AUTHOR_ROW_HEIGHT
        );
    }

    static PathmindMarketplaceScreen.Rect exitProfile(PathmindMarketplaceScreen.Layout layout) {
        return new PathmindMarketplaceScreen.Rect(layout.bodyX(), layout.searchFieldY(), 92, PathmindMarketplaceScreen.SORT_BUTTON_HEIGHT);
    }

    static int sectionHeaderHeight(boolean viewingAuthorProfile, boolean myPresetsOnly) {
        if (viewingAuthorProfile) {
            return PathmindMarketplaceScreen.SECTION_HEADER_HEIGHT;
        }
        return myPresetsOnly
            ? PathmindMarketplaceScreen.SECTION_HEADER_HEIGHT + PathmindMarketplaceScreen.MY_PRESET_FILTER_BUTTON_HEIGHT + 8
            : PathmindMarketplaceScreen.SECTION_HEADER_HEIGHT;
    }
}
