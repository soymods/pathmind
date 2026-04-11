package com.pathmind.marketplace;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pathmind.data.NodeGraphData;
import com.pathmind.data.NodeGraphPersistence;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Minimal Supabase-backed marketplace read service.
 */
public final class MarketplaceService {
    public static final String PROJECT_URL = "https://gadzentglfdzhylcchmk.supabase.co";
    public static final String PUBLISHABLE_KEY = "sb_publishable_ZJbCFcG5Yh4QM9W9as4jVA_daD3fwnn";
    public static final String PUBLIC_BUCKET_NAME = "graphs";
    public static final String PRIVATE_BUCKET_NAME = "private_graphs";
    private static final String PUBLISH_PRESET_RPC = "publish_marketplace_preset";
    private static final String DELETE_PRESET_RPC = "delete_marketplace_preset";
    private static final String UPDATE_PRESET_METADATA_RPC = "update_marketplace_preset_metadata";
    private static final String TOGGLE_PRESET_LIKE_RPC = "toggle_marketplace_preset_like";
    private static final String INCREMENT_DOWNLOAD_RPC = "increment_preset_downloads";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private MarketplaceService() {
    }

    public static CompletableFuture<List<MarketplacePreset>> fetchPublishedPresets() {
        return fetchPresets(buildPublishedListingsUrl(), false);
    }

    public static CompletableFuture<List<MarketplacePreset>> fetchOwnedPresets(String accessToken, String userId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (accessToken == null || accessToken.isBlank() || userId == null || userId.isBlank()) {
                    return List.of();
                }
                return fetchPresets(buildOwnedListingsUrl(userId), accessToken, false);
            } catch (Exception e) {
                throw new RuntimeException("Failed to fetch owned marketplace presets", e);
            }
        });
    }

    public static CompletableFuture<MarketplacePreset> fetchPresetById(String accessToken, String presetId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (accessToken == null || accessToken.isBlank() || presetId == null || presetId.isBlank()) {
                    return null;
                }
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(PROJECT_URL
                        + "/rest/v1/marketplace_presets?select=id,slug,author_user_id,name,author_name,description,tags,game_version,pathmind_version,likes_count,downloads_count,storage_bucket,file_path,published,created_at,updated_at&id=eq."
                        + URLEncoder.encode(presetId, StandardCharsets.UTF_8)
                        + "&limit=1"))
                    .timeout(Duration.ofSeconds(15))
                    .header("apikey", PUBLISHABLE_KEY)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IOException("Preset lookup failed with HTTP " + response.statusCode());
                }
                List<MarketplacePreset> presets = parsePresets(response.body());
                return presets.isEmpty() ? null : presets.get(0);
            } catch (Exception e) {
                throw new RuntimeException("Failed to fetch marketplace preset", e);
            }
        });
    }

    private static CompletableFuture<List<MarketplacePreset>> fetchPresets(String url, boolean hydrateLikeCounts) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return fetchPresets(url, PUBLISHABLE_KEY, hydrateLikeCounts);
            } catch (Exception e) {
                throw new RuntimeException("Failed to fetch marketplace presets", e);
            }
        });
    }

    private static List<MarketplacePreset> fetchPresets(String url, String bearerToken, boolean hydrateLikeCounts) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .header("apikey", PUBLISHABLE_KEY)
            .header("Authorization", "Bearer " + bearerToken)
            .header("Accept", "application/json")
            .GET()
            .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Marketplace request failed with HTTP " + response.statusCode());
        }
        List<MarketplacePreset> presets = parsePresets(response.body());
        if (!hydrateLikeCounts || presets.isEmpty()) {
            return presets;
        }
        Map<String, Integer> likeCounts = fetchPresetLikeCounts();
        return likeCounts.isEmpty() ? presets : applyLikeCounts(presets, likeCounts);
    }

    public static String buildPublicGraphUrl(String bucketName, String filePath) {
        String normalizedPath = filePath == null ? "" : filePath.replace("\\", "/");
        String[] segments = normalizedPath.split("/");
        StringBuilder encodedPath = new StringBuilder();
        for (String segment : segments) {
            if (segment == null || segment.isEmpty()) {
                continue;
            }
            if (!encodedPath.isEmpty()) {
                encodedPath.append('/');
            }
            encodedPath.append(URLEncoder.encode(segment, StandardCharsets.UTF_8));
        }
        return PROJECT_URL + "/storage/v1/object/public/" + normalizeBucketName(bucketName) + "/" + encodedPath;
    }

    public static CompletableFuture<Path> downloadPresetToTempFile(MarketplacePreset preset) {
        return downloadPresetToTempFile(preset, null);
    }

    public static CompletableFuture<Path> downloadPresetToTempFile(MarketplacePreset preset, String accessToken) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String slug = preset != null && preset.getSlug() != null && !preset.getSlug().isBlank()
                    ? preset.getSlug()
                    : "marketplace-preset";
                Path tempFile = Files.createTempFile("pathmind-" + slug + "-", ".json");
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json");

                if (preset != null && isPrivateBucket(preset.getStorageBucket())) {
                    if (accessToken == null || accessToken.isBlank()) {
                        throw new IOException("Sign in to download private presets.");
                    }
                    requestBuilder
                        .uri(URI.create(PROJECT_URL + "/storage/v1/object/authenticated/"
                            + normalizeBucketName(preset.getStorageBucket()) + "/" + encodeStoragePath(preset.getFilePath())))
                        .header("apikey", PUBLISHABLE_KEY)
                        .header("Authorization", "Bearer " + accessToken);
                } else {
                    requestBuilder.uri(URI.create(buildPublicGraphUrl(
                        preset == null ? PUBLIC_BUCKET_NAME : preset.getStorageBucket(),
                        preset == null ? "" : preset.getFilePath()
                    )));
                }

                HttpRequest request = requestBuilder.GET().build();

                HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IOException("Marketplace download failed with HTTP " + response.statusCode());
                }

                Files.write(tempFile, response.body());
                return tempFile;
            } catch (Exception e) {
                throw new RuntimeException("Failed to download marketplace preset", e);
            }
        });
    }

    public static CompletableFuture<NodeGraphData> fetchPresetGraphData(MarketplacePreset preset) {
        return fetchPresetGraphData(preset, null);
    }

    public static CompletableFuture<NodeGraphData> fetchPresetGraphData(MarketplacePreset preset, String accessToken) {
        return downloadPresetToTempFile(preset, accessToken).thenApply(path -> {
            try {
                return NodeGraphPersistence.loadNodeGraphFromPath(path);
            } finally {
                if (path != null) {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                }
            }
        });
    }

    public static CompletableFuture<Set<String>> fetchLikedPresetIds(String accessToken, String userId) {
        return CompletableFuture.supplyAsync(() -> {
            if (accessToken == null || accessToken.isBlank() || userId == null || userId.isBlank()) {
                return Set.of();
            }
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(PROJECT_URL
                        + "/rest/v1/preset_likes?select=preset_id&user_id=eq."
                        + URLEncoder.encode(userId, StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(15))
                    .header("apikey", PUBLISHABLE_KEY)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IOException("Liked preset request failed with HTTP " + response.statusCode());
                }

                JsonElement root = JsonParser.parseString(response.body());
                if (!root.isJsonArray()) {
                    return Set.of();
                }
                Set<String> likedPresetIds = new java.util.HashSet<>();
                for (JsonElement element : root.getAsJsonArray()) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    String value = getNullableString(element.getAsJsonObject(), "preset_id");
                    if (value != null && !value.isBlank()) {
                        likedPresetIds.add(value);
                    }
                }
                return likedPresetIds;
            } catch (Exception e) {
                throw new RuntimeException("Failed to fetch liked marketplace presets", e);
            }
        });
    }

    public static CompletableFuture<Boolean> toggleLike(String accessToken, String presetId, String userId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (accessToken == null || accessToken.isBlank() || presetId == null || presetId.isBlank() || userId == null || userId.isBlank()) {
                    throw new IOException("Marketplace auth expired.");
                }

                JsonObject payload = new JsonObject();
                payload.addProperty("target_preset_id", presetId);
                JsonObject result = postRpcJson(TOGGLE_PRESET_LIKE_RPC, payload, accessToken);
                JsonElement likedElement = result.get("liked");
                if (likedElement == null || likedElement.isJsonNull()) {
                    throw new IOException("Like toggle returned no result.");
                }
                return likedElement.getAsBoolean();
            } catch (Exception e) {
                throw new RuntimeException("Failed to toggle marketplace like", e);
            }
        });
    }

    public static CompletableFuture<Void> incrementDownload(String accessToken, String presetId) {
        return CompletableFuture.runAsync(() -> {
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("target_preset_id", presetId);
                postRpcJson(INCREMENT_DOWNLOAD_RPC, payload, accessToken);
            } catch (Exception e) {
                throw new RuntimeException("Failed to increment marketplace download count", e);
            }
        });
    }

    public static CompletableFuture<Void> deletePreset(String accessToken, String presetId, String storageBucket, String filePath) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (accessToken == null || accessToken.isBlank()) {
                    throw new IOException("Marketplace auth token missing.");
                }
                if (presetId == null || presetId.isBlank()) {
                    throw new IOException("Preset id is missing.");
                }

                JsonObject payload = new JsonObject();
                payload.addProperty("target_preset_id", presetId);
                MarketplacePreset deletedPreset = postRpcPreset(DELETE_PRESET_RPC, payload, accessToken, "Failed to delete marketplace preset");
                if (deletedPreset == null) {
                    throw new IOException("Marketplace preset delete was denied or no row matched.");
                }

                String deletedFilePath = deletedPreset.getFilePath() == null || deletedPreset.getFilePath().isBlank()
                    ? filePath
                    : deletedPreset.getFilePath();
                String deletedBucket = deletedPreset.getStorageBucket() == null || deletedPreset.getStorageBucket().isBlank()
                    ? storageBucket
                    : deletedPreset.getStorageBucket();
                deleteStorageObject(accessToken, deletedBucket, deletedFilePath);
            } catch (Exception e) {
                throw new RuntimeException("Failed to delete marketplace preset", e);
            }
        });
    }

    public static CompletableFuture<MarketplacePreset> publishPreset(String accessToken, String userId, PublishRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (accessToken == null || accessToken.isBlank()) {
                    throw new IOException("Marketplace auth token missing.");
                }
                if (request == null || request.localPresetPath() == null || !Files.exists(request.localPresetPath())) {
                    throw new IOException("Preset file is missing.");
                }

                String slug = sanitizeSlug(request.slug());
                if (slug.isBlank()) {
                    throw new IOException("Preset slug is required.");
                }

                String targetBucket = request.published() ? PUBLIC_BUCKET_NAME : PRIVATE_BUCKET_NAME;
                String previousBucket = normalizeBucketName(request.existingStorageBucket());
                String storagePath = buildStoragePath(userId, slug, request.existingFilePath());
                // Publishing should be retry-safe: if a previous attempt uploaded the graph before failing
                // later in the flow, a second publish should overwrite the same owned storage object.
                uploadPresetFile(accessToken, targetBucket, storagePath, request.localPresetPath(), true);

                JsonObject payload = buildPublishRpcPayload(request, slug, targetBucket, storagePath);
                MarketplacePreset publishedPreset = postRpcPreset(PUBLISH_PRESET_RPC, payload, accessToken, "Failed to publish marketplace preset");
                if (publishedPreset == null) {
                    throw new IOException("Marketplace publish returned no preset.");
                }
                if (!previousBucket.isBlank() && !previousBucket.equals(targetBucket) && request.existingFilePath() != null && !request.existingFilePath().isBlank()) {
                    deleteStorageObject(accessToken, previousBucket, request.existingFilePath());
                }
                return publishedPreset;
            } catch (Exception e) {
                throw new RuntimeException("Failed to publish marketplace preset", e);
            }
        });
    }

    public static CompletableFuture<MarketplacePreset> updatePresetMetadata(String accessToken, MarketplacePreset preset, PublishRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (accessToken == null || accessToken.isBlank()) {
                    throw new IOException("Marketplace auth token missing.");
                }
                if (preset == null || preset.getId() == null || preset.getId().isBlank()) {
                    throw new IOException("Preset id is missing.");
                }
                if (request == null) {
                    throw new IOException("Preset metadata is missing.");
                }

                String targetBucket = request.published() ? PUBLIC_BUCKET_NAME : PRIVATE_BUCKET_NAME;
                String currentBucket = normalizeBucketName(preset.getStorageBucket());
                String targetPath = preset.getFilePath();
                boolean hasLocalPresetFile = request.localPresetPath() != null && Files.exists(request.localPresetPath());
                if (hasLocalPresetFile) {
                    uploadPresetFile(accessToken, targetBucket, targetPath, request.localPresetPath(), true);
                } else if (!currentBucket.equals(targetBucket)) {
                    moveStorageObject(accessToken, currentBucket, targetBucket, targetPath);
                }

                JsonObject payload = new JsonObject();
                payload.addProperty("target_preset_id", preset.getId());
                payload.addProperty("preset_name", blankToEmpty(request.name()));
                payload.addProperty("preset_description", blankToEmpty(request.description()));
                payload.add("preset_tags", toJsonArray(request.tags()));
                payload.addProperty("preset_game_version", blankToEmpty(request.gameVersion()));
                payload.addProperty("preset_pathmind_version", blankToEmpty(request.pathmindVersion()));
                payload.addProperty("preset_published", request.published());
                payload.addProperty("preset_storage_bucket", targetBucket);
                payload.addProperty("preset_file_path", blankToEmpty(targetPath));
                MarketplacePreset updatedPreset = postRpcPreset(
                    UPDATE_PRESET_METADATA_RPC,
                    payload,
                    accessToken,
                    "Failed to update marketplace preset"
                );
                if (updatedPreset == null) {
                    throw new IOException("Marketplace metadata update returned no preset.");
                }
                if (!currentBucket.equals(targetBucket)) {
                    deleteStorageObject(accessToken, currentBucket, targetPath);
                }
                return updatedPreset;
            } catch (Exception e) {
                throw new RuntimeException("Failed to update marketplace preset", e);
            }
        });
    }

    private static String buildPublishedListingsUrl() {
        return PROJECT_URL
            + "/rest/v1/marketplace_presets"
            + "?select=id,slug,author_user_id,name,author_name,description,tags,game_version,pathmind_version,likes_count,downloads_count,storage_bucket,file_path,published,created_at,updated_at"
            + "&published=eq.true"
            + "&order=created_at.desc";
    }

    private static String buildOwnedListingsUrl(String userId) {
        return PROJECT_URL
            + "/rest/v1/marketplace_presets"
            + "?select=id,slug,author_user_id,name,author_name,description,tags,game_version,pathmind_version,likes_count,downloads_count,storage_bucket,file_path,published,created_at,updated_at"
            + "&author_user_id=eq." + URLEncoder.encode(userId, StandardCharsets.UTF_8)
            + "&order=updated_at.desc";
    }

    private static Map<String, Integer> fetchPresetLikeCounts() throws IOException, InterruptedException {
        Map<String, Integer> counts = new HashMap<>();
        int offset = 0;
        int pageSize = 1000;
        while (true) {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(PROJECT_URL
                    + "/rest/v1/preset_likes?select=preset_id&limit=" + pageSize + "&offset=" + offset))
                .timeout(Duration.ofSeconds(15))
                .header("apikey", PUBLISHABLE_KEY)
                .header("Authorization", "Bearer " + PUBLISHABLE_KEY)
                .header("Accept", "application/json")
                .GET()
                .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Preset like count request failed with HTTP " + response.statusCode());
            }

            JsonElement root = JsonParser.parseString(response.body());
            if (!root.isJsonArray()) {
                break;
            }
            JsonArray rows = root.getAsJsonArray();
            if (rows.isEmpty()) {
                break;
            }
            for (JsonElement rowElement : rows) {
                if (!rowElement.isJsonObject()) {
                    continue;
                }
                String presetId = getNullableString(rowElement.getAsJsonObject(), "preset_id");
                if (presetId != null && !presetId.isBlank()) {
                    counts.merge(presetId, 1, Integer::sum);
                }
            }
            if (rows.size() < pageSize) {
                break;
            }
            offset += pageSize;
        }
        return counts;
    }

    private static JsonObject postRpcJson(String functionName, JsonObject payload, String accessToken)
        throws IOException, InterruptedException {
        HttpResponse<String> response = sendRpcRequest(functionName, payload, accessToken, Duration.ofSeconds(15));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw buildHttpException("Marketplace RPC " + functionName + " failed", response);
        }
        JsonElement root = JsonParser.parseString(response.body());
        return root != null && root.isJsonObject() ? root.getAsJsonObject() : new JsonObject();
    }

    private static MarketplacePreset postRpcPreset(String functionName, JsonObject payload, String accessToken, String errorMessage)
        throws IOException, InterruptedException {
        HttpResponse<String> response = sendRpcRequest(functionName, payload, accessToken, Duration.ofSeconds(20));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw buildHttpException(errorMessage, response);
        }
        return parseSinglePreset(response.body());
    }

    private static HttpResponse<String> sendRpcRequest(String functionName, JsonObject payload, String accessToken, Duration timeout)
        throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(PROJECT_URL + "/rest/v1/rpc/" + functionName))
            .timeout(timeout)
            .header("apikey", PUBLISHABLE_KEY)
            .header("Authorization", "Bearer " + accessToken)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
            .build();
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static JsonObject buildPublishRpcPayload(PublishRequest request, String slug, String storageBucket, String storagePath) {
        JsonObject payload = new JsonObject();
        payload.addProperty("preset_slug", slug);
        payload.addProperty("preset_name", blankToEmpty(request.name()));
        payload.addProperty("preset_author_name", blankToEmpty(request.authorName()));
        payload.addProperty("preset_description", blankToEmpty(request.description()));
        payload.add("preset_tags", toJsonArray(request.tags()));
        payload.addProperty("preset_game_version", blankToEmpty(request.gameVersion()));
        payload.addProperty("preset_pathmind_version", blankToEmpty(request.pathmindVersion()));
        payload.addProperty("preset_storage_bucket", blankToEmpty(storageBucket));
        payload.addProperty("preset_file_path", blankToEmpty(storagePath));
        payload.addProperty("preset_published", request.published());
        return payload;
    }

    private static JsonObject buildPresetPayload(PublishRequest request, String slug, String storagePath, String authorUserId) {
        JsonObject payload = new JsonObject();
        payload.addProperty("slug", slug);
        payload.addProperty("name", blankToEmpty(request.name()));
        payload.addProperty("author_name", blankToEmpty(request.authorName()));
        payload.addProperty("description", blankToEmpty(request.description()));
        payload.add("tags", toJsonArray(request.tags()));
        payload.addProperty("game_version", blankToEmpty(request.gameVersion()));
        payload.addProperty("pathmind_version", blankToEmpty(request.pathmindVersion()));
        payload.addProperty("storage_bucket", request.published() ? PUBLIC_BUCKET_NAME : PRIVATE_BUCKET_NAME);
        payload.addProperty("file_path", blankToEmpty(storagePath));
        payload.addProperty("published", true);
        if (authorUserId != null && !authorUserId.isBlank()) {
            payload.addProperty("author_user_id", authorUserId);
        }
        return payload;
    }

    private static JsonArray toJsonArray(List<String> values) {
        JsonArray array = new JsonArray();
        if (values == null) {
            return array;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                array.add(value.trim());
            }
        }
        return array;
    }

    private static void uploadPresetFile(String accessToken, String bucketName, String storagePath, Path localPresetPath, boolean upsert)
        throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(PROJECT_URL + "/storage/v1/object/" + normalizeBucketName(bucketName) + "/" + encodeStoragePath(storagePath)))
            .timeout(Duration.ofSeconds(20))
            .header("apikey", PUBLISHABLE_KEY)
            .header("Authorization", "Bearer " + accessToken)
            .header("Content-Type", "application/json")
            .header("x-upsert", upsert ? "true" : "false")
            .POST(HttpRequest.BodyPublishers.ofByteArray(Files.readAllBytes(localPresetPath)))
            .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw buildHttpException("Failed to upload preset graph", response);
        }
    }

    private static void deleteStorageObject(String accessToken, String bucketName, String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(PROJECT_URL + "/storage/v1/object/" + normalizeBucketName(bucketName) + "/" + encodeStoragePath(storagePath)))
                .timeout(Duration.ofSeconds(20))
                .header("apikey", PUBLISHABLE_KEY)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .DELETE()
                .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw buildHttpException("Failed to delete preset graph", response);
            }
        } catch (Exception ignored) {
            // Listing deletion is the primary user-facing operation; storage cleanup is best-effort.
        }
    }

    private static void moveStorageObject(String accessToken, String sourceBucket, String targetBucket, String storagePath)
        throws IOException, InterruptedException {
        String normalizedSource = normalizeBucketName(sourceBucket);
        String normalizedTarget = normalizeBucketName(targetBucket);
        if (storagePath == null || storagePath.isBlank() || normalizedSource.equals(normalizedTarget)) {
            return;
        }

        byte[] bytes = downloadStorageObject(accessToken, normalizedSource, storagePath);
        String suffix = storagePath.endsWith(".json") ? ".json" : ".tmp";
        Path tempFile = Files.createTempFile("pathmind-marketplace-move-", suffix);
        try {
            Files.write(tempFile, bytes);
            uploadPresetFile(accessToken, normalizedTarget, storagePath, tempFile, true);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private static byte[] downloadStorageObject(String accessToken, String bucketName, String storagePath)
        throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(PROJECT_URL + "/storage/v1/object/authenticated/" + normalizeBucketName(bucketName) + "/" + encodeStoragePath(storagePath)))
            .timeout(Duration.ofSeconds(20))
            .header("apikey", PUBLISHABLE_KEY)
            .header("Authorization", "Bearer " + accessToken)
            .header("Accept", "application/json")
            .GET()
            .build();

        HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Failed to download preset graph (HTTP " + response.statusCode() + ")");
        }
        return response.body();
    }

    private static IOException buildHttpException(String message, HttpResponse<String> response) {
        String body = response.body() == null ? "" : response.body().trim();
        String normalizedBody = body.toLowerCase(Locale.ROOT);
        if (normalizedBody.contains("jwt expired") || normalizedBody.contains("token is expired") || normalizedBody.contains("invalid jwt")) {
            return new IOException("Marketplace auth expired.");
        }
        String friendlyMessage = mapFriendlyMarketplaceError(message, body);
        if (friendlyMessage != null) {
            return new IOException(friendlyMessage);
        }
        String extractedMessage = extractJsonErrorMessage(body);
        if (extractedMessage != null && !extractedMessage.isBlank()) {
            if (extractedMessage.toLowerCase(Locale.ROOT).contains("row-level security policy")) {
                if (message.toLowerCase(Locale.ROOT).contains("upload preset graph")) {
                    return new IOException("Supabase Storage denied the upload: row-level security policy.");
                }
                return new IOException(message + ": row-level security policy.");
            }
            return new IOException(message + ": " + extractedMessage);
        }
        if (!body.isEmpty()) {
            return new IOException(message + " (HTTP " + response.statusCode() + "): " + body);
        }
        return new IOException(message + " (HTTP " + response.statusCode() + ")");
    }

    private static String mapFriendlyMarketplaceError(String operationMessage, String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        String normalized = body.toLowerCase(Locale.ROOT);
        boolean publishOperation = operationMessage != null && operationMessage.toLowerCase(Locale.ROOT).contains("publish");
        boolean updateOperation = operationMessage != null && operationMessage.toLowerCase(Locale.ROOT).contains("update");

        if (normalized.contains("marketplace_presets_slug_unique")
            || normalized.contains("duplicate key value violates unique constraint")
            || normalized.contains("already exists")) {
            return "A preset with this name already exists. Try a different name.";
        }
        if (normalized.contains("marketplace_presets_name_not_blank") || normalized.contains("preset name is required")) {
            return "Enter a preset name.";
        }
        if (normalized.contains("marketplace_presets_name_len")) {
            return "Preset name is too long.";
        }
        if (normalized.contains("marketplace_presets_description_len")) {
            return "Description is too long.";
        }
        if (normalized.contains("publish rate limit exceeded")) {
            return "You have published too many presets recently. Try again in a few minutes.";
        }
        if (normalized.contains("like rate limit exceeded")) {
            return "You're clicking like too quickly. Try again in a moment.";
        }
        if (normalized.contains("authentication required")) {
            return "Marketplace auth expired.";
        }
        if (normalized.contains("not found or not owned by current user")) {
            return updateOperation
                ? "You can only edit presets you published."
                : "You can only delete presets you published.";
        }
        if (publishOperation && normalized.contains("preset file path is required")) {
            return "Preset file upload failed.";
        }
        return null;
    }

    private static String extractJsonErrorMessage(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonObject()) {
                return null;
            }
            JsonObject object = root.getAsJsonObject();
            String message = getNullableString(object, "message");
            if (message != null && !message.isBlank()) {
                return message;
            }
            String error = getNullableString(object, "error");
            if (error != null && !error.isBlank()) {
                return error;
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String buildStoragePath(String userId, String slug, String existingFilePath) {
        if (existingFilePath != null && !existingFilePath.isBlank()) {
            return existingFilePath;
        }
        String ownerSegment = sanitizeSlug(userId == null ? "anonymous" : userId);
        return ownerSegment + "/" + slug + ".json";
    }

    private static String encodeStoragePath(String path) {
        String normalizedPath = path == null ? "" : path.replace("\\", "/");
        String[] segments = normalizedPath.split("/");
        StringBuilder encoded = new StringBuilder();
        for (String segment : segments) {
            if (segment == null || segment.isBlank()) {
                continue;
            }
            if (!encoded.isEmpty()) {
                encoded.append('/');
            }
            encoded.append(URLEncoder.encode(segment, StandardCharsets.UTF_8));
        }
        return encoded.toString();
    }

    private static String sanitizeSlug(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-+|-+$)", "");
        return normalized;
    }

    private static String normalizeBucketName(String bucketName) {
        return bucketName == null || bucketName.isBlank() ? PUBLIC_BUCKET_NAME : bucketName.trim();
    }

    private static boolean isPrivateBucket(String bucketName) {
        return PRIVATE_BUCKET_NAME.equalsIgnoreCase(normalizeBucketName(bucketName));
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static MarketplacePreset parseSinglePreset(String json) {
        JsonElement root = JsonParser.parseString(json);
        if (root == null || root.isJsonNull()) {
            return null;
        }
        if (root.isJsonArray()) {
            JsonArray array = root.getAsJsonArray();
            if (array.isEmpty() || !array.get(0).isJsonObject()) {
                return null;
            }
            return parsePreset(array.get(0).getAsJsonObject());
        }
        if (root.isJsonObject()) {
            return parsePreset(root.getAsJsonObject());
        }
        return null;
    }

    private static List<MarketplacePreset> parsePresets(String json) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonArray()) {
            return List.of();
        }

        List<MarketplacePreset> presets = new ArrayList<>();
        JsonArray rows = root.getAsJsonArray();
        for (JsonElement rowElement : rows) {
            if (!rowElement.isJsonObject()) {
                continue;
            }
            presets.add(parsePreset(rowElement.getAsJsonObject()));
        }
        return presets;
    }

    private static List<MarketplacePreset> applyLikeCounts(List<MarketplacePreset> presets, Map<String, Integer> likeCounts) {
        List<MarketplacePreset> updated = new ArrayList<>(presets.size());
        for (MarketplacePreset preset : presets) {
            updated.add(withLikeCount(preset, likeCounts.getOrDefault(preset.getId(), 0)));
        }
        return List.copyOf(updated);
    }

    private static MarketplacePreset parsePreset(JsonObject row) {
        return new MarketplacePreset(
            getString(row, "id"),
            getString(row, "slug"),
            getNullableString(row, "author_user_id"),
            getString(row, "name"),
            getString(row, "author_name"),
            getNullableString(row, "author_avatar_url"),
            getString(row, "description"),
            getStringArray(row, "tags"),
            getNullableString(row, "game_version"),
            getNullableString(row, "pathmind_version"),
            getInt(row, "likes_count"),
            getInt(row, "downloads_count"),
            getNullableString(row, "storage_bucket"),
            getString(row, "file_path"),
            getBoolean(row, "published"),
            getNullableString(row, "created_at"),
            getNullableString(row, "updated_at")
        );
    }

    private static MarketplacePreset withLikeCount(MarketplacePreset preset, int likesCount) {
        if (preset == null) {
            return null;
        }
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
            Math.max(0, likesCount),
            preset.getDownloadsCount(),
            preset.getStorageBucket(),
            preset.getFilePath(),
            preset.isPublished(),
            preset.getCreatedAt(),
            preset.getUpdatedAt()
        );
    }

    private static String getString(JsonObject object, String key) {
        String value = getNullableString(object, key);
        return value == null ? "" : value;
    }

    private static String getNullableString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        return element.getAsString();
    }

    private static boolean getBoolean(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && !element.isJsonNull() && element.getAsBoolean();
    }

    private static int getInt(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && !element.isJsonNull() ? element.getAsInt() : 0;
    }

    private static List<String> getStringArray(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonElement item : element.getAsJsonArray()) {
            if (item != null && !item.isJsonNull()) {
                String value = item.getAsString();
                if (!value.isBlank()) {
                    values.add(value);
                }
            }
        }
        return values;
    }

    public record PublishRequest(
        Path localPresetPath,
        String existingStorageBucket,
        String existingFilePath,
        String slug,
        String name,
        String authorName,
        String description,
        List<String> tags,
        String gameVersion,
        String pathmindVersion,
        boolean published
    ) {
    }
}
