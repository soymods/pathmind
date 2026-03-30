package com.pathmind.ui.overlay;

import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DrawContextBridge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renders a lightweight suggestion menu above chat for Pathmind's ! navigator commands.
 * This intentionally mirrors the command-list feel without depending on Minecraft's internal suggestor classes.
 */
public final class NavigatorChatSuggestions {
    private static final int WIDTH = 220;
    private static final int ENTRY_HEIGHT = 14;
    private static final int PADDING = 4;
    private static final int BOTTOM_MARGIN = 2;
    private static final int LEFT_MARGIN = 4;
    private static final int PANEL_BACKGROUND = 0x80000000;

    private static final NavigatorChatSuggestions INSTANCE = new NavigatorChatSuggestions();

    private int selectedIndex;

    private NavigatorChatSuggestions() {
    }

    public static NavigatorChatSuggestions getInstance() {
        return INSTANCE;
    }

    public void render(ChatScreen chatScreen, DrawContext context, int mouseX, int mouseY) {
        if (chatScreen == null || context == null) {
            return;
        }
        TextFieldWidget input = resolveChatField(chatScreen);
        if (input == null) {
            return;
        }
        List<SuggestionEntry> suggestions = getSuggestions(input.getText());
        if (suggestions.isEmpty()) {
            selectedIndex = 0;
            return;
        }

        selectedIndex = Math.max(0, Math.min(selectedIndex, suggestions.size() - 1));

        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client != null ? client.textRenderer : null;
        if (textRenderer == null) {
            return;
        }

        int totalHeight = suggestions.size() * ENTRY_HEIGHT + PADDING * 2;
        int x = Math.max(2, input.getX() + LEFT_MARGIN);
        int y = input.getY() - totalHeight - BOTTOM_MARGIN;

        context.fill(x, y, x + WIDTH, y + totalHeight, PANEL_BACKGROUND);
        drawPanelBorder(context, x, y, WIDTH, totalHeight, UITheme.BORDER_HIGHLIGHT);

        for (int i = 0; i < suggestions.size(); i++) {
            SuggestionEntry entry = suggestions.get(i);
            int rowY = y + PADDING + i * ENTRY_HEIGHT;
            if (i == selectedIndex) {
                context.fill(x + 1, rowY - 1, x + WIDTH - 1, rowY + ENTRY_HEIGHT - 1, 0x503A3A3A);
            }
            int commandX = x + 6;
            int hintX = Math.min(x + WIDTH - 8, commandX + textRenderer.getWidth(entry.command()) + 12);
            context.drawTextWithShadow(textRenderer, entry.command(), commandX, rowY, UITheme.TEXT_HEADER);
            context.drawTextWithShadow(textRenderer, entry.hint(), hintX, rowY, UITheme.TEXT_SECONDARY);
        }
    }

    public void tick(MinecraftClient client) {
        if (client == null || !(client.currentScreen instanceof ChatScreen chatScreen)) {
            return;
        }
        TextFieldWidget input = resolveChatField(chatScreen);
        if (input == null || getSuggestions(input.getText()).isEmpty()) {
            selectedIndex = 0;
        }
    }

    public boolean handleKeyPressed(ChatScreen chatScreen, int keyCode) {
        TextFieldWidget input = resolveChatField(chatScreen);
        if (input == null) {
            return false;
        }
        List<SuggestionEntry> suggestions = getSuggestions(input.getText());
        if (suggestions.isEmpty()) {
            selectedIndex = 0;
            return false;
        }

        switch (keyCode) {
            case GLFW.GLFW_KEY_UP:
                if (selectedIndex <= 0) {
                    selectedIndex = 0;
                    return false;
                }
                selectedIndex = Math.max(0, selectedIndex - 1);
                return true;
            case GLFW.GLFW_KEY_DOWN:
                selectedIndex = Math.min(suggestions.size() - 1, selectedIndex + 1);
                return true;
            case GLFW.GLFW_KEY_TAB:
                applySelectedSuggestion(input, suggestions);
                return true;
            default:
                return false;
        }
    }

    private void applySelectedSuggestion(TextFieldWidget input, List<SuggestionEntry> suggestions) {
        if (input == null || suggestions.isEmpty()) {
            return;
        }
        selectedIndex = Math.max(0, Math.min(selectedIndex, suggestions.size() - 1));
        String completion = normalizeCompletion(input.getText(), suggestions.get(selectedIndex).completion());
        input.setText(completion);
        input.setCursorToEnd(false);
        input.setSelectionEnd(input.getText().length());
    }

    private String normalizeCompletion(String currentText, String completion) {
        if (completion == null) {
            return currentText == null ? "" : currentText;
        }
        String current = currentText == null ? "" : currentText;
        String normalized = current.stripTrailing().stripLeading().toLowerCase(Locale.ROOT);
        for (String rootCommand : List.of("!travel", "!path", "!nav", "!flag", "!stop")) {
            if (rootCommand.equals(completion) && normalized.startsWith(rootCommand)) {
                return rootCommand;
            }
        }
        for (String branchCommand : List.of("!nav debug", "!nav water", "!nav water normal", "!nav water avoid",
            "!nav logs", "!nav logs enable", "!nav logs disable",
            "!flag break", "!flag place", "!flag break enable", "!flag break disable",
            "!flag place enable", "!flag place disable")) {
            if (branchCommand.equals(completion)) {
                return completion;
            }
        }
        return completion;
    }

    private List<SuggestionEntry> getSuggestions(String rawInput) {
        if (rawInput == null) {
            return List.of();
        }
        String input = rawInput.stripLeading().toLowerCase(Locale.ROOT);
        if (!input.startsWith("!")) {
            return List.of();
        }
        boolean endsWithSpace = input.endsWith(" ");
        String trimmed = endsWithSpace ? input.substring(0, input.length() - 1) : input;
        String[] parts = trimmed.substring(1).isBlank() ? new String[0] : trimmed.substring(1).split("\\s+");

        List<SuggestionEntry> suggestions = new ArrayList<>();

        if (parts.length == 0) {
            return rootSuggestions("");
        }
        if (parts.length == 1 && !endsWithSpace) {
            return rootSuggestions(parts[0]);
        }

        String root = parts[0];
        if ("travel".equals(root) || "path".equals(root)) {
            return movementSuggestions(root, parts.length, endsWithSpace);
        }
        if ("flag".equals(root)) {
            return flagSuggestions(parts, endsWithSpace);
        }
        if ("nav".equals(root)) {
            return navSuggestions(parts, endsWithSpace);
        }
        return suggestions;
    }

    private List<SuggestionEntry> rootSuggestions(String partialRoot) {
        List<SuggestionEntry> suggestions = new ArrayList<>();
        addIfMatches(suggestions, "!travel", "go somewhere", "!travel", partialRoot);
        addIfMatches(suggestions, "!path", "preview route", "!path", partialRoot);
        addIfMatches(suggestions, "!nav", "navigator tools", "!nav", partialRoot);
        addIfMatches(suggestions, "!flag", "toggle flags", "!flag", partialRoot);
        addIfMatches(suggestions, "!stop", "cancel navigator", "!stop", partialRoot);
        return suggestions;
    }

    private List<SuggestionEntry> movementSuggestions(String root, int partCount, boolean endsWithSpace) {
        List<SuggestionEntry> suggestions = new ArrayList<>();
        if (partCount == 1 && endsWithSpace) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.player != null) {
                int x = client.player.getBlockX();
                int y = client.player.getBlockY();
                int z = client.player.getBlockZ();
                suggestions.add(new SuggestionEntry(x + " " + y + " " + z, "current coords", "!" + root + " " + x + " " + y + " " + z));
                suggestions.add(new SuggestionEntry(x + " " + z, "current x z", "!" + root + " " + x + " " + z));
            } else {
                suggestions.add(new SuggestionEntry("<x> <y> <z>", "absolute or ~ coords", "!" + root + " "));
                suggestions.add(new SuggestionEntry("<x> <z>", "keep current y", "!" + root + " "));
            }
        }
        return suggestions;
    }

    private List<SuggestionEntry> flagSuggestions(String[] parts, boolean endsWithSpace) {
        List<SuggestionEntry> suggestions = new ArrayList<>();
        if (parts.length == 1 && endsWithSpace) {
            suggestions.add(new SuggestionEntry("break", "breaking flag", "!flag break"));
            suggestions.add(new SuggestionEntry("place", "placing flag", "!flag place"));
            return suggestions;
        }
        if (parts.length == 2 && !endsWithSpace) {
            addIfMatches(suggestions, "break", "breaking flag", "!flag break", parts[1]);
            addIfMatches(suggestions, "place", "placing flag", "!flag place", parts[1]);
            return suggestions;
        }
        if (parts.length == 2 && endsWithSpace && ("break".equals(parts[1]) || "place".equals(parts[1]))) {
            suggestions.add(new SuggestionEntry("enable", "turn on", "!flag " + parts[1] + " enable"));
            suggestions.add(new SuggestionEntry("disable", "turn off", "!flag " + parts[1] + " disable"));
            return suggestions;
        }
        if (parts.length == 3 && ("break".equals(parts[1]) || "place".equals(parts[1])) && !endsWithSpace) {
            addIfMatches(suggestions, "enable", "turn on", "!flag " + parts[1] + " enable", parts[2]);
            addIfMatches(suggestions, "disable", "turn off", "!flag " + parts[1] + " disable", parts[2]);
        }
        return suggestions;
    }

    private List<SuggestionEntry> navSuggestions(String[] parts, boolean endsWithSpace) {
        List<SuggestionEntry> suggestions = new ArrayList<>();
        if (parts.length == 1 && endsWithSpace) {
            suggestions.add(new SuggestionEntry("debug", "planner state", "!nav debug"));
            suggestions.add(new SuggestionEntry("water", "water mode", "!nav water"));
            suggestions.add(new SuggestionEntry("logs", "event log file", "!nav logs"));
            return suggestions;
        }
        if (parts.length == 2 && !endsWithSpace) {
            addIfMatches(suggestions, "debug", "planner state", "!nav debug", parts[1]);
            addIfMatches(suggestions, "water", "water mode", "!nav water", parts[1]);
            addIfMatches(suggestions, "logs", "event log file", "!nav logs", parts[1]);
            return suggestions;
        }
        if (parts.length == 2 && "logs".equals(parts[1]) && endsWithSpace) {
            suggestions.add(new SuggestionEntry("enable", "write log file", "!nav logs enable"));
            suggestions.add(new SuggestionEntry("disable", "stop writing log file", "!nav logs disable"));
            return suggestions;
        }
        if (parts.length == 3 && "logs".equals(parts[1]) && !endsWithSpace) {
            addIfMatches(suggestions, "enable", "write log file", "!nav logs enable", parts[2]);
            addIfMatches(suggestions, "disable", "stop writing log file", "!nav logs disable", parts[2]);
            return suggestions;
        }
        if (parts.length == 2 && "water".equals(parts[1]) && endsWithSpace) {
            suggestions.add(new SuggestionEntry("normal", "allow water", "!nav water normal"));
            suggestions.add(new SuggestionEntry("avoid", "avoid water", "!nav water avoid"));
            return suggestions;
        }
        if (parts.length == 3 && "water".equals(parts[1]) && !endsWithSpace) {
            addIfMatches(suggestions, "normal", "allow water", "!nav water normal", parts[2]);
            addIfMatches(suggestions, "avoid", "avoid water", "!nav water avoid", parts[2]);
        }
        return suggestions;
    }

    private void addIfMatches(List<SuggestionEntry> suggestions, String label, String hint, String completion, String partial) {
        if (partial == null || partial.isBlank() || label.startsWith("!" + partial) || label.startsWith(partial)) {
            suggestions.add(new SuggestionEntry(label, hint, completion));
        }
    }

    private TextFieldWidget resolveChatField(ChatScreen chatScreen) {
        Class<?> type = chatScreen.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() != TextFieldWidget.class) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(chatScreen);
                    if (value instanceof TextFieldWidget widget) {
                        return widget;
                    }
                } catch (IllegalAccessException ignored) {
                    return null;
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private void drawPanelBorder(DrawContext context, int x, int y, int width, int height, int color) {
        if (context == null || width <= 0 || height <= 0) {
            return;
        }
        int right = x + width - 1;
        int bottom = y + height - 1;
        context.drawHorizontalLine(x, right, y, color);
        context.drawVerticalLine(x, y, bottom, color);
        context.drawVerticalLine(right, y, bottom, color);
    }

    private record SuggestionEntry(String command, String hint, String completion) {
    }
}
