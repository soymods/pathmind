package com.pathmind;

import com.pathmind.util.VersionSupport;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The main mod class for Pathmind.
 * This class initializes the mod and sets up event handlers.
 */
public class PathmindMod implements ModInitializer {
    public static final String MOD_ID = "pathmind";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
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
        
        // Register server tick events
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Add server-side logic here
        });
        
        LOGGER.info("Pathmind mod initialized successfully");
    }
}
