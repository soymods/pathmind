package com.pathmind.ui.graph;

import java.util.List;
import java.util.Locale;

import com.pathmind.nodes.Node;

/** Owns graph node lookup, search scoring, selection, and viewport focus. */
final class NodeFocusController {

    interface Host {
        List<Node> nodes();
        void stopEditorsForFocus();
        void clearSelection();
        void selectNode(Node node);
        void focusViewport(
            Node node, int screenWidth, int screenHeight, int sidebarWidth, int titleBarHeight);
    }

    private final Host host;

    NodeFocusController(Host host) {
        this.host = host;
    }

    boolean focusNodeById(
        String nodeId, int screenWidth, int screenHeight, int sidebarWidth, int titleBarHeight
    ) {
        if (nodeId == null || nodeId.isBlank()) {
            return false;
        }
        for (Node node : host.nodes()) {
            if (node != null && nodeId.equals(node.getId())) {
                focusNode(node, screenWidth, screenHeight, sidebarWidth, titleBarHeight);
                return true;
            }
        }
        return false;
    }

    void focusNode(
        Node node, int screenWidth, int screenHeight, int sidebarWidth, int titleBarHeight
    ) {
        if (node == null) {
            return;
        }
        host.stopEditorsForFocus();
        host.clearSelection();
        host.selectNode(node);

        host.focusViewport(node, screenWidth, screenHeight, sidebarWidth, titleBarHeight);
    }

    boolean focusBestMatchingNode(
        String query, int screenWidth, int screenHeight, int sidebarWidth, int titleBarHeight
    ) {
        Node match = findBestMatchingNode(query);
        if (match == null) {
            return false;
        }
        focusNode(match, screenWidth, screenHeight, sidebarWidth, titleBarHeight);
        return true;
    }

    String getBestMatchingNodeLabel(String query) {
        Node match = findBestMatchingNode(query);
        if (match == null || match.getType() == null) {
            return null;
        }
        return match.getType().getDisplayName();
    }

    private Node findBestMatchingNode(String query) {
        if (query == null) {
            return null;
        }
        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        if (normalizedQuery.isEmpty()) {
            return null;
        }

        Node bestNode = null;
        int bestScore = 0;
        for (Node node : host.nodes()) {
            if (node == null || node.getType() == null) {
                continue;
            }
            int score = scoreNodeSearch(node, normalizedQuery);
            if (score > bestScore) {
                bestScore = score;
                bestNode = node;
            }
        }
        return bestNode;
    }

    private int scoreNodeSearch(Node node, String query) {
        int bestScore = 0;
        bestScore = Math.max(bestScore, scoreSearchCandidate(node.getType().getDisplayName(), query));
        if (node.getMode() != null) {
            bestScore = Math.max(bestScore, scoreSearchCandidate(node.getMode().getDisplayName(), query) - 20);
        }
        if (node.getId() != null) {
            bestScore = Math.max(bestScore, scoreSearchCandidate(node.getId(), query) - 40);
        }
        return bestScore;
    }

    private int scoreSearchCandidate(String candidate, String query) {
        if (candidate == null || query == null) {
            return 0;
        }
        String normalizedCandidate = candidate.trim().toLowerCase(Locale.ROOT);
        if (normalizedCandidate.isEmpty() || query.isEmpty()) {
            return 0;
        }
        if (normalizedCandidate.equals(query)) {
            return 1000;
        }
        if (normalizedCandidate.startsWith(query)) {
            return 800 - Math.max(0, normalizedCandidate.length() - query.length());
        }
        int containsIndex = normalizedCandidate.indexOf(query);
        if (containsIndex >= 0) {
            return 650 - containsIndex * 6;
        }

        int fuzzyScore = fuzzySubsequenceScore(normalizedCandidate, query);
        return fuzzyScore > 0 ? 300 + fuzzyScore : 0;
    }

    private int fuzzySubsequenceScore(String candidate, String query) {
        int score = 0;
        int streak = 0;
        int queryIndex = 0;
        for (int i = 0; i < candidate.length() && queryIndex < query.length(); i++) {
            if (candidate.charAt(i) == query.charAt(queryIndex)) {
                score += 8 + streak * 4;
                streak++;
                queryIndex++;
            } else {
                streak = 0;
            }
        }
        if (queryIndex != query.length()) {
            return 0;
        }
        return Math.max(1, score - Math.max(0, candidate.length() - query.length()));
    }
}
