package com.pathmind.util;

import com.pathmind.PathmindCommon;
import net.minecraft.client.MinecraftClient;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ClientMessageSender {
    private static final String FABRIC_SEND_EVENTS_CLASS = "net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents";
    private static boolean fabricBridgeWarningLogged;

    private ClientMessageSender() {
    }

    public static void send(MinecraftClient client, String message) {
        if (client == null || client.player == null || client.player.networkHandler == null || message == null) {
            return;
        }
        String trimmed = message.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        if (tryDispatchViaFabricEvents(client, trimmed)) {
            return;
        }
        sendDirect(client, trimmed);
    }

    private static boolean tryDispatchViaFabricEvents(MinecraftClient client, String message) {
        try {
            Class<?> eventsClass = Class.forName(FABRIC_SEND_EVENTS_CLASS, false, ClientMessageSender.class.getClassLoader());
            if (message.startsWith("/")) {
                String rawCommand = message.substring(1).trim();
                if (rawCommand.isEmpty()) {
                    return true;
                }
                if (!invokeBooleanEvent(eventsClass, "ALLOW_COMMAND", "allowSendCommandMessage", rawCommand)) {
                    invokeVoidEvent(eventsClass, "COMMAND_CANCELED", "onSendCommandMessageCanceled", rawCommand);
                    return true;
                }
                String modified = invokeStringEvent(eventsClass, "MODIFY_COMMAND", "modifySendCommandMessage", rawCommand);
                invokeVoidEvent(eventsClass, "COMMAND", "onSendCommandMessage", modified);
                client.player.networkHandler.sendChatCommand(modified);
                return true;
            }

            if (!invokeBooleanEvent(eventsClass, "ALLOW_CHAT", "allowSendChatMessage", message)) {
                invokeVoidEvent(eventsClass, "CHAT_CANCELED", "onSendChatMessageCanceled", message);
                return true;
            }
            String modified = invokeStringEvent(eventsClass, "MODIFY_CHAT", "modifySendChatMessage", message);
            invokeVoidEvent(eventsClass, "CHAT", "onSendChatMessage", modified);
            client.player.networkHandler.sendChatMessage(modified);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (ReflectiveOperationException | LinkageError e) {
            if (!fabricBridgeWarningLogged) {
                fabricBridgeWarningLogged = true;
                PathmindCommon.LOGGER.warn("Pathmind could not invoke Fabric chat send events; falling back to direct chat send", e);
            }
            return false;
        }
    }

    private static void sendDirect(MinecraftClient client, String message) {
        if (message.startsWith("/")) {
            String rawCommand = message.substring(1).trim();
            if (rawCommand.isEmpty()) {
                return;
            }
            FabricEventTracker.record("neoforge.client.message.send_command", "fabric.client.message.send_command");
            client.player.networkHandler.sendChatCommand(rawCommand);
            return;
        }
        FabricEventTracker.record("neoforge.client.message.send_chat", "fabric.client.message.send_chat");
        client.player.networkHandler.sendChatMessage(message);
    }

    private static boolean invokeBooleanEvent(Class<?> eventsClass, String fieldName, String methodName, String value)
        throws ReflectiveOperationException {
        Object result = invokeEvent(eventsClass, fieldName, methodName, value);
        return !(result instanceof Boolean booleanResult) || booleanResult;
    }

    private static String invokeStringEvent(Class<?> eventsClass, String fieldName, String methodName, String value)
        throws ReflectiveOperationException {
        Object result = invokeEvent(eventsClass, fieldName, methodName, value);
        return result instanceof String stringResult ? stringResult : value;
    }

    private static void invokeVoidEvent(Class<?> eventsClass, String fieldName, String methodName, String value)
        throws ReflectiveOperationException {
        invokeEvent(eventsClass, fieldName, methodName, value);
    }

    private static Object invokeEvent(Class<?> eventsClass, String fieldName, String methodName, String value)
        throws ReflectiveOperationException {
        Field field = eventsClass.getField(fieldName);
        Object event = field.get(null);
        Method invokerMethod = event.getClass().getMethod("invoker");
        Object invoker = invokerMethod.invoke(event);
        Method callbackMethod = invoker.getClass().getMethod(methodName, String.class);
        callbackMethod.setAccessible(true);
        return callbackMethod.invoke(invoker, value);
    }
}
