package com.pathmind.util;

import java.lang.reflect.Method;
import java.util.Optional;

public final class LoaderMetadata {
    private LoaderMetadata() {
    }

    public static String getLoaderName() {
        if (isClassPresent("net.neoforged.fml.ModList")) {
            return "NeoForge";
        }
        if (isClassPresent("net.fabricmc.loader.api.FabricLoader")) {
            return "Fabric Loader";
        }
        return "Loader";
    }

    public static boolean isNeoForge() {
        return isClassPresent("net.neoforged.fml.ModList");
    }

    public static String getLoaderVersion() {
        if (isClassPresent("net.neoforged.fml.ModList")) {
            return getNeoForgeModVersion("neoforge");
        }
        return getFabricModVersion("fabricloader");
    }

    public static String getModVersion(String modId) {
        String neoForgeVersion = getNeoForgeModVersion(modId);
        if (!"Unknown".equals(neoForgeVersion)) {
            return neoForgeVersion;
        }
        return getFabricModVersion(modId);
    }

    private static String getFabricModVersion(String modId) {
        try {
            Class<?> loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object loader = loaderClass.getMethod("getInstance").invoke(null);
            Object optionalContainer = loaderClass.getMethod("getModContainer", String.class).invoke(loader, modId);
            Object container = optionalValue(optionalContainer).orElse(null);
            if (container == null) {
                return "Unknown";
            }

            Object metadata = container.getClass().getMethod("getMetadata").invoke(container);
            Object version = metadata.getClass().getMethod("getVersion").invoke(metadata);
            return String.valueOf(version.getClass().getMethod("getFriendlyString").invoke(version));
        } catch (ReflectiveOperationException | LinkageError e) {
            return "Unknown";
        }
    }

    private static String getNeoForgeModVersion(String modId) {
        try {
            Class<?> modListClass = Class.forName("net.neoforged.fml.ModList");
            Object modList = modListClass.getMethod("get").invoke(null);
            Object optionalContainer = modListClass.getMethod("getModContainerById", String.class).invoke(modList, modId);
            Object container = optionalValue(optionalContainer).orElse(null);
            if (container == null) {
                return "Unknown";
            }

            Object modInfo = container.getClass().getMethod("getModInfo").invoke(container);
            Object version = modInfo.getClass().getMethod("getVersion").invoke(modInfo);
            return String.valueOf(version);
        } catch (ReflectiveOperationException | LinkageError e) {
            return "Unknown";
        }
    }

    private static Optional<?> optionalValue(Object value) {
        return value instanceof Optional<?> optional ? optional : Optional.empty();
    }

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, LoaderMetadata.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
