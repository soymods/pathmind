package com.pathmind.util;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * Tracks recently received chat messages for time-window queries.
 */
public final class ChatMessageTracker {
    private static final Object LOCK = new Object();
    private static final Deque<ChatMessageEntry> MESSAGES = new ArrayDeque<>();
    private static final long MAX_RETENTION_MS = 60_000L;
    private static final int MAX_ENTRIES = 200;

    private ChatMessageTracker() {
    }

    public static void record(String sender, String rawMessage, long timestampMs) {
        if (sender == null || rawMessage == null) {
            return;
        }
        String senderTrim = sender.trim();
        if (senderTrim.isEmpty()) {
            return;
        }
        String rawTrim = rawMessage.trim();
        if (rawTrim.isEmpty()) {
            return;
        }
        String content = stripSenderPrefix(rawTrim, senderTrim);
        synchronized (LOCK) {
            prune(timestampMs);
            MESSAGES.addLast(new ChatMessageEntry(senderTrim, rawTrim, content, timestampMs));
            while (MESSAGES.size() > MAX_ENTRIES) {
                MESSAGES.removeFirst();
            }
        }
    }

    public static boolean hasRecentMessage(String sender, String message, double seconds) {
        if (sender == null || message == null) {
            return false;
        }
        String senderTrim = sender.trim();
        String messageTrim = message.trim();
        if (senderTrim.isEmpty() || messageTrim.isEmpty()) {
            return false;
        }
        long now = System.currentTimeMillis();
        long windowMs = (long) Math.ceil(Math.max(0.0, seconds) * 1000.0);
        long cutoff = now - windowMs;
        String senderLower = senderTrim.toLowerCase(Locale.ROOT);
        String messageLower = messageTrim.toLowerCase(Locale.ROOT);

        synchronized (LOCK) {
            prune(now);
            for (ChatMessageEntry entry : MESSAGES) {
                if (entry.timestampMs < cutoff) {
                    continue;
                }
                if (!entry.senderLower.equals(senderLower)) {
                    continue;
                }
                if (entry.rawLower.equals(messageLower) || entry.contentLower.equals(messageLower)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void clear() {
        synchronized (LOCK) {
            MESSAGES.clear();
        }
    }

    private static void prune(long now) {
        long cutoff = now - MAX_RETENTION_MS;
        while (!MESSAGES.isEmpty()) {
            ChatMessageEntry entry = MESSAGES.peekFirst();
            if (entry == null || entry.timestampMs >= cutoff) {
                break;
            }
            MESSAGES.removeFirst();
        }
    }

    private static String stripSenderPrefix(String message, String sender) {
        String trimmed = message.trim();
        if (sender == null || sender.isEmpty()) {
            return trimmed;
        }
        String[] prefixes = new String[]{
            "<" + sender + "> ",
            sender + ": ",
            sender + " > ",
            sender + ">> ",
            sender + " -> "
        };
        for (String prefix : prefixes) {
            if (trimmed.startsWith(prefix)) {
                return trimmed.substring(prefix.length()).trim();
            }
        }
        return trimmed;
    }

    private static final class ChatMessageEntry {
        private final String senderLower;
        private final String rawLower;
        private final String contentLower;
        private final long timestampMs;

        private ChatMessageEntry(String sender, String rawMessage, String content, long timestampMs) {
            this.senderLower = sender.toLowerCase(Locale.ROOT);
            this.rawLower = rawMessage.toLowerCase(Locale.ROOT);
            this.contentLower = content.toLowerCase(Locale.ROOT);
            this.timestampMs = timestampMs;
        }
    }
}
