package com.pathmind.screen;

import com.pathmind.marketplace.MarketplaceAuthManager;
import com.pathmind.marketplace.MarketplacePreset;
import com.pathmind.marketplace.MarketplaceService;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;

final class PathmindMarketplaceAsyncController {
    private PathmindMarketplaceAsyncController() {
    }

    static void fetchListings(Minecraft client,
                              boolean manageable,
                              String accessToken,
                              MarketplaceService.ListingMode listingMode,
                              BiConsumer<List<MarketplacePreset>, Throwable> callback) {
        CompletableFuture<List<MarketplacePreset>> request = manageable
            ? MarketplaceService.fetchManageablePresets(accessToken)
            : MarketplaceService.fetchPublishedPresets(listingMode);
        onClientThread(client, request, callback);
    }

    static void ensureValidSession(Minecraft client, BiConsumer<MarketplaceAuthManager.AuthSession, Throwable> callback) {
        onClientThread(client, MarketplaceAuthManager.ensureValidSession(), callback);
    }

    static void startDiscordSignIn(Minecraft client, BiConsumer<MarketplaceAuthManager.AuthSession, Throwable> callback) {
        onClientThread(client, MarketplaceAuthManager.startDiscordSignIn(), callback);
    }

    static void signOut(Minecraft client, BiConsumer<Void, Throwable> callback) {
        onClientThread(client, MarketplaceAuthManager.signOut(), callback);
    }

    static void fetchLikedPresetIds(Minecraft client,
                                    String accessToken,
                                    String userId,
                                    BiConsumer<Set<String>, Throwable> callback) {
        onClientThread(client, MarketplaceService.fetchLikedPresetIds(accessToken, userId), callback);
    }

    static void fetchModeratorStatus(Minecraft client,
                                     String accessToken,
                                     String userId,
                                     BiConsumer<Boolean, Throwable> callback) {
        onClientThread(client, MarketplaceService.fetchMarketplaceModeratorStatus(accessToken, userId), callback);
    }

    static void fetchPresetById(Minecraft client,
                                String accessToken,
                                String presetId,
                                BiConsumer<MarketplacePreset, Throwable> callback) {
        onClientThread(client, MarketplaceService.fetchPresetById(accessToken, presetId), callback);
    }

    static void downloadPresetToTempFile(Minecraft client,
                                         MarketplacePreset preset,
                                         String accessToken,
                                         BiConsumer<Path, Throwable> callback) {
        onClientThread(client, MarketplaceService.downloadPresetToTempFile(preset, accessToken), callback);
    }

    static void incrementDownload(Minecraft client,
                                  String accessToken,
                                  String presetId,
                                  BiConsumer<Void, Throwable> callback) {
        onClientThread(client, MarketplaceService.incrementDownload(accessToken, presetId), callback);
    }

    static void publishPreset(Minecraft client,
                              String accessToken,
                              String userId,
                              MarketplaceService.PublishRequest request,
                              BiConsumer<MarketplacePreset, Throwable> callback) {
        onClientThread(client, MarketplaceService.publishPreset(accessToken, userId, request), callback);
    }

    static void updatePresetMetadata(Minecraft client,
                                     String accessToken,
                                     MarketplacePreset preset,
                                     MarketplaceService.PublishRequest request,
                                     BiConsumer<MarketplacePreset, Throwable> callback) {
        onClientThread(client, MarketplaceService.updatePresetMetadata(accessToken, preset, request), callback);
    }

    static void toggleLike(Minecraft client,
                           String accessToken,
                           String presetId,
                           String userId,
                           BiConsumer<Boolean, Throwable> callback) {
        onClientThread(client, MarketplaceService.toggleLike(accessToken, presetId, userId), callback);
    }

    static void deletePreset(Minecraft client,
                             String accessToken,
                             MarketplacePreset preset,
                             BiConsumer<Void, Throwable> callback) {
        onClientThread(client, MarketplaceService.deletePreset(accessToken, preset.getId(), preset.getStorageBucket(), preset.getFilePath()), callback);
    }

    private static <T> void onClientThread(Minecraft client,
                                           CompletableFuture<T> future,
                                           BiConsumer<T, Throwable> callback) {
        future.whenComplete((value, throwable) -> runOnClientThread(client, () -> callback.accept(value, throwable)));
    }

    private static void runOnClientThread(Minecraft client, Runnable runnable) {
        if (client == null) {
            return;
        }
        client.execute(runnable);
    }
}
