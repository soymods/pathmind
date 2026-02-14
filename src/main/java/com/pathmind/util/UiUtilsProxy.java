package com.pathmind.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.screen.ScreenHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public final class UiUtilsProxy {
    private static final Logger LOGGER = LoggerFactory.getLogger("Pathmind/UiUtils");
    private static final String SHARED_VARIABLES_CLASS = "com.ui_utils.SharedVariables";
    private static final String COMMAND_SYSTEM_CLASS = "com.mrbreaknfix.ui_utils.command.CommandSystem";

    private static volatile boolean initialized;
    private static Backend backend = Backend.NONE;
    private static Class<?> sharedVariablesClass;
    private static Field sendUIPacketsField;
    private static Field delayUIPacketsField;
    private static Field shouldEditSignField;
    private static Field delayedUIPacketsField;
    private static Field storedScreenField;
    private static Field storedScreenHandlerField;
    private static Field enabledField;
    private static Field bypassResourcePackField;
    private static Field resourcePackForceDenyField;
    private static Method executeCommandMethod;
    private static Field overlayField;
    private static Method overlaySetEnabledMethod;
    private static Field overlayEnabledField;

    private UiUtilsProxy() {
    }

    public static boolean isAvailable() {
        return UiUtilsDependencyChecker.isUiUtilsPresent() && init();
    }

    public static boolean isLegacyBackend() {
        return backend == Backend.LEGACY;
    }

    public static boolean isModernBackend() {
        return backend == Backend.MODERN;
    }

    private static boolean init() {
        if (initialized) {
            return backend != Backend.NONE;
        }
        initialized = true;
        try {
            sharedVariablesClass = Class.forName(SHARED_VARIABLES_CLASS, false, UiUtilsProxy.class.getClassLoader());
            sendUIPacketsField = sharedVariablesClass.getField("sendUIPackets");
            delayUIPacketsField = sharedVariablesClass.getField("delayUIPackets");
            shouldEditSignField = sharedVariablesClass.getField("shouldEditSign");
            delayedUIPacketsField = sharedVariablesClass.getField("delayedUIPackets");
            storedScreenField = sharedVariablesClass.getField("storedScreen");
            storedScreenHandlerField = sharedVariablesClass.getField("storedScreenHandler");
            enabledField = sharedVariablesClass.getField("enabled");
            bypassResourcePackField = sharedVariablesClass.getField("bypassResourcePack");
            resourcePackForceDenyField = sharedVariablesClass.getField("resourcePackForceDeny");
            backend = Backend.LEGACY;
            return true;
        } catch (ReflectiveOperationException e) {
            LOGGER.debug("Legacy UI Utils classes not found: {}", e.getMessage());
        } catch (Throwable t) {
            LOGGER.debug("Unexpected error while initializing legacy UI Utils proxy", t);
        }

        try {
            Class<?> commandSystem = Class.forName(COMMAND_SYSTEM_CLASS, false, UiUtilsProxy.class.getClassLoader());
            executeCommandMethod = commandSystem.getMethod("executeCommand", String.class);
            backend = Backend.MODERN;
            initOverlayAccess();
            return true;
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            LOGGER.debug("UI Utils modern backend unavailable: {}", e.getMessage());
            UiUtilsDependencyChecker.markUnavailable();
            backend = Backend.NONE;
            return false;
        } catch (LinkageError e) {
            LOGGER.debug("UI Utils modern backend linkage failure, disabling integration", e);
            UiUtilsDependencyChecker.markUnavailable();
            backend = Backend.NONE;
            return false;
        } catch (Throwable t) {
            LOGGER.debug("Failed to initialize UI Utils proxy, disabling integration", t);
            UiUtilsDependencyChecker.markUnavailable();
            backend = Backend.NONE;
            return false;
        }
    }

    private static void initOverlayAccess() {
        try {
            Class<?> uiUtilsClass = Class.forName("com.mrbreaknfix.ui_utils.UiUtils", false, UiUtilsProxy.class.getClassLoader());
            overlayField = uiUtilsClass.getField("overlay");
            Object overlay = overlayField.get(null);
            if (overlay != null) {
                overlaySetEnabledMethod = overlay.getClass().getMethod("setEnabled", boolean.class);
            }
        } catch (ReflectiveOperationException ignored) {
            // Optional compatibility only.
        }
        if (overlaySetEnabledMethod == null) {
            try {
                Class<?> baseOverlayClass = Class.forName("com.mrbreaknfix.ui_utils.gui.BaseOverlay", false, UiUtilsProxy.class.getClassLoader());
                overlayEnabledField = baseOverlayClass.getField("enabled");
            } catch (ReflectiveOperationException ignored) {
                // Optional compatibility only.
            }
        }
    }

    public static Boolean setOverlayEnabled(boolean enabled) {
        if (!init()) {
            return null;
        }
        try {
            if (overlayField == null) {
                initOverlayAccess();
            }
            if (overlayField == null) {
                return null;
            }
            Object overlay = overlayField.get(null);
            if (overlay == null) {
                return null;
            }
            Boolean previous = getOverlayEnabled(overlay);
            if (overlaySetEnabledMethod != null) {
                overlaySetEnabledMethod.invoke(overlay, enabled);
                return previous;
            }
            if (overlayEnabledField != null) {
                overlayEnabledField.setBoolean(overlay, enabled);
                return previous;
            }
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
        return null;
    }

    private static Boolean getOverlayEnabled(Object overlay) {
        try {
            if (overlayEnabledField != null) {
                return overlayEnabledField.getBoolean(overlay);
            }
            Method getter = overlay.getClass().getMethod("isEnabled");
            Object value = getter.invoke(overlay);
            return value instanceof Boolean ? (Boolean) value : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    public static Boolean getSendPackets() {
        return getBooleanField(sendUIPacketsField);
    }

    public static Boolean getDelayPackets() {
        return getBooleanField(delayUIPacketsField);
    }

    public static boolean setSendPackets(boolean value) {
        return setBooleanField(sendUIPacketsField, value);
    }

    public static boolean setDelayPackets(boolean value) {
        return setBooleanField(delayUIPacketsField, value);
    }

    public static boolean setShouldEditSign(boolean value) {
        return setBooleanField(shouldEditSignField, value);
    }

    public static boolean setEnabled(boolean value) {
        return setBooleanField(enabledField, value);
    }

    public static boolean setBypassResourcePack(boolean value) {
        return setBooleanField(bypassResourcePackField, value);
    }

    public static boolean setResourcePackForceDeny(boolean value) {
        return setBooleanField(resourcePackForceDenyField, value);
    }

    public static boolean executeCommand(String command) {
        if (!init() || backend != Backend.MODERN || executeCommandMethod == null || command == null) {
            return false;
        }
        try {
            executeCommandMethod.invoke(null, command);
            return true;
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("Failed to execute UI Utils command: {}", e.getMessage());
            return false;
        } catch (Throwable t) {
            LOGGER.warn("Unexpected error while executing UI Utils command", t);
            return false;
        }
    }

    public static List<?> getDelayedPackets() {
        if (!init() || backend != Backend.LEGACY) {
            return null;
        }
        try {
            Object value = delayedUIPacketsField.get(null);
            return value instanceof List<?> ? (List<?>) value : null;
        } catch (IllegalAccessException e) {
            LOGGER.warn("Failed to access UI Utils delayed packets: {}", e.getMessage());
            return null;
        }
    }

    public static boolean setStoredScreen(Screen screen, ScreenHandler handler) {
        if (!init() || backend != Backend.LEGACY) {
            return false;
        }
        try {
            storedScreenField.set(null, screen);
            storedScreenHandlerField.set(null, handler);
            return true;
        } catch (IllegalAccessException e) {
            LOGGER.warn("Failed to set UI Utils stored screen: {}", e.getMessage());
            return false;
        }
    }

    public static Screen getStoredScreen() {
        if (!init() || backend != Backend.LEGACY) {
            return null;
        }
        try {
            Object value = storedScreenField.get(null);
            return value instanceof Screen ? (Screen) value : null;
        } catch (IllegalAccessException e) {
            LOGGER.warn("Failed to access UI Utils stored screen: {}", e.getMessage());
            return null;
        }
    }

    public static ScreenHandler getStoredScreenHandler() {
        if (!init() || backend != Backend.LEGACY) {
            return null;
        }
        try {
            Object value = storedScreenHandlerField.get(null);
            return value instanceof ScreenHandler ? (ScreenHandler) value : null;
        } catch (IllegalAccessException e) {
            LOGGER.warn("Failed to access UI Utils stored screen handler: {}", e.getMessage());
            return null;
        }
    }

    public static boolean flushDelayedPackets(MinecraftClient client) {
        if (backend != Backend.LEGACY || client == null || client.getNetworkHandler() == null) {
            return false;
        }
        List<?> packets = getDelayedPackets();
        if (packets == null || packets.isEmpty()) {
            return true;
        }
        for (Object packet : new java.util.ArrayList<>(packets)) {
            if (packet instanceof Packet<?> cast) {
                client.getNetworkHandler().sendPacket(cast);
            }
        }
        packets.clear();
        return true;
    }

    public static boolean tryWriteAndFlush(ClientConnection connection, Packet<?> packet) {
        if (connection == null || packet == null) {
            return false;
        }
        try {
            Method getChannel = connection.getClass().getMethod("getChannel");
            Object channel = getChannel.invoke(connection);
            if (channel == null) {
                return false;
            }
            Method writeAndFlush = channel.getClass().getMethod("writeAndFlush", Object.class);
            writeAndFlush.invoke(channel, packet);
            return true;
        } catch (ReflectiveOperationException e) {
            LOGGER.debug("UI Utils channel write unavailable: {}", e.getMessage());
            return false;
        } catch (Throwable t) {
            LOGGER.debug("Unexpected error while writing UI Utils packet", t);
            return false;
        }
    }

    private static Boolean getBooleanField(Field field) {
        if (!init() || backend != Backend.LEGACY || field == null) {
            return null;
        }
        try {
            return field.getBoolean(null);
        } catch (IllegalAccessException e) {
            LOGGER.warn("Failed to read UI Utils boolean field: {}", e.getMessage());
            return null;
        }
    }

    private static boolean setBooleanField(Field field, boolean value) {
        if (!init() || backend != Backend.LEGACY || field == null) {
            return false;
        }
        try {
            field.setBoolean(null, value);
            return true;
        } catch (IllegalAccessException e) {
            LOGGER.warn("Failed to update UI Utils boolean field: {}", e.getMessage());
            return false;
        }
    }

    private enum Backend {
        NONE,
        LEGACY,
        MODERN
    }
}
