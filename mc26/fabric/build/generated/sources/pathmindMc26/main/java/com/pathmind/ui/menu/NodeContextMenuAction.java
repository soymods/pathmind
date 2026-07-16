package com.pathmind.ui.menu;

public enum NodeContextMenuAction {
    COPY("pathmind.context.copy"),
    DUPLICATE("pathmind.context.duplicate"),
    PASTE("pathmind.context.paste"),
    DELETE("pathmind.context.delete");

    private final String translationKey;

    NodeContextMenuAction(String translationKey) {
        this.translationKey = translationKey;
    }

    public String getTranslationKey() {
        return translationKey;
    }
}
