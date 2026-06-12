package com.pathmind;

import com.pathmind.util.VersionSupport;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;

public class PathmindMod implements ModInitializer {
    public static final String MOD_ID = PathmindCommon.MOD_ID;
    public static final Logger LOGGER = PathmindCommon.LOGGER;

    public static Identifier id(String path) {
        return PathmindCommon.id(path);
    }

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Pathmind mod");

        String minecraftVersion = FabricLoader.getInstance()
            .getModContainer("minecraft")
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown");
        if (!VersionSupport.isSupported(minecraftVersion)) {
            LOGGER.warn("Pathmind targets Minecraft {} but detected {}", VersionSupport.SUPPORTED_RANGE, minecraftVersion);
        }

        LOGGER.info("Pathmind mod initialized successfully");
    }
}
