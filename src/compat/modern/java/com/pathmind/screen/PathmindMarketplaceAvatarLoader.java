package com.pathmind.screen;

import com.pathmind.util.TextureCompatibilityBridge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

final class PathmindMarketplaceAvatarLoader {
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private String accountTextureUrl = null;
    private Identifier accountTextureId = null;
    private boolean accountLoading = false;

    private String viewedAuthorUrl = null;
    private String viewedAuthorTextureUrl = null;
    private Identifier viewedAuthorTextureId = null;
    private boolean viewedAuthorLoading = false;

    private final Map<String, Identifier> authorDirectoryTextures = new HashMap<>();
    private final Set<String> authorDirectoryLoading = new HashSet<>();

    Identifier getAccount(MinecraftClient client, String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return null;
        }
        if (avatarUrl.equals(accountTextureUrl) && accountTextureId != null) {
            return accountTextureId;
        }
        if (accountLoading) {
            return accountTextureId;
        }
        accountLoading = true;
        CompletableFuture.supplyAsync(() -> downloadAvatarImage(avatarUrl)).whenComplete((image, throwable) -> {
            if (client == null) {
                accountLoading = false;
                return;
            }
            client.execute(() -> {
                accountLoading = false;
                if (throwable == null && image != null) {
                    accountTextureUrl = avatarUrl;
                    accountTextureId = registerAvatarTexture(client, avatarUrl, image);
                }
            });
        });
        return accountTextureId;
    }

    Identifier getViewedAuthor(MinecraftClient client, String avatarUrl) {
        viewedAuthorUrl = avatarUrl;
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return null;
        }
        if (viewedAuthorTextureId != null && avatarUrl.equals(viewedAuthorTextureUrl)) {
            return viewedAuthorTextureId;
        }
        if (viewedAuthorLoading) {
            return viewedAuthorTextureId;
        }
        viewedAuthorLoading = true;
        CompletableFuture.supplyAsync(() -> downloadAvatarImage(avatarUrl)).whenComplete((image, throwable) -> {
            if (client == null) {
                viewedAuthorLoading = false;
                return;
            }
            client.execute(() -> {
                viewedAuthorLoading = false;
                if (throwable == null && image != null && avatarUrl.equals(viewedAuthorUrl)) {
                    viewedAuthorTextureUrl = avatarUrl;
                    viewedAuthorTextureId = registerAvatarTexture(client, avatarUrl, image);
                }
            });
        });
        return viewedAuthorTextureId;
    }

    Identifier getAuthorDirectory(MinecraftClient client, String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return null;
        }
        Identifier existing = authorDirectoryTextures.get(avatarUrl);
        if (existing != null) {
            return existing;
        }
        if (authorDirectoryLoading.contains(avatarUrl)) {
            return null;
        }
        authorDirectoryLoading.add(avatarUrl);
        CompletableFuture.supplyAsync(() -> downloadAvatarImage(avatarUrl)).whenComplete((image, throwable) -> {
            if (client == null) {
                authorDirectoryLoading.remove(avatarUrl);
                return;
            }
            client.execute(() -> {
                authorDirectoryLoading.remove(avatarUrl);
                if (throwable == null && image != null) {
                    authorDirectoryTextures.put(avatarUrl, registerAvatarTexture(client, avatarUrl, image));
                }
            });
        });
        return null;
    }

    void clearViewedAuthor() {
        viewedAuthorUrl = null;
        viewedAuthorTextureUrl = null;
        viewedAuthorTextureId = null;
        viewedAuthorLoading = false;
    }

    private NativeImage downloadAvatarImage(String avatarUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(avatarUrl))
                .GET()
                .build();
            HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            try (InputStream input = response.body()) {
                return NativeImage.read(input);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private Identifier registerAvatarTexture(MinecraftClient client, String avatarUrl, NativeImage image) {
        if (client == null || image == null) {
            return null;
        }
        NativeImageBackedTexture texture = TextureCompatibilityBridge.createNativeImageBackedTexture("pathmind_marketplace_avatar", image);
        Identifier id = Identifier.of("pathmind", "textures/dynamic/marketplace_avatar_" + Integer.toHexString(avatarUrl.hashCode()));
        client.getTextureManager().registerTexture(id, texture);
        return id;
    }
}
