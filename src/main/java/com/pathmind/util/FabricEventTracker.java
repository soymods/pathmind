package com.pathmind.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks recently-fired Fabric events so sensor nodes can query them.
 */
public final class FabricEventTracker {
    private static final long MAX_RETENTION_MS = 60_000L;
    private static final Map<String, Long> LAST_EVENT_TIMESTAMPS = new ConcurrentHashMap<>();
    private static final AtomicLong LAST_ANY_EVENT_TIMESTAMP = new AtomicLong(0L);
    private static final List<String> SUPPORTED_EVENTS = buildSupportedEvents();

    private FabricEventTracker() {
    }

    public static void record(String eventName) {
        if (eventName == null) {
            return;
        }
        String normalized = normalize(eventName);
        if (normalized.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        LAST_EVENT_TIMESTAMPS.put(normalized, now);
        LAST_ANY_EVENT_TIMESTAMP.set(now);
    }

    public static boolean hasRecentEvent(String eventName, double seconds) {
        String normalized = normalize(eventName);
        if (normalized.isEmpty()) {
            return false;
        }
        long now = System.currentTimeMillis();
        long windowMs = (long) Math.ceil(Math.max(0.0, seconds) * 1000.0);
        long cutoff = now - windowMs;
        Long timestamp = LAST_EVENT_TIMESTAMPS.get(normalized);
        if (timestamp == null || timestamp < cutoff) {
            if (timestamp != null && now - timestamp > MAX_RETENTION_MS) {
                LAST_EVENT_TIMESTAMPS.remove(normalized, timestamp);
            }
            return false;
        }
        return true;
    }

    public static boolean hasAnyRecentEvent(double seconds) {
        long now = System.currentTimeMillis();
        long windowMs = (long) Math.ceil(Math.max(0.0, seconds) * 1000.0);
        long cutoff = now - windowMs;
        long timestamp = LAST_ANY_EVENT_TIMESTAMP.get();
        return timestamp >= cutoff;
    }

    public static double getMaxRetentionSeconds() {
        return MAX_RETENTION_MS / 1000.0;
    }

    public static void clear() {
        LAST_EVENT_TIMESTAMPS.clear();
        LAST_ANY_EVENT_TIMESTAMP.set(0L);
    }

    public static List<String> getSupportedEvents() {
        return SUPPORTED_EVENTS;
    }

    private static String normalize(String eventName) {
        if (eventName == null) {
            return "";
        }
        return eventName.trim().toLowerCase(Locale.ROOT);
    }

    private static List<String> buildSupportedEvents() {
        Set<String> ordered = new LinkedHashSet<>();

        // Lifecycle / world lifecycle
        ordered.add("fabric.client.lifecycle.block_entity_load");
        ordered.add("fabric.client.lifecycle.block_entity_unload");
        ordered.add("fabric.client.lifecycle.chunk_load");
        ordered.add("fabric.client.lifecycle.chunk_unload");
        ordered.add("fabric.client.lifecycle.entity_load");
        ordered.add("fabric.client.lifecycle.entity_unload");
        ordered.add("fabric.client.lifecycle.client_started");
        ordered.add("fabric.client.lifecycle.start_client_tick");
        ordered.add("fabric.client.lifecycle.end_client_tick");
        ordered.add("fabric.client.lifecycle.start_world_tick");
        ordered.add("fabric.client.lifecycle.end_world_tick");
        ordered.add("fabric.client.lifecycle.after_client_world_change");
        ordered.add("fabric.client.lifecycle.client_stopping");

        // Networking
        ordered.add("fabric.client.networking.c2s_configuration_channel_register");
        ordered.add("fabric.client.networking.c2s_configuration_channel_unregister");
        ordered.add("fabric.client.networking.c2s_play_channel_register");
        ordered.add("fabric.client.networking.c2s_play_channel_unregister");
        ordered.add("fabric.client.networking.configuration_connection_init");
        ordered.add("fabric.client.networking.configuration_connection_start");
        ordered.add("fabric.client.networking.configuration_connection_complete");
        ordered.add("fabric.client.networking.configuration_connection_disconnect");
        ordered.add("fabric.client.networking.login_connection_init");
        ordered.add("fabric.client.networking.login_connection_query_start");
        ordered.add("fabric.client.networking.login_connection_disconnect");
        ordered.add("fabric.client.networking.play_connection_init");
        ordered.add("fabric.client.networking.play_connection_join");
        ordered.add("fabric.client.networking.play_connection_disconnect");

        // Messages
        ordered.add("fabric.client.message.receive_allow_chat");
        ordered.add("fabric.client.message.receive_allow_game");
        ordered.add("fabric.client.message.receive_modify_game");
        ordered.add("fabric.client.message.receive_chat");
        ordered.add("fabric.client.message.receive_game");
        ordered.add("fabric.client.message.receive_chat_canceled");
        ordered.add("fabric.client.message.receive_game_canceled");
        ordered.add("fabric.client.message.send_allow_chat");
        ordered.add("fabric.client.message.send_allow_command");
        ordered.add("fabric.client.message.send_modify_chat");
        ordered.add("fabric.client.message.send_modify_command");
        ordered.add("fabric.client.message.send_chat");
        ordered.add("fabric.client.message.send_command");
        ordered.add("fabric.client.message.send_chat_canceled");
        ordered.add("fabric.client.message.send_command_canceled");

        // Rendering / HUD
        ordered.add("fabric.client.render.hud");

        // Player interaction callbacks
        ordered.add("fabric.player.attack_block");
        ordered.add("fabric.player.attack_entity");
        ordered.add("fabric.player.use_block");
        ordered.add("fabric.player.use_entity");
        ordered.add("fabric.player.use_item");

        return Collections.unmodifiableList(new ArrayList<>(ordered));
    }
}
