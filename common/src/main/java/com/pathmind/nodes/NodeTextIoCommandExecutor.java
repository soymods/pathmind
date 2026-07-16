package com.pathmind.nodes;

import static com.pathmind.util.PathmindI18n.tr;

import com.pathmind.util.PlayerInventoryBridge;
import com.pathmind.execution.ExecutionManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;

final class NodeTextIoCommandExecutor {
    private final Node owner;

    NodeTextIoCommandExecutor(Node owner) {
        this.owner = owner;
    }

    void executeWriteBookCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }

        int pageNumber = getIntParameter("Page", 1);
        String text = getBookTextForPage(pageNumber);
        // Convert to 0-indexed page
        int pageIndex = Math.max(0, pageNumber - 1);

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) {
            sendNodeErrorMessage(client, tr("pathmind.error.clientOrPlayerUnavailable"));
            future.completeExceptionally(new RuntimeException(tr("pathmind.error.clientOrPlayerUnavailable")));
            return;
        }

        // Check if a book edit screen is open
        if (!(client.screen instanceof BookEditScreen)) {
            sendNodeErrorMessage(client, tr("pathmind.error.noBookScreenOpen"));
            future.completeExceptionally(new RuntimeException(tr("pathmind.error.noBookScreenOpen")));
            return;
        }

        BookEditScreen bookScreen = (BookEditScreen) client.screen;

        client.execute(() -> {
            try {
                // Use reflection to access the book screen's internal state
                // Get the pages list
                java.util.List<Object> pages = null;
                int currentPage = 0;

                // Try to find the pages field
                java.util.List<Object> emptyCandidate = null;
                java.util.List<Field> stringListFields = new java.util.ArrayList<>();
                Field pagesField = null;
                try {
                    pagesField = bookScreen.getClass().getDeclaredField("pages");
                    pagesField.setAccessible(true);
                    Object value = pagesField.get(bookScreen);
                    if (value instanceof java.util.List) {
                    @SuppressWarnings("unchecked")
                    java.util.List<Object> list = (java.util.List<Object>) value;
                    pages = list;
                    }
                } catch (NoSuchFieldException ignored) {
                    // Fallback to heuristic search below
                }
                for (Field field : bookScreen.getClass().getDeclaredFields()) {
                    if (pages != null) {
                        break;
                    }
                    if (field.getType() != java.util.List.class) {
                        continue;
                    }
                    field.setAccessible(true);
                    Object value = field.get(bookScreen);
                    if (!(value instanceof java.util.List)) {
                        continue;
                    }
                    @SuppressWarnings("unchecked")
                    java.util.List<Object> list = (java.util.List<Object>) value;
                    stringListFields.add(field);
                    if (!list.isEmpty()) {
                        pages = list;
                        break;
                    }
                    String fieldName = field.getName().toLowerCase();
                    if (fieldName.contains("page")) {
                        pages = list;
                        break;
                    }
                    if (emptyCandidate == null) {
                        emptyCandidate = list;
                    }
                }
                if (pages == null && emptyCandidate != null) {
                    pages = emptyCandidate;
                }

                if (pages == null) {
                    for (Field field : bookScreen.getClass().getDeclaredFields()) {
                    }
                    sendNodeErrorMessage(client, tr("pathmind.error.bookPagesUnavailable"));
                    future.completeExceptionally(new RuntimeException(tr("pathmind.error.bookPagesUnavailable")));
                    return;
                }

                // Ensure we have enough pages
                Method appendNewPageMethod = null;
                Method countPagesMethod = null;
                Method setPageTextMethod = null;
                Method updatePageMethod = null;
                Method writeNbtDataMethod = null;
                for (Method method : bookScreen.getClass().getDeclaredMethods()) {
                    String methodName = method.getName().toLowerCase();
                    if (method.getParameterCount() == 0 && method.getReturnType() == void.class) {
                        if (appendNewPageMethod == null
                            && (methodName.contains("appendnewpage") || methodName.contains("method_2436"))) {
                            method.setAccessible(true);
                            appendNewPageMethod = method;
                        }
                    }
                    if (method.getParameterCount() == 0 && method.getReturnType() == int.class) {
                        if (countPagesMethod == null
                            && (methodName.contains("countpages") || methodName.contains("method_17046"))) {
                            method.setAccessible(true);
                            countPagesMethod = method;
                        }
                    }
                    if (method.getParameterCount() == 0 && method.getReturnType() == void.class) {
                        if (updatePageMethod == null
                            && (methodName.contains("updatepage") || methodName.contains("method_71537"))) {
                            method.setAccessible(true);
                            updatePageMethod = method;
                        }
                        if (writeNbtDataMethod == null
                            && (methodName.contains("writenbtdata") || methodName.contains("method_37433"))) {
                            method.setAccessible(true);
                            writeNbtDataMethod = method;
                        }
                    }
                    if (method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == String.class
                        && method.getReturnType() == void.class) {
                        if (setPageTextMethod == null
                            && (methodName.contains("setpage") || methodName.contains("pagetext") || methodName.contains("method_71539"))) {
                            method.setAccessible(true);
                            setPageTextMethod = method;
                        }
                    }
                }

                if (pagesField != null) {
                    Object value = pagesField.get(bookScreen);
                    if (value instanceof java.util.List) {
                        @SuppressWarnings("unchecked")
                        java.util.List<Object> list = (java.util.List<Object>) value;
                        if (list.isEmpty()) {
                            try {
                                list.add(setPageTextMethod != null ? Filterable.passThrough("") : "");
                            } catch (UnsupportedOperationException ignored) {
                                // replace with mutable list if backing list is immutable
                                list = null;
                            }
                        }
                        if (list == null) {
                            java.util.List<Object> seeded = new java.util.ArrayList<>();
                            seeded.add(setPageTextMethod != null ? Filterable.passThrough("") : "");
                            pagesField.set(bookScreen, seeded);
                            pages = seeded;
                        } else {
                            pages = list;
                        }
                    }
                } else if (pages.isEmpty()) {
                    pages.add(setPageTextMethod != null ? Filterable.passThrough("") : "");
                }

                boolean useRawFilteredPairs = false;
                if (!pages.isEmpty()) {
                    useRawFilteredPairs = !(pages.get(0) instanceof String);
                } else if (setPageTextMethod != null) {
                    useRawFilteredPairs = true;
                }
                if (!pages.isEmpty()) {
                    Object first = pages.get(0);
                }
                int pageCount = pages.size();
                if (countPagesMethod != null) {
                    try {
                        pageCount = (int) countPagesMethod.invoke(bookScreen);
                    } catch (Exception ignored) {
                        pageCount = pages.size();
                    }
                }

                while (pageCount <= pageIndex) {
                    if (appendNewPageMethod != null) {
                        int beforeSize = pages.size();
                        appendNewPageMethod.invoke(bookScreen);
                        if (pagesField != null) {
                            Object value = pagesField.get(bookScreen);
                            if (value instanceof java.util.List) {
                                @SuppressWarnings("unchecked")
                                java.util.List<Object> list = (java.util.List<Object>) value;
                                pages = list;
                            }
                        }
                        if (countPagesMethod != null) {
                            pageCount = (int) countPagesMethod.invoke(bookScreen);
                        } else {
                            pageCount = pages.size();
                        }
                        if (pages.size() == beforeSize && pageCount == beforeSize) {
                            pages.add(useRawFilteredPairs ? Filterable.passThrough("") : "");
                            pageCount = pages.size();
                        }
                    } else {
                        pages.add(useRawFilteredPairs ? Filterable.passThrough("") : "");
                        pageCount = pages.size();
                    }
                }

                // Set the current page before applying text
                for (Field field : bookScreen.getClass().getDeclaredFields()) {
                    if (field.getType() == int.class) {
                        String fieldName = field.getName().toLowerCase();
                        if (fieldName.contains("page") || fieldName.contains("current")) {
                            field.setAccessible(true);
                            field.setInt(bookScreen, pageIndex);
                            break;
                        }
                    }
                }

                // Set the text on the specified page
                String truncatedText = text;
                if (truncatedText.length() > Node.BOOK_PAGE_MAX_CHARS) {
                    truncatedText = truncatedText.substring(0, Node.BOOK_PAGE_MAX_CHARS);
                }
                boolean setViaMethod = false;
                if (setPageTextMethod != null && pageIndex >= 0 && pageIndex < pages.size()) {
                    try {
                        setPageTextMethod.invoke(bookScreen, truncatedText);
                        setViaMethod = true;
                    } catch (Exception ignored) {
                        // Ignore UI refresh errors
                    }
                }
                if (!setViaMethod && pageIndex >= 0 && pageIndex < pages.size()) {
                    pages.set(pageIndex, useRawFilteredPairs ? Filterable.passThrough(truncatedText) : truncatedText);
                    if (pagesField != null) {
                        java.util.List<Object> copy = new java.util.ArrayList<>(pages);
                        pagesField.set(bookScreen, copy);
                        pages = copy;
                    }
                } else if (setViaMethod && pagesField != null) {
                    Object value = pagesField.get(bookScreen);
                    if (value instanceof java.util.List) {
                        @SuppressWarnings("unchecked")
                        java.util.List<Object> list = (java.util.List<Object>) value;
                        pages = list;
                    }
                }

                java.util.List<String> pageStrings = new java.util.ArrayList<>();
                for (Object page : pages) {
                    if (page instanceof String) {
                        pageStrings.add((String) page);
                    } else if (page instanceof Filterable) {
                        @SuppressWarnings("unchecked")
                        Filterable<String> pair = (Filterable<String>) page;
                        pageStrings.add(pair.get(false));
                    } else {
                        pageStrings.add("");
                    }
                }

                // Update any page-related fields to ensure the UI refreshes
                EditBox editBox = null;
                for (Field field : bookScreen.getClass().getDeclaredFields()) {
                    String fieldName = field.getName().toLowerCase();
                    if (field.getType() == java.util.List.class) {
                        if (!fieldName.contains("page")) {
                            continue;
                        }
                        field.setAccessible(true);
                        Object value = field.get(bookScreen);
                        if (value instanceof java.util.List) {
                            @SuppressWarnings("unchecked")
                            java.util.List<Object> list = (java.util.List<Object>) value;
                            try {
                                list.clear();
                                list.addAll(pages);
                            } catch (UnsupportedOperationException ignored) {
                                // Skip immutable lists
                            }
                        }
                        continue;
                    }
                    if (field.getType() == String[].class && fieldName.contains("page")) {
                        field.setAccessible(true);
                        field.set(bookScreen, pageStrings.toArray(new String[0]));
                        continue;
                    }
                    if (field.getType() == String.class
                        && fieldName.contains("page")
                        && (fieldName.contains("text") || fieldName.contains("content"))) {
                        field.setAccessible(true);
                        field.set(bookScreen, truncatedText);
                        continue;
                    }
                    if (field.getType() == EditBox.class
                        && (fieldName.contains("page") || fieldName.contains("text"))) {
                        field.setAccessible(true);
                        Object value = field.get(bookScreen);
                        if (value instanceof EditBox) {
                            editBox = (EditBox) value;
                            editBox.setValue(truncatedText);
                        }
                    }
                }
                if (editBox == null) {
                    for (Field field : bookScreen.getClass().getDeclaredFields()) {
                        if (field.getType() == EditBox.class) {
                            field.setAccessible(true);
                            Object value = field.get(bookScreen);
                            if (value instanceof EditBox) {
                                editBox = (EditBox) value;
                                editBox.setValue(truncatedText);
                                break;
                            }
                        }
                    }
                }

                // Keep the screen's backing ItemStack in sync so UI updates immediately
                ItemStack screenStack = null;
                for (Field field : bookScreen.getClass().getDeclaredFields()) {
                    if (field.getType() == ItemStack.class) {
                        field.setAccessible(true);
                        Object value = field.get(bookScreen);
                        if (value instanceof ItemStack) {
                            screenStack = (ItemStack) value;
                            break;
                        }
                    }
                }
                if (screenStack != null && screenStack.is(Items.WRITABLE_BOOK)) {
                    try {
                        java.util.List<Filterable<String>> componentPages = new java.util.ArrayList<>();
                        for (String page : pageStrings) {
                            componentPages.add(Filterable.passThrough(page));
                        }
                        screenStack.set(DataComponents.WRITABLE_BOOK_CONTENT,
                            new WritableBookContent(componentPages));
                    } catch (Exception ignored) {
                        // Ignore component sync errors
                    }
                }

                // Write updated pages into the book stack if possible
                if (writeNbtDataMethod != null) {
                    try {
                        writeNbtDataMethod.invoke(bookScreen);
                    } catch (Exception ignored) {
                        // Ignore persistence errors to avoid stopping execution
                    }
                }

                // Send book update to server and client so text becomes visible immediately.
                InteractionHand hand = InteractionHand.MAIN_HAND;
                for (Field field : bookScreen.getClass().getDeclaredFields()) {
                    if (field.getType() == InteractionHand.class) {
                        field.setAccessible(true);
                        Object value = field.get(bookScreen);
                        if (value instanceof InteractionHand) {
                            hand = (InteractionHand) value;
                            break;
                        }
                    }
                }
                ItemStack main = client.player.getMainHandItem();
                ItemStack offhand = client.player.getOffhandItem();
                if (!main.is(Items.WRITABLE_BOOK) && offhand.is(Items.WRITABLE_BOOK)) {
                    hand = InteractionHand.OFF_HAND;
                }
                ItemStack heldBook = client.player.getItemInHand(hand);
                if (heldBook != null && heldBook.is(Items.WRITABLE_BOOK)) {
                    // Keep stack component in sync so the UI reflects the change immediately
                    try {
                        java.util.List<Filterable<String>> componentPages = new java.util.ArrayList<>();
                        for (String page : pageStrings) {
                            componentPages.add(Filterable.passThrough(page));
                        }
                        heldBook.set(DataComponents.WRITABLE_BOOK_CONTENT,
                            new WritableBookContent(componentPages));
                    } catch (Exception ignored) {
                        // Fallback to packet-only update
                    }

                    // Tell server (and client) via standard packet (works in dev env)
                    int slot = hand == InteractionHand.MAIN_HAND
                        ? PlayerInventoryBridge.getSelectedSlot(client.player.getInventory())
                        : Inventory.INVENTORY_SIZE + Node.PLAYER_ARMOR_SLOT_COUNT;
                    client.getConnection().send(
                        new ServerboundEditBookPacket(slot, pageStrings, java.util.Optional.empty())
                    );

                    // Reopen the book screen to force a full UI refresh of the edited text
                    ItemStack reopenStack = screenStack != null ? screenStack : heldBook;
                    if (reopenStack != null && reopenStack.is(Items.WRITABLE_BOOK)) {
                        WritableBookContent content = reopenStack.get(DataComponents.WRITABLE_BOOK_CONTENT);
                        if (content == null) {
                            content = WritableBookContent.EMPTY;
                        }
                        final ItemStack reopenStackFinal = reopenStack;
                        final WritableBookContent contentFinal = content;
                        final InteractionHand reopenHand = hand;
                        final Player playerFinal = client.player;
                        client.execute(() -> {
                            if (playerFinal != null) {
                                net.minecraft.client.gui.screens.Screen bookEditScreen = createBookEditScreen(playerFinal, reopenStackFinal, reopenHand, contentFinal);
                                if (bookEditScreen != null) {
                                    client.setScreen(bookEditScreen);
                                }
                            }
                        });
                    }
                }

                // Flag book screen as dirty if such a field exists
                for (Field field : bookScreen.getClass().getDeclaredFields()) {
                    if (field.getType() == boolean.class) {
                        String fieldName = field.getName().toLowerCase();
                        if (fieldName.contains("dirty") || fieldName.contains("modified")) {
                            field.setAccessible(true);
                            field.setBoolean(bookScreen, true);
                            break;
                        }
                    }
                }

                // Safely invoke updatePage if it exists (only after pages are populated)
                if (updatePageMethod != null) {
                    try {
                        if (!pages.isEmpty()) {
                            updatePageMethod.invoke(bookScreen);
                        }
                    } catch (Exception ignored) {
                        // Ignore UI refresh errors to avoid stopping execution
                    }
                }

                // One more refresh on the next tick in case the edit box wasn't ready yet
                final Method setPageTextMethodFinal = setPageTextMethod;
                final Method updatePageMethodFinal = updatePageMethod;
                final java.util.List<Object> pagesFinal = pages;
                final String truncatedTextFinal = truncatedText;
                final int pageIndexFinal = pageIndex;
                final BookEditScreen bookScreenFinal = bookScreen;
                client.execute(() -> {
                    try {
                        if (setPageTextMethodFinal != null && pageIndexFinal >= 0 && pageIndexFinal < pagesFinal.size()) {
                            setPageTextMethodFinal.invoke(bookScreenFinal, truncatedTextFinal);
                        }
                        if (updatePageMethodFinal != null && !pagesFinal.isEmpty()) {
                            updatePageMethodFinal.invoke(bookScreenFinal);
                        }
                        // Force edit box text refresh on the next tick
                        EditBox delayedEditBox = null;
                        try {
                            Field editBoxField = bookScreenFinal.getClass().getDeclaredField("editBox");
                            editBoxField.setAccessible(true);
                            Object value = editBoxField.get(bookScreenFinal);
                            if (value instanceof EditBox) {
                                delayedEditBox = (EditBox) value;
                            }
                        } catch (NoSuchFieldException ignored) {
                            // fall back to scanning fields below
                        }
                        if (delayedEditBox == null) {
                            for (Field field : bookScreenFinal.getClass().getDeclaredFields()) {
                                if (field.getType() == EditBox.class) {
                                    field.setAccessible(true);
                                    Object value = field.get(bookScreenFinal);
                                    if (value instanceof EditBox) {
                                        delayedEditBox = (EditBox) value;
                                        break;
                                    }
                                }
                            }
                        }
                        if (delayedEditBox != null) {
                            delayedEditBox.setValue(truncatedTextFinal);
                            bookScreenFinal.setFocused(delayedEditBox);
                        }
                    } catch (Exception ignored) {
                        // Ignore delayed UI refresh errors
                    }
                });

                future.complete(null);
            } catch (Exception e) {
                String message = e.getMessage();
                if (message == null || message.isBlank()) {
                    message = e.getClass().getSimpleName();
                }
                sendNodeErrorMessage(client, tr("pathmind.error.writeBookFailed", message));
                future.completeExceptionally(e);
            }
        });
    }

    private static net.minecraft.client.gui.screens.Screen createBookEditScreen(
            Player player, ItemStack stack, InteractionHand hand, WritableBookContent content) {
        try {
            // Try 4-arg constructor (newer MC versions)
            java.lang.reflect.Constructor<?> ctor = BookEditScreen.class.getConstructor(
                Player.class, ItemStack.class, InteractionHand.class, WritableBookContent.class);
            return (net.minecraft.client.gui.screens.Screen) ctor.newInstance(player, stack, hand, content);
        } catch (NoSuchMethodException ignored) {
            // Fall through to 3-arg constructor
        } catch (ReflectiveOperationException e) {
            return null;
        }
        try {
            // Try 3-arg constructor (MC 1.21)
            java.lang.reflect.Constructor<?> ctor = BookEditScreen.class.getConstructor(
                Player.class, ItemStack.class, InteractionHand.class);
            return (net.minecraft.client.gui.screens.Screen) ctor.newInstance(player, stack, hand);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    void executeWriteSignCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) {
            sendNodeErrorMessage(client, tr("pathmind.error.clientOrPlayerUnavailable"));
            future.completeExceptionally(new RuntimeException(tr("pathmind.error.clientOrPlayerUnavailable")));
            return;
        }

        if (!(client.screen instanceof AbstractSignEditScreen)) {
            sendNodeErrorMessage(client, tr("pathmind.error.noSignScreenOpen"));
            future.completeExceptionally(new RuntimeException(tr("pathmind.error.noSignScreenOpen")));
            return;
        }

        String[] lines = new String[Node.SIGN_MAX_LINES];
        Arrays.fill(lines, "");
        String[] split = (getBookText() == null ? "" : getBookText()).split("\\n", -1);
        int copyCount = Math.min(Node.SIGN_MAX_LINES, split.length);
        for (int i = 0; i < copyCount; i++) {
            String line = split[i] == null ? "" : split[i];
            lines[i] = line.length() > Node.SIGN_LINE_MAX_CHARS ? line.substring(0, Node.SIGN_LINE_MAX_CHARS) : line;
        }

        final String[] signLines = lines;
        final AbstractSignEditScreen signScreen = (AbstractSignEditScreen) client.screen;
        client.execute(() -> {
            try {
                Field currentRowField = null;
                Method setCurrentRowMessageMethod = null;
                for (Class<?> cls = signScreen.getClass(); cls != null; cls = cls.getSuperclass()) {
                    for (Field field : cls.getDeclaredFields()) {
                        String fieldName = field.getName().toLowerCase(Locale.ROOT);
                        if (field.getType() == int.class && (fieldName.contains("currentrow") || fieldName.equals("field_40428"))) {
                            field.setAccessible(true);
                            currentRowField = field;
                            break;
                        }
                    }
                    if (currentRowField != null) {
                        break;
                    }
                }
                for (Class<?> cls = signScreen.getClass(); cls != null; cls = cls.getSuperclass()) {
                    for (Method method : cls.getDeclaredMethods()) {
                        String methodName = method.getName().toLowerCase(Locale.ROOT);
                        if (method.getParameterCount() == 1
                            && method.getParameterTypes()[0] == String.class
                            && method.getReturnType() == void.class
                            && (methodName.contains("setcurrentrowmessage") || methodName.equals("method_49913"))) {
                            method.setAccessible(true);
                            setCurrentRowMessageMethod = method;
                            break;
                        }
                    }
                    if (setCurrentRowMessageMethod != null) {
                        break;
                    }
                }

                if (currentRowField != null && setCurrentRowMessageMethod != null) {
                    for (int i = 0; i < signLines.length; i++) {
                        currentRowField.setInt(signScreen, i);
                        setCurrentRowMessageMethod.invoke(signScreen, signLines[i]);
                    }
                }

                for (Class<?> cls = signScreen.getClass(); cls != null; cls = cls.getSuperclass()) {
                    for (Field field : cls.getDeclaredFields()) {
                        field.setAccessible(true);
                        if (field.getType() == String[].class) {
                            Object raw = field.get(signScreen);
                            if (raw instanceof String[] target && target.length >= Node.SIGN_MAX_LINES) {
                                for (int i = 0; i < Node.SIGN_MAX_LINES; i++) {
                                    target[i] = signLines[i];
                                }
                                field.set(signScreen, target);
                            }
                        }
                    }
                }

                future.complete(null);
            } catch (Exception e) {
                String message = e.getMessage();
                if (message == null || message.isBlank()) {
                    message = e.getClass().getSimpleName();
                }
                sendNodeErrorMessage(client, tr("pathmind.error.writeSignFailed", message));
                future.completeExceptionally(e);
            }
        });
    }

    void executeMessageCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        List<String> lines = getMessageLines();
        if (lines == null || lines.isEmpty()) {
            lines = Collections.singletonList("Hello World");
        }

        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client != null && client.player != null) {
            long delayMs = 120L;
            int[] sent = {0};
            for (int i = 0; i < lines.size(); i++) {
                String raw = lines.get(i);
                String text = raw == null ? "" : raw.trim();
                text = resolveRuntimeVariablesInText(text);
                if (text.isEmpty()) {
                    continue;
                }
                String sendText = text;
                long scheduledDelay = sent[0] * delayMs;
                Node.MESSAGE_SCHEDULER.schedule(() -> {
                    Minecraft.getInstance().execute(() -> {
                        Minecraft currentClient = Minecraft.getInstance();
                        if (currentClient.player != null) {
                            if (isMessageClientSide()) {
                                currentClient.player.displayClientMessage(Component.literal(sendText), false);
                            } else if (currentClient.player.connection != null) {
                                boolean isCommand = sendText.startsWith("/");
                                if (isCommand) {
                                    String cmd = sendText.length() > 1 ? sendText.substring(1) : "";
                                    if (!cmd.isEmpty()) {
                                        currentClient.player.connection.sendCommand(cmd);
                                    }
                                } else {
                                    currentClient.player.connection.sendChat(sendText);
                                }
                            }
                        }
                    });
                }, scheduledDelay, TimeUnit.MILLISECONDS);
                sent[0]++;
            }
            long completionDelay = Math.max(0, (sent[0] - 1) * delayMs + delayMs);
            Node.MESSAGE_SCHEDULER.schedule(() -> {
                future.complete(null);
            }, completionDelay, TimeUnit.MILLISECONDS);
        } else {
            System.err.println("Unable to send message: client or player not available");
            future.complete(null);
        }
    }

    String resolveRuntimeVariablesInText(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        Node startNode = owner.getOwningStartNode();
        if (startNode == null && owner.getParentControl() != null) {
            startNode = owner.getParentControl().getOwningStartNode();
        }
        ExecutionManager manager = ExecutionManager.getInstance();
        StringBuilder output = new StringBuilder(raw.length());
        int index = 0;
        boolean containsStructuredReplacement = false;
        while (index < raw.length()) {
            char current = raw.charAt(index);
            if (current == '$') {
                RuntimeVariableInlineMatch match = findInlineRuntimeVariableReference(raw, index, manager, startNode);
                if (match != null) {
                    String replacement = formatRuntimeVariableValue(match.variable);
                    if (replacement != null && !replacement.isEmpty()) {
                        output.append(replacement);
                        if (replacement.indexOf(' ') >= 0 || replacement.indexOf('\t') >= 0 || replacement.indexOf('\n') >= 0) {
                            containsStructuredReplacement = true;
                        }
                        index = match.endIndex;
                        continue;
                    }
                    output.append(raw, index, match.endIndex);
                    index = match.endIndex;
                    continue;
                }
            }
            output.append(current);
            index++;
        }
        String resolved = output.toString();
        if (containsStructuredReplacement) {
            return resolved;
        }
        Double evaluated = Node.evaluateNumericExpression(resolved);
        if (evaluated != null) {
            return formatEvaluatedNumericText(evaluated);
        }
        return resolved;
    }

    private static String formatEvaluatedNumericText(double value) {
        if (!Double.isFinite(value)) {
            return Double.toString(value);
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private RuntimeVariableInlineMatch findInlineRuntimeVariableReference(String raw, int variableIndex,
                                                                          ExecutionManager manager, Node startNode) {
        if (raw == null || manager == null || variableIndex < 0 || variableIndex >= raw.length() || raw.charAt(variableIndex) != '$') {
            return null;
        }
        int nameStart = variableIndex + 1;
        if (nameStart >= raw.length()) {
            return null;
        }
        RuntimeVariableInlineMatch bestMatch = null;
        Set<String> candidateNames = collectRuntimeVariableNamesForParsing(manager, startNode);
        for (String candidateName : candidateNames) {
            if (candidateName == null || candidateName.isEmpty()) {
                continue;
            }
            if (!raw.regionMatches(nameStart, candidateName, 0, candidateName.length())) {
                continue;
            }
            int endIndex = nameStart + candidateName.length();
            if (endIndex < raw.length()) {
                char boundary = raw.charAt(endIndex);
                if (isInlineRuntimeVariableNameCharacter(boundary)) {
                    continue;
                }
            }
            ExecutionManager.RuntimeVariable variable = resolveRuntimeVariableForName(manager, startNode, candidateName);
            if (variable == null) {
                continue;
            }
            if (bestMatch == null || candidateName.length() > bestMatch.name.length()) {
                bestMatch = new RuntimeVariableInlineMatch(candidateName, endIndex, variable);
            }
        }
        return bestMatch;
    }

    private static boolean isInlineRuntimeVariableNameCharacter(char value) {
        return Character.isLetterOrDigit(value) || value == '_';
    }

    private Set<String> collectRuntimeVariableNamesForParsing(ExecutionManager manager, Node startNode) {
        Set<String> names = new LinkedHashSet<>();
        if (manager == null) {
            return names;
        }
        names.addAll(manager.getKnownRuntimeVariableNames());
        if (startNode != null) {
            for (ExecutionManager.RuntimeVariableEntry entry : manager.getRuntimeVariableEntries()) {
                if (entry == null || entry.getStartNodeId() == null || !startNode.getId().equals(entry.getStartNodeId())) {
                    continue;
                }
                String name = entry.getName();
                if (name != null && !name.trim().isEmpty()) {
                    names.add(name.trim());
                }
            }
        }
        return names;
    }

    ExecutionManager.RuntimeVariable resolveRuntimeVariableForName(ExecutionManager manager, Node startNode, String name) {
        if (manager == null || name == null || name.trim().isEmpty()) {
            return null;
        }
        String trimmedName = name.trim();
        if (startNode != null) {
            ExecutionManager.RuntimeVariable direct = manager.getRuntimeVariable(startNode, trimmedName);
            if (direct != null) {
                return direct;
            }
        }
        ExecutionManager.RuntimeVariable anyActive = manager.getRuntimeVariableFromAnyActiveChain(trimmedName);
        if (anyActive != null) {
            return anyActive;
        }
        ExecutionManager.RuntimeVariable match = null;
        for (ExecutionManager.RuntimeVariableEntry entry : manager.getRuntimeVariableEntries()) {
            if (entry == null) {
                continue;
            }
            String entryName = entry.getName();
            if (entryName == null) {
                continue;
            }
            if (!entryName.trim().equals(trimmedName)) {
                continue;
            }
            if (match != null) {
                return null;
            }
            match = entry.getVariable();
        }
        return match;
    }

    String formatRuntimeVariableValue(ExecutionManager.RuntimeVariable variable) {
        if (variable == null) {
            return "";
        }
        Map<String, String> values = variable.getValues();
        if (values == null || values.isEmpty()) {
            return "";
        }
        NodeType valueType = variable.getType();
        if (valueType == null) {
            return "";
        }
        if (valueType == NodeType.LIST_ITEM) {
            return formatListItemRuntimeVariable(values);
        }
        switch (valueType) {
            case PARAM_BLOCK:
            case PARAM_PLACE_TARGET:
                return getRuntimeValue(values, "block");
            case PARAM_ITEM:
            case PARAM_VILLAGER_TRADE:
                return getRuntimeValue(values, "item");
            case PARAM_ENTITY:
                return getRuntimeValue(values, "entity");
            case PARAM_PLAYER:
                return getRuntimeValue(values, "player");
            case PARAM_WAYPOINT:
                return getRuntimeValue(values, "waypoint");
            case PARAM_SCHEMATIC:
                return getRuntimeValue(values, "schematic");
            case PARAM_INVENTORY_SLOT:
                return getRuntimeValue(values, "slot");
            case SENSOR_CURRENT_HAND:
                return getRuntimeValue(values, "slot");
            case SENSOR_IS_ON_GROUND:
                return getRuntimeValue(values, "distance");
            case PARAM_DURATION:
                return getRuntimeValue(values, "duration");
            case PARAM_RANGE:
            case PARAM_CLOSEST:
                return getRuntimeValue(values, "range");
            case PARAM_DISTANCE:
                return getRuntimeValue(values, "distance");
            case PARAM_BLOCK_FACE: {
                String face = getRuntimeValue(values, "face");
                if (!face.isEmpty()) {
                    return face;
                }
                face = getRuntimeValue(values, "side");
                if (!face.isEmpty()) {
                    return face;
                }
                return getRuntimeValue(values, "direction");
            }
            case PARAM_DIRECTION: {
                String yaw = getRuntimeValue(values, "yaw");
                String pitch = getRuntimeValue(values, "pitch");
                if (!yaw.isEmpty() && !pitch.isEmpty()) {
                    return yaw + " " + pitch;
                }
                String direction = getRuntimeValue(values, "direction");
                if (!direction.isEmpty()) {
                    return direction;
                }
                direction = getRuntimeValue(values, "side");
                if (!direction.isEmpty()) {
                    return direction;
                }
                return getRuntimeValue(values, "face");
            }
            case PARAM_AMOUNT:
                return getRuntimeValue(values, "amount");
            case OPERATOR_MOD: {
                String value = getRuntimeValue(values, "value");
                if (!value.isEmpty()) {
                    return value;
                }
                return getRuntimeValue(values, "amount");
            }
            case LIST_LENGTH: {
                String length = getRuntimeValue(values, "count");
                if (!length.isEmpty()) {
                    return length;
                }
                length = getRuntimeValue(values, "value");
                if (!length.isEmpty()) {
                    return length;
                }
                return getRuntimeValue(values, "amount");
            }
            case SENSOR_SLOT_ITEM_COUNT:
                return getRuntimeValue(values, "amount");
            case OPERATOR_RANDOM:
                String value = getRuntimeValue(values, "value");
                if (!value.isEmpty()) {
                    return value;
                }
                return getRuntimeValue(values, "amount");
            case PARAM_BOOLEAN:
                return getRuntimeValue(values, "toggle");
            case PARAM_HAND:
                return getRuntimeValue(values, "hand");
            case SENSOR_CURRENT_GUI:
            case PARAM_GUI:
                return getRuntimeValue(values, "gui");
            case PARAM_COORDINATE:
                return formatCoordinateValues(values);
            case PARAM_ROTATION:
                return formatRotationValues(values);
            case VARIABLE:
                return getRuntimeValue(values, "variable");
            case SENSOR_POSITION_OF:
                if (owner.isSensorPositionSingleAxisMode()) {
                    String amount = getRuntimeValue(values, "amount");
                    if (!amount.isEmpty()) {
                        return amount;
                    }
                    amount = getRuntimeValue(values, "value");
                    if (!amount.isEmpty()) {
                        return amount;
                    }
                }
                return formatCoordinateValues(values);
            case SENSOR_DISTANCE_BETWEEN:
                return getRuntimeValue(values, "distance");
            case SENSOR_TARGETED_BLOCK: {
                String block = getRuntimeValue(values, "block");
                if (!block.isEmpty()) {
                    String state = getRuntimeValue(values, "state");
                    if (!state.isEmpty()) {
                        return block + "[" + state + "]";
                    }
                    return block;
                }
                break;
            }
            case SENSOR_TARGETED_ENTITY: {
                String entity = getRuntimeValue(values, "entity");
                if (!entity.isEmpty()) {
                    String state = getRuntimeValue(values, "state");
                    if (!state.isEmpty()) {
                        return entity + "[" + state + "]";
                    }
                    return entity;
                }
                break;
            }
            case SENSOR_LOOK_DIRECTION: {
                String yaw = getRuntimeValue(values, "yaw");
                String pitch = getRuntimeValue(values, "pitch");
                if (!yaw.isEmpty() && !pitch.isEmpty()) {
                    return yaw + " " + pitch;
                }
                String amount = getRuntimeValue(values, "amount");
                if (!amount.isEmpty()) {
                    return amount;
                }
                String direction = getRuntimeValue(values, "direction");
                if (!direction.isEmpty()) {
                    return direction;
                }
                direction = getRuntimeValue(values, "side");
                if (!direction.isEmpty()) {
                    return direction;
                }
                return getRuntimeValue(values, "face");
            }
            case SENSOR_TARGETED_BLOCK_FACE: {
                String side = getRuntimeValue(values, "side");
                if (!side.isEmpty()) {
                    return side;
                }
                side = getRuntimeValue(values, "face");
                if (!side.isEmpty()) {
                    return side;
                }
                return getRuntimeValue(values, "text");
            }
            default:
                break;
        }
        return owner.formatCanonicalValueMap(values);
    }

    private String formatListItemRuntimeVariable(Map<String, String> values) {
        Node listItem = new Node(NodeType.LIST_ITEM, 0, 0);
        listItem.setSocketsHidden(true);
        Node startNode = owner.resolveExecutionStartNode();
        if (startNode != null) {
            listItem.setOwningStartNode(startNode);
        }
        listItem.applyParameterValuesFromMap(values);

        Node resolved = owner.resolveListItemValueNode(listItem, null, false, null);
        if (resolved == null) {
            return "";
        }
        return formatRuntimeVariableValue(new ExecutionManager.RuntimeVariable(
            resolved.getType(),
            resolved.exportParameterValues()
        ));
    }

    String formatCoordinateValues(Map<String, String> values) {
        String x = getRuntimeValue(values, "x");
        String y = getRuntimeValue(values, "y");
        String z = getRuntimeValue(values, "z");
        if (x.isEmpty() || y.isEmpty() || z.isEmpty()) {
            return "";
        }
        return x + " " + y + " " + z;
    }

    String formatRotationValues(Map<String, String> values) {
        String yaw = getRuntimeValue(values, "yaw");
        String pitch = getRuntimeValue(values, "pitch");
        if (yaw.isEmpty() || pitch.isEmpty()) {
            return "";
        }
        return yaw + " " + pitch;
    }

    String getRuntimeValue(Map<String, String> values, String key) {
        if (values == null || key == null) {
            return "";
        }
        String direct = values.get(key);
        if (direct != null && !direct.trim().isEmpty()) {
            return direct.trim();
        }
        String lowerKey = key.toLowerCase(Locale.ROOT);
        if (!lowerKey.equals(key)) {
            String lower = values.get(lowerKey);
            if (lower != null && !lower.trim().isEmpty()) {
                return lower.trim();
            }
        }
        String normalizedKey = Node.normalizeParameterKey(key);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry == null || entry.getKey() == null) {
                continue;
            }
            if (!Node.normalizeParameterKey(entry.getKey()).equals(normalizedKey)) {
                continue;
            }
            String candidate = entry.getValue();
            if (candidate != null && !candidate.trim().isEmpty()) {
                return candidate.trim();
            }
        }
        return "";
    }

    private static final class RuntimeVariableInlineMatch {
        private final String name;
        private final int endIndex;
        private final ExecutionManager.RuntimeVariable variable;

        private RuntimeVariableInlineMatch(String name, int endIndex, ExecutionManager.RuntimeVariable variable) {
            this.name = name;
            this.endIndex = endIndex;
            this.variable = variable;
        }
    }

    private Node.ParameterHandlingResult preprocessAttachedParameter(EnumSet<Node.ParameterUsage> usages, CompletableFuture<Void> future) {
        return owner.preprocessAttachedParameter(usages, future);
    }

    private int getIntParameter(String name, int defaultValue) {
        return owner.getIntParameter(name, defaultValue);
    }

    private String getBookTextForPage(int pageNumber) {
        return owner.getBookTextForPage(pageNumber);
    }

    private String getBookText() {
        return owner.getBookText();
    }

    private List<String> getMessageLines() {
        return owner.getMessageLines();
    }

    private boolean isMessageClientSide() {
        return owner.isMessageClientSide();
    }

    private void sendNodeErrorMessage(Minecraft client, String message) {
        owner.sendNodeErrorMessage(client, message);
    }
}
