package com.pathmind.screen;

import com.pathmind.marketplace.MarketplaceAuthManager;
import com.pathmind.marketplace.MarketplacePreset;
import com.pathmind.marketplace.MarketplaceRateLimitManager;
import com.pathmind.marketplace.MarketplaceService;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

/**
 * Shared marketplace workflows return structured results; screens still own UI state and navigation.
 */
final class PathmindMarketplaceFlowController {
    private PathmindMarketplaceFlowController() {
    }

    static void resolveLinkedPreset(Minecraft client,
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

    static void submitPublish(Minecraft client,
                              MarketplacePreset editingPreset,
                              MarketplaceService.PublishRequest request,
                              Consumer<PublishResult> callback) {
        PathmindMarketplaceAsyncController.ensureValidSession(client, (session, sessionThrowable) -> {
            if (sessionThrowable != null || session == null || session.getAccessToken() == null || session.getAccessToken().isBlank()) {
                callback.accept(PublishResult.sessionExpired(sessionThrowable));
                return;
            }

            if (editingPreset == null) {
                MarketplaceRateLimitManager.LimitCheck limitCheck = MarketplaceRateLimitManager.validatePublish(session.getUserId());
                if (!limitCheck.permitted()) {
                    callback.accept(PublishResult.rateLimited(session, limitCheck.message()));
                    return;
                }
            }

            if (editingPreset == null) {
                PathmindMarketplaceAsyncController.publishPreset(client, session.getAccessToken(), session.getUserId(), request, (preset, throwable) ->
                    callback.accept(PublishResult.completed(session, preset, throwable, false))
                );
            } else {
                PathmindMarketplaceAsyncController.updatePresetMetadata(client, session.getAccessToken(), editingPreset, request, (preset, throwable) ->
                    callback.accept(PublishResult.completed(session, preset, throwable, true))
                );
            }
        });
    }

    static void submitPresetUpdate(Minecraft client,
                                   MarketplacePreset preset,
                                   MarketplaceService.PublishRequest request,
                                   Consumer<PublishResult> callback) {
        submitPublish(client, preset, request, callback);
    }

    enum LinkedPresetStatus {
        MISSING_LOCAL_LINK,
        SESSION_EXPIRED,
        FOUND,
        NOT_FOUND
    }

    enum PublishStatus {
        SESSION_EXPIRED,
        RATE_LIMITED,
        COMPLETED
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

    static final class PublishResult {
        private final PublishStatus status;
        private final MarketplaceAuthManager.AuthSession session;
        private final MarketplacePreset preset;
        private final Throwable throwable;
        private final String limitMessage;

        private PublishResult(PublishStatus status,
                              MarketplaceAuthManager.AuthSession session,
                              MarketplacePreset preset,
                              Throwable throwable,
                              String limitMessage) {
            this.status = status;
            this.session = session;
            this.preset = preset;
            this.throwable = throwable;
            this.limitMessage = limitMessage;
        }

        static PublishResult sessionExpired(Throwable throwable) {
            return new PublishResult(PublishStatus.SESSION_EXPIRED, null, null, throwable, null);
        }

        static PublishResult rateLimited(MarketplaceAuthManager.AuthSession session, String limitMessage) {
            return new PublishResult(PublishStatus.RATE_LIMITED, session, null, null, limitMessage);
        }

        static PublishResult completed(MarketplaceAuthManager.AuthSession session,
                                       MarketplacePreset preset,
                                       Throwable throwable,
                                       boolean editing) {
            if (throwable == null && !editing && preset != null && session != null && session.getUserId() != null && !session.getUserId().isBlank()) {
                MarketplaceRateLimitManager.recordSuccessfulPublish(session.getUserId());
            }
            return new PublishResult(PublishStatus.COMPLETED, session, preset, throwable, null);
        }

        PublishStatus status() {
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

        String limitMessage() {
            return limitMessage;
        }
    }
}
