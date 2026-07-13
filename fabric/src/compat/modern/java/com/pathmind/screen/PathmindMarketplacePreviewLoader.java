package com.pathmind.screen;

import com.pathmind.data.NodeGraphData;
import com.pathmind.data.NodeGraphPersistence;
import com.pathmind.marketplace.MarketplacePreset;
import com.pathmind.marketplace.MarketplaceService;
import com.pathmind.nodes.Node;
import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.TextureCompatibilityBridge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.HashSet;

final class PathmindMarketplacePreviewLoader {
    private static final int MAX_CONCURRENT_REQUESTS = 3;
    private static final int THUMBNAIL_WIDTH = 112;
    private static final int THUMBNAIL_HEIGHT = 74;
    private static final int THUMBNAIL_PADDING_X = 8;
    private static final int THUMBNAIL_PADDING_Y = 6;
    private static final int THUMBNAIL_MAX_CONNECTIONS = 64;

    private final Map<String, PathmindMarketplaceScreen.PreviewGraphModel> cache = new HashMap<>();
    private final Set<String> loading = new HashSet<>();
    private final Queue<MarketplacePreset> queue = new ArrayDeque<>();
    private final Set<String> queued = new HashSet<>();

    PathmindMarketplaceScreen.PreviewGraphModel getCached(MarketplacePreset preset) {
        if (preset == null || preset.getId() == null) {
            return null;
        }
        return cache.get(preset.getId());
    }

    void request(MinecraftClient client, MarketplacePreset preset, String accessToken) {
        String presetId = preset == null ? null : preset.getId();
        if (presetId == null || cache.containsKey(presetId) || loading.contains(presetId) || queued.contains(presetId)) {
            return;
        }
        if (loading.size() >= MAX_CONCURRENT_REQUESTS) {
            queue.add(preset);
            queued.add(presetId);
            return;
        }
        startRequest(client, preset, presetId, accessToken);
    }

    void invalidate(MarketplacePreset preset) {
        if (preset == null || preset.getId() == null) {
            return;
        }
        cache.remove(preset.getId());
        loading.remove(preset.getId());
        queued.remove(preset.getId());
        queue.removeIf(queuedPreset -> preset.getId().equals(queuedPreset.getId()));
    }

    private void startRequest(MinecraftClient client, MarketplacePreset preset, String presetId, String accessToken) {
        loading.add(presetId);
        MarketplaceService.fetchPresetGraphData(preset, accessToken).whenComplete((graphData, throwable) -> {
            if (client == null) {
                return;
            }
            client.execute(() -> {
                loading.remove(presetId);
                if (throwable == null && graphData != null) {
                    cache.put(presetId, buildModel(client, presetId, graphData));
                }
                drainQueue(client, accessToken);
            });
        });
    }

    private void drainQueue(MinecraftClient client, String accessToken) {
        while (loading.size() < MAX_CONCURRENT_REQUESTS && !queue.isEmpty()) {
            MarketplacePreset queuedPreset = queue.poll();
            String queuedPresetId = queuedPreset == null ? null : queuedPreset.getId();
            if (queuedPresetId == null) {
                continue;
            }
            queued.remove(queuedPresetId);
            if (cache.containsKey(queuedPresetId) || loading.contains(queuedPresetId)) {
                continue;
            }
            startRequest(client, queuedPreset, queuedPresetId, accessToken);
        }
    }

    private PathmindMarketplaceScreen.PreviewGraphModel buildModel(MinecraftClient client, String presetId, NodeGraphData graphData) {
        List<Node> rebuiltNodes = NodeGraphPersistence.convertToNodes(graphData);
        Map<String, Node> nodeLookup = new HashMap<>();
        for (Node node : rebuiltNodes) {
            nodeLookup.put(node.getId(), node);
        }
        List<NodeGraphData.ConnectionData> connections = graphData.getConnections() == null
            ? List.of()
            : List.copyOf(graphData.getConnections());
        List<Node> nodes = List.copyOf(rebuiltNodes);
        PathmindMarketplaceScreen.GraphBounds bounds = PathmindMarketplaceScreen.GraphBounds.of(nodes);
        Identifier galleryThumbnailTexture = registerThumbnailTexture(client, presetId,
            buildThumbnailImage(nodes, connections, nodeLookup, bounds));
        return new PathmindMarketplaceScreen.PreviewGraphModel(nodes, connections, nodeLookup, bounds, galleryThumbnailTexture);
    }

    private NativeImage buildThumbnailImage(List<Node> nodes,
                                            List<NodeGraphData.ConnectionData> connections,
                                            Map<String, Node> nodeLookup,
                                            PathmindMarketplaceScreen.GraphBounds bounds) {
        NativeImage image = new NativeImage(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, true);
        clearThumbnailImage(image);
        if (nodes == null || nodes.isEmpty()) {
            return image;
        }

        float usableWidth = Math.max(1f, THUMBNAIL_WIDTH - THUMBNAIL_PADDING_X * 2f);
        float usableHeight = Math.max(1f, THUMBNAIL_HEIGHT - THUMBNAIL_PADDING_Y * 2f);
        float scaleX = usableWidth / Math.max(1f, bounds.width());
        float scaleY = usableHeight / Math.max(1f, bounds.height());
        float scale = Math.max(0.04f, Math.min(scaleX, scaleY));
        float offsetX = (THUMBNAIL_WIDTH - bounds.width() * scale) * 0.5f - bounds.minX() * scale;
        float offsetY = (THUMBNAIL_HEIGHT - bounds.height() * scale) * 0.5f - bounds.minY() * scale;

        int renderedConnections = 0;
        if (connections != null) {
            for (NodeGraphData.ConnectionData connection : connections) {
                if (renderedConnections >= THUMBNAIL_MAX_CONNECTIONS) {
                    break;
                }
                Node from = nodeLookup.get(connection.getOutputNodeId());
                Node to = nodeLookup.get(connection.getInputNodeId());
                if (from == null || to == null) {
                    continue;
                }
                int outputSocket = Math.max(0, connection.getOutputSocket());
                int inputSocket = Math.max(0, connection.getInputSocket());
                int safeOutputSocket = Math.min(outputSocket, Math.max(0, from.getOutputSocketCount() - 1));
                int safeInputSocket = Math.min(inputSocket, Math.max(0, to.getInputSocketCount() - 1));
                int startX = Math.round(offsetX + from.getSocketX(false) * scale);
                int startY = Math.round(offsetY + from.getSocketY(safeOutputSocket, false) * scale);
                int endX = Math.round(offsetX + to.getSocketX(true) * scale);
                int endY = Math.round(offsetY + to.getSocketY(safeInputSocket, true) * scale);
                drawThumbnailLine(image, startX, startY, endX, endY, from.getOutputSocketColor(safeOutputSocket));
                renderedConnections++;
            }
        }

        for (Node node : nodes) {
            if (node != null) {
                drawThumbnailNode(image, node, offsetX, offsetY, scale);
            }
        }
        return image;
    }

    private void clearThumbnailImage(NativeImage image) {
        if (image == null) {
            return;
        }
        for (int x = 0; x < THUMBNAIL_WIDTH; x++) {
            for (int y = 0; y < THUMBNAIL_HEIGHT; y++) {
                image.setColor(x, y, 0x00000000);
            }
        }
    }

    private void drawThumbnailNode(NativeImage image, Node node, float offsetX, float offsetY, float scale) {
        int x = Math.round(offsetX + node.getX() * scale);
        int y = Math.round(offsetY + node.getY() * scale);
        int width = Math.max(3, Math.round(Math.max(10f, node.getWidth()) * scale));
        int height = Math.max(3, Math.round(Math.max(8f, node.getHeight()) * scale));
        int color = node.getColor();
        int borderColor = node.isStopControlNode() ? UITheme.MARKETPLACE_STOP_NODE_BORDER : color;
        int backgroundColor = AnimationHelper.darken(UITheme.BACKGROUND_SECONDARY, 0.94f);
        fillThumbnailRect(image, x, y, width, height, backgroundColor);
        drawThumbnailRectBorder(image, x, y, width, height, borderColor);
        int headerHeight = Math.min(height, Math.max(1, Math.round(height * 0.35f)));
        fillThumbnailRect(image, x + 1, y + 1, Math.max(1, width - 2), Math.max(1, headerHeight), (color & UITheme.NODE_HEADER_ALPHA_MASK) | 0xFF000000);
    }

    private void drawThumbnailLine(NativeImage image, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        while (true) {
            setThumbnailPixel(image, x0, y0, color);
            if (x0 == x1 && y0 == y1) {
                break;
            }
            int e2 = err * 2;
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    private void fillThumbnailRect(NativeImage image, int x, int y, int width, int height, int color) {
        for (int px = x; px < x + width; px++) {
            for (int py = y; py < y + height; py++) {
                setThumbnailPixel(image, px, py, color);
            }
        }
    }

    private void drawThumbnailRectBorder(NativeImage image, int x, int y, int width, int height, int color) {
        for (int px = x; px < x + width; px++) {
            setThumbnailPixel(image, px, y, color);
            setThumbnailPixel(image, px, y + height - 1, color);
        }
        for (int py = y; py < y + height; py++) {
            setThumbnailPixel(image, x, py, color);
            setThumbnailPixel(image, x + width - 1, py, color);
        }
    }

    private void setThumbnailPixel(NativeImage image, int x, int y, int argbColor) {
        if (image == null || x < 0 || y < 0 || x >= THUMBNAIL_WIDTH || y >= THUMBNAIL_HEIGHT) {
            return;
        }
        image.setColor(x, y, toNativeImageColor(argbColor));
    }

    private int toNativeImageColor(int argbColor) {
        int alpha = (argbColor >>> 24) & 0xFF;
        int red = (argbColor >>> 16) & 0xFF;
        int green = (argbColor >>> 8) & 0xFF;
        int blue = argbColor & 0xFF;
        return (alpha << 24) | (blue << 16) | (green << 8) | red;
    }

    private Identifier registerThumbnailTexture(MinecraftClient client, String presetId, NativeImage image) {
        if (client == null || presetId == null || presetId.isBlank() || image == null) {
            return null;
        }
        NativeImageBackedTexture texture = TextureCompatibilityBridge.createNativeImageBackedTexture("pathmind_marketplace_preview", image);
        Identifier id = Identifier.of("pathmind", "textures/dynamic/marketplace_preview_" + Integer.toHexString(presetId.hashCode()));
        client.getTextureManager().registerTexture(id, texture);
        return id;
    }
}
