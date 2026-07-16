package com.pathmind.ui.graph;

import com.pathmind.nodes.Node;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class NodeGraphHierarchySupport {
    private NodeGraphHierarchySupport() {
    }

    static NodeGraph.SelectionBounds calculateBounds(Collection<Node> nodesToMeasure) {
        if (nodesToMeasure == null || nodesToMeasure.isEmpty()) {
            return null;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Node node : nodesToMeasure) {
            if (node == null) {
                continue;
            }
            minX = Math.min(minX, node.getX());
            minY = Math.min(minY, node.getY());
            maxX = Math.max(maxX, node.getX() + node.getWidth());
            maxY = Math.max(maxY, node.getY() + node.getHeight());
        }
        if (minX == Integer.MAX_VALUE) {
            return null;
        }
        return new NodeGraph.SelectionBounds(minX, minY, maxX, maxY);
    }

    static void collectHierarchy(Node node, List<Node> result, Set<Node> visited) {
        if (node == null || visited.contains(node)) {
            return;
        }
        visited.add(node);
        result.add(node);

        Node actionChild = node.getAttachedActionNode();
        collectHierarchy(actionChild, result, visited);

        Node sensorChild = node.getAttachedSensor();
        collectHierarchy(sensorChild, result, visited);

        Map<Integer, Node> parameterMap = node.getAttachedParameters();
        if (parameterMap != null && !parameterMap.isEmpty()) {
            List<Integer> keys = new ArrayList<>(parameterMap.keySet());
            Collections.sort(keys);
            for (Integer key : keys) {
                collectHierarchy(parameterMap.get(key), result, visited);
            }
        }
    }

    static void bringNodeToFront(NodeGraph graph, Node node, List<Node> nodes) {
        if (node == null) {
            return;
        }
        Node root = graph.getRootNode(node);
        List<Node> hierarchy = new ArrayList<>();
        collectHierarchy(root, hierarchy, new HashSet<>());
        for (Node member : hierarchy) {
            nodes.remove(member);
        }
        nodes.addAll(hierarchy);
    }

    static void rebuildHierarchyCacheIfNeeded(
        NodeGraph graph,
        boolean hierarchyGeometryDirty,
        List<Node> nodes,
        List<Node> cachedRootNodes,
        Map<Node, NodeGraph.SelectionBounds> cachedHierarchyBounds,
        Map<Node, Integer> cachedHierarchyNodeCounts
    ) {
        if (!hierarchyGeometryDirty) {
            return;
        }

        cachedRootNodes.clear();
        cachedHierarchyBounds.clear();
        cachedHierarchyNodeCounts.clear();

        Set<Node> seenRoots = new LinkedHashSet<>();
        for (Node node : nodes) {
            Node root = graph.getRootNode(node);
            if (root == null || !seenRoots.add(root)) {
                continue;
            }

            List<Node> hierarchyNodes = new ArrayList<>();
            graph.collectHierarchyNodes(root, hierarchyNodes, new HashSet<>());
            cachedRootNodes.add(root);
            cachedHierarchyBounds.put(root, calculateBounds(hierarchyNodes));
            cachedHierarchyNodeCounts.put(root, hierarchyNodes.size());
        }
    }
}
