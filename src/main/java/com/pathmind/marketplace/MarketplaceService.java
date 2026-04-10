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
import java.util.List;
import java.util.Set;
import java.util.Locale;
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
                return parsePresets(response.body());
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

    private static String buildListingsUrl() {
        return PROJECT_URL
            + "/rest/v1/marketplace_presets"
            + "?select=id,slug,name,author_name,description,tags,game_version,pathmind_version,likes_count,downloads_count,file_path,published,created_at,updated_at"
            + "&published=eq.true"
            + "&order=created_at.desc";
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
            if (response.statusCode() == 401 || response.statusCode() == 403 || body.contains("unauthorized")) {
                throw new IOException("Marketplace auth expired.");
            }
            throw new IOException("Marketplace RPC " + functionName + " failed with HTTP " + response.statusCode());
        }
        JsonElement root = JsonParser.parseString(response.body());
        return root != null && root.isJsonObject() ? root.getAsJsonObject() : new JsonObject();
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
            JsonObject row = rowElement.getAsJsonObject();
            presets.add(new MarketplacePreset(
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
            ));
        }
        return presets;
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
}
