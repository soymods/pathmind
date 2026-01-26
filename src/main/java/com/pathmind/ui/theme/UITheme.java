package com.pathmind.ui.theme;

/**
 * Centralized theme constants for consistent UI styling across Pathmind.
 * Based on a clean, modern dark aesthetic with subtle borders and smooth transitions.
 */
public final class UITheme {

    private UITheme() {} // Prevent instantiation

    // ═══════════════════════════════════════════════════════════════════════════
    // BACKGROUND COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Main workspace background - darkest charcoal */
    public static final int BACKGROUND_PRIMARY = 0xFF1E1E1E;

    /** Popup and panel backgrounds - slightly lighter */
    public static final int BACKGROUND_SECONDARY = 0xFF252525;

    /** Elevated surfaces, hover states - lighter for depth */
    public static final int BACKGROUND_TERTIARY = 0xFF2D2D2D;

    /** Sidebar background - darkest element */
    public static final int BACKGROUND_SIDEBAR = 0xFF1A1A1A;

    /** Input field backgrounds - very dark */
    public static final int BACKGROUND_INPUT = 0xFF181818;

    /** Semi-transparent overlay for modals */
    public static final int OVERLAY_BACKGROUND = 0xAA000000;

    // ═══════════════════════════════════════════════════════════════════════════
    // BORDER COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Subtle borders - barely visible */
    public static final int BORDER_SUBTLE = 0xFF3A3A3A;

    /** Standard borders - default state */
    public static final int BORDER_DEFAULT = 0xFF444444;

    /** Focused element borders - more visible */
    public static final int BORDER_FOCUS = 0xFF5A5A5A;

    /** Active/highlighted borders - most prominent */
    public static final int BORDER_HIGHLIGHT = 0xFF666666;

    // ═══════════════════════════════════════════════════════════════════════════
    // TEXT COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Primary text - high contrast white */
    public static final int TEXT_PRIMARY = 0xFFE8E8E8;

    /** Secondary text - muted grey */
    public static final int TEXT_SECONDARY = 0xFFB0B0B0;

    /** Tertiary text - disabled/placeholder */
    public static final int TEXT_TERTIARY = 0xFF808080;

    /** Header text - pure white for titles */
    public static final int TEXT_HEADER = 0xFFFFFFFF;

    // ═══════════════════════════════════════════════════════════════════════════
    // TOGGLE SWITCH COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Toggle ON background - green */
    public static final int TOGGLE_ON_BG = 0xFF4CAF50;

    /** Toggle ON border - darker green */
    public static final int TOGGLE_ON_BORDER = 0xFF388E3C;

    /** Toggle OFF background - dark grey */
    public static final int TOGGLE_OFF_BG = 0xFF424242;

    /** Toggle OFF border - lighter grey */
    public static final int TOGGLE_OFF_BORDER = 0xFF616161;

    /** Toggle knob color - white */
    public static final int TOGGLE_KNOB = 0xFFFFFFFF;

    // ═══════════════════════════════════════════════════════════════════════════
    // ACCENT COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Sky blue accent - softer than original */
    public static final int ACCENT_SKY = 0xFF6BA3BE;

    /** Mint green accent */
    public static final int ACCENT_MINT = 0xFF5CB85C;

    /** Amber/orange accent */
    public static final int ACCENT_AMBER = 0xFFE6A23C;

    /** Default accent color (sky) */
    public static final int ACCENT_DEFAULT = ACCENT_SKY;

    // ═══════════════════════════════════════════════════════════════════════════
    // STATE COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Success state - green */
    public static final int STATE_SUCCESS = 0xFF4CAF50;

    /** Error state - red */
    public static final int STATE_ERROR = 0xFFE53935;

    /** Warning state - orange */
    public static final int STATE_WARNING = 0xFFFF9800;

    /** Info state - blue */
    public static final int STATE_INFO = 0xFF2196F3;

    // ═══════════════════════════════════════════════════════════════════════════
    // BUTTON COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Default button background */
    public static final int BUTTON_DEFAULT_BG = 0xFF3A3A3A;

    /** Default button hover background */
    public static final int BUTTON_DEFAULT_HOVER = 0xFF4A4A4A;

    /** Default button border */
    public static final int BUTTON_DEFAULT_BORDER = 0xFF505050;

    /** Button hover outline - light blue */
    public static final int BUTTON_HOVER_OUTLINE = 0xFF7DB9E8;

    /** Primary button background - uses accent */
    public static final int BUTTON_PRIMARY_BG = 0xFF4A6B7C;

    /** Primary button hover */
    public static final int BUTTON_PRIMARY_HOVER = 0xFF5A7B8C;

    /** Danger button background */
    public static final int BUTTON_DANGER_BG = 0xFF8B3A3A;

    /** Danger button hover */
    public static final int BUTTON_DANGER_HOVER = 0xFFA54A4A;

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
    public static final int TOGGLE_WIDTH = 40;

    /** Toggle switch height */
    public static final int TOGGLE_HEIGHT = 16;

    /** Tab height */
    public static final int TAB_HEIGHT = 24;

    /** Standard popup padding */
    public static final int POPUP_PADDING = 16;

    /** Section spacing */
    public static final int SECTION_SPACING = 12;

    /** Scrollbar width */
    public static final int SCROLLBAR_WIDTH = 4;

    // ═══════════════════════════════════════════════════════════════════════════
    // ANIMATION DURATIONS (milliseconds)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Hover animation duration */
    public static final int HOVER_ANIM_MS = 150;

    /** General transition duration */
    public static final int TRANSITION_ANIM_MS = 200;

    /** Popup fade duration */
    public static final int POPUP_ANIM_MS = 180;

    /** Fast animation for subtle effects */
    public static final int FAST_ANIM_MS = 100;
}
