package com.pathmind.screen;

import com.pathmind.data.NodeGraphData;
import com.pathmind.data.NodeGraphPersistence;
import com.pathmind.marketplace.MarketplacePreset;
import com.pathmind.marketplace.MarketplaceService;
import com.pathmind.nodes.Node;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import net.minecraft.client.Minecraft;

final class PathmindMarketplacePreviewLoader {
    private static final int MAX_CONCURRENT_REQUESTS = 3;

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

    void request(Minecraft client, MarketplacePreset preset, String accessToken) {
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

    private void startRequest(Minecraft client, MarketplacePreset preset, String presetId, String accessToken) {
        loading.add(presetId);
        MarketplaceService.fetchPresetGraphData(preset, accessToken).whenComplete((graphData, throwable) -> {
            if (client == null) {
                return;
            }
            client.execute(() -> {
                loading.remove(presetId);
                if (throwable == null && graphData != null) {
                    cache.put(presetId, buildModel(graphData));
                }
                drainQueue(client, accessToken);
            });
        });
    }

    private void drainQueue(Minecraft client, String accessToken) {
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

    private PathmindMarketplaceScreen.PreviewGraphModel buildModel(NodeGraphData graphData) {
        List<Node> rebuiltNodes = NodeGraphPersistence.convertToNodes(graphData);
        Map<String, Node> nodeLookup = new HashMap<>();
        for (Node node : rebuiltNodes) {
            nodeLookup.put(node.getId(), node);
        }
        List<NodeGraphData.ConnectionData> connections = graphData.getConnections() == null
            ? List.of()
            : List.copyOf(graphData.getConnections());
        List<Node> nodes = List.copyOf(rebuiltNodes);
        return new PathmindMarketplaceScreen.PreviewGraphModel(
            nodes,
            connections,
            nodeLookup,
            PathmindMarketplaceScreen.GraphBounds.of(nodes)
        );
    }
}
