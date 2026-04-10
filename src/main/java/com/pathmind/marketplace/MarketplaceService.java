package com.pathmind.marketplace;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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

    private static String buildListingsUrl() {
        return PROJECT_URL
            + "/rest/v1/marketplace_presets"
            + "?select=id,slug,name,author_name,description,tags,game_version,pathmind_version,file_path,published,created_at"
            + "&published=eq.true"
            + "&order=created_at.desc";
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
                getString(row, "file_path"),
                getBoolean(row, "published"),
                getNullableString(row, "created_at")
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
