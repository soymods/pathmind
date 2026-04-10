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
    public static final String BUCKET_NAME = "graphs";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private MarketplaceService() {
    }

    public static CompletableFuture<List<MarketplacePreset>> fetchPublishedPresets() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(buildListingsUrl()))
                    .timeout(Duration.ofSeconds(15))
                    .header("apikey", PUBLISHABLE_KEY)
                    .header("Authorization", "Bearer " + PUBLISHABLE_KEY)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IOException("Marketplace request failed with HTTP " + response.statusCode());
                }
                List<MarketplacePreset> presets = parsePresets(response.body());
                if (presets.isEmpty()) {
                    return presets;
                }
                Map<String, Integer> likeCounts = fetchPresetLikeCounts();
                return likeCounts.isEmpty() ? presets : applyLikeCounts(presets, likeCounts);
            } catch (Exception e) {
                throw new RuntimeException("Failed to fetch marketplace presets", e);
            }
        });
    }

    public static String buildPublicGraphUrl(String filePath) {
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
        return PROJECT_URL + "/storage/v1/object/public/" + BUCKET_NAME + "/" + encodedPath;
    }

    public static CompletableFuture<Path> downloadPresetToTempFile(MarketplacePreset preset) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String slug = preset != null && preset.getSlug() != null && !preset.getSlug().isBlank()
                    ? preset.getSlug()
                    : "marketplace-preset";
                Path tempFile = Files.createTempFile("pathmind-" + slug + "-", ".json");
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(buildPublicGraphUrl(preset.getFilePath())))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

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
        return downloadPresetToTempFile(preset).thenApply(path -> {
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

                HttpRequest selectRequest = HttpRequest.newBuilder()
                    .uri(URI.create(PROJECT_URL
                        + "/rest/v1/preset_likes?select=preset_id&preset_id=eq."
                        + URLEncoder.encode(presetId, StandardCharsets.UTF_8)
                        + "&user_id=eq."
                        + URLEncoder.encode(userId, StandardCharsets.UTF_8)
                        + "&limit=1"))
                    .timeout(Duration.ofSeconds(15))
                    .header("apikey", PUBLISHABLE_KEY)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

                HttpResponse<String> selectResponse = HTTP_CLIENT.send(selectRequest, HttpResponse.BodyHandlers.ofString());
                if (selectResponse.statusCode() < 200 || selectResponse.statusCode() >= 300) {
                    throw new IOException("Like lookup failed with HTTP " + selectResponse.statusCode());
                }

                JsonElement lookupRoot = JsonParser.parseString(selectResponse.body());
                boolean alreadyLiked = lookupRoot.isJsonArray() && !lookupRoot.getAsJsonArray().isEmpty();

                if (alreadyLiked) {
                    HttpRequest deleteRequest = HttpRequest.newBuilder()
                        .uri(URI.create(PROJECT_URL
                            + "/rest/v1/preset_likes?preset_id=eq."
                            + URLEncoder.encode(presetId, StandardCharsets.UTF_8)
                            + "&user_id=eq."
                            + URLEncoder.encode(userId, StandardCharsets.UTF_8)))
                        .timeout(Duration.ofSeconds(15))
                        .header("apikey", PUBLISHABLE_KEY)
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Accept", "application/json")
                        .header("Prefer", "return=minimal")
                        .DELETE()
                        .build();

                    HttpResponse<String> deleteResponse = HTTP_CLIENT.send(deleteRequest, HttpResponse.BodyHandlers.ofString());
                    if (deleteResponse.statusCode() < 200 || deleteResponse.statusCode() >= 300) {
                        throw new IOException("Unlike failed with HTTP " + deleteResponse.statusCode());
                    }
                    return false;
                }

                JsonObject payload = new JsonObject();
                payload.addProperty("preset_id", presetId);
                payload.addProperty("user_id", userId);
                HttpRequest insertRequest = HttpRequest.newBuilder()
                    .uri(URI.create(PROJECT_URL + "/rest/v1/preset_likes"))
                    .timeout(Duration.ofSeconds(15))
                    .header("apikey", PUBLISHABLE_KEY)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Prefer", "return=minimal")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();

                HttpResponse<String> insertResponse = HTTP_CLIENT.send(insertRequest, HttpResponse.BodyHandlers.ofString());
                if (insertResponse.statusCode() < 200 || insertResponse.statusCode() >= 300) {
                    throw new IOException("Like insert failed with HTTP " + insertResponse.statusCode());
                }
                return true;
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
                postRpcJson("increment_preset_downloads", payload, accessToken);
            } catch (Exception e) {
                throw new RuntimeException("Failed to increment marketplace download count", e);
            }
        });
    }

    public static CompletableFuture<Void> deletePreset(String accessToken, String presetId, String filePath) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (accessToken == null || accessToken.isBlank()) {
                    throw new IOException("Marketplace auth token missing.");
                }
                if (presetId == null || presetId.isBlank()) {
                    throw new IOException("Preset id is missing.");
                }

                HttpRequest deletePresetRequest = HttpRequest.newBuilder()
                    .uri(URI.create(PROJECT_URL + "/rest/v1/marketplace_presets?id=eq."
                        + URLEncoder.encode(presetId, StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(20))
                    .header("apikey", PUBLISHABLE_KEY)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/json")
                    .header("Prefer", "return=representation")
                    .DELETE()
                    .build();

                HttpResponse<String> deletePresetResponse = HTTP_CLIENT.send(deletePresetRequest, HttpResponse.BodyHandlers.ofString());
                if (deletePresetResponse.statusCode() < 200 || deletePresetResponse.statusCode() >= 300) {
                    throw buildHttpException("Failed to delete marketplace preset", deletePresetResponse);
                }
                if (parseSinglePreset(deletePresetResponse.body()) == null) {
                    throw new IOException("Marketplace preset delete was denied or no row matched.");
                }

                deleteStorageObject(accessToken, filePath);
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

                String storagePath = buildStoragePath(userId, slug, request.existingFilePath());
                // Publishing should be retry-safe: if a previous attempt uploaded the graph before failing
                // later in the flow, a second publish should overwrite the same owned storage object.
                uploadPresetFile(accessToken, storagePath, request.localPresetPath(), true);

                JsonObject payload = buildPresetPayload(request, slug, storagePath, userId);
                HttpRequest requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(PROJECT_URL + "/rest/v1/marketplace_presets"))
                    .timeout(Duration.ofSeconds(20))
                    .header("apikey", PUBLISHABLE_KEY)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Prefer", "return=representation")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();

                HttpResponse<String> response = HTTP_CLIENT.send(requestBuilder, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw buildHttpException("Failed to publish marketplace preset", response);
                }
                return parseSinglePreset(response.body());
            } catch (Exception e) {
                throw new RuntimeException("Failed to publish marketplace preset", e);
            }
        });
    }

    public static CompletableFuture<MarketplacePreset> updatePresetMetadata(String accessToken, String presetId, PublishRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (accessToken == null || accessToken.isBlank()) {
                    throw new IOException("Marketplace auth token missing.");
                }
                if (presetId == null || presetId.isBlank()) {
                    throw new IOException("Preset id is missing.");
                }
                if (request == null) {
                    throw new IOException("Preset metadata is missing.");
                }

                JsonObject payload = buildPresetPayload(
                    request,
                    sanitizeSlug(request.slug()),
                    request.existingFilePath(),
                    null
                );
                HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(PROJECT_URL + "/rest/v1/marketplace_presets?id=eq."
                        + URLEncoder.encode(presetId, StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(20))
                    .header("apikey", PUBLISHABLE_KEY)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Prefer", "return=representation")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();

                HttpResponse<String> response = HTTP_CLIENT.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw buildHttpException("Failed to update marketplace preset", response);
                }
                return parseSinglePreset(response.body());
            } catch (Exception e) {
                throw new RuntimeException("Failed to update marketplace preset", e);
            }
        });
    }

    private static String buildListingsUrl() {
        return PROJECT_URL
            + "/rest/v1/marketplace_presets"
            + "?select=id,slug,name,author_name,description,tags,game_version,pathmind_version,likes_count,downloads_count,file_path,published,created_at,updated_at"
            + "&published=eq.true"
            + "&order=created_at.desc";
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
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(PROJECT_URL + "/rest/v1/rpc/" + functionName))
            .timeout(Duration.ofSeconds(15))
            .header("apikey", PUBLISHABLE_KEY)
            .header("Authorization", "Bearer " + accessToken)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
            .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String body = response.body() == null ? "" : response.body().toLowerCase(Locale.ROOT);
            if (body.contains("jwt expired") || body.contains("token is expired") || body.contains("invalid jwt")) {
                throw new IOException("Marketplace auth expired.");
            }
            throw new IOException("Marketplace RPC " + functionName + " failed with HTTP " + response.statusCode());
        }
        JsonElement root = JsonParser.parseString(response.body());
        return root != null && root.isJsonObject() ? root.getAsJsonObject() : new JsonObject();
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

    private static void uploadPresetFile(String accessToken, String storagePath, Path localPresetPath, boolean upsert)
        throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(PROJECT_URL + "/storage/v1/object/" + BUCKET_NAME + "/" + encodeStoragePath(storagePath)))
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

    private static void deleteStorageObject(String accessToken, String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(PROJECT_URL + "/storage/v1/object/" + BUCKET_NAME + "/" + encodeStoragePath(storagePath)))
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

    private static IOException buildHttpException(String message, HttpResponse<String> response) {
        String body = response.body() == null ? "" : response.body().trim();
        String normalizedBody = body.toLowerCase(Locale.ROOT);
        if (normalizedBody.contains("jwt expired") || normalizedBody.contains("token is expired") || normalizedBody.contains("invalid jwt")) {
            return new IOException("Marketplace auth expired.");
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
            getString(row, "name"),
            getString(row, "author_name"),
            getString(row, "description"),
            getStringArray(row, "tags"),
            getNullableString(row, "game_version"),
            getNullableString(row, "pathmind_version"),
            getInt(row, "likes_count"),
            getInt(row, "downloads_count"),
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
            preset.getName(),
            preset.getAuthorName(),
            preset.getDescription(),
            preset.getTags(),
            preset.getGameVersion(),
            preset.getPathmindVersion(),
            Math.max(0, likesCount),
            preset.getDownloadsCount(),
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
        String existingFilePath,
        String slug,
        String name,
        String authorName,
        String description,
        List<String> tags,
        String gameVersion,
        String pathmindVersion
    ) {
    }
}
