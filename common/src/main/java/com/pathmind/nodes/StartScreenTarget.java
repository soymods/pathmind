package com.pathmind.nodes;

import com.google.gson.annotations.SerializedName;

import java.util.Locale;

public enum StartScreenTarget {
    @SerializedName("any")
    ANY("any", "Any Screen"),
    @SerializedName("main_menu")
    MAIN_MENU("main_menu", "Main Menu"),
    @SerializedName("pause_menu")
    PAUSE_MENU("pause_menu", "Pause Menu"),
    @SerializedName("chat")
    CHAT("chat", "Chat"),
    @SerializedName("inventory")
    INVENTORY("inventory", "Inventory"),
    @SerializedName("merchant")
    MERCHANT("merchant", "Merchant"),
    @SerializedName("visual_editor")
    VISUAL_EDITOR("visual_editor", "Visual Editor");

    private final String id;
    private final String displayName;

    StartScreenTarget(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean matches(String screenKey) {
        return this == ANY || id.equals(screenKey);
    }

    public static StartScreenTarget fromId(String id) {
        if (id == null || id.trim().isEmpty()) {
            return ANY;
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (StartScreenTarget target : values()) {
            if (target.id.equals(normalized) || target.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return target;
            }
        }
        return ANY;
    }
}
