package com.pathmind.ui.graph;

import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeConnection;

final class ConnectionGeometry {

    private ConnectionGeometry() {
    }

    static boolean isPointNearConnection(NodeConnection connection, int worldX, int worldY, int threshold) {
        if (connection == null) {
            return false;
        }
        Node outputNode = connection.getOutputNode();
        Node inputNode = connection.getInputNode();
        if (outputNode == null || inputNode == null) {
            return false;
        }

        int outputX = outputNode.getSocketX(false);
        int outputY = outputNode.getSocketY(connection.getOutputSocket(), false);
        int inputX = inputNode.getSocketX(true);
        int inputY = inputNode.getSocketY(connection.getInputSocket(), true);
        int midX = outputX + (inputX - outputX) / 2;

        return pointToSegmentDistanceSquared(worldX, worldY, outputX, outputY, midX, outputY) <= threshold * threshold
            || pointToSegmentDistanceSquared(worldX, worldY, midX, outputY, midX, inputY) <= threshold * threshold
            || pointToSegmentDistanceSquared(worldX, worldY, midX, inputY, inputX, inputY) <= threshold * threshold;
    }

    static boolean doesSegmentIntersectConnection(NodeConnection connection, int x1, int y1, int x2, int y2, int threshold) {
        if (connection == null) {
            return false;
        }
        Node outputNode = connection.getOutputNode();
        Node inputNode = connection.getInputNode();
        if (outputNode == null || inputNode == null) {
            return false;
        }

        int outputX = outputNode.getSocketX(false);
        int outputY = outputNode.getSocketY(connection.getOutputSocket(), false);
        int inputX = inputNode.getSocketX(true);
        int inputY = inputNode.getSocketY(connection.getInputSocket(), true);
        int midX = outputX + (inputX - outputX) / 2;

        return segmentsIntersectWithTolerance(x1, y1, x2, y2, outputX, outputY, midX, outputY, threshold)
            || segmentsIntersectWithTolerance(x1, y1, x2, y2, midX, outputY, midX, inputY, threshold)
            || segmentsIntersectWithTolerance(x1, y1, x2, y2, midX, inputY, inputX, inputY, threshold);
    }

    static boolean connectionIntersectsRect(NodeConnection connection, int left, int top, int right, int bottom, int tolerance) {
        if (connection == null) {
            return false;
        }
        Node outputNode = connection.getOutputNode();
        Node inputNode = connection.getInputNode();
        if (outputNode == null || inputNode == null) {
            return false;
        }

        int expandedLeft = left - tolerance;
        int expandedTop = top - tolerance;
        int expandedRight = right + tolerance;
        int expandedBottom = bottom + tolerance;
        int outputX = outputNode.getSocketX(false);
        int outputY = outputNode.getSocketY(connection.getOutputSocket(), false);
        int inputX = inputNode.getSocketX(true);
        int inputY = inputNode.getSocketY(connection.getInputSocket(), true);
        int midX = outputX + (inputX - outputX) / 2;

        return axisAlignedSegmentIntersectsRect(outputX, outputY, midX, outputY, expandedLeft, expandedTop, expandedRight, expandedBottom)
            || axisAlignedSegmentIntersectsRect(midX, outputY, midX, inputY, expandedLeft, expandedTop, expandedRight, expandedBottom)
            || axisAlignedSegmentIntersectsRect(midX, inputY, inputX, inputY, expandedLeft, expandedTop, expandedRight, expandedBottom);
    }

    private static boolean axisAlignedSegmentIntersectsRect(int x1, int y1, int x2, int y2, int left, int top, int right, int bottom) {
        if (x1 == x2) {
            int x = x1;
            int minY = Math.min(y1, y2);
            int maxY = Math.max(y1, y2);
            return x >= left && x <= right && maxY >= top && minY <= bottom;
        }
        if (y1 == y2) {
            int y = y1;
            int minX = Math.min(x1, x2);
            int maxX = Math.max(x1, x2);
            return y >= top && y <= bottom && maxX >= left && minX <= right;
        }
        return false;
    }

    static double connectionDistanceToPoint(NodeConnection connection, double px, double py) {
        if (connection == null) {
            return Double.MAX_VALUE;
        }
        Node outputNode = connection.getOutputNode();
        Node inputNode = connection.getInputNode();
        if (outputNode == null || inputNode == null) {
            return Double.MAX_VALUE;
        }

        int outputX = outputNode.getSocketX(false);
        int outputY = outputNode.getSocketY(connection.getOutputSocket(), false);
        int inputX = inputNode.getSocketX(true);
        int inputY = inputNode.getSocketY(connection.getInputSocket(), true);
        int midX = outputX + (inputX - outputX) / 2;

        double distanceA = pointToSegmentDistanceSquared((int) Math.round(px), (int) Math.round(py), outputX, outputY, midX, outputY);
        double distanceB = pointToSegmentDistanceSquared((int) Math.round(px), (int) Math.round(py), midX, outputY, midX, inputY);
        double distanceC = pointToSegmentDistanceSquared((int) Math.round(px), (int) Math.round(py), midX, inputY, inputX, inputY);
        return Math.min(distanceA, Math.min(distanceB, distanceC));
    }

    private static boolean segmentsIntersectWithTolerance(int ax1, int ay1, int ax2, int ay2,
                                                          int bx1, int by1, int bx2, int by2,
                                                          int tolerance) {
        if (segmentsIntersect(ax1, ay1, ax2, ay2, bx1, by1, bx2, by2)) {
            return true;
        }
        int toleranceSq = tolerance * tolerance;
        return pointToSegmentDistanceSquared(ax1, ay1, bx1, by1, bx2, by2) <= toleranceSq
            || pointToSegmentDistanceSquared(ax2, ay2, bx1, by1, bx2, by2) <= toleranceSq
            || pointToSegmentDistanceSquared(bx1, by1, ax1, ay1, ax2, ay2) <= toleranceSq
            || pointToSegmentDistanceSquared(bx2, by2, ax1, ay1, ax2, ay2) <= toleranceSq;
    }

    private static boolean segmentsIntersect(int ax1, int ay1, int ax2, int ay2,
                                             int bx1, int by1, int bx2, int by2) {
        int o1 = orientation(ax1, ay1, ax2, ay2, bx1, by1);
        int o2 = orientation(ax1, ay1, ax2, ay2, bx2, by2);
        int o3 = orientation(bx1, by1, bx2, by2, ax1, ay1);
        int o4 = orientation(bx1, by1, bx2, by2, ax2, ay2);

        if (o1 != o2 && o3 != o4) {
            return true;
        }

        return (o1 == 0 && onSegment(ax1, ay1, bx1, by1, ax2, ay2))
            || (o2 == 0 && onSegment(ax1, ay1, bx2, by2, ax2, ay2))
            || (o3 == 0 && onSegment(bx1, by1, ax1, ay1, bx2, by2))
            || (o4 == 0 && onSegment(bx1, by1, ax2, ay2, bx2, by2));
    }

    private static int orientation(int ax, int ay, int bx, int by, int cx, int cy) {
        long value = (long) (by - ay) * (cx - bx) - (long) (bx - ax) * (cy - by);
        if (value == 0L) {
            return 0;
        }
        return value > 0L ? 1 : 2;
    }

    private static boolean onSegment(int ax, int ay, int bx, int by, int cx, int cy) {
        return bx >= Math.min(ax, cx) && bx <= Math.max(ax, cx)
            && by >= Math.min(ay, cy) && by <= Math.max(ay, cy);
    }

    private static int pointToSegmentDistanceSquared(int px, int py, int x1, int y1, int x2, int y2) {
        int dx = x2 - x1;
        int dy = y2 - y1;
        if (dx == 0 && dy == 0) {
            int diffX = px - x1;
            int diffY = py - y1;
            return diffX * diffX + diffY * diffY;
        }

        double lengthSquared = (double) dx * dx + (double) dy * dy;
        double projection = ((px - x1) * (double) dx + (py - y1) * (double) dy) / lengthSquared;
        double t = Math.max(0.0d, Math.min(1.0d, projection));
        double closestX = x1 + t * dx;
        double closestY = y1 + t * dy;
        double distX = px - closestX;
        double distY = py - closestY;
        return (int) Math.round(distX * distX + distY * distY);
    }
}
