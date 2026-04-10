package com.pathmind.marketplace;

import java.util.List;

/**
 * Read-only marketplace listing payload fetched from Supabase.
 */
public final class MarketplacePreset {
    private final String id;
    private final String slug;
    private final String name;
    private final String authorName;
    private final String description;
    private final List<String> tags;
    private final String gameVersion;
    private final String pathmindVersion;
    private final String filePath;
    private final boolean published;
    private final String createdAt;

    public MarketplacePreset(String id,
                             String slug,
                             String name,
                             String authorName,
                             String description,
                             List<String> tags,
                             String gameVersion,
                             String pathmindVersion,
                             String filePath,
                             boolean published,
                             String createdAt) {
        this.id = id;
        this.slug = slug;
        this.name = name;
        this.authorName = authorName;
        this.description = description;
        this.tags = tags == null ? List.of() : List.copyOf(tags);
        this.gameVersion = gameVersion;
        this.pathmindVersion = pathmindVersion;
        this.filePath = filePath;
        this.published = published;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public String getAuthorName() {
        return authorName;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getTags() {
        return tags;
    }

    public String getGameVersion() {
        return gameVersion;
    }

    public String getPathmindVersion() {
        return pathmindVersion;
    }

    public String getFilePath() {
        return filePath;
    }

    public boolean isPublished() {
        return published;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
