package com.pathmind.marketplace;

import java.util.List;

/**
 * Read-only marketplace listing payload fetched from Supabase.
 */
public final class MarketplacePreset {
    private final String id;
    private final String slug;
    private final String authorUserId;
    private final String name;
    private final String authorName;
    private final String authorAvatarUrl;
    private final String description;
    private final List<String> tags;
    private final String gameVersion;
    private final String pathmindVersion;
    private final int likesCount;
    private final int downloadsCount;
    private final String storageBucket;
    private final String filePath;
    private final boolean published;
    private final String createdAt;
    private final String updatedAt;

    public MarketplacePreset(String id,
                             String slug,
                             String authorUserId,
                             String name,
                             String authorName,
                             String description,
                             List<String> tags,
                             String gameVersion,
                             String pathmindVersion,
                             int likesCount,
                             int downloadsCount,
                             String storageBucket,
                             String filePath,
                             boolean published,
                             String createdAt,
                             String updatedAt) {
        this(id, slug, authorUserId, name, authorName, null, description, tags, gameVersion, pathmindVersion, likesCount,
            downloadsCount, storageBucket, filePath, published, createdAt, updatedAt);
    }

    public MarketplacePreset(String id,
                             String slug,
                             String authorUserId,
                             String name,
                             String authorName,
                             String authorAvatarUrl,
                             String description,
                             List<String> tags,
                             String gameVersion,
                             String pathmindVersion,
                             int likesCount,
                             int downloadsCount,
                             String storageBucket,
                             String filePath,
                             boolean published,
                             String createdAt,
                             String updatedAt) {
        this.id = id;
        this.slug = slug;
        this.authorUserId = authorUserId;
        this.name = name;
        this.authorName = authorName;
        this.authorAvatarUrl = authorAvatarUrl;
        this.description = description;
        this.tags = tags == null ? List.of() : List.copyOf(tags);
        this.gameVersion = gameVersion;
        this.pathmindVersion = pathmindVersion;
        this.likesCount = likesCount;
        this.downloadsCount = downloadsCount;
        this.storageBucket = storageBucket;
        this.filePath = filePath;
        this.published = published;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getAuthorUserId() {
        return authorUserId;
    }

    public String getName() {
        return name;
    }

    public String getAuthorName() {
        return authorName;
    }

    public String getAuthorAvatarUrl() {
        return authorAvatarUrl;
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

    public int getLikesCount() {
        return likesCount;
    }

    public int getDownloadsCount() {
        return downloadsCount;
    }

    public String getStorageBucket() {
        return storageBucket;
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

    public String getUpdatedAt() {
        return updatedAt;
    }
}
