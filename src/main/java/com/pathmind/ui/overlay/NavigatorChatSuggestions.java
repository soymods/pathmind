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
            context.drawTextWithShadow(textRenderer, entry.command(), x + 6, rowY, UITheme.TEXT_HEADER);
            context.drawTextWithShadow(textRenderer, entry.hint(), x + 74, rowY, UITheme.TEXT_SECONDARY);
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
        if ("!goto".equals(completion)) {
            String normalized = current.stripTrailing().stripLeading();
            if (normalized.startsWith("!goto")) {
                return "!goto";
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
        if (input.endsWith(" ")) {
            return List.of();
        }

        List<SuggestionEntry> suggestions = new ArrayList<>();
        if ("!".equals(input) || "!g".startsWith(input) || "!go".startsWith(input) || "!got".startsWith(input) || "!goto".startsWith(input)) {
            suggestions.add(new SuggestionEntry("!goto", "x y z", "!goto"));
        }
        if ("!".equals(input) || "!s".startsWith(input) || "!st".startsWith(input) || "!sto".startsWith(input) || "!stop".startsWith(input)) {
            suggestions.add(new SuggestionEntry("!stop", "cancel navigator", "!stop"));
        }
        return suggestions;
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
