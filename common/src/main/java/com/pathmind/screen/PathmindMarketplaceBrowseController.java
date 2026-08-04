package com.pathmind.screen;

import com.pathmind.marketplace.MarketplaceAuthManager;
import com.pathmind.marketplace.MarketplacePreset;
import com.pathmind.marketplace.MarketplaceService;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Owns marketplace filtering, sorting, author grouping, and result selection state updates. */
final class PathmindMarketplaceBrowseController {
    interface Host {
        List<MarketplacePreset> allPresets();
        boolean myPresetsOnly();
        MyPresetsFilter myPresetsFilter();
        SortMode sortMode();
        boolean canManagePreset(MarketplacePreset preset);
        boolean isPresetSavedLocally(MarketplacePreset preset);
        boolean isViewingAuthorProfile();
        String viewedAuthorKey();
        MarketplaceAuthManager.AuthSession authSession();
        int pageIndex();
        int selectedIndex();
        int maxPageIndex();
        void setPresets(List<MarketplacePreset> presets);
        void setAuthorResults(List<AuthorSummary> authorResults);
        void setPageIndex(int pageIndex);
        void setSelectedIndex(int selectedIndex);
        void setStatusMessage(String statusMessage);
    }

    private final Host host;

    PathmindMarketplaceBrowseController(Host host) {
        this.host = host;
    }

    void applyFilters(String searchValue) {
        String query = normalizeSearch(searchValue);
        List<MarketplacePreset> filtered = new ArrayList<>();
        for (MarketplacePreset preset : host.allPresets()) {
            if (!host.myPresetsOnly() && !preset.isPublished()) {
                continue;
            }
            if (host.myPresetsOnly() && !host.canManagePreset(preset)) {
                continue;
            }
            if (host.myPresetsOnly() && !host.myPresetsFilter().matches(preset)) {
                continue;
            }
            if (!host.sortMode().matches(host, preset)) {
                continue;
            }
            if (host.isViewingAuthorProfile() && (!preset.isPublished() || !isViewedAuthorPreset(preset))) {
                continue;
            }
            boolean matches = isAuthorDirectoryMode()
                ? query.isEmpty() || containsNormalized(preset.getAuthorName(), query)
                : query.isEmpty() || matchesQuery(preset, query);
            if (matches) {
                filtered.add(preset);
            }
        }
        filtered.sort(host.sortMode().comparator);
        List<MarketplacePreset> presets = PathmindMarketplaceActions.dedupePresetsById(filtered);
        List<AuthorSummary> authorResults = buildAuthorResults(filtered);
        host.setPresets(presets);
        host.setAuthorResults(authorResults);
        host.setPageIndex(Math.max(0, Math.min(host.pageIndex(), host.maxPageIndex())));
        int currentCount = isAuthorDirectoryMode() ? authorResults.size() : presets.size();
        host.setSelectedIndex(currentCount == 0 ? -1 : Math.max(0, Math.min(host.selectedIndex(), currentCount - 1)));
        if (host.myPresetsOnly() && host.authSession() == null) {
            host.setStatusMessage(Component.translatable("pathmind.status.signInViewPresets").getString());
        } else if (isAuthorDirectoryMode() && authorResults.isEmpty()) {
            host.setStatusMessage(query.isEmpty() ? Component.translatable("pathmind.status.noAuthorsPublic").getString() : Component.translatable("pathmind.status.noAuthorsSearch").getString());
        } else if (host.isViewingAuthorProfile() && presets.isEmpty()) {
            host.setStatusMessage(Component.translatable("pathmind.status.noCreatorPresets").getString());
        } else if (host.allPresets().isEmpty()) {
            host.setStatusMessage(host.myPresetsOnly() ? Component.translatable("pathmind.status.noCloudPresets").getString() : Component.translatable("pathmind.status.noPublishedPresets").getString());
        } else if (presets.isEmpty()) {
            if (host.myPresetsOnly()) {
                host.setStatusMessage(switch (host.myPresetsFilter()) {
                    case PUBLIC -> Component.translatable("pathmind.status.noPublicSearch").getString();
                    case PRIVATE -> Component.translatable("pathmind.status.noPrivateSearch").getString();
                    default -> Component.translatable("pathmind.status.noPresetsSearch").getString();
                });
            } else {
                host.setStatusMessage(host.sortMode() == SortMode.SAVED ? Component.translatable("pathmind.status.noSavedSearch").getString() : Component.translatable("pathmind.status.noPresetsSearch").getString());
            }
        } else if (isAuthorDirectoryMode()) {
            host.setStatusMessage(Component.translatable("pathmind.status.loadedAuthors", authorResults.size(), authorResults.size() == 1 ? "" : "s").getString());
        } else {
            host.setStatusMessage(translatedCount("pathmind.status.loadedPresets", presets.size()));
        }
    }

    private List<AuthorSummary> buildAuthorResults(List<MarketplacePreset> filtered) {
        if (filtered == null || filtered.isEmpty()) {
            return List.of();
        }
        Map<String, AuthorAccumulator> authors = new LinkedHashMap<>();
        for (MarketplacePreset preset : filtered) {
            if (preset == null || !preset.isPublished()) {
                continue;
            }
            String key = buildAuthorKey(preset);
            if (key == null || key.isBlank()) {
                continue;
            }
            AuthorAccumulator accumulator = authors.computeIfAbsent(key, ignored -> new AuthorAccumulator(
                key,
                fallback(preset.getAuthorName(), Component.translatable("pathmind.marketplace.unknown").getString()),
                fallback(preset.getAuthorAvatarUrl(), ""),
                preset
            ));
            accumulator.presetCount++;
            accumulator.totalLikes += Math.max(0, preset.getLikesCount());
            accumulator.totalDownloads += Math.max(0, preset.getDownloadsCount());
            if ((accumulator.avatarUrl == null || accumulator.avatarUrl.isBlank())
                && preset.getAuthorAvatarUrl() != null && !preset.getAuthorAvatarUrl().isBlank()) {
                accumulator.avatarUrl = preset.getAuthorAvatarUrl();
            }
        }
        List<AuthorSummary> summaries = new ArrayList<>(authors.size());
        for (AuthorAccumulator accumulator : authors.values()) {
            summaries.add(new AuthorSummary(
                accumulator.key,
                accumulator.displayName,
                accumulator.avatarUrl,
                accumulator.presetCount,
                accumulator.totalLikes,
                accumulator.totalDownloads,
                accumulator.representativePreset
            ));
        }
        summaries.sort(Comparator.comparing((AuthorSummary author) -> normalizeSearch(author.displayName())));
        return List.copyOf(summaries);
    }

    private boolean matchesQuery(MarketplacePreset preset, String query) {
        if (containsNormalized(preset.getName(), query)
            || containsNormalized(preset.getSlug(), query)
            || containsNormalized(preset.getAuthorName(), query)
            || containsNormalized(preset.getDescription(), query)) {
            return true;
        }
        for (String tag : preset.getTags()) {
            if (containsNormalized(tag, query)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsNormalized(String value, String query) {
        return value != null && normalizeSearch(value).contains(query);
    }

    private boolean isViewedAuthorPreset(MarketplacePreset preset) {
        return host.viewedAuthorKey() != null && host.viewedAuthorKey().equals(buildAuthorKey(preset));
    }

    private boolean isAuthorDirectoryMode() {
        return !host.myPresetsOnly() && !host.isViewingAuthorProfile() && host.sortMode() == SortMode.AUTHOR;
    }

    static String buildAuthorKey(MarketplacePreset preset) {
        if (preset == null) {
            return null;
        }
        String userId = fallback(preset.getAuthorUserId(), "").trim();
        if (!userId.isEmpty()) {
            return "id:" + userId;
        }
        String authorName = normalizeSearch(fallback(preset.getAuthorName(), ""));
        return authorName.isEmpty() ? null : "name:" + authorName;
    }

    static String normalizeSearch(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private static String translatedCount(String baseKey, int count) {
        return Component.translatable(count == 1 ? baseKey + ".one" : baseKey + ".other", count).getString();
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    record AuthorSummary(
        String key,
        String displayName,
        String avatarUrl,
        int presetCount,
        int totalLikes,
        int totalDownloads,
        MarketplacePreset representativePreset
    ) {
    }

    private static final class AuthorAccumulator {
        private final String key;
        private final String displayName;
        private String avatarUrl;
        private final MarketplacePreset representativePreset;
        private int presetCount;
        private int totalLikes;
        private int totalDownloads;

        private AuthorAccumulator(String key, String displayName, String avatarUrl, MarketplacePreset representativePreset) {
            this.key = key;
            this.displayName = displayName;
            this.avatarUrl = avatarUrl;
            this.representativePreset = representativePreset;
        }
    }

    enum SortMode {
        TRENDING(Component.translatable("pathmind.marketplace.sort.trending").getString(), Comparator
            .comparingInt(MarketplacePreset::getDownloadsCount).reversed()
            .thenComparing(Comparator.comparingInt(MarketplacePreset::getLikesCount).reversed())
            .thenComparing(Comparator.comparing((MarketplacePreset preset) -> fallbackStatic(preset.getUpdatedAt(), "")).reversed())),
        SAVED(Component.translatable("pathmind.marketplace.saved").getString(), Comparator.comparing((MarketplacePreset preset) -> fallbackStatic(preset.getName(), "").toLowerCase(Locale.ROOT))),
        NEWEST(Component.translatable("pathmind.marketplace.sort.newest").getString(), Comparator.comparing((MarketplacePreset preset) -> fallbackStatic(preset.getCreatedAt(), "")).reversed()),
        UPDATED(Component.translatable("pathmind.marketplace.sort.updated").getString(), Comparator.comparing((MarketplacePreset preset) -> fallbackStatic(preset.getUpdatedAt(), "")).reversed()),
        DOWNLOADS(Component.translatable("pathmind.marketplace.downloads").getString(), Comparator.comparingInt(MarketplacePreset::getDownloadsCount).reversed()),
        LIKES(Component.translatable("pathmind.marketplace.likes").getString(), Comparator.comparingInt(MarketplacePreset::getLikesCount).reversed()),
        NAME(Component.translatable("pathmind.marketplace.sort.name").getString(), Comparator.comparing((MarketplacePreset preset) -> fallbackStatic(preset.getName(), "").toLowerCase(Locale.ROOT))),
        AUTHOR(Component.translatable("pathmind.marketplace.sort.author").getString(), Comparator.comparing((MarketplacePreset preset) -> fallbackStatic(preset.getAuthorName(), "").toLowerCase(Locale.ROOT)));

        final String label;
        private final Comparator<MarketplacePreset> comparator;

        SortMode(String label, Comparator<MarketplacePreset> comparator) {
            this.label = label;
            this.comparator = comparator;
        }

        private boolean matches(Host host, MarketplacePreset preset) {
            if (this == SAVED) {
                return host.isPresetSavedLocally(preset);
            }
            return true;
        }

        MarketplaceService.ListingMode toListingMode() {
            return switch (this) {
                case TRENDING -> MarketplaceService.ListingMode.TRENDING;
                case UPDATED -> MarketplaceService.ListingMode.UPDATED;
                case DOWNLOADS -> MarketplaceService.ListingMode.DOWNLOADS;
                case LIKES -> MarketplaceService.ListingMode.LIKES;
                default -> MarketplaceService.ListingMode.NEWEST;
            };
        }

        private static String fallbackStatic(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value;
        }
    }

    enum MyPresetsFilter {
        ALL,
        PUBLIC,
        PRIVATE;

        private boolean matches(MarketplacePreset preset) {
            return switch (this) {
                case ALL -> true;
                case PUBLIC -> preset != null && preset.isPublished();
                case PRIVATE -> preset != null && !preset.isPublished();
            };
        }
    }
}
