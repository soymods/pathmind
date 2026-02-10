package com.pathmind.nodes;

/**
 * Enum representing different modes for generalized nodes.
 * Each mode corresponds to a specific behavior within a generalized node type.
 */
public enum NodeMode {
    // GOTO modes
    GOTO_XYZ("Go to XYZ", "Go to specific X, Y, Z coordinates"),
    GOTO_XZ("Go to XZ", "Go to X, Z coordinates (Y defaults to surface)"),
    GOTO_Y("Go to Y", "Go to specific Y level"),
    GOTO_BLOCK("Go to Block", "Go to nearest block of specified type"),
    
    // GOAL modes
    GOAL_XYZ("Set Goal XYZ", "Set goal to specific X, Y, Z coordinates"),
    GOAL_XZ("Set Goal XZ", "Set goal to X, Z coordinates (Y defaults to surface)"),
    GOAL_Y("Set Goal Y", "Set goal to specific Y level"),
    GOAL_CURRENT("Set Goal Current", "Set goal to player's current position"),
    GOAL_CLEAR("Clear Goal", "Clear current goal"),
    
    // COLLECT modes
    COLLECT_SINGLE("Mine Single Block", "Configure mining of a single block type"),
    COLLECT_MULTIPLE("Mine Multiple Blocks", "Configure mining of multiple block types"),
    
    // BUILD modes
    BUILD_PLAYER("Build at Player", "Build schematic at player's location"),
    BUILD_XYZ("Build at XYZ", "Build schematic at specified coordinates"),
    
    // EXPLORE modes
    EXPLORE_CURRENT("Explore from Current", "Explore from current position"),
    EXPLORE_XYZ("Explore from XYZ", "Explore from specified coordinates"),
    EXPLORE_FILTER("Explore with Filter", "Explore using filter file"),
    
    // FOLLOW modes
    FOLLOW_PLAYER("Follow Player", "Follow specific player"),
    FOLLOW_PLAYERS("Follow Any Players", "Follow any players in range"),
    FOLLOW_ENTITIES("Follow Any Entities", "Follow any entities nearby"),
    FOLLOW_ENTITY_TYPE("Follow Entity Type", "Follow entities of specific type"),

    // CRAFT modes
    CRAFT_PLAYER_GUI("Player Inventory", "Craft using the player's 2x2 grid"),
    CRAFT_CRAFTING_TABLE("Crafting Table", "Craft using an open crafting table"),

    // Player GUI modes
    PLAYER_GUI_OPEN("Open Player GUI", "Open the player's inventory screen"),
    PLAYER_GUI_CLOSE("Close GUI", "Close the currently open screen"),

    // Screen control modes
    SCREEN_OPEN_CHAT("Open Chat", "Open the chat screen for typing"),
    SCREEN_CLOSE_CURRENT("Close Screen", "Close the currently open screen"),

    // UI Utils modes
    UI_UTILS_CLOSE_WITHOUT_PACKET("Close Without Packet", "Close the current GUI without sending a close packet"),
    UI_UTILS_CLOSE_SIGN_WITHOUT_PACKET("Close Sign Without Packet", "Close the sign editor without sending its update packet"),
    UI_UTILS_DESYNC("De-sync GUI", "Close the GUI server-side while keeping it open client-side"),
    UI_UTILS_SET_SEND_PACKETS("Set Send Packets", "Enable or disable sending UI packets"),
    UI_UTILS_SET_DELAY_PACKETS("Set Delay Packets", "Enable or disable delaying UI packets"),
    UI_UTILS_FLUSH_DELAYED_PACKETS("Flush Delayed Packets", "Send all delayed UI packets now"),
    UI_UTILS_SAVE_GUI("Save GUI", "Save the current GUI for later restore"),
    UI_UTILS_RESTORE_GUI("Restore Saved GUI", "Restore the last saved GUI"),
    UI_UTILS_DISCONNECT("Disconnect", "Disconnect from the current server"),
    UI_UTILS_DISCONNECT_AND_SEND("Disconnect and Send Packets", "Send delayed UI packets before disconnecting"),
    UI_UTILS_COPY_TITLE_JSON("Copy GUI Title JSON", "Copy the current GUI title as JSON"),
    UI_UTILS_FABRICATE_CLICK_SLOT("Fabricate Click Slot Packet", "Send a fabricated ClickSlot packet"),
    UI_UTILS_FABRICATE_BUTTON_CLICK("Fabricate Button Click Packet", "Send a fabricated ButtonClick packet"),
    UI_UTILS_SET_ENABLED("Set UI Utils Enabled", "Enable or disable UI Utils features"),
    UI_UTILS_SET_BYPASS_RESOURCE_PACK("Set Bypass Resource Pack", "Enable or disable resource pack bypass"),
    UI_UTILS_SET_FORCE_DENY_RESOURCE_PACK("Set Force Deny Resource Pack", "Enable or disable forced resource pack denial"),

    // FARM modes
    FARM_RANGE("Farm in Range", "Farm within specified range"),
    FARM_WAYPOINT("Farm at Waypoint", "Farm around specified waypoint"),
    
    // STOP modes
    STOP_NORMAL("Stop Process", "Stop current process"),
    STOP_CANCEL("Cancel Process", "Cancel current process"),
    STOP_FORCE("Force Cancel", "Force stop all processes"),

    // WAIT modes
    WAIT_SECONDS("Seconds", "Wait duration in seconds"),
    WAIT_TICKS("Ticks", "Wait duration in ticks (20 per second)"),
    WAIT_MINUTES("Minutes", "Wait duration in minutes"),
    WAIT_HOURS("Hours", "Wait duration in hours");

    private final String displayName;
    private final String description;

    NodeMode(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
    
    /**
     * Get all modes for a specific node type
     */
    public static NodeMode[] getModesForNodeType(NodeType nodeType) {
        switch (nodeType) {
            case GOTO:
                return new NodeMode[]{
                    GOTO_XYZ, GOTO_XZ, GOTO_Y, GOTO_BLOCK
                };
            case GOAL:
                return new NodeMode[]{
                    GOAL_XYZ, GOAL_XZ, GOAL_Y, GOAL_CURRENT, GOAL_CLEAR
                };
            case COLLECT:
                return new NodeMode[]{
                    COLLECT_SINGLE, COLLECT_MULTIPLE
                };
            case BUILD:
                return new NodeMode[]{
                    BUILD_PLAYER, BUILD_XYZ
                };
            case EXPLORE:
                return new NodeMode[]{
                    EXPLORE_CURRENT, EXPLORE_XYZ, EXPLORE_FILTER
                };
            case FOLLOW:
                return new NodeMode[]{
                    FOLLOW_PLAYER, FOLLOW_PLAYERS, FOLLOW_ENTITIES, FOLLOW_ENTITY_TYPE
                };
            case CRAFT:
                return new NodeMode[]{
                    CRAFT_PLAYER_GUI, CRAFT_CRAFTING_TABLE
                };
            case SCREEN_CONTROL:
                return new NodeMode[]{
                    SCREEN_OPEN_CHAT, SCREEN_CLOSE_CURRENT
                };
            case UI_UTILS:
                return new NodeMode[]{
                    UI_UTILS_CLOSE_WITHOUT_PACKET,
                    UI_UTILS_CLOSE_SIGN_WITHOUT_PACKET,
                    UI_UTILS_DESYNC,
                    UI_UTILS_SET_SEND_PACKETS,
                    UI_UTILS_SET_DELAY_PACKETS,
                    UI_UTILS_FLUSH_DELAYED_PACKETS,
                    UI_UTILS_SAVE_GUI,
                    UI_UTILS_RESTORE_GUI,
                    UI_UTILS_DISCONNECT,
                    UI_UTILS_DISCONNECT_AND_SEND,
                    UI_UTILS_COPY_TITLE_JSON,
                    UI_UTILS_FABRICATE_CLICK_SLOT,
                    UI_UTILS_FABRICATE_BUTTON_CLICK,
                    UI_UTILS_SET_ENABLED,
                    UI_UTILS_SET_BYPASS_RESOURCE_PACK,
                    UI_UTILS_SET_FORCE_DENY_RESOURCE_PACK
                };
            case FARM:
                return new NodeMode[]{
                    FARM_RANGE, FARM_WAYPOINT
                };
            case STOP:
                return new NodeMode[]{
                    STOP_NORMAL, STOP_CANCEL, STOP_FORCE
                };
            case WAIT:
                return new NodeMode[]{
                    WAIT_SECONDS, WAIT_TICKS, WAIT_MINUTES, WAIT_HOURS
                };
            default:
                return new NodeMode[0];
        }
    }
    
    /**
     * Get the default mode for a node type
     */
    public static NodeMode getDefaultModeForNodeType(NodeType nodeType) {
        switch (nodeType) {
            case GOTO:
                return GOTO_XYZ;
            case GOAL:
                return GOAL_XYZ;
            case COLLECT:
                return COLLECT_SINGLE;
            case BUILD:
                return BUILD_PLAYER;
            case EXPLORE:
                return EXPLORE_CURRENT;
            case FOLLOW:
                return FOLLOW_PLAYER;
            case CRAFT:
                return CRAFT_PLAYER_GUI;
            case OPEN_INVENTORY:
                return PLAYER_GUI_OPEN;
            case CLOSE_GUI:
                return PLAYER_GUI_CLOSE;
            case SCREEN_CONTROL:
                return SCREEN_OPEN_CHAT;
            case UI_UTILS:
                return UI_UTILS_CLOSE_WITHOUT_PACKET;
            case FARM:
                return FARM_RANGE;
            case STOP:
                return STOP_NORMAL;
            case WAIT:
                return WAIT_SECONDS;
            default:
                return null;
        }
    }
}
