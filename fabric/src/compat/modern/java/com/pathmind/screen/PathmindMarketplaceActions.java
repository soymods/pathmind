package com.pathmind.screen;

import com.pathmind.marketplace.MarketplacePreset;
import com.pathmind.marketplace.MarketplaceService;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class PathmindMarketplaceActions {
    private static final String MARKETPLACE_MODERATOR_USER_ID = "4f1bdb60-3d3f-44ad-85ac-83f324da5e3e";

    private PathmindMarketplaceActions() {
    }

    static MarketplaceService.PublishRequest publishRequest(Path localPresetPath,
                                                            MarketplacePreset editingPreset,
                                                            String name,
                                                            String authorName,
                                                            String description,
                                                            String tags,
                                                            String gameVersion,
                                                            String pathmindVersion,
                                                            boolean published) {
        String slugSource = editingPreset != null && name.equalsIgnoreCase(fallback(editingPreset.getName(), ""))
            ? fallback(editingPreset.getSlug(), name)
            : name;
        return new MarketplaceService.PublishRequest(
            localPresetPath,
            editingPreset == null ? null : editingPreset.getStorageBucket(),
            editingPreset == null ? null : editingPreset.getFilePath(),
            sanitizeSlug(slugSource),
            name,
            authorName,
            fallback(description, ""),
            parseTags(tags),
            gameVersion,
            pathmindVersion,
            published
        );
    }

    static MarketplaceService.PublishRequest updateFromLocalRequest(Path localPresetPath, MarketplacePreset preset) {
        return new MarketplaceService.PublishRequest(
            localPresetPath,
            preset.getStorageBucket(),
            preset.getFilePath(),
            fallback(preset.getSlug(), sanitizeSlug(preset.getName())),
            preset.getName(),
            preset.getAuthorName(),
            preset.getDescription(),
            preset.getTags(),
            preset.getGameVersion(),
            preset.getPathmindVersion(),
            preset.isPublished()
        );
    }

    static boolean canManagePreset(MarketplacePreset preset, String currentUserId, boolean moderator) {
        return isOwnPreset(preset, currentUserId) || moderator || isKnownMarketplaceModerator(currentUserId);
    }

    static boolean isOwnPreset(MarketplacePreset preset, String currentUserId) {
        if (preset == null || currentUserId == null || currentUserId.isBlank()) {
            return false;
        }
        String presetAuthorUserId = fallback(preset.getAuthorUserId(), "");
        String normalizedCurrentUserId = fallback(currentUserId, "");
        return !presetAuthorUserId.isBlank() && presetAuthorUserId.equals(normalizedCurrentUserId);
    }

    static boolean isKnownMarketplaceModerator(String userId) {
        return userId != null && MARKETPLACE_MODERATOR_USER_ID.equalsIgnoreCase(userId.trim());
    }

    static List<MarketplacePreset> removePresetFromList(List<MarketplacePreset> source, String presetId) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<MarketplacePreset> updated = new ArrayList<>(source.size());
        for (MarketplacePreset preset : source) {
            if (preset == null || presetId.equals(preset.getId())) {
                continue;
            }
            updated.add(preset);
        }
        return List.copyOf(updated);
    }

    static List<MarketplacePreset> upsertPresetList(List<MarketplacePreset> source, MarketplacePreset preset) {
        List<MarketplacePreset> updated = new ArrayList<>(source == null ? List.of() : source);
        boolean replaced = false;
        for (int i = 0; i < updated.size(); i++) {
            MarketplacePreset candidate = updated.get(i);
            if (candidate != null && preset.getId().equals(candidate.getId())) {
                updated.set(i, preset);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            updated.add(0, preset);
        }
        return dedupePresetsById(updated);
    }

    static List<MarketplacePreset> updatePresetCountList(List<MarketplacePreset> source, String presetId, int likesDelta, int downloadsDelta) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<MarketplacePreset> updated = new ArrayList<>(source.size());
        for (MarketplacePreset preset : source) {
            updated.add(presetId.equals(preset.getId()) ? updatedPresetCounts(preset, likesDelta, downloadsDelta) : preset);
        }
        return List.copyOf(updated);
    }

    static MarketplacePreset updatedPresetCounts(MarketplacePreset preset, int likesDelta, int downloadsDelta) {
        return new MarketplacePreset(
            preset.getId(),
            preset.getSlug(),
            preset.getAuthorUserId(),
            preset.getName(),
            preset.getAuthorName(),
            preset.getAuthorAvatarUrl(),
            preset.getDescription(),
            preset.getTags(),
            preset.getGameVersion(),
            preset.getPathmindVersion(),
            Math.max(0, preset.getLikesCount() + likesDelta),
            Math.max(0, preset.getDownloadsCount() + downloadsDelta),
            preset.getStorageBucket(),
            preset.getFilePath(),
            preset.isPublished(),
            preset.getCreatedAt(),
            preset.getUpdatedAt()
        );
    }

    static List<MarketplacePreset> dedupePresetsById(List<MarketplacePreset> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        Map<String, MarketplacePreset> presetsById = new LinkedHashMap<>();
        List<MarketplacePreset> presetsWithoutId = new ArrayList<>();
        for (MarketplacePreset preset : source) {
            if (preset == null) {
                continue;
            }
            String presetId = normalizePresetId(preset.getId());
            if (presetId.isEmpty()) {
                presetsWithoutId.add(preset);
            } else {
                presetsById.putIfAbsent(presetId, preset);
            }
        }
        List<MarketplacePreset> deduped = new ArrayList<>(presetsById.size() + presetsWithoutId.size());
        deduped.addAll(presetsById.values());
        deduped.addAll(presetsWithoutId);
        return List.copyOf(deduped);
    }

    static String extractThrowableMessage(Throwable throwable, String fallbackMessage) {
        if (throwable == null) {
            return fallbackMessage;
        }
        Throwable leaf = throwable;
        while (leaf.getCause() != null && leaf.getCause() != leaf) {
            leaf = leaf.getCause();
        }
        String deepest = leaf.getMessage();
        if (deepest != null && !deepest.isBlank()) {
            return deepest;
        }
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                return message;
            }
            current = current.getCause();
        }
        return fallbackMessage;
    }

    static List<String> parseTags(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> tags = new ArrayList<>();
        for (String token : value.split(",")) {
            String normalized = token == null ? "" : token.trim();
            if (!normalized.isEmpty() && !tags.contains(normalized)) {
                tags.add(normalized);
            }
        }
        return List.copyOf(tags);
    }

    static String sanitizeSlug(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-+|-+$)", "");
    }

    static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String normalizePresetId(String presetId) {
        return presetId == null ? "" : presetId.trim().toLowerCase(Locale.ROOT);
    }
}
