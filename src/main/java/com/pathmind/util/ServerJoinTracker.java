package com.pathmind.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Tracks players who recently appeared in the current server player list.
 */
public final class ServerJoinTracker {
    private static final Object LOCK = new Object();
    private static final Deque<JoinEntry> JOINS = new ArrayDeque<>();
    private static final Set<String> KNOWN_PLAYERS = new HashSet<>();
    private static final long RETENTION_MS = 10_000L;
    private static final int MAX_ENTRIES = 200;
    private static boolean initialized;

    private ServerJoinTracker() {
    }

    public static void recordClientJoin(MinecraftClient client) {
        clear();
        if (client == null || client.player == null) {
            return;
        }
        record(GameProfileCompatibilityBridge.getName(client.player.getGameProfile()), System.currentTimeMillis());
    }

    public static void tick(MinecraftClient client) {
        if (client == null || client.getNetworkHandler() == null || client.world == null) {
            clearKnownPlayers();
            return;
        }
        long now = System.currentTimeMillis();
        Set<String> currentPlayers = new HashSet<>();
        for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
            if (entry == null || entry.getProfile() == null) {
                continue;
            }
            String normalized = normalizePlayerName(GameProfileCompatibilityBridge.getName(entry.getProfile()));
            if (normalized != null) {
                currentPlayers.add(normalized);
            }
        }
        synchronized (LOCK) {
            prune(now);
            if (!initialized) {
                KNOWN_PLAYERS.clear();
                KNOWN_PLAYERS.addAll(currentPlayers);
                initialized = true;
                return;
            }
            for (String player : currentPlayers) {
                if (KNOWN_PLAYERS.add(player)) {
                    JOINS.addLast(new JoinEntry(player, now));
                }
            }
            KNOWN_PLAYERS.retainAll(currentPlayers);
            while (JOINS.size() > MAX_ENTRIES) {
                JOINS.removeFirst();
            }
        }
    }

    public static boolean hasRecentJoin(String playerName, double seconds, boolean matchAnyPlayer) {
        String normalized = normalizePlayerName(playerName);
        if (!matchAnyPlayer && normalized == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        long windowMs = (long) Math.ceil(Math.max(0.0, seconds) * 1000.0);
        long cutoff = now - windowMs;
        synchronized (LOCK) {
            prune(now);
            for (JoinEntry entry : JOINS) {
                if (entry.timestampMs < cutoff) {
                    continue;
                }
                if (matchAnyPlayer || entry.playerLower.equals(normalized)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static double getRetentionSeconds() {
        return RETENTION_MS / 1000.0;
    }

    public static void clear() {
        synchronized (LOCK) {
            JOINS.clear();
            KNOWN_PLAYERS.clear();
            initialized = false;
        }
    }

    private static void record(String playerName, long timestampMs) {
        String normalized = normalizePlayerName(playerName);
        if (normalized == null) {
            return;
        }
        synchronized (LOCK) {
            prune(timestampMs);
            JOINS.addLast(new JoinEntry(normalized, timestampMs));
            while (JOINS.size() > MAX_ENTRIES) {
                JOINS.removeFirst();
            }
        }
    }

    private static void clearKnownPlayers() {
        synchronized (LOCK) {
            KNOWN_PLAYERS.clear();
            initialized = false;
        }
    }

    private static void prune(long now) {
        long cutoff = now - RETENTION_MS;
        while (!JOINS.isEmpty()) {
            JoinEntry entry = JOINS.peekFirst();
            if (entry == null || entry.timestampMs >= cutoff) {
                break;
            }
            JOINS.removeFirst();
        }
    }

    private static String normalizePlayerName(String playerName) {
        if (playerName == null) {
            return null;
        }
        String trimmed = playerName.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static final class JoinEntry {
        private final String playerLower;
        private final long timestampMs;

        private JoinEntry(String playerLower, long timestampMs) {
            this.playerLower = playerLower;
            this.timestampMs = timestampMs;
        }
    }
}
