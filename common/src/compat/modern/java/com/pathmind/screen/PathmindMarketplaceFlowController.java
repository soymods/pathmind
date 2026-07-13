package com.pathmind.screen;

import com.pathmind.marketplace.MarketplaceAuthManager;
import com.pathmind.marketplace.MarketplacePreset;
import net.minecraft.client.MinecraftClient;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Shared marketplace workflows return structured results; screens still own UI state and navigation.
 */
final class PathmindMarketplaceFlowController {
    private PathmindMarketplaceFlowController() {
    }

    static void resolveLinkedPreset(MinecraftClient client,
                                    MarketplaceAuthManager.AuthSession cachedSession,
                                    Optional<String> linkedPresetId,
                                    Consumer<LinkedPresetResult> callback) {
        if (cachedSession == null || linkedPresetId == null || linkedPresetId.isEmpty()) {
            callback.accept(LinkedPresetResult.missingLocalLink());
            return;
        }

        PathmindMarketplaceAsyncController.ensureValidSession(client, (session, sessionThrowable) -> {
            if (sessionThrowable != null || session == null) {
                callback.accept(LinkedPresetResult.sessionExpired(sessionThrowable));
                return;
            }

            PathmindMarketplaceAsyncController.fetchPresetById(client, session.getAccessToken(), linkedPresetId.get(), (preset, throwable) -> {
                if (throwable == null && preset != null) {
                    callback.accept(LinkedPresetResult.found(session, preset));
                    return;
                }
                callback.accept(LinkedPresetResult.notFound(session, throwable));
            });
        });
    }

    enum LinkedPresetStatus {
        MISSING_LOCAL_LINK,
        SESSION_EXPIRED,
        FOUND,
        NOT_FOUND
    }

    static final class LinkedPresetResult {
        private final LinkedPresetStatus status;
        private final MarketplaceAuthManager.AuthSession session;
        private final MarketplacePreset preset;
        private final Throwable throwable;

        private LinkedPresetResult(LinkedPresetStatus status,
                                   MarketplaceAuthManager.AuthSession session,
                                   MarketplacePreset preset,
                                   Throwable throwable) {
            this.status = status;
            this.session = session;
            this.preset = preset;
            this.throwable = throwable;
        }

        static LinkedPresetResult missingLocalLink() {
            return new LinkedPresetResult(LinkedPresetStatus.MISSING_LOCAL_LINK, null, null, null);
        }

        static LinkedPresetResult sessionExpired(Throwable throwable) {
            return new LinkedPresetResult(LinkedPresetStatus.SESSION_EXPIRED, null, null, throwable);
        }

        static LinkedPresetResult found(MarketplaceAuthManager.AuthSession session, MarketplacePreset preset) {
            return new LinkedPresetResult(LinkedPresetStatus.FOUND, session, preset, null);
        }

        static LinkedPresetResult notFound(MarketplaceAuthManager.AuthSession session, Throwable throwable) {
            return new LinkedPresetResult(LinkedPresetStatus.NOT_FOUND, session, null, throwable);
        }

        LinkedPresetStatus status() {
            return status;
        }

        MarketplaceAuthManager.AuthSession session() {
            return session;
        }

        MarketplacePreset preset() {
            return preset;
        }

        Throwable throwable() {
            return throwable;
        }
    }
}
