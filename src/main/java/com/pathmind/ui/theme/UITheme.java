package com.pathmind.ui.theme;

/**
 * Centralized theme constants for consistent UI styling across Pathmind.
 * Dark gaming panel aesthetic with red/green toggle indicators,
 * near-black backgrounds, and subtle borders.
 *
 * ALL UI colors must be defined here — no hardcoded hex values in rendering code.
 */
public final class UITheme {

    private UITheme() {} // Prevent instantiation

    // ═══════════════════════════════════════════════════════════════════════════
    // BACKGROUND COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Main workspace background - near black */
    public static final int BACKGROUND_PRIMARY = 0xFF0D0F13;

    /** Popup and panel backgrounds - very dark grey */
    public static final int BACKGROUND_SECONDARY = 0xFF171A20;

    /** Elevated surfaces, hover states - slightly lighter for depth */
    public static final int BACKGROUND_TERTIARY = 0xFF21262E;

    /** Sidebar background - darkest element */
    public static final int BACKGROUND_SIDEBAR = 0xFF12151B;

    /** Input field backgrounds - near black */
    public static final int BACKGROUND_INPUT = 0xFF0A0C10;

    /** Semi-transparent overlay for modals - dark gray with 50% opacity */
    public static final int OVERLAY_BACKGROUND = 0xA0101218;

    /** Section header background - slightly distinct from panel */
    public static final int BACKGROUND_SECTION = 0xFF14171E;

    /** Row stripe for alternating list items */
    public static final int BACKGROUND_ROW_ALT = 0xFF1A1D24;

    // ═══════════════════════════════════════════════════════════════════════════
    // BORDER COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Subtle borders - barely visible dividers */
    public static final int BORDER_SUBTLE = 0xFF292E37;

    /** Standard borders - default state */
    public static final int BORDER_DEFAULT = 0xFF3B414D;

    /** Focused element borders - more visible */
    public static final int BORDER_FOCUS = 0xFF596171;

    /** Active/highlighted borders - most prominent */
    public static final int BORDER_HIGHLIGHT = 0xFF707A8E;

    /** Section header border accent */
    public static final int BORDER_SECTION = 0xFF363D49;

    /** Border shown when a node is being dragged */
    public static final int BORDER_DRAGGING = 0xFF888888;

    /** Border for socket outlines on nodes */
    public static final int BORDER_SOCKET = 0xFF000000;

    /** Danger/warning border - light red */
    public static final int BORDER_DANGER = 0xFFF0B1B1;

    /** Muted danger border */
    public static final int BORDER_DANGER_MUTED = 0xFF6A4A4A;

    // ═══════════════════════════════════════════════════════════════════════════
    // TEXT COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Primary text - high contrast white */
    public static final int TEXT_PRIMARY = 0xFFE7E7E0;

    /** Secondary text - muted description text */
    public static final int TEXT_SECONDARY = 0xFFA0A39C;

    /** Tertiary text - disabled/placeholder */
    public static final int TEXT_TERTIARY = 0xFF6A6E78;

    /** Header text - bright white for titles */
    public static final int TEXT_HEADER = 0xFFF5F4EE;

    /** Label text - slightly dimmer than primary for form labels */
    public static final int TEXT_LABEL = 0xFFD4D5CE;

    // ═══════════════════════════════════════════════════════════════════════════
    // NODE RENDERING COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Node background when selected */
    public static final int NODE_SELECTED_BG = 0xFF2A2A2A;

    /** Node background when dragged over sidebar (deletion preview) */
    public static final int NODE_DIMMED_BG = 0xFF222222;

    /** Input field background inside nodes (active/editing) */
    public static final int NODE_INPUT_BG_ACTIVE = 0xFF181818;

    /** Input field background inside nodes (dimmed, over sidebar) */
    public static final int NODE_INPUT_BG_DIMMED = 0xFF1E1E1E;

    /** Semi-transparent node header overlay when over sidebar */
    public static final int NODE_HEADER_DIMMED = 0x80444444;

    /** Label color on nodes (normal) */
    public static final int NODE_LABEL_COLOR = 0xFF999999;

    /** Label color on nodes (dimmed, over sidebar) */
    public static final int NODE_LABEL_DIMMED = 0xFF666666;

    /** Node selection box fill */
    public static final int NODE_SELECTION_FILL = 0x401AA3FF;

    /** Node selection box border */
    public static final int NODE_SELECTION_BORDER = 0x801AA3FF;

    /** Alpha mask for node header tint */
    public static final int NODE_HEADER_ALPHA_MASK = 0x80FFFFFF;

    /** Start node fill */
    public static final int NODE_START_BG = 0xFF4CAF50;

    /** Start node border */
    public static final int NODE_START_BORDER = 0xFF2E7D32;

    /** Stop control node fill */
    public static final int NODE_STOP_BG = 0xFFE53935;

    /** Stop control node border */
    public static final int NODE_STOP_BORDER = 0xFFB71C1C;

    /** Event function node fill */
    public static final int NODE_EVENT_BG = 0xFFE91E63;

    /** Event function node border */
    public static final int NODE_EVENT_BORDER = 0xFFAD1457;

    /** Event call node fill */
    public static final int NODE_EVENT_CALL_BG = 0xFFD81B60;

    /** Event node title text */
    public static final int NODE_EVENT_TITLE = 0xFFFFF5F8;

    /** Event node input text */
    public static final int NODE_EVENT_TEXT = 0xFFFFEEF5;

    /** Event node input border */
    public static final int NODE_EVENT_INPUT_BORDER = 0xFF6A3A50;

    /** Event call node input border */
    public static final int NODE_EVENT_CALL_INPUT_BORDER = 0xFF51323E;

    /** Variable node fill */
    public static final int NODE_VARIABLE_BG = 0xFFFF9800;

    /** Variable node border */
    public static final int NODE_VARIABLE_BORDER = 0xFFEF6C00;

    /** Variable node title text */
    public static final int NODE_VARIABLE_TITLE = 0xFFFFF5E6;

    /** Variable node input text */
    public static final int NODE_VARIABLE_TEXT = 0xFFFFF1E1;

    /** Variable node input border */
    public static final int NODE_VARIABLE_INPUT_BORDER = 0xFF6A4E2A;

    /** Operator node fill */
    public static final int NODE_OPERATOR_BG = 0xFF00C853;

    /** Operator node border */
    public static final int NODE_OPERATOR_BORDER = 0xFF009E47;

    /** Operator node title text */
    public static final int NODE_OPERATOR_TITLE = 0xFFE9FFF5;

    /** Operator symbol text */
    public static final int NODE_OPERATOR_SYMBOL = 0xFFEFFFFA;

    // ═══════════════════════════════════════════════════════════════════════════
    // EDITING / CARET COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Active editing text - blue-tinted highlight */
    public static final int TEXT_EDITING = 0xFFE6F7FF;

    /** Active editing label - brighter blue tint */
    public static final int TEXT_EDITING_LABEL = 0xFFB8E7FF;

    /** Editing caret/cursor color */
    public static final int CARET_COLOR = 0xFFE6F7FF;

    /** Danger-context active editing text */
    public static final int TEXT_DANGER_ACTIVE = 0xFFFFEEEE;

    /** Danger-context muted value text */
    public static final int TEXT_DANGER_MUTED = 0xFF9A7A7A;

    /** Danger-context caret color */
    public static final int CARET_DANGER = 0xFFFFD6D6;

    /** Selection highlight for text inputs */
    public static final int TEXT_SELECTION_BG = 0x805577D6;

    /** Selection highlight for danger inputs */
    public static final int TEXT_SELECTION_DANGER_BG = 0x80E2A0A0;

    // ═══════════════════════════════════════════════════════════════════════════
    // DRAG & DROP COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Blue-tinted drop target background */
    public static final int DROP_HIGHLIGHT_BLUE = 0xFF182838;

    /** Green-tinted drop target background */
    public static final int DROP_HIGHLIGHT_GREEN = 0xFF1E2818;

    /** Green accent for action drop targets */
    public static final int DROP_ACCENT_GREEN = 0xFF8BC34A;

    /** Gray-tinted dropdown hover row */
    public static final int DROP_ROW_HIGHLIGHT = 0xFF2B2B2B;

    /** Schematic active border accent */
    public static final int SCHEMATIC_ACTIVE_BORDER = 0xFF9CCC65;

    // ═══════════════════════════════════════════════════════════════════════════
    // TOGGLE SWITCH COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Toggle track background */
    public static final int TOGGLE_TRACK = 0xFF1A1A1A;

    /** Toggle track hover background */
    public static final int TOGGLE_TRACK_HOVER = 0xFF222222;

    /** Toggle ON background - vivid green */
    public static final int TOGGLE_ON_BG = 0xFF2E7D32;

    /** Toggle ON border - bright green indicator */
    public static final int TOGGLE_ON_BORDER = 0xFF43A047;

    /** Toggle ON indicator color - bright green square */
    public static final int TOGGLE_ON_INDICATOR = 0xFF4CAF50;

    /** Toggle ON dimmed (inactive side) */
    public static final int TOGGLE_ON_DIM = 0xFF102A10;

    /** Toggle OFF background - deep red */
    public static final int TOGGLE_OFF_BG = 0xFF7B1A1A;

    /** Toggle OFF border - dark red */
    public static final int TOGGLE_OFF_BORDER = 0xFF9A2222;

    /** Toggle OFF indicator color - red square */
    public static final int TOGGLE_OFF_INDICATOR = 0xFFC62828;

    /** Toggle OFF dimmed (inactive side) */
    public static final int TOGGLE_OFF_DIM = 0xFF2A1010;

    /** Toggle knob color */
    public static final int TOGGLE_KNOB = 0xFFE0E0E0;

    /** Boolean toggle ON fill (inside nodes) */
    public static final int BOOL_TOGGLE_ON_FILL = 0xFF152A1C;

    /** Boolean toggle OFF fill (inside nodes) */
    public static final int BOOL_TOGGLE_OFF_FILL = 0xFF3A1515;

    /** Amount toggle enabled background */
    public static final int AMOUNT_TOGGLE_ON = 0xFF1E3324;

    // ═══════════════════════════════════════════════════════════════════════════
    // ACCENT COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Sky blue accent - selection and focus indicator */
    public static final int ACCENT_SKY = 0xFF698BA0;

    /** Mint green accent */
    public static final int ACCENT_MINT = 0xFF4C9A5A;

    /** Amber/orange accent */
    public static final int ACCENT_AMBER = 0xFFD0A937;

    /** Default accent color (sky) */
    public static final int ACCENT_DEFAULT = ACCENT_AMBER;

    // ═══════════════════════════════════════════════════════════════════════════
    // STATE COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Success state - green */
    public static final int STATE_SUCCESS = 0xFF43A047;

    /** Error state - red */
    public static final int STATE_ERROR = 0xFFC62828;

    /** Warning state - orange */
    public static final int STATE_WARNING = 0xFFE68A00;

    /** Info state - blue */
    public static final int STATE_INFO = 0xFF1E88E5;

    // ═══════════════════════════════════════════════════════════════════════════
    // BUTTON COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Default button background - dark */
    public static final int BUTTON_DEFAULT_BG = 0xFF232831;

    /** Default button hover background */
    public static final int BUTTON_DEFAULT_HOVER = 0xFF2D3440;

    /** Default button border */
    public static final int BUTTON_DEFAULT_BORDER = 0xFF4A5261;

    /** Button hover outline - subtle highlight */
    public static final int BUTTON_HOVER_OUTLINE = 0xFFD0A937;

    /** Primary button background - darker accent */
    public static final int BUTTON_PRIMARY_BG = 0xFF3A311B;

    /** Primary button hover */
    public static final int BUTTON_PRIMARY_HOVER = 0xFF4A3E22;

    /** Danger button background - dark red */
    public static final int BUTTON_DANGER_BG = 0xFF4F1F1F;

    /** Danger button hover */
    public static final int BUTTON_DANGER_HOVER = 0xFF642828;

    /** Active/selected button in segmented group */
    public static final int BUTTON_ACTIVE_BG = 0xFF2A2F39;

    /** Active button border in segmented group */
    public static final int BUTTON_ACTIVE_BORDER = 0xFFD0A937;

    // ═══════════════════════════════════════════════════════════════════════════
    // TOOLBAR COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Toolbar button default background */
    public static final int TOOLBAR_BG = 0xFF222730;

    /** Toolbar button hovered background */
    public static final int TOOLBAR_BG_HOVER = 0xFF2D3440;

    /** Toolbar button active/pressed background */
    public static final int TOOLBAR_BG_ACTIVE = 0xFF323843;

    /** Toolbar button disabled background */
    public static final int TOOLBAR_BG_DISABLED = 0xFF1A1E26;

    /** Execute button background when running */
    public static final int TOOLBAR_EXECUTE_BG = 0xFF243224;

    /** Execute button hover when running */
    public static final int TOOLBAR_EXECUTE_HOVER = 0xFF2F4531;

    /** Execute icon color */
    public static final int TOOLBAR_EXECUTE_ICON = 0xFF4CAF50;

    /** Execute icon hover color */
    public static final int TOOLBAR_EXECUTE_ICON_HOVER = 0xFF8BE97A;

    /** Execute icon disabled */
    public static final int TOOLBAR_EXECUTE_ICON_DISABLED = 0xFF4A7C4A;

    /** Stop button background when active */
    public static final int TOOLBAR_STOP_BG = 0xFF8C1B1B;

    /** Stop button hover when active */
    public static final int TOOLBAR_STOP_HOVER = 0xFFA02525;

    /** Stop button active border */
    public static final int TOOLBAR_STOP_BORDER = 0xFFFF4C4C;

    /** Stop button hover border */
    public static final int TOOLBAR_STOP_BORDER_HOVER = 0xFFFF6666;

    /** Stop icon color */
    public static final int TOOLBAR_STOP_ICON = 0xFFFF6F6F;

    /** Stop icon hover */
    public static final int TOOLBAR_STOP_ICON_HOVER = 0xFFFF8A8A;

    /** Stop icon inactive/faded */
    public static final int TOOLBAR_STOP_ICON_INACTIVE = 0xFFFFA6A6;

    /** Stop icon disabled */
    public static final int TOOLBAR_STOP_ICON_DISABLED = 0xFFB35E5E;

    // ═══════════════════════════════════════════════════════════════════════════
    // PRESET / DROPDOWN COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Preset dropdown background */
    public static final int DROPDOWN_BG = 0xFF232831;

    /** Preset dropdown hover */
    public static final int DROPDOWN_BG_HOVER = 0xFF2D3440;

    /** Dropdown option background */
    public static final int DROPDOWN_OPTION_BG = 0xFF1B2027;

    /** Dropdown option hover */
    public static final int DROPDOWN_OPTION_HOVER = 0xFF2A313D;

    /** Dropdown action disabled text */
    public static final int DROPDOWN_ACTION_DISABLED = 0xFF555555;

    // ═══════════════════════════════════════════════════════════════════════════
    // KEY SELECTOR COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Selected key background in keyboard selector */
    public static final int KEY_SELECTED_BG = 0xFF2F5E8A;

    // ═══════════════════════════════════════════════════════════════════════════
    // TOOLTIP COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Tooltip background (semi-transparent) */
    public static final int TOOLTIP_BG = 0xF0161920;

    /** Tooltip border (semi-transparent white) */
    public static final int TOOLTIP_BORDER = 0x80959EAF;

    // ═══════════════════════════════════════════════════════════════════════════
    // GRID COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Grid line color (semi-transparent) */
    public static final int GRID_LINE = 0x40363B45;

    /** Grid origin marker */
    public static final int GRID_ORIGIN = 0xFF2C313C;

    // ═══════════════════════════════════════════════════════════════════════════
    // DRAG PREVIEW COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Drag preview ghost background (semi-transparent) */
    public static final int DRAG_PREVIEW_BG = 0x802A2A2A;

    /** Drag preview border */
    public static final int DRAG_PREVIEW_BORDER = 0xFF888888;

    /** Drag preview header alpha */
    public static final int DRAG_PREVIEW_HEADER_ALPHA = 0x80;

    // ═══════════════════════════════════════════════════════════════════════════
    // MISCELLANEOUS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Baritone download link color */
    public static final int LINK_COLOR = 0xFF87CEEB;

    /** Rename field input background */
    public static final int RENAME_INPUT_BG = 0xFF151921;

    /** Rename field border */
    public static final int RENAME_INPUT_BORDER = 0xFF2E3440;

    /** Dropdown icon hover background */
    public static final int DROPDOWN_ICON_HOVER_BG = 0x33555555;

    /** Icon detail shadow (low) */
    public static final int ICON_DETAIL_SHADE_LOW = 0x66000000;

    /** Icon detail shadow (high) */
    public static final int ICON_DETAIL_SHADE_HIGH = 0x88000000;

    // ═══════════════════════════════════════════════════════════════════════════
    // INVENTORY SLOT CATEGORY COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final int SLOT_PLAYER = 0xFF3D6F7A;
    public static final int SLOT_HOTBAR = 0xFF4A5F9D;
    public static final int SLOT_ARMOR = 0xFFB0804F;
    public static final int SLOT_OFFHAND = 0xFF8A63C5;
    public static final int SLOT_CRAFTING = 0xFF4D8F52;
    public static final int SLOT_RESULT = 0xFFD0862D;
    public static final int SLOT_CONTAINER = 0xFF3C5E8A;
    public static final int SLOT_INPUT = 0xFF7A4A32;
    public static final int SLOT_OUTPUT = 0xFF6A6A6A;
    public static final int SLOT_SPECIAL = 0xFFD27A48;

    // ═══════════════════════════════════════════════════════════════════════════
    // CONTEXT MENU COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Context menu background */
    public static final int CONTEXT_MENU_BG = 0xFF161A21;

    /** Context menu border */
    public static final int CONTEXT_MENU_BORDER = 0xFF49505E;

    /** Context menu item hover background */
    public static final int CONTEXT_MENU_ITEM_HOVER = 0xFF242C37;

    /** Context menu separator line */
    public static final int CONTEXT_MENU_SEPARATOR = 0xFF333945;

    /** Context menu text color */
    public static final int CONTEXT_MENU_TEXT = 0xFFE0E0E0;

    /** Context menu category icon background */
    public static final int CONTEXT_MENU_ICON_BG = 0xFF262D38;

    /** Context menu group header text (sub-groups) */
    public static final int CONTEXT_MENU_GROUP_HEADER = 0xFF9CA2AF;

    /** Inner frame color used for beveled panel styling. */
    public static final int PANEL_INNER_BORDER = 0xFF1D222C;

    /** Hover background for icon hitboxes in list rows. */
    public static final int ICON_HITBOX_HOVER_BG = 0x334D5564;

    // ═══════════════════════════════════════════════════════════════════════════
    // DIMENSIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Standard border width */
    public static final int BORDER_WIDTH = 1;

    /** Standard button height */
    public static final int BUTTON_HEIGHT = 20;

    /** Standard input field height */
    public static final int INPUT_HEIGHT = 18;

    /** Toggle switch width */
    public static final int TOGGLE_WIDTH = 36;

    /** Toggle switch height */
    public static final int TOGGLE_HEIGHT = 14;

    /** Tab height */
    public static final int TAB_HEIGHT = 22;

    /** Standard popup padding */
    public static final int POPUP_PADDING = 14;

    /** Section spacing */
    public static final int SECTION_SPACING = 10;

    /** Scrollbar width */
    public static final int SCROLLBAR_WIDTH = 4;

    /** Context menu width */
    public static final int CONTEXT_MENU_WIDTH = 140;

    /** Context menu item height */
    public static final int CONTEXT_MENU_ITEM_HEIGHT = 18;

    /** Context menu padding */
    public static final int CONTEXT_MENU_PADDING = 2;

    /** Submenu horizontal offset from parent menu */
    public static final int SUBMENU_OFFSET = 0;

    // ═══════════════════════════════════════════════════════════════════════════
    // ANIMATION DURATIONS (milliseconds)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Hover animation duration */
    public static final int HOVER_ANIM_MS = 120;

    /** General transition duration */
    public static final int TRANSITION_ANIM_MS = 180;

    /** Popup fade duration */
    public static final int POPUP_ANIM_MS = 150;

    /** Fast animation for subtle effects */
    public static final int FAST_ANIM_MS = 80;
}
