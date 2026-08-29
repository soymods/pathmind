package com.pathmind.nodes;

import static com.pathmind.util.PathmindI18n.tr;

import com.pathmind.PathmindCommon;
import com.pathmind.data.SettingsManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * Out-of-game notification. Automation runs while the player is tabbed out or AFK, so an
 * alert has to reach someone who is not looking at the screen: a sound for when you are at
 * the machine, a webhook for when you are not.
 */
final class NodeAlertCommandExecutor {
    /** Discord kills webhooks that are hammered, and an Alert inside Forever would do exactly that. */
    private static final long WEBHOOK_MIN_INTERVAL_MS = 3_000L;
    private static final AtomicLong LAST_WEBHOOK_SEND_MS = new AtomicLong(0L);

    private static final String DEFAULT_SOUND_ID = "minecraft:block.note_block.pling";
    private static final Duration WEBHOOK_TIMEOUT = Duration.ofSeconds(10);

    private static volatile HttpClient httpClient;

    private final Node owner;

    NodeAlertCommandExecutor(Node owner) {
        this.owner = owner;
    }

    void executeAlertCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        String text = resolveAlertText();

        if (owner.getMode() == NodeMode.ALERT_WEBHOOK) {
            sendWebhook(client, text);
        } else {
            playSound(client);
        }

        // An alert must never stall the graph it is reporting on, so the node completes as soon
        // as the notification is dispatched rather than waiting on the network.
        future.complete(null);
    }

    private String resolveAlertText() {
        List<String> lines = getMessageLines();
        if (lines == null || lines.isEmpty()) {
            return "Pathmind alert";
        }
        StringBuilder builder = new StringBuilder();
        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(owner.resolveRuntimeVariablesInText(line));
        }
        return builder.isEmpty() ? "Pathmind alert" : builder.toString();
    }

    // ---------------------------------------------------------------- sound

    private void playSound(Minecraft client) {
        if (client == null) {
            return;
        }
        String soundId = owner.getStringParameter("Sound", DEFAULT_SOUND_ID);
        SoundEvent soundEvent = resolveSoundEvent(soundId);
        if (soundEvent == null) {
            sendNodeErrorMessage(client, tr("pathmind.error.unknownSound", soundId));
            return;
        }
        float volume = (float) Math.max(0.0, Math.min(1.0, owner.getDoubleParameter("Volume", 1.0)));
        if (volume <= 0.0F) {
            return;
        }
        client.execute(() -> {
            try {
                // forUI keeps the alert at full volume regardless of where the player is standing,
                // which is the whole point of an alert.
                client.getSoundManager().play(SimpleSoundInstance.forUI(soundEvent, 1.0F, volume));
            } catch (RuntimeException | LinkageError error) {
                PathmindCommon.LOGGER.warn("Alert node failed to play a sound", error);
            }
        });
    }

    private static SoundEvent resolveSoundEvent(String soundId) {
        String candidate = soundId == null || soundId.isBlank() ? DEFAULT_SOUND_ID : soundId.trim();
        Identifier identifier = Identifier.tryParse(candidate);
        if (identifier == null) {
            return null;
        }
        return BuiltInRegistries.SOUND_EVENT.getOptional(identifier).orElse(null);
    }

    // -------------------------------------------------------------- webhook

    private void sendWebhook(Minecraft client, String text) {
        String url = SettingsManager.getAlertWebhookUrl();
        if (url == null) {
            sendNodeErrorMessage(client, tr("pathmind.error.alertWebhookUnset"));
            return;
        }

        long now = System.currentTimeMillis();
        long previous = LAST_WEBHOOK_SEND_MS.get();
        if (now - previous < WEBHOOK_MIN_INTERVAL_MS
            || !LAST_WEBHOOK_SEND_MS.compareAndSet(previous, now)) {
            PathmindCommon.LOGGER.debug("Alert webhook skipped: minimum interval not elapsed");
            return;
        }

        HttpRequest request;
        try {
            request = buildRequest(url, text);
        } catch (IllegalArgumentException invalid) {
            sendNodeErrorMessage(client, tr("pathmind.error.alertWebhookUnset"));
            return;
        }

        client().sendAsync(request, HttpResponse.BodyHandlers.discarding())
            .thenAccept(response -> {
                if (response.statusCode() >= 300) {
                    PathmindCommon.LOGGER.warn("Alert webhook returned HTTP {}", response.statusCode());
                }
            })
            .exceptionally(error -> {
                PathmindCommon.LOGGER.warn("Alert webhook failed to send", error);
                return null;
            });
    }

    private static HttpRequest buildRequest(String url, String text) {
        // ponytail: two payload shapes cover the endpoints people actually use - Discord wants
        // JSON, ntfy takes the raw body. Add a Format parameter if a third shape shows up.
        boolean discord = URI.create(url).getHost().toLowerCase(java.util.Locale.ROOT).contains("discord");
        String body = discord ? "{\"content\":" + jsonString(text) + "}" : text;
        return HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(WEBHOOK_TIMEOUT)
            .header("Content-Type", discord ? "application/json" : "text/plain; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
    }

    static String jsonString(String value) {
        String source = value == null ? "" : value;
        StringBuilder out = new StringBuilder(source.length() + 2).append('"');
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    private static HttpClient client() {
        HttpClient existing = httpClient;
        if (existing == null) {
            synchronized (NodeAlertCommandExecutor.class) {
                existing = httpClient;
                if (existing == null) {
                    existing = HttpClient.newBuilder().connectTimeout(WEBHOOK_TIMEOUT).build();
                    httpClient = existing;
                }
            }
        }
        return existing;
    }

    // ------------------------------------------------------------ delegates

    private Node.ParameterHandlingResult preprocessAttachedParameter(CompletableFuture<Void> future) {
        return owner.preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future);
    }

    private List<String> getMessageLines() {
        return owner.getMessageLines();
    }

    private void sendNodeErrorMessage(Minecraft client, String message) {
        owner.sendNodeErrorMessage(client, message);
    }
}
