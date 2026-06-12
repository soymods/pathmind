package com.pathmind.nodes;

/**
 * Pure editor geometry helpers for nodes.
 *
 * <p>Node still owns the public API during the migration, but these helpers keep
 * hit testing and child placement math out of the logical node shell.
 */
final class NodeGeometry {
    private NodeGeometry() {
    }

    static int socketY(
            int nodeY,
            int nodeHeight,
            int socketIndex,
            int socketCount,
            int socketSpacing,
            boolean compactEntryNode,
            boolean minimalPresentation,
            int standardHeaderHeight,
            int standardHeaderPadding) {
        if (compactEntryNode) {
            return nodeY + nodeHeight / 2;
        }
        if (minimalPresentation) {
            if (socketCount <= 1) {
                return nodeY + nodeHeight / 2;
            }
            int totalHeight = (socketCount - 1) * socketSpacing;
            int startY = nodeY + nodeHeight / 2 - totalHeight / 2;
            return startY + socketIndex * socketSpacing;
        }
        int contentStartY = nodeY + standardHeaderHeight + standardHeaderPadding;
        return contentStartY + socketIndex * socketSpacing;
    }

    static int socketX(int nodeX, int nodeWidth, boolean isInput, int socketInset) {
        return isInput ? nodeX - socketInset : nodeX + nodeWidth + socketInset;
    }

    static boolean containsPoint(int left, int top, int width, int height, int pointX, int pointY) {
        return pointX >= left && pointX <= left + width
            && pointY >= top && pointY <= top + height;
    }

    static boolean isPointNear(int centerX, int centerY, int radius, int pointX, int pointY) {
        return Math.abs(pointX - centerX) <= radius && Math.abs(pointY - centerY) <= radius;
    }

    static int centeredChildX(int slotLeft, int innerPadding, int slotWidth, int childWidth, int visualAdjustment) {
        int availableWidth = slotWidth - 2 * innerPadding;
        return slotLeft + innerPadding + Math.max(0, (availableWidth - childWidth - visualAdjustment) / 2);
    }

    static int centeredChildY(int slotTop, int innerPadding, int slotHeight, int childHeight) {
        int availableHeight = slotHeight - 2 * innerPadding;
        return slotTop + innerPadding + Math.max(0, (availableHeight - childHeight) / 2);
    }
}
