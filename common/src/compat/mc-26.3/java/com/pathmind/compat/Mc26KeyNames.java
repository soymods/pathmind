package com.pathmind.compat;

/**
 * Maps legacy GLFW key names onto the InputConstants field names used by
 * Minecraft 26.3, whose input constants are SDL scancodes.
 */
public final class Mc26KeyNames {
    private static final String GLFW_PREFIX = "GLFW_";
    private static final String NUMPAD_PREFIX = "KEY_KP_";

    private Mc26KeyNames() {
    }

    public static String normalize(String name) {
        if (name == null) {
            return null;
        }
        String normalized = name.startsWith(GLFW_PREFIX) ? name.substring(GLFW_PREFIX.length()) : name;
        if (normalized.startsWith(NUMPAD_PREFIX) && normalized.length() == NUMPAD_PREFIX.length() + 1) {
            char digit = normalized.charAt(NUMPAD_PREFIX.length());
            if (Character.isDigit(digit)) {
                return "KEY_NUMPAD" + digit;
            }
        }
        return switch (normalized) {
            case "KEY_ENTER" -> "KEY_RETURN";
            case "KEY_EQUAL" -> "KEY_EQUALS";
            case "KEY_GRAVE_ACCENT" -> "KEY_GRAVE";
            case "KEY_LEFT_BRACKET" -> "KEY_LBRACKET";
            case "KEY_RIGHT_BRACKET" -> "KEY_RBRACKET";
            case "KEY_RIGHT_ALT" -> "KEY_RALT";
            case "KEY_KP_ENTER" -> "KEY_NUMPADENTER";
            case "KEY_KP_EQUAL" -> "KEY_NUMPADEQUALS";
            case "KEY_KP_ADD" -> "KEY_ADD";
            case "KEY_KP_MULTIPLY" -> "KEY_MULTIPLY";
            default -> normalized;
        };
    }
}
