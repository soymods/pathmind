package com.pathmind.nodes;

import com.google.gson.annotations.SerializedName;

import java.util.Locale;

public enum StartLaunchMode {
    @SerializedName("manual")
    MANUAL("manual", "Manual"),
    @SerializedName("client_launch")
    CLIENT_LAUNCH("client_launch", "Client Launch"),
    @SerializedName("world_join")
    WORLD_JOIN("world_join", "World Join"),
    @SerializedName("main_menu_open")
    MAIN_MENU_OPEN("main_menu_open", "Main Menu Open"),
    @SerializedName("screen_opened")
    SCREEN_OPENED("screen_opened", "Screen Opened");

    private final String id;
    private final String displayName;

    StartLaunchMode(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static StartLaunchMode fromId(String id) {
        if (id == null || id.trim().isEmpty()) {
            return MANUAL;
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (StartLaunchMode mode : values()) {
            if (mode.id.equals(normalized) || mode.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return mode;
            }
        }
        return MANUAL;
    }
}
