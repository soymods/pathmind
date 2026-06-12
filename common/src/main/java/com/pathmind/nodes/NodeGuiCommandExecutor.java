package com.pathmind.nodes;

import static com.pathmind.util.PathmindI18n.tr;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;

final class NodeGuiCommandExecutor {
    private final Node owner;
    private final NodeMode mode;

    NodeGuiCommandExecutor(Node owner) {
        this.owner = owner;
        this.mode = owner.getMode();
    }

    void executeUiUtilsCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        NodeMode uiMode = mode != null ? mode : NodeMode.UI_UTILS_CLOSE_WITHOUT_PACKET;
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();

        if (client == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        if (!com.pathmind.util.UiUtilsProxy.isAvailable()) {
            sendNodeErrorMessage(client, tr("pathmind.error.uiUtilsNotInstalled"));
            future.complete(null);
            return;
        }

        try {
            runOnClientThread(client, () -> {
                boolean modernBackend = com.pathmind.util.UiUtilsProxy.isModernBackend();
                switch (uiMode) {
                    case UI_UTILS_CLOSE_WITHOUT_PACKET:
                        if (modernBackend) {
                            executeUiUtilsCommandOrThrow("close");
                        } else {
                            client.setScreen(null);
                        }
                        break;
                    case UI_UTILS_CLOSE_SIGN_WITHOUT_PACKET:
                        if (modernBackend) {
                            throw new RuntimeException("UI Utils version does not support close sign without packet.");
                        }
                        com.pathmind.util.UiUtilsProxy.setShouldEditSign(false);
                        client.setScreen(null);
                        break;
                    case UI_UTILS_DESYNC:
                        if (modernBackend) {
                            executeUiUtilsCommandOrThrow("desync");
                            break;
                        }
                        if (client.getNetworkHandler() == null || client.player == null) {
                            throw new RuntimeException("Cannot de-sync without a connected player.");
                        }
                        client.getNetworkHandler().sendPacket(
                            new net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket(
                                client.player.currentScreenHandler.syncId
                            )
                        );
                        break;
                    case UI_UTILS_SET_SEND_PACKETS: {
                        boolean enabled = parseNodeBoolean(owner, "Enabled", true);
                        if (!com.pathmind.util.UiUtilsProxy.setSendPackets(enabled)) {
                            throw new RuntimeException("Failed to update UI Utils send packets setting.");
                        }
                        break;
                    }
                    case UI_UTILS_SET_DELAY_PACKETS: {
                        boolean enabled = parseNodeBoolean(owner, "Enabled", true);
                        updateUiUtilsDelayPackets(client, modernBackend, enabled);
                        break;
                    }
                    case UI_UTILS_ENABLE_DELAY_PACKETS:
                        toggleUiUtilsDelayPackets(client, modernBackend);
                        break;
                    case UI_UTILS_DISABLE_DELAY_PACKETS:
                        updateUiUtilsDelayPackets(client, modernBackend, false);
                        break;
                    case UI_UTILS_FLUSH_DELAYED_PACKETS:
                        flushUiUtilsDelayedPackets(client, true);
                        break;
                    case UI_UTILS_SAVE_GUI:
                        if (modernBackend) {
                            executeUiUtilsCommandOrThrow("screen save default");
                            break;
                        }
                        if (client.player == null) {
                            throw new RuntimeException("Cannot save GUI without an active player.");
                        }
                        if (!com.pathmind.util.UiUtilsProxy.setStoredScreen(client.currentScreen, client.player.currentScreenHandler)) {
                            throw new RuntimeException("Failed to save GUI.");
                        }
                        break;
                    case UI_UTILS_RESTORE_GUI: {
                        if (modernBackend) {
                            executeUiUtilsCommandOrThrow("screen load default");
                            break;
                        }
                        if (client.player == null) {
                            throw new RuntimeException("Cannot restore GUI without an active player.");
                        }
                        net.minecraft.client.gui.screen.Screen storedScreen = com.pathmind.util.UiUtilsProxy.getStoredScreen();
                        net.minecraft.screen.ScreenHandler storedHandler = com.pathmind.util.UiUtilsProxy.getStoredScreenHandler();
                        if (storedScreen == null || storedHandler == null) {
                            throw new RuntimeException("No saved GUI is available.");
                        }
                        client.setScreen(storedScreen);
                        client.player.currentScreenHandler = storedHandler;
                        break;
                    }
                    case UI_UTILS_DISCONNECT:
                        if (modernBackend) {
                            executeUiUtilsCommandOrThrow("disconnect");
                            break;
                        }
                        if (client.getNetworkHandler() == null) {
                            throw new RuntimeException("Cannot disconnect without a network handler.");
                        }
                        client.getNetworkHandler().getConnection().disconnect(Text.of("Disconnecting (UI-UTILS)"));
                        break;
                    case UI_UTILS_DISCONNECT_AND_SEND:
                        if (modernBackend) {
                            if (!com.pathmind.util.UiUtilsProxy.disconnectAndSendPackets()) {
                                throw new RuntimeException("Failed to disconnect and send UI Utils packets.");
                            }
                            break;
                        }
                        if (client.getNetworkHandler() == null) {
                            throw new RuntimeException("Cannot disconnect without a network handler.");
                        }
                        com.pathmind.util.UiUtilsProxy.setDelayPackets(false);
                        flushUiUtilsDelayedPackets(client, false);
                        client.getNetworkHandler().getConnection().disconnect(Text.of("Disconnecting (UI-UTILS)"));
                        break;
                    case UI_UTILS_COPY_TITLE_JSON:
                        if (client.currentScreen == null) {
                            throw new RuntimeException("No GUI is open to copy.");
                        }
                        copyGuiTitleJson(client);
                        break;
                    case UI_UTILS_FABRICATE_CLICK_SLOT:
                        if (modernBackend) {
                            executeUiUtilsCommandOrThrow(buildModernClickCommand());
                            break;
                        }
                        fabricateClickSlotPacket(client);
                        break;
                    case UI_UTILS_FABRICATE_BUTTON_CLICK:
                        if (modernBackend) {
                            executeUiUtilsCommandOrThrow(buildModernButtonCommand());
                            break;
                        }
                        fabricateButtonClickPacket(client);
                        break;
                    case UI_UTILS_SET_ENABLED: {
                        if (modernBackend) {
                            throw new RuntimeException("UI Utils version does not support enable toggles.");
                        }
                        boolean enabled = parseNodeBoolean(owner, "Enabled", true);
                        if (!com.pathmind.util.UiUtilsProxy.setEnabled(enabled)) {
                            throw new RuntimeException("Failed to update UI Utils enabled state.");
                        }
                        break;
                    }
                    case UI_UTILS_SET_BYPASS_RESOURCE_PACK: {
                        if (modernBackend) {
                            throw new RuntimeException("UI Utils version does not support bypass resource pack toggles.");
                        }
                        boolean enabled = parseNodeBoolean(owner, "Enabled", true);
                        if (!com.pathmind.util.UiUtilsProxy.setBypassResourcePack(enabled)) {
                            throw new RuntimeException("Failed to update UI Utils resource pack bypass.");
                        }
                        break;
                    }
                    case UI_UTILS_SET_FORCE_DENY_RESOURCE_PACK: {
                        if (modernBackend) {
                            throw new RuntimeException("UI Utils version does not support force-deny toggles.");
                        }
                        boolean enabled = parseNodeBoolean(owner, "Enabled", true);
                        if (!com.pathmind.util.UiUtilsProxy.setResourcePackForceDeny(enabled)) {
                            throw new RuntimeException("Failed to update UI Utils force deny setting.");
                        }
                        break;
                    }
                    default:
                        throw new IllegalStateException("Unknown UI Utils mode: " + uiMode);
                }
            });
            future.complete(null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.completeExceptionally(e);
        } catch (RuntimeException e) {
            sendNodeErrorMessage(client, e.getMessage());
            future.complete(null);
        }
    }

    private void executeUiUtilsCommandOrThrow(String command) {
        if (command == null || command.isBlank()) {
            throw new RuntimeException("UI Utils command is empty.");
        }
        if (!com.pathmind.util.UiUtilsProxy.executeCommand(command)) {
            throw new RuntimeException("UI Utils command failed: " + command);
        }
    }

    private void updateUiUtilsDelayPackets(net.minecraft.client.MinecraftClient client, boolean modernBackend, boolean enabled) {
        Boolean wasEnabled = com.pathmind.util.UiUtilsProxy.getDelayPackets();
        if (!com.pathmind.util.UiUtilsProxy.setDelayPackets(enabled)) {
            throw new RuntimeException("Failed to update UI Utils delay packets setting.");
        }
        if (!modernBackend && !enabled && Boolean.TRUE.equals(wasEnabled)) {
            flushUiUtilsDelayedPackets(client, true);
        }
    }

    private void toggleUiUtilsDelayPackets(net.minecraft.client.MinecraftClient client, boolean modernBackend) {
        Boolean currentlyEnabled = com.pathmind.util.UiUtilsProxy.getDelayPackets();
        updateUiUtilsDelayPackets(client, modernBackend, !Boolean.TRUE.equals(currentlyEnabled));
    }

    private String buildModernClickCommand() {
        int syncId = parseNodeInt(owner, "SyncId", -1);
        int revision = parseNodeInt(owner, "Revision", -1);
        int slot = parseNodeInt(owner, "Slot", 0);
        int button = parseNodeInt(owner, "Button", 0);
        String actionLabel = getParameterString(owner, "Action");
        int timesToSend = Math.max(1, parseNodeInt(owner, "TimesToSend", 1));

        SlotActionType action = parseSlotActionType(actionLabel);
        if (action == null) {
            throw new RuntimeException("Invalid slot action type.");
        }

        StringBuilder command = new StringBuilder();
        command.append("click ")
            .append(slot)
            .append(' ')
            .append(button)
            .append(' ')
            .append(action.name());

        if (syncId >= 0) {
            command.append(" --syncId ").append(syncId);
        }
        if (revision >= 0) {
            command.append(" --revision ").append(revision);
        }
        if (timesToSend > 1) {
            command.append(" --times ").append(timesToSend);
        }
        return command.toString();
    }

    private String buildModernButtonCommand() {
        int syncId = parseNodeInt(owner, "SyncId", -1);
        int buttonId = parseNodeInt(owner, "ButtonId", 0);
        int timesToSend = Math.max(1, parseNodeInt(owner, "TimesToSend", 1));

        StringBuilder command = new StringBuilder();
        command.append("button ").append(buttonId);
        if (syncId >= 0) {
            command.append(" --syncId ").append(syncId);
        }
        if (timesToSend > 1) {
            command.append(" --times ").append(timesToSend);
        }
        return command.toString();
    }

    private void flushUiUtilsDelayedPackets(net.minecraft.client.MinecraftClient client, boolean notifyPlayer) {
        if (client == null || client.getNetworkHandler() == null) {
            throw new RuntimeException("Minecraft network handler not available.");
        }
        java.util.List<?> packets = com.pathmind.util.UiUtilsProxy.getDelayedPackets();
        int count = packets != null ? packets.size() : 0;
        if (!com.pathmind.util.UiUtilsProxy.flushDelayedPackets(client)) {
            throw new RuntimeException("Failed to send delayed packets.");
        }
        if (notifyPlayer && count > 0 && client.player != null) {
            client.player.sendMessage(Text.of("Sent " + count + " packets."), false);
        }
    }

    private void copyGuiTitleJson(net.minecraft.client.MinecraftClient client) {
        String json = new com.google.gson.Gson().toJson(
            net.minecraft.text.TextCodecs.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, client.currentScreen.getTitle()).getOrThrow()
        );
        client.keyboard.setClipboard(json);
    }

    private void fabricateClickSlotPacket(net.minecraft.client.MinecraftClient client) {
        if (client == null || client.getNetworkHandler() == null) {
            throw new RuntimeException("Cannot send packets without a network handler.");
        }
        int syncId = parseNodeInt(owner, "SyncId", -1);
        int revision = parseNodeInt(owner, "Revision", -1);
        int slot = parseNodeInt(owner, "Slot", 0);
        int button = parseNodeInt(owner, "Button", 0);
        String actionLabel = getParameterString(owner, "Action");
        int timesToSend = Math.max(1, parseNodeInt(owner, "TimesToSend", 1));
        boolean delay = parseNodeBoolean(owner, "Delay", false);

        if (client.player == null || client.player.currentScreenHandler == null) {
            throw new RuntimeException("No active screen handler for fabricated packet.");
        }
        if (syncId < 0) {
            syncId = client.player.currentScreenHandler.syncId;
        }
        if (revision < 0) {
            revision = client.player.currentScreenHandler.getRevision();
        }

        SlotActionType action = parseSlotActionType(actionLabel);
        if (action == null) {
            throw new RuntimeException("Invalid slot action type.");
        }

        net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket packet =
            createClickSlotPacket(syncId, revision, slot, button, action);

        sendFabricatedPacket(client, packet, delay, timesToSend);
    }

    private void fabricateButtonClickPacket(net.minecraft.client.MinecraftClient client) {
        if (client == null || client.getNetworkHandler() == null) {
            throw new RuntimeException("Cannot send packets without a network handler.");
        }
        int syncId = parseNodeInt(owner, "SyncId", -1);
        int buttonId = parseNodeInt(owner, "ButtonId", 0);
        int timesToSend = Math.max(1, parseNodeInt(owner, "TimesToSend", 1));
        boolean delay = parseNodeBoolean(owner, "Delay", false);

        if (client.player == null || client.player.currentScreenHandler == null) {
            throw new RuntimeException("No active screen handler for fabricated packet.");
        }
        if (syncId < 0) {
            syncId = client.player.currentScreenHandler.syncId;
        }

        net.minecraft.network.packet.c2s.play.ButtonClickC2SPacket packet =
            new net.minecraft.network.packet.c2s.play.ButtonClickC2SPacket(syncId, buttonId);

        sendFabricatedPacket(client, packet, delay, timesToSend);
    }

    private net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket createClickSlotPacket(
        int syncId,
        int revision,
        int slot,
        int button,
        net.minecraft.screen.slot.SlotActionType action
    ) {
        net.minecraft.item.ItemStack stack = net.minecraft.item.ItemStack.EMPTY;
        it.unimi.dsi.fastutil.ints.Int2ObjectMap<net.minecraft.item.ItemStack> changed =
            new it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap<>();

        java.lang.reflect.Constructor<?>[] constructors =
            net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket.class.getConstructors();

        int[] numbers = new int[] {syncId, revision, slot, button};
        for (java.lang.reflect.Constructor<?> constructor : constructors) {
            Class<?>[] params = constructor.getParameterTypes();
            if (params.length != 7) {
                continue;
            }
            Object[] args = new Object[7];
            int numberIndex = 0;
            boolean ok = true;
            for (int i = 0; i < params.length; i++) {
                Class<?> param = params[i];
                if (param == int.class || param == Integer.class) {
                    if (numberIndex >= numbers.length) {
                        ok = false;
                        break;
                    }
                    args[i] = numbers[numberIndex++];
                } else if (param == short.class || param == Short.class) {
                    if (numberIndex >= numbers.length) {
                        ok = false;
                        break;
                    }
                    args[i] = (short) numbers[numberIndex++];
                } else if (param == byte.class || param == Byte.class) {
                    if (numberIndex >= numbers.length) {
                        ok = false;
                        break;
                    }
                    args[i] = (byte) numbers[numberIndex++];
                } else if (param.isAssignableFrom(net.minecraft.screen.slot.SlotActionType.class)) {
                    args[i] = action;
                } else if (param.isAssignableFrom(net.minecraft.item.ItemStack.class)) {
                    args[i] = stack;
                } else if (param.isAssignableFrom(it.unimi.dsi.fastutil.ints.Int2ObjectMap.class)) {
                    args[i] = changed;
                } else {
                    ok = false;
                    break;
                }
            }
            if (!ok || numberIndex != numbers.length) {
                continue;
            }
            try {
                return (net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket) constructor.newInstance(args);
            } catch (ReflectiveOperationException ignored) {
                // Try next constructor
            }
        }

        throw new RuntimeException("Unsupported ClickSlotC2SPacket constructor.");
    }

    private void sendFabricatedPacket(net.minecraft.client.MinecraftClient client, net.minecraft.network.packet.Packet<?> packet, boolean delay, int timesToSend) {
        if (client == null || client.getNetworkHandler() == null) {
            throw new RuntimeException("Cannot send packets without a network handler.");
        }
        for (int i = 0; i < timesToSend; i++) {
            client.getNetworkHandler().sendPacket(packet);
            if (!delay) {
                com.pathmind.util.UiUtilsProxy.tryWriteAndFlush(client.getNetworkHandler().getConnection(), packet);
            }
        }
    }

    private SlotActionType parseSlotActionType(String value) {
        if (value == null || value.isBlank()) {
            return SlotActionType.PICKUP;
        }
        switch (value.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "PICKUP":
                return SlotActionType.PICKUP;
            case "QUICK_MOVE":
                return SlotActionType.QUICK_MOVE;
            case "SWAP":
                return SlotActionType.SWAP;
            case "CLONE":
                return SlotActionType.CLONE;
            case "THROW":
                return SlotActionType.THROW;
            case "QUICK_CRAFT":
                return SlotActionType.QUICK_CRAFT;
            case "PICKUP_ALL":
                return SlotActionType.PICKUP_ALL;
            default:
                return null;
        }
    }

    void executePlayerGuiCommand(CompletableFuture<Void> future, NodeMode desiredMode) {
        if (preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        NodeMode playerGuiMode = desiredMode != null ? desiredMode : (mode != null ? mode : NodeMode.PLAYER_GUI_OPEN);
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();

        if (client == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        try {
            runOnClientThread(client, () -> {
                switch (playerGuiMode) {
                    case PLAYER_GUI_OPEN:
                        if (client.player == null || client.player.networkHandler == null) {
                            throw new RuntimeException("Cannot open the player GUI without an active player.");
                        }

                        client.player.networkHandler.sendPacket(new ClientCommandC2SPacket(
                                client.player,
                                ClientCommandC2SPacket.Mode.OPEN_INVENTORY
                        ));

                        if (!(client.currentScreen instanceof InventoryScreen)) {
                            client.setScreen(new InventoryScreen(client.player));
                        }
                        break;
                case PLAYER_GUI_CLOSE:
                    if (client.player == null) {
                        throw new RuntimeException("Cannot close the player GUI without an active player.");
                    }

                    Screen currentScreen = client.currentScreen;
                    if (currentScreen instanceof AbstractSignEditScreen) {
                        currentScreen.close();
                    } else if (currentScreen != null) {
                        client.player.closeHandledScreen();
                        if (client.currentScreen != null) {
                            currentScreen.close();
                        }
                    }
                    break;
                default:
                        throw new IllegalStateException("Unknown player GUI mode: " + playerGuiMode);
                }
            });
            future.complete(null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.completeExceptionally(e);
        } catch (RuntimeException e) {
            sendNodeErrorMessage(client, e.getMessage());
            future.complete(null);
        }
    }

    private Node.ParameterHandlingResult preprocessAttachedParameter(EnumSet<Node.ParameterUsage> usages, CompletableFuture<Void> future) {
        return owner.preprocessAttachedParameter(usages, future);
    }

    private void runOnClientThread(MinecraftClient client, Runnable task) throws InterruptedException {
        owner.runOnClientThread(client, task);
    }

    private void sendNodeErrorMessage(MinecraftClient client, String message) {
        owner.sendNodeErrorMessage(client, message);
    }

    private static boolean parseNodeBoolean(Node node, String name, boolean defaultValue) {
        return Node.parseNodeBoolean(node, name, defaultValue);
    }

    private static int parseNodeInt(Node node, String name, int defaultValue) {
        return Node.parseNodeInt(node, name, defaultValue);
    }

    private static String getParameterString(Node node, String name) {
        return Node.getParameterString(node, name);
    }
}
