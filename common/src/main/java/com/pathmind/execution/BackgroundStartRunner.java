package com.pathmind.execution;

import com.pathmind.data.NodeGraphData;
import com.pathmind.data.NodeGraphPersistence;
import com.pathmind.data.PresetManager;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeConnection;
import com.pathmind.nodes.NodeType;
import com.pathmind.nodes.StartLaunchMode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class BackgroundStartRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(BackgroundStartRunner.class);
    private static final BackgroundStartRunner INSTANCE = new BackgroundStartRunner();

    private final Map<String, CompletableFuture<Void>> activeLaunches = new ConcurrentHashMap<>();

    private BackgroundStartRunner() {
    }

    public static BackgroundStartRunner getInstance() {
        return INSTANCE;
    }

    public void launch(StartLaunchMode mode) {
        launch(mode, null);
    }

    public void launch(StartLaunchMode mode, String screenKey) {
        if (mode == null || mode == StartLaunchMode.MANUAL) {
            return;
        }
        launchPresetStarts(PresetManager.getActivePreset(), mode, screenKey);
    }

    private void launchPresetStarts(String presetName, StartLaunchMode mode, String screenKey) {
        NodeGraphData data = NodeGraphPersistence.loadNodeGraphForPreset(presetName);
        if (data == null || data.getNodes() == null || data.getNodes().isEmpty()) {
            return;
        }

        List<Node> nodes = NodeGraphPersistence.convertToNodes(data);
        Map<String, Node> nodeMap = new HashMap<>();
        for (Node node : nodes) {
            if (node != null) {
                nodeMap.put(node.getId(), node);
            }
        }
        List<NodeConnection> connections = NodeGraphPersistence.convertToConnections(data, nodeMap);
        for (Node node : nodes) {
            if (node == null || node.getType() != NodeType.START || node.getStartLaunchMode() != mode) {
                continue;
            }
            if (mode == StartLaunchMode.SCREEN_OPENED && !node.getStartScreenTarget().matches(screenKey)) {
                continue;
            }
            launchStart(presetName, node, nodes, connections, mode);
        }
    }

    private void launchStart(String presetName, Node startNode, List<Node> nodes, List<NodeConnection> connections,
                             StartLaunchMode mode) {
        String key = launchKey(presetName, startNode, mode);
        CompletableFuture<Void> existing = activeLaunches.get(key);
        if (existing != null && !existing.isDone()) {
            return;
        }

        CompletableFuture<Void> future = ExecutionManager.getInstance().executeExternalBranchAndWait(
            startNode,
            nodes,
            connections,
            presetName
        );
        if (future == null) {
            LOGGER.debug("Background START {} in preset {} did not launch", startNode.getStartNodeNumber(), presetName);
            return;
        }

        activeLaunches.put(key, future);
        future.whenComplete((ignored, throwable) -> activeLaunches.remove(key, future));
    }

    private static String launchKey(String presetName, Node startNode, StartLaunchMode mode) {
        String normalizedPreset = presetName == null ? "" : presetName.trim().toLowerCase(Locale.ROOT);
        return normalizedPreset + ":" + startNode.getStartNodeNumber() + ":" + mode.getId();
    }
}
