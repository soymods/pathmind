package com.pathmind.marketplace;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Util;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Browser-based Supabase auth for the marketplace.
 */
public final class MarketplaceAuthManager {
    public static final String CALLBACK_HOST = "127.0.0.1";
    public static final int CALLBACK_PORT = 38451;
    public static final String CALLBACK_PATH = "/auth/callback";
    public static final String COMPLETE_PATH = "/auth/complete";
    public static final String CALLBACK_URL = "http://" + CALLBACK_HOST + ":" + CALLBACK_PORT + CALLBACK_PATH;

    private static final String BASE_DIRECTORY_NAME = "pathmind";
    private static final String SESSION_FILE_NAME = "marketplace_auth.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private static final Object LOCK = new Object();

    private static volatile AuthSession cachedSession;
    private static volatile PendingLogin pendingLogin;
    private static volatile HttpServer callbackServer;

    private MarketplaceAuthManager() {
    }

    public static void initialize() {
        loadSessionFromDisk();
    }

    public static Optional<AuthSession> getCachedSession() {
        AuthSession session = cachedSession;
        if (session == null) {
            session = loadSessionFromDisk();
        }
        if (session == null) {
            return Optional.empty();
        }
        return Optional.of(session);
    }

    public static CompletableFuture<AuthSession> ensureValidSession() {
        return CompletableFuture.supplyAsync(() -> {
            AuthSession session = loadSessionFromDisk();
            if (session == null) {
                return null;
            }
            if (!session.shouldRefreshSoon()) {
                return session;
            }
            try {
                AuthSession refreshed = refreshSession(session);
                if (refreshed != null) {
                    return refreshed;
                }
            } catch (Exception ignored) {
            }
            clearSession();
            return null;
        });
    }

    public static CompletableFuture<AuthSession> startDiscordSignIn() {
        synchronized (LOCK) {
            PendingLogin existing = pendingLogin;
            if (existing != null) {
                return existing.future;
            }
            PendingLogin login = new PendingLogin();
            pendingLogin = login;
            try {
                ensureCallbackServer();
                Util.getOperatingSystem().open(buildDiscordAuthorizeUrl());
            } catch (Exception e) {
                pendingLogin = null;
                login.future.completeExceptionally(e);
            }
            return login.future;
        }
    }

    public static CompletableFuture<Void> signOut() {
        return CompletableFuture.runAsync(() -> {
            AuthSession session = loadSessionFromDisk();
            if (session != null && session.accessToken != null && !session.accessToken.isBlank()) {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(MarketplaceService.PROJECT_URL + "/auth/v1/logout"))
                        .timeout(Duration.ofSeconds(10))
                        .header("apikey", MarketplaceService.PUBLISHABLE_KEY)
                        .header("Authorization", "Bearer " + session.accessToken)
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build();
                    HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
                } catch (Exception ignored) {
                }
            }
            clearSession();
        });
    }

    private static AuthSession loadSessionFromDisk() {
        AuthSession session = cachedSession;
        if (session != null) {
            return session;
        }
        Path path = getSessionPath();
        if (!Files.exists(path)) {
            return null;
        }
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            AuthSession loaded = GSON.fromJson(json, AuthSession.class);
            cachedSession = loaded;
            return loaded;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void saveSession(AuthSession session) {
        try {
            cachedSession = session;
            Files.createDirectories(getSessionPath().getParent());
            Files.writeString(getSessionPath(), GSON.toJson(session), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static void clearSession() {
        cachedSession = null;
        try {
            Files.deleteIfExists(getSessionPath());
        } catch (IOException ignored) {
        }
    }

    private static AuthSession refreshSession(AuthSession session) throws IOException, InterruptedException {
        if (session == null || session.refreshToken == null || session.refreshToken.isBlank()) {
            return null;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("refresh_token", session.refreshToken);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(MarketplaceService.PROJECT_URL + "/auth/v1/token?grant_type=refresh_token"))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json")
            .header("apikey", MarketplaceService.PUBLISHABLE_KEY)
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
            .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return null;
        }
        AuthSession refreshed = parseSessionResponse(response.body());
        if (refreshed == null) {
            return null;
        }
        AuthSession merged = refreshed.mergeProfile(session.userId, session.email, session.displayName, session.avatarUrl, session.provider);
        AuthSession withProfile = fetchUserProfile(merged.accessToken).map(merged::withProfile).orElse(merged);
        syncMarketplaceAuthorProfile(withProfile);
        saveSession(withProfile);
        return withProfile;
    }

    private static void ensureCallbackServer() throws IOException {
        if (callbackServer != null) {
            return;
        }
        HttpServer server = HttpServer.create(new InetSocketAddress(CALLBACK_HOST, CALLBACK_PORT), 0);
        server.createContext("/", MarketplaceAuthManager::handleCallbackPage);
        server.createContext(CALLBACK_PATH, MarketplaceAuthManager::handleCallbackPage);
        server.createContext(COMPLETE_PATH, MarketplaceAuthManager::handleComplete);
        server.setExecutor(Runnable::run);
        server.start();
        callbackServer = server;
    }

    private static void handleCallbackPage(HttpExchange exchange) throws IOException {
        byte[] body = CALLBACK_HTML.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static void handleComplete(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        String body;
        try (InputStream input = exchange.getRequestBody()) {
            body = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        PendingLogin login = pendingLogin;
        int status = 200;
        String response = "{\"ok\":true}";
        try {
            JsonObject payload = JsonParser.parseString(body).getAsJsonObject();
            String href = getJsonString(payload, "href");
            Map<String, String> params = parseMergedParams(href);
            if (login == null) {
                throw new IllegalStateException("No sign-in request is waiting for a callback.");
            }

            if (params.containsKey("error_description")) {
                throw new IllegalStateException(params.get("error_description"));
            }
            if (params.containsKey("error")) {
                throw new IllegalStateException(params.get("error"));
            }

            AuthSession session = parseSessionParams(params);
            if (session == null) {
                throw new IllegalStateException("Supabase did not return a marketplace session.");
            }
            AuthSession withProfile = fetchUserProfile(session.accessToken).map(session::withProfile).orElse(session);
            syncMarketplaceAuthorProfile(withProfile);
            saveSession(withProfile);
            pendingLogin = null;
            login.future.complete(withProfile);
        } catch (Exception e) {
            status = 400;
            response = "{\"ok\":false}";
            if (login != null) {
                pendingLogin = null;
                login.future.completeExceptionally(e);
            }
        }

        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, responseBytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(responseBytes);
        }
    }

    private static Optional<UserProfile> fetchUserProfile(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return Optional.empty();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(MarketplaceService.PROJECT_URL + "/auth/v1/user"))
                .timeout(Duration.ofSeconds(15))
                .header("apikey", MarketplaceService.PUBLISHABLE_KEY)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET()
                .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }

            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            String userId = getJsonString(root, "id");
            String email = getJsonString(root, "email");
            JsonObject metadata = root.has("user_metadata") && root.get("user_metadata").isJsonObject()
                ? root.getAsJsonObject("user_metadata")
                : new JsonObject();
            JsonObject appMetadata = root.has("app_metadata") && root.get("app_metadata").isJsonObject()
                ? root.getAsJsonObject("app_metadata")
                : new JsonObject();
            String displayName = firstNonBlank(
                getJsonString(metadata, "full_name"),
                getJsonString(metadata, "name"),
                getJsonString(metadata, "preferred_username"),
                getJsonString(metadata, "user_name"),
                email
            );
            String avatarUrl = firstNonBlank(
                getJsonString(metadata, "avatar_url"),
                getJsonString(metadata, "picture")
            );
            String provider = firstNonBlank(
                getJsonString(appMetadata, "provider"),
                getJsonString(metadata, "provider")
            );
            return Optional.of(new UserProfile(userId, email, displayName, avatarUrl, provider));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static String buildDiscordAuthorizeUrl() {
        return MarketplaceService.PROJECT_URL
            + "/auth/v1/authorize?provider=discord"
            + "&redirect_to=" + URLEncoder.encode(CALLBACK_URL, StandardCharsets.UTF_8)
            + "&scopes=" + URLEncoder.encode("identify email", StandardCharsets.UTF_8)
            + "&apikey=" + URLEncoder.encode(MarketplaceService.PUBLISHABLE_KEY, StandardCharsets.UTF_8);
    }

    private static void syncMarketplaceAuthorProfile(AuthSession session) {
        if (session == null || session.accessToken == null || session.accessToken.isBlank()) {
            return;
        }
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("p_display_name", firstNonBlank(session.displayName, session.email, "Discord user"));
            payload.addProperty("p_avatar_url", firstNonBlank(session.avatarUrl, ""));
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(MarketplaceService.PROJECT_URL + "/rest/v1/rpc/sync_marketplace_author_profile"))
                .timeout(Duration.ofSeconds(15))
                .header("apikey", MarketplaceService.PUBLISHABLE_KEY)
                .header("Authorization", "Bearer " + session.accessToken)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();
            HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
        }
    }

    private static Map<String, String> parseMergedParams(String href) {
        Map<String, String> values = new LinkedHashMap<>();
        if (href == null || href.isBlank()) {
            return values;
        }
        try {
            URI uri = URI.create(href);
            mergeQueryString(values, uri.getRawQuery());
            String fragment = uri.getRawFragment();
            if (fragment != null && !fragment.isBlank()) {
                mergeQueryString(values, fragment);
            }
        } catch (Exception ignored) {
        }
        return values;
    }

    private static void mergeQueryString(Map<String, String> values, String query) {
        if (query == null || query.isBlank()) {
            return;
        }
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            if (pair == null || pair.isBlank()) {
                continue;
            }
            int equalsIndex = pair.indexOf('=');
            String key = equalsIndex >= 0 ? pair.substring(0, equalsIndex) : pair;
            String value = equalsIndex >= 0 ? pair.substring(equalsIndex + 1) : "";
            values.put(URLDecoder.decode(key, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
    }

    private static AuthSession parseSessionParams(Map<String, String> params) {
        String accessToken = firstNonBlank(params.get("access_token"));
        String refreshToken = firstNonBlank(params.get("refresh_token"));
        if (accessToken == null || refreshToken == null) {
            return null;
        }
        long expiresAt = 0L;
        String expiresAtValue = params.get("expires_at");
        if (expiresAtValue != null && !expiresAtValue.isBlank()) {
            try {
                expiresAt = Long.parseLong(expiresAtValue);
            } catch (NumberFormatException ignored) {
            }
        }
        if (expiresAt <= 0L) {
            String expiresInValue = params.get("expires_in");
            if (expiresInValue != null && !expiresInValue.isBlank()) {
                try {
                    expiresAt = (System.currentTimeMillis() / 1000L) + Long.parseLong(expiresInValue);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (expiresAt <= 0L) {
            expiresAt = (System.currentTimeMillis() / 1000L) + 3600L;
        }
        return new AuthSession(
            accessToken,
            refreshToken,
            expiresAt,
            null,
            null,
            null,
            null,
            null
        );
    }

    private static AuthSession parseSessionResponse(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            String accessToken = getJsonString(root, "access_token");
            String refreshToken = getJsonString(root, "refresh_token");
            if (accessToken == null || refreshToken == null) {
                return null;
            }
            long expiresAt = 0L;
            if (root.has("expires_at") && !root.get("expires_at").isJsonNull()) {
                expiresAt = root.get("expires_at").getAsLong();
            } else if (root.has("expires_in") && !root.get("expires_in").isJsonNull()) {
                expiresAt = (System.currentTimeMillis() / 1000L) + root.get("expires_in").getAsLong();
            }

            String userId = null;
            String email = null;
            String displayName = null;
            String avatarUrl = null;
            String provider = null;
            if (root.has("user") && root.get("user").isJsonObject()) {
                JsonObject user = root.getAsJsonObject("user");
                userId = getJsonString(user, "id");
                email = getJsonString(user, "email");
                JsonObject metadata = user.has("user_metadata") && user.get("user_metadata").isJsonObject()
                    ? user.getAsJsonObject("user_metadata")
                    : new JsonObject();
                JsonObject appMetadata = user.has("app_metadata") && user.get("app_metadata").isJsonObject()
                    ? user.getAsJsonObject("app_metadata")
                    : new JsonObject();
                displayName = firstNonBlank(
                    getJsonString(metadata, "full_name"),
                    getJsonString(metadata, "name"),
                    getJsonString(metadata, "preferred_username"),
                    email
                );
                avatarUrl = firstNonBlank(getJsonString(metadata, "avatar_url"), getJsonString(metadata, "picture"));
                provider = firstNonBlank(getJsonString(appMetadata, "provider"), getJsonString(metadata, "provider"));
            }
            return new AuthSession(accessToken, refreshToken, expiresAt, userId, email, displayName, avatarUrl, provider);
        } catch (Exception e) {
            return null;
        }
    }

    private static String getJsonString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        return object.get(key).getAsString();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static Path getSessionPath() {
        return getBaseDirectory().resolve(SESSION_FILE_NAME);
    }

    private static Path getBaseDirectory() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.runDirectory != null) {
            return client.runDirectory.toPath().resolve(BASE_DIRECTORY_NAME);
        }
        return FabricLoader.getInstance().getGameDir().resolve(BASE_DIRECTORY_NAME);
    }

    private static final class PendingLogin {
        private final CompletableFuture<AuthSession> future = new CompletableFuture<>();
    }

    private static final class UserProfile {
        private final String userId;
        private final String email;
        private final String displayName;
        private final String avatarUrl;
        private final String provider;

        private UserProfile(String userId, String email, String displayName, String avatarUrl, String provider) {
            this.userId = userId;
            this.email = email;
            this.displayName = displayName;
            this.avatarUrl = avatarUrl;
            this.provider = provider;
        }
    }

    public static final class AuthSession {
        private String accessToken;
        private String refreshToken;
        private long expiresAtEpochSeconds;
        private String userId;
        private String email;
        private String displayName;
        private String avatarUrl;
        private String provider;

        public AuthSession() {
        }

        public AuthSession(String accessToken, String refreshToken, long expiresAtEpochSeconds,
                           String userId, String email, String displayName, String avatarUrl, String provider) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresAtEpochSeconds = expiresAtEpochSeconds;
            this.userId = userId;
            this.email = email;
            this.displayName = displayName;
            this.avatarUrl = avatarUrl;
            this.provider = provider;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public long getExpiresAtEpochSeconds() {
            return expiresAtEpochSeconds;
        }

        public String getUserId() {
            return userId;
        }

        public String getEmail() {
            return email;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getAvatarUrl() {
            return avatarUrl;
        }

        public String getProvider() {
            return provider;
        }

        public boolean shouldRefreshSoon() {
            return expiresAtEpochSeconds <= 0L || expiresAtEpochSeconds - 60L <= (System.currentTimeMillis() / 1000L);
        }

        private AuthSession withProfile(UserProfile profile) {
            return new AuthSession(
                accessToken,
                refreshToken,
                expiresAtEpochSeconds,
                profile.userId,
                profile.email,
                profile.displayName,
                profile.avatarUrl,
                profile.provider
            );
        }

        private AuthSession mergeProfile(String fallbackUserId, String fallbackEmail, String fallbackDisplayName,
                                         String fallbackAvatarUrl, String fallbackProvider) {
            return new AuthSession(
                accessToken,
                refreshToken,
                expiresAtEpochSeconds,
                firstNonBlank(userId, fallbackUserId),
                firstNonBlank(email, fallbackEmail),
                firstNonBlank(displayName, fallbackDisplayName),
                firstNonBlank(avatarUrl, fallbackAvatarUrl),
                firstNonBlank(provider, fallbackProvider)
            );
        }
    }

    private static final String CALLBACK_HTML = """
        <!doctype html>
        <html lang="en">
        <head>
          <meta charset="utf-8" />
          <title>Pathmind Marketplace Login</title>
          <style>
            :root {
              --bg: #0f1215;
              --panel: #181d22;
              --panel-hi: #20262d;
              --border: #3b434d;
              --inner: #262d35;
              --text: #ffffff;
              --muted: #ffffff;
              --accent: #66d8ff;
              --accent-dim: rgba(102, 216, 255, 0.18);
              --error: #ff8a80;
              --grid: rgba(255,255,255,0.035);
            }

            * { box-sizing: border-box; }
            html, body { height: 100%; }
            body {
              margin: 0;
              color: var(--text);
              font-family: "Trebuchet MS", "Segoe UI", sans-serif;
              background:
                radial-gradient(circle at 50% 18%, rgba(102,216,255,0.20), transparent 24%),
                radial-gradient(circle at 28% 78%, rgba(255,77,77,0.16), transparent 24%),
                radial-gradient(circle at 72% 78%, rgba(100,235,120,0.16), transparent 24%),
                linear-gradient(rgba(255,255,255,0.02) 1px, transparent 1px),
                linear-gradient(90deg, rgba(255,255,255,0.02) 1px, transparent 1px),
                var(--bg);
              background-size: auto, auto, auto, 28px 28px, 28px 28px, auto;
            }

            main {
              min-height: 100vh;
              display: grid;
              place-items: center;
              padding: 32px;
            }

            .shell {
              width: min(720px, 100%);
            }

            .titlebar {
              text-align: center;
              margin-bottom: 18px;
            }

            .eyebrow {
              display: inline-block;
              margin-bottom: 10px;
              padding: 4px 10px;
              border: 1px solid var(--border);
              background: rgba(255,255,255,0.03);
              color: rgba(255,255,255,0.82);
              font-size: 11px;
              letter-spacing: 0.14em;
              text-transform: uppercase;
            }

            .brand {
              margin: 0;
              font-size: clamp(28px, 5vw, 42px);
              line-height: 1;
              letter-spacing: 0.01em;
              text-shadow: 0 0 18px rgba(102,216,255,0.16);
            }

            .subtitle {
              margin: 10px 0 0;
              color: rgba(255,255,255,0.84);
              font-size: 15px;
            }

            .card {
              position: relative;
              padding: 0;
              background: linear-gradient(180deg, rgba(255,255,255,0.03), rgba(255,255,255,0.01));
              border: 1px solid var(--border);
              box-shadow:
                inset 0 0 0 1px rgba(255,255,255,0.03),
                0 18px 60px rgba(0,0,0,0.36);
            }

            .card::before {
              content: "";
              position: absolute;
              inset: 6px;
              border: 1px solid var(--inner);
              pointer-events: none;
            }

            .card-top {
              display: grid;
              grid-template-columns: 104px 1fr;
              gap: 18px;
              padding: 22px;
              align-items: center;
            }

            .preview {
              position: relative;
              aspect-ratio: 1 / 1;
              border: 1px solid var(--border);
              background:
                linear-gradient(var(--grid) 1px, transparent 1px),
                linear-gradient(90deg, var(--grid) 1px, transparent 1px),
                #11161a;
              background-size: 18px 18px, 18px 18px;
              overflow: hidden;
            }

            .node {
              position: absolute;
              height: 14px;
              border: 1px solid #454f59;
              background: #1a2026;
              box-shadow: inset 0 0 0 1px rgba(255,255,255,0.03);
            }

            .n1 { left: 14px; top: 16px; width: 34px; }
            .n2 { left: 38px; top: 44px; width: 42px; }
            .n3 { left: 56px; top: 72px; width: 28px; }

            .wire {
              position: absolute;
              height: 2px;
              background: var(--accent);
              box-shadow: 0 0 10px var(--accent-dim);
            }

            .w1 { left: 48px; top: 23px; width: 22px; }
            .w2 { left: 58px; top: 51px; width: 24px; }

            .content h1 {
              margin: 0 0 8px;
              font-size: 24px;
              line-height: 1.1;
              color: var(--accent);
            }

            .content p {
              margin: 0;
              color: rgba(255,255,255,0.92);
              line-height: 1.55;
              font-size: 15px;
            }

            .footer {
              display: flex;
              justify-content: space-between;
              align-items: center;
              gap: 12px;
              padding: 14px 22px 20px;
              border-top: 1px solid rgba(255,255,255,0.045);
              color: rgba(255,255,255,0.82);
              font-size: 13px;
            }

            .hint {
              color: rgba(255,255,255,0.82);
            }

            .status-pill {
              padding: 6px 10px;
              border: 1px solid var(--border);
              background: rgba(255,255,255,0.025);
              color: #ffffff;
              white-space: nowrap;
            }

            .ok { color: #ffffff; }
            .err { color: var(--error); }

            .logo {
              width: 100%;
              height: 100%;
              object-fit: contain;
              display: block;
              image-rendering: auto;
              filter: drop-shadow(0 0 14px rgba(255,255,255,0.06));
            }

            @media (max-width: 560px) {
              .card-top {
                grid-template-columns: 1fr;
              }

              .preview {
                max-width: 150px;
              }

              .footer {
                flex-direction: column;
                align-items: flex-start;
              }
            }
          </style>
        </head>
        <body>
          <main>
            <div class="shell">
              <div class="titlebar">
                <div class="eyebrow">Pathmind Marketplace</div>
                <h1 class="brand">Marketplace Login</h1>
                <p class="subtitle">Connecting your Discord account to Pathmind.</p>
              </div>
              <div class="card">
                <div class="card-top">
                  <div class="preview" aria-hidden="true">
                    <img class="logo" src="https://soymods.com/pathmind/logo.png" alt="Pathmind logo" />
                  </div>
                  <div class="content">
                    <h1 id="title">Signing you in…</h1>
                    <p id="body">Pathmind is waiting for the Discord login result and will hand control back to the mod when it finishes.</p>
                  </div>
                </div>
                <div class="footer">
                  <span class="hint">Return to Minecraft after this page updates.</span>
                  <span id="status" class="status-pill">Awaiting callback</span>
                </div>
              </div>
            </div>
          </main>
          <script>
            const title = document.getElementById('title');
            const body = document.getElementById('body');
            const status = document.getElementById('status');
            fetch('http://127.0.0.1:38451/auth/complete', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ href: window.location.href })
            }).then(async (response) => {
              if (!response.ok) throw new Error('Auth callback was rejected.');
              title.textContent = 'Sign-in complete';
              title.className = 'ok';
              body.textContent = 'You can close this browser tab and return to Pathmind.';
              status.textContent = 'Connected';
            }).catch((error) => {
              title.textContent = 'Sign-in failed';
              title.className = 'err';
              body.textContent = error?.message || 'Pathmind could not finish the Discord login flow.';
              status.textContent = 'Needs attention';
              status.className = 'status-pill err';
            });
          </script>
        </body>
        </html>
        """;
}
