package com.pathmind.ui.control;

/**
 * Shared popup layout math for render and input paths.
 */
public final class PathmindPopupLayout {
    private static final int PUBLISH_FIELD_MARGIN_X = 20;
    private static final int PUBLISH_FIELD_WIDTH_INSET = 40;
    private static final int PUBLISH_FIELD_HEIGHT = 16;
    private static final int PUBLISH_BUTTON_BOTTOM_MARGIN = 16;
    private static final int PUBLISH_NAME_OFFSET_Y = 44;
    private static final int PUBLISH_DESCRIPTION_OFFSET_Y = 82;
    private static final int PUBLISH_TAGS_OFFSET_Y = 120;
    private static final int PUBLISH_VISIBILITY_OFFSET_Y = 158;

    private PathmindPopupLayout() {
    }

    public static Rect rect(int x, int y, int width, int height) {
        return new Rect(x, y, width, height);
    }

    public static Rect leftButton(int popupX, int contentY, int preferredHeight,
                                  int buttonWidth, int buttonHeight, int bottomMargin) {
        return rect(popupX + PUBLISH_FIELD_MARGIN_X, buttonY(contentY, preferredHeight, buttonHeight, bottomMargin),
            buttonWidth, buttonHeight);
    }

    public static Rect rightButton(int popupX, int popupWidth, int contentY, int preferredHeight,
                                   int buttonWidth, int buttonHeight, int bottomMargin) {
        return rect(popupX + popupWidth - buttonWidth - PUBLISH_FIELD_MARGIN_X,
            buttonY(contentY, preferredHeight, buttonHeight, bottomMargin), buttonWidth, buttonHeight);
    }

    public static Rect centeredButton(int popupX, int popupWidth, int contentY, int preferredHeight,
                                      int buttonWidth, int buttonHeight, int bottomMargin) {
        return rect(popupX + (popupWidth - buttonWidth) / 2,
            buttonY(contentY, preferredHeight, buttonHeight, bottomMargin), buttonWidth, buttonHeight);
    }

    public static ButtonRow twoButtonRow(int popupX, int popupWidth, int contentY, int preferredHeight,
                                         int buttonWidth, int buttonHeight, int bottomMargin) {
        return new ButtonRow(
            leftButton(popupX, contentY, preferredHeight, buttonWidth, buttonHeight, bottomMargin),
            rightButton(popupX, popupWidth, contentY, preferredHeight, buttonWidth, buttonHeight, bottomMargin)
        );
    }

    public static ThreeButtonRow threeButtonRow(int popupX, int popupWidth, int contentY, int preferredHeight,
                                                int buttonWidth, int buttonHeight, int buttonGap, int bottomMargin) {
        int buttonY = buttonY(contentY, preferredHeight, buttonHeight, bottomMargin);
        int totalButtonsWidth = buttonWidth * 3 + buttonGap * 2;
        int startX = popupX + (popupWidth - totalButtonsWidth) / 2;
        Rect first = rect(startX, buttonY, buttonWidth, buttonHeight);
        Rect second = rect(startX + buttonWidth + buttonGap, buttonY, buttonWidth, buttonHeight);
        Rect third = rect(startX + (buttonWidth + buttonGap) * 2, buttonY, buttonWidth, buttonHeight);
        return new ThreeButtonRow(first, second, third);
    }

    public static ThreeButtonRow leftPairRightButtonRow(int popupX, int popupWidth, int contentY, int preferredHeight,
                                                        int buttonWidth, int buttonHeight, int buttonGap,
                                                        int bottomMargin) {
        int buttonY = buttonY(contentY, preferredHeight, buttonHeight, bottomMargin);
        Rect first = rect(popupX + PUBLISH_FIELD_MARGIN_X, buttonY, buttonWidth, buttonHeight);
        Rect second = rect(first.x() + buttonWidth + buttonGap, buttonY, buttonWidth, buttonHeight);
        Rect third = rect(popupX + popupWidth - buttonWidth - PUBLISH_FIELD_MARGIN_X, buttonY, buttonWidth, buttonHeight);
        return new ThreeButtonRow(first, second, third);
    }

    public static PublishPresetLayout publishPreset(int popupX, int popupY, int popupWidth, int popupHeight,
                                                    int contentY, int preferredHeight,
                                                    int buttonWidth, int buttonHeight,
                                                    int visibilityToggleWidth, int visibilityToggleHeight) {
        int fieldX = popupX + PUBLISH_FIELD_MARGIN_X;
        int fieldWidth = popupWidth - PUBLISH_FIELD_WIDTH_INSET;
        Rect popup = rect(popupX, popupY, popupWidth, popupHeight);
        Rect nameField = rect(fieldX, contentY + PUBLISH_NAME_OFFSET_Y, fieldWidth, PUBLISH_FIELD_HEIGHT);
        Rect descriptionField = rect(fieldX, contentY + PUBLISH_DESCRIPTION_OFFSET_Y, fieldWidth, PUBLISH_FIELD_HEIGHT);
        Rect tagsField = rect(fieldX, contentY + PUBLISH_TAGS_OFFSET_Y, fieldWidth, PUBLISH_FIELD_HEIGHT);
        Rect visibilityRow = rect(fieldX, contentY + PUBLISH_VISIBILITY_OFFSET_Y, fieldWidth, PUBLISH_FIELD_HEIGHT);
        Rect visibilityToggle = rect(
            fieldX + fieldWidth - visibilityToggleWidth,
            visibilityRow.y(),
            visibilityToggleWidth,
            visibilityToggleHeight
        );
        int buttonY = contentY + preferredHeight - buttonHeight - PUBLISH_BUTTON_BOTTOM_MARGIN;
        Rect cancelButton = rect(popupX + PUBLISH_FIELD_MARGIN_X, buttonY, buttonWidth, buttonHeight);
        Rect publishButton = rect(popupX + popupWidth - buttonWidth - PUBLISH_FIELD_MARGIN_X, buttonY, buttonWidth, buttonHeight);
        Rect signInButton = rect(popupX + (popupWidth - buttonWidth) / 2, buttonY, buttonWidth, buttonHeight);
        return new PublishPresetLayout(
            popup,
            fieldX,
            fieldWidth,
            nameField,
            descriptionField,
            tagsField,
            visibilityRow,
            visibilityToggle,
            cancelButton,
            publishButton,
            signInButton
        );
    }

    public static Rect validationInput(int panelX, int panelWidth, int rowY,
                                       int panelPadding, int rowHeight,
                                       int fieldWidth, int fieldHeight) {
        return rect(
            panelX + panelWidth - fieldWidth - panelPadding,
            rowY + (rowHeight - fieldHeight) / 2,
            fieldWidth,
            fieldHeight
        );
    }

    public static Rect validationToggle(int panelX, int panelWidth, int rowY,
                                        int panelPadding, int rowHeight,
                                        int toggleWidth, int toggleHeight) {
        return rect(
            panelX + panelWidth - toggleWidth - panelPadding,
            rowY + (rowHeight - toggleHeight) / 2,
            toggleWidth,
            toggleHeight
        );
    }

    public static Rect settingsBody(int popupX, int popupY, int popupWidth, int popupHeight) {
        int bodyX = popupX + 1;
        int bodyTop = popupY + 40;
        int buttonY = popupY + popupHeight - 20 - 16;
        int bodyBottom = buttonY - 8;
        return rect(bodyX, bodyTop, Math.max(1, popupWidth - 2), Math.max(1, bodyBottom - bodyTop));
    }

    public static int settingsContentX(int popupX) {
        return popupX + 20;
    }

    public static int settingsNodeSectionLabelY(int contentPopupY, int optionHeight) {
        int languageLabelY = contentPopupY + 44;
        int languageButtonY = languageLabelY + 12;
        int accentLabelY = languageButtonY + 50;
        int accentOptionsY = accentLabelY + 12;
        int sectionDividerY = accentOptionsY + optionHeight + 10;
        int settingDividerY = sectionDividerY + 22;
        int lowDetailDividerY = settingDividerY + 22;
        int footerDividerY = lowDetailDividerY + 22;
        int chatDividerY = footerDividerY + 22;
        int overlayDividerY = chatDividerY + 22;
        int hudDividerY = overlayDividerY + 22;
        int profilerDividerY = hudDividerY + 22;
        int delayDividerY = profilerDividerY + 26;
        return delayDividerY + 12;
    }

    public static int settingsNodeSectionBodyY(int contentPopupY, int optionHeight) {
        return settingsNodeSectionLabelY(contentPopupY, optionHeight) + 14;
    }

    public static Rect settingsCloseButton(int popupX, int popupY, int popupWidth, int popupHeight,
                                           int buttonWidth, int buttonHeight) {
        return rect(
            popupX + popupWidth - buttonWidth - 20,
            popupY + popupHeight - buttonHeight - 16,
            buttonWidth,
            buttonHeight
        );
    }

    public record PublishPresetLayout(
        Rect popup,
        int fieldX,
        int fieldWidth,
        Rect nameField,
        Rect descriptionField,
        Rect tagsField,
        Rect visibilityRow,
        Rect visibilityToggle,
        Rect cancelButton,
        Rect publishButton,
        Rect signInButton
    ) {
    }

    public record ButtonRow(Rect left, Rect right) {
    }

    public record ThreeButtonRow(Rect first, Rect second, Rect third) {
    }

    public record Rect(int x, int y, int width, int height) {
        public boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    private static int buttonY(int contentY, int preferredHeight, int buttonHeight, int bottomMargin) {
        return contentY + preferredHeight - buttonHeight - bottomMargin;
    }
}
