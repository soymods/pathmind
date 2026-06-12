package com.pathmind.marketplace;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pathmind.data.PresetManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lightweight client-side guardrails for common marketplace spam paths.
 * This is not a substitute for backend enforcement, but it blocks accidental
 * rapid-fire actions from normal clients and persists across restarts.
 * <p>
 * Publish limits are recorded only after a successful publish so failed attempts
 * (validation, duplicate name, network errors) do not consume quota.
 */
public final class MarketplaceRateLimitManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path STATE_PATH = PresetManager.getBaseDirectory().resolve("marketplace_rate_limits.json");
    private static final Object LOCK = new Object();

    private static final long PUBLISH_WINDOW_MS = 60L * 1000L;
    private static final int MAX_PUBLISHES_PER_WINDOW = 6;
    private static final long MIN_PUBLISH_GAP_MS = 10L * 1000L;
    private static final long LIKE_COOLDOWN_MS = 2500L;
    private static final long DOWNLOAD_COUNT_COOLDOWN_MS = 2500L;

    private static RateLimitState cachedState;

    private MarketplaceRateLimitManager() {
    }

    /**
     * Checks publish limits without recording an attempt. Call {@link #recordSuccessfulPublish}
     * after the server confirms a new preset was published.
     */
    public static LimitCheck validatePublish(String userId) {
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            RateLimitState state = loadState();
            String key = normalizeKey(userId, "anonymous");
            List<Long> publishTimes = state.publishTimesByUser.computeIfAbsent(key, ignored -> new ArrayList<>());
            publishTimes.removeIf(timestamp -> now - timestamp >= PUBLISH_WINDOW_MS);
            if (!publishTimes.isEmpty()) {
                long lastPublish = Collections.max(publishTimes);
                if (now - lastPublish < MIN_PUBLISH_GAP_MS) {
                    long retryAfterMs = Math.max(1000L, MIN_PUBLISH_GAP_MS - (now - lastPublish));
                    return LimitCheck.deny("Wait " + formatDuration(retryAfterMs) + " before publishing again (10 seconds between publishes).");
                }
            }
            if (publishTimes.size() >= MAX_PUBLISHES_PER_WINDOW) {
                long oldest = Collections.min(publishTimes);
                long retryAfterMs = Math.max(1000L, PUBLISH_WINDOW_MS - (now - oldest));
                return LimitCheck.deny("Publish limit reached. At most 6 per minute — try again in " + formatDuration(retryAfterMs) + ".");
            }
            return LimitCheck.permit();
        }
    }

    /** Records one successful new publish for client-side rate tracking. */
    public static void recordSuccessfulPublish(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            RateLimitState state = loadState();
            String key = normalizeKey(userId, "anonymous");
            List<Long> publishTimes = state.publishTimesByUser.computeIfAbsent(key, ignored -> new ArrayList<>());
            publishTimes.removeIf(timestamp -> now - timestamp >= PUBLISH_WINDOW_MS);
            publishTimes.add(now);
            saveState(state);
        }
    }

    public static LimitCheck tryConsumeLike(String userId, String presetId) {
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            RateLimitState state = loadState();
            String key = buildScopedKey(userId, presetId, "like");
            Long lastActionAt = state.lastLikeAtByUserPreset.get(key);
            if (lastActionAt != null && now - lastActionAt < LIKE_COOLDOWN_MS) {
                return LimitCheck.deny("Please wait " + formatDuration(LIKE_COOLDOWN_MS - (now - lastActionAt)) + " before toggling this like again.");
            }
            state.lastLikeAtByUserPreset.put(key, now);
            saveState(state);
            return LimitCheck.permit();
        }
    }

    public static LimitCheck tryConsumeDownloadCount(String userId, String presetId) {
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            RateLimitState state = loadState();
            String key = buildScopedKey(userId, presetId, "download");
            Long lastCountAt = state.lastDownloadCountAtByUserPreset.get(key);
            if (lastCountAt != null && now - lastCountAt < DOWNLOAD_COUNT_COOLDOWN_MS) {
                return LimitCheck.deny("Download already counted recently.");
            }
            state.lastDownloadCountAtByUserPreset.put(key, now);
            saveState(state);
            return LimitCheck.permit();
        }
    }

    private static RateLimitState loadState() {
        if (cachedState != null) {
            return cachedState;
        }
        if (!Files.exists(STATE_PATH)) {
            cachedState = new RateLimitState();
            return cachedState;
        }
        try {
            RateLimitState loaded = GSON.fromJson(Files.readString(STATE_PATH, StandardCharsets.UTF_8), RateLimitState.class);
            cachedState = loaded == null ? new RateLimitState() : loaded.sanitized();
        } catch (Exception ignored) {
            cachedState = new RateLimitState();
        }
        return cachedState;
    }

    private static void saveState(RateLimitState state) {
        try {
            cachedState = state.sanitized();
            Files.createDirectories(STATE_PATH.getParent());
            Files.writeString(STATE_PATH, GSON.toJson(cachedState), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static String buildScopedKey(String userId, String presetId, String fallbackScope) {
        return normalizeKey(userId, "anonymous") + "::" + normalizeKey(presetId, fallbackScope);
    }

    private static String normalizeKey(String value, String fallback) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? fallback : normalized;
    }

    private static String formatDuration(long millis) {
        long totalSeconds = Math.max(1L, (millis + 999L) / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        if (minutes <= 0L) {
            return seconds + "s";
        }
        if (seconds == 0L) {
            return minutes + "m";
        }
        return minutes + "m " + seconds + "s";
    }

    public record LimitCheck(boolean permitted, String message) {
        public static LimitCheck permit() {
            return new LimitCheck(true, "");
        }

        public static LimitCheck deny(String message) {
            return new LimitCheck(false, message == null ? "" : message);
        }
    }

    private static final class RateLimitState {
        private Map<String, List<Long>> publishTimesByUser = new HashMap<>();
        private Map<String, Long> lastLikeAtByUserPreset = new HashMap<>();
        private Map<String, Long> lastDownloadCountAtByUserPreset = new HashMap<>();

        private RateLimitState sanitized() {
            if (publishTimesByUser == null) {
                publishTimesByUser = new HashMap<>();
            }
            if (lastLikeAtByUserPreset == null) {
                lastLikeAtByUserPreset = new HashMap<>();
            }
            if (lastDownloadCountAtByUserPreset == null) {
                lastDownloadCountAtByUserPreset = new HashMap<>();
            }
            return this;
        }
    }
}
